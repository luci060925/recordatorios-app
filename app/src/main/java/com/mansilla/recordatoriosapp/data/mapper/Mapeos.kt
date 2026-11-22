package com.mansilla.recordatoriosapp.data.mapper

import com.mansilla.recordatoriosapp.Categoria
import com.mansilla.recordatoriosapp.EstadoRecordatorio
import com.mansilla.recordatoriosapp.EstadoSync
import com.mansilla.recordatoriosapp.Prioridad
import com.mansilla.recordatoriosapp.Recordatorio
import com.mansilla.recordatoriosapp.data.local.CodigoOperacion
import com.mansilla.recordatoriosapp.data.local.CodigoSync
import com.mansilla.recordatoriosapp.data.local.RecordatorioEntity
import com.mansilla.recordatoriosapp.data.remote.RecordatorioDto

// Mapeos explicitos entre las tres capas:
//   DTO (contrato del servicio) <-> Entidad (esquema de Room) <-> Dominio (interfaz)
// Los valores desconocidos recibidos desde afuera usan un valor predeterminado seguro.

// ---------- Codigos de texto de los tipos del dominio ----------

fun Categoria.codigo(): String = when (this) {
    Categoria.Estudio -> "ESTUDIO"
    Categoria.Trabajo -> "TRABAJO"
    Categoria.Personal -> "PERSONAL"
}

fun Prioridad.codigo(): String = when (this) {
    Prioridad.Alta -> "ALTA"
    Prioridad.Media -> "MEDIA"
    Prioridad.Baja -> "BAJA"
}

fun EstadoRecordatorio.codigo(): String = when (this) {
    EstadoRecordatorio.Pendiente -> "PENDIENTE"
    EstadoRecordatorio.Completado -> "COMPLETADO"
    EstadoRecordatorio.Cancelado -> "CANCELADO"
}

fun categoriaDesdeCodigo(codigo: String?): Categoria = when (codigo?.uppercase()) {
    "ESTUDIO" -> Categoria.Estudio
    "TRABAJO" -> Categoria.Trabajo
    else -> Categoria.Personal
}

fun prioridadDesdeCodigo(codigo: String?): Prioridad = when (codigo?.uppercase()) {
    "ALTA" -> Prioridad.Alta
    "BAJA" -> Prioridad.Baja
    else -> Prioridad.Media
}

fun estadoDesdeCodigo(codigo: String?): EstadoRecordatorio = when (codigo?.uppercase()) {
    "COMPLETADO" -> EstadoRecordatorio.Completado
    "CANCELADO" -> EstadoRecordatorio.Cancelado
    else -> EstadoRecordatorio.Pendiente
}

fun estadoSyncDesdeCodigo(codigo: String?): EstadoSync = when (codigo) {
    CodigoSync.SINCRONIZADO -> EstadoSync.SINCRONIZADO
    CodigoSync.ERROR -> EstadoSync.ERROR
    else -> EstadoSync.PENDIENTE
}

// ---------- Entidad -> Dominio ----------

fun RecordatorioEntity.toDomain(): Recordatorio = Recordatorio(
    id = id,
    titulo = titulo,
    descripcion = descripcion,
    categoria = categoriaDesdeCodigo(categoria),
    prioridad = prioridadDesdeCodigo(prioridad),
    estado = estadoDesdeCodigo(estado),
    fecha = fecha,
    estadoSync = estadoSyncDesdeCodigo(estadoSync)
)

// ---------- Entidad -> DTO (envio al servicio) ----------

fun RecordatorioEntity.toDto(): RecordatorioDto = RecordatorioDto(
    id = id,
    titulo = titulo,
    descripcion = descripcion,
    categoria = categoria,
    prioridad = prioridad,
    estado = estado,
    fecha = fecha,
    actualizadoEn = actualizadoEn
)

// ---------- DTO -> Entidad (datos recibidos de la nube, asociados a la cuenta uid) ----------

/**
 * Convierte un DTO recibido en una entidad ya sincronizada. Devuelve null si el
 * registro no tiene los datos minimos (identificador y titulo), para descartarlo.
 */
fun RecordatorioDto.toEntityOrNull(uid: String, ahora: Long): RecordatorioEntity? {
    val idRecibido = id?.takeIf { it.isNotBlank() } ?: return null
    val tituloRecibido = titulo?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return RecordatorioEntity(
        id = idRecibido,
        ownerUid = uid,
        titulo = tituloRecibido,
        descripcion = descripcion?.trim()?.takeIf { it.isNotEmpty() },
        categoria = categoriaDesdeCodigo(categoria).codigo(),
        prioridad = prioridadDesdeCodigo(prioridad).codigo(),
        estado = estadoDesdeCodigo(estado).codigo(),
        fecha = fecha ?: ahora,
        actualizadoEn = actualizadoEn ?: fecha ?: ahora,
        version = 0,
        estadoSync = CodigoSync.SINCRONIZADO,
        operacionPendiente = null,
        ultimoError = null
    )
}
