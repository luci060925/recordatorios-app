# Recordatorios App

Actividad 1 - Programación Mobile (Teclab).

App Android con Kotlin y Jetpack Compose (Material 3) que muestra una lista de recordatorios.

## Qué incluye
- Modelo: data class `Recordatorio` con propiedades `val`, opcionales `String?` y validaciones (`init` + `require`).
- Sealed classes: `Categoria`, `Prioridad` y `EstadoRecordatorio`.
- Colecciones: List, Set (categorías únicas), Map (categoría -> color) y filtros.
- UI: Scaffold + LazyColumn + Card de Material 3.

## Archivos
- `MainActivity.kt` - pantalla Compose
- `Recordatorio.kt` - modelo
- `DatosSimulados.kt` - datos de prueba

## Cómo ejecutar
Abrir en Android Studio, sincronizar Gradle y ejecutar la configuración `app` en un emulador.
