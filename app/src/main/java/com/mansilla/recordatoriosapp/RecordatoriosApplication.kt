package com.mansilla.recordatoriosapp

import android.app.Application

class RecordatoriosApplication : Application() {

    lateinit var contenedor: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = AppContainer(this)
        contenedor.iniciarSincronizacion()
    }
}
