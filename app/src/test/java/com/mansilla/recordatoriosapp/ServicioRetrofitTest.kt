package com.mansilla.recordatoriosapp

import com.mansilla.recordatoriosapp.data.remote.RecordatorioApi
import com.mansilla.recordatoriosapp.data.remote.RecordatorioDto
import com.mansilla.recordatoriosapp.data.remote.ResultadoRed
import com.mansilla.recordatoriosapp.data.remote.RetrofitRecordatorioRemoteDataSource
import com.mansilla.recordatoriosapp.data.remote.TipoErrorHttp
import com.mansilla.recordatoriosapp.data.remote.simulado.BackendSimuladoInterceptor
import com.mansilla.recordatoriosapp.data.remote.simulado.ControlServicioSimulado
import com.mansilla.recordatoriosapp.data.remote.simulado.ModoServicio
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Ejercita la pila real (Retrofit + OkHttp + Gson) contra el servicio simulado y comprueba
 * que la capa de datos distingue exito, 4xx, 5xx, timeout y falta de conexion.
 */
class ServicioRetrofitTest {

    private val control = ControlServicioSimulado()
    private var hayConexion = true

    private val remoto = RetrofitRecordatorioRemoteDataSource(
        Retrofit.Builder()
            .baseUrl("http://servicio.test/")
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(
                        BackendSimuladoInterceptor(
                            hayConexion = { hayConexion },
                            control = control,
                            archivo = null,
                            latenciaMs = 0
                        )
                    )
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RecordatorioApi::class.java)
    )

    private fun dto(id: String = "uuid-1", titulo: String? = "Titulo valido") = RecordatorioDto(
        id, titulo, null, "ESTUDIO", "ALTA", "PENDIENTE", 1L, 1L
    )

    private suspend fun cantidadEnServidor() =
        (remoto.obtenerTodos() as ResultadoRed.Exito).valor.size

    @Test
    fun respuestaExitosa_seTransformaEnExito() = runBlocking {
        val creado = remoto.crear(dto())
        assertTrue(creado is ResultadoRed.Exito)
        assertEquals("uuid-1", (creado as ResultadoRed.Exito).valor.id)
    }

    @Test
    fun repetirUnPost_noCreaUnRegistroNuevo() = runBlocking {
        val inicial = cantidadEnServidor()
        remoto.crear(dto())
        remoto.crear(dto(titulo = "Titulo editado"))
        assertEquals(inicial + 1, cantidadEnServidor())
        val guardado = (remoto.obtenerTodos() as ResultadoRed.Exito).valor.first { it.id == "uuid-1" }
        assertEquals("Titulo editado", guardado.titulo)
    }

    @Test
    fun error500_seClasificaComoErrorDelServidor() = runBlocking {
        control.modo.value = ModoServicio.ERROR_SERVIDOR
        val r = remoto.obtenerTodos() as ResultadoRed.ErrorHttp
        assertEquals(500, r.codigo)
        assertEquals(TipoErrorHttp.SERVIDOR, r.tipo)
    }

    @Test
    fun error400_seClasificaComoSolicitudInvalida() = runBlocking {
        control.modo.value = ModoServicio.SOLICITUD_INVALIDA
        val r = remoto.crear(dto()) as ResultadoRed.ErrorHttp
        assertEquals(400, r.codigo)
        assertEquals(TipoErrorHttp.SOLICITUD_INVALIDA, r.tipo)
    }

    @Test
    fun tituloInvalido_elServicioRechazaConError400() = runBlocking {
        val r = remoto.crear(dto(titulo = "ab")) as ResultadoRed.ErrorHttp
        assertEquals(TipoErrorHttp.SOLICITUD_INVALIDA, r.tipo)
    }

    @Test
    fun timeout_seClasificaComoTimeout() = runBlocking {
        control.modo.value = ModoServicio.TIMEOUT
        assertEquals(ResultadoRed.Timeout, remoto.obtenerTodos())
    }

    @Test
    fun timeoutTrasProcesar_elServicioAplicoElCambioPeroLaAppVeTimeout() = runBlocking {
        val inicial = cantidadEnServidor()
        control.modo.value = ModoServicio.TIMEOUT_TRAS_PROCESAR
        assertEquals(ResultadoRed.Timeout, remoto.crear(dto()))

        control.modo.value = ModoServicio.NORMAL
        assertEquals(inicial + 1, cantidadEnServidor())
    }

    @Test
    fun sinConexion_seClasificaComoSinConexion() = runBlocking {
        hayConexion = false
        assertEquals(ResultadoRed.SinConexion, remoto.obtenerTodos())
    }

    @Test
    fun eliminarDosVeces_laSegundaDevuelve404() = runBlocking {
        remoto.crear(dto())
        assertTrue(remoto.eliminar("uuid-1") is ResultadoRed.Exito)
        val segunda = remoto.eliminar("uuid-1") as ResultadoRed.ErrorHttp
        assertEquals(TipoErrorHttp.NO_ENCONTRADO, segunda.tipo)
    }
}
