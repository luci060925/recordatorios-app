package com.mansilla.recordatoriosapp.data.prefs

enum class TemaApp(val etiqueta: String) {
    SISTEMA("Seguir al sistema"),
    CLARO("Claro"),
    OSCURO("Oscuro")
}

enum class OrdenListado(val etiqueta: String) {
    FECHA_RECIENTE("Más recientes primero"),
    FECHA_ANTIGUA("Más antiguos primero"),
    TITULO("Por título (A-Z)"),
    PRIORIDAD("Por prioridad")
}

enum class FiltroPrioridad(val etiqueta: String) {
    TODAS("Todas"),
    ALTA("Alta"),
    MEDIA("Media"),
    BAJA("Baja")
}

/**
 * Modelo propio de preferencias: la app nunca trabaja con claves de DataStore.
 * Los valores por defecto se usan cuando no hay datos guardados, cuando el dato
 * guardado no es valido o cuando la lectura falla de forma recuperable.
 */
data class PreferenciasUsuario(
    val tema: TemaApp = TemaApp.SISTEMA,
    val mostrarCompletados: Boolean = true,
    val orden: OrdenListado = OrdenListado.FECHA_RECIENTE,
    val filtroPrioridad: FiltroPrioridad = FiltroPrioridad.TODAS
)
