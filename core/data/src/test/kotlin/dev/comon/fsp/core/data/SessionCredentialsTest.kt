package dev.comon.fsp.core.data

import dev.comon.fsp.core.security.RefreshTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionCredentialsTest {
    @get:Rule val folder = TemporaryFolder()

    private val tokenFile by lazy { File(folder.root, "no_backup/session/refresh-token") }
    private val keyFile by lazy { File(folder.root, "no_backup/session/refresh-idempotency-key") }
    private val holder = AccessTokenHolder()
    private val credentials by lazy { newCredentials() }

    private fun newCredentials() =
        SessionCredentials(holder, RefreshTokenStore(tokenFile, FakeCipher()), RefreshKeyStore(keyFile))

    @Test fun storeKeepsAccessTokenInMemoryAndRefreshTokenEncrypted() {
        credentials.store("user-1", "at-1", "rt-secret")
        assertEquals("at-1", holder.accessToken())
        assertEquals("user-1", credentials.currentUserId)
        assertFalse(tokenFile.readText().contains("rt-secret"))
        assertEquals("rt-secret", credentials.refreshToken("user-1"))
    }

    @Test fun anotherAccountCannotReadTheToken() {
        credentials.store("user-1", "at-1", "rt-1")
        assertNull(credentials.refreshToken("user-2"))
    }

    @Test fun activateAfterRestartNeedsTheStoredToken() {
        credentials.store("user-1", "at-1", "rt-1")
        val restarted = newCredentials()
        assertFalse(restarted.activate("user-2"))
        assertNull(restarted.currentUserId)
        assertTrue(restarted.activate("user-1"))
        assertEquals("user-1", restarted.currentUserId)
    }

    @Test fun pendingRefreshKeyIsStableUntilNewTokensAreStored() {
        credentials.store("user-1", "at-1", "rt-1")
        val key = credentials.pendingRefreshKey()
        assertEquals(key, newCredentials().pendingRefreshKey())
        credentials.store("user-1", "at-2", "rt-2")
        assertFalse(keyFile.exists())
    }

    @Test fun logoutClearsTokensAndPendingKey() {
        credentials.store("user-1", "at-1", "rt-1")
        credentials.pendingRefreshKey()
        credentials.clear()
        assertNull(holder.accessToken())
        assertNull(credentials.currentUserId)
        assertNull(credentials.refreshToken("user-1"))
        assertFalse(tokenFile.exists())
        assertFalse(keyFile.exists())
    }
}
