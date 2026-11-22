package com.mansilla.recordatoriosapp.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Informa si el dispositivo tiene una red con acceso a internet. */
class MonitorConectividad(context: Context) {

    private val administrador =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun hayConexion(): Boolean {
        val red = administrador.activeNetwork ?: return false
        val capacidades = administrador.getNetworkCapabilities(red) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Emite true/false cada vez que cambia la disponibilidad de la red. */
    val conectado: Flow<Boolean> = callbackFlow {
        trySend(hayConexion())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        administrador.registerDefaultNetworkCallback(callback)
        awaitClose { administrador.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
