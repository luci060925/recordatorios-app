package com.mansilla.recordatoriosapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mansilla.recordatoriosapp.AppContainer

/**
 * Crea los ViewModel inyectando las interfaces de datos. Los ViewModel reciben el
 * repositorio y nunca referencias a Activity, Context ni vistas.
 */
class AppViewModelFactory(
    private val contenedor: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(ListadoViewModel::class.java) ->
            ListadoViewModel(contenedor.repositorio, contenedor.preferenciasRepository)

        modelClass.isAssignableFrom(DetalleViewModel::class.java) ->
            DetalleViewModel(contenedor.repositorio)

        modelClass.isAssignableFrom(FormularioViewModel::class.java) ->
            FormularioViewModel(contenedor.repositorio)

        modelClass.isAssignableFrom(AjustesViewModel::class.java) ->
            AjustesViewModel(contenedor.preferenciasRepository, contenedor.controlServicioSimulado)

        else -> throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
    } as T
}
