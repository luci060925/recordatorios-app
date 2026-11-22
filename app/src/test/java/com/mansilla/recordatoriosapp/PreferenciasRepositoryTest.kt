package com.mansilla.recordatoriosapp

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mansilla.recordatoriosapp.data.prefs.FiltroPrioridad
import com.mansilla.recordatoriosapp.data.prefs.OrdenListado
import com.mansilla.recordatoriosapp.data.prefs.PreferenciasRepository
import com.mansilla.recordatoriosapp.data.prefs.PreferenciasUsuario
import com.mansilla.recordatoriosapp.data.prefs.TemaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Pruebas de DataStore sobre un archivo real, simulando el cierre y la reapertura de la app. */
class PreferenciasRepositoryTest {

    private lateinit var carpeta: File
    private val alcances = mutableListOf<CoroutineScope>()

    @Before
    fun preparar() {
        carpeta = Files.createTempDirectory("prefs-test").toFile()
    }

    @After
    fun limpiar() {
        alcances.forEach { it.cancel() }
        carpeta.deleteRecursively()
    }

    /** Equivale a "abrir la app": crea un DataStore nuevo sobre el mismo archivo. */
    private fun abrirApp(): androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
        val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        alcances += alcance
        return PreferenceDataStoreFactory.create(scope = alcance) { File(carpeta, "prefs.preferences_pb") }
    }

    /** Equivale a "cerrar la app": espera a que DataStore libere el archivo antes de reabrirlo. */
    private suspend fun cerrarApp() {
        alcances.forEach { it.coroutineContext[Job]?.cancelAndJoin() }
        alcances.clear()
        delay(100)
    }

    @Test
    fun sinDatosGuardados_seUsanLosValoresPredeterminados() = runBlocking {
        val repo = PreferenciasRepository(abrirApp())
        assertEquals(PreferenciasUsuario(), repo.preferencias.first())
    }

    @Test
    fun lasPreferenciasSeConservanAlCerrarYAbrirLaApp() = runBlocking {
        val primera = PreferenciasRepository(abrirApp())
        primera.actualizarTema(TemaApp.OSCURO)
        primera.actualizarMostrarCompletados(false)
        primera.actualizarOrden(OrdenListado.TITULO)
        primera.actualizarFiltroPrioridad(FiltroPrioridad.ALTA)
        cerrarApp()

        val reabierta = PreferenciasRepository(abrirApp()).preferencias.first()
        assertEquals(TemaApp.OSCURO, reabierta.tema)
        assertEquals(false, reabierta.mostrarCompletados)
        assertEquals(OrdenListado.TITULO, reabierta.orden)
        assertEquals(FiltroPrioridad.ALTA, reabierta.filtroPrioridad)
    }

    @Test
    fun soloSeConservaLoQueSeModifico_elRestoUsaElPredeterminado() = runBlocking {
        PreferenciasRepository(abrirApp()).actualizarTema(TemaApp.CLARO)
        cerrarApp()

        val reabierta = PreferenciasRepository(abrirApp()).preferencias.first()
        assertEquals(TemaApp.CLARO, reabierta.tema)
        assertEquals(PreferenciasUsuario().orden, reabierta.orden)
        assertEquals(PreferenciasUsuario().mostrarCompletados, reabierta.mostrarCompletados)
    }

    @Test
    fun unDatoInvalido_seReemplazaPorElPredeterminadoSinFallar() = runBlocking {
        val dataStore = abrirApp()
        // Simula un valor corrupto o de una version futura de la app.
        dataStore.edit {
            it[stringPreferencesKey("orden_listado")] = "ORDEN_QUE_NO_EXISTE"
            it[stringPreferencesKey("tema")] = "OSCURO"
        }

        val prefs = PreferenciasRepository(dataStore).preferencias.first()
        assertEquals(OrdenListado.FECHA_RECIENTE, prefs.orden)
        // Los demas valores validos no se ven afectados.
        assertEquals(TemaApp.OSCURO, prefs.tema)
    }
}
