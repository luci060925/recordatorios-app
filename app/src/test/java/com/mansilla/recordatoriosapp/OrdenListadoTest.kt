package com.mansilla.recordatoriosapp

import com.mansilla.recordatoriosapp.data.prefs.OrdenListado
import com.mansilla.recordatoriosapp.ui.state.ordenarRecordatorios
import org.junit.Assert.assertEquals
import org.junit.Test

class OrdenListadoTest {

    private fun r(id: String, titulo: String, prioridad: Prioridad, fecha: Long) = Recordatorio(
        id = id, titulo = titulo, descripcion = null, categoria = Categoria.Personal,
        prioridad = prioridad, estado = EstadoRecordatorio.Pendiente, fecha = fecha
    )

    private val lista = listOf(
        r("1", "beta", Prioridad.Baja, 10),
        r("2", "Alfa", Prioridad.Alta, 30),
        r("3", "gamma", Prioridad.Media, 20)
    )

    private fun ids(orden: OrdenListado) = ordenarRecordatorios(lista, orden).map { it.id }

    @Test
    fun fechaReciente() = assertEquals(listOf("2", "3", "1"), ids(OrdenListado.FECHA_RECIENTE))

    @Test
    fun fechaAntigua() = assertEquals(listOf("1", "3", "2"), ids(OrdenListado.FECHA_ANTIGUA))

    @Test
    fun porTituloSinDistinguirMayusculas() = assertEquals(listOf("2", "1", "3"), ids(OrdenListado.TITULO))

    @Test
    fun porPrioridad() = assertEquals(listOf("2", "3", "1"), ids(OrdenListado.PRIORIDAD))
}
