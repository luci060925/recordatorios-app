package com.mansilla.recordatoriosapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansilla.recordatoriosapp.data.auth.AuthRepository
import com.mansilla.recordatoriosapp.data.auth.EstadoAuth
import com.mansilla.recordatoriosapp.data.auth.ResultadoAuth
import com.mansilla.recordatoriosapp.ui.state.AccesoUiState
import com.mansilla.recordatoriosapp.ui.state.ModoAcceso
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Registro e inicio de sesion. La interfaz nunca toca Firebase: todo pasa por [AuthRepository]. */
class AccesoViewModel(
    private val auth: AuthRepository
) : ViewModel() {

    private val formulario = MutableStateFlow(AccesoUiState())

    init {
        // Al iniciar sesion se descarta todo lo escrito (correo, modo, errores): este ViewModel vive
        // mientras vive la Activity, y la persona que use el dispositivo despues no debe ver nada
        // de la anterior en la pantalla de acceso.
        viewModelScope.launch {
            auth.estado.collect { sesion ->
                if (sesion is EstadoAuth.ConSesion) formulario.value = AccesoUiState()
            }
        }
    }

    val estado: StateFlow<AccesoUiState> =
        combine(formulario, auth.estado) { campos, sesion ->
            campos.copy(sesionExpirada = (sesion as? EstadoAuth.SinSesion)?.expirada == true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccesoUiState())

    fun cambiarEmail(valor: String) = formulario.update {
        it.copy(email = valor, interactuado = true, error = null)
    }

    fun cambiarContrasena(valor: String) = formulario.update {
        it.copy(contrasena = valor, interactuado = true, error = null)
    }

    fun cambiarModo(modo: ModoAcceso) = formulario.update {
        it.copy(modo = modo, error = null)
    }

    fun enviar() {
        val actual = formulario.value
        if (!actual.puedeEnviar) return

        formulario.update { it.copy(enCurso = true, error = null) }
        viewModelScope.launch {
            val resultado = when (actual.modo) {
                ModoAcceso.INICIAR_SESION -> auth.iniciarSesion(actual.email, actual.contrasena)
                ModoAcceso.REGISTRARSE -> auth.registrar(actual.email, actual.contrasena)
            }
            formulario.update {
                when (resultado) {
                    // Con exito la sesion cambia a ConSesion y la raiz de la app muestra el listado.
                    // La contrasena no se conserva en el estado.
                    ResultadoAuth.Exito -> it.copy(enCurso = false, contrasena = "")
                    is ResultadoAuth.Error -> it.copy(enCurso = false, error = resultado.tipo)
                }
            }
        }
    }
}
