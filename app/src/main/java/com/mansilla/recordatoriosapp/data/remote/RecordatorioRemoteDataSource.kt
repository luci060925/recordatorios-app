package com.mansilla.recordatoriosapp.data.remote

import retrofit2.HttpException

/** Fuente remota: devuelve siempre un [ResultadoRed], nunca lanza excepciones de red. */
interface RecordatorioRemoteDataSource {
    suspend fun obtenerTodos(): ResultadoRed<List<RecordatorioDto>>
    suspend fun crear(dto: RecordatorioDto): ResultadoRed<RecordatorioDto>
    suspend fun actualizar(dto: RecordatorioDto): ResultadoRed<RecordatorioDto>
    suspend fun eliminar(id: String): ResultadoRed<Unit>
}

class RetrofitRecordatorioRemoteDataSource(
    private val api: RecordatorioApi
) : RecordatorioRemoteDataSource {

    override suspend fun obtenerTodos() = llamadaSegura { api.obtenerTodos() }

    override suspend fun crear(dto: RecordatorioDto) = llamadaSegura { api.crear(dto) }

    override suspend fun actualizar(dto: RecordatorioDto) = llamadaSegura {
        api.actualizar(requireNotNull(dto.id), dto)
    }

    override suspend fun eliminar(id: String) = llamadaSegura {
        val respuesta = api.eliminar(id)
        if (!respuesta.isSuccessful) throw HttpException(respuesta)
    }
}
