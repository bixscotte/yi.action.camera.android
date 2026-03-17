package com.yi.actioncamera

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.PatternMatcher
import android.widget.Toast

enum class ConnectionStatus {
    IDLE, SEARCHING, CONNECTED, ERROR
}

const val DEFAULT_PASSWORD = "1234567890"
const val DEFAULT_IP = "192.168.42.1"

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
