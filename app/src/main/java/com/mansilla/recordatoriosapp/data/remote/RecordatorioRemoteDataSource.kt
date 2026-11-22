package com.mansilla.recordatoriosapp.data.remote

/**
 * Fuente remota de recordatorios de UNA cuenta. Todas las operaciones reciben el uid
 * explicitamente: una sincronizacion nunca mezcla cuentas aunque la sesion cambie mientras corre.
 * Devuelve siempre un [ResultadoRed]; nunca lanza excepciones del proveedor.
 */
interface RecordatorioRemoteDataSource {
    suspend fun obtenerTodos(uid: String): ResultadoRed<List<RecordatorioDto>>

    /** Crea el recordatorio o, si ya existe con ese id, lo reemplaza (idempotente). */
    suspend fun guardar(uid: String, dto: RecordatorioDto): ResultadoRed<Unit>

    /** Elimina el recordatorio; si ya no existe tambien es exito. */
    suspend fun eliminar(uid: String, id: String): ResultadoRed<Unit>
}
