package cl.andres.semana4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions

class SettingsActivity : ComponentActivity() {
    private val vm: RangesViewModel by viewModels { RangesViewModel.factory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SettingsScreen(
                    load = { cb -> vm.getRanges(cb) },
                    save = { minC, maxC -> vm.setRanges(minC, maxC); finish() }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    load: ((Double, Double) -> Unit) -> Unit,
    save: (Double, Double) -> Unit
) {
    var min by remember { mutableStateOf("-5.0") }
    var max by remember { mutableStateOf("4.0") }

    // Carga inicial desde Room
    LaunchedEffect(Unit) {
        load { minC, maxC ->
            min = minC.toString()
            max = maxC.toString()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configurar rangos (°C)", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = min,
            onValueChange = { min = it },
            label = { Text("Mínimo °C") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        OutlinedTextField(
            value = max,
            onValueChange = { max = it },
            label = { Text("Máximo °C") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        Button(
            onClick = {
                val minC = min.toDoubleOrNull() ?: -5.0
                val maxC = max.toDoubleOrNull() ?: 4.0
                save(minC, maxC)
            },
            modifier = Modifier.align(Alignment.End)
        ) { Text("Guardar") }
    }
}