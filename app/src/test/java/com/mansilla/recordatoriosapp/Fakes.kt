package com.mansilla.recordatoriosapp

import com.mansilla.recordatoriosapp.data.local.CodigoOperacion
import com.mansilla.recordatoriosapp.data.local.CodigoSync
import com.mansilla.recordatoriosapp.data.local.RecordatorioDao
import com.mansilla.recordatoriosapp.data.local.RecordatorioEntity
import com.mansilla.recordatoriosapp.data.remote.RecordatorioDto
import com.mansilla.recordatoriosapp.data.remote.RecordatorioRemoteDataSource
import com.mansilla.recordatoriosapp.data.remote.ResultadoRed
import com.mansilla.recordatoriosapp.data.remote.TipoErrorHttp
import com.mansilla.recordatoriosapp.data.repository.EjecutorTransacciones
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** DAO en memoria con el mismo comportamiento que las consultas de Room. */
class FakeDao : RecordatorioDao {
    val datos = MutableStateFlow<Map<String, RecordatorioEntity>>(emptyMap())

    private fun visible(e: RecordatorioEntity) = e.operacionPendiente != CodigoOperacion.ELIMINAR

    override fun observarTodos(): Flow<List<RecordatorioEntity>> =
        datos.map { m -> m.values.filter(::visible).sortedByDescending { it.fecha } }

    override fun observarPorId(id: String): Flow<RecordatorioEntity?> =
        datos.map { m -> m[id]?.takeIf(::visible) }

    override fun observarPorEstado(estado: String): Flow<List<RecordatorioEntity>> =
        datos.map { m -> m.values.filter { visible(it) && it.estado == estado } }

    override fun observarFiltrados(prioridad: String?, incluirCompletados: Boolean): Flow<List<RecordatorioEntity>> =
        datos.map { m ->
            m.values.filter {
                visible(it) &&
                    (prioridad == null || it.prioridad == prioridad) &&
                    (incluirCompletados || it.estado != "COMPLETADO")
            }.sortedByDescending { it.fecha }
        }

    override fun observarCantidadPendientes(): Flow<Int> =
        datos.map { m -> m.values.count { it.estadoSync == CodigoSync.PENDIENTE } }

    override fun observarCantidadConError(): Flow<Int> =
        datos.map { m -> m.values.count { it.estadoSync == CodigoSync.ERROR } }

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

    override suspend fun eliminarPorIds(ids: List<String>) {
        datos.update { it - ids.toSet() }
    }

    override suspend fun obtenerPorId(id: String) = datos.value[id]

    override suspend fun obtenerTodosParaSync() = datos.value.values.toList()

    override suspend fun obtenerPendientes() =
        datos.value.values.filter { it.estadoSync == CodigoSync.PENDIENTE }.sortedBy { it.actualizadoEn }

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

    override suspend fun reactivarErrores(): Int {
        var cambios = 0
        datos.update { m ->
            m.mapValues { (_, e) ->
                if (e.estadoSync == CodigoSync.ERROR) {
                    cambios++
                    e.copy(estadoSync = CodigoSync.PENDIENTE, ultimoError = null)
                } else e
            }
        }
        return cambios
    }

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

enum class ModoFake { NORMAL, SIN_CONEXION, TIMEOUT, ERROR_500, ERROR_400, PERDER_RESPUESTA }

/**
 * Servicio remoto falso con un "servidor" en memoria. Es idempotente por id, igual que el
 * contrato real: repetir un POST actualiza el registro existente, no crea otro.
 */
class FakeRemote : RecordatorioRemoteDataSource {
    val servidor = linkedMapOf<String, RecordatorioDto>()
    var modo = ModoFake.NORMAL
    var latenciaMs = 0L
    var llamadasCrear = 0
    var llamadasActualizar = 0
    var llamadasEliminar = 0
    var llamadasObtener = 0

    /** Permite simular un cambio del usuario justo mientras se envia un registro. */
    var alRecibirCrear: suspend () -> Unit = {}

    private fun <T> falla(): ResultadoRed<T>? = when (modo) {
        ModoFake.SIN_CONEXION -> ResultadoRed.SinConexion
        ModoFake.TIMEOUT -> ResultadoRed.Timeout
        ModoFake.ERROR_500 -> ResultadoRed.ErrorHttp(500, TipoErrorHttp.SERVIDOR, "500")
        ModoFake.ERROR_400 -> ResultadoRed.ErrorHttp(400, TipoErrorHttp.SOLICITUD_INVALIDA, "400")
        else -> null
    }

    override suspend fun obtenerTodos(): ResultadoRed<List<RecordatorioDto>> {
        llamadasObtener++
        falla<List<RecordatorioDto>>()?.let { return it }
        return ResultadoRed.Exito(servidor.values.toList())
    }

    override suspend fun crear(dto: RecordatorioDto): ResultadoRed<RecordatorioDto> {
        llamadasCrear++
        if (latenciaMs > 0) delay(latenciaMs)
        falla<RecordatorioDto>()?.let { return it }
        servidor[dto.id!!] = dto
        alRecibirCrear()
        if (modo == ModoFake.PERDER_RESPUESTA) return ResultadoRed.Timeout
        return ResultadoRed.Exito(dto)
    }

    override suspend fun actualizar(dto: RecordatorioDto): ResultadoRed<RecordatorioDto> {
        llamadasActualizar++
        falla<RecordatorioDto>()?.let { return it }
        if (!servidor.containsKey(dto.id)) {
            return ResultadoRed.ErrorHttp(404, TipoErrorHttp.NO_ENCONTRADO, "404")
        }
        servidor[dto.id!!] = dto
        if (modo == ModoFake.PERDER_RESPUESTA) return ResultadoRed.Timeout
        return ResultadoRed.Exito(dto)
    }

    override suspend fun eliminar(id: String): ResultadoRed<Unit> {
        llamadasEliminar++
        falla<Unit>()?.let { return it }
        val existia = servidor.remove(id) != null
        if (modo == ModoFake.PERDER_RESPUESTA) return ResultadoRed.Timeout
        return if (existia) ResultadoRed.Exito(Unit)
        else ResultadoRed.ErrorHttp(404, TipoErrorHttp.NO_ENCONTRADO, "404")
    }
}
