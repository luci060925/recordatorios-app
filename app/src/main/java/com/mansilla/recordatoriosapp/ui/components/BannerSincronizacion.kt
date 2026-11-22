package com.mansilla.recordatoriosapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mansilla.recordatoriosapp.data.repository.EstadoSincronizacion
import com.mansilla.recordatoriosapp.formatearFecha
import com.mansilla.recordatoriosapp.ui.state.SincronizacionUiState

/**
 * Franja que informa si los datos locales ya coinciden con el servicio, si hay
 * cambios pendientes o si la sincronización falló. Siempre incluye un texto
 * descriptivo y, ante un problema, una acción de reintento.
 */
@Composable
fun BannerSincronizacion(
    estado: SincronizacionUiState,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val error = estado.estado as? EstadoSincronizacion.Error
    val hayProblema = error != null || estado.conError > 0
    val sincronizando = estado.estado == EstadoSincronizacion.Sincronizando

    val texto = when {
        sincronizando -> "Sincronizando con el servicio..."
        error != null -> "Error de sincronización: ${error.mensaje}"
        estado.conError > 0 ->
            "Error de sincronización: ${estado.conError} cambio(s) rechazado(s) por el servicio."
        estado.pendientes > 0 ->
            "Pendiente de sincronizar: ${estado.pendientes} cambio(s) guardado(s) en el dispositivo."
        estado.estado is EstadoSincronizacion.Exito ->
            "Sincronizado a las ${formatearFecha(estado.estado.instante).takeLast(5)}."
        else -> "Datos guardados en el dispositivo."
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (hayProblema) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (hayProblema) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (sincronizando) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Text(text = texto, style = MaterialTheme.typography.bodyMedium)
            }
            if (hayProblema || estado.pendientes > 0) {
                TextButton(onClick = onReintentar, enabled = !sincronizando) {
                    Text("Reintentar sincronización")
                }
            }
        }
    }
}
