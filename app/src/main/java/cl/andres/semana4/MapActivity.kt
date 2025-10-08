package cl.andres.semana4

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.android.gms.maps.CameraUpdateFactory



private const val BODEGA_LAT = -35.016
private const val BODEGA_LON = -71.333
private const val RADIO_GRATIS_KM = 20.0

class MapActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Mapa y cálculo de despacho") }) }
                ) { padding ->
                    MapScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun MapScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val fused = remember { LocationServices.getFusedLocationProviderClient(ctx) }

    val bodega = LatLng(BODEGA_LAT, BODEGA_LON)
    var myLatLng by remember { mutableStateOf<LatLng?>(null) }
    var distanciaKm by remember { mutableStateOf<Double?>(null) }
    var totalCompra by remember { mutableStateOf("") }
    var resultado by remember { mutableStateOf<String?>(null) }

    // ¿Hay permisos actualmente?
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(bodega, 12f)
    }

    // Pido permisos cuando hace falta
    val pedirPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    loc?.let {
                        val latLng = LatLng(it.latitude, it.longitude)
                        myLatLng = latLng
                        // Uso la función ya existente en tu proyecto:
                        distanciaKm = haversineKm(it.latitude, it.longitude, BODEGA_LAT, BODEGA_LON)
                        // Centro cámara en la ubicación del usuario
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngZoom(latLng, 13f)
                        )
                    }
                }
        }
    }

    // Obtener ubicación al entrar
    LaunchedEffect(Unit) {
        if (!hasLocationPermission()) {
            pedirPermisos.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    loc?.let {
                        val latLng = LatLng(it.latitude, it.longitude)
                        myLatLng = latLng
                        distanciaKm = haversineKm(it.latitude, it.longitude, BODEGA_LAT, BODEGA_LON)
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngZoom(latLng, 13f)
                        )
                    }
                }
        }
    }

    Column(modifier.fillMaxSize()) {

        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = true),
            // Habilito "mi ubicación" solo si hay permisos
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission())
        ) {
            // Bodega
            Marker(
                state = MarkerState(position = bodega),
                title = "Bodega",
                snippet = "Punto de referencia"
            )
            // Radio de despacho GRATIS
            Circle(
                center = bodega,
                radius = RADIO_GRATIS_KM * 1000.0,
                strokeWidth = 2f
            )
            // Mi ubicación
            myLatLng?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Mi ubicación"
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                Text(
                    "Distancia a bodega: ${
                        distanciaKm?.let { String.format("%.2f km", it) } ?: "--"
                    }"
                )

                OutlinedTextField(
                    value = totalCompra,
                    onValueChange = { totalCompra = it },
                    singleLine = true,
                    label = { Text("Total compra (CLP)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val total = totalCompra
                            .replace(".", "")
                            .replace(",", "")
                            .toIntOrNull()
                        val km = distanciaKm

                        resultado = if (total != null && km != null) {
                            val costo = when {
                                total >= 50_000 && km <= RADIO_GRATIS_KM -> 0
                                total >= 25_000 -> (150 * km).toInt()
                                else -> (300 * km).toInt()
                            }
                            if (costo == 0)
                                "Despacho GRATIS (≤ ${RADIO_GRATIS_KM.toInt()} km y total ≥ \$50.000)"
                            else
                                "Costo despacho: \$%,d".format(costo)
                        } else {
                            "Completa el total y permite la ubicación."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge
                ) { Text("Calcular despacho") }

                resultado?.let { Text(it) }
            }
        }
    }
}