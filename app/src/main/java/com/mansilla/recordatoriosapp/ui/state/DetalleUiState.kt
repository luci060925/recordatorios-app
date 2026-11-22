package com.mansilla.recordatoriosapp.ui.state

import com.mansilla.recordatoriosapp.Recordatorio

/** Estados mutuamente excluyentes de la pantalla de detalle. */
sealed interface DetalleUiState {
    data object Cargando : DetalleUiState
    data class Contenido(val recordatorio: Recordatorio) : DetalleUiState
    data class Error(val mensaje: String) : DetalleUiState

    /** El registro se elimino desde esta pantalla; la pantalla debe volver al listado. */
    data object Eliminado : DetalleUiState
}
