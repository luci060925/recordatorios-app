package com.mansilla.recordatoriosapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mansilla.recordatoriosapp.EstadoSync
import com.mansilla.recordatoriosapp.Recordatorio
import com.mansilla.recordatoriosapp.nombreCategoria
import com.mansilla.recordatoriosapp.nombreEstado
import com.mansilla.recordatoriosapp.nombreEstadoSync
import com.mansilla.recordatoriosapp.nombrePrioridad

/** Representa un recordatorio en el listado; informa la selección con [onClick]. */
@Composable
fun TarjetaRecordatorio(
    recordatorio: Recordatorio,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = recordatorio.titulo,
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = "Categoría: ${nombreCategoria(recordatorio.categoria)}")
            Text(text = "Prioridad: ${nombrePrioridad(recordatorio.prioridad)}")
            Text(text = "Estado: ${nombreEstado(recordatorio.estado)}")
            // El estado de sincronización se indica con texto, no solo con color.
            Text(
                text = "Sincronización: ${nombreEstadoSync(recordatorio.estadoSync)}",
                style = MaterialTheme.typography.labelLarge,
                color = if (recordatorio.estadoSync == EstadoSync.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
