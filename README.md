# Recordatorios App

Programación Mobile (Teclab) · Actividad 4 — versión candidata `4.0.0-rc1`.

App Android con Kotlin y Jetpack Compose (Material 3) para gestionar recordatorios. Esta versión final conserva la arquitectura local-first (Room como fuente de verdad) y agrega **Firebase Authentication** y **Cloud Firestore** para que cada persona acceda a sus recordatorios desde más de un dispositivo, con reglas de seguridad por usuario, aislamiento de datos entre cuentas y una compilación **release firmada** (APK y AAB).

| Entrega | Contenido |
|---|---|
| Actividad 1 | Modelo de dominio y primera pantalla. |
| Actividad 2 | Listado, creación y detalle; formulario validado; ViewModel + UiState + StateFlow; navegación. |
| Actividad 3 | Room, DataStore, repositorio local-first, sincronización con un servicio REST simulado y funcionamiento offline. |
| **Actividad 4** (actual) | Firebase (Auth + Firestore), reglas de seguridad, sesión por usuario, sabores `dev`/`prod`, versionado y firma release. |

## Arquitectura

```
Compose ──evento──▶ ViewModel ──▶ RecordatorioRepository (local-first, por cuenta)
   ▲                                  │                      │
   │                                  ▼                      ▼
   └── StateFlow<UiState> ◀── Room (fuente de verdad)   FirestoreRemoteDataSource ──▶ Cloud Firestore
                                  ownerUid = uid             users/{uid}/reminders/{id}
                                                                  ▲
Acceso:  Compose ──▶ AccesoViewModel ──▶ AuthRepository ──▶ Firebase Authentication (uid)
```

- **Compose nunca llama a Firebase.** Las pantallas reciben `UiState` y lambdas; el ViewModel habla con interfaces (`RecordatorioRepository`, `AuthRepository`); Firebase queda dentro de `data/auth` y `data/remote`.
- **Room sigue siendo la fuente local.** Cada fila tiene `ownerUid` y todas las consultas filtran por cuenta. La caché de Firestore se mantiene solo en memoria.
- **Identificadores estables:** el UUID del recordatorio es el id del documento en Firestore, así que un reintento reemplaza el mismo documento en lugar de crear una copia.
- **Modelos separados:** `RecordatorioDto` (nube) ↔ `RecordatorioEntity` (Room) ↔ `Recordatorio` (dominio), con mapeos explícitos. La interfaz no recibe tipos de Firebase: los errores del proveedor se traducen a `ResultadoRed` y `TipoErrorAuth`.

### Cloud Firestore

Estructura: `users/{uid}/reminders/{reminderId}` con los campos `titulo`, `descripcion`, `categoria`, `prioridad`, `estado`, `fecha` y `actualizadoEn`. El uid está en la ruta, no dentro del documento.

Reglas (`firebase/firestore.rules`): solo el dueño de la cuenta puede leer y escribir su colección, el servidor valida el contenido (título de al menos 4 caracteres, opciones válidas, campos permitidos) y todo lo demás está cerrado. La autorización se decide siempre en el servidor, nunca por la visibilidad de los botones.

### Sesión y datos locales

| Situación | Qué ocurre |
|---|---|
| Registro / inicio de sesión | Firebase Authentication (correo y contraseña). Estados: en curso, éxito, error. |
| Reapertura con sesión existente | El SDK restaura la sesión y la app entra directo al listado; además se verifica que siga siendo válida. |
| Sesión perdida o inválida (cuenta eliminada o deshabilitada) | La app cierra la sesión y avisa en la pantalla de acceso. |
| Cierre de sesión | Se envía lo pendiente, se borra del dispositivo lo que ya está en la nube y se descarta el estado de la interfaz. Los cambios que no pudieron sincronizarse se conservan, asociados a su cuenta y ocultos para cualquier otra. |
| Otra cuenta en el mismo dispositivo | Empieza desde cero: no ve datos de la anterior. |

La app **no guarda contraseñas ni tokens**: la sesión la gestiona el SDK de Firebase. Las preferencias de DataStore son solo configuración de la interfaz. El respaldo automático de Android está desactivado para que la base local no se copie fuera del dispositivo.

## Ambientes, versionado y firma

| | `dev` | `prod` |
|---|---|---|
| Backend | Firebase Emulator Suite en la PC (`10.0.2.2`) | Proyecto Firebase real |
| `applicationId` | `com.mansilla.recordatoriosapp.dev` | `com.mansilla.recordatoriosapp` |
| Configuración | `app/src/dev/google-services.json` (proyecto ficticio `demo-recordatorios`) | `app/src/prod/google-services.json` (propio del equipo, no se versiona) |
| HTTP sin cifrar | Solo hacia `10.0.2.2` (network security config del sabor `dev`) | No permitido |

