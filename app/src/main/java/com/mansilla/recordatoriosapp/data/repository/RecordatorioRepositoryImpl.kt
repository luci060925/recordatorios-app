package com.mansilla.recordatoriosapp.data.repository

import com.mansilla.recordatoriosapp.EstadoRecordatorio
import com.mansilla.recordatoriosapp.Recordatorio
import com.mansilla.recordatoriosapp.data.local.CodigoOperacion
import com.mansilla.recordatoriosapp.data.local.CodigoSync
import com.mansilla.recordatoriosapp.data.local.RecordatorioDao
import com.mansilla.recordatoriosapp.data.local.RecordatorioEntity
import com.mansilla.recordatoriosapp.data.mapper.codigo
import com.mansilla.recordatoriosapp.data.mapper.toDomain
import com.mansilla.recordatoriosapp.data.mapper.toDto
import com.mansilla.recordatoriosapp.data.mapper.toEntityOrNull
import com.mansilla.recordatoriosapp.data.remote.RecordatorioRemoteDataSource
import com.mansilla.recordatoriosapp.data.remote.ResultadoRed
import com.mansilla.recordatoriosapp.data.remote.TipoErrorHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Ejecuta un bloque como una transaccion de la base (en las pruebas, simplemente lo ejecuta). */
interface EjecutorTransacciones {
    suspend fun <T> ejecutar(bloque: suspend () -> T): T
}

/**
 * Repositorio local-first.
 *
 * - Lecturas: solo desde Room (Flow). La interfaz no depende de la conectividad.
 * - Escrituras: se aplican primero en Room y quedan marcadas como PENDIENTE;
 *   luego se intenta enviarlas al servicio en segundo plano.
 * - Sincronizacion: primero envia lo pendiente, despues descarga el estado del servicio.
 *
 * Como se evitan los duplicados:
 * 1. El id es un UUID generado en el dispositivo y viaja al servicio, que lo usa como
 *    clave: repetir un envio devuelve el registro existente en lugar de crear otro.
 * 2. Un Mutex impide dos sincronizaciones simultaneas, y las solicitudes automaticas
 *    se combinan en una sola (canal CONFLATED).
 * 3. Cada registro tiene una version: se marca como sincronizado solo si no cambio
 *    mientras se enviaba.
 * 4. Al descargar, un registro con cambios locales pendientes nunca se pisa.
 */
