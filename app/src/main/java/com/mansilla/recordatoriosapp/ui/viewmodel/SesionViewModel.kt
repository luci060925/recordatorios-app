package com.mansilla.recordatoriosapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.mansilla.recordatoriosapp.data.auth.AuthRepository
import com.mansilla.recordatoriosapp.data.auth.EstadoAuth
import kotlinx.coroutines.flow.StateFlow

/** Expone el estado de la sesion para decidir que pantalla mostrar (acceso o aplicacion). */
class SesionViewModel(auth: AuthRepository) : ViewModel() {
    val estado: StateFlow<EstadoAuth> = auth.estado
}
