package dev.comon.fsp.core.network

import dev.comon.fsp.core.network.auth.AuthApi
import dev.comon.fsp.core.network.auth.LoginRequest
import dev.comon.fsp.core.network.auth.LogoutRequest
import dev.comon.fsp.core.network.di.NetworkModule
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.http.GET
import java.time.Clock

/** v1.4 §7.1/§11.3: a 401 triggers at most one refresh and one resumed request. */
class TokenAuthenticatorTest {
    @Serializable
    data class Probe(val ok: Boolean)

    interface ProbeApi {
        @GET("probe")
        suspend fun probe(): Response<ApiSuccess<Probe>>
    }

    private val server = MockWebServer()
    @Volatile private var token: String? = "old-token"
    private val refreshCalls = mutableListOf<String?>()
    private var refreshed: String? = "new-token"
    private lateinit var client: ApiClient

    @Before fun setUp() {
        server.start()
        val refresher = TokenRefresher { failed ->
            refreshCalls += failed
            refreshed?.also { token = it }
        }
        val okHttp = NetworkModule.apiOkHttpClient(ClientHeadersInterceptor({ "device-1" }, { token }, "1.0-test"))
            .newBuilder().authenticator(TokenAuthenticator(refresher)).build()
        client = ApiClient(
            NetworkConfig.parse(server.url("/api/v1").toString(), "1.0-test", allowCleartext = true),
            okHttp, NetworkModule.apiJson(), Clock.systemUTC(),
        )
    }

    @After fun tearDown() = server.close()

    private fun unauthorized() = MockResponse.Builder().code(401)
        .body("""{"error":{"code":"UNAUTHENTICATED","message":"m","retryable":false}}""").build()

    private fun ok() = MockResponse.Builder().code(200).body("""{"data":{"ok":true}}""").build()

    @Test fun unauthorizedRequestIsRefreshedOnceAndResumed() = runTest {
        server.enqueue(unauthorized())
        server.enqueue(ok())

        val result = client.call(ProbeApi::class) { probe() }

        assertTrue(result is ApiResult.Success)
        assertEquals(listOf<String?>("old-token"), refreshCalls)
        val first = server.takeRequest()
        val retried = server.takeRequest()
        assertEquals("Bearer old-token", first.headers["Authorization"])
        assertEquals("Bearer new-token", retried.headers["Authorization"])
        assertNotEquals(first.headers["X-Request-ID"], retried.headers["X-Request-ID"])
    }

    @Test fun secondUnauthorizedAfterRefreshIsReturnedWithoutLooping() = runTest {
        server.enqueue(unauthorized())
        server.enqueue(unauthorized())

        val failure = (client.call(ProbeApi::class) { probe() } as ApiResult.Failure).failure as ApiFailure.Http

        assertEquals(401, failure.status)
        assertEquals(1, refreshCalls.size)
        assertEquals(2, server.requestCount)
    }

    @Test fun unrecoverableSessionReturnsOriginalUnauthorized() = runTest {
        refreshed = null
        server.enqueue(unauthorized())

        val failure = (client.call(ProbeApi::class) { probe() } as ApiResult.Failure).failure as ApiFailure.Http

        assertEquals("UNAUTHENTICATED", failure.code)
        assertEquals(1, server.requestCount)
    }

    @Test fun missingAccessTokenAfterRestoreIsRecoveredByRefresh() = runTest {
        token = null
        server.enqueue(unauthorized())
        server.enqueue(ok())

        assertTrue(client.call(ProbeApi::class) { probe() } is ApiResult.Success)
        assertEquals(listOf<String?>(null), refreshCalls)
        assertNull(server.takeRequest().headers["Authorization"])
        assertEquals("Bearer new-token", server.takeRequest().headers["Authorization"])
    }

    @Test fun authEndpointsNeverTriggerRefresh() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":{"code":"INVALID_CREDENTIALS","message":"m","retryable":false}}""").build())
        server.enqueue(unauthorized())

        client.call(AuthApi::class) { login(LoginRequest("worker001", "Example!1234A", "device-1")) }
        client.callNoContent(AuthApi::class) { logout(LogoutRequest("r".repeat(43))) }

        assertTrue(refreshCalls.isEmpty())
        assertEquals(2, server.requestCount)
    }
}
