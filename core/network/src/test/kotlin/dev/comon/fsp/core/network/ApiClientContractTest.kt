package dev.comon.fsp.core.network

import dev.comon.fsp.core.network.auth.AuthApi
import dev.comon.fsp.core.network.auth.LoginRequest
import dev.comon.fsp.core.network.di.NetworkModule
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.http.GET
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit

/** HTTP contract of spec section 11/12 against a local MockWebServer. No real server is involved. */
class ApiClientContractTest {
    @Serializable
    data class Probe(val ok: Boolean)

    /** Test-only authenticated endpoint. */
    interface ProbeApi {
        @GET("probe")
        suspend fun probe(): Response<ApiSuccess<Probe>>
    }

    private val server = MockWebServer()
    private val clock = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC)
    private var token: String? = null
    private var interceptedCalls = 0
    private lateinit var client: ApiClient

    private fun apiClient(baseUrl: String): ApiClient {
        val headers = ClientHeadersInterceptor({ "device-1" }, { token }, "1.0-test")
        val okHttp = NetworkModule.apiOkHttpClient(headers, readTimeoutMillis = 500).newBuilder()
            .addInterceptor { chain -> interceptedCalls++; chain.proceed(chain.request()) }
            .build()
        return ApiClient(NetworkConfig.parse(baseUrl, "1.0-test", allowCleartext = true), okHttp, NetworkModule.apiJson(), clock)
    }

    @Before fun setUp() {
        server.start()
        client = apiClient(server.url("/api/v1").toString())
    }

    @After fun tearDown() = server.close()

    private fun enqueue(code: Int, body: String, vararg headers: Pair<String, String>) {
        val builder = MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json")
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        server.enqueue(builder.build())
    }

    private suspend fun probe() = client.call(ProbeApi::class) { probe() }

    private val loginSuccess = """
        {"data":{"accessToken":"at-1","expiresIn":900,"refreshToken":"rt-1","credentialVersion":2,
          "user":{"userId":"u-1","id":"worker001","name":"홍길동","grade":3,"role":"INTERVIEWER","active":true,
                  "resourceVersion":7}},
         "meta":{"requestId":"req-1","serverTime":"2026-09-30T00:00:00Z"}}
    """.trimIndent()

    @Test fun loginRequestAndResponseFollowContract() = runTest {
        token = "stale-token"
        enqueue(200, loginSuccess)

        val result = client.call(AuthApi::class) { login(LoginRequest("worker001", " Secret!1234ab ", "device-1")) }

        val success = result as ApiResult.Success
        assertEquals("at-1", success.data.accessToken)
        assertEquals(3, success.data.user.grade)
        assertEquals("req-1", success.meta?.requestId)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/auth/login", recorded.url.encodedPath)
        assertTrue(recorded.headers["Content-Type"]!!.startsWith("application/json"))
        val body = Json.parseToJsonElement(recorded.body!!.utf8()) as JsonObject
        assertEquals(
            mapOf("id" to "worker001", "pw" to " Secret!1234ab ", "deviceId" to "device-1"),
            body.mapValues { (it.value as JsonPrimitive).content },
        )
        UUID.fromString(recorded.headers["X-Request-ID"])
        assertEquals("device-1", recorded.headers["X-Device-ID"])
        assertEquals("1.0-test", recorded.headers["X-App-Version"])
        assertNull("login is public and must not send a token", recorded.headers["Authorization"])
        assertNull("internal marker must not leave the device", recorded.headers["X-Fsp-No-Auth"])
    }

    @Test fun authenticatedRequestSendsBearerOnlyWhenSessionExists() = runTest {
        enqueue(200, """{"data":{"ok":true}}""")
        enqueue(200, """{"data":{"ok":true}}""")
        token = "at-1"
        probe()
        token = null
        probe()
        assertEquals("Bearer at-1", server.takeRequest().headers["Authorization"])
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test fun everyRequestGetsNewRequestId() = runTest {
        enqueue(200, """{"data":{"ok":true}}""")
        enqueue(200, """{"data":{"ok":true}}""")
        probe()
        probe()
        assertNotEquals(server.takeRequest().headers["X-Request-ID"], server.takeRequest().headers["X-Request-ID"])
    }

    @Test fun errorEnvelopeIsParsed() = runTest {
        enqueue(
            422,
            """{"error":{"code":"VALIDATION_FAILED","message":"입력값을 확인해 주세요.","retryable":false,
               "fields":[{"path":"answers[0].value","code":"REQUIRED"}]},"meta":{"requestId":"req-9"}}""",
        )
        val failure = (probe() as ApiResult.Failure).failure as ApiFailure.Http
        assertEquals(HttpFailureKind.UNPROCESSABLE, failure.kind)
        assertEquals("VALIDATION_FAILED", failure.code)
        assertEquals(listOf(ApiFieldError("answers[0].value", "REQUIRED")), failure.fields)
        assertEquals("req-9", failure.requestId)
        assertFalse(failure.retryable)
    }

    @Test fun statusCodesMapToKindAndRetryability() = runTest {
        val expected = mapOf(
            400 to (HttpFailureKind.BAD_REQUEST to false),
            401 to (HttpFailureKind.UNAUTHORIZED to false),
            403 to (HttpFailureKind.FORBIDDEN to false),
            404 to (HttpFailureKind.NOT_FOUND to false),
            408 to (HttpFailureKind.REQUEST_TIMEOUT to true),
            409 to (HttpFailureKind.CONFLICT to false),
            410 to (HttpFailureKind.RETENTION_EXPIRED to false),
            412 to (HttpFailureKind.PRECONDITION_FAILED to false),
            413 to (HttpFailureKind.PAYLOAD_TOO_LARGE to false),
            418 to (HttpFailureKind.UNEXPECTED to false),
            422 to (HttpFailureKind.UNPROCESSABLE to false),
            428 to (HttpFailureKind.PRECONDITION_REQUIRED to false),
            429 to (HttpFailureKind.TOO_MANY_REQUESTS to true),
            500 to (HttpFailureKind.SERVER_ERROR to true),
            503 to (HttpFailureKind.SERVER_ERROR to true),
        )
        for ((status, kindAndRetry) in expected) {
            enqueue(status, """{"error":{"code":"C$status"}}""")
            val failure = (probe() as ApiResult.Failure).failure as ApiFailure.Http
            assertEquals("status $status", kindAndRetry.first, failure.kind)
            assertEquals("status $status", kindAndRetry.second, failure.retryable)
            assertEquals("C$status", failure.code)
        }
    }

    @Test fun retentionExpiredIsFinal() = runTest {
        enqueue(410, """{"error":{"code":"RETENTION_EXPIRED","retryable":false}}""")
        val failure = (probe() as ApiResult.Failure).failure as ApiFailure.Http
        assertEquals("RETENTION_EXPIRED", failure.code)
        assertFalse(failure.retryable)
    }

    @Test fun tooManyRequestsCarriesRetryAfter() = runTest {
        enqueue(429, """{"error":{"code":"RATE_LIMITED","retryable":true}}""", "Retry-After" to "120")
        val failure = (probe() as ApiResult.Failure).failure as ApiFailure.Http
        assertEquals(120_000L, failure.retryAfterMillis)
        assertTrue(failure.retryable)
    }

    @Test fun nonEnvelopeErrorBodyStillMapsByStatus() = runTest {
        enqueue(502, "<html>Bad Gateway</html>", "X-Request-ID" to "proxy-req")
        val failure = (probe() as ApiResult.Failure).failure as ApiFailure.Http
        assertEquals(HttpFailureKind.SERVER_ERROR, failure.kind)
        assertNull(failure.code)
        assertEquals("proxy-req", failure.requestId)
        assertTrue(failure.retryable)
    }

    @Test fun successBodyOutsideContractIsMalformed() = runTest {
        enqueue(200, """{"unexpected":1}""")
        enqueue(200, "not json")
        assertEquals(ApiResult.Failure(ApiFailure.MalformedResponse), probe())
        assertEquals(ApiResult.Failure(ApiFailure.MalformedResponse), probe())
    }

    @Test fun slowServerIsTimeout() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"data":{"ok":true}}""").headersDelay(2, TimeUnit.SECONDS).build(),
        )
        assertEquals(ApiResult.Failure(ApiFailure.Network(NetworkFailureReason.TIMEOUT)), probe())
    }

    @Test fun unreachableServerIsConnectivityFailure() = runTest {
        server.close()
        val result = probe()
        assertEquals(ApiResult.Failure(ApiFailure.Network(NetworkFailureReason.CONNECTIVITY)), result)
        assertTrue((result as ApiResult.Failure).failure.retryable)
    }

    @Test fun missingBaseUrlMakesNoRequest() = runTest {
        val unconfigured = apiClient("")
        assertEquals(ApiResult.Failure(ApiFailure.NotConfigured), unconfigured.call(ProbeApi::class) { probe() })
        assertEquals(0, interceptedCalls)
        assertEquals(0, server.requestCount)
    }

    @Test fun parseRetryAfterFormats() {
        val now = Instant.parse("2026-09-30T00:00:00Z")
        assertEquals(30_000L, ApiClient.parseRetryAfter("30", now))
        assertEquals(90_000L, ApiClient.parseRetryAfter("Wed, 30 Sep 2026 00:01:30 GMT", now))
        assertEquals(0L, ApiClient.parseRetryAfter("Tue, 29 Sep 2026 23:00:00 GMT", now))
        assertNull(ApiClient.parseRetryAfter("-5", now))
        assertNull(ApiClient.parseRetryAfter("soon", now))
        assertNull(ApiClient.parseRetryAfter(null, now))
    }
}
