package com.mansilla.recordatoriosapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mansilla.recordatoriosapp.data.local.AppDatabase
import com.mansilla.recordatoriosapp.data.local.CodigoOperacion
import com.mansilla.recordatoriosapp.data.local.CodigoSync
import com.mansilla.recordatoriosapp.data.local.RecordatorioDao
import com.mansilla.recordatoriosapp.data.local.RecordatorioEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Consultas reales de Room (SQL) sobre una base en memoria: filtros y aislamiento entre cuentas. */
@RunWith(AndroidJUnit4::class)
class RecordatorioDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RecordatorioDao

    @Before
    fun abrir() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java).build()
        dao = db.recordatorioDao()
    }

    @After
    fun cerrar() = db.close()

    private fun fila(
        id: String, uid: String, titulo: String = "Titulo $id", prioridad: String = "ALTA",
        estado: String = "PENDIENTE", sync: String = CodigoSync.SINCRONIZADO,
        operacion: String? = null, fecha: Long = 1L
    ) = RecordatorioEntity(
        id = id, ownerUid = uid, titulo = titulo, descripcion = null, categoria = "ESTUDIO",
        prioridad = prioridad, estado = estado, fecha = fecha, actualizadoEn = fecha, version = 1,
        estadoSync = sync, operacionPendiente = operacion, ultimoError = null
    )

    @Test
    fun cadaCuentaVeSoloSusRecordatorios() = runBlocking {
        dao.insertarTodos(listOf(fila("a1", "ana"), fila("a2", "ana"), fila("b1", "beto")))

        assertEquals(setOf("a1", "a2"), dao.observarTodos("ana").first().map { it.id }.toSet())
        assertEquals(setOf("b1"), dao.observarTodos("beto").first().map { it.id }.toSet())
        assertEquals(emptyList<RecordatorioEntity>(), dao.observarTodos("carla").first())
    }

    @Test
    fun noSePuedeLeerPorIdUnRegistroAjeno() = runBlocking {
        dao.insertar(fila("a1", "ana"))

        assertEquals("a1", dao.observarPorId("ana", "a1").first()?.id)
        assertNull(dao.observarPorId("beto", "a1").first())
        assertNull(dao.obtenerPorId("beto", "a1"))
    }

    @Test
    fun losMarcadosParaEliminarNoSeMuestran() = runBlocking {
        dao.insertarTodos(
            listOf(fila("a1", "ana"), fila("a2", "ana", sync = CodigoSync.PENDIENTE, operacion = CodigoOperacion.ELIMINAR))
        )
        assertEquals(listOf("a1"), dao.observarTodos("ana").first().map { it.id })
    }

    @Test
    fun filtrosPorPrioridadEstadoYCompletados() = runBlocking {
        dao.insertarTodos(
            listOf(
                fila("a1", "ana", prioridad = "ALTA"),
                fila("a2", "ana", prioridad = "BAJA", estado = "COMPLETADO"),
                fila("a3", "ana", prioridad = "BAJA"),
                fila("b1", "beto", prioridad = "BAJA")
            )
        )
        assertEquals(setOf("a2", "a3"), dao.observarFiltrados("ana", "BAJA", true).first().map { it.id }.toSet())
        assertEquals(setOf("a1", "a3"), dao.observarFiltrados("ana", null, false).first().map { it.id }.toSet())
        assertEquals(listOf("a2"), dao.observarPorEstado("ana", "COMPLETADO").first().map { it.id })
    }

    @Test
    fun alCerrarSesionSePurganSoloLosSincronizadosDeESaCuenta() = runBlocking {
        dao.insertarTodos(
            listOf(
                fila("a1", "ana", sync = CodigoSync.SINCRONIZADO),
                fila("a2", "ana", sync = CodigoSync.PENDIENTE, operacion = CodigoOperacion.CREAR),
                fila("b1", "beto", sync = CodigoSync.SINCRONIZADO)
            )
        )

        assertEquals(1, dao.purgarSincronizados("ana"))

        assertEquals(listOf("a2"), dao.obtenerTodosParaSync("ana").map { it.id })
        assertEquals(listOf("b1"), dao.obtenerTodosParaSync("beto").map { it.id })
        assertEquals(1, dao.contarSinSincronizar("ana"))
    }

    @Test
    fun laVersionProtegeContraConfirmarUnCambioPosterior() = runBlocking {
        dao.insertar(fila("a1", "ana", sync = CodigoSync.PENDIENTE, operacion = CodigoOperacion.CREAR))
        // El registro cambio (version 2) mientras se enviaba la version 1: no debe quedar sincronizado.
        dao.actualizar(dao.obtenerPorId("ana", "a1")!!.copy(version = 2))

        assertEquals(0, dao.marcarSincronizado("a1", 1))
        assertEquals(CodigoSync.PENDIENTE, dao.obtenerPorId("ana", "a1")!!.estadoSync)
        assertEquals(1, dao.marcarSincronizado("a1", 2))
    }
}
