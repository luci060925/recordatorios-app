package com.mansilla.recordatoriosapp.data.auth

import java.io.IOException

/**
 * Indica si un fallo se debe a la red (sin conexion, host inalcanzable, timeout).
 *
 * El SDK de Firebase no siempre lo informa con FirebaseNetworkException: segun el caso llega como una
 * excepcion generica cuya causa es una IOException, o con el codigo ERROR_NETWORK_REQUEST_FAILED.
 * Se recorre la cadena de causas para reconocerlo en cualquiera de esas formas.
 */
fun Throwable.esErrorDeRed(): Boolean =
    generateSequence(this) { it.cause }
        .take(PROFUNDIDAD_MAXIMA)
        .any { causa ->
            causa is IOException ||
                causa.message.orEmpty().contains("network", ignoreCase = true) ||
                causa.message.orEmpty().contains("unreachable", ignoreCase = true) ||
                causa.message.orEmpty().contains("NETWORK_REQUEST_FAILED", ignoreCase = true)
        }

private const val PROFUNDIDAD_MAXIMA = 6

/**
 * Indica si un fallo del servicio de autenticacion significa que la cuenta o su credencial ya no son
 * validas (cuenta eliminada o deshabilitada, token de renovacion invalido o vencido).
 *
 * El SDK informa estos casos con distintos tipos de excepcion segun el servicio; por eso tambien se
 * reconoce el codigo del servicio que viaja en el mensaje.
 */
fun Throwable.indicaSesionInvalida(): Boolean =
    generateSequence(this) { it.cause }
        .take(PROFUNDIDAD_MAXIMA)
        .any { causa ->
            val mensaje = causa.message.orEmpty()
            CODIGOS_SESION_INVALIDA.any { codigo -> mensaje.contains(codigo, ignoreCase = true) }
        }

private val CODIGOS_SESION_INVALIDA = listOf(
    "INVALID_REFRESH_TOKEN",
    "TOKEN_EXPIRED",
    "USER_NOT_FOUND",
    "USER_DISABLED",
    "USER_TOKEN_EXPIRED",
    "INVALID_USER_TOKEN",
    "CREDENTIAL_TOO_OLD",
    "The user's credential is no longer valid"
)
