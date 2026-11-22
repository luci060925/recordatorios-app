package com.mansilla.recordatoriosapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansilla.recordatoriosapp.EstadoRecordatorio
import com.mansilla.recordatoriosapp.data.repository.RecordatorioRepository
import com.mansilla.recordatoriosapp.ui.state.DetalleUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DetalleViewModel(
    private val repositorio: RecordatorioRepository
) : ViewModel() {

    private val idActual = MutableStateFlow<String?>(null)
    private val eliminado = MutableStateFlow(false)
    private val mensajeError = MutableStateFlow<String?>(null)

    val detalleState: StateFlow<DetalleUiState> =
        combine(
            idActual.flatMapLatest { id ->
                if (id == null) {
                    kotlinx.coroutines.flow.flowOf<DetalleUiState>(DetalleUiState.Cargando)
                } else {
                    repositorio.observarPorId(id).map { recordatorio ->
                        if (recordatorio != null) {
                            DetalleUiState.Contenido(recordatorio)
                        } else {
                            DetalleUiState.Error("No se encontró el recordatorio solicitado.")
                        }
                    }.catch { emit(DetalleUiState.Error("No se pudo leer el recordatorio.")) }
                }
            },
            eliminado
        ) { estado, fueEliminado ->
            if (fueEliminado) DetalleUiState.Eliminado else estado
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetalleUiState.Cargando)

    /** Mensaje recuperable de una accion fallida (por ejemplo, error al guardar en la base). */
    val error: StateFlow<String?> = mensajeError.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null
    )

    fun cargar(id: String) {
        idActual.update { id }
    }

    fun alternarCompletado() {
        val actual = (detalleState.value as? DetalleUiState.Contenido)?.recordatorio ?: return
        val nuevoEstado = if (actual.estado == EstadoRecordatorio.Completado) {
            EstadoRecordatorio.Pendiente
        } else {
            EstadoRecordatorio.Completado
        }
        ejecutar { repositorio.cambiarEstado(actual.id, nuevoEstado) }
    }

    fun eliminar() {
        val id = idActual.value ?: return
        ejecutar {
            repositorio.eliminar(id)
            eliminado.value = true
        }
    }

    fun descartarError() {
        mensajeError.value = null
    }

    private fun ejecutar(accion: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                accion()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mensajeError.value = "No se pudo completar la acción. Intentá nuevamente."
            }
        }
    }
}
