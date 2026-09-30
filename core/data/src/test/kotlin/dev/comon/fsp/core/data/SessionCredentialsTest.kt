package dev.comon.fsp.core.data

import dev.comon.fsp.core.security.RefreshTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionCredentialsTest {
    @get:Rule val folder = TemporaryFolder()

    private val tokenFile by lazy { File(folder.root, "no_backup/session/refresh-token") }
    private val holder = AccessTokenHolder()
    private val credentials by lazy { SessionCredentials(holder, RefreshTokenStore(tokenFile, FakeCipher())) }

    @Test fun storeKeepsAccessTokenInMemoryAndRefreshTokenEncrypted() {
        credentials.store("user-1", "at-1", "rt-secret")
        assertEquals("at-1", holder.accessToken())
        assertFalse(tokenFile.readText().contains("rt-secret"))
        assertEquals("rt-secret", credentials.refreshToken("user-1"))
    }

    @Test fun anotherAccountCannotReadTheToken() {
        credentials.store("user-1", "at-1", "rt-1")
        assertNull(credentials.refreshToken("user-2"))
    }

    @Test fun logoutClearsBothTokens() {
        credentials.store("user-1", "at-1", "rt-1")
        credentials.clear()
        assertNull(holder.accessToken())
        assertNull(credentials.refreshToken("user-1"))
        assertFalse(tokenFile.exists())
    }
}