- **Versión:** se define una sola vez en `gradle.properties` (`APP_VERSION_CODE`, `APP_VERSION_NAME`). La pantalla Ajustes muestra versión, ambiente, tipo de compilación, commit y fecha, para relacionar los reportes de testers con el código exacto.
- **Firma release:** `app/build.gradle.kts` lee las credenciales de `keystore.properties`, que está en `.gitignore` (ver `keystore.properties.example`). La clave `.jks` se guarda fuera del proyecto. El build release usa R8 (reducción y ofuscación de código y recursos).

## Cómo ejecutar

**Requisitos:** Android Studio con Android SDK 36, JDK 11+ (incluido en Android Studio), Node.js 18+ y Java 11+ para el emulador de Firestore.

### Ambiente `dev` (Firebase Emulator Suite)

1. Instalar las herramientas de Firebase: `npm install -g firebase-tools`.
2. Iniciar los emuladores de Authentication (puerto 9099) y Firestore (puerto 8080) con las reglas del proyecto:

   ```bash
   cd firebase
   firebase emulators:start --only auth,firestore --project demo-recordatorios
   ```
3. Abrir el proyecto en Android Studio, elegir la variante `devDebug` y ejecutar en un emulador de Android (accede a la PC como `10.0.2.2`).

### Ambiente `prod` (proyecto Firebase real)

1. En la [consola de Firebase](https://console.firebase.google.com) crear un proyecto, agregar una app Android con el paquete `com.mansilla.recordatoriosapp`, y descargar `google-services.json` en `app/src/prod/` (hay un `google-services.json.example` como guía).
2. Activar **Authentication → Correo electrónico y contraseña** y crear una base **Cloud Firestore**.
3. Publicar las reglas: `cd firebase && firebase deploy --only firestore:rules --project <id-del-proyecto>`.
4. Ejecutar la variante `prodDebug` o generar la release (ver abajo).

### Generar la versión release firmada

1. Crear la clave (una sola vez) y copiar `keystore.properties.example` como `keystore.properties` completando las rutas y contraseñas:

   ```bash
   keytool -genkeypair -keystore <ruta-segura>/recordatorios-release.jks -alias recordatorios -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Compilar:

   ```bash
   ./gradlew assembleDevRelease bundleDevRelease      # ambiente dev
   ./gradlew assembleProdRelease bundleProdRelease    # ambiente prod (requiere google-services.json de prod)
   ```
   El APK queda en `app/build/outputs/apk/<ambiente>/release/` y el AAB en `app/build/outputs/bundle/<ambiente>Release/`.
3. Verificar la firma: `apksigner verify --print-certs app-<ambiente>-release.apk`.

## Pruebas

| Tipo | Comando | Qué cubre |
|---|---|---|
| Unitarias (57) | `./gradlew testDevDebugUnitTest` | Repositorio (offline, pendientes, duplicados, aislamiento entre cuentas, cierre de sesión, permisos y sesión inválida), acceso, mapeos, DataStore y validaciones. |
| Instrumentadas | `./gradlew connectedDevDebugAndroidTest` | Migración de Room 1→2 y consultas reales del DAO filtradas por cuenta. |
| Reglas de Firestore (11) | `cd firebase && firebase emulators:exec --only firestore --project demo-recordatorios "npm --prefix rules-tests test"` | Una cuenta no puede leer, listar ni escribir datos de otra; validación de contenido. |

## Estructura

```
app/src/main/java/com/mansilla/recordatoriosapp/
├── MainActivity.kt, RecordatoriosApplication.kt, AppContainer.kt
├── Recordatorio.kt                       Modelo de dominio
├── data/
│   ├── auth/                             Sesión y autenticación (Firebase Authentication)
│   ├── local/                            Room: entidad, DAO, base de datos y migración
│   ├── remote/                           Fuente remota (Firestore), DTO y mapeos
│   ├── mapper/, prefs/, connectivity/    Mapeos, DataStore y conectividad
│   └── repository/                       Repositorio local-first y sincronización
└── ui/                                   components, navigation, screens, state, theme, viewmodel
app/src/dev|prod/                          Configuración por ambiente (Firebase, red)
firebase/                                  Reglas de Firestore, configuración de emuladores y pruebas de reglas
```
