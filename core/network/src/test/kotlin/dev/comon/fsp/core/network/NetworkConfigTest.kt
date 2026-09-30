package dev.comon.fsp.core.network

import dev.comon.fsp.core.network.auth.LoginRequest
import dev.comon.fsp.core.network.auth.LoginResponse
import dev.comon.fsp.core.network.auth.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkConfigTest {
    @Test fun blankMeansNotConfigured() {
        assertNull(NetworkConfig.parse("  ", "1.0").baseUrl)
    }

    @Test fun httpsBaseUrlIsNormalizedWithTrailingSlash() {
        assertEquals("https://api.example.com/api/v1/", NetworkConfig.parse("https://api.example.com/api/v1", "1.0").baseUrl.toString())
        assertEquals("https://api.example.com/api/v1/", NetworkConfig.parse("https://api.example.com/api/v1/", "1.0").baseUrl.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun cleartextIsRejectedOutsideTests() {
        NetworkConfig.parse("http://api.example.com/api/v1", "1.0")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidUrlIsRejected() {
        NetworkConfig.parse("not a url", "1.0")
    }

    @Test fun credentialsAreMaskedInDebugStrings() {
        assertFalse(LoginRequest("worker001", "Secret!1234ab", "d").toString().contains("Secret"))
        val response = LoginResponse("at-secret", 900, "rt-secret", UserDto("u", "worker001", "n", 3, "INTERVIEWER", true), 1)
        assertFalse(response.toString().contains("secret"))
    }
}
