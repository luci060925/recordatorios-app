package com.mansilla.recordatoriosapp

sealed class Prioridad {
    data object Alta : Prioridad()
    data object Media : Prioridad()
    data object Baja : Prioridad()
}

sealed class Categoria {
    data object Estudio : Categoria()
    data object Trabajo : Categoria()
    data object Personal : Categoria()
}

sealed class EstadoRecordatorio {
    data object Pendiente : EstadoRecordatorio()
    data object Completado : EstadoRecordatorio()
    data object Cancelado : EstadoRecordatorio()
}

/** Situacion del registro respecto del servicio remoto. */
enum class EstadoSync {
    /** Coincide con el servicio remoto. */
    SINCRONIZADO,

    /** Guardado solo en el dispositivo; se enviara cuando haya conexion. */
    PENDIENTE,

    /** El servicio rechazo la operacion; requiere un nuevo intento manual. */
    ERROR
}

/**
 * Modelo de dominio: es lo unico que llega a ViewModels y pantallas.
 * No conoce DTO ni entidades de Room.
 *
 * El [id] es un UUID generado en el dispositivo: es estable desde que se crea
 * el registro y sirve como clave de idempotencia ante el servicio remoto.
 */
data class Recordatorio(
    val id: String,
    val titulo: String,
    val descripcion: String?,
    val categoria: Categoria,
    val prioridad: Prioridad,
    val estado: EstadoRecordatorio,
    /** Fecha de creacion, en milisegundos desde epoch. */
    val fecha: Long,
    val estadoSync: EstadoSync = EstadoSync.SINCRONIZADO
) {
    init {
        require(id.isNotBlank()) { "El ID no puede estar vacio" }
        require(titulo.isNotBlank()) { "El titulo no puede estar vacio" }
    }
}
