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
