package com.mansilla.recordatoriosapp

import com.mansilla.recordatoriosapp.data.auth.EstadoAuth
import com.mansilla.recordatoriosapp.data.auth.ResultadoAuth
import com.mansilla.recordatoriosapp.data.auth.TipoErrorAuth
import com.mansilla.recordatoriosapp.ui.state.ModoAcceso
import com.mansilla.recordatoriosapp.ui.state.mensaje
import com.mansilla.recordatoriosapp.ui.viewmodel.AccesoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Estados de la pantalla de acceso: validacion, autenticacion en curso, error y sesion expirada. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccesoViewModelTest {

    private val sesion = FakeSesion(uidInicial = null)

    @Before
    fun preparar() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun limpiar() = Dispatchers.resetMain()

    private fun kotlinx.coroutines.test.TestScope.crear(): AccesoViewModel {
        val vm = AccesoViewModel(sesion)
        // El estado se comparte con WhileSubscribed: hace falta un suscriptor.
        backgroundScope.launch { vm.estado.collect { } }
        return vm
    }

    @Test
    fun conCamposVacios_noSePuedeEnviar() = runTest {
        val vm = crear()
        advanceUntilIdle()
        assertFalse(vm.estado.value.puedeEnviar)
        vm.enviar()
        advanceUntilIdle()
        assertEquals(0, sesion.intentosDeAcceso)
    }

    @Test
    fun correoInvalido_muestraMensajeYNoPermiteEnviar() = runTest {
        val vm = crear()
        vm.cambiarEmail("no-es-un-correo")
        vm.cambiarContrasena("secreta1")
        advanceUntilIdle()

        assertFalse(vm.estado.value.emailValido)
        assertEquals("Ingresá un correo válido.", vm.estado.value.errorEmail)
        assertFalse(vm.estado.value.puedeEnviar)
    }

    @Test
    fun contrasenaCorta_noPermiteEnviar() = runTest {
        val vm = crear()
        vm.cambiarEmail("ana@test.com")
        vm.cambiarContrasena("123")
        advanceUntilIdle()

        assertNull(vm.estado.value.errorEmail)
        assertTrue(vm.estado.value.errorContrasena!!.contains("al menos 6"))
        assertFalse(vm.estado.value.puedeEnviar)
    }

    @Test
    fun conDatosValidos_seInicia_sesionYSeLimpiaLaContrasena() = runTest {
        val vm = crear()
        vm.cambiarEmail("ana@test.com")
        vm.cambiarContrasena("secreta1")
        advanceUntilIdle()
        assertTrue(vm.estado.value.puedeEnviar)

        vm.enviar()
        advanceUntilIdle()

        assertEquals(1, sesion.intentosDeAcceso)
        assertFalse(vm.estado.value.enCurso)
        assertEquals("", vm.estado.value.contrasena)
        assertNull(vm.estado.value.error)
    }

    @Test
    fun durantelaAutenticacion_elBotonQuedaDeshabilitadoYNoSeRepiteElEnvio() = runTest {
        val compuerta = kotlinx.coroutines.CompletableDeferred<Unit>()
        sesion.compuerta = compuerta
        val vm = crear()
        vm.cambiarEmail("ana@test.com")
        vm.cambiarContrasena("secreta1")
        advanceUntilIdle()

        vm.enviar()
        runCurrent()
        // Autenticacion en curso: no se puede volver a enviar.
        assertTrue(vm.estado.value.enCurso)
        assertFalse(vm.estado.value.puedeEnviar)
        vm.enviar()
        runCurrent()
        assertEquals(1, sesion.intentosDeAcceso)

        compuerta.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.estado.value.enCurso)
        assertEquals(1, sesion.intentosDeAcceso)
    }

    @Test
    fun errorDeAutenticacion_seMuestraConMensajeYSeLimpiaAlEditar() = runTest {
        sesion.resultadoAuth = ResultadoAuth.Error(TipoErrorAuth.CREDENCIALES_INVALIDAS)
        val vm = crear()
        vm.cambiarEmail("ana@test.com")
        vm.cambiarContrasena("mala-clave")
        advanceUntilIdle()

        vm.enviar()
        advanceUntilIdle()

        assertEquals(TipoErrorAuth.CREDENCIALES_INVALIDAS, vm.estado.value.error)
        assertEquals("Correo o contraseña incorrectos.", vm.estado.value.error!!.mensaje())
        assertFalse(vm.estado.value.enCurso)

        vm.cambiarContrasena("otra-clave")
        advanceUntilIdle()
        assertNull(vm.estado.value.error)
    }

    @Test
    fun alternarEntreIniciarSesionYRegistrarse() = runTest {
        val vm = crear()
        advanceUntilIdle()
        assertEquals(ModoAcceso.INICIAR_SESION, vm.estado.value.modo)
        vm.cambiarModo(ModoAcceso.REGISTRARSE)
        advanceUntilIdle()
        assertEquals(ModoAcceso.REGISTRARSE, vm.estado.value.modo)
    }

    @Test
    fun cuandoLaSesionExpira_laPantallaDeAccesoLoAvisa() = runTest {
        val vm = crear()
        advanceUntilIdle()
        assertFalse(vm.estado.value.sesionExpirada)

        sesion.resultadoVerificacion = com.mansilla.recordatoriosapp.data.auth.ResultadoVerificacion.INVALIDA
        sesion.iniciar("ana")
        sesion.verificarSesion()
        advanceUntilIdle()

        assertEquals(EstadoAuth.SinSesion(expirada = true), sesion.estado.value)
        assertTrue(vm.estado.value.sesionExpirada)
    }
}
