package com.mansilla.recordatoriosapp

import com.mansilla.recordatoriosapp.data.auth.AuthRepository
import com.mansilla.recordatoriosapp.data.auth.EstadoAuth
import com.mansilla.recordatoriosapp.data.auth.ResultadoAuth
import com.mansilla.recordatoriosapp.data.auth.ResultadoVerificacion
import com.mansilla.recordatoriosapp.data.auth.UsuarioSesion
import com.mansilla.recordatoriosapp.data.local.CodigoOperacion
import com.mansilla.recordatoriosapp.data.local.CodigoSync
import com.mansilla.recordatoriosapp.data.local.RecordatorioDao
import com.mansilla.recordatoriosapp.data.local.RecordatorioEntity
import com.mansilla.recordatoriosapp.data.remote.RecordatorioDto
import com.mansilla.recordatoriosapp.data.remote.RecordatorioRemoteDataSource
import com.mansilla.recordatoriosapp.data.remote.ResultadoRed
import com.mansilla.recordatoriosapp.data.remote.TipoErrorRemoto
import com.mansilla.recordatoriosapp.data.repository.EjecutorTransacciones
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** DAO en memoria con el mismo comportamiento que las consultas de Room (filtradas por cuenta). */
class FakeDao : RecordatorioDao {
    val datos = MutableStateFlow<Map<String, RecordatorioEntity>>(emptyMap())

    private fun visible(e: RecordatorioEntity) = e.operacionPendiente != CodigoOperacion.ELIMINAR
    private fun deCuenta(uid: String) = datos.map { m -> m.values.filter { it.ownerUid == uid } }

    /** Filas de una cuenta, tal como estan en la tabla. */
    fun filasDe(uid: String) = datos.value.values.filter { it.ownerUid == uid }

    override fun observarTodos(uid: String): Flow<List<RecordatorioEntity>> =
        deCuenta(uid).map { l -> l.filter(::visible).sortedByDescending { it.fecha } }

    override fun observarPorId(uid: String, id: String): Flow<RecordatorioEntity?> =
        deCuenta(uid).map { l -> l.firstOrNull { it.id == id && visible(it) } }

    override fun observarPorEstado(uid: String, estado: String): Flow<List<RecordatorioEntity>> =
        deCuenta(uid).map { l -> l.filter { visible(it) && it.estado == estado } }

    override fun observarFiltrados(
        uid: String, prioridad: String?, incluirCompletados: Boolean
    ): Flow<List<RecordatorioEntity>> =
        deCuenta(uid).map { l ->
            l.filter {
                visible(it) &&
                    (prioridad == null || it.prioridad == prioridad) &&
                    (incluirCompletados || it.estado != "COMPLETADO")
            }.sortedByDescending { it.fecha }
        }

    override fun observarCantidadPendientes(uid: String): Flow<Int> =
        deCuenta(uid).map { l -> l.count { it.estadoSync == CodigoSync.PENDIENTE } }

    override fun observarCantidadConError(uid: String): Flow<Int> =
        deCuenta(uid).map { l -> l.count { it.estadoSync == CodigoSync.ERROR } }

    override suspend fun insertar(entidad: RecordatorioEntity) {
        datos.update { it + (entidad.id to entidad) }
    }

    override suspend fun insertarTodos(entidades: List<RecordatorioEntity>) {
        datos.update { it + entidades.associateBy { e -> e.id } }
    }

    override suspend fun actualizar(entidad: RecordatorioEntity) {
        datos.update { it + (entidad.id to entidad) }
    }

    override suspend fun eliminar(entidad: RecordatorioEntity) {
        datos.update { it - entidad.id }
    }

    override suspend fun eliminarPorIds(uid: String, ids: List<String>) {
        datos.update { m -> m.filterNot { (id, e) -> e.ownerUid == uid && id in ids } }
    }

    override suspend fun obtenerPorId(uid: String, id: String) =
        datos.value[id]?.takeIf { it.ownerUid == uid }

    override suspend fun obtenerTodosParaSync(uid: String) = filasDe(uid)

    override suspend fun obtenerPendientes(uid: String) =
        filasDe(uid).filter { it.estadoSync == CodigoSync.PENDIENTE }.sortedBy { it.actualizadoEn }

    override suspend fun marcarSincronizado(id: String, version: Int): Int = cambiarSiVersion(id, version) {
        it.copy(estadoSync = CodigoSync.SINCRONIZADO, operacionPendiente = null, ultimoError = null)
    }

    override suspend fun eliminarSiVersion(id: String, version: Int): Int {
        val actual = datos.value[id] ?: return 0
        if (actual.version != version) return 0
        datos.update { it - id }
        return 1
    }

    override suspend fun marcarError(id: String, version: Int, mensaje: String): Int =
        cambiarSiVersion(id, version) { it.copy(estadoSync = CodigoSync.ERROR, ultimoError = mensaje) }

    override suspend fun reactivarErrores(uid: String): Int {
        var cambios = 0
        datos.update { m ->
            m.mapValues { (_, e) ->
                if (e.ownerUid == uid && e.estadoSync == CodigoSync.ERROR) {
                    cambios++
                    e.copy(estadoSync = CodigoSync.PENDIENTE, ultimoError = null)
                } else e
            }
        }
        return cambios
    }

    override suspend fun purgarSincronizados(uid: String): Int {
        val antes = datos.value.size
        datos.update { m -> m.filterNot { (_, e) -> e.ownerUid == uid && e.estadoSync == CodigoSync.SINCRONIZADO } }
        return antes - datos.value.size
    }

    override suspend fun contarSinSincronizar(uid: String) =
        filasDe(uid).count { it.estadoSync != CodigoSync.SINCRONIZADO }

