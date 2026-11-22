package com.mansilla.recordatoriosapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mansilla.recordatoriosapp.ui.screens.PantallaAjustes
import com.mansilla.recordatoriosapp.ui.screens.PantallaCrear
import com.mansilla.recordatoriosapp.ui.screens.PantallaDetalle
import com.mansilla.recordatoriosapp.ui.screens.PantallaListado

object Rutas {
    const val LISTADO = "listado"
    const val CREAR = "crear"
    const val AJUSTES = "ajustes"
    const val ARG_ID = "id"
    const val DETALLE = "detalle/{$ARG_ID}"
    const val EDITAR = "editar/{$ARG_ID}"

    fun detalle(id: String) = "detalle/$id"
    fun editar(id: String) = "editar/$id"
}

@Composable
fun RecordatoriosNavHost(
    viewModelFactory: ViewModelProvider.Factory
) {
    // El NavController se administra solo en este nivel: las pantallas y los
    // componentes reciben lambdas y nunca el NavController.
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Rutas.LISTADO
    ) {
        composable(Rutas.LISTADO) {
            PantallaListado(
                viewModelFactory = viewModelFactory,
                onCrearRecordatorio = { navController.navigate(Rutas.CREAR) },
                onRecordatorioClick = { id -> navController.navigate(Rutas.detalle(id)) },
                onAbrirAjustes = { navController.navigate(Rutas.AJUSTES) }
            )
        }

        composable(Rutas.CREAR) {
            PantallaCrear(
                recordatorioId = null,
                viewModelFactory = viewModelFactory,
                // popBackStack vuelve a la pantalla anterior, sin apilar otra copia.
                onCancelar = { navController.popBackStack() },
                onGuardadoExitoso = { navController.popBackStack() }
            )
        }

        composable(
            route = Rutas.EDITAR,
            arguments = listOf(navArgument(Rutas.ARG_ID) { type = NavType.StringType })
        ) { entrada ->
            val id = entrada.arguments?.getString(Rutas.ARG_ID) ?: return@composable
            PantallaCrear(
                recordatorioId = id,
                viewModelFactory = viewModelFactory,
                onCancelar = { navController.popBackStack() },
                onGuardadoExitoso = { navController.popBackStack() }
            )
        }

        composable(
            route = Rutas.DETALLE,
            arguments = listOf(navArgument(Rutas.ARG_ID) { type = NavType.StringType })
        ) { entrada ->
            // Solo viaja el identificador, nunca el objeto completo.
            val id = entrada.arguments?.getString(Rutas.ARG_ID) ?: return@composable
            PantallaDetalle(
                recordatorioId = id,
                viewModelFactory = viewModelFactory,
                onVolver = { navController.popBackStack() },
                onEditar = { idEditar -> navController.navigate(Rutas.editar(idEditar)) }
            )
        }

        composable(Rutas.AJUSTES) {
            PantallaAjustes(
                viewModelFactory = viewModelFactory,
                onVolver = { navController.popBackStack() }
            )
        }
    }
}
