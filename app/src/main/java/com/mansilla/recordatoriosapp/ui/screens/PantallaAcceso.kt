package com.mansilla.recordatoriosapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mansilla.recordatoriosapp.ui.components.CampoTexto
import com.mansilla.recordatoriosapp.ui.state.AccesoUiState
import com.mansilla.recordatoriosapp.ui.state.LONGITUD_MINIMA_CONTRASENA
import com.mansilla.recordatoriosapp.ui.state.ModoAcceso
import com.mansilla.recordatoriosapp.ui.state.mensaje
import com.mansilla.recordatoriosapp.ui.viewmodel.AccesoViewModel

/** Punto de entrada con estado de la pantalla de acceso. */
@Composable
fun PantallaAcceso(viewModelFactory: ViewModelProvider.Factory) {
    val viewModel: AccesoViewModel = viewModel(factory = viewModelFactory)
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    ContenidoAcceso(
        estado = estado,
        onEmailChange = viewModel::cambiarEmail,
        onContrasenaChange = viewModel::cambiarContrasena,
        onCambiarModo = viewModel::cambiarModo,
        onEnviar = viewModel::enviar
    )
}

/** Contenido sin estado propio: recibe el UiState y comunica los eventos con lambdas. */
@Composable
fun ContenidoAcceso(
    estado: AccesoUiState,
    onEmailChange: (String) -> Unit,
    onContrasenaChange: (String) -> Unit,
    onCambiarModo: (ModoAcceso) -> Unit,
    onEnviar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val registrando = estado.modo == ModoAcceso.REGISTRARSE

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (registrando) "Crear cuenta" else "Iniciar sesión",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Tus recordatorios se guardan en tu cuenta y se sincronizan en todos tus dispositivos.",
                style = MaterialTheme.typography.bodyMedium
            )

            // Aviso cuando la sesion anterior se perdio o dejo de ser valida.
            if (estado.sesionExpirada) {
                Text(
                    text = "Tu sesión expiró o dejó de ser válida. Iniciá sesión nuevamente.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                )
            }

            CampoTexto(
                valor = estado.email,
                onValorChange = onEmailChange,
                etiqueta = "Correo electrónico",
                mensajeError = estado.errorEmail,
                tipoTeclado = KeyboardType.Email,
                habilitado = !estado.enCurso
            )
            CampoTexto(
                valor = estado.contrasena,
                onValorChange = onContrasenaChange,
                etiqueta = "Contraseña",
                mensajeError = estado.errorContrasena,
                ayuda = if (registrando) "Mínimo $LONGITUD_MINIMA_CONTRASENA caracteres" else null,
                tipoTeclado = KeyboardType.Password,
                ocultarTexto = true,
                habilitado = !estado.enCurso
            )

            // Error de autenticacion: texto + region activa, no solo color.
            if (estado.error != null) {
                Text(
                    text = "Error: ${estado.error.mensaje()}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                )
            }

            Button(
                onClick = onEnviar,
                enabled = estado.puedeEnviar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (estado.enCurso) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        when {
                            estado.enCurso && registrando -> "Creando cuenta..."
                            estado.enCurso -> "Iniciando sesión..."
                            registrando -> "Crear cuenta"
                            else -> "Iniciar sesión"
                        }
                    )
                }
            }

            TextButton(
                onClick = {
                    onCambiarModo(if (registrando) ModoAcceso.INICIAR_SESION else ModoAcceso.REGISTRARSE)
                },
                enabled = !estado.enCurso,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (registrando) "¿Ya tenés cuenta? Iniciá sesión" else "¿No tenés cuenta? Registrate"
                )
            }
        }
    }
}
