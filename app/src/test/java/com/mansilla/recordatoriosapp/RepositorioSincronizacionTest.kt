package com.mansilla.recordatoriosapp

import com.mansilla.recordatoriosapp.data.local.CodigoOperacion
import com.mansilla.recordatoriosapp.data.local.CodigoSync
import com.mansilla.recordatoriosapp.data.remote.RecordatorioDto
import com.mansilla.recordatoriosapp.data.repository.DatosRecordatorio
import com.mansilla.recordatoriosapp.data.repository.EstadoSincronizacion
import com.mansilla.recordatoriosapp.data.repository.RecordatorioRepositoryImpl
import com.mansilla.recordatoriosapp.data.repository.ResultadoSincronizacion
import com.mansilla.recordatoriosapp.data.repository.TipoErrorSync
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la estrategia local-first: pendientes, offline, errores y ausencia de duplicados. */
class RepositorioSincronizacionTest {

    private val dao = FakeDao()
    private val remoto = FakeRemote()
    private var reloj = 1_000L
    private var contadorId = 0

    private fun TestScope.repositorio() = RecordatorioRepositoryImpl(
        dao = dao,
        remoto = remoto,
        transacciones = TransaccionDirecta,
        alcance = backgroundScope,
        reloj = { reloj++ },
        generarId = { "id-${++contadorId}" }
    )

    private val datos = DatosRecordatorio("Estudiar Room", "Repasar DAO", Categoria.Estudio, Prioridad.Alta)

    @Test
    fun crearSinConexion_seGuardaEnRoomComoPendiente() = runTest {
        val repo = repositorio()
        remoto.modo = ModoFake.SIN_CONEXION

        val id = repo.crear(datos)
        val resultado = repo.sincronizar()

        val guardado = dao.datos.value.getValue(id)
        assertEquals(CodigoSync.PENDIENTE, guardado.estadoSync)
        assertEquals(CodigoOperacion.CREAR, guardado.operacionPendiente)
        assertTrue(resultado is ResultadoSincronizacion.Fallida)
        assertEquals(TipoErrorSync.SIN_CONEXION, (resultado as ResultadoSincronizacion.Fallida).tipo)
        // La interfaz sigue viendo el registro aunque no hubo red.
        assertEquals(1, repo.observarRecordatorios().first().size)
        assertEquals(EstadoSync.PENDIENTE, repo.observarRecordatorios().first().single().estadoSync)
    }

    @Test
    fun alRecuperarLaRed_seEnviaSinCrearUnRegistroNuevo() = runTest {
        val repo = repositorio()
        remoto.modo = ModoFake.SIN_CONEXION
        val id = repo.crear(datos)
        repo.sincronizar()

        remoto.modo = ModoFake.NORMAL
        val resultado = repo.sincronizar()

        assertEquals(ResultadoSincronizacion.Completada, resultado)
        assertEquals(CodigoSync.SINCRONIZADO, dao.datos.value.getValue(id).estadoSync)
        assertNull(dao.datos.value.getValue(id).operacionPendiente)
        assertEquals(1, remoto.servidor.size)
        assertEquals(1, dao.datos.value.size)
    }

    @Test
    fun variasSincronizacionesSimultaneas_envianUnaSolaVez() = runTest {
        val repo = repositorio()
        remoto.latenciaMs = 100
        repo.crear(datos)

        val resultados = (1..5).map { async { repo.sincronizar() } }.awaitAll()

        assertEquals(1, remoto.llamadasCrear)
        assertEquals(1, resultados.count { it == ResultadoSincronizacion.Completada })
        assertEquals(4, resultados.count { it == ResultadoSincronizacion.YaEnCurso })
        assertEquals(1, remoto.servidor.size)
    }

