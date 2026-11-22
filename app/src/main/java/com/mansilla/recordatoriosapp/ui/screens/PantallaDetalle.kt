package com.mansilla.recordatoriosapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mansilla.recordatoriosapp.EstadoRecordatorio
import com.mansilla.recordatoriosapp.Recordatorio
import com.mansilla.recordatoriosapp.formatearFecha
import com.mansilla.recordatoriosapp.nombreCategoria
import com.mansilla.recordatoriosapp.nombreEstado
import com.mansilla.recordatoriosapp.nombreEstadoSync
import com.mansilla.recordatoriosapp.nombrePrioridad
import com.mansilla.recordatoriosapp.ui.components.IndicadorCarga
import com.mansilla.recordatoriosapp.ui.components.MensajeEstado
import com.mansilla.recordatoriosapp.ui.state.DetalleUiState
import com.mansilla.recordatoriosapp.ui.viewmodel.DetalleViewModel

/** Punto de entrada con estado: recibe solo el identificador y observa el registro en Room. */
@Composable
fun PantallaDetalle(
    recordatorioId: String,
    viewModelFactory: ViewModelProvider.Factory,
    onVolver: () -> Unit,
    onEditar: (String) -> Unit
) {
    val viewModel: DetalleViewModel = viewModel(factory = viewModelFactory)
    val estado by viewModel.detalleState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(recordatorioId) {
        viewModel.cargar(recordatorioId)
    }
    // Tras eliminar el registro se vuelve al listado.
    LaunchedEffect(estado) {
        if (estado == DetalleUiState.Eliminado) onVolver()
    }

    ContenidoDetalle(
        estado = estado,
        mensajeError = error,
        onVolver = onVolver,
        onEditar = { onEditar(recordatorioId) },
        onAlternarCompletado = viewModel::alternarCompletado,
        onEliminar = viewModel::eliminar,
        onDescartarError = viewModel::descartarError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContenidoDetalle(
    estado: DetalleUiState,
    mensajeError: String?,
    onVolver: () -> Unit,
    onEditar: () -> Unit,
    onAlternarCompletado: () -> Unit,
    onEliminar: () -> Unit,
    onDescartarError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Detalle del recordatorio") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver al listado"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val contenidoModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (estado) {
            DetalleUiState.Cargando -> IndicadorCarga(
                modifier = contenidoModifier,
                mensaje = "Cargando recordatorio..."
            )

            is DetalleUiState.Error -> MensajeEstado(
                titulo = "No se pudo mostrar el recordatorio",
                mensaje = estado.mensaje,
                esError = true,
                textoAccion = "Volver al listado",
                onAccion = onVolver,
                modifier = contenidoModifier
            )

            DetalleUiState.Eliminado -> IndicadorCarga(
                modifier = contenidoModifier,
                mensaje = "Recordatorio eliminado"
            )

            is DetalleUiState.Contenido -> TarjetaDetalle(
                recordatorio = estado.recordatorio,
                mensajeError = mensajeError,
                onEditar = onEditar,
                onAlternarCompletado = onAlternarCompletado,
                onEliminar = onEliminar,
                onDescartarError = onDescartarError,
                modifier = contenidoModifier
            )
        }
    }
}

@Composable
private fun TarjetaDetalle(
    recordatorio: Recordatorio,
    mensajeError: String?,
    onEditar: () -> Unit,
    onAlternarCompletado: () -> Unit,
    onEliminar: () -> Unit,
    onDescartarError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmandoEliminacion by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = recordatorio.titulo,
                    style = MaterialTheme.typography.titleLarge
                )
                HorizontalDivider()
                FilaDato("Descripción", recordatorio.descripcion ?: "Sin descripción")
                FilaDato("Categoría", nombreCategoria(recordatorio.categoria))
                FilaDato("Prioridad", nombrePrioridad(recordatorio.prioridad))
                FilaDato("Estado", nombreEstado(recordatorio.estado))
                FilaDato("Fecha de creación", formatearFecha(recordatorio.fecha))
                FilaDato("Sincronización", nombreEstadoSync(recordatorio.estadoSync))
                FilaDato("Identificador", recordatorio.id)
            }
        }

        if (mensajeError != null) {
            Text(
                text = "Error: $mensajeError",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = onDescartarError) { Text("Cerrar aviso") }
        }

        Button(onClick = onEditar, modifier = Modifier.fillMaxWidth()) {
            Text("Editar")
        }
        OutlinedButton(onClick = onAlternarCompletado, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (recordatorio.estado == EstadoRecordatorio.Completado) {
                    "Marcar como pendiente"
                } else {
                    "Marcar como completado"
                }
            )
        }
        OutlinedButton(
            onClick = { confirmandoEliminacion = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Eliminar", color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmandoEliminacion) {
        AlertDialog(
            onDismissRequest = { confirmandoEliminacion = false },
            title = { Text("¿Eliminar recordatorio?") },
            text = { Text("Se eliminará \"${recordatorio.titulo}\". Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmandoEliminacion = false
                    onEliminar()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmandoEliminacion = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun FilaDato(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = valor, style = MaterialTheme.typography.bodyLarge)
    }
}
