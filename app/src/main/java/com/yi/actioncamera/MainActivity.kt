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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.yi.actioncamera.ui.theme.AppTheme
import com.yi.actioncamera.ui.theme.YiPilotTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("camera_prefs", MODE_PRIVATE) }
            var currentTheme by remember { 
                mutableStateOf(AppTheme.valueOf(prefs.getString("app_theme", AppTheme.WHITE.name) ?: AppTheme.WHITE.name)) 
            }

            YiPilotTheme(appTheme = currentTheme) {
                MainScreen(
                    currentTheme = currentTheme,
                    onThemeChange = { newTheme -> 
                        currentTheme = newTheme
                        prefs.edit { putString("app_theme", newTheme.name) }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    initialStatus: ConnectionStatus = ConnectionStatus.IDLE
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE) }
    
    var showSettings by remember { mutableStateOf(false) }
    var cameraIp by remember { 
        mutableStateOf(prefs.getString("camera_ip", DEFAULT_IP) ?: DEFAULT_IP)
    }
    var cameraPassword by remember {
        mutableStateOf(prefs.getString("camera_password", DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD)
    }
    var status by remember { mutableStateOf(initialStatus) }
    val readyStatus = stringResource(R.string.status_ready)
    var statusMessage by remember(readyStatus) { mutableStateOf(readyStatus) }

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
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (status == ConnectionStatus.CONNECTED) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    if (status != ConnectionStatus.CONNECTED) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        IconButton(onClick = { 
                            status = ConnectionStatus.IDLE 
                            statusMessage = readyStatus
                        }) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = stringResource(R.string.btn_disconnect),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (status == ConnectionStatus.CONNECTED) {
                ControlScreen(cameraIp = cameraIp)
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
                    }
                )
            }
        }

        if (showSettings) {
            SettingsDialog(
                currentIp = cameraIp,
                currentPassword = cameraPassword,
                currentTheme = currentTheme,
                onThemeChange = onThemeChange,
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
                CircularProgressIndicator(
                    modifier = Modifier.padding(bottom = 16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 32.dp),
                    color = MaterialTheme.colorScheme.onBackground
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
                    modifier = Modifier.padding(bottom = 32.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Button(
            onClick = onConnectClick,
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
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
    var isLoadingOptions by remember { mutableStateOf(false) }
    var cameraOptions by remember { mutableStateOf<Map<String, String>?>(null) }
    val errorPrefix = stringResource(R.string.toast_error_prefix)

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
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Text(
            text = "IP : $cameraIp",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Bouton Photo
        Button(
            onClick = {
                scope.launch {
                    takePhoto(cameraIp).onSuccess {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "$errorPrefix ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.6f).height(64.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
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
                            Toast.makeText(context, "$errorPrefix ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        startVideo(cameraIp).onSuccess {
                            isRecording = true
                            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "$errorPrefix ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.6f).height(64.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
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

        // Bouton Options Caméra
        Button(
            onClick = {
                scope.launch {
                    isLoadingOptions = true
                    getCameraOptions(cameraIp).onSuccess { options ->
                        cameraOptions = options
                    }.onFailure {
                        Toast.makeText(context, "$errorPrefix ${it.message}", Toast.LENGTH_LONG).show()
                    }
                    isLoadingOptions = false
                }
            },
            modifier = Modifier.fillMaxWidth(0.6f).height(64.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (isLoadingOptions) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.btn_camera_settings), style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    if (cameraOptions != null) {
        CameraOptionsDialog(
            options = cameraOptions!!,
            onDismiss = { cameraOptions = null }
        )
    }
}

@Composable
fun CameraOptionsDialog(
    options: Map<String, String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.camera_settings_dialog_title)) },
        text = {
            LazyColumn(modifier = Modifier.height(400.dp)) {
                items(options.toList()) { (key, value) ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(text = key, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Text(text = value, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        }
    )
}

@Composable
fun SettingsDialog(
    currentIp: String,
    currentPassword: String,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempIp by remember { mutableStateOf(currentIp) }
    var tempPassword by remember { mutableStateOf(currentPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title), color = MaterialTheme.colorScheme.onSurface) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                // Section Thème
                Column {
                    Text("Apparence", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ThemeColorSelector(Color.White, "Blanc", currentTheme == AppTheme.WHITE) {
                            onThemeChange(AppTheme.WHITE)
                        }
                        ThemeColorSelector(Color.Black, "Noir", currentTheme == AppTheme.BLACK) {
                            onThemeChange(AppTheme.BLACK)
                        }
                        ThemeColorSelector(Color(0xFFCDDC39), "Vert", currentTheme == AppTheme.GREEN) {
                            onThemeChange(AppTheme.GREEN)
                        }
                    }
                }

                Text(stringResource(R.string.settings_network_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)

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

                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = Color.Transparent
                    )
                ) {
                    Text(stringResource(R.string.btn_reset_settings))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(tempIp, tempPassword) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
fun ThemeColorSelector(
    color: Color,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (isSelected) Modifier.background(color).padding(2.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.5f))
                    else Modifier
                )
        ) {
            if (isSelected) {
                 Box(Modifier.fillMaxSize().padding(4.dp).clip(CircleShape).background(color))
            }
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// WHITE PREVIEW
////////////////////////////////////////////////////////////////////////////////////////////////////

@Preview(showBackground = true)
@Composable
fun WhiteMainScreenPreview() {
    YiPilotTheme(appTheme = AppTheme.WHITE) {
        MainScreen(currentTheme = AppTheme.WHITE, onThemeChange = {})
    }
}

@Preview(showBackground = true)
@Composable
fun WhiteControlScreenPreview() {
    YiPilotTheme(appTheme = AppTheme.WHITE) {
        MainScreen(currentTheme = AppTheme.WHITE, onThemeChange = {}, initialStatus = ConnectionStatus.CONNECTED)
    }
}

@Preview(showBackground = true)
@Composable
fun WhiteSettingsDialogPreview() {
    YiPilotTheme(appTheme = AppTheme.WHITE) {
        Box(Modifier.fillMaxSize()) {
            SettingsDialog(
                currentIp = "192.168.42.1",
                currentPassword = "********",
                currentTheme = AppTheme.WHITE,
                onThemeChange = {},
                onSave = { _, _ -> },
                onDismiss = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WhiteCameraOptionsDialogPreview() {
    val options = mapOf(
        "video_resolution" to "1920x1080 60P 16:9",
        "photo_size" to "16M (4608x3456) 4:3",
        "field_of_view" to "Wide"
    )
    YiPilotTheme(appTheme = AppTheme.WHITE) {
        Box(Modifier.fillMaxSize()) {
            CameraOptionsDialog(options = options, onDismiss = {})
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// BLACK PREVIEW
////////////////////////////////////////////////////////////////////////////////////////////////////

@Preview(showBackground = true)
@Composable
fun BlackMainScreenPreview() {
    YiPilotTheme(appTheme = AppTheme.BLACK) {
        MainScreen(currentTheme = AppTheme.BLACK, onThemeChange = {})
    }
}

@Preview(showBackground = true)
@Composable
fun BlackControlScreenPreview() {
    YiPilotTheme(appTheme = AppTheme.BLACK) {
        MainScreen(currentTheme = AppTheme.BLACK, onThemeChange = {}, initialStatus = ConnectionStatus.CONNECTED)
    }
}

@Preview(showBackground = true)
@Composable
fun BlackSettingsDialogPreview() {
    YiPilotTheme(appTheme = AppTheme.BLACK) {
        Box(Modifier.fillMaxSize()) {
            SettingsDialog(
                currentIp = "192.168.42.1",
                currentPassword = "********",
                currentTheme = AppTheme.BLACK,
                onThemeChange = {},
                onSave = { _, _ -> },
                onDismiss = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BlackCameraOptionsDialogPreview() {
    val options = mapOf(
        "video_resolution" to "1920x1080 60P 16:9",
        "photo_size" to "16M (4608x3456) 4:3",
        "field_of_view" to "Wide"
    )
    YiPilotTheme(appTheme = AppTheme.BLACK) {
        Box(Modifier.fillMaxSize()) {
            CameraOptionsDialog(options = options, onDismiss = {})
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// GREEN PREVIEW
////////////////////////////////////////////////////////////////////////////////////////////////////

@Preview(showBackground = true)
@Composable
fun GreenMainScreenPreview() {
    YiPilotTheme(appTheme = AppTheme.GREEN) {
        MainScreen(currentTheme = AppTheme.GREEN, onThemeChange = {})
    }
}

@Preview(showBackground = true)
@Composable
fun GreenControlScreenPreview() {
    YiPilotTheme(appTheme = AppTheme.GREEN) {
        MainScreen(currentTheme = AppTheme.GREEN, onThemeChange = {}, initialStatus = ConnectionStatus.CONNECTED)
    }
}

@Preview(showBackground = true)
@Composable
fun GreenSettingsDialogPreview() {
    YiPilotTheme(appTheme = AppTheme.GREEN) {
        Box(Modifier.fillMaxSize()) {
            SettingsDialog(
                currentIp = "192.168.42.1",
                currentPassword = "********",
                currentTheme = AppTheme.GREEN,
                onThemeChange = {},
                onSave = { _, _ -> },
                onDismiss = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreenCameraOptionsDialogPreview() {
    val options = mapOf(
        "video_resolution" to "1920x1080 60P 16:9",
        "photo_size" to "16M (4608x3456) 4:3",
        "field_of_view" to "Wide"
    )
    YiPilotTheme(appTheme = AppTheme.GREEN) {
        Box(Modifier.fillMaxSize()) {
            CameraOptionsDialog(options = options, onDismiss = {})
        }
    }
}
