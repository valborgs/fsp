package dev.comon.fsp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import dev.comon.fsp.core.network.ApiFailure
import dev.comon.fsp.core.network.ApiResult
import dev.comon.fsp.core.network.auth.AuthApi
import dev.comon.fsp.core.network.auth.LoginRequest
import dev.comon.fsp.di.NetworkGraphEntryPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Hilt network graph on device. The test build has no API base URL, so nothing is sent. */
@RunWith(AndroidJUnit4::class)
class NetworkGraphTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val graph = EntryPointAccessors.fromApplication(context, NetworkGraphEntryPoint::class.java)

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
}
