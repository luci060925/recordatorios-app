package com.mansilla.recordatoriosapp.ui.state

import com.mansilla.recordatoriosapp.Prioridad
import com.mansilla.recordatoriosapp.Recordatorio
import com.mansilla.recordatoriosapp.data.prefs.FiltroPrioridad
import com.mansilla.recordatoriosapp.data.prefs.OrdenListado
import com.mansilla.recordatoriosapp.data.repository.EstadoSincronizacion

/** Estados mutuamente excluyentes de la pantalla de listado. */
sealed interface ListadoUiState {
    data object Cargando : ListadoUiState

    /** [porFiltros] es true cuando hay recordatorios pero los filtros activos los ocultan. */
    data class Vacio(val porFiltros: Boolean = false) : ListadoUiState

    data class Contenido(val recordatorios: List<Recordatorio>) : ListadoUiState
    data class Error(val mensaje: String) : ListadoUiState
}

/** Estado de la sincronizacion tal como lo muestra la interfaz. */
data class SincronizacionUiState(
    val estado: EstadoSincronizacion = EstadoSincronizacion.Inactiva,
    val pendientes: Int = 0,
    val conError: Int = 0
)

private fun pesoPrioridad(p: Prioridad): Int = when (p) {
    Prioridad.Alta -> 0
    Prioridad.Media -> 1
    Prioridad.Baja -> 2
}

/** Aplica el criterio de orden elegido en las preferencias. */
fun ordenarRecordatorios(lista: List<Recordatorio>, orden: OrdenListado): List<Recordatorio> =
    when (orden) {
        OrdenListado.FECHA_RECIENTE -> lista.sortedByDescending { it.fecha }
        OrdenListado.FECHA_ANTIGUA -> lista.sortedBy { it.fecha }
        OrdenListado.TITULO -> lista.sortedBy { it.titulo.lowercase() }
        OrdenListado.PRIORIDAD -> lista.sortedWith(
            compareBy<Recordatorio> { pesoPrioridad(it.prioridad) }.thenByDescending { it.fecha }
        )
    }

fun FiltroPrioridad.aPrioridad(): Prioridad? = when (this) {
    FiltroPrioridad.TODAS -> null
    FiltroPrioridad.ALTA -> Prioridad.Alta
    FiltroPrioridad.MEDIA -> Prioridad.Media
    FiltroPrioridad.BAJA -> Prioridad.Baja
}
