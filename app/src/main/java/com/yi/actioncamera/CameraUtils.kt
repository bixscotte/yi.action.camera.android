package com.yi.actioncamera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

private const val CAM_PORT = 7878

/**
 * Interface pour permettre le mocking de la connexion Socket dans les tests
 */
interface CameraSocketProvider {
    fun createSocket(): Socket
}

object DefaultCameraSocketProvider : CameraSocketProvider {
    override fun createSocket(): Socket = Socket()
}

/**
 * Prend une photo (msg_id: 769)
 */
suspend fun takePhoto(cameraIp: String, socketProvider: CameraSocketProvider = DefaultCameraSocketProvider): Result<String> =
    executeCameraCommand(cameraIp, 769, "Photo déclenchée !", socketProvider)

/**
 * Démarre l'enregistrement vidéo (msg_id: 513)
 */
suspend fun startVideo(cameraIp: String, socketProvider: CameraSocketProvider = DefaultCameraSocketProvider): Result<String> =
    executeCameraCommand(cameraIp, 513, "Enregistrement vidéo démarré !", socketProvider)

/**
 * Arrête l'enregistrement vidéo (msg_id: 514)
 */
suspend fun stopVideo(cameraIp: String, socketProvider: CameraSocketProvider = DefaultCameraSocketProvider): Result<String> =
    executeCameraCommand(cameraIp, 514, "Enregistrement vidéo arrêté !", socketProvider)

/**
 * Récupère tous les paramètres de la caméra (msg_id: 3)
 */
suspend fun getCameraOptions(cameraIp: String, socketProvider: CameraSocketProvider = DefaultCameraSocketProvider): Result<Map<String, String>> = withContext(Dispatchers.IO) {
    var socket: Socket? = null
    try {
        socket = socketProvider.createSocket()
        socket.connect(InetSocketAddress(cameraIp, CAM_PORT), 5000)
        socket.soTimeout = 5000
        val output = socket.getOutputStream()
        val input = socket.getInputStream()

        // 1. Authentification
        val token = getToken(output, input)
            ?: return@withContext Result.failure(Exception("Échec de l'authentification"))

        // 2. Commande pour récupérer les options (msg_id: 3)
        val command = "{\"msg_id\":3,\"token\":$token}"
        output.write(command.toByteArray())
        output.flush()

        // Lecture de la réponse
        val data = readResponse(input)
        val json = JSONObject(data)
        
        val optionsMap = mutableMapOf<String, String>()
        if (json.has("param")) {
            val params = json.getJSONArray("param")
            for (i in 0 until params.length()) {
                val obj = params.getJSONObject(i)
                val key = obj.keys().next()
                optionsMap[key] = obj.getString(key)
            }
        }
        
        Result.success(optionsMap)
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        socket?.close()
    }
}

/**
 * Fonction générique pour exécuter une commande après authentification
 */
private suspend fun executeCameraCommand(
    cameraIp: String,
    msgId: Int,
    successMessage: String,
    socketProvider: CameraSocketProvider
): Result<String> = withContext(Dispatchers.IO) {
    var socket: Socket? = null
    try {
        socket = socketProvider.createSocket()
        socket.connect(InetSocketAddress(cameraIp, CAM_PORT), 5000)
        socket.soTimeout = 5000
        val output = socket.getOutputStream()
        val input = socket.getInputStream()

        // 1. Authentification pour obtenir le token
        val token = getToken(output, input)
            ?: return@withContext Result.failure(Exception("Échec de l'authentification : impossible d'obtenir le token"))

        // 2. Envoi de la commande demandée
        val command = "{\"msg_id\":$msgId,\"token\":$token}"
        output.write(command.toByteArray())
        output.flush()

        // Lecture de la réponse de la caméra
        readResponse(input)

        Result.success("$successMessage (Token: $token)")
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignorer
        }
    }
}

/**
 * Logique pour récupérer le token de session.
 */
private fun getToken(output: OutputStream, input: InputStream): Int? {
    val authCmd = "{\"msg_id\":257,\"token\":0}"
    output.write(authCmd.toByteArray())
    output.flush()

    // On essaie de lire la réponse (jusqu'à 2 tentatives)
    repeat(2) {
        val data = readResponse(input)
        if (data.contains("rval")) {
            return try {
                val json = JSONObject(data)
                if (json.has("param")) {
                    json.getInt("param")
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    return null
}

/**
 * Lit les données brutes du socket et les convertit en String.
 */
private fun readResponse(input: InputStream): String {
    val buffer = ByteArray(4096) // Buffer plus grand pour les options
    return try {
        val bytesRead = input.read(buffer)
        if (bytesRead != -1) {
            String(buffer, 0, bytesRead, Charsets.UTF_8)
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
}
