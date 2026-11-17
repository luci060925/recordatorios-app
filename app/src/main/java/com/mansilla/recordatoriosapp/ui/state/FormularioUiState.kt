package com.mansilla.recordatoriosapp.ui.state

import com.mansilla.recordatoriosapp.Categoria
import com.mansilla.recordatoriosapp.Prioridad

const val LONGITUD_MINIMA_TITULO = 4
const val LONGITUD_MAXIMA_DESCRIPCION = 200

/** Resultado del envio del formulario. */
sealed interface EstadoEnvio {
    data object Inactivo : EstadoEnvio
    data object Guardando : EstadoEnvio
    data object Exito : EstadoEnvio
    data class Error(val mensaje: String) : EstadoEnvio
}

/**
 * Estado inmutable del formulario de creacion: valores actuales, mensajes
 * de validacion y estado del envio. Cada cambio produce una nueva instancia.
 */
data class FormularioUiState(
    val titulo: String = "",
    val descripcion: String = "",
    val categoria: Categoria? = null,
    val prioridad: Prioridad? = null,
    // Pasa a true cuando la persona modifica algun campo, para no mostrar
    // errores antes de que empiece a completar el formulario.
    val interactuado: Boolean = false,
    val estadoEnvio: EstadoEnvio = EstadoEnvio.Inactivo
) {
    val tituloValido: Boolean
        get() = titulo.trim().length >= LONGITUD_MINIMA_TITULO
    val descripcionValida: Boolean
        get() = descripcion.length <= LONGITUD_MAXIMA_DESCRIPCION
    val categoriaValida: Boolean
        get() = categoria != null && categoria in OPCIONES_CATEGORIA
    val prioridadValida: Boolean
        get() = prioridad != null

    val errorTitulo: String?
        get() = if (interactuado && !tituloValido) {
            "El titulo debe tener al menos $LONGITUD_MINIMA_TITULO caracteres."
        } else null
    val errorDescripcion: String?
        get() = if (!descripcionValida) {
            "La descripcion no puede superar los $LONGITUD_MAXIMA_DESCRIPCION caracteres."
        } else null
    val errorCategoria: String?
        get() = if (interactuado && !categoriaValida) "Selecciona una categoria." else null
    val errorPrioridad: String?
        get() = if (interactuado && !prioridadValida) "Selecciona una prioridad." else null

    val formularioValido: Boolean
        get() = tituloValido && descripcionValida && categoriaValida && prioridadValida
    val enviando: Boolean
        get() = estadoEnvio == EstadoEnvio.Guardando

    /** El boton de guardado se habilita solo con formulario valido y sin envio en curso. */
    val puedeGuardar: Boolean
        get() = formularioValido && !enviando && estadoEnvio != EstadoEnvio.Exito
}

val OPCIONES_CATEGORIA: List<Categoria> =
    listOf(Categoria.Estudio, Categoria.Trabajo, Categoria.Personal)
val OPCIONES_PRIORIDAD: List<Prioridad> =
    listOf(Prioridad.Alta, Prioridad.Media, Prioridad.Baja)
