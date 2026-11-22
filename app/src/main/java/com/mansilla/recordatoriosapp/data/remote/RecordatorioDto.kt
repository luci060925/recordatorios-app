package com.mansilla.recordatoriosapp.data.remote

/**
 * Contrato del servicio remoto (lo que viaja por HTTP). No llega a la interfaz:
 * antes de usarse se transforma con las funciones de mapeo.
 *
 * Los campos son anulables a proposito: el servicio es una fuente externa y un
 * dato faltante no debe provocar un fallo; el mapeo descarta lo invalido.
 */
data class RecordatorioDto(
    val id: String?,
    val titulo: String?,
    val descripcion: String?,
    val categoria: String?,
    val prioridad: String?,
    val estado: String?,
    val fecha: Long?,
    val actualizadoEn: Long?
)
