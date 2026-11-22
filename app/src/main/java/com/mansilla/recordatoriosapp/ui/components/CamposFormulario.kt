package com.mansilla.recordatoriosapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

/**
 * Campo de texto controlado: el valor y el error vienen del estado y cada
 * cambio se informa con [onValorChange]. El error se comunica con isError,
 * un texto "Error: ..." y la propiedad semantica error().
 */
@Composable
fun CampoTexto(
    valor: String,
    onValorChange: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    mensajeError: String? = null,
    ayuda: String? = null,
    minLineas: Int = 1,
    tipoTeclado: KeyboardType = KeyboardType.Text,
    ocultarTexto: Boolean = false,
    habilitado: Boolean = true
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValorChange,
        label = { Text(etiqueta) },
        isError = mensajeError != null,
        minLines = minLineas,
        enabled = habilitado,
        singleLine = minLineas == 1,
        keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado),
        visualTransformation = if (ocultarTexto) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = {
            when {
                mensajeError != null -> Text("Error: $mensajeError")
                ayuda != null -> Text(ayuda)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (mensajeError != null) error(mensajeError) }
    )
}

/**
 * Grupo de opciones controlado (RadioButton). La opcion elegida viene del
 * estado y la seleccion se informa con [onSeleccion].
 */
@Composable
fun <T> SelectorOpciones(
    titulo: String,
    opciones: List<T>,
    seleccionada: T?,
    nombreOpcion: (T) -> String,
    onSeleccion: (T) -> Unit,
    modifier: Modifier = Modifier,
    mensajeError: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (mensajeError != null) error(mensajeError) },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = titulo, style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.selectableGroup()) {
            opciones.forEach { opcion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = opcion == seleccionada,
                            onClick = { onSeleccion(opcion) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(selected = opcion == seleccionada, onClick = null)
                    Text(
                        text = nombreOpcion(opcion),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
        if (mensajeError != null) {
            Text(
                text = "Error: $mensajeError",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
