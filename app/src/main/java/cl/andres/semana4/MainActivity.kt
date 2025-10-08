package cl.andres.semana4

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.database.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.*

// mis clases/recursos
import cl.andres.semana4.RangesViewModel
import cl.andres.semana4.SettingsActivity
import cl.andres.semana4.R

// ------ NUEVO: Google Maps Compose ------
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.*

// --- Constantes ---
private const val BODEGA_LAT = -35.016
private const val BODEGA_LON = -71.333
private const val RADIO_GRATIS_KM = 20.0

// --- Utilidades ---
fun gradosARadianes(grados: Double): Double = grados * Math.PI / 180.0

fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0
    val dLat = gradosARadianes(lat2 - lat1)
    val dLon = gradosARadianes(lon2 - lon1)
    val lat1R = gradosARadianes(lat1)
    val lat2R = gradosARadianes(lat2)
    val a = sin(dLat / 2).pow(2) + cos(lat1R) * cos(lat2R) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}

data class ShippingInput(val totalCompra: Int, val distanciaKm: Double)
data class ShippingResult(val costoDespacho: Int, val aplicaGratis: Boolean)

fun calcularDespacho(input: ShippingInput): ShippingResult {
    val total = input.totalCompra
    val km = input.distanciaKm
    return if (total >= 50_000 && km <= RADIO_GRATIS_KM) {
        ShippingResult(0, true)
    } else if (total >= 25_000) {
        ShippingResult((150 * km).toInt(), false)
    } else {
        ShippingResult((300 * km).toInt(), false)
    }
}

fun formatoCLP(valor: Int): String {
    val nf = NumberFormat.getIntegerInstance(Locale("es", "CL"))
    return "$" + nf.format(valor)
}

// =======================================================
// MainActivity
// =======================================================
class MainActivity : ComponentActivity() {

    // Firebase + alarma + ViewModel (Room)
    private lateinit var db: DatabaseReference
    private var vib: Vibrator? = null       // ahora nullable
    private var mp: MediaPlayer? = null     // ahora nullable
    private val vm: RangesViewModel by viewModels { RangesViewModel.factory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        mp  = MediaPlayer.create(this, R.raw.alerta) // res/raw/alerta.mp3 (minúsculas)
        db  = FirebaseDatabase.getInstance().reference

        setContent {
            AppScreen(
                // Suscripción a RTDB en tiempo real
                subscribeTemperature = { onValue ->
                    db.child("sensors").child("truck1").child("temperatura")
                        .addValueEventListener(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val f = snapshot.getValue(Double::class.java) ?: return
                                onValue(f)
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                },
                // Rangos desde Room
                getRanges = { cb -> vm.getRanges(cb) },
                // Alarma si sale de rango
                onOutOfRange = { triggerAlarm() },
                // Abrir configuración
                onOpenSettings = {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
            )
        }
    }

    private fun triggerAlarm() {
        // vibración segura
        vib?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(700, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(700)
            }
        }
        // sonido seguro
        mp?.let { player ->
            if (!player.isPlaying) player.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // libero recursos para evitar leaks/crashes
        mp?.release()
        mp = null
        vib = null
    }
}

// =======================================================
// UI Compose (lo que ya tenías + monitor + MAPA SEMANA 9)
// =======================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    subscribeTemperature: ((Double) -> Unit) -> Unit = {},
    getRanges: ((Double, Double) -> Unit) -> Unit = {},
    onOutOfRange: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    var kmDetectado by remember { mutableStateOf<Double?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("TALLER DE APLICACIONES MÓVILES") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Aplicación.")
            ConversorRadianesUI()
            Divider()
            CalculadoraDespachoUI(kmDetectado = kmDetectado)
            Divider()
            DistanciaBodegaUI(
                bodegaLat = BODEGA_LAT,
                bodegaLon = BODEGA_LON,
                onKmDetectado = { km -> kmDetectado = km }
            )
            Divider()
            TemperatureMonitorUI(
                subscribeTemperature = subscribeTemperature,
                getRanges = getRanges,
                onOutOfRange = onOutOfRange,
                onOpenSettings = onOpenSettings
            )
            // -------- NUEVO: Mapa con bodega + mi ubicación + círculo 20km --------
            Divider()
            Text("Mapa (Bodega, radio 20 km y tu ubicación)", style = MaterialTheme.typography.titleMedium)
            MapSection(
                bodegaLat = BODEGA_LAT,
                bodegaLon = BODEGA_LON,
                onKmDetectado = { km -> kmDetectado = km }
            )
        }
    }
}