class RecordatorioRepositoryImpl(
    private val dao: RecordatorioDao,
    private val remoto: RecordatorioRemoteDataSource,
    private val transacciones: EjecutorTransacciones,
    private val alcance: CoroutineScope,
    private val reloj: () -> Long = { System.currentTimeMillis() },
    private val generarId: () -> String = { UUID.randomUUID().toString() }
) : RecordatorioRepository {

    private val mutex = Mutex()
    private val solicitudes = Channel<Unit>(Channel.CONFLATED)
    private val reintentarErroresPendiente = AtomicBoolean(false)

    private val _estadoSincronizacion = MutableStateFlow<EstadoSincronizacion>(EstadoSincronizacion.Inactiva)
    override val estadoSincronizacion: StateFlow<EstadoSincronizacion> = _estadoSincronizacion.asStateFlow()

    // ---------- Lecturas ----------

    override fun observarRecordatorios(filtro: FiltroRecordatorios): Flow<List<Recordatorio>> =
        dao.observarFiltrados(filtro.prioridad?.codigo(), filtro.incluirCompletados)
            .map { lista -> lista.map(RecordatorioEntity::toDomain) }

    override fun observarPorId(id: String): Flow<Recordatorio?> =
        dao.observarPorId(id).map { it?.toDomain() }

    override fun observarPorEstado(estado: EstadoRecordatorio): Flow<List<Recordatorio>> =
        dao.observarPorEstado(estado.codigo()).map { lista -> lista.map(RecordatorioEntity::toDomain) }

    override fun observarResumenPendientes(): Flow<ResumenPendientes> =
        combine(dao.observarCantidadPendientes(), dao.observarCantidadConError()) { pendientes, conError ->
            ResumenPendientes(pendientes, conError)
        }.distinctUntilChanged()

    // ---------- Escrituras (Room primero) ----------

    override suspend fun crear(datos: DatosRecordatorio): String {
        val ahora = reloj()
        val id = generarId()
        dao.insertar(
            RecordatorioEntity(
                id = id,
                titulo = datos.titulo.trim(),
                descripcion = datos.descripcion?.trim()?.takeIf { it.isNotEmpty() },
                categoria = datos.categoria.codigo(),
                prioridad = datos.prioridad.codigo(),
                estado = EstadoRecordatorio.Pendiente.codigo(),
                fecha = ahora,
                actualizadoEn = ahora,
                version = 1,
                estadoSync = CodigoSync.PENDIENTE,
                operacionPendiente = CodigoOperacion.CREAR,
                ultimoError = null
            )
        )
        solicitarSincronizacion()
        return id
    }

    override suspend fun actualizar(id: String, datos: DatosRecordatorio) {
        modificar(id) {
            it.copy(
                titulo = datos.titulo.trim(),
                descripcion = datos.descripcion?.trim()?.takeIf { d -> d.isNotEmpty() },
                categoria = datos.categoria.codigo(),
                prioridad = datos.prioridad.codigo()
            )
        }
    }

    override suspend fun cambiarEstado(id: String, estado: EstadoRecordatorio) {
        modificar(id) { it.copy(estado = estado.codigo()) }
    }

    override suspend fun eliminar(id: String) {
        val actual = dao.obtenerPorId(id) ?: return
        // Baja logica: el registro deja de verse, y se borra de verdad cuando el servicio lo confirma.
        dao.actualizar(
            actual.copy(
                estadoSync = CodigoSync.PENDIENTE,
                operacionPendiente = CodigoOperacion.ELIMINAR,
                version = actual.version + 1,
                actualizadoEn = reloj(),
                ultimoError = null
            )
        )
        solicitarSincronizacion()
    }

    private suspend fun modificar(id: String, cambio: (RecordatorioEntity) -> RecordatorioEntity) {
        val actual = dao.obtenerPorId(id) ?: return
        if (actual.operacionPendiente == CodigoOperacion.ELIMINAR) return
        // Un registro que todavia no se envio sigue siendo una creacion; si no, es una actualizacion.
        val operacion = if (actual.operacionPendiente == CodigoOperacion.CREAR) {
            CodigoOperacion.CREAR
        } else {
            CodigoOperacion.ACTUALIZAR
        }
        dao.actualizar(
            cambio(actual).copy(
                estadoSync = CodigoSync.PENDIENTE,
                operacionPendiente = operacion,
                version = actual.version + 1,
                actualizadoEn = reloj(),
                ultimoError = null
            )
        )
        solicitarSincronizacion()
    }

    // ---------- Sincronizacion ----------

    /** Inicia el trabajador que atiende las solicitudes y reacciona a la recuperacion de la red. */
    @OptIn(FlowPreview::class)
    fun iniciarSincronizacionAutomatica(conectividad: Flow<Boolean>) {
        alcance.launch {
            for (senal in solicitudes) {
                sincronizar(reintentarErroresPendiente.getAndSet(false))
            }
        }
        alcance.launch {
            conectividad
                .debounce(CONECTIVIDAD_ESTABLE_MS)
                .distinctUntilChanged()
                .filter { it }
                .collect { solicitarSincronizacion() }
        }
    }

    override fun solicitarSincronizacion(reintentarErrores: Boolean) {
        if (reintentarErrores) reintentarErroresPendiente.set(true)
        solicitudes.trySend(Unit)
    }

    override suspend fun sincronizar(reintentarErrores: Boolean): ResultadoSincronizacion {
        if (!mutex.tryLock()) return ResultadoSincronizacion.YaEnCurso
        try {
            _estadoSincronizacion.value = EstadoSincronizacion.Sincronizando
            if (reintentarErrores) dao.reactivarErrores()

            enviarPendientes()?.let { return fallo(it) }
            descargarRemotos()?.let { return fallo(it) }

            _estadoSincronizacion.value = EstadoSincronizacion.Exito(reloj())
            return ResultadoSincronizacion.Completada
        } finally {
            mutex.unlock()
        }
    }

    private fun fallo(error: EstadoSincronizacion.Error): ResultadoSincronizacion.Fallida {
        _estadoSincronizacion.value = error
        return ResultadoSincronizacion.Fallida(error.tipo, error.mensaje)
    }

    /**
     * Envia cada registro pendiente. Devuelve un error si hay que detener la sincronizacion
     * (sin red, timeout o falla del servidor); los registros quedan pendientes. Si el servicio
     * rechaza un registro (4xx) se marca ERROR y se continua con el resto.
     */
    private suspend fun enviarPendientes(): EstadoSincronizacion.Error? {
        for (entidad in dao.obtenerPendientes()) {
            val operacion = entidad.operacionPendiente
            val resultado: ResultadoRed<*> = when (operacion) {
                CodigoOperacion.CREAR -> remoto.crear(entidad.toDto())
                CodigoOperacion.ACTUALIZAR -> {
                    val r = remoto.actualizar(entidad.toDto())
                    // Si el servicio no tiene el registro, se vuelve a crear con el mismo id.
                    if (r is ResultadoRed.ErrorHttp && r.tipo == TipoErrorHttp.NO_ENCONTRADO) {
                        remoto.crear(entidad.toDto())
                    } else r
                }
                CodigoOperacion.ELIMINAR -> {
                    val r = remoto.eliminar(entidad.id)
                    // Si ya no existe en el servicio, el objetivo (que no exista) esta cumplido.
                    if (r is ResultadoRed.ErrorHttp && r.tipo == TipoErrorHttp.NO_ENCONTRADO) {
                        ResultadoRed.Exito(Unit)
                    } else r
                }
                else -> ResultadoRed.Exito(Unit)
            }

            when (resultado) {
                is ResultadoRed.Exito -> {
                    if (operacion == CodigoOperacion.ELIMINAR) {
                        dao.eliminarSiVersion(entidad.id, entidad.version)
                    } else {
                        dao.marcarSincronizado(entidad.id, entidad.version)
                    }
                }
                is ResultadoRed.ErrorHttp -> when (resultado.tipo) {
                    TipoErrorHttp.SERVIDOR -> return EstadoSincronizacion.Error(
                        TipoErrorSync.SERVIDOR,
                        "El servicio no está disponible (error ${resultado.codigo}). Se reintentará más tarde."
                    )
                    else -> dao.marcarError(
                        entidad.id,
                        entidad.version,
                        "El servicio rechazó el cambio (código ${resultado.codigo})."
                    )
                }
                ResultadoRed.Timeout -> return EstadoSincronizacion.Error(
                    TipoErrorSync.TIMEOUT,
                    "El servicio tardó demasiado en responder. Los cambios siguen guardados en el dispositivo."
                )
                ResultadoRed.SinConexion -> return EstadoSincronizacion.Error(
                    TipoErrorSync.SIN_CONEXION,
                    "Sin conexión. Los cambios se guardaron en el dispositivo y se enviarán cuando vuelva la red."
                )
                is ResultadoRed.ErrorDesconocido -> return EstadoSincronizacion.Error(
                    TipoErrorSync.DESCONOCIDO,
                    "Ocurrió un error inesperado al sincronizar."
                )
            }
        }
        return null
    }

    /** Descarga el estado del servicio y lo incorpora a Room, sin pisar cambios locales pendientes. */
    private suspend fun descargarRemotos(): EstadoSincronizacion.Error? {
        val respuesta = remoto.obtenerTodos()
        val dtos = when (respuesta) {
            is ResultadoRed.Exito -> respuesta.valor
            is ResultadoRed.ErrorHttp -> return EstadoSincronizacion.Error(
                if (respuesta.tipo == TipoErrorHttp.SERVIDOR) TipoErrorSync.SERVIDOR else TipoErrorSync.SOLICITUD_INVALIDA,
                "No se pudo actualizar desde el servicio (código ${respuesta.codigo}). Se muestran los datos locales."
            )
            ResultadoRed.Timeout -> return EstadoSincronizacion.Error(
                TipoErrorSync.TIMEOUT,
                "El servicio tardó demasiado en responder. Se muestran los datos locales."
            )
            ResultadoRed.SinConexion -> return EstadoSincronizacion.Error(
                TipoErrorSync.SIN_CONEXION,
                "Sin conexión. Se muestran los datos locales."
            )
            is ResultadoRed.ErrorDesconocido -> return EstadoSincronizacion.Error(
                TipoErrorSync.DESCONOCIDO,
                "Ocurrió un error inesperado al actualizar."
            )
        }

        val ahora = reloj()
        val remotos = dtos.mapNotNull { it.toEntityOrNull(ahora) }
        transacciones.ejecutar {
            val locales = dao.obtenerTodosParaSync().associateBy { it.id }
            val idsRemotos = remotos.map { it.id }.toSet()

            val aGuardar = remotos.filter { remota ->
                val local = locales[remota.id]
                local == null || local.estadoSync == CodigoSync.SINCRONIZADO
            }
            val aEliminar = locales.values
                .filter { it.estadoSync == CodigoSync.SINCRONIZADO && it.id !in idsRemotos }
                .map { it.id }

            dao.insertarTodos(aGuardar)
            if (aEliminar.isNotEmpty()) dao.eliminarPorIds(aEliminar)
        }
        return null
    }

    private companion object {
        const val CONECTIVIDAD_ESTABLE_MS = 500L
    }
}
