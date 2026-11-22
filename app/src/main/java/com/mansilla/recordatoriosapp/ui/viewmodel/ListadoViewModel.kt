package com.mansilla.recordatoriosapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansilla.recordatoriosapp.data.prefs.FiltroPrioridad
import com.mansilla.recordatoriosapp.data.prefs.PreferenciasRepository
import com.mansilla.recordatoriosapp.data.prefs.PreferenciasUsuario
import com.mansilla.recordatoriosapp.data.repository.FiltroRecordatorios
import com.mansilla.recordatoriosapp.data.repository.RecordatorioRepository
import com.mansilla.recordatoriosapp.ui.state.ListadoUiState
import com.mansilla.recordatoriosapp.ui.state.SincronizacionUiState
import com.mansilla.recordatoriosapp.ui.state.aPrioridad
import com.mansilla.recordatoriosapp.ui.state.ordenarRecordatorios
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

/**
 * Estado del listado: combina las preferencias (DataStore) con los datos de Room
 * a traves del repositorio. Cada cambio en la base o en las preferencias vuelve a
 * emitir un UiState nuevo; la pantalla nunca consulta la red ni el DAO.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListadoViewModel(
    private val repositorio: RecordatorioRepository,
    preferenciasRepositorio: PreferenciasRepository
) : ViewModel() {

    private val reintentos = MutableStateFlow(0)

    val preferencias: StateFlow<PreferenciasUsuario> = preferenciasRepositorio.preferencias
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TIEMPO_ESPERA_MS), PreferenciasUsuario())

    val listadoState: StateFlow<ListadoUiState> =
        combine(preferenciasRepositorio.preferencias, reintentos) { prefs, _ -> prefs }
            .flatMapLatest { prefs ->
                repositorio.observarRecordatorios(
                    FiltroRecordatorios(
                        prioridad = prefs.filtroPrioridad.aPrioridad(),
                        incluirCompletados = prefs.mostrarCompletados
                    )
                )
                    .map { lista ->
                        val ordenada = ordenarRecordatorios(lista, prefs.orden)
                        if (ordenada.isEmpty()) {
                            ListadoUiState.Vacio(porFiltros = hayFiltrosActivos(prefs))
                        } else {
                            ListadoUiState.Contenido(ordenada)
                        }
                    }
                    .catch { emit(ListadoUiState.Error("No se pudieron leer los recordatorios guardados.")) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TIEMPO_ESPERA_MS), ListadoUiState.Cargando)

    val sincronizacion: StateFlow<SincronizacionUiState> =
        combine(repositorio.estadoSincronizacion, repositorio.observarResumenPendientes()) { estado, resumen ->
            SincronizacionUiState(estado, resumen.pendientes, resumen.conError)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(TIEMPO_ESPERA_MS), SincronizacionUiState())

    fun reintentar() {
        reintentos.update { it + 1 }
    }

    /** Sincronizacion manual: tambien reintenta los registros que el servicio habia rechazado. */
    fun sincronizarAhora() {
        repositorio.solicitarSincronizacion(reintentarErrores = true)
    }

    private fun hayFiltrosActivos(prefs: PreferenciasUsuario) =
        prefs.filtroPrioridad != FiltroPrioridad.TODAS || !prefs.mostrarCompletados

    private companion object {
        const val TIEMPO_ESPERA_MS = 5_000L
    }
}
