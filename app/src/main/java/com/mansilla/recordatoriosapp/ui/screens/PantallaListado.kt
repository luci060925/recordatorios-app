package com.mansilla.recordatoriosapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mansilla.recordatoriosapp.Recordatorio
import com.mansilla.recordatoriosapp.data.prefs.FiltroPrioridad
import com.mansilla.recordatoriosapp.data.prefs.PreferenciasUsuario
import com.mansilla.recordatoriosapp.ui.components.BannerSincronizacion
import com.mansilla.recordatoriosapp.ui.components.IndicadorCarga
import com.mansilla.recordatoriosapp.ui.components.MensajeEstado
import com.mansilla.recordatoriosapp.ui.components.TarjetaRecordatorio
import com.mansilla.recordatoriosapp.ui.state.ListadoUiState
import com.mansilla.recordatoriosapp.ui.state.SincronizacionUiState
import com.mansilla.recordatoriosapp.ui.viewmodel.ListadoViewModel

/** Punto de entrada con estado: observa los flujos del ViewModel respetando el ciclo de vida. */
@Composable
fun PantallaListado(
    viewModelFactory: ViewModelProvider.Factory,
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (String) -> Unit,
    onAbrirAjustes: () -> Unit
) {
    val viewModel: ListadoViewModel = viewModel(factory = viewModelFactory)
    val estado by viewModel.listadoState.collectAsStateWithLifecycle()
    val sincronizacion by viewModel.sincronizacion.collectAsStateWithLifecycle()
    val preferencias by viewModel.preferencias.collectAsStateWithLifecycle()

    ContenidoListado(
        estado = estado,
        sincronizacion = sincronizacion,
        preferencias = preferencias,
        onCrearRecordatorio = onCrearRecordatorio,
        onRecordatorioClick = onRecordatorioClick,
        onReintentar = viewModel::reintentar,
        onSincronizar = viewModel::sincronizarAhora,
        onAbrirAjustes = onAbrirAjustes
    )
}

/** Contenido sin estado propio: dibuja el UiState recibido y comunica acciones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContenidoListado(
    estado: ListadoUiState,
    sincronizacion: SincronizacionUiState,
    preferencias: PreferenciasUsuario,
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (String) -> Unit,
    onReintentar: () -> Unit,
    onSincronizar: () -> Unit,
    onAbrirAjustes: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Mis recordatorios") },
                actions = {
                    IconButton(onClick = onSincronizar) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sincronizar ahora")
                    }
                    IconButton(onClick = onAbrirAjustes) {
                        Icon(Icons.Filled.Settings, contentDescription = "Abrir ajustes")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCrearRecordatorio) {
                Icon(Icons.Filled.Add, contentDescription = "Crear recordatorio")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BannerSincronizacion(estado = sincronizacion, onReintentar = onSincronizar)

            Box(modifier = Modifier.weight(1f)) {
                // when exhaustivo: nunca se muestran a la vez contenido, carga y error.
                when (estado) {
                    ListadoUiState.Cargando -> IndicadorCarga(mensaje = "Cargando recordatorios...")

                    is ListadoUiState.Vacio -> if (estado.porFiltros) {
                        MensajeEstado(
                            titulo = "Ningún recordatorio coincide",
                            mensaje = "Hay recordatorios guardados, pero los filtros de los ajustes los ocultan.",
                            textoAccion = "Cambiar filtros",
                            onAccion = onAbrirAjustes
                        )
                    } else {
                        MensajeEstado(
                            titulo = "Todavía no hay recordatorios",
                            mensaje = "Creá tu primer recordatorio para empezar.",
                            textoAccion = "Crear recordatorio",
                            onAccion = onCrearRecordatorio
                        )
                    }

                    is ListadoUiState.Error -> MensajeEstado(
                        titulo = "Ocurrió un error",
                        mensaje = estado.mensaje,
                        esError = true,
                        textoAccion = "Reintentar",
                        onAccion = onReintentar
                    )

                    is ListadoUiState.Contenido -> ListaRecordatorios(
                        recordatorios = estado.recordatorios,
                        preferencias = preferencias,
                        onRecordatorioClick = onRecordatorioClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ListaRecordatorios(
    recordatorios: List<Recordatorio>,
    preferencias: PreferenciasUsuario,
    onRecordatorioClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Al aparecer un recordatorio nuevo arriba de la lista, se vuelve al inicio para que se vea.
    val estadoLista = rememberLazyListState()
    LaunchedEffect(recordatorios.firstOrNull()?.id) {
        estadoLista.scrollToItem(0)
    }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "${recordatorios.size} recordatorios",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = resumenPreferencias(preferencias),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyColumn(
            state = estadoLista,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // Espacio inferior para que el FAB no tape la última tarjeta.
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

private fun resumenPreferencias(p: PreferenciasUsuario): String {
    val prioridad = if (p.filtroPrioridad == FiltroPrioridad.TODAS) "todas" else p.filtroPrioridad.etiqueta
    val completados = if (p.mostrarCompletados) "visibles" else "ocultos"
    return "Orden: ${p.orden.etiqueta} · Prioridad: $prioridad · Completados: $completados"
}
