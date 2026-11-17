# Recordatorios App

Programación Mobile (Teclab) · Actividad 2.

App Android con Kotlin y Jetpack Compose (Material 3) para gestionar recordatorios. Esta segunda versión suma listado, creación y detalle conectados con Navigation Compose, un formulario validado y una arquitectura de presentación con ViewModel, UiState y StateFlow sobre un repositorio en memoria.

| Entrega | Contenido |
|---|---|
| Actividad 1 | Modelo de dominio (data class + sealed classes), datos simulados y primera pantalla. |
| **Actividad 2** (actual) | Pantallas de listado, creación y detalle; componentes reutilizables; formulario validado; ViewModel + UiState + StateFlow; repositorio en memoria; navegación. |

## Arquitectura

```
Pantalla (Compose) ──eventos──▶ ViewModel ──suspend──▶ RecordatorioRepository (interfaz)
       ▲                           │                           │
       └──── StateFlow<UiState> ◀──┘                 RepositorioEnMemoria
```

- **Repositorio**: la interfaz `RecordatorioRepository` declara `obtenerRecordatorios`, `obtenerPorId` y `guardarRecordatorio`. `RepositorioEnMemoria` la implementa. El ViewModel depende solo de la interfaz.
- **ViewModel**: mantiene `MutableStateFlow` privados y expone `StateFlow` de solo lectura; actualiza con `update { it.copy(...) }`. Las operaciones asincrónicas (carga y guardado) se simulan con `delay` dentro de `viewModelScope`. No guarda referencias a Activity ni a View.
- **UiState** (todos inmutables):
  - `ListadoUiState`: `Cargando`, `Vacio`, `Contenido`, `Error`.
  - `DetalleUiState`: `Cargando`, `Contenido`, `Error`.
  - `FormularioUiState`: valores de los campos, mensajes de validación y `EstadoEnvio` (`Inactivo`, `Guardando`, `Exito`, `Error`).
- **Pantallas**: cada una tiene un punto de entrada con estado, que obtiene el ViewModel y observa el flujo con `collectAsStateWithLifecycle`, y un contenido sin estado que solo recibe datos y lambdas.

## Navegación

| Ruta | Pantalla | Argumentos | Acciones |
|---|---|---|---|
| `listado` (inicial) | `PantallaListado` | — | `onCrearRecordatorio`, `onRecordatorioClick(id)` |
| `crear` | `PantallaCrear` | — | `onGuardar`, `onCancelar` (`popBackStack()`) |
| `detalle/{id}` | `PantallaDetalle` | `id: Int` | `onVolver` (`popBackStack()`) |

El `NavController` se crea solo en `RecordatoriosNavHost`. Hacia el detalle viaja únicamente el identificador.

## Formulario y validaciones

- Título: al menos 4 caracteres después de `trim()`.
- Descripción: opcional, máximo 200 caracteres (con contador).
- Categoría y prioridad: obligatorias, elegidas entre las opciones definidas.
- **Guardar** se deshabilita si el formulario es inválido o si hay un guardado en curso.
- Los errores se comunican con `isError`, un texto "Error: …" y la propiedad semántica `error(...)`; el color no es el único recurso.

## Simulación de estados

`RecordatoriosApplication` crea el repositorio con tres banderas para comprobar todos los estados sin base de datos ni servicio remoto:

```kotlin
RepositorioEnMemoria(
    simularListaVacia = false,    // estado vacío con acción "Crear recordatorio"
    simularErrorCarga = false,    // error de carga con botón "Reintentar"
    simularErrorGuardado = false  // error al guardar con "Reintentar guardado"
)
```

## Estructura

```
app/src/main/java/com/mansilla/recordatoriosapp/
├── MainActivity.kt                 Activity: tema + NavHost
├── RecordatoriosApplication.kt     Instancia única del repositorio
├── Recordatorio.kt                 Modelo de dominio y sealed classes
├── DatosSimulados.kt               Datos de prueba
├── data/                           RecordatorioRepository, RepositorioEnMemoria
└── ui/
    ├── components/                 TarjetaRecordatorio, EstadoPantalla, CamposFormulario
    ├── navigation/                 RecordatoriosNavHost y rutas
    ├── screens/                    PantallaListado, PantallaCrear, PantallaDetalle
    ├── state/                      ListadoUiState, DetalleUiState, FormularioUiState
    ├── theme/                      Tema Material 3
    └── viewmodel/                  RecordatoriosViewModel y su Factory
```

## Cómo ejecutar

Abrir en Android Studio, sincronizar Gradle y ejecutar la configuración `app` en un emulador o dispositivo con depuración USB.
