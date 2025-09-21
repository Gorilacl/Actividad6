package cl.andres.semana4

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : ComponentActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }

    // Launcher para el intent de Google
    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.result
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential).addOnCompleteListener { t ->
                if (t.isSuccessful) {
                    Toast.makeText(this, "Login con Google OK", Toast.LENGTH_SHORT).show()
                    goToMenu()
                } else {
                    Toast.makeText(
                        this,
                        t.exception?.localizedMessage ?: "Error Google",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("Auth", "Google sign-in error", t.exception)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cancelado o error: ${e.localizedMessage}", Toast.LENGTH_LONG)
                .show()
            Log.e("Auth", "Google intent error", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LoginScreen(
            onEmailLogin = { email, pass -> signInEmail(email, pass) },
            onRegister = { email, pass -> registerEmail(email, pass) },
            onGoogle = {
                Toast.makeText(this, "Abriendo Google…", Toast.LENGTH_SHORT).show()
                startGoogleSignIn()
            }
        ) }
    }

    private fun canUsePlayServices(): Boolean {
        val api = GoogleApiAvailability.getInstance()
        val code = api.isGooglePlayServicesAvailable(this)
        if (code != ConnectionResult.SUCCESS) {
            if (api.isUserResolvableError(code)) {
                api.getErrorDialog(this, code, /*requestCode*/9000)?.show()
            } else {
                Toast.makeText(this, "Google Play Services no disponible", Toast.LENGTH_LONG).show()
            }
            return false
        }
        return true
    }

    private fun startGoogleSignIn() {
        if (!canUsePlayServices()) return
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // generado por google-services
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        googleLauncher.launch(client.signInIntent)
    }

    private fun goToMenu() {
        startActivity(Intent(this, MenuActivity::class.java))
        finish()
    }

    // ---------- Email/Password ----------
    private fun signInEmail(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            Toast.makeText(this, "Completa correo y contraseña", Toast.LENGTH_SHORT).show()
            return
        }
        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { t ->
            if (t.isSuccessful) goToMenu()
            else Toast.makeText(
                this, t.exception?.localizedMessage ?: "Error al iniciar",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun registerEmail(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            Toast.makeText(this, "Completa correo y contraseña", Toast.LENGTH_SHORT).show()
            return
        }
        if (pass.length < 6) {
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return
        }
        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { t ->
            if (t.isSuccessful) {
                Toast.makeText(this, "Cuenta creada, iniciando sesión…", Toast.LENGTH_SHORT).show()
                goToMenu()
            } else {
                Toast.makeText(
                    this, t.exception?.localizedMessage ?: "No se pudo crear la cuenta",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onEmailLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onGoogle: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Inicia sesión") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
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
            Text("Mínimo 6 caracteres", style = MaterialTheme.typography.bodySmall)

            Button(
                onClick = { onEmailLogin(email.trim(), pass) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) { Text("Entrar") }

            OutlinedButton(
                onClick = { onRegister(email.trim(), pass) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) { Text("Crear cuenta") }

            Divider(Modifier.padding(vertical = 4.dp))

            Button(
                onClick = onGoogle,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) { Text("Continuar con Google") }
        }
    }
}