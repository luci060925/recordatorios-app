package com.mansilla.recordatoriosapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansilla.recordatoriosapp.data.prefs.FiltroPrioridad
import com.mansilla.recordatoriosapp.data.prefs.OrdenListado
import com.mansilla.recordatoriosapp.data.prefs.PreferenciasRepository
import com.mansilla.recordatoriosapp.data.prefs.PreferenciasUsuario
import com.mansilla.recordatoriosapp.data.prefs.TemaApp
import com.mansilla.recordatoriosapp.data.remote.simulado.ControlServicioSimulado
import com.mansilla.recordatoriosapp.data.remote.simulado.ModoServicio
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AjustesViewModel(
    private val preferenciasRepositorio: PreferenciasRepository,
    private val controlServicio: ControlServicioSimulado?
) : ViewModel() {

    val preferencias: StateFlow<PreferenciasUsuario> = preferenciasRepositorio.preferencias
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreferenciasUsuario())

    /** null cuando la app usa un servicio real (no hay modo de simulacion). */
    val modoServicio: StateFlow<ModoServicio?>? = controlServicio?.modo

    val servicioSimulado: Boolean
        get() = controlServicio != null

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

    fun cambiarModoServicio(modo: ModoServicio) {
        controlServicio?.modo?.value = modo
    }
}
