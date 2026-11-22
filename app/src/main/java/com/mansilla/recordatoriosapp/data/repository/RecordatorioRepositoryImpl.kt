package com.mansilla.recordatoriosapp.data.repository

import com.mansilla.recordatoriosapp.EstadoRecordatorio
import com.mansilla.recordatoriosapp.Recordatorio
import com.mansilla.recordatoriosapp.data.auth.EstadoAuth
import com.mansilla.recordatoriosapp.data.auth.ProveedorSesion
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
import com.mansilla.recordatoriosapp.data.remote.TipoErrorRemoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Ejecuta un bloque como una transaccion de la base (en las pruebas, simplemente lo ejecuta). */
interface EjecutorTransacciones {
    suspend fun <T> ejecutar(bloque: suspend () -> T): T
}

/** Se lanza si se intenta escribir sin una cuenta con sesion activa. */
class SinSesionException : IllegalStateException("No hay una sesion activa")

/**
 * Repositorio local-first asociado a la cuenta con sesion activa.
 *
 * - Lecturas: solo desde Room (Flow) y solo de la cuenta activa. La interfaz no depende
 *   de la conectividad ni de Firebase.
 * - Escrituras: se aplican primero en Room y quedan marcadas como PENDIENTE; luego se
 *   intenta enviarlas a la nube en segundo plano.
 * - Sincronizacion: primero envia lo pendiente, despues descarga el estado de la nube.
 *
 * Como se evitan los duplicados:
 * 1. El id es un UUID generado en el dispositivo y es el id del documento en Firestore:
 *    repetir un envio reemplaza el mismo documento en lugar de crear otro.
 * 2. Un Mutex impide dos sincronizaciones simultaneas, y las solicitudes automaticas
 *    se combinan en una sola (canal CONFLATED).
 * 3. Cada registro tiene una version: se marca como sincronizado solo si no cambio
 *    mientras se enviaba.
 * 4. Al descargar, un registro con cambios locales pendientes nunca se pisa.
 *
 * Como se evita mezclar cuentas:
 * - Cada fila de Room tiene ownerUid y todas las consultas lo filtran.
 * - Una sincronizacion captura el uid al empezar y lo usa en todas sus operaciones, aunque
 *   la sesion cambie mientras corre.
 */
