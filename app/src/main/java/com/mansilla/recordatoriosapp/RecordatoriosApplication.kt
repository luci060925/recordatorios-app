package com.mansilla.recordatoriosapp

import android.app.Application
import com.mansilla.recordatoriosapp.data.RecordatorioRepository
import com.mansilla.recordatoriosapp.data.RepositorioEnMemoria

/**
 * Mantiene una unica instancia del repositorio mientras viva el proceso,
 * de modo que los recordatorios creados sobrevivan a la navegacion.
 */
class RecordatoriosApplication : Application() {

    // Para comprobar los estados vacio y de error, cambiar a true la bandera
    // correspondiente y volver a ejecutar la aplicacion.
    val repositorio: RecordatorioRepository by lazy {
        RepositorioEnMemoria(
            simularListaVacia = false,
            simularErrorCarga = false,
            simularErrorGuardado = false
        )
    }
}
