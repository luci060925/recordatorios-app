package com.mansilla.recordatoriosapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mansilla.recordatoriosapp.data.RecordatorioRepository

/** Construye el ViewModel inyectando la interfaz del repositorio. */
class RecordatoriosViewModelFactory(
    private val repositorio: RecordatorioRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(RecordatoriosViewModel::class.java)) {
            "ViewModel desconocido: ${modelClass.name}"
        }
        return RecordatoriosViewModel(repositorio) as T
    }
}