    private fun cambiarSiVersion(id: String, version: Int, cambio: (RecordatorioEntity) -> RecordatorioEntity): Int {
        val actual = datos.value[id] ?: return 0
        if (actual.version != version) return 0
        datos.update { it + (id to cambio(actual)) }
        return 1
    }
}

object TransaccionDirecta : EjecutorTransacciones {
    override suspend fun <T> ejecutar(bloque: suspend () -> T): T = bloque()
}

/** Sesion falsa: permite iniciar y cerrar sesion de distintas cuentas en la prueba. */
class FakeSesion(uidInicial: String? = "ana") : AuthRepository {
    private val _estado = MutableStateFlow<EstadoAuth>(estadoDe(uidInicial))
    override val estado: StateFlow<EstadoAuth> = _estado

    var resultadoVerificacion = ResultadoVerificacion.VALIDA
    var verificaciones = 0
    var resultadoAuth: ResultadoAuth = ResultadoAuth.Exito
    var intentosDeAcceso = 0
    var cierres = 0

    /** Si se define, iniciarSesion espera a que se complete (para observar el estado "en curso"). */
    var compuerta: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    private fun estadoDe(uid: String?): EstadoAuth =
        if (uid == null) EstadoAuth.SinSesion() else EstadoAuth.ConSesion(UsuarioSesion(uid, "$uid@test.com"))

    fun iniciar(uid: String) { _estado.value = estadoDe(uid) }
    fun cerrar() { _estado.value = EstadoAuth.SinSesion() }

    override suspend fun verificarSesion(): ResultadoVerificacion {
        verificaciones++
        if (resultadoVerificacion == ResultadoVerificacion.INVALIDA) _estado.value = EstadoAuth.SinSesion(expirada = true)
        return resultadoVerificacion
    }

    override suspend fun registrar(email: String, contrasena: String): ResultadoAuth {
        intentosDeAcceso++
        return resultadoAuth
    }

    override suspend fun iniciarSesion(email: String, contrasena: String): ResultadoAuth {
        intentosDeAcceso++
        compuerta?.await()
        return resultadoAuth
    }

    override suspend fun cerrarSesion() {
        cierres++
        cerrar()
    }
}

enum class ModoFake { NORMAL, SIN_CONEXION, TIMEOUT, ERROR_SERVIDOR, SOLICITUD_INVALIDA, SIN_PERMISO, NO_AUTENTICADO, PERDER_RESPUESTA }

/**
 * Nube falsa con un "servidor" por cuenta. Es idempotente por id, igual que Firestore:
 * guardar dos veces el mismo id reemplaza el documento, no crea otro.
 */
class FakeRemote : RecordatorioRemoteDataSource {
    val servidores = mutableMapOf<String, LinkedHashMap<String, RecordatorioDto>>()
    var modo = ModoFake.NORMAL
    var latenciaMs = 0L
    var llamadasGuardar = 0
    var llamadasEliminar = 0
    var llamadasObtener = 0
    val uidsUsados = mutableListOf<String>()

    /** Atajo para la cuenta "ana". */
    val servidor: LinkedHashMap<String, RecordatorioDto> get() = servidorDe("ana")
    fun servidorDe(uid: String): LinkedHashMap<String, RecordatorioDto> = servidores.getOrPut(uid) { linkedMapOf() }

    /** Permite simular un cambio del usuario justo mientras se envia un registro. */
    var alRecibirGuardar: suspend () -> Unit = {}

    private fun <T> falla(): ResultadoRed<T>? = when (modo) {
        ModoFake.SIN_CONEXION -> ResultadoRed.SinConexion
        ModoFake.TIMEOUT -> ResultadoRed.Timeout
        ModoFake.ERROR_SERVIDOR -> ResultadoRed.ErrorRemoto("UNAVAILABLE", TipoErrorRemoto.SERVIDOR, "")
        ModoFake.SOLICITUD_INVALIDA -> ResultadoRed.ErrorRemoto("INVALID_ARGUMENT", TipoErrorRemoto.SOLICITUD_INVALIDA, "")
        ModoFake.SIN_PERMISO -> ResultadoRed.ErrorRemoto("PERMISSION_DENIED", TipoErrorRemoto.SIN_PERMISO, "")
        ModoFake.NO_AUTENTICADO -> ResultadoRed.ErrorRemoto("UNAUTHENTICATED", TipoErrorRemoto.NO_AUTENTICADO, "")
        else -> null
    }

    override suspend fun obtenerTodos(uid: String): ResultadoRed<List<RecordatorioDto>> {
        llamadasObtener++
        uidsUsados += uid
        falla<List<RecordatorioDto>>()?.let { return it }
        return ResultadoRed.Exito(servidorDe(uid).values.toList())
    }

    override suspend fun guardar(uid: String, dto: RecordatorioDto): ResultadoRed<Unit> {
        llamadasGuardar++
        uidsUsados += uid
        if (latenciaMs > 0) delay(latenciaMs)
        falla<Unit>()?.let { return it }
        servidorDe(uid)[dto.id!!] = dto
        alRecibirGuardar()
        if (modo == ModoFake.PERDER_RESPUESTA) return ResultadoRed.Timeout
        return ResultadoRed.Exito(Unit)
    }

    override suspend fun eliminar(uid: String, id: String): ResultadoRed<Unit> {
        llamadasEliminar++
        uidsUsados += uid
        falla<Unit>()?.let { return it }
        servidorDe(uid).remove(id)
        if (modo == ModoFake.PERDER_RESPUESTA) return ResultadoRed.Timeout
        return ResultadoRed.Exito(Unit)
    }
}
