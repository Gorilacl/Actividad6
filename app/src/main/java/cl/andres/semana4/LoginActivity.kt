package cl.andres.semana4

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Versión sin Firebase Auth / Google Sign-In para mantener minSdk 21 (Lollipop).
 * Valida formato básico y navega al menú si los datos son plausibles.
 */
class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LoginScreen(
                    onEmailLogin = { email, pass ->
                        val ok = email.contains("@") && pass.length >= 4
                        if (ok) {
                            startActivity(Intent(this@LoginActivity, MenuActivity::class.java))
                            finish()
                        }
                    },
                    onRegister = { _, _ ->
                        // Mock de registro: simplemente continúa al menú
                        startActivity(Intent(this@LoginActivity, MenuActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onEmailLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Inicia sesión") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = { Text("Correo") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it; error = null },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passVisible = !passVisible }) {
                        Icon(
                            imageVector = if (passVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            } else {
                Text("Mínimo 4 caracteres", style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    if (email.contains("@") && pass.length >= 4) {
                        onEmailLogin(email.trim(), pass)
                    } else {
                        error = "Correo o contraseña inválidos."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) { Text("Entrar") }

            OutlinedButton(
                onClick = {
                    if (email.contains("@") && pass.length >= 4) {
                        onRegister(email.trim(), pass)
                    } else {
                        error = "Datos inválidos para registro."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) { Text("Crear cuenta") }

            Divider(Modifier.padding(vertical = 4.dp))

            Text(
                "Inicio con Google deshabilitado en esta versión (compatibilidad API 21).",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}