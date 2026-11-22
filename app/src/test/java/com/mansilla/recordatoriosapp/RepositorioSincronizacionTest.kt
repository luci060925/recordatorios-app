package com.mansilla.recordatoriosapp

import com.mansilla.recordatoriosapp.data.auth.EstadoAuth
import com.mansilla.recordatoriosapp.data.auth.ResultadoVerificacion
import com.mansilla.recordatoriosapp.data.local.CodigoOperacion
import com.mansilla.recordatoriosapp.data.local.CodigoSync
import com.mansilla.recordatoriosapp.data.remote.RecordatorioDto
import com.mansilla.recordatoriosapp.data.repository.DatosRecordatorio
import com.mansilla.recordatoriosapp.data.repository.EstadoSincronizacion
import com.mansilla.recordatoriosapp.data.repository.FiltroRecordatorios
import com.mansilla.recordatoriosapp.data.repository.RecordatorioRepositoryImpl
import com.mansilla.recordatoriosapp.data.repository.ResultadoSincronizacion
import com.mansilla.recordatoriosapp.data.repository.SinSesionException
import com.mansilla.recordatoriosapp.data.repository.TipoErrorSync
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Estrategia local-first con cuentas: pendientes, offline, errores, duplicados y aislamiento entre usuarios. */
class RepositorioSincronizacionTest {

    private val dao = FakeDao()
    private val remoto = FakeRemote()
    private val sesion = FakeSesion("ana")
    private var reloj = 1_000L
    private var contadorId = 0

    private fun TestScope.repositorio() = RecordatorioRepositoryImpl(
        dao = dao,
        remoto = remoto,
        sesion = sesion,
        transacciones = TransaccionDirecta,
        alcance = backgroundScope,
        reloj = { reloj++ },
        generarId = { "id-${++contadorId}" }
    )

    private val datos = DatosRecordatorio("Estudiar Room", "Repasar DAO", Categoria.Estudio, Prioridad.Alta)

    // ======================= Offline, pendientes y duplicados =======================

    @Test
    fun crearSinConexion_seGuardaEnRoomComoPendienteDeLaCuenta() = runTest {
        val repo = repositorio()
        remoto.modo = ModoFake.SIN_CONEXION

        val id = repo.crear(datos)
        val resultado = repo.sincronizar()

        val guardado = dao.datos.value.getValue(id)
        assertEquals("ana", guardado.ownerUid)
        assertEquals(CodigoSync.PENDIENTE, guardado.estadoSync)
        assertEquals(CodigoOperacion.CREAR, guardado.operacionPendiente)
        assertEquals(TipoErrorSync.SIN_CONEXION, (resultado as ResultadoSincronizacion.Fallida).tipo)
        assertEquals(EstadoSync.PENDIENTE, repo.observarRecordatorios().first().single().estadoSync)
    }

    @Test
    fun alRecuperarLaRed_seEnviaSinCrearUnRegistroNuevo() = runTest {
        val repo = repositorio()
        remoto.modo = ModoFake.SIN_CONEXION
        val id = repo.crear(datos)
        repo.sincronizar()

        remoto.modo = ModoFake.NORMAL
        assertEquals(ResultadoSincronizacion.Completada, repo.sincronizar())

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

        assertEquals(1, remoto.llamadasGuardar)
        assertEquals(1, resultados.count { it == ResultadoSincronizacion.Completada })
        assertEquals(4, resultados.count { it == ResultadoSincronizacion.YaEnCurso })
        assertEquals(1, remoto.servidor.size)
    }