    @Test
    fun siSePierdeLaRespuesta_elReintentoNoDuplica() = runTest {
        val repo = repositorio()
        val id = repo.crear(datos)

        // El servidor aplica el cambio pero la app recibe un timeout.
        remoto.modo = ModoFake.PERDER_RESPUESTA
        val primero = repo.sincronizar()
        assertEquals(TipoErrorSync.TIMEOUT, (primero as ResultadoSincronizacion.Fallida).tipo)
        assertEquals(CodigoSync.PENDIENTE, dao.datos.value.getValue(id).estadoSync)
        assertEquals(1, remoto.servidor.size)

        // El reintento usa el mismo id: el servidor lo actualiza, no crea otro.
        remoto.modo = ModoFake.NORMAL
        assertEquals(ResultadoSincronizacion.Completada, repo.sincronizar())
        assertEquals(2, remoto.llamadasCrear)
        assertEquals(1, remoto.servidor.size)
        assertEquals(1, dao.datos.value.size)
        assertEquals(CodigoSync.SINCRONIZADO, dao.datos.value.getValue(id).estadoSync)
    }

    @Test
    fun errorDelServidor_conservaLosDatosLocalesYQuedaPendiente() = runTest {
        val repo = repositorio()
        val id = repo.crear(datos)
        remoto.modo = ModoFake.ERROR_500

        val resultado = repo.sincronizar()

        assertEquals(TipoErrorSync.SERVIDOR, (resultado as ResultadoSincronizacion.Fallida).tipo)
        assertEquals(CodigoSync.PENDIENTE, dao.datos.value.getValue(id).estadoSync)
        assertTrue(repo.estadoSincronizacion.value is EstadoSincronizacion.Error)
    }

    @Test
    fun solicitudRechazada_seMarcaErrorYSeReintentaManualmente() = runTest {
        val repo = repositorio()
        val id = repo.crear(datos)
        remoto.modo = ModoFake.ERROR_400

        repo.sincronizar()
        assertEquals(CodigoSync.ERROR, dao.datos.value.getValue(id).estadoSync)
        // Una sincronizacion automatica no reintenta lo rechazado.
        val llamadas = remoto.llamadasCrear
        remoto.modo = ModoFake.NORMAL
        repo.sincronizar()
        assertEquals(llamadas, remoto.llamadasCrear)

        // El reintento manual si.
        repo.sincronizar(reintentarErrores = true)
        assertEquals(CodigoSync.SINCRONIZADO, dao.datos.value.getValue(id).estadoSync)
        assertEquals(1, remoto.servidor.size)
    }

    @Test
    fun siElRegistroCambiaMientrasSeEnvia_noSeMarcaComoSincronizado() = runTest {
        val repo = repositorio()
        val id = repo.crear(datos)
        // El usuario edita el titulo justo mientras se envia la creacion.
        remoto.alRecibirCrear = {
            repo.actualizar(id, datos.copy(titulo = "Titulo nuevo"))
            remoto.alRecibirCrear = {}
        }

        repo.sincronizar()
        assertEquals(CodigoSync.PENDIENTE, dao.datos.value.getValue(id).estadoSync)

        repo.sincronizar()
        assertEquals(CodigoSync.SINCRONIZADO, dao.datos.value.getValue(id).estadoSync)
        assertEquals("Titulo nuevo", remoto.servidor.getValue(id).titulo)
        assertEquals(1, remoto.servidor.size)
    }

    @Test
    fun editarUnRegistroSincronizado_quedaPendienteDeActualizar() = runTest {
        val repo = repositorio()
        val id = repo.crear(datos)
        repo.sincronizar()

        repo.actualizar(id, datos.copy(titulo = "Otro titulo"))
        assertEquals(CodigoOperacion.ACTUALIZAR, dao.datos.value.getValue(id).operacionPendiente)

        repo.sincronizar()
        assertEquals(1, remoto.llamadasActualizar)
        assertEquals("Otro titulo", remoto.servidor.getValue(id).titulo)
        assertEquals(1, remoto.servidor.size)
    }

