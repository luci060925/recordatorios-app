package com.mansilla.recordatoriosapp.data.remote

/**
 * Conversion entre el DTO y el documento de Firestore. Son funciones puras (sin tipos del SDK)
 * para poder probarlas sin Firebase.
 *
 * El identificador del recordatorio es el id del documento: users/{uid}/reminders/{id}.
 * No se repite dentro de los campos ni se guarda el uid: la ruta ya identifica a la cuenta.
 */
object CamposFirestore {
    const val TITULO = "titulo"
    const val DESCRIPCION = "descripcion"
    const val CATEGORIA = "categoria"
    const val PRIORIDAD = "prioridad"
    const val ESTADO = "estado"
    const val FECHA = "fecha"
    const val ACTUALIZADO_EN = "actualizadoEn"
}

fun RecordatorioDto.aDocumentoFirestore(): Map<String, Any?> = mapOf(
    CamposFirestore.TITULO to titulo,
    CamposFirestore.DESCRIPCION to descripcion,
    CamposFirestore.CATEGORIA to categoria,
    CamposFirestore.PRIORIDAD to prioridad,
    CamposFirestore.ESTADO to estado,
    CamposFirestore.FECHA to fecha,
    CamposFirestore.ACTUALIZADO_EN to actualizadoEn
)

/** Convierte un documento leido de Firestore. Los campos ausentes o de otro tipo quedan en null. */
fun documentoADto(id: String, campos: Map<String, Any?>?): RecordatorioDto = RecordatorioDto(
    id = id,
    titulo = campos?.get(CamposFirestore.TITULO) as? String,
    descripcion = campos?.get(CamposFirestore.DESCRIPCION) as? String,
    categoria = campos?.get(CamposFirestore.CATEGORIA) as? String,
    prioridad = campos?.get(CamposFirestore.PRIORIDAD) as? String,
    estado = campos?.get(CamposFirestore.ESTADO) as? String,
    fecha = (campos?.get(CamposFirestore.FECHA) as? Number)?.toLong(),
    actualizadoEn = (campos?.get(CamposFirestore.ACTUALIZADO_EN) as? Number)?.toLong()
)
