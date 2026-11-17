package com.mansilla.recordatoriosapp.ui.state

import com.mansilla.recordatoriosapp.Recordatorio

/** Estados mutuamente excluyentes de la pantalla de listado. */
sealed interface ListadoUiState {
    data object Cargando : ListadoUiState
    data object Vacio : ListadoUiState
    data class Contenido(val recordatorios: List<Recordatorio>) : ListadoUiState
    data class Error(val mensaje: String) : ListadoUiState
}
