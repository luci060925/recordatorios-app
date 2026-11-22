package com.mansilla.recordatoriosapp

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.mansilla.recordatoriosapp.data.connectivity.MonitorConectividad
import com.mansilla.recordatoriosapp.data.local.AppDatabase
import com.mansilla.recordatoriosapp.data.prefs.PreferenciasRepository
import com.mansilla.recordatoriosapp.data.prefs.dataStorePreferencias
import com.mansilla.recordatoriosapp.data.remote.RecordatorioApi
import com.mansilla.recordatoriosapp.data.remote.RetrofitRecordatorioRemoteDataSource
import com.mansilla.recordatoriosapp.data.remote.simulado.BackendSimuladoInterceptor
import com.mansilla.recordatoriosapp.data.remote.simulado.ControlServicioSimulado
import com.mansilla.recordatoriosapp.data.repository.EjecutorTransacciones
import com.mansilla.recordatoriosapp.data.repository.RecordatorioRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Contenedor de dependencias. Crea una unica instancia de cada componente de datos
 * (Room, DataStore, Retrofit/OkHttp y repositorios) y las comparte con los ViewModel.
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
    ).build()

    // ---------- DataStore (instancia unica) ----------
    val preferenciasRepository = PreferenciasRepository(appContext.dataStorePreferencias)

    // ---------- Retrofit + OkHttp ----------
    /** Solo existe cuando se usa el servicio simulado (USAR_SERVICIO_SIMULADO=true). */
    val controlServicioSimulado: ControlServicioSimulado? =
        if (BuildConfig.USAR_SERVICIO_SIMULADO) ControlServicioSimulado() else null

    private val clienteHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .apply {
            controlServicioSimulado?.let { control ->
                addInterceptor(
                    BackendSimuladoInterceptor(
                        hayConexion = monitorConectividad::hayConexion,
                        control = control,
                        archivo = File(appContext.filesDir, "servidor_simulado.json")
                    )
                )
            }
        }
        .build()

    private val api: RecordatorioApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL.let { if (it.endsWith("/")) it else "$it/" })
        .client(clienteHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RecordatorioApi::class.java)

    // ---------- Repositorio local-first ----------
    val repositorio: RecordatorioRepositoryImpl = RecordatorioRepositoryImpl(
        dao = baseDeDatos.recordatorioDao(),
        remoto = RetrofitRecordatorioRemoteDataSource(api),
        transacciones = object : EjecutorTransacciones {
            override suspend fun <T> ejecutar(bloque: suspend () -> T): T =
                baseDeDatos.withTransaction { bloque() }
        },
        alcance = alcanceApp
    )

    /** Sincroniza al abrir la app y cada vez que se recupera la conexion. */
    fun iniciarSincronizacion() {
        repositorio.iniciarSincronizacionAutomatica(monitorConectividad.conectado)
    }
}
