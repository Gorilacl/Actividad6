package cl.andres.semana4

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlin.math.*
import java.text.NumberFormat
import java.util.Locale

// Plaza de Armas
private const val BODEGA_LAT = -35.016
private const val BODEGA_LON = -71.333

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

private const val RADIO_GRATIS_KM = 20.0

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppScreen() }

        val grados = 90.0
        val rad = gradosARadianes(grados)
        println("$grados grados = $rad radianes")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
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
                println("$valor grados = $rad radianes")
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
                        permisos.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
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
            val total = totalTxt.replace(".", "").replace(',', '.').toDoubleOrNull()?.toInt()
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
                println("DEBUG despacho → total=$total, km=$km, costo=${r.costoDespacho}, gratis=${r.aplicaGratis}")
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) { Text("Calcular despacho") }

    if (mensaje.isNotBlank()) Text(mensaje)
}