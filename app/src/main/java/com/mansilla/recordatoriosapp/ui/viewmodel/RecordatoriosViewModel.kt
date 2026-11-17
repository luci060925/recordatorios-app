package com.mansilla.recordatoriosapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansilla.recordatoriosapp.Categoria
import com.mansilla.recordatoriosapp.EstadoRecordatorio
import com.mansilla.recordatoriosapp.Prioridad
import com.mansilla.recordatoriosapp.Recordatorio
import com.mansilla.recordatoriosapp.data.RecordatorioRepository
import com.mansilla.recordatoriosapp.ui.state.DetalleUiState
import com.mansilla.recordatoriosapp.ui.state.EstadoEnvio
import com.mansilla.recordatoriosapp.ui.state.FormularioUiState
import com.mansilla.recordatoriosapp.ui.state.ListadoUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Recibe los eventos de la interfaz, aplica las reglas de validacion y
 * publica estados inmutables. Depende de la interfaz del repositorio y no
 * guarda referencias a Activity, View ni componentes visuales.
 */
class RecordatoriosViewModel(
    private val repositorio: RecordatorioRepository
) : ViewModel() {

    private val _listadoState = MutableStateFlow<ListadoUiState>(ListadoUiState.Cargando)
    val listadoState: StateFlow<ListadoUiState> = _listadoState.asStateFlow()

    private val _detalleState = MutableStateFlow<DetalleUiState>(DetalleUiState.Cargando)
    val detalleState: StateFlow<DetalleUiState> = _detalleState.asStateFlow()

    private val _formularioState = MutableStateFlow(FormularioUiState())
    val formularioState: StateFlow<FormularioUiState> = _formularioState.asStateFlow()

    // ---------- Listado ----------

    fun cargarRecordatorios() {
        viewModelScope.launch {
            _listadoState.update { ListadoUiState.Cargando }
            try {
                delay(RETARDO_CARGA_MS) // simula una consulta asincronica
                val datos = repositorio.obtenerRecordatorios()
                _listadoState.update {
                    if (datos.isEmpty()) ListadoUiState.Vacio else ListadoUiState.Contenido(datos)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _listadoState.update {
                    ListadoUiState.Error(e.message ?: "No se pudieron cargar los recordatorios.")
                }
            }
        }
    }

    // ---------- Detalle ----------

    fun cargarDetalle(id: Int) {
        viewModelScope.launch {
            _detalleState.update { DetalleUiState.Cargando }
            try {
                delay(RETARDO_CARGA_MS)
                val recordatorio = repositorio.obtenerPorId(id)
                _detalleState.update {
                    if (recordatorio != null) {
                        DetalleUiState.Contenido(recordatorio)
                    } else {
                        DetalleUiState.Error("No se encontro el recordatorio solicitado.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _detalleState.update {
                    DetalleUiState.Error(e.message ?: "No se pudo cargar el recordatorio.")
                }
            }
        }
    }

    // ---------- Formulario: eventos de la interfaz ----------

    fun actualizarTitulo(valor: String) {
        _formularioState.update { it.copy(titulo = valor, interactuado = true, estadoEnvio = reiniciarError(it)) }
    }

    fun actualizarDescripcion(valor: String) {
        _formularioState.update { it.copy(descripcion = valor, interactuado = true, estadoEnvio = reiniciarError(it)) }
    }

    fun actualizarCategoria(valor: Categoria) {
        _formularioState.update { it.copy(categoria = valor, interactuado = true, estadoEnvio = reiniciarError(it)) }
    }

    fun actualizarPrioridad(valor: Prioridad) {
        _formularioState.update { it.copy(prioridad = valor, interactuado = true, estadoEnvio = reiniciarError(it)) }
    }

    fun guardar() {
        val datos = _formularioState.value
        if (!datos.puedeGuardar) return

        _formularioState.update { it.copy(estadoEnvio = EstadoEnvio.Guardando) }

        viewModelScope.launch {
            try {
                delay(RETARDO_GUARDADO_MS) // simula una operacion remota

                val siguienteId = (repositorio.obtenerRecordatorios().maxOfOrNull { it.id } ?: 0) + 1
                repositorio.guardarRecordatorio(
                    Recordatorio(
                        id = siguienteId,
                        titulo = datos.titulo.trim(),
                        descripcion = datos.descripcion.trim().ifBlank { null },
                        categoria = requireNotNull(datos.categoria),
                        prioridad = requireNotNull(datos.prioridad),
                        estado = EstadoRecordatorio.Pendiente
                    )
                )
                _formularioState.update { it.copy(estadoEnvio = EstadoEnvio.Exito) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _formularioState.update {
                    it.copy(
                        estadoEnvio = EstadoEnvio.Error(
                            e.message ?: "No se pudo guardar el recordatorio."
                        )
                    )
                }
            }
        }
    }

    /** Si habia un error de guardado, se limpia al volver a editar el formulario. */
    private fun reiniciarError(actual: FormularioUiState): EstadoEnvio =
        if (actual.estadoEnvio is EstadoEnvio.Error) EstadoEnvio.Inactivo else actual.estadoEnvio

    private companion object {
        const val RETARDO_CARGA_MS = 800L
        const val RETARDO_GUARDADO_MS = 1000L
    }
}
