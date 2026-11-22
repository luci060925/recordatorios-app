package com.mansilla.recordatoriosapp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a la tabla de recordatorios. Solo lo usa el repositorio: ni las pantallas ni los
 * ViewModel acceden al DAO.
 *
 * Todas las consultas reciben el uid de la cuenta (ownerUid): los datos de una cuenta son
 * invisibles para las demas aunque compartan el dispositivo.
 * Las consultas que consume la interfaz devuelven Flow, por lo que Room vuelve a emitir
 * cada vez que cambia la tabla.
 */
@Dao
interface RecordatorioDao {

    // ---------- Consultas observables (interfaz) ----------

    /** Listado completo de la cuenta, sin los registros marcados para eliminar. */
    @Query(
        "SELECT * FROM recordatorios WHERE ownerUid = :uid " +
            "AND (operacionPendiente IS NULL OR operacionPendiente != 'ELIMINAR') " +
            "ORDER BY fecha DESC"
    )
    fun observarTodos(uid: String): Flow<List<RecordatorioEntity>>

    /** Un recordatorio por identificador (solo si pertenece a la cuenta). */
    @Query(
        "SELECT * FROM recordatorios WHERE ownerUid = :uid AND id = :id " +
            "AND (operacionPendiente IS NULL OR operacionPendiente != 'ELIMINAR')"
    )
    fun observarPorId(uid: String, id: String): Flow<RecordatorioEntity?>

    /** Filtro por estado (PENDIENTE, COMPLETADO, CANCELADO). */
    @Query(
        "SELECT * FROM recordatorios WHERE ownerUid = :uid AND estado = :estado " +
            "AND (operacionPendiente IS NULL OR operacionPendiente != 'ELIMINAR') " +
            "ORDER BY fecha DESC"
    )
    fun observarPorEstado(uid: String, estado: String): Flow<List<RecordatorioEntity>>

    /**
     * Filtro combinado que usan las preferencias: prioridad opcional (null = todas)
     * y posibilidad de ocultar los completados.
     */
    @Query(
        "SELECT * FROM recordatorios WHERE ownerUid = :uid " +
            "AND (operacionPendiente IS NULL OR operacionPendiente != 'ELIMINAR') " +
            "AND (:prioridad IS NULL OR prioridad = :prioridad) " +
            "AND (:incluirCompletados = 1 OR estado != 'COMPLETADO') " +
            "ORDER BY fecha DESC"
    )
    fun observarFiltrados(uid: String, prioridad: String?, incluirCompletados: Boolean): Flow<List<RecordatorioEntity>>

    @Query("SELECT COUNT(*) FROM recordatorios WHERE ownerUid = :uid AND estadoSync = 'PENDIENTE'")
    fun observarCantidadPendientes(uid: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM recordatorios WHERE ownerUid = :uid AND estadoSync = 'ERROR'")
    fun observarCantidadConError(uid: String): Flow<Int>

    // ---------- Operaciones de escritura ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(entidad: RecordatorioEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodos(entidades: List<RecordatorioEntity>)

    @Update
    suspend fun actualizar(entidad: RecordatorioEntity)

    @Delete
    suspend fun eliminar(entidad: RecordatorioEntity)

    @Query("DELETE FROM recordatorios WHERE ownerUid = :uid AND id IN (:ids)")
    suspend fun eliminarPorIds(uid: String, ids: List<String>)

    // ---------- Soporte de sincronizacion ----------

    @Query("SELECT * FROM recordatorios WHERE ownerUid = :uid AND id = :id")
    suspend fun obtenerPorId(uid: String, id: String): RecordatorioEntity?

    @Query("SELECT * FROM recordatorios WHERE ownerUid = :uid")
    suspend fun obtenerTodosParaSync(uid: String): List<RecordatorioEntity>

    /** Registros de la cuenta que falta enviar, del mas antiguo al mas nuevo. */
    @Query("SELECT * FROM recordatorios WHERE ownerUid = :uid AND estadoSync = 'PENDIENTE' ORDER BY actualizadoEn ASC")
    suspend fun obtenerPendientes(uid: String): List<RecordatorioEntity>

    /** Confirma el envio solo si el registro no cambio mientras se sincronizaba. */
    @Query(
        "UPDATE recordatorios SET estadoSync = 'SINCRONIZADO', operacionPendiente = NULL, " +
            "ultimoError = NULL WHERE id = :id AND version = :version"
    )
    suspend fun marcarSincronizado(id: String, version: Int): Int

    @Query("DELETE FROM recordatorios WHERE id = :id AND version = :version")
    suspend fun eliminarSiVersion(id: String, version: Int): Int

    @Query(
        "UPDATE recordatorios SET estadoSync = 'ERROR', ultimoError = :mensaje " +
            "WHERE id = :id AND version = :version"
    )
    suspend fun marcarError(id: String, version: Int, mensaje: String): Int

    /** Reintento manual: los registros rechazados vuelven a pendientes. */
    @Query("UPDATE recordatorios SET estadoSync = 'PENDIENTE', ultimoError = NULL WHERE ownerUid = :uid AND estadoSync = 'ERROR'")
    suspend fun reactivarErrores(uid: String): Int

    /**
     * Al cerrar sesion se borra de este dispositivo lo que ya esta guardado en la nube.
     * Lo que todavia no se sincronizo se conserva (para no perder cambios), siempre
     * asociado a su cuenta y oculto para cualquier otra.
     */
    @Query("DELETE FROM recordatorios WHERE ownerUid = :uid AND estadoSync = 'SINCRONIZADO'")
    suspend fun purgarSincronizados(uid: String): Int

    @Query("SELECT COUNT(*) FROM recordatorios WHERE ownerUid = :uid AND estadoSync != 'SINCRONIZADO'")
    suspend fun contarSinSincronizar(uid: String): Int
}
