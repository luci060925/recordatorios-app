package com.mansilla.recordatoriosapp.data.auth

import kotlinx.coroutines.flow.StateFlow

/** Identidad autenticada. Es lo unico de la cuenta que conoce el resto de la app. */
data class UsuarioSesion(val uid: String, val email: String?)

/** Estados de la sesion, sin tipos del proveedor (Firebase). */
sealed interface EstadoAuth {
    /** Todavia se esta consultando si hay una sesion guardada (arranque de la app). */
    data object Verificando : EstadoAuth

    /** No hay sesion. [expirada] es true si se perdio o dejo de ser valida (no fue un cierre voluntario). */
    data class SinSesion(val expirada: Boolean = false) : EstadoAuth

    data class ConSesion(val usuario: UsuarioSesion) : EstadoAuth
}

enum class TipoErrorAuth {
    CREDENCIALES_INVALIDAS,
    EMAIL_EN_USO,
    CONTRASENA_DEBIL,
    EMAIL_INVALIDO,
    SIN_CONEXION,
    DEMASIADOS_INTENTOS,
    DESCONOCIDO
}

sealed interface ResultadoAuth {
    data object Exito : ResultadoAuth
    data class Error(val tipo: TipoErrorAuth) : ResultadoAuth
}

enum class ResultadoVerificacion {
    /** La sesion sigue siendo valida. */
    VALIDA,

    /** La cuenta ya no es valida (eliminada, deshabilitada o credenciales revocadas): se cerro la sesion. */
    INVALIDA,

    /** No se pudo comprobar (sin conexion): se conserva la sesion. */
    NO_VERIFICABLE
}

/** Lo que necesita el repositorio de datos para saber de quien son los datos. */
interface ProveedorSesion {
    val estado: StateFlow<EstadoAuth>

    /** uid de la sesion activa, o null si no hay. */
    fun uidActual(): String? = (estado.value as? EstadoAuth.ConSesion)?.usuario?.uid

    /** Comprueba con el servicio que la sesion siga siendo valida; si no lo es, la cierra. */
    suspend fun verificarSesion(): ResultadoVerificacion
}

/** Autenticacion (quien es la persona). La autorizacion la decide el servicio con sus reglas. */
interface AuthRepository : ProveedorSesion {
    suspend fun registrar(email: String, contrasena: String): ResultadoAuth
    suspend fun iniciarSesion(email: String, contrasena: String): ResultadoAuth
    suspend fun cerrarSesion()
}