    @Test
    fun eliminarSinConexion_ocultaElRegistroYLoBorraAlConfirmarse() = runTest {
        val repo = repositorio()
        val id = repo.crear(datos)
        repo.sincronizar()

        remoto.modo = ModoFake.SIN_CONEXION
        repo.eliminar(id)
        // Deja de verse en la interfaz, pero sigue en Room hasta confirmarse.
        assertTrue(repo.observarRecordatorios().first().isEmpty())
        assertNull(repo.observarPorId(id).first())
        assertEquals(CodigoOperacion.ELIMINAR, dao.datos.value.getValue(id).operacionPendiente)

        remoto.modo = ModoFake.NORMAL
        repo.sincronizar()
        assertTrue(dao.datos.value.isEmpty())
        assertTrue(remoto.servidor.isEmpty())
    }

    @Test
    fun alDescargar_incorporaLoNuevoPeroNoPisaRegistrosSinConfirmar() = runTest {
        val repo = repositorio()
        val idLocal = repo.crear(datos)
        remoto.servidor["srv-1"] = dtoRemoto("srv-1", "Del servidor")
        // El servidor tiene una version vieja del registro local: no debe pisarlo.
        remoto.servidor[idLocal] = dtoRemoto(idLocal, "Version vieja del servidor")

        // El registro local esta rechazado (ERROR): no se reenvia, pero tampoco se pisa al descargar.
        dao.actualizar(dao.datos.value.getValue(idLocal).copy(estadoSync = CodigoSync.ERROR))
        repo.sincronizar()

        assertEquals("Del servidor", dao.datos.value.getValue("srv-1").titulo)
        assertEquals(CodigoSync.SINCRONIZADO, dao.datos.value.getValue("srv-1").estadoSync)
        assertEquals("Estudiar Room", dao.datos.value.getValue(idLocal).titulo)
    }

    @Test
    fun alDescargar_seQuitanLosSincronizadosQueYaNoExistenEnElServicio() = runTest {
        val repo = repositorio()
        remoto.servidor["srv-1"] = dtoRemoto("srv-1", "Uno")
        remoto.servidor["srv-2"] = dtoRemoto("srv-2", "Dos")
        repo.sincronizar()
        assertEquals(2, dao.datos.value.size)

        remoto.servidor.remove("srv-2")
        repo.sincronizar()
        assertEquals(setOf("srv-1"), dao.datos.value.keys)
    }

    @Test
    fun alDescargar_seDescartanLosRegistrosInvalidos() = runTest {
        val repo = repositorio()
        remoto.servidor["a"] = dtoRemoto("a", "Valido")
        remoto.servidor["b"] = dtoRemoto("b", null)
        remoto.servidor["c"] = dtoRemoto("c", "   ")
        repo.sincronizar()
        assertEquals(setOf("a"), dao.datos.value.keys)
    }

    @Test
    fun filtrosYEstado_sinConexionSeguimosLeyendoDeRoom() = runTest {
        val repo = repositorio()
        remoto.modo = ModoFake.SIN_CONEXION
        val a = repo.crear(datos)
        repo.crear(datos.copy(titulo = "Baja prioridad", prioridad = Prioridad.Baja))
        repo.cambiarEstado(a, EstadoRecordatorio.Completado)

        assertEquals(2, repo.observarRecordatorios().first().size)
        assertEquals(1, repo.observarRecordatorios(
            com.mansilla.recordatoriosapp.data.repository.FiltroRecordatorios(prioridad = Prioridad.Baja)
        ).first().size)
        assertEquals(1, repo.observarRecordatorios(
            com.mansilla.recordatoriosapp.data.repository.FiltroRecordatorios(incluirCompletados = false)
        ).first().size)
        assertEquals(1, repo.observarPorEstado(EstadoRecordatorio.Completado).first().size)
    }

    private fun dtoRemoto(id: String, titulo: String?) = RecordatorioDto(
        id = id, titulo = titulo, descripcion = null, categoria = "PERSONAL", prioridad = "MEDIA",
        estado = "PENDIENTE", fecha = 10L, actualizadoEn = 10L
    )
}
