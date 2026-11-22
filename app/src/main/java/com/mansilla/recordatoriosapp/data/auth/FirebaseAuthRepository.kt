package com.mansilla.recordatoriosapp.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

/**
 * Autenticacion con Firebase Authentication (correo y contrasena).
 *
 * - No guarda contrasenas ni tokens: la sesion la persiste y renueva el SDK de Firebase,
 *   en el almacenamiento protegido propio de la app. La app solo conserva el uid y el correo
 *   en memoria, para asociar los datos con la cuenta.
 * - Traduce las excepciones del SDK a [TipoErrorAuth]; la interfaz no conoce Firebase.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    alcance: CoroutineScope
) : AuthRepository {

    /** Se marca cuando la sesion se pierde por invalidez, para informarlo en la pantalla de acceso. */
    private val sesionExpirada = kotlinx.coroutines.flow.MutableStateFlow(false)

    override val estado: StateFlow<EstadoAuth> =
        kotlinx.coroutines.flow.combine(usuarioActual(), sesionExpirada) { usuario, expirada ->
            if (usuario != null) {
                EstadoAuth.ConSesion(UsuarioSesion(usuario.uid, usuario.email))
            } else {
                EstadoAuth.SinSesion(expirada = expirada)
            }
        }.stateIn(alcance, SharingStarted.Eagerly, EstadoAuth.Verificando)

    private fun usuarioActual(): Flow<FirebaseUser?> = callbackFlow {
        // El listener emite de inmediato el usuario guardado (reapertura con sesion existente).
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun registrar(email: String, contrasena: String): ResultadoAuth =
        ejecutar { auth.createUserWithEmailAndPassword(email.trim(), contrasena).await() }

    override suspend fun iniciarSesion(email: String, contrasena: String): ResultadoAuth =
        ejecutar { auth.signInWithEmailAndPassword(email.trim(), contrasena).await() }

    private suspend fun ejecutar(operacion: suspend () -> Unit): ResultadoAuth =
        try {
            operacion()
            sesionExpirada.update { false }
            ResultadoAuth.Exito
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoAuth.Error(traducir(e))
        }

    override suspend fun cerrarSesion() {
        sesionExpirada.update { false }
        auth.signOut()
    }

    override suspend fun verificarSesion(): ResultadoVerificacion {
        val usuario = auth.currentUser ?: return ResultadoVerificacion.VALIDA
        return try {
            // Fuerza la renovacion del token: falla si la cuenta ya no es valida en el servicio.
            usuario.getIdToken(true).await()
            ResultadoVerificacion.VALIDA
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseNetworkException) {
            ResultadoVerificacion.NO_VERIFICABLE
        } catch (e: FirebaseAuthInvalidUserException) {
            invalidarSesion()
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            invalidarSesion()
        } catch (e: Exception) {
            ResultadoVerificacion.NO_VERIFICABLE
        }
    }

    private fun invalidarSesion(): ResultadoVerificacion {
        sesionExpirada.update { true }
        auth.signOut()
        return ResultadoVerificacion.INVALIDA
    }

    private fun traducir(e: Exception): TipoErrorAuth = when (e) {
        is FirebaseAuthWeakPasswordException -> TipoErrorAuth.CONTRASENA_DEBIL
        is FirebaseAuthUserCollisionException -> TipoErrorAuth.EMAIL_EN_USO
        is FirebaseAuthInvalidUserException -> TipoErrorAuth.CREDENCIALES_INVALIDAS
        is FirebaseAuthInvalidCredentialsException ->
            if (e.errorCode == "ERROR_INVALID_EMAIL") TipoErrorAuth.EMAIL_INVALIDO
            else TipoErrorAuth.CREDENCIALES_INVALIDAS
        is FirebaseNetworkException -> TipoErrorAuth.SIN_CONEXION
        is FirebaseTooManyRequestsException -> TipoErrorAuth.DEMASIADOS_INTENTOS
        else -> TipoErrorAuth.DESCONOCIDO
    }
}
