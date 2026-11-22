package com.mansilla.recordatoriosapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mansilla.recordatoriosapp.data.prefs.TemaApp
import com.mansilla.recordatoriosapp.ui.navigation.RecordatoriosNavHost
import com.mansilla.recordatoriosapp.ui.theme.RecordatoriosAppTheme
import com.mansilla.recordatoriosapp.ui.viewmodel.AppViewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val contenedor = (application as RecordatoriosApplication).contenedor
        val viewModelFactory = AppViewModelFactory(contenedor)

        setContent {
            // Las preferencias persistidas en DataStore determinan el tema desde el primer cuadro.
            val preferencias by contenedor.preferenciasRepository.preferencias
                .collectAsStateWithLifecycle(initialValue = null)

            val tema = preferencias?.tema
            val oscuro = when (tema) {
                TemaApp.CLARO -> false
                TemaApp.OSCURO -> true
                else -> isSystemInDarkTheme()
            }

            // Los íconos de las barras del sistema siguen el tema elegido en la app, no el del sistema.
            DisposableEffect(oscuro) {
                enableEdgeToEdge(
                    statusBarStyle = if (oscuro) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (oscuro) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
                onDispose { }
            }

            RecordatoriosAppTheme(darkTheme = oscuro) {
                // Hasta leer las preferencias no se dibuja contenido, para evitar un parpadeo de tema.
                if (preferencias != null) {
                    RecordatoriosNavHost(viewModelFactory = viewModelFactory)
                }
            }
        }
    }
}

fun nombreCategoria(categoria: Categoria): String =
    when (categoria) {
        Categoria.Estudio -> "Estudio"
        Categoria.Trabajo -> "Trabajo"
        Categoria.Personal -> "Personal"
    }

fun nombrePrioridad(prioridad: Prioridad): String =
    when (prioridad) {
        Prioridad.Alta -> "Alta"
        Prioridad.Media -> "Media"
        Prioridad.Baja -> "Baja"
    }

fun nombreEstado(estado: EstadoRecordatorio): String =
    when (estado) {
        EstadoRecordatorio.Pendiente -> "Pendiente"
        EstadoRecordatorio.Completado -> "Completado"
        EstadoRecordatorio.Cancelado -> "Cancelado"
    }

fun nombreEstadoSync(estado: EstadoSync): String =
    when (estado) {
        EstadoSync.SINCRONIZADO -> "Sincronizado"
        EstadoSync.PENDIENTE -> "Pendiente de sincronizar"
        EstadoSync.ERROR -> "Error de sincronización"
    }

fun formatearFecha(milisegundos: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR")).format(Date(milisegundos))
