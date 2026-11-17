package com.mansilla.recordatoriosapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mansilla.recordatoriosapp.Recordatorio
import com.mansilla.recordatoriosapp.ui.components.IndicadorCarga
import com.mansilla.recordatoriosapp.ui.components.MensajeEstado
import com.mansilla.recordatoriosapp.ui.components.TarjetaRecordatorio
import com.mansilla.recordatoriosapp.ui.state.ListadoUiState
import com.mansilla.recordatoriosapp.ui.viewmodel.RecordatoriosViewModel
import com.mansilla.recordatoriosapp.ui.viewmodel.RecordatoriosViewModelFactory

/** Punto de entrada con estado: obtiene el ViewModel y observa su StateFlow. */
@Composable
fun PantallaListado(
    viewModelFactory: RecordatoriosViewModelFactory,
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (Int) -> Unit
) {
    val viewModel: RecordatoriosViewModel = viewModel(factory = viewModelFactory)
    val estado by viewModel.listadoState.collectAsStateWithLifecycle()

    // Carga al mostrarse y al volver desde la creacion.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.cargarRecordatorios()
    }

    ContenidoListado(
        estado = estado,
        onCrearRecordatorio = onCrearRecordatorio,
        onRecordatorioClick = onRecordatorioClick,
        onReintentar = viewModel::cargarRecordatorios
    )
}

/** Contenido sin estado propio: dibuja el UiState recibido y comunica acciones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContenidoListado(
    estado: ListadoUiState,
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (Int) -> Unit,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Mis recordatorios") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCrearRecordatorio) {
                Icon(Icons.Filled.Add, contentDescription = "Crear recordatorio")
            }
        }
    ) { innerPadding ->
        val contenidoModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        // when exhaustivo: nunca se muestran a la vez contenido, carga y error.
        when (estado) {
            ListadoUiState.Cargando -> IndicadorCarga(
                modifier = contenidoModifier,
                mensaje = "Cargando recordatorios..."
            )

            ListadoUiState.Vacio -> MensajeEstado(
                titulo = "Todavia no hay recordatorios",
                mensaje = "Crea tu primer recordatorio para empezar.",
                textoAccion = "Crear recordatorio",
                onAccion = onCrearRecordatorio,
                modifier = contenidoModifier
            )

            is ListadoUiState.Error -> MensajeEstado(
                titulo = "Ocurrio un error",
                mensaje = estado.mensaje,
                esError = true,
                textoAccion = "Reintentar",
                onAccion = onReintentar,
                modifier = contenidoModifier
            )

            is ListadoUiState.Contenido -> ListaRecordatorios(
                recordatorios = estado.recordatorios,
                onRecordatorioClick = onRecordatorioClick,
                modifier = contenidoModifier
            )
        }
    }
}

@Composable
private fun ListaRecordatorios(
    recordatorios: List<Recordatorio>,
    onRecordatorioClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "${recordatorios.size} recordatorios registrados",
            modifier = Modifier.padding(vertical = 8.dp)
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // Espacio inferior para que el FAB no tape la ultima tarjeta.
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            items(recordatorios, key = { it.id }) { recordatorio ->
                TarjetaRecordatorio(
                    recordatorio = recordatorio,
                    onClick = { onRecordatorioClick(recordatorio.id) }
                )
            }
        }
    }
}