@Composable
fun ConversorRadianesUI() {
    var entrada by remember { mutableStateOf("") }
    var resultado by remember { mutableStateOf<Double?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    OutlinedTextField(
        value = entrada,
        onValueChange = { entrada = it; error = null },
        label = { Text("Grados (ej: 90)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        isError = error != null,
        supportingText = { if (error != null) Text(error!!) }
    )

    Button(
        onClick = {
            val valor = entrada.replace(',', '.').toDoubleOrNull()
            if (valor == null) {
                error = "Ingrese un número válido"
                resultado = null
            } else {
                val rad = gradosARadianes(valor)
                resultado = rad
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) { Text("Convertir a radianes") }

    resultado?.let { rad -> Text("Resultado: $rad rad") }
}

@Composable
fun DistanciaBodegaUI(
    bodegaLat: Double,
    bodegaLon: Double,
    onKmDetectado: (Double) -> Unit
) {
    val context = LocalContext.current
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val permisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    var distancia by remember { mutableStateOf<Double?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ubicación dispositivo a bodega")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val lacksFine = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                    val lacksCoarse = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED

                    if (lacksFine || lacksCoarse) {
                        permisos.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                        return@Button
                    }

                    fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { loc ->
                            if (loc != null) {
                                val km = haversineKm(loc.latitude, loc.longitude, bodegaLat, bodegaLon)
                                distancia = km
                                onKmDetectado(km)
                            } else {
                                error = "No fue posible obtener la ubicación actual."
                            }
                        }
                        .addOnFailureListener { error = it.message ?: "Fallo al obtener ubicación." }
                },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge
            ) { Text("Obtener y calcular") }

            distancia?.let { km ->
                AssistChip(
                    onClick = { onKmDetectado(km) },
                    label = { Text("Usar ${"%.2f".format(km)} km") }
                )
            }
        }

        if (distancia != null) Text("Distancia: ${"%.2f".format(distancia)} km")
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun CalculadoraDespachoUI(kmDetectado: Double? = null) {
    var totalTxt by remember { mutableStateOf("") }
    var kmTxt by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var errorTotal by remember { mutableStateOf<String?>(null) }
    var errorKm by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(kmDetectado) {
        if (kmDetectado != null && kmTxt.isBlank()) {
            kmTxt = "%.2f".format(kmDetectado)
        }
    }

    Text("Cálculo de despacho")

    OutlinedTextField(
        value = totalTxt,
        onValueChange = { totalTxt = it; errorTotal = null },
        label = { Text("Total compra (CLP)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        isError = errorTotal != null,
        supportingText = { if (errorTotal != null) Text(errorTotal!!) }
    )

    OutlinedTextField(
        value = kmTxt,
        onValueChange = { kmTxt = it; errorKm = null },
        label = { Text("Distancia (km)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        isError = errorKm != null,
        supportingText = { if (errorKm != null) Text(errorKm!!) }
    )

    Button(
        onClick = {
            val total = totalTxt.replace(".", "").replace(',', '.')
                .toDoubleOrNull()?.toInt()
            val km = kmTxt.replace(',', '.').toDoubleOrNull()

            if (total == null) errorTotal = "Ingrese un total válido (entero)"
            if (km == null) errorKm = "Ingrese kilómetros válidos (número)"

            if (total != null && km != null) {
                val r = calcularDespacho(ShippingInput(total, km))
                mensaje = if (r.aplicaGratis) {
                    "Despacho GRATIS (total ≥ \$50.000 y ≤ ${RADIO_GRATIS_KM.toInt()} km)"
                } else {
                    "Costo despacho: ${formatoCLP(r.costoDespacho)}"
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) { Text("Calcular despacho") }

    if (mensaje.isNotBlank()) Text(mensaje)
}

// -------- Monitor de temperatura (Parte B) --------
@Composable
fun TemperatureMonitorUI(
    subscribeTemperature: ((Double) -> Unit) -> Unit,
    getRanges: ((Double, Double) -> Unit) -> Unit,
    onOutOfRange: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var f by remember { mutableStateOf<Double?>(null) }
    var c by remember { mutableStateOf<Double?>(null) }
    var minC by remember { mutableStateOf(-5.0) }
    var maxC by remember { mutableStateOf(4.0) }

    // cargo rangos (Room)
    LaunchedEffect(Unit) { getRanges { min, max -> minC = min; maxC = max } }

    // escucho Firebase
    LaunchedEffect(Unit) {
        subscribeTemperature { fValue ->
            f = fValue
            c = (fValue - 32.0) * 5.0 / 9.0
            val cur = c!!
            if (cur < minC || cur > maxC) onOutOfRange()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Monitoreo de Temperatura", style = MaterialTheme.typography.titleMedium)
        Text("Fahrenheit: ${f?.let { String.format("%.2f °F", it) } ?: "--"}")
        Text("Celsius: ${c?.let { String.format("%.2f °C", it) } ?: "--"}")
        Text("Rango permitido: $minC °C  —  $maxC °C")
        Button(onClick = onOpenSettings) { Text("Configurar rangos") }
    }
}

// ==================== NUEVO: SECCIÓN MAPA (Compose) ====================
@Composable
fun MapSection(
    bodegaLat: Double,
    bodegaLon: Double,
    onKmDetectado: (Double) -> Unit
) {
    val context = LocalContext.current
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }

    val bodega = LatLng(bodegaLat, bodegaLon)
    var myLatLng by remember { mutableStateOf<LatLng?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(bodega, 12f)
    }

    // pedir permisos si faltan y capturar ubicación
    val permisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    loc?.let {
                        myLatLng = LatLng(it.latitude, it.longitude)
                        val km = haversineKm(it.latitude, it.longitude, bodegaLat, bodegaLon)
                        onKmDetectado(km)
                    }
                }
        }
    }

    LaunchedEffect(Unit) {
        val lacksFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
        val lacksCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED

        if (lacksFine && lacksCoarse) {
            permisos.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    loc?.let {
                        myLatLng = LatLng(it.latitude, it.longitude)
                        val km = haversineKm(it.latitude, it.longitude, bodegaLat, bodegaLon)
                        onKmDetectado(km)
                    }
                }
        }
    }

    // Mapa
    Box(Modifier.fillMaxWidth().height(280.dp)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = false
            ),
            properties = MapProperties(
                isMyLocationEnabled = myLatLng != null
            )
        ) {
            // marcador bodega
            Marker(
                state = MarkerState(position = bodega),
                title = "Bodega",
                snippet = "Punto de despacho"
            )
            // círculo de 20 km para despacho gratis
            Circle(
                center = bodega,
                radius = RADIO_GRATIS_KM * 1000.0,
                strokeWidth = 2f
            )
            // mi ubicación
            myLatLng?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Mi ubicación"
                )
            }
        }
    }
}