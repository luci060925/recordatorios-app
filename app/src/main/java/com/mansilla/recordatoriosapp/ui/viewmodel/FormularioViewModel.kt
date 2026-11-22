package com.mansilla.recordatoriosapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansilla.recordatoriosapp.Categoria
import com.mansilla.recordatoriosapp.Prioridad
import com.mansilla.recordatoriosapp.data.repository.DatosRecordatorio
import com.mansilla.recordatoriosapp.data.repository.RecordatorioRepository
import com.mansilla.recordatoriosapp.ui.state.EstadoEnvio
import com.mansilla.recordatoriosapp.ui.state.FormularioUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Formulario de creacion y edicion: valida, guarda en Room a traves del repositorio. */
class FormularioViewModel(
    private val repositorio: RecordatorioRepository
) : ViewModel() {

    private val _formularioState = MutableStateFlow(FormularioUiState())
    val formularioState: StateFlow<FormularioUiState> = _formularioState.asStateFlow()

    /** Carga un recordatorio existente para editarlo (una sola vez por pantalla). */
    fun cargarParaEditar(id: String) {
        if (_formularioState.value.idEditando == id) return
        _formularioState.update { it.copy(idEditando = id, cargando = true) }
        viewModelScope.launch {
            val existente = repositorio.observarPorId(id).first()
            _formularioState.update {
                if (existente == null) {
                    it.copy(
                        cargando = false,
                        estadoEnvio = EstadoEnvio.Error("No se encontró el recordatorio que se queria editar.")
                    )
                } else {
                    it.copy(
                        titulo = existente.titulo,
                        descripcion = existente.descripcion.orEmpty(),
                        categoria = existente.categoria,
                        prioridad = existente.prioridad,
                        cargando = false
                    )
                }
            }
        }
    }

    // ---------- Eventos de la interfaz ----------

    fun actualizarTitulo(valor: String) = cambiar { it.copy(titulo = valor) }

    fun actualizarDescripcion(valor: String) = cambiar { it.copy(descripcion = valor) }

    fun actualizarCategoria(valor: Categoria) = cambiar { it.copy(categoria = valor) }

    fun actualizarPrioridad(valor: Prioridad) = cambiar { it.copy(prioridad = valor) }

    /** Todo cambio marca el formulario como tocado y limpia un error de guardado previo. */
    private fun cambiar(transformacion: (FormularioUiState) -> FormularioUiState) {
        _formularioState.update {
            val nuevo = transformacion(it).copy(interactuado = true)
            if (nuevo.estadoEnvio is EstadoEnvio.Error) nuevo.copy(estadoEnvio = EstadoEnvio.Inactivo) else nuevo
        }
    }

    fun guardar() {
        val datos = _formularioState.value
        if (!datos.puedeGuardar) return

        _formularioState.update { it.copy(estadoEnvio = EstadoEnvio.Guardando) }

        viewModelScope.launch {
            try {
                val contenido = DatosRecordatorio(
                    titulo = datos.titulo.trim(),
                    descripcion = datos.descripcion.trim().ifBlank { null },
                    categoria = requireNotNull(datos.categoria),
                    prioridad = requireNotNull(datos.prioridad)
                )
                val id = datos.idEditando
                if (id != null) repositorio.actualizar(id, contenido) else repositorio.crear(contenido)

                _formularioState.update { it.copy(estadoEnvio = EstadoEnvio.Exito) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _formularioState.update {
                    it.copy(estadoEnvio = EstadoEnvio.Error("No se pudo guardar el recordatorio en el dispositivo."))
                }
            }
        }
    }
}
