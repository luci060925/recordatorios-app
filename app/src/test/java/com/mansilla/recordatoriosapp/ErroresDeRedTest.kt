package com.mansilla.recordatoriosapp

import com.mansilla.recordatoriosapp.data.auth.esErrorDeRed
import com.mansilla.recordatoriosapp.data.auth.indicaSesionInvalida
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reconocimiento de fallos de red, para informar "Sin conexion" en lugar de un error generico. */
class ErroresDeRedTest {

    @Test
    fun unaIOExceptionEsErrorDeRed() {
        assertTrue(IOException("fallo").esErrorDeRed())
        assertTrue(UnknownHostException("host").esErrorDeRed())
        assertTrue(ConnectException("Network is unreachable").esErrorDeRed())
    }

    @Test
    fun seReconoceLaCausaDentroDeUnaExcepcionGenerica() {
        val generica = RuntimeException("An internal error has occurred", IOException("Network is unreachable"))
        assertTrue(generica.esErrorDeRed())
    }

    @Test
    fun seReconoceElMensajeDeRedAunSinCausa() {
        assertTrue(RuntimeException("A network error has occurred").esErrorDeRed())
        assertTrue(RuntimeException("ERROR_NETWORK_REQUEST_FAILED").esErrorDeRed())
    }

    @Test
    fun otrosErroresNoSeConfundenConRed() {
        assertFalse(RuntimeException("Contrasena incorrecta").esErrorDeRed())
        assertFalse(IllegalStateException("estado invalido", IllegalArgumentException("x")).esErrorDeRed())
    }

    @Test
    fun seReconocenLosCodigosDeSesionInvalida() {
        assertTrue(RuntimeException("INVALID_REFRESH_TOKEN").indicaSesionInvalida())
        assertTrue(RuntimeException("An error: USER_NOT_FOUND").indicaSesionInvalida())
        assertTrue(RuntimeException("x", RuntimeException("TOKEN_EXPIRED")).indicaSesionInvalida())
        assertTrue(RuntimeException("The user's credential is no longer valid. The user must sign in again.").indicaSesionInvalida())
    }

    @Test
    fun otrosFallosNoSeTomanComoSesionInvalida() {
        assertFalse(IOException("Network is unreachable").indicaSesionInvalida())
        assertFalse(RuntimeException("Contrasena incorrecta").indicaSesionInvalida())
    }
}