    @Test
    fun siSePierdeLaRespuesta_elReintentoNoDuplica() = runTest {
        val repo = repositorio()
        val id = repo.crear(datos)

        // La nube aplica el cambio pero la app recibe un timeout.
        remoto.modo = ModoFake.PERDER_RESPUESTA
        val primero = repo.sincronizar()
        assertEquals(TipoErrorSync.TIMEOUT, (primero as ResultadoSincronizacion.Fallida).tipo)
        assertEquals(CodigoSync.PENDIENTE, dao.datos.value.getValue(id).estadoSync)
        assertEquals(1, remoto.servidor.size)

        // El reintento usa el mismo id: la nube reemplaza el documento, no crea otro.
        remoto.modo = ModoFake.NORMAL
        assertEquals(ResultadoSincronizacion.Completada, repo.sincronizar())
        assertEquals(2, remoto.llamadasGuardar)
        assertEquals(1, remoto.servidor.size)
        assertEquals(1, dao.datos.value.size)
        assertEquals(CodigoSync.SINCRONIZADO, dao.datos.value.getValue(id).estadoSync)
    }

    @Test
    fun errorDelServicio_conservaLosDatosLocalesYQuedaPendiente() = runTest {
        val repo = repositorio()
        val id = repo.crear(datos)
        remoto.modo = ModoFake.ERROR_SERVIDOR

        val resultado = repo.sincronizar()

        assertEquals(TipoErrorSync.SERVIDOR, (resultado as ResultadoSincronizacion.Fallida).tipo)
        assertEquals(CodigoSync.PENDIENTE, dao.datos.value.getValue(id).estadoSync)
        assertTrue(repo.estadoSincronizacion.value is EstadoSincronizacion.Error)
    }

