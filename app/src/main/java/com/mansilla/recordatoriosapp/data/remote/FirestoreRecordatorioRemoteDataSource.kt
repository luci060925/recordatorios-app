package com.mansilla.recordatoriosapp.data.remote

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Fuente remota basada en Cloud Firestore: users/{uid}/reminders/{id}.
 *
 * - Usa el id del recordatorio (UUID del dispositivo) como id del documento, por lo que un
 *   reintento reemplaza el mismo documento en lugar de crear una copia.
 * - Room es la unica fuente local: la cache de Firestore se mantiene solo en memoria.
 * - Si no hay conexion falla de inmediato, en vez de dejar la escritura encolada en el SDK,
 *   para que el repositorio pueda marcar el cambio como pendiente.
 */
class FirestoreRecordatorioRemoteDataSource(
    private val firestore: FirebaseFirestore,
    private val hayConexion: () -> Boolean,
    private val tiempoMaximoMs: Long = 10_000L
) : RecordatorioRemoteDataSource {

    private fun recordatorios(uid: String): CollectionReference =
        firestore.collection("users").document(uid).collection("reminders")

    override suspend fun obtenerTodos(uid: String): ResultadoRed<List<RecordatorioDto>> =
        llamarFirestore {
            // Source.SERVER evita responder con datos de cache cuando no hay conexion.
            recordatorios(uid).get(Source.SERVER).await().documents.map { documento ->
                documentoADto(documento.id, documento.data)
            }
        }

    override suspend fun guardar(uid: String, dto: RecordatorioDto): ResultadoRed<Unit> =
        llamarFirestore {
            recordatorios(uid).document(requireNotNull(dto.id)).set(dto.aDocumentoFirestore()).await()
            Unit
        }

    override suspend fun eliminar(uid: String, id: String): ResultadoRed<Unit> =
        llamarFirestore {
            recordatorios(uid).document(id).delete().await()
            Unit
        }

    private suspend fun <T> llamarFirestore(bloque: suspend () -> T): ResultadoRed<T> {
        if (!hayConexion()) return ResultadoRed.SinConexion
        return try {
            ResultadoRed.Exito(withTimeout(tiempoMaximoMs) { bloque() })
        } catch (e: TimeoutCancellationException) {
            ResultadoRed.Timeout
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            traducir(e)
        } catch (e: Exception) {
            ResultadoRed.ErrorDesconocido(e.message ?: "Error desconocido")
        }
    }

    /** Traduce los codigos de Firestore a los tipos propios; la interfaz no los conoce. */
    private fun traducir(e: FirebaseFirestoreException): ResultadoRed<Nothing> = when (e.code) {
        Code.UNAVAILABLE -> ResultadoRed.SinConexion
        Code.DEADLINE_EXCEEDED -> ResultadoRed.Timeout
        Code.PERMISSION_DENIED -> ResultadoRed.ErrorRemoto(e.code.name, TipoErrorRemoto.SIN_PERMISO, "permiso denegado")
        Code.UNAUTHENTICATED -> ResultadoRed.ErrorRemoto(e.code.name, TipoErrorRemoto.NO_AUTENTICADO, "sesion no valida")
        Code.NOT_FOUND -> ResultadoRed.ErrorRemoto(e.code.name, TipoErrorRemoto.NO_ENCONTRADO, "no encontrado")
        Code.INVALID_ARGUMENT, Code.FAILED_PRECONDITION, Code.OUT_OF_RANGE, Code.ALREADY_EXISTS ->
            ResultadoRed.ErrorRemoto(e.code.name, TipoErrorRemoto.SOLICITUD_INVALIDA, "solicitud invalida")
        Code.INTERNAL, Code.UNKNOWN, Code.DATA_LOSS, Code.RESOURCE_EXHAUSTED, Code.ABORTED ->
            ResultadoRed.ErrorRemoto(e.code.name, TipoErrorRemoto.SERVIDOR, "error del servicio")
        else -> ResultadoRed.ErrorRemoto(e.code.name, TipoErrorRemoto.OTRO, "error del servicio")
    }
}
