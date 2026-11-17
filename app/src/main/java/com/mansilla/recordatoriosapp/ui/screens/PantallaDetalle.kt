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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mansilla.recordatoriosapp.Recordatorio
import com.mansilla.recordatoriosapp.nombreCategoria
import com.mansilla.recordatoriosapp.nombreEstado
import com.mansilla.recordatoriosapp.nombrePrioridad
import com.mansilla.recordatoriosapp.ui.components.IndicadorCarga
import com.mansilla.recordatoriosapp.ui.components.MensajeEstado
import com.mansilla.recordatoriosapp.ui.state.DetalleUiState
import com.mansilla.recordatoriosapp.ui.viewmodel.RecordatoriosViewModel
import com.mansilla.recordatoriosapp.ui.viewmodel.RecordatoriosViewModelFactory

/** Punto de entrada con estado: recibe solo el identificador y carga el registro. */
@Composable
fun PantallaDetalle(
    recordatorioId: Int,
    viewModelFactory: RecordatoriosViewModelFactory,
    onVolver: () -> Unit
) {
    val viewModel: RecordatoriosViewModel = viewModel(factory = viewModelFactory)
    val estado by viewModel.detalleState.collectAsStateWithLifecycle()

    LaunchedEffect(recordatorioId) {
        viewModel.cargarDetalle(recordatorioId)
    }

    ContenidoDetalle(
        estado = estado,
        onVolver = onVolver,
        onReintentar = { viewModel.cargarDetalle(recordatorioId) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContenidoDetalle(
    estado: DetalleUiState,
    onVolver: () -> Unit,
    onReintentar: () -> Unit,
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
                textoAccion = "Reintentar",
                onAccion = onReintentar,
                modifier = contenidoModifier
            )

            is DetalleUiState.Contenido -> TarjetaDetalle(
                recordatorio = estado.recordatorio,
                modifier = contenidoModifier
            )
        }
    }
}

@Composable
private fun TarjetaDetalle(
    recordatorio: Recordatorio,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
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
                FilaDato("Descripcion", recordatorio.descripcion ?: "Sin descripcion")
                FilaDato("Categoria", nombreCategoria(recordatorio.categoria))
                FilaDato("Prioridad", nombrePrioridad(recordatorio.prioridad))
                FilaDato("Estado", nombreEstado(recordatorio.estado))
                FilaDato("Identificador", recordatorio.id.toString())
            }
        }
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
