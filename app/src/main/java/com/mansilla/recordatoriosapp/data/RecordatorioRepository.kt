package com.mansilla.recordatoriosapp.data

import com.mansilla.recordatoriosapp.Recordatorio

/**
 * Fuente de datos de recordatorios. El ViewModel depende solo de esta
 * interfaz, de modo que la implementacion en memoria pueda reemplazarse
 * mas adelante (base de datos, servicio remoto) sin tocar las pantallas.
 */
interface RecordatorioRepository {
    suspend fun obtenerRecordatorios(): List<Recordatorio>

    suspend fun obtenerPorId(id: Int): Recordatorio?

    suspend fun guardarRecordatorio(recordatorio: Recordatorio)
}
