package com.mansilla.recordatoriosapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mansilla.recordatoriosapp.data.prefs.FiltroPrioridad
import com.mansilla.recordatoriosapp.data.prefs.OrdenListado
import com.mansilla.recordatoriosapp.data.prefs.PreferenciasUsuario
import com.mansilla.recordatoriosapp.data.prefs.TemaApp
import com.mansilla.recordatoriosapp.data.remote.simulado.ModoServicio
import com.mansilla.recordatoriosapp.ui.components.SelectorOpciones
import com.mansilla.recordatoriosapp.ui.viewmodel.AjustesViewModel

/** Punto de entrada con estado de la pantalla de ajustes (preferencias de DataStore). */
@Composable
fun PantallaAjustes(
    viewModelFactory: ViewModelProvider.Factory,
    onVolver: () -> Unit
) {
    val viewModel: AjustesViewModel = viewModel(factory = viewModelFactory)
    val preferencias by viewModel.preferencias.collectAsStateWithLifecycle()
    val modoServicio = viewModel.modoServicio?.collectAsStateWithLifecycle()?.value

    ContenidoAjustes(
        preferencias = preferencias,
        modoServicio = modoServicio,
        onTema = viewModel::cambiarTema,
        onMostrarCompletados = viewModel::cambiarMostrarCompletados,
        onOrden = viewModel::cambiarOrden,
        onFiltroPrioridad = viewModel::cambiarFiltroPrioridad,
        onModoServicio = viewModel::cambiarModoServicio,
        onVolver = onVolver
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContenidoAjustes(
    preferencias: PreferenciasUsuario,
    modoServicio: ModoServicio?,
    onTema: (TemaApp) -> Unit,
    onMostrarCompletados: (Boolean) -> Unit,
    onOrden: (OrdenListado) -> Unit,
    onFiltroPrioridad: (FiltroPrioridad) -> Unit,
    onModoServicio: (ModoServicio) -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Las preferencias se guardan en el dispositivo y se conservan al cerrar la aplicación.",
                style = MaterialTheme.typography.bodyMedium
            )

            SelectorOpciones(
                titulo = "Tema",
                opciones = TemaApp.entries,
                seleccionada = preferencias.tema,
                nombreOpcion = { it.etiqueta },
                onSeleccion = onTema
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Mostrar recordatorios completados",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = preferencias.mostrarCompletados,
                    onCheckedChange = onMostrarCompletados
                )
            }

            HorizontalDivider()

            SelectorOpciones(
                titulo = "Orden del listado",
                opciones = OrdenListado.entries,
                seleccionada = preferencias.orden,
                nombreOpcion = { it.etiqueta },
                onSeleccion = onOrden
            )

            HorizontalDivider()

            SelectorOpciones(
                titulo = "Filtrar por prioridad",
                opciones = FiltroPrioridad.entries,
                seleccionada = preferencias.filtroPrioridad,
                nombreOpcion = { it.etiqueta },
                onSeleccion = onFiltroPrioridad
            )

            if (modoServicio != null) {
                HorizontalDivider()
                Text(
                    text = "Servicio simulado (solo para pruebas)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Permite comprobar cómo responde la app ante errores del servicio. " +
                        "Este valor no se guarda: vuelve a \"Normal\" al reiniciar.",
                    style = MaterialTheme.typography.bodySmall
                )
                SelectorOpciones(
                    titulo = "Comportamiento del servicio",
                    opciones = ModoServicio.entries,
                    seleccionada = modoServicio,
                    nombreOpcion = { it.etiqueta },
                    onSeleccion = onModoServicio
                )
            }
        }
    }
}
