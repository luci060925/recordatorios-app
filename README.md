# Recordatorios App

Programación Mobile (Teclab) · Actividad 3.

App Android con Kotlin y Jetpack Compose (Material 3) para gestionar recordatorios. Esta tercera versión conserva los datos entre sesiones con **Room**, guarda preferencias con **DataStore**, funciona sin conexión con un repositorio **local-first** y se sincroniza con un servicio remoto mediante **Retrofit y OkHttp**, sin generar duplicados.

| Entrega | Contenido |
|---|---|
| Actividad 1 | Modelo de dominio (data class + sealed classes), datos simulados y primera pantalla. |
| Actividad 2 | Listado, creación y detalle; formulario validado; ViewModel + UiState + StateFlow; navegación. |
| **Actividad 3** (actual) | Room, DataStore, repositorio local-first, Retrofit/OkHttp, sincronización controlada y funcionamiento offline. |

## Qué guarda cada tecnología

| | DataStore (Preferences) | Room |
|---|---|---|
| Datos | Configuración simple: tema, orden, filtro de prioridad, mostrar completados | Recordatorios (registros estructurados) |
| Acceso | `Flow<PreferenciasUsuario>` | `Flow<List<RecordatorioEntity>>` y consultas por id, estado o prioridad |
| Ante datos inválidos | Usa el valor predeterminado | Esquema versionado (`app/schemas`) |

## Arquitectura

```
Compose ──evento──▶ ViewModel ──▶ RecordatorioRepository (local-first)
   ▲                                  │           │
   │                                  ▼           ▼
   └── StateFlow<UiState> ◀── Room (fuente de verdad)    Retrofit + OkHttp ──▶ Servicio remoto
                                      ▲           │
                                      └─ DTO → Entidad (mapeo) ─┘

Preferencias:  Compose ──▶ AjustesViewModel ──▶ PreferenciasRepository ──▶ DataStore
```

- **Room es la fuente de verdad.** Las pantallas solo leen de Room (a través del repositorio) y nunca dependen de la red ni de los DTO.
- **Tres modelos separados**, con funciones de mapeo explícitas (`data/mapper/Mapeos.kt`): `RecordatorioDto` (contrato del servicio), `RecordatorioEntity` (esquema de Room) y `Recordatorio` (dominio, lo único que ve la interfaz).
- **Escrituras**: se aplican primero en Room y quedan como `PENDIENTE` (con la operación `CREAR`, `ACTUALIZAR` o `ELIMINAR`). Luego se envían en segundo plano.
- **Sincronización**: primero se envían los pendientes y después se descarga el estado del servicio, sin pisar cambios locales sin confirmar. Se dispara al abrir la app, al recuperar la conexión, tras cada cambio local y a pedido del usuario.
- **Sin duplicados**:
  1. Cada recordatorio tiene un UUID generado en el dispositivo, que el servicio usa como clave: repetir un envío actualiza el mismo registro.
  2. Un `Mutex` impide dos sincronizaciones simultáneas y las solicitudes automáticas se combinan en una sola.
  3. Cada registro tiene una `version`; se marca como sincronizado solo si no cambió mientras se enviaba.
  4. Al descargar, un registro con cambios locales pendientes no se pisa.
- **Errores de red** (`ResultadoRed`): éxito, 4xx (solicitud inválida), 404, 5xx (servidor), timeout y sin conexión se interpretan en la capa de datos; la interfaz recibe un estado recuperable y conserva los datos locales.

## Navegación

| Ruta | Pantalla | Argumentos |
|---|---|---|
| `listado` (inicial) | Listado con estado de sincronización | — |
| `crear` | Formulario de alta | — |
| `editar/{id}` | Formulario de edición | `id: String` |
| `detalle/{id}` | Detalle, editar, completar y eliminar | `id: String` |
| `ajustes` | Preferencias (DataStore) | — |

## Configuración, compilación y ejecución

**Requisitos:** Android Studio (versión estable reciente) con Android SDK 36, JDK 11 o superior (incluido en Android Studio) y un emulador o un dispositivo con depuración USB.

1. Clonar el repositorio y abrir la carpeta en Android Studio. Esperar a que Gradle sincronice.
2. (Opcional) Configurar el servicio remoto: copiar `local.properties.example` como `local.properties` y completar los valores. Si no se define nada, la app usa el servicio simulado.

   | Propiedad | Descripción | Valor por defecto |
   |---|---|---|
   | `API_BASE_URL` | URL base del servicio remoto (termina en `/`) | `https://api.recordatorios.example.org/` |
   | `USAR_SERVICIO_SIMULADO` | `true` usa el servicio simulado incluido; `false` consume `API_BASE_URL` | `true` |

   `local.properties` no se sube al repositorio y el proyecto no contiene secretos ni credenciales.
3. Elegir un emulador o dispositivo y ejecutar la configuración `app`.

Desde la terminal:

```bash
./gradlew assembleDebug          # compila el APK en app/build/outputs/apk/debug
./gradlew testDebugUnitTest      # ejecuta las pruebas unitarias
```

### Servicio remoto

La app consume la siguiente API REST (`data/remote/RecordatorioApi.kt`). Los recursos se identifican con el UUID que genera el dispositivo.

| Método | Ruta | Respuesta |
|---|---|---|
| `GET` | `/recordatorios` | 200 con la lista |
| `POST` | `/recordatorios` | 201 si es nuevo; 200 si el id ya existía (se actualiza) |
| `PUT` | `/recordatorios/{id}` | 200, o 404 si no existe |
| `DELETE` | `/recordatorios/{id}` | 204, o 404 si no existe |

Con `USAR_SERVICIO_SIMULADO=true`, un interceptor de OkHttp (`BackendSimuladoInterceptor`) implementa este contrato y guarda sus datos en un archivo, de modo que sobreviven al cierre de la app. En **Ajustes → Servicio simulado** se puede cambiar su comportamiento para comprobar errores: 500, 400, timeout y timeout con respuesta perdida. Para probar la ausencia de conexión basta con activar el modo avión.

## Pruebas

`./gradlew testDebugUnitTest` ejecuta 43 pruebas:

- `RepositorioSincronizacionTest`: operaciones pendientes, offline, errores, reintentos y ausencia de duplicados (incluida la sincronización simultánea y la respuesta perdida).
- `ServicioRetrofitTest`: pila real de Retrofit/OkHttp contra el servicio simulado y clasificación de errores HTTP.
- `PreferenciasRepositoryTest`: DataStore sobre un archivo real, con cierre y reapertura, valores por defecto y dato inválido.
- `MapeosTest`, `OrdenListadoTest` y `FormularioUiStateTest`.

## Estructura

```
app/src/main/java/com/mansilla/recordatoriosapp/
├── MainActivity.kt, RecordatoriosApplication.kt, AppContainer.kt
├── Recordatorio.kt                       Modelo de dominio
├── data/
│   ├── local/                            Room: entidad, DAO y base de datos
│   ├── remote/                           Retrofit: DTO, API, resultado de red
│   │   └── simulado/                     Servicio simulado (interceptor de OkHttp)
│   ├── mapper/                           Mapeos DTO ↔ entidad ↔ dominio
│   ├── prefs/                            DataStore: modelo y repositorio de preferencias
│   ├── connectivity/                     Monitor de conectividad
│   └── repository/                       Repositorio local-first y sincronización
└── ui/
    ├── components/, navigation/, screens/, state/, theme/
    └── viewmodel/                        Listado, detalle, formulario y ajustes
```
