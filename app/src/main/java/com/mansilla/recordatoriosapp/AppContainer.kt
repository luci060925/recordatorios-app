package com.mansilla.recordatoriosapp

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.mansilla.recordatoriosapp.data.auth.AuthRepository
import com.mansilla.recordatoriosapp.data.auth.EstadoAuth
import com.mansilla.recordatoriosapp.data.auth.FirebaseAuthRepository
import com.mansilla.recordatoriosapp.data.connectivity.MonitorConectividad
import com.mansilla.recordatoriosapp.data.local.AppDatabase
import com.mansilla.recordatoriosapp.data.local.MIGRACION_1_2
import com.mansilla.recordatoriosapp.data.prefs.PreferenciasRepository
import com.mansilla.recordatoriosapp.data.prefs.dataStorePreferencias
import com.mansilla.recordatoriosapp.data.remote.FirestoreRecordatorioRemoteDataSource
import com.mansilla.recordatoriosapp.data.repository.EjecutorTransacciones
import com.mansilla.recordatoriosapp.data.repository.RecordatorioRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Contenedor de dependencias. Crea una unica instancia de cada componente de datos
 * (Room, DataStore, Firebase y repositorios) y las comparte con los ViewModel.
 */
class AppContainer(contexto: Context) {

    private val appContext = contexto.applicationContext

    /** Alcance de aplicacion para la sincronizacion en segundo plano. */
    val alcanceApp = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val monitorConectividad = MonitorConectividad(appContext)

    // ---------- Room (instancia unica) ----------
    private val baseDeDatos: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "recordatorios.db"
    ).addMigrations(MIGRACION_1_2).build()

    // ---------- DataStore (instancia unica) ----------
    val preferenciasRepository = PreferenciasRepository(appContext.dataStorePreferencias)

    // ---------- Firebase ----------
    // El ambiente "dev" usa el Firebase Emulator Suite de la PC; "prod" usa el proyecto real.
    // Debe configurarse antes del primer uso de cada servicio.
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance().also {
        if (BuildConfig.USAR_EMULADOR_FIREBASE) it.useEmulator(BuildConfig.EMULADOR_HOST, PUERTO_AUTH_EMULADOR)
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance().also {
        // Room es la unica fuente local: la cache de Firestore queda solo en memoria.
        it.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
        if (BuildConfig.USAR_EMULADOR_FIREBASE) it.useEmulator(BuildConfig.EMULADOR_HOST, PUERTO_FIRESTORE_EMULADOR)
    }

    val authRepository: AuthRepository = FirebaseAuthRepository(firebaseAuth, alcanceApp)

    // ---------- Repositorio local-first ----------
    val repositorio: RecordatorioRepositoryImpl = RecordatorioRepositoryImpl(
        dao = baseDeDatos.recordatorioDao(),
        remoto = FirestoreRecordatorioRemoteDataSource(firestore, monitorConectividad::hayConexion),
        sesion = authRepository,
        transacciones = object : EjecutorTransacciones {
            override suspend fun <T> ejecutar(bloque: suspend () -> T): T =
                baseDeDatos.withTransaction { bloque() }
        },
        alcance = alcanceApp
    )

    /** Sincroniza al abrir la app, al iniciar sesion y cada vez que se recupera la conexion. */
    fun iniciarSincronizacion() {
        repositorio.iniciarSincronizacionAutomatica(monitorConectividad.conectado)
        // Al abrir la app con una sesion guardada, se comprueba que siga siendo valida.
        alcanceApp.launch {
            authRepository.estado.first { it !is EstadoAuth.Verificando }
            authRepository.verificarSesion()
        }
    }

    private companion object {
        const val PUERTO_AUTH_EMULADOR = 9099
        const val PUERTO_FIRESTORE_EMULADOR = 8080
    }
}
