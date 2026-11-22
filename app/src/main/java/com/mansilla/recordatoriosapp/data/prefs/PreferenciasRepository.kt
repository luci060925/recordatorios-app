package com.mansilla.recordatoriosapp.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Unica instancia de DataStore de la aplicacion. El delegado a nivel de archivo
 * garantiza que no se creen dos instancias para el mismo archivo.
 */
val Context.dataStorePreferencias: DataStore<Preferences> by preferencesDataStore(name = "preferencias_usuario")

/** Claves tipadas de DataStore. Solo guardan configuraciones simples. */
internal object ClavesPreferencias {
    val TEMA = stringPreferencesKey("tema")
    val MOSTRAR_COMPLETADOS = booleanPreferencesKey("mostrar_completados")
    val ORDEN = stringPreferencesKey("orden_listado")
    val FILTRO_PRIORIDAD = stringPreferencesKey("filtro_prioridad")
}

class PreferenciasRepository(private val dataStore: DataStore<Preferences>) {

    /**
     * Flujo de preferencias ya transformadas al modelo propio. Ante un error de
     * lectura recuperable (IOException) emite los valores por defecto en vez de fallar.
     */
    val preferencias: Flow<PreferenciasUsuario> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.aPreferenciasUsuario() }
        .distinctUntilChanged()

    // Escrituras: funciones suspendidas, se llaman desde viewModelScope (nunca bloquean el hilo principal).

    suspend fun actualizarTema(tema: TemaApp) {
        dataStore.edit { it[ClavesPreferencias.TEMA] = tema.name }
    }

    suspend fun actualizarMostrarCompletados(mostrar: Boolean) {
        dataStore.edit { it[ClavesPreferencias.MOSTRAR_COMPLETADOS] = mostrar }
    }

    suspend fun actualizarOrden(orden: OrdenListado) {
        dataStore.edit { it[ClavesPreferencias.ORDEN] = orden.name }
    }

    suspend fun actualizarFiltroPrioridad(filtro: FiltroPrioridad) {
        dataStore.edit { it[ClavesPreferencias.FILTRO_PRIORIDAD] = filtro.name }
    }
}

/** Convierte las claves guardadas al modelo; un valor ausente o desconocido usa el predeterminado. */
internal fun Preferences.aPreferenciasUsuario(): PreferenciasUsuario {
    val predeterminadas = PreferenciasUsuario()
    return PreferenciasUsuario(
        tema = this[ClavesPreferencias.TEMA].aEnumOPredeterminado(predeterminadas.tema),
        mostrarCompletados = this[ClavesPreferencias.MOSTRAR_COMPLETADOS] ?: predeterminadas.mostrarCompletados,
        orden = this[ClavesPreferencias.ORDEN].aEnumOPredeterminado(predeterminadas.orden),
        filtroPrioridad = this[ClavesPreferencias.FILTRO_PRIORIDAD].aEnumOPredeterminado(predeterminadas.filtroPrioridad)
    )
}

private inline fun <reified E : Enum<E>> String?.aEnumOPredeterminado(predeterminado: E): E =
    enumValues<E>().firstOrNull { it.name == this } ?: predeterminado
