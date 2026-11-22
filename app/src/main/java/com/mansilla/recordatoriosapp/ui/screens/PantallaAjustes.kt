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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mansilla.recordatoriosapp.BuildConfig
import com.mansilla.recordatoriosapp.data.prefs.FiltroPrioridad
import com.mansilla.recordatoriosapp.data.prefs.OrdenListado
import com.mansilla.recordatoriosapp.data.prefs.PreferenciasUsuario
import com.mansilla.recordatoriosapp.data.prefs.TemaApp
import com.mansilla.recordatoriosapp.data.repository.ResumenPendientes
import com.mansilla.recordatoriosapp.ui.components.SelectorOpciones
import com.mansilla.recordatoriosapp.ui.viewmodel.AjustesViewModel

/** Punto de entrada con estado de la pantalla de ajustes (preferencias y cuenta). */
@Composable
fun PantallaAjustes(
    viewModelFactory: ViewModelProvider.Factory,
    onVolver: () -> Unit
) {
    val viewModel: AjustesViewModel = viewModel(factory = viewModelFactory)
    val preferencias by viewModel.preferencias.collectAsStateWithLifecycle()
    val correo by viewModel.correo.collectAsStateWithLifecycle()
    val sinSincronizar by viewModel.sinSincronizar.collectAsStateWithLifecycle()
    val cerrandoSesion by viewModel.cerrandoSesion.collectAsStateWithLifecycle()

    ContenidoAjustes(
        preferencias = preferencias,
        correo = correo,
        sinSincronizar = sinSincronizar,
        cerrandoSesion = cerrandoSesion,
        onTema = viewModel::cambiarTema,
        onMostrarCompletados = viewModel::cambiarMostrarCompletados,
        onOrden = viewModel::cambiarOrden,
        onFiltroPrioridad = viewModel::cambiarFiltroPrioridad,
        onCerrarSesion = viewModel::cerrarSesion,
        onVolver = onVolver
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContenidoAjustes(
    preferencias: PreferenciasUsuario,
    correo: String?,
    sinSincronizar: ResumenPendientes,
    cerrandoSesion: Boolean,
    onTema: (TemaApp) -> Unit,
    onMostrarCompletados: (Boolean) -> Unit,
    onOrden: (OrdenListado) -> Unit,
    onFiltroPrioridad: (FiltroPrioridad) -> Unit,
    onCerrarSesion: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmandoCierre by remember { mutableStateOf(false) }
    val cambiosSinSincronizar = sinSincronizar.pendientes + sinSincronizar.conError

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
            // ---------- Cuenta ----------
            Text(text = "Cuenta", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Sesión iniciada como ${correo ?: "—"}",
                style = MaterialTheme.typography.bodyLarge
            )
            OutlinedButton(
                onClick = { confirmandoCierre = true },
                enabled = !cerrandoSesion,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (cerrandoSesion) "Cerrando sesión..." else "Cerrar sesión")
            }

            HorizontalDivider()

            // ---------- Preferencias (DataStore) ----------
            Text(text = "Preferencias", style = MaterialTheme.typography.titleLarge)
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

            HorizontalDivider()

            // ---------- Version (para relacionar los reportes de testers con el codigo exacto) ----------
            Text(text = "Acerca de esta versión", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Versión ${BuildConfig.VERSION_NAME} (código ${BuildConfig.VERSION_CODE})\n" +
                    "Ambiente: ${BuildConfig.AMBIENTE} · Compilación: ${BuildConfig.BUILD_TYPE}\n" +
                    "Código: ${BuildConfig.GIT_DESCRIBE}\n" +
                    "Generada: ${BuildConfig.BUILD_DATE}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (confirmandoCierre) {
        AlertDialog(
            onDismissRequest = { confirmandoCierre = false },
            title = { Text("¿Cerrar sesión?") },
            text = {
                Text(
                    if (cambiosSinSincronizar > 0) {
                        "Hay $cambiosSinSincronizar cambio(s) que todavía no se sincronizaron. Se intentarán " +
                            "enviar ahora; si no hay conexión, quedarán guardados en este dispositivo, solo " +
                            "para tu cuenta, y se enviarán cuando vuelvas a iniciar sesión."
                    } else {
                        "Tus recordatorios están guardados en tu cuenta. Se quitarán de este dispositivo " +
                            "y volverán a aparecer cuando inicies sesión de nuevo."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmandoCierre = false
                    onCerrarSesion()
                }) { Text("Cerrar sesión") }
            },
            dismissButton = {
                TextButton(onClick = { confirmandoCierre = false }) { Text("Cancelar") }
            }
        )
    }
}
