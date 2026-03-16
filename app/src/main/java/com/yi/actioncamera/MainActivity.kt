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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE) }
    
    var showSettings by remember { mutableStateOf(false) }
    var cameraIp by remember { 
        mutableStateOf(prefs.getString("camera_ip", "192.168.42.1") ?: "192.168.42.1") 
    }
    var cameraPassword by remember {
        mutableStateOf(prefs.getString("camera_password", "12345678") ?: "12345678")
    }
    var connectionStatus by remember { mutableStateOf("Prêt pour la connexion") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            connectToCameraWifi(context, cameraPassword) { status -> connectionStatus = status }
        } else {
            Toast.makeText(context, "Permission de localisation nécessaire pour le Wi-Fi", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YI Action Camera") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "IP : $cameraIp",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = connectionStatus,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            Button(
                onClick = { 
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        connectToCameraWifi(context, cameraPassword) { status -> connectionStatus = status }
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935),
                    contentColor = Color.White
                )
            ) {
                Text(text = "Se connecter au Wi-Fi")
            }
        }

        if (showSettings) {
            SettingsDialog(
                currentIp = cameraIp,
                currentPassword = cameraPassword,
                onSave = { newIp, newPassword ->
                    cameraIp = newIp
                    cameraPassword = newPassword
                    prefs.edit()
                        .putString("camera_ip", newIp)
                        .putString("camera_password", newPassword)
                        .apply()
                    showSettings = false
                },
                onDismiss = { showSettings = false }
            )
        }
    }
}

fun connectToCameraWifi(context: Context, password: String, onStatusChange: (String) -> Unit) {
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
        
        onStatusChange("Recherche de la caméra...")
        
        connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                connectivityManager.bindProcessToNetwork(network)
                onStatusChange("Connecté à la caméra !")
            }

            override fun onUnavailable() {
                super.onUnavailable()
                onStatusChange("Caméra introuvable ou mot de passe incorrect")
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                connectivityManager.bindProcessToNetwork(null)
                onStatusChange("Connexion perdue")
            }
        })
    } else {
        onStatusChange("Veuillez vous connecter manuellement (Android < 10)")
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
    val defaultIp = "192.168.42.1"
    val defaultPassword = "12345678"
    var tempIp by remember { mutableStateOf(currentIp) }
    var tempPassword by remember { mutableStateOf(currentPassword) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paramètres réseau", style = MaterialTheme.typography.labelLarge)
                
                OutlinedTextField(
                    value = tempIp,
                    onValueChange = { tempIp = it },
                    label = { Text("Adresse IP de la caméra") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = tempPassword,
                    onValueChange = { tempPassword = it },
                    label = { Text("Mot de passe Wi-Fi") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
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
