package com.mansilla.recordatoriosapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mansilla.recordatoriosapp.ui.navigation.RecordatoriosNavHost
import com.mansilla.recordatoriosapp.ui.theme.RecordatoriosAppTheme
import com.mansilla.recordatoriosapp.ui.viewmodel.RecordatoriosViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repositorio = (application as RecordatoriosApplication).repositorio
        val viewModelFactory = RecordatoriosViewModelFactory(repositorio)

        setContent {
            RecordatoriosAppTheme {
                RecordatoriosNavHost(viewModelFactory = viewModelFactory)
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
