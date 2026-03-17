package com.yi.actioncamera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoCall
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.launch

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
        mutableStateOf(prefs.getString("camera_ip", DEFAULT_IP) ?: DEFAULT_IP)
    }
    var cameraPassword by remember {
        mutableStateOf(prefs.getString("camera_password", DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD)
    }
    var status by remember { mutableStateOf(ConnectionStatus.IDLE) }
    var statusMessage by remember { mutableStateOf(context.getString(R.string.status_ready)) }

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
            Toast.makeText(context, R.string.permission_required_toast, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (status != ConnectionStatus.CONNECTED) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_title)
                            )
                        }
                    } else {
                        TextButton(onClick = { 
                            status = ConnectionStatus.IDLE 
                            statusMessage = context.getString(R.string.status_ready)
                        }) {
                            Text(stringResource(R.string.btn_disconnect), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (status == ConnectionStatus.CONNECTED) {
            ControlScreen(
                cameraIp = cameraIp,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            ConnectionContent(
                status = status,
                statusMessage = statusMessage,
                cameraIp = cameraIp,
                onConnectClick = {
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
                modifier = Modifier.padding(innerPadding)
            )
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

@Composable
fun ConnectionContent(
    status: ConnectionStatus,
    statusMessage: String,
    cameraIp: String,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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
                    modifier = Modifier.padding(bottom = 32.dp)
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
            onClick = onConnectClick,
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE53935),
                contentColor = Color.White
            )
        ) {
            Text(
                text = if (status == ConnectionStatus.ERROR) 
                    stringResource(R.string.btn_retry) 
                else stringResource(R.string.btn_connect)
            )
        }
    }
}

@Composable
fun ControlScreen(
    cameraIp: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = stringResource(R.string.camera_connected_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF4CAF50)
        )
        
        Text(
            text = "IP : $cameraIp",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Bouton Photo
        Button(
            onClick = {
                scope.launch {
                    takePhoto(cameraIp).onSuccess {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, context.getString(R.string.toast_error_prefix, it.message), Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.btn_take_photo), style = MaterialTheme.typography.titleLarge)
        }

        // Bouton Vidéo
        Button(
            onClick = {
                scope.launch {
                    if (isRecording) {
                        stopVideo(cameraIp).onSuccess {
                            isRecording = false
                            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.toast_error_prefix, it.message), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        startVideo(cameraIp).onSuccess {
                            isRecording = true
                            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.toast_error_prefix, it.message), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.VideoCall,
                contentDescription = null
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (isRecording) 
                    stringResource(R.string.btn_stop_video) 
                else stringResource(R.string.btn_start_video),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
fun SettingsDialog(
    currentIp: String,
    currentPassword: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempIp by remember { mutableStateOf(currentIp) }
    var tempPassword by remember { mutableStateOf(currentPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_network_label), style = MaterialTheme.typography.labelLarge)

                OutlinedTextField(
                    value = tempPassword,
                    onValueChange = { tempPassword = it },
                    label = { Text(stringResource(R.string.settings_pwd_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible)
                            Icons.Filled.Visibility
                        else Icons.Filled.VisibilityOff

                        val description = if (passwordVisible) 
                            stringResource(R.string.settings_pwd_hide) 
                        else stringResource(R.string.settings_pwd_show)

                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    }
                )

                OutlinedTextField(
                    value = tempIp,
                    onValueChange = { tempIp = it },
                    label = { Text(stringResource(R.string.settings_ip_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                TextButton(
                    onClick = { 
                        tempIp = DEFAULT_IP
                        tempPassword = DEFAULT_PASSWORD
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.btn_reset_settings))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(tempIp, tempPassword) }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
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
