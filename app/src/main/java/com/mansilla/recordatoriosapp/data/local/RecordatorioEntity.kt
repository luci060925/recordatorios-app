package com.mansilla.recordatoriosapp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Valores guardados en la columna estadoSync. */
object CodigoSync {
    const val SINCRONIZADO = "SINCRONIZADO"
    const val PENDIENTE = "PENDIENTE"
    const val ERROR = "ERROR"
}

/** Valores guardados en la columna operacionPendiente. */
object CodigoOperacion {
    const val CREAR = "CREAR"
    const val ACTUALIZAR = "ACTUALIZAR"
    const val ELIMINAR = "ELIMINAR"
}

/**
 * Entidad de Room: responde al esquema de la tabla y no se expone a la interfaz.
 *
 * - [id]: UUID estable generado en el dispositivo (tambien identifica al registro en el servicio).
 * - [version]: aumenta con cada cambio local; permite confirmar una sincronizacion
 *   solo si el registro no cambio mientras se enviaba.
 * - [estadoSync] y [operacionPendiente]: marcan que falta enviar al servicio.
 *   Una eliminacion queda como operacion pendiente (baja logica) hasta confirmarse.
 */
@Entity(
    tableName = "recordatorios",
    indices = [Index("estadoSync"), Index("prioridad"), Index("estado")]
)
data class RecordatorioEntity(
    @PrimaryKey val id: String,
    val titulo: String,
    val descripcion: String?,
    val categoria: String,
    val prioridad: String,
    val estado: String,
    val fecha: Long,
    val actualizadoEn: Long,
    val version: Int,
    val estadoSync: String,
    val operacionPendiente: String?,
    val ultimoError: String?
)
