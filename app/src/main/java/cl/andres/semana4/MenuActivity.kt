package cl.andres.semana4

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class MenuActivity : ComponentActivity() {

    private lateinit var fused: FusedLocationProviderClient

    // pido permisos de ubicación cuando entro al menú
    private val permisosLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        val fine = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = resultados[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            capturarYGuardarUbicacion()
        } else {
            Toast.makeText(
                this,
                "Permiso de ubicación denegado: no se guardó la posición.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fused = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            MaterialTheme {
                MenuScreen(
                    onOpenApp = { startActivity(Intent(this, MainActivity::class.java)) },
                    // abro la pantalla exclusiva del mapa
                    onOpenMap = { startActivity(Intent(this, MapActivity::class.java)) },
                    onLogout = {
                        // sin Auth: cierro sesión volviendo al login
                        startActivity(
                            Intent(this, LoginActivity::class.java).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                                )
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // guardo la ubicación apenas abro el menú
        solicitarPermisoYGuardar()
    }

    private fun solicitarPermisoYGuardar() {
        val fineOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineOk || coarseOk) {
            capturarYGuardarUbicacion()
        } else {
            permisosLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // capturo la ubicación y la dejo en RTDB bajo /devices/<ANDROID_ID>/lastLocation
    @SuppressLint("MissingPermission")
    private fun capturarYGuardarUbicacion() {
        val fineOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineOk && !coarseOk) {
            permisosLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        // como no uso FirebaseAuth, identifico el dispositivo con el ANDROID_ID
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"

        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        val ref = FirebaseDatabase.getInstance()
                            .getReference("devices")
                            .child(deviceId)
                            .child("lastLocation")

                        val data = mapOf(
                            "lat" to loc.latitude,
                            "lon" to loc.longitude,
                            "ts"  to ServerValue.TIMESTAMP
                        )

                        ref.setValue(data)
                            // .addOnSuccessListener { Toast.makeText(this, "Ubicación guardada", Toast.LENGTH_SHORT).show() }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    this,
                                    "Error al guardar ubicación: ${e.localizedMessage}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    } else {
                        Toast.makeText(
                            this,
                            "No fue posible obtener la ubicación.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "Error al obtener ubicación: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } catch (se: SecurityException) {
            Toast.makeText(this, "Permiso de ubicación requerido.", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuScreen(
    onOpenApp: () -> Unit,
    onOpenMap: () -> Unit,   // nuevo: abre la pantalla de mapa
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Menú") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onOpenApp,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) { Text("Abrir aplicación") }

            // botón para ir al mapa (Semana 9)
            Button(
                onClick = onOpenMap,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) { Text("Abrir mapa") }

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) { Text("Cerrar sesión") }
        }
    }
}