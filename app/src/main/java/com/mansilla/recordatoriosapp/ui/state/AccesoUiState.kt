package com.mansilla.recordatoriosapp.ui.state

import com.mansilla.recordatoriosapp.data.auth.TipoErrorAuth

const val LONGITUD_MINIMA_CONTRASENA = 6

enum class ModoAcceso { INICIAR_SESION, REGISTRARSE }

/**
 * Estado inmutable de la pantalla de acceso (inicio de sesion y registro): valores de los campos,
 * validaciones, autenticacion en curso, error de autenticacion y aviso de sesion perdida.
 */
data class AccesoUiState(
    val modo: ModoAcceso = ModoAcceso.INICIAR_SESION,
    val email: String = "",
    val contrasena: String = "",
    val interactuado: Boolean = false,
    /** Autenticacion en curso: se deshabilita el boton para evitar envios repetidos. */
    val enCurso: Boolean = false,
    val error: TipoErrorAuth? = null,
    /** La sesion anterior se perdio o dejo de ser valida (no fue un cierre voluntario). */
    val sesionExpirada: Boolean = false
) {
    val emailValido: Boolean
        get() = EMAIL.matches(email.trim())
    val contrasenaValida: Boolean
        get() = contrasena.length >= LONGITUD_MINIMA_CONTRASENA

    val errorEmail: String?
        get() = if (interactuado && email.isNotEmpty() && !emailValido) "Ingresá un correo válido." else null
    val errorContrasena: String?
        get() = if (interactuado && contrasena.isNotEmpty() && !contrasenaValida) {
            "La contraseña debe tener al menos $LONGITUD_MINIMA_CONTRASENA caracteres."
        } else null

    val puedeEnviar: Boolean
        get() = emailValido && contrasenaValida && !enCurso

    private companion object {
        val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}

/** Mensaje para la persona usuaria. No revela si el correo existe: las credenciales fallan de forma generica. */
fun TipoErrorAuth.mensaje(): String = when (this) {
    TipoErrorAuth.CREDENCIALES_INVALIDAS -> "Correo o contraseña incorrectos."
    TipoErrorAuth.EMAIL_EN_USO -> "Ya existe una cuenta con ese correo. Probá iniciar sesión."
    TipoErrorAuth.CONTRASENA_DEBIL -> "La contraseña es demasiado débil. Usá al menos $LONGITUD_MINIMA_CONTRASENA caracteres."
    TipoErrorAuth.EMAIL_INVALIDO -> "El correo no tiene un formato válido."
    TipoErrorAuth.SIN_CONEXION -> "Sin conexión. Verificá tu red e intentá nuevamente."
    TipoErrorAuth.DEMASIADOS_INTENTOS -> "Demasiados intentos. Esperá unos minutos e intentá de nuevo."
    TipoErrorAuth.DESCONOCIDO -> "No se pudo completar la operación. Intentá nuevamente."
}
