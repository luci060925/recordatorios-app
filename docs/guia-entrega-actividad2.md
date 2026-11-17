# Guía para armar el PDF de la Actividad 2

Nombre del archivo: `Apellido_Nombre_Actividad2_ProgramacionMobile.pdf`

## Primera página
- Nombre y apellido
- Materia: Programación Mobile
- Actividad: 2
- Dispositivo/emulador: Medium Phone API 36.1 (o el que uses)
- SDK: compileSdk 36 · targetSdk 36 · minSdk 24

## Capturas (una por línea, indicar la consigna)

| # | Captura | Consigna | Cómo obtenerla |
|---|---|---|---|
| 1 | Listado con recordatorios | 1 | Ejecutar con las tres banderas en `false` |
| 2 | Formulario de creación | 1 y 2 | Tocar el botón "+" (conviene mostrar un error de validación, p. ej. título de 2 letras) |
| 3 | Detalle | 1 y 5 | Tocar una tarjeta |
| 4 | Estado vacío | 3 | `simularListaVacia = true` en `RecordatoriosApplication.kt` y volver a ejecutar |
| 5 | Error con reintento | 3 | `simularErrorCarga = true`, volver a ejecutar |
| 6 | (Extra) Error de guardado | 3 y 4 | `simularErrorGuardado = true`, completar el formulario y guardar |
| 7 | Estructura del proyecto | — | Panel Project de Android Studio con los paquetes expandidos |

Dejar las tres banderas en `false` al terminar.

## Descripción de la organización (texto sugerido)
Cada pantalla tiene un punto de entrada con estado (`PantallaListado`, `PantallaCrear`, `PantallaDetalle`) que obtiene el `RecordatoriosViewModel` y observa su `StateFlow` con `collectAsStateWithLifecycle`. El contenido visual (`ContenidoListado`, `FormularioRecordatorio`, `ContenidoDetalle`) no tiene estado propio: recibe un `UiState` inmutable y comunica las acciones con lambdas. El ViewModel mantiene `MutableStateFlow` privados, expone `StateFlow` de solo lectura, aplica las validaciones y actualiza con `update { it.copy(...) }`. Depende de la interfaz `RecordatorioRepository`, implementada en memoria por `RepositorioEnMemoria`, por lo que la fuente de datos puede cambiarse sin tocar las pantallas.

## Tabla de rutas

| Ruta | Destino | Argumentos |
|---|---|---|
| `listado` (inicial) | PantallaListado | — |
| `crear` | PantallaCrear | — |
| `detalle/{id}` | PantallaDetalle | `id: Int` (solo el identificador) |

## Decisiones técnicas (registro breve)
- `sealed interface` para los UiState: estados mutuamente excluyentes y `when` exhaustivo, sin combinaciones contradictorias.
- `FormularioUiState` calcula validez y mensajes como propiedades derivadas; los errores se muestran recién después de que la persona interactúa.
- Guardar se deshabilita con formulario inválido o envío en curso (`puedeGuardar`).
- Errores accesibles: `isError`, texto "Error: …" y `semantics { error(...) }`.
- `NavController` solo en `RecordatoriosNavHost`; las pantallas reciben lambdas.
- Guardar/cancelar usan `popBackStack()`, así no se apila otro listado; el listado recarga en `ON_RESUME`.
- Banderas de simulación en `RecordatoriosApplication` para probar vacío, error de carga y error de guardado.
- Pruebas unitarias de las reglas de validación en `FormularioUiStateTest`.
