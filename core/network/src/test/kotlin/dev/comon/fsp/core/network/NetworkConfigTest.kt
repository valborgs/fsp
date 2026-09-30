package dev.comon.fsp.core.network

import dev.comon.fsp.core.network.auth.LoginRequest
import dev.comon.fsp.core.network.auth.LogoutRequest
import dev.comon.fsp.core.network.auth.RefreshRequest
import dev.comon.fsp.core.network.auth.TokenPairDto
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

    @Test fun appVersionMustBeValidHeaderValue() {
        NetworkConfig.parse("", "1.0.0-rc.1+build.7")
        listOf("", "1.0 beta", "버전1", "v".repeat(33)).forEach {
            assertFalse(it, runCatching { NetworkConfig.parse("", it) }.isSuccess)
        }
    }

    @Test fun credentialsAreMaskedInDebugStrings() {
        assertFalse(LoginRequest("worker001", "Secret!1234ab", "d").toString().contains("Secret"))
        assertFalse(RefreshRequest("secret-refresh", "d").toString().contains("secret"))
        assertFalse(LogoutRequest("secret-refresh").toString().contains("secret"))
        val tokens = TokenPairDto("Bearer", "at-secret", 900, "t", "rt-secret", 1, "t", "s", 1)
        assertFalse(tokens.toString().contains("secret"))
    }
}
