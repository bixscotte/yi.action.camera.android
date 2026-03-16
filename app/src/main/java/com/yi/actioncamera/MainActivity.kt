package com.yi.actioncamera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.os.PatternMatcher
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit

enum class ConnectionStatus {
    IDLE, SEARCHING, CONNECTED, ERROR
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

private const val defaultPassword = "1234567890"
private const val defaultIp = "192.168.42.1"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE) }
    
    var showSettings by remember { mutableStateOf(false) }
    var cameraIp by remember { 
        mutableStateOf(prefs.getString("camera_ip", defaultIp) ?: defaultIp)
    }
    var cameraPassword by remember {
        mutableStateOf(prefs.getString("camera_password", defaultPassword) ?: defaultPassword)
    }
    var status by remember { mutableStateOf(ConnectionStatus.IDLE) }
    var statusMessage by remember { mutableStateOf("Prêt pour la connexion") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.NEARBY_WIFI_DEVICES] ?: false
        } else {
            false
        }
        
        val canConnect = nearbyGranted || fineGranted || coarseGranted
        
        if (canConnect) {
            connectToCameraWifi(context, cameraPassword) { newStatus, msg -> 
                status = newStatus
                statusMessage = msg 
            }
        } else {
            Toast.makeText(context, "Permissions nécessaires pour le Wi-Fi", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YIPilot") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuration"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Zone d'affichage du statut
            when (status) {
                ConnectionStatus.SEARCHING -> {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                }
                ConnectionStatus.ERROR -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 32.dp),
                        color = if (status == ConnectionStatus.CONNECTED) Color(0xFF4CAF50) else Color.Unspecified
                    )
                }
            }

            if (status != ConnectionStatus.ERROR) {
                Text(
                    text = "IP : $cameraIp",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
            
            Button(
                onClick = {
                    val permissionsNeeded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
                    } else {
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    }

                    val allGranted = permissionsNeeded.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }

                    if (allGranted) {
                        connectToCameraWifi(context, cameraPassword) { newStatus, msg -> 
                            status = newStatus
                            statusMessage = msg 
                        }
                    } else {
                        permissionLauncher.launch(permissionsNeeded)
                    }
                },
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935),
                    contentColor = Color.White
                )
            ) {
                Text(text = if (status == ConnectionStatus.ERROR) "Réessayer la connexion" else "Se connecter au Wi-Fi")
            }
        }

        if (showSettings) {
            SettingsDialog(
                currentIp = cameraIp,
                currentPassword = cameraPassword,
                onSave = { newIp, newPassword ->
                    cameraIp = newIp
                    cameraPassword = newPassword
                    prefs.edit {
                        putString("camera_ip", newIp)
                        putString("camera_password", newPassword)
                    }
                    showSettings = false
                },
                onDismiss = { showSettings = false }
            )
        }
    }
}

fun connectToCameraWifi(context: Context, password: String, onStatusUpdate: (ConnectionStatus, String) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsidPattern(PatternMatcher("YDXJ_.*", PatternMatcher.PATTERN_SIMPLE_GLOB))
            .setWpa2Passphrase(password)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        onStatusUpdate(ConnectionStatus.SEARCHING, "Ouverture de la sélection Wi-Fi...")
        
        try {
            connectivityManager.requestNetwork(
                request, 
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        connectivityManager.bindProcessToNetwork(network)
                        onStatusUpdate(ConnectionStatus.CONNECTED, "Connecté à la caméra !")
                    }

                    override fun onUnavailable() {
                        super.onUnavailable()
                        onStatusUpdate(
                            ConnectionStatus.ERROR, 
                            "Caméra introuvable. Vérifiez que le Wi-Fi de la caméra est activé (bouton Wi-Fi sur le côté) et que vous êtes à proximité."
                        )
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        connectivityManager.bindProcessToNetwork(null)
                        onStatusUpdate(ConnectionStatus.IDLE, "Connexion perdue")
                    }
                },
                30000 // Timeout de 30 secondes
            )
        } catch (e: SecurityException) {
            onStatusUpdate(ConnectionStatus.ERROR, "Erreur de sécurité : permissions manquantes.")
        }
    } else {
        onStatusUpdate(ConnectionStatus.IDLE, "Veuillez vous connecter manuellement (Android < 10)")
        Toast.makeText(context, "Version d'Android trop ancienne pour la connexion auto", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun SettingsDialog(
    currentIp: String,
    currentPassword: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultIp = defaultIp
    val defaultPassword = defaultPassword
    var tempIp by remember { mutableStateOf(currentIp) }
    var tempPassword by remember { mutableStateOf(currentPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paramètres réseau", style = MaterialTheme.typography.labelLarge)

                OutlinedTextField(
                    value = tempPassword,
                    onValueChange = { tempPassword = it },
                    label = { Text("Mot de passe Wi-Fi") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible)
                            Icons.Filled.Visibility
                        else Icons.Filled.VisibilityOff

                        val description = if (passwordVisible) "Cacher le mot de passe" else "Afficher le mot de passe"

                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    }
                )

                OutlinedTextField(
                    value = tempIp,
                    onValueChange = { tempIp = it },
                    label = { Text("Adresse IP de la caméra") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                TextButton(
                    onClick = { 
                        tempIp = defaultIp
                        tempPassword = defaultPassword
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Réinitialiser les paramètres")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(tempIp, tempPassword) }) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MaterialTheme {
        MainScreen()
    }
}
