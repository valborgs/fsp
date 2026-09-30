package dev.comon.fsp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import dev.comon.fsp.core.database.entity.OutboxKind
import dev.comon.fsp.core.network.ApiFailure
import dev.comon.fsp.core.network.ApiResult
import dev.comon.fsp.core.network.auth.AuthApi
import dev.comon.fsp.core.network.auth.LoginRequest
import dev.comon.fsp.di.InfrastructureGraphEntryPoint
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Hilt graph with the real Android Keystore on device. The test build has no API base URL. */
@RunWith(AndroidJUnit4::class)
class InfrastructureGraphTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val graph = EntryPointAccessors.fromApplication(context, InfrastructureGraphEntryPoint::class.java)
    private val tokenFile = File(context.noBackupFilesDir, "session/refresh-token")

    @After fun signOut() = graph.sessionCredentials().clear()

    @Test fun unconfiguredBuildReportsNotConfiguredWithoutCalling() = runTest {
        val result = graph.apiClient().call(AuthApi::class) { login(LoginRequest("worker001", "pw", "device")) }
        assertEquals(ApiResult.Failure(ApiFailure.NotConfigured), result)
    }

    @Test fun deviceIdIsStableUuidOutsideBackup() {
        val id = graph.deviceIdProvider().deviceId()
        UUID.fromString(id)
        assertEquals(id, graph.deviceIdProvider().deviceId())
        assertEquals(id, File(context.noBackupFilesDir, "installation-id").readText())
    }

    @Test fun credentialsAreEncryptedAtRestAndClearedOnLogout() {
        val credentials = graph.sessionCredentials()
        credentials.store("user-1", "at-secret", "rt-secret")
        assertEquals("at-secret", graph.accessTokenProvider().accessToken())
        assertFalse(tokenFile.readText().contains("rt-secret"))
        assertEquals("rt-secret", credentials.refreshToken("user-1"))

        credentials.clear()
        assertNull(graph.accessTokenProvider().accessToken())
        assertFalse(tokenFile.exists())
    }

    @Test fun outboxPayloadIsEncryptedWithKeystore() {
        val operations = graph.outboxOperations()
        val row = operations.create("user-1", OutboxKind.ATTENDANCE_EVENT, "a1", """{"status":"ON"}""", now = 0)
        assertFalse(row.payloadJson.contains("ON"))
        assertEquals("""{"status":"ON"}""", operations.payload(row))
    }
}
