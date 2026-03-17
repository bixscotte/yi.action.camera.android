package com.yi.actioncamera

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class CameraUtilsTest {

    @Test
    fun `takePhoto should return success when camera responds with token and success`() = runBlocking {
        // GIVEN
        val mockSocket = mockk<Socket>(relaxed = true)
        val mockInput = mockk<InputStream>()
        val mockOutput = mockk<OutputStream>(relaxed = true)
        
        val socketProvider = object : CameraSocketProvider {
            override fun createSocket(): Socket = mockSocket
        }

        val authResponse = "{\"rval\":0,\"param\":123}".toByteArray()
        val cmdResponse = "{\"rval\":0}".toByteArray()

        // Simulation de lectures séquentielles
        every { mockInput.read(any()) } answers {
            val buffer = firstArg<ByteArray>()
            authResponse.copyInto(buffer)
            authResponse.size
        } andThenAnswer {
            val buffer = it.invocation.args[0] as ByteArray
            cmdResponse.copyInto(buffer)
            cmdResponse.size
        }

        every { mockSocket.getInputStream() } returns mockInput
        every { mockSocket.getOutputStream() } returns mockOutput

        // WHEN
        val result = takePhoto("192.168.42.1", socketProvider)

        // THEN
        assertTrue("Le résultat devrait être un succès", result.isSuccess)
        assertTrue("Le message devrait contenir le token 123", result.getOrNull()?.contains("Token: 123") == true)
    }

    @Test
    fun `takePhoto should return failure when authentication fails`() = runBlocking {
        // GIVEN
        val mockSocket = mockk<Socket>(relaxed = true)
        val mockInput = mockk<InputStream>()
        
        val socketProvider = object : CameraSocketProvider {
            override fun createSocket(): Socket = mockSocket
        }

        val errorResponse = "{\"rval\":-1}".toByteArray()
        
        every { mockInput.read(any()) } answers {
            val buffer = firstArg<ByteArray>()
            errorResponse.copyInto(buffer)
            errorResponse.size
        }

        every { mockSocket.getInputStream() } returns mockInput
        every { mockSocket.getOutputStream() } returns mockk(relaxed = true)

        // WHEN
        val result = takePhoto("192.168.42.1", socketProvider)

        // THEN
        assertTrue("Le résultat devrait être un échec", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("impossible d'obtenir le token") == true)
    }
}