class RecordatorioRepositoryImpl(
    private val dao: RecordatorioDao,
    private val remoto: RecordatorioRemoteDataSource,
    private val sesion: ProveedorSesion,
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

    // ---------- Lecturas (siempre desde Room, siempre de la cuenta activa) ----------

    /** Re-emite cada vez que cambia la cuenta; sin sesion devuelve [valorSinSesion]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> porCuenta(valorSinSesion: T, consulta: (String) -> Flow<T>): Flow<T> =
        sesion.estado
            .map { (it as? EstadoAuth.ConSesion)?.usuario?.uid }
            .distinctUntilChanged()
            .flatMapLatest { uid -> if (uid == null) flowOf(valorSinSesion) else consulta(uid) }

    override fun observarRecordatorios(filtro: FiltroRecordatorios): Flow<List<Recordatorio>> =
        porCuenta(emptyList()) { uid ->
            dao.observarFiltrados(uid, filtro.prioridad?.codigo(), filtro.incluirCompletados)
                .map { lista -> lista.map(RecordatorioEntity::toDomain) }
        }

    override fun observarPorId(id: String): Flow<Recordatorio?> =
        porCuenta<Recordatorio?>(null) { uid -> dao.observarPorId(uid, id).map { it?.toDomain() } }

    override fun observarPorEstado(estado: EstadoRecordatorio): Flow<List<Recordatorio>> =
        porCuenta(emptyList()) { uid ->
            dao.observarPorEstado(uid, estado.codigo()).map { lista -> lista.map(RecordatorioEntity::toDomain) }
        }

    override fun observarResumenPendientes(): Flow<ResumenPendientes> =
        porCuenta(ResumenPendientes()) { uid ->
            combine(dao.observarCantidadPendientes(uid), dao.observarCantidadConError(uid)) { pendientes, conError ->
                ResumenPendientes(pendientes, conError)
            }
        }.distinctUntilChanged()

    // ---------- Escrituras (Room primero) ----------

    private fun uidRequerido(): String = sesion.uidActual() ?: throw SinSesionException()

    override suspend fun crear(datos: DatosRecordatorio): String {
        val uid = uidRequerido()
        val ahora = reloj()
        val id = generarId()
        dao.insertar(
            RecordatorioEntity(
                id = id,
                ownerUid = uid,
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
        val uid = uidRequerido()
        val actual = dao.obtenerPorId(uid, id) ?: return
        // Baja logica: el registro deja de verse, y se borra de verdad cuando la nube lo confirma.
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
        val uid = uidRequerido()
        // Solo se puede modificar lo que pertenece a la cuenta activa.
        val actual = dao.obtenerPorId(uid, id) ?: return
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
        // Al iniciar sesion (o cambiar de cuenta) se sincroniza para traer los datos de la nube.
        alcance.launch {
            sesion.estado
                .map { (it as? EstadoAuth.ConSesion)?.usuario?.uid }
                .distinctUntilChanged()
                .filter { it != null }
                .collect { solicitarSincronizacion() }
        }
    }

    override fun solicitarSincronizacion(reintentarErrores: Boolean) {
        if (reintentarErrores) reintentarErroresPendiente.set(true)
        solicitudes.trySend(Unit)
    }

    override suspend fun sincronizar(reintentarErrores: Boolean): ResultadoSincronizacion {
        // El uid se captura una vez: toda la sincronizacion trabaja con la misma cuenta.
        val uid = sesion.uidActual() ?: return ResultadoSincronizacion.SinSesion
        if (!mutex.tryLock()) return ResultadoSincronizacion.YaEnCurso
        try {
            _estadoSincronizacion.value = EstadoSincronizacion.Sincronizando
            if (reintentarErrores) dao.reactivarErrores(uid)

            enviarPendientes(uid)?.let { return fallo(it) }
            descargarRemotos(uid)?.let { return fallo(it) }

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
     * (sin red, timeout, falla del servicio o sesion invalida); los registros quedan pendientes.
     * Si la nube rechaza un registro (datos invalidos o sin permiso) se marca ERROR y se
     * continua con el resto.
     */
    private suspend fun enviarPendientes(uid: String): EstadoSincronizacion.Error? {
        for (entidad in dao.obtenerPendientes(uid)) {
            val operacion = entidad.operacionPendiente
            val resultado: ResultadoRed<*> = when (operacion) {
                CodigoOperacion.CREAR, CodigoOperacion.ACTUALIZAR -> remoto.guardar(uid, entidad.toDto())
                CodigoOperacion.ELIMINAR -> remoto.eliminar(uid, entidad.id)
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
                is ResultadoRed.ErrorRemoto -> when (resultado.tipo) {
                    TipoErrorRemoto.SIN_PERMISO -> dao.marcarError(
                        entidad.id, entidad.version,
                        "La cuenta no tiene permiso para modificar este registro."
                    )
                    TipoErrorRemoto.SOLICITUD_INVALIDA, TipoErrorRemoto.NO_ENCONTRADO -> dao.marcarError(
                        entidad.id, entidad.version,
                        "La nube rechazo el cambio (${resultado.codigo})."
                    )
                    TipoErrorRemoto.NO_AUTENTICADO -> return sesionInvalida()
                    TipoErrorRemoto.SERVIDOR, TipoErrorRemoto.OTRO -> return errorServidor(resultado.codigo)
                }
                ResultadoRed.Timeout -> return errorTimeout()
                ResultadoRed.SinConexion -> return errorSinConexion(guardado = true)
                is ResultadoRed.ErrorDesconocido -> return errorDesconocido()
            }
        }
        return null
    }

    /** Descarga el estado de la nube y lo incorpora a Room, sin pisar cambios locales pendientes. */
    private suspend fun descargarRemotos(uid: String): EstadoSincronizacion.Error? {
        val dtos = when (val respuesta = remoto.obtenerTodos(uid)) {
            is ResultadoRed.Exito -> respuesta.valor
            is ResultadoRed.ErrorRemoto -> return when (respuesta.tipo) {
                TipoErrorRemoto.NO_AUTENTICADO -> sesionInvalida()
                TipoErrorRemoto.SIN_PERMISO -> EstadoSincronizacion.Error(
                    TipoErrorSync.SIN_PERMISO,
                    "La cuenta no tiene permiso para leer estos datos. Se muestran los datos locales."
                )
                else -> errorServidor(respuesta.codigo)
            }
            ResultadoRed.Timeout -> return errorTimeout()
            ResultadoRed.SinConexion -> return errorSinConexion(guardado = false)
            is ResultadoRed.ErrorDesconocido -> return errorDesconocido()
        }

        val ahora = reloj()
        val remotos = dtos.mapNotNull { it.toEntityOrNull(uid, ahora) }
        transacciones.ejecutar {
            val locales = dao.obtenerTodosParaSync(uid).associateBy { it.id }
            val idsRemotos = remotos.map { it.id }.toSet()

            val aGuardar = remotos.filter { remota ->
                val local = locales[remota.id]
                local == null || local.estadoSync == CodigoSync.SINCRONIZADO
            }
            val aEliminar = locales.values
                .filter { it.estadoSync == CodigoSync.SINCRONIZADO && it.id !in idsRemotos }
                .map { it.id }

            dao.insertarTodos(aGuardar)
            if (aEliminar.isNotEmpty()) dao.eliminarPorIds(uid, aEliminar)
        }
        return null
    }

    // ---------- Mensajes de error (sin detalles del proveedor) ----------

    private fun errorSinConexion(guardado: Boolean) = EstadoSincronizacion.Error(
        TipoErrorSync.SIN_CONEXION,
        if (guardado) {
            "Sin conexión. Los cambios se guardaron en el dispositivo y se enviarán cuando vuelva la red."
        } else {
            "Sin conexión. Se muestran los datos locales."
        }
    )

    private fun errorTimeout() = EstadoSincronizacion.Error(
        TipoErrorSync.TIMEOUT,
        "El servicio tardó demasiado en responder. Los cambios siguen guardados en el dispositivo."
    )

    private fun errorServidor(codigo: String) = EstadoSincronizacion.Error(
        TipoErrorSync.SERVIDOR,
        "El servicio no está disponible ($codigo). Se reintentará más tarde."
    )

    private fun errorDesconocido() = EstadoSincronizacion.Error(
        TipoErrorSync.DESCONOCIDO,
        "Ocurrió un error inesperado al sincronizar."
    )

    /** La nube dice que no hay una identidad valida: se comprueba la sesion y, si no sirve, se cierra. */
    private suspend fun sesionInvalida(): EstadoSincronizacion.Error {
        sesion.verificarSesion()
        return EstadoSincronizacion.Error(
            TipoErrorSync.SESION_INVALIDA,
            "La sesión ya no es válida. Iniciá sesión nuevamente."
        )
    }

    // ---------- Cierre de sesion ----------

    override suspend fun contarSinSincronizar(): Int {
        val uid = sesion.uidActual() ?: return 0
        return dao.contarSinSincronizar(uid)
    }

    override suspend fun prepararCierreDeSesion(): Int {
        val uid = sesion.uidActual() ?: return 0
        // Si hay una sincronizacion en curso se espera a que termine (hasta unos segundos).
        var intentos = 0
        while (sincronizar() == ResultadoSincronizacion.YaEnCurso && intentos < ESPERA_MAXIMA_INTENTOS) {
            delay(ESPERA_ENTRE_INTENTOS_MS)
            intentos++
        }
        dao.purgarSincronizados(uid)
        return dao.contarSinSincronizar(uid)
    }

    private companion object {
        const val CONECTIVIDAD_ESTABLE_MS = 500L
        const val ESPERA_ENTRE_INTENTOS_MS = 500L
        const val ESPERA_MAXIMA_INTENTOS = 10
    }
}
