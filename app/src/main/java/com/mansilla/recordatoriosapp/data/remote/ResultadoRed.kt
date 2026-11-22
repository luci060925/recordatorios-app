package com.mansilla.recordatoriosapp.data.remote

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.InterruptedIOException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class TipoErrorHttp {
    /** 4xx distinto de 404: el servicio rechazo la solicitud. */
    SOLICITUD_INVALIDA,

    /** 404: el recurso no existe en el servicio. */
    NO_ENCONTRADO,

    /** 5xx: falla del servicio. */
    SERVIDOR,

    OTRO
}

/**
 * Resultado de una llamada remota. La interpretacion de codigos HTTP, timeouts y
 * falta de conexion ocurre en la capa de datos; las pantallas nunca ven una excepcion.
 */
sealed interface ResultadoRed<out T> {
    data class Exito<T>(val valor: T) : ResultadoRed<T>
    data class ErrorHttp(val codigo: Int, val tipo: TipoErrorHttp, val mensaje: String) : ResultadoRed<Nothing>
    data object Timeout : ResultadoRed<Nothing>
    data object SinConexion : ResultadoRed<Nothing>
    data class ErrorDesconocido(val mensaje: String) : ResultadoRed<Nothing>
}

internal fun clasificarCodigoHttp(codigo: Int): TipoErrorHttp = when (codigo) {
    404 -> TipoErrorHttp.NO_ENCONTRADO
    in 400..499 -> TipoErrorHttp.SOLICITUD_INVALIDA
    in 500..599 -> TipoErrorHttp.SERVIDOR
    else -> TipoErrorHttp.OTRO
}

/** Ejecuta una llamada y traduce excepciones y codigos HTTP a [ResultadoRed]. */
internal suspend fun <T> llamadaSegura(bloque: suspend () -> T): ResultadoRed<T> =
    try {
        ResultadoRed.Exito(bloque())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        ResultadoRed.ErrorHttp(e.code(), clasificarCodigoHttp(e.code()), e.message())
    } catch (e: SocketTimeoutException) {
        ResultadoRed.Timeout
    } catch (e: InterruptedIOException) {
        // Timeouts de OkHttp que no son SocketTimeoutException (por ejemplo, call timeout).
        ResultadoRed.Timeout
    } catch (e: UnknownHostException) {
        ResultadoRed.SinConexion
    } catch (e: ConnectException) {
        ResultadoRed.SinConexion
    } catch (e: IOException) {
        ResultadoRed.SinConexion
    } catch (e: Exception) {
        // Respuestas mal formadas u otros fallos inesperados.
        ResultadoRed.ErrorDesconocido(e.message ?: "Error desconocido")
    }
