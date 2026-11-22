package com.mansilla.recordatoriosapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mansilla.recordatoriosapp.Categoria
import com.mansilla.recordatoriosapp.Prioridad
import com.mansilla.recordatoriosapp.nombreCategoria
import com.mansilla.recordatoriosapp.nombrePrioridad
import com.mansilla.recordatoriosapp.ui.components.CampoTexto
import com.mansilla.recordatoriosapp.ui.components.IndicadorCarga
import com.mansilla.recordatoriosapp.ui.components.SelectorOpciones
import com.mansilla.recordatoriosapp.ui.state.EstadoEnvio
import com.mansilla.recordatoriosapp.ui.state.FormularioUiState
import com.mansilla.recordatoriosapp.ui.state.LONGITUD_MAXIMA_DESCRIPCION
import com.mansilla.recordatoriosapp.ui.state.LONGITUD_MINIMA_TITULO
import com.mansilla.recordatoriosapp.ui.state.OPCIONES_CATEGORIA
import com.mansilla.recordatoriosapp.ui.state.OPCIONES_PRIORIDAD
import com.mansilla.recordatoriosapp.ui.viewmodel.FormularioViewModel

/**
 * Punto de entrada con estado. Sirve para crear (recordatorioId = null) y para
 * editar un recordatorio existente.
 */
@Composable
fun PantallaCrear(
    recordatorioId: String?,
    viewModelFactory: ViewModelProvider.Factory,
    onCancelar: () -> Unit,
    onGuardadoExitoso: () -> Unit
) {
    val viewModel: FormularioViewModel = viewModel(factory = viewModelFactory)
    val estado by viewModel.formularioState.collectAsStateWithLifecycle()

    LaunchedEffect(recordatorioId) {
        if (recordatorioId != null) viewModel.cargarParaEditar(recordatorioId)
    }
    // Resultado del envío: al guardar con éxito se vuelve a la pantalla anterior.
    LaunchedEffect(estado.estadoEnvio) {
        if (estado.estadoEnvio == EstadoEnvio.Exito) onGuardadoExitoso()
    }

    FormularioRecordatorio(
        estado = estado,
        onTituloChange = viewModel::actualizarTitulo,
        onDescripcionChange = viewModel::actualizarDescripcion,
        onCategoriaSeleccionada = viewModel::actualizarCategoria,
        onPrioridadSeleccionada = viewModel::actualizarPrioridad,
        onGuardar = viewModel::guardar,
        onCancelar = onCancelar
    )
}

/** Formulario sin estado propio: recibe el UiState y comunica cada evento con lambdas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioRecordatorio(
    estado: FormularioUiState,
    onTituloChange: (String) -> Unit,
    onDescripcionChange: (String) -> Unit,
    onCategoriaSeleccionada: (Categoria) -> Unit,
    onPrioridadSeleccionada: (Prioridad) -> Unit,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (estado.modoEdicion) "Editar recordatorio" else "Nuevo recordatorio") },
                navigationIcon = {
                    IconButton(onClick = onCancelar) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancelar y volver"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (estado.cargando) {
            IndicadorCarga(
                modifier = Modifier.padding(innerPadding),
                mensaje = "Cargando recordatorio..."
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CampoTexto(
                valor = estado.titulo,
                onValorChange = onTituloChange,
                etiqueta = "Título",
                mensajeError = estado.errorTitulo,
                ayuda = "Mínimo $LONGITUD_MINIMA_TITULO caracteres"
            )
            CampoTexto(
                valor = estado.descripcion,
                onValorChange = onDescripcionChange,
                etiqueta = "Descripción (opcional)",
                mensajeError = estado.errorDescripcion,
                ayuda = "${estado.descripcion.length}/$LONGITUD_MAXIMA_DESCRIPCION",
                minLineas = 3
            )
            SelectorOpciones(
                titulo = "Categoría",
                opciones = OPCIONES_CATEGORIA,
                seleccionada = estado.categoria,
                nombreOpcion = ::nombreCategoria,
                onSeleccion = onCategoriaSeleccionada,
                mensajeError = estado.errorCategoria
            )
            SelectorOpciones(
                titulo = "Prioridad",
                opciones = OPCIONES_PRIORIDAD,
                seleccionada = estado.prioridad,
                nombreOpcion = ::nombrePrioridad,
                onSeleccion = onPrioridadSeleccionada,
                mensajeError = estado.errorPrioridad
            )

            // Error general de guardado
            if (estado.estadoEnvio is EstadoEnvio.Error) {
                Text(
                    text = "Error: ${estado.estadoEnvio.mensaje}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                )
            }

            Button(
                onClick = onGuardar,
                enabled = estado.puedeGuardar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (estado.enviando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        when {
                            estado.enviando -> "Guardando..."
                            estado.modoEdicion -> "Guardar cambios"
                            else -> "Guardar recordatorio"
                        }
                    )
                }
            }
            OutlinedButton(
                onClick = onCancelar,
                enabled = !estado.enviando,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancelar")
            }
        }
    }
}
