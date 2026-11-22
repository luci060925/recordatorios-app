package com.mansilla.recordatoriosapp.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Interfaz Retrofit del servicio. Cada recurso se identifica con el UUID que
 * genera el dispositivo, de modo que repetir un POST no crea un registro nuevo
 * (el servicio actualiza el que ya existe con ese id).
 */
interface RecordatorioApi {

    @GET("recordatorios")
    suspend fun obtenerTodos(): List<RecordatorioDto>

    @POST("recordatorios")
    suspend fun crear(@Body dto: RecordatorioDto): RecordatorioDto

    @PUT("recordatorios/{id}")
    suspend fun actualizar(@Path("id") id: String, @Body dto: RecordatorioDto): RecordatorioDto

    @DELETE("recordatorios/{id}")
    suspend fun eliminar(@Path("id") id: String): Response<Unit>
}
