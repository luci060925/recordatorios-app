package com.mansilla.recordatoriosapp.data

import com.mansilla.recordatoriosapp.Recordatorio
import com.mansilla.recordatoriosapp.recordatorios

/**
 * Implementacion en memoria del repositorio, con los datos simulados de la
 * Actividad 1 como contenido inicial.
 *
 * Los parametros de simulacion permiten comprobar todos los estados de la
 * interfaz (vacio, error de carga, error de guardado) sin base de datos ni
 * servicio remoto. Se configuran en RecordatoriosApplication.
 */
class RepositorioEnMemoria(
    private val simularListaVacia: Boolean = false,
    private val simularErrorCarga: Boolean = false,
    private val simularErrorGuardado: Boolean = false
) : RecordatorioRepository {

    private val lista: MutableList<Recordatorio> =
        if (simularListaVacia) mutableListOf() else recordatorios.toMutableList()

    override suspend fun obtenerRecordatorios(): List<Recordatorio> {
        if (simularErrorCarga) {
            throw IllegalStateException("No se pudieron cargar los recordatorios (error simulado).")
        }
        return lista.toList()
    }

    override suspend fun obtenerPorId(id: Int): Recordatorio? =
        lista.find { it.id == id }

    override suspend fun guardarRecordatorio(recordatorio: Recordatorio) {
        if (simularErrorGuardado) {
            throw IllegalStateException("No se pudo guardar el recordatorio (error simulado).")
        }
        lista.add(recordatorio)
    }
}
