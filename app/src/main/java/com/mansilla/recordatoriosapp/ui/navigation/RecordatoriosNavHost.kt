package com.mansilla.recordatoriosapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mansilla.recordatoriosapp.ui.screens.PantallaCrear
import com.mansilla.recordatoriosapp.ui.screens.PantallaDetalle
import com.mansilla.recordatoriosapp.ui.screens.PantallaListado
import com.mansilla.recordatoriosapp.ui.viewmodel.RecordatoriosViewModelFactory

object Rutas {
    const val LISTADO = "listado"
    const val CREAR = "crear"
    const val ARG_ID = "id"
    const val DETALLE = "detalle/{$ARG_ID}"

    fun detalle(id: Int) = "detalle/$id"
}

@Composable
fun RecordatoriosNavHost(
    viewModelFactory: RecordatoriosViewModelFactory
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
                onRecordatorioClick = { id -> navController.navigate(Rutas.detalle(id)) }
            )
        }

        composable(Rutas.CREAR) {
            PantallaCrear(
                viewModelFactory = viewModelFactory,
                // popBackStack vuelve al listado existente, sin apilar otra copia.
                onCancelar = { navController.popBackStack() },
                onGuardadoExitoso = { navController.popBackStack() }
            )
        }

        composable(
            route = Rutas.DETALLE,
            arguments = listOf(navArgument(Rutas.ARG_ID) { type = NavType.IntType })
        ) { entrada ->
            // Solo viaja el identificador, nunca el objeto completo.
            val id = entrada.arguments?.getInt(Rutas.ARG_ID) ?: return@composable
            PantallaDetalle(
                recordatorioId = id,
                viewModelFactory = viewModelFactory,
                onVolver = { navController.popBackStack() }
            )
        }
    }
}
