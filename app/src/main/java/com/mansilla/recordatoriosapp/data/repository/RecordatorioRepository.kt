package com.mansilla.recordatoriosapp.data.repository

import com.mansilla.recordatoriosapp.Categoria
import com.mansilla.recordatoriosapp.EstadoRecordatorio
import com.mansilla.recordatoriosapp.Prioridad
import com.mansilla.recordatoriosapp.Recordatorio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Datos editables de un recordatorio (lo que completa la persona en el formulario). */
data class DatosRecordatorio(
    val titulo: String,
    val descripcion: String?,
    val categoria: Categoria,
    val prioridad: Prioridad
)

/** Criterios de consulta del listado (provienen de las preferencias). */
data class FiltroRecordatorios(
    val prioridad: Prioridad? = null,
    val incluirCompletados: Boolean = true
)

enum class TipoErrorSync { SIN_CONEXION, TIMEOUT, SERVIDOR, SOLICITUD_INVALIDA, DESCONOCIDO }

/** Estado de la sincronizacion con el servicio remoto, observable desde la interfaz. */
sealed interface EstadoSincronizacion {
    data object Inactiva : EstadoSincronizacion
    data object Sincronizando : EstadoSincronizacion
    data class Exito(val instante: Long) : EstadoSincronizacion
    data class Error(val tipo: TipoErrorSync, val mensaje: String) : EstadoSincronizacion
}

sealed interface ResultadoSincronizacion {
    data object Completada : ResultadoSincronizacion
    data object YaEnCurso : ResultadoSincronizacion
    data class Fallida(val tipo: TipoErrorSync, val mensaje: String) : ResultadoSincronizacion
}

/** Cantidad de registros que todavia no coinciden con el servicio. */
data class ResumenPendientes(val pendientes: Int = 0, val conError: Int = 0)

/**
 * Unico punto de acceso a los datos para los ViewModel. Room es la fuente de verdad:
 * toda lectura sale de la base local y toda escritura se aplica primero en ella.
 * La conectividad y los DTO quedan encapsulados en la implementacion.
 */
interface RecordatorioRepository {

    // ---------- Lecturas (siempre desde Room) ----------
    fun observarRecordatorios(filtro: FiltroRecordatorios = FiltroRecordatorios()): Flow<List<Recordatorio>>
    fun observarPorId(id: String): Flow<Recordatorio?>
    fun observarPorEstado(estado: EstadoRecordatorio): Flow<List<Recordatorio>>
    fun observarResumenPendientes(): Flow<ResumenPendientes>

    // ---------- Escrituras (se guardan en Room y quedan pendientes de sincronizar) ----------
    /** Crea un recordatorio y devuelve su identificador. */
    suspend fun crear(datos: DatosRecordatorio): String
    suspend fun actualizar(id: String, datos: DatosRecordatorio)
    suspend fun cambiarEstado(id: String, estado: EstadoRecordatorio)
    suspend fun eliminar(id: String)

    // ---------- Sincronizacion ----------
    val estadoSincronizacion: StateFlow<EstadoSincronizacion>

    /**
     * Solicita una sincronizacion en segundo plano. Varias solicitudes seguidas se
     * combinan en una sola, de modo que nunca corren dos sincronizaciones a la vez.
     */
    fun solicitarSincronizacion(reintentarErrores: Boolean = false)

    /** Ejecuta una sincronizacion; si ya hay una en curso devuelve YaEnCurso sin repetir el envio. */
    suspend fun sincronizar(reintentarErrores: Boolean = false): ResultadoSincronizacion
}
