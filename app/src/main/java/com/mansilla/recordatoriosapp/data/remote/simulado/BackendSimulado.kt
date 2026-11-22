package com.mansilla.recordatoriosapp.data.remote.simulado

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mansilla.recordatoriosapp.data.remote.RecordatorioDto
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.File
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Comportamientos del servicio simulado, para comprobar cada tipo de error. */
enum class ModoServicio(val etiqueta: String) {
    NORMAL("Normal"),
    ERROR_SERVIDOR("Error 500 del servidor"),
    SOLICITUD_INVALIDA("Error 400 (solicitud inválida)"),
    TIMEOUT("Timeout (sin respuesta)"),
    TIMEOUT_TRAS_PROCESAR("Timeout tras procesar (respuesta perdida)")
}

/** Permite cambiar el modo del servicio simulado mientras la app esta en uso. */
class ControlServicioSimulado {
    val modo = MutableStateFlow(ModoServicio.NORMAL)
}

/**
 * Servicio remoto simulado, implementado como interceptor de OkHttp: la app usa
 * Retrofit y OkHttp igual que con un servicio real, pero las respuestas las
 * produce este objeto. Cumple el contrato de [com.mansilla.recordatoriosapp.data.remote.RecordatorioApi]:
 *
 * - GET    /recordatorios        -> 200 con la lista
 * - POST   /recordatorios        -> 201 si es nuevo; 200 si el id ya existia (se actualiza, no se duplica)
 * - PUT    /recordatorios/{id}   -> 200, o 404 si no existe
 * - DELETE /recordatorios/{id}   -> 204, o 404 si no existe
 *
 * Sus datos se guardan en [archivo] para sobrevivir al cierre de la app, como lo haria un servidor.
 * Sin conexion del dispositivo, lanza UnknownHostException, igual que una llamada real.
 */
class BackendSimuladoInterceptor(
    private val hayConexion: () -> Boolean,
    private val control: ControlServicioSimulado,
    private val archivo: File? = null,
    private val latenciaMs: Long = 700L,
    private val gson: Gson = Gson()
) : Interceptor {

    private val almacen = LinkedHashMap<String, RecordatorioDto>()
    private val lock = Any()

    init {
        cargarDesdeArchivo()
        if (almacen.isEmpty()) sembrarDatosIniciales()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!hayConexion()) throw UnknownHostException("Sin conexion (simulado)")
        dormir()

        val modo = control.modo.value
        if (modo == ModoServicio.TIMEOUT) throw SocketTimeoutException("Timeout (simulado)")
        if (modo == ModoServicio.ERROR_SERVIDOR) {
            return respuesta(request, 500, """{"error":"Error interno del servidor (simulado)"}""")
        }
        val escribe = request.method == "POST" || request.method == "PUT"
        if (modo == ModoServicio.SOLICITUD_INVALIDA && escribe) {
            return respuesta(request, 400, """{"error":"Solicitud invalida (simulado)"}""")
        }

        val resultado = procesar(request)
        if (modo == ModoServicio.TIMEOUT_TRAS_PROCESAR && request.method != "GET") {
            // El servicio ya aplico el cambio, pero la respuesta no llega a la app.
            throw SocketTimeoutException("Respuesta perdida (simulado)")
        }
        return resultado
    }

    private fun procesar(request: Request): Response {
        val partes = request.url.pathSegments.filter { it.isNotEmpty() }
        val indice = partes.indexOf("recordatorios")
        if (indice < 0) return respuesta(request, 404, """{"error":"Ruta inexistente"}""")
        val idRecurso = partes.getOrNull(indice + 1)

        return synchronized(lock) {
            when {
                request.method == "GET" && idRecurso == null ->
                    respuesta(request, 200, gson.toJson(almacen.values.toList()))

                request.method == "POST" && idRecurso == null -> {
                    val dto = leerCuerpo(request)
                    when {
                        dto == null || !esValido(dto) -> respuesta(request, 400, """{"error":"Datos invalidos"}""")
                        else -> {
                            // Idempotente: si el id ya existe se actualiza en lugar de duplicar.
                            val existia = almacen.containsKey(dto.id)
                            almacen[dto.id!!] = dto
                            guardarEnArchivo()
                            respuesta(request, if (existia) 200 else 201, gson.toJson(dto))
                        }
                    }
                }

                request.method == "PUT" && idRecurso != null -> {
                    val dto = leerCuerpo(request)
                    when {
                        dto == null || !esValido(dto) -> respuesta(request, 400, """{"error":"Datos invalidos"}""")
                        !almacen.containsKey(idRecurso) -> respuesta(request, 404, """{"error":"No encontrado"}""")
                        else -> {
                            val actualizado = dto.copy(id = idRecurso)
                            almacen[idRecurso] = actualizado
                            guardarEnArchivo()
                            respuesta(request, 200, gson.toJson(actualizado))
                        }
                    }
                }

                request.method == "DELETE" && idRecurso != null ->
                    if (almacen.remove(idRecurso) != null) {
                        guardarEnArchivo()
                        respuesta(request, 204, "")
                    } else {
                        respuesta(request, 404, """{"error":"No encontrado"}""")
                    }

                else -> respuesta(request, 405, """{"error":"Metodo no permitido"}""")
            }
        }
    }

    private fun esValido(dto: RecordatorioDto) =
        !dto.id.isNullOrBlank() && (dto.titulo?.trim()?.length ?: 0) >= 4

    private fun leerCuerpo(request: Request): RecordatorioDto? = try {
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        gson.fromJson(buffer.readUtf8(), RecordatorioDto::class.java)
    } catch (e: Exception) {
        null
    }

    private fun respuesta(request: Request, codigo: Int, json: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(codigo)
            .message(if (codigo in 200..299) "OK" else "Error")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()

    private fun dormir() {
        try {
            Thread.sleep(latenciaMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Llamada cancelada")
        }
    }

    // ---------- Persistencia del "servidor" simulado ----------

    private fun cargarDesdeArchivo() {
        val f = archivo ?: return
        if (!f.exists()) return
        try {
            val tipo = object : TypeToken<List<RecordatorioDto>>() {}.type
            val lista: List<RecordatorioDto> = gson.fromJson(f.readText(), tipo) ?: emptyList()
            lista.forEach { dto -> dto.id?.let { almacen[it] = dto } }
        } catch (e: Exception) {
            almacen.clear()
        }
    }

    private fun guardarEnArchivo() {
        val f = archivo ?: return
        try {
            f.writeText(gson.toJson(almacen.values.toList()))
        } catch (_: Exception) {
            // El archivo es solo un respaldo del servidor simulado.
        }
    }

    private fun sembrarDatosIniciales() {
        val ahora = System.currentTimeMillis()
        listOf(
            RecordatorioDto(
                id = "srv-0001", titulo = "Reunión con el equipo", descripcion = "Preparar el avance semanal",
                categoria = "TRABAJO", prioridad = "ALTA", estado = "PENDIENTE",
                fecha = ahora - 2 * 86_400_000L, actualizadoEn = ahora - 2 * 86_400_000L
            ),
            RecordatorioDto(
                id = "srv-0002", titulo = "Renovar documentación", descripcion = null,
                categoria = "PERSONAL", prioridad = "MEDIA", estado = "PENDIENTE",
                fecha = ahora - 86_400_000L, actualizadoEn = ahora - 86_400_000L
            )
        ).forEach { almacen[it.id!!] = it }
        guardarEnArchivo()
    }
}
