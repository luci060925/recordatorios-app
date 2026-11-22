package com.mansilla.recordatoriosapp.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mansilla.recordatoriosapp.data.auth.EstadoAuth
import com.mansilla.recordatoriosapp.ui.components.IndicadorCarga
import com.mansilla.recordatoriosapp.ui.screens.PantallaAcceso
import com.mansilla.recordatoriosapp.ui.viewmodel.SesionViewModel

/**
 * Decide que mostrar segun la sesion. La interfaz de la aplicacion solo existe mientras hay una
 * cuenta activa: al cerrar sesion se descarta todo su estado (navegacion y ViewModels), y una
 * cuenta nueva empieza desde cero sin ver nada de la anterior.
 */
@Composable
fun RaizApp(viewModelFactory: ViewModelProvider.Factory) {
    val sesionViewModel: SesionViewModel = viewModel(factory = viewModelFactory)
    val estado by sesionViewModel.estado.collectAsStateWithLifecycle()

    when (val sesion = estado) {
        EstadoAuth.Verificando -> Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            IndicadorCarga(modifier = Modifier.padding(padding), mensaje = "Verificando sesión...")
        }

        is EstadoAuth.SinSesion -> PantallaAcceso(viewModelFactory)

        // key(uid): si cambia la cuenta, la navegacion y los ViewModels se crean de nuevo.
        is EstadoAuth.ConSesion -> key(sesion.usuario.uid) {
            RecordatoriosNavHost(viewModelFactory = viewModelFactory)
        }
    }
}
