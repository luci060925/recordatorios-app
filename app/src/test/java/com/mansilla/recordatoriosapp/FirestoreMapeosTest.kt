package com.mansilla.recordatoriosapp

import com.mansilla.recordatoriosapp.data.remote.CamposFirestore
import com.mansilla.recordatoriosapp.data.remote.RecordatorioDto
import com.mansilla.recordatoriosapp.data.remote.aDocumentoFirestore
import com.mansilla.recordatoriosapp.data.remote.documentoADto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Conversion entre el DTO y el documento users/{uid}/reminders/{id} de Firestore. */
class FirestoreMapeosTest {

    private val dto = RecordatorioDto(
        id = "uuid-1", titulo = "Estudiar", descripcion = "Desc", categoria = "ESTUDIO",
        prioridad = "ALTA", estado = "PENDIENTE", fecha = 100L, actualizadoEn = 200L
    )

    @Test
    fun elDocumentoNoRepiteElIdNiGuardaElUid() {
        val documento = dto.aDocumentoFirestore()
        // El id es el id del documento y el uid esta en la ruta: no se duplican dentro de los campos.
        assertFalse(documento.containsKey("id"))
        assertFalse(documento.containsKey("uid"))
        assertFalse(documento.containsKey("ownerUid"))
        assertEquals(
            setOf("titulo", "descripcion", "categoria", "prioridad", "estado", "fecha", "actualizadoEn"),
            documento.keys
        )
    }

    @Test
    fun idaYVuelta_conservaLosDatos() {
        val vuelta = documentoADto("uuid-1", dto.aDocumentoFirestore())
        assertEquals(dto, vuelta)
    }

    @Test
    fun losNumerosDeFirestorePuedenLlegarComoIntLongODouble() {
        val campos = dto.aDocumentoFirestore().toMutableMap()
        campos[CamposFirestore.FECHA] = 100          // Int
        campos[CamposFirestore.ACTUALIZADO_EN] = 200.0 // Double
        val vuelta = documentoADto("uuid-1", campos)
        assertEquals(100L, vuelta.fecha)
        assertEquals(200L, vuelta.actualizadoEn)
    }

    @Test
    fun losCamposAusentesODeOtroTipoQuedanEnNull() {
        val vuelta = documentoADto("uuid-2", mapOf("titulo" to 123, "fecha" to "ayer"))
        assertEquals("uuid-2", vuelta.id)
        assertNull(vuelta.titulo)
        assertNull(vuelta.fecha)
        assertNull(vuelta.categoria)
    }

    @Test
    fun unDocumentoVacioONuloNoRompe() {
        assertNull(documentoADto("x", null).titulo)
        assertNull(documentoADto("x", emptyMap()).titulo)
    }
}