    @Test
    fun solicitudRechazada_seMarcaErrorYSeReintentaManualmente() = runTest {
        val repo = repositorio()
        val id = repo.crear(datos)
        remoto.modo = ModoFake.SOLICITUD_INVALIDA

        repo.sincronizar()
        assertEquals(CodigoSync.ERROR, dao.datos.value.getValue(id).estadoSync)
        // Una sincronizacion automatica no reintenta lo rechazado.
        val llamadas = remoto.llamadasGuardar
        remoto.modo = ModoFake.NORMAL
        repo.sincronizar()
        assertEquals(llamadas, remoto.llamadasGuardar)

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
        remoto.alRecibirGuardar = {
            repo.actualizar(id, datos.copy(titulo = "Titulo nuevo"))
            remoto.alRecibirGuardar = {}
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
        assertEquals("Otro titulo", remoto.servidor.getValue(id).titulo)
        assertEquals(1, remoto.servidor.size)
    }

    @Test
    fun modificarSinConexion_seAplicaEnRoomYSeEnviaAlVolverLaRed() = runTest {
        val repo = repositorio()
        val id = repo.crear(datos)
        repo.sincronizar()

        remoto.modo = ModoFake.SIN_CONEXION
        repo.actualizar(id, datos.copy(titulo = "Editado sin red"))
        repo.sincronizar()
        // En la nube todavia esta el titulo anterior; en Room ya esta el nuevo.
        assertEquals("Estudiar Room", remoto.servidor.getValue(id).titulo)
        assertEquals("Editado sin red", repo.observarPorId(id).first()?.titulo)
        assertEquals(EstadoSync.PENDIENTE, repo.observarPorId(id).first()?.estadoSync)

        remoto.modo = ModoFake.NORMAL
        repo.sincronizar()
        assertEquals("Editado sin red", remoto.servidor.getValue(id).titulo)
    }

    @Test
    fun eliminarSinConexion_ocultaElRegistroYLoBorraAlConfirmarse() = runTest {
        val repo = repositorio()
        val id = repo.crear(datos)
        repo.sincronizar()

        remoto.modo = ModoFake.SIN_CONEXION
        repo.eliminar(id)
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
        remoto.servidor["srv-1"] = dtoRemoto("srv-1", "De la nube")
        remoto.servidor[idLocal] = dtoRemoto(idLocal, "Version vieja de la nube")

        // El registro local esta rechazado (ERROR): no se reenvia, pero tampoco se pisa al descargar.
        dao.actualizar(dao.datos.value.getValue(idLocal).copy(estadoSync = CodigoSync.ERROR))
        repo.sincronizar()

        assertEquals("De la nube", dao.datos.value.getValue("srv-1").titulo)
        assertEquals(CodigoSync.SINCRONIZADO, dao.datos.value.getValue("srv-1").estadoSync)
        assertEquals("ana", dao.datos.value.getValue("srv-1").ownerUid)
        assertEquals("Estudiar Room", dao.datos.value.getValue(idLocal).titulo)
    }

    @Test
    fun alDescargar_seQuitanLosSincronizadosQueYaNoExistenEnLaNube() = runTest {
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
        assertEquals(1, repo.observarRecordatorios(FiltroRecordatorios(prioridad = Prioridad.Baja)).first().size)
        assertEquals(1, repo.observarRecordatorios(FiltroRecordatorios(incluirCompletados = false)).first().size)
        assertEquals(1, repo.observarPorEstado(EstadoRecordatorio.Completado).first().size)
    }

    // ======================= Cuentas y aislamiento =======================

    @Test
    fun unaCuentaNoVeLosDatosLocalesDeOtraEnElMismoDispositivo() = runTest {
        val repo = repositorio()
        repo.crear(datos.copy(titulo = "Secreto de Ana"))
        repo.sincronizar()
        assertEquals(1, repo.observarRecordatorios().first().size)

        // Cambia la cuenta activa en el mismo dispositivo.
        sesion.iniciar("beto")
        assertTrue(repo.observarRecordatorios().first().isEmpty())

        repo.crear(datos.copy(titulo = "Dato de Beto"))
        assertEquals(listOf("Dato de Beto"), repo.observarRecordatorios().first().map { it.titulo })

        // Al volver Ana, ve solo lo suyo.
        sesion.iniciar("ana")
        assertEquals(listOf("Secreto de Ana"), repo.observarRecordatorios().first().map { it.titulo })
    }

    @Test
    fun unaCuentaNoPuedeModificarNiEliminarRegistrosAjenos() = runTest {
        val repo = repositorio()
        val idDeAna = repo.crear(datos)
        repo.sincronizar()

        sesion.iniciar("beto")
        repo.actualizar(idDeAna, datos.copy(titulo = "Pisado por Beto"))
        repo.cambiarEstado(idDeAna, EstadoRecordatorio.Cancelado)
        repo.eliminar(idDeAna)

        val original = dao.datos.value.getValue(idDeAna)
        assertEquals("Estudiar Room", original.titulo)
        assertEquals(CodigoSync.SINCRONIZADO, original.estadoSync)
        assertNull(original.operacionPendiente)
        assertNull(repo.observarPorId(idDeAna).first())
    }

    @Test
    fun laSincronizacionUsaSoloElUidDeLaCuentaActiva() = runTest {
        val repo = repositorio()
        remoto.servidorDe("ana")["a1"] = dtoRemoto("a1", "De Ana")
        remoto.servidorDe("beto")["b1"] = dtoRemoto("b1", "De Beto")

        repo.sincronizar()
        assertEquals(setOf("a1"), dao.datos.value.keys)
        assertEquals(setOf("ana"), remoto.uidsUsados.toSet())

        sesion.iniciar("beto")
        repo.sincronizar()
        assertEquals(setOf("ana", "beto"), remoto.uidsUsados.toSet())
        assertEquals(setOf("b1"), dao.filasDe("beto").map { it.id }.toSet())
        assertEquals(setOf("a1"), dao.filasDe("ana").map { it.id }.toSet())
    }

    @Test
    fun sinSesion_noSeLeeNiSeEscribeNiSeSincroniza() = runTest {
        val repo = repositorio()
        repo.crear(datos)
        sesion.cerrar()

        assertTrue(repo.observarRecordatorios().first().isEmpty())
        assertEquals(ResultadoSincronizacion.SinSesion, repo.sincronizar())
        try {
            repo.crear(datos)
            fail("Deberia rechazar la escritura sin sesion")
        } catch (e: SinSesionException) {
            // esperado
        }
    }

    @Test
    fun sinPermisoEnLaNube_marcaErrorYNoReintentaSolo() = runTest {
        val repo = repositorio()
        val id = repo.crear(datos)
        remoto.modo = ModoFake.SIN_PERMISO

        repo.sincronizar()

        val fila = dao.datos.value.getValue(id)
        assertEquals(CodigoSync.ERROR, fila.estadoSync)
        assertTrue(fila.ultimoError!!.contains("permiso"))
    }

    @Test
    fun sesionInvalida_seDetectaYSeCierraLaSesion() = runTest {
        val repo = repositorio()
        repo.crear(datos)
        remoto.modo = ModoFake.NO_AUTENTICADO
        sesion.resultadoVerificacion = ResultadoVerificacion.INVALIDA

        val resultado = repo.sincronizar()

        assertEquals(TipoErrorSync.SESION_INVALIDA, (resultado as ResultadoSincronizacion.Fallida).tipo)
        assertEquals(1, sesion.verificaciones)
        // La sesion quedo cerrada con aviso de expiracion.
        assertEquals(EstadoAuth.SinSesion(expirada = true), sesion.estado.value)
        // Los cambios siguen guardados, ligados a la cuenta.
        assertEquals(1, dao.filasDe("ana").size)
    }

    // ======================= Cierre de sesion =======================

    @Test
    fun cerrarSesionConConexion_borraLoLocalYAlVolverASesionSeRecuperaDeLaNube() = runTest {
        val repo = repositorio()
        repo.crear(datos)
        repo.crear(datos.copy(titulo = "Segundo recordatorio"))

        val sinSincronizar = repo.prepararCierreDeSesion()

        // Se envio todo y se limpio el dispositivo.
        assertEquals(0, sinSincronizar)
        assertTrue(dao.filasDe("ana").isEmpty())
        assertEquals(2, remoto.servidor.size)

        // "Otro dispositivo" / nueva sesion: los datos se recuperan desde la nube.
        repo.sincronizar()
        assertEquals(2, repo.observarRecordatorios().first().size)
        assertTrue(repo.observarRecordatorios().first().all { it.estadoSync == EstadoSync.SINCRONIZADO })
    }

    @Test
    fun cerrarSesionSinConexion_conservaLosCambiosPendientesSoloParaSuCuenta() = runTest {
        val repo = repositorio()
        repo.crear(datos.copy(titulo = "Ya sincronizado"))
        repo.sincronizar()
        remoto.modo = ModoFake.SIN_CONEXION
        repo.crear(datos.copy(titulo = "Creado sin red"))

        val sinSincronizar = repo.prepararCierreDeSesion()

        // Lo sincronizado se borro del dispositivo; lo pendiente se conserva para no perderlo.
        assertEquals(1, sinSincronizar)
        assertEquals(listOf("Creado sin red"), dao.filasDe("ana").map { it.titulo })

        // Otra cuenta en el mismo dispositivo no ve nada de eso.
        sesion.cerrar()
        sesion.iniciar("beto")
        assertTrue(repo.observarRecordatorios().first().isEmpty())

        // Cuando Ana vuelve y hay red, el cambio pendiente se envia.
        sesion.iniciar("ana")
        remoto.modo = ModoFake.NORMAL
        repo.sincronizar()
        assertEquals(setOf("Ya sincronizado", "Creado sin red"), remoto.servidor.values.map { it.titulo }.toSet())
    }

    @Test
    fun cerrarSesion_noAfectaLosDatosDeOtraCuentaGuardadosEnElDispositivo() = runTest {
        val repo = repositorio()
        repo.crear(datos)
        repo.sincronizar()
        sesion.iniciar("beto")
        remoto.modo = ModoFake.SIN_CONEXION
        repo.crear(datos.copy(titulo = "Pendiente de Beto"))

        sesion.iniciar("ana")
        repo.prepararCierreDeSesion()

        assertTrue(dao.filasDe("ana").isEmpty())
        assertEquals(listOf("Pendiente de Beto"), dao.filasDe("beto").map { it.titulo })
        assertFalse(dao.filasDe("beto").isEmpty())
    }

    private fun dtoRemoto(id: String, titulo: String?) = RecordatorioDto(
        id = id, titulo = titulo, descripcion = null, categoria = "PERSONAL", prioridad = "MEDIA",
        estado = "PENDIENTE", fecha = 10L, actualizadoEn = 10L
    )
}
