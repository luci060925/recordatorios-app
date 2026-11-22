package com.mansilla.recordatoriosapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansilla.recordatoriosapp.data.auth.AuthRepository
import com.mansilla.recordatoriosapp.data.auth.EstadoAuth
import com.mansilla.recordatoriosapp.data.prefs.FiltroPrioridad
import com.mansilla.recordatoriosapp.data.prefs.OrdenListado
import com.mansilla.recordatoriosapp.data.prefs.PreferenciasRepository
import com.mansilla.recordatoriosapp.data.prefs.PreferenciasUsuario
import com.mansilla.recordatoriosapp.data.prefs.TemaApp
import com.mansilla.recordatoriosapp.data.repository.RecordatorioRepository
import com.mansilla.recordatoriosapp.data.repository.ResumenPendientes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AjustesViewModel(
    private val preferenciasRepositorio: PreferenciasRepository,
    private val auth: AuthRepository,
    private val repositorio: RecordatorioRepository
) : ViewModel() {

    val preferencias: StateFlow<PreferenciasUsuario> = preferenciasRepositorio.preferencias
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreferenciasUsuario())

    /** Correo de la cuenta activa (null si no hay sesion). */
    val correo: StateFlow<String?> = auth.estado
        .map { (it as? EstadoAuth.ConSesion)?.usuario?.email }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Cambios que todavia no estan en la nube: se informan antes de cerrar la sesion. */
    val sinSincronizar: StateFlow<ResumenPendientes> = MutableStateFlow(ResumenPendientes()).also { destino ->
        viewModelScope.launch {
            repositorio.observarResumenPendientes().collect { destino.value = it }
        }
    }

    private val _cerrandoSesion = MutableStateFlow(false)
    val cerrandoSesion: StateFlow<Boolean> = _cerrandoSesion

    // Las escrituras son funciones suspendidas ejecutadas en viewModelScope.
    fun cambiarTema(tema: TemaApp) {
        viewModelScope.launch { preferenciasRepositorio.actualizarTema(tema) }
    }

    fun cambiarMostrarCompletados(mostrar: Boolean) {
        viewModelScope.launch { preferenciasRepositorio.actualizarMostrarCompletados(mostrar) }
    }

    fun cambiarOrden(orden: OrdenListado) {
        viewModelScope.launch { preferenciasRepositorio.actualizarOrden(orden) }
    }

    fun cambiarFiltroPrioridad(filtro: FiltroPrioridad) {
        viewModelScope.launch { preferenciasRepositorio.actualizarFiltroPrioridad(filtro) }
    }

    /**
     * Cierra la sesion: primero intenta enviar lo pendiente y borra de este dispositivo los
     * datos que ya estan en la nube; despues cierra la sesion de la cuenta.
     */
    fun cerrarSesion() {
        if (_cerrandoSesion.value) return
        _cerrandoSesion.value = true
        viewModelScope.launch {
            try {
                repositorio.prepararCierreDeSesion()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Aun si falla la limpieza, la sesion se cierra igual (los datos quedan ligados a la cuenta).
            } finally {
                auth.cerrarSesion()
                _cerrandoSesion.value = false
            }
        }
    }
}
