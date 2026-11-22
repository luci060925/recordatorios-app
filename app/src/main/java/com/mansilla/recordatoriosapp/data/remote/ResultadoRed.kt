package com.mansilla.recordatoriosapp.data.remote

enum class TipoErrorRemoto {
    /** El servicio rechazo los datos o la solicitud. */
    SOLICITUD_INVALIDA,

    /** El recurso no existe en el servicio. */
    NO_ENCONTRADO,

    /** La cuenta autenticada no tiene permiso (reglas de seguridad del servicio). */
    SIN_PERMISO,

    /** No hay una identidad valida: falta iniciar sesion o la sesion dejo de ser valida. */
    NO_AUTENTICADO,

    /** Falla del servicio (no disponible, error interno). */
    SERVIDOR,

    OTRO
}

/**
 * Resultado de una operacion remota. El detalle del proveedor (excepciones y codigos de
 * Firebase) se interpreta en la capa de datos; la interfaz solo recibe un estado
 * recuperable, nunca una excepcion ni un tipo del SDK.
 */
sealed interface ResultadoRed<out T> {
    data class Exito<T>(val valor: T) : ResultadoRed<T>
    data class ErrorRemoto(val codigo: String, val tipo: TipoErrorRemoto, val mensaje: String) : ResultadoRed<Nothing>
    data object Timeout : ResultadoRed<Nothing>
    data object SinConexion : ResultadoRed<Nothing>
    data class ErrorDesconocido(val mensaje: String) : ResultadoRed<Nothing>
}
