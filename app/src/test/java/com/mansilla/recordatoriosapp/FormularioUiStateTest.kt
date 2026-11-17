package com.mansilla.recordatoriosapp

import com.mansilla.recordatoriosapp.ui.state.EstadoEnvio
import com.mansilla.recordatoriosapp.ui.state.FormularioUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormularioUiStateTest {

    private val valido = FormularioUiState(
        titulo = "Estudiar",
        descripcion = "Repasar",
        categoria = Categoria.Estudio,
        prioridad = Prioridad.Alta,
        interactuado = true
    )

    @Test
    fun formularioCompletoEsValido() {
        assertTrue(valido.formularioValido)
        assertTrue(valido.puedeGuardar)
    }

    @Test
    fun tituloSeValidaDespuesDeEliminarEspacios() {
        assertFalse(valido.copy(titulo = "  ab  ").tituloValido)
        assertFalse(valido.copy(titulo = "  abc ").tituloValido)
        assertTrue(valido.copy(titulo = "  abcd ").tituloValido)
        assertNotNull(valido.copy(titulo = "abc").errorTitulo)
    }

    @Test
    fun descripcionNoPuedeSuperarDoscientosCaracteres() {
        assertTrue(valido.copy(descripcion = "a".repeat(200)).descripcionValida)
        val larga = valido.copy(descripcion = "a".repeat(201))
        assertFalse(larga.descripcionValida)
        assertNotNull(larga.errorDescripcion)
        assertFalse(larga.puedeGuardar)
    }

    @Test
    fun categoriaYPrioridadSonObligatorias() {
        assertFalse(valido.copy(categoria = null).categoriaValida)
        assertFalse(valido.copy(prioridad = null).prioridadValida)
        assertNotNull(valido.copy(categoria = null).errorCategoria)
        assertNotNull(valido.copy(prioridad = null).errorPrioridad)
    }

    @Test
    fun noHayErroresAntesDeInteractuar() {
        val inicial = FormularioUiState()
        assertNull(inicial.errorTitulo)
        assertNull(inicial.errorCategoria)
        assertFalse(inicial.puedeGuardar)
    }

    @Test
    fun guardadoSeDeshabilitaDuranteElEnvio() {
        val enviando = valido.copy(estadoEnvio = EstadoEnvio.Guardando)
        assertTrue(enviando.enviando)
        assertFalse(enviando.puedeGuardar)
    }

    @Test
    fun trasUnErrorDeGuardadoSePuedeReintentar() {
        val conError = valido.copy(estadoEnvio = EstadoEnvio.Error("fallo"))
        assertTrue(conError.puedeGuardar)
        assertEquals("fallo", (conError.estadoEnvio as EstadoEnvio.Error).mensaje)
    }
}
