package dev.comon.fsp.core.data

import dev.comon.fsp.core.database.entity.LocalSessionEntity
import dev.comon.fsp.core.network.ApiClient
import dev.comon.fsp.core.network.ClientHeadersInterceptor
import dev.comon.fsp.core.network.NetworkConfig
import dev.comon.fsp.core.network.TokenAuthenticator
import dev.comon.fsp.core.network.TokenRefresher
import dev.comon.fsp.core.network.di.NetworkModule
import dev.comon.fsp.core.security.RefreshTokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

const val DEVICE_ID = "00000000-0000-4000-8000-000000000003"
const val USER_ID = "00000000-0000-4000-8000-000000000001"
val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-30T00:05:10Z"), ZoneOffset.UTC)

/** In-memory local_session with the same open/close semantics as the Room store. */
class FakeAccountSessionStore : AccountSessionStore {
    val rows = mutableListOf<LocalSessionEntity>()
    var failOnStart = false
    private val current = MutableStateFlow<LocalSessionEntity?>(null)

    override suspend fun current(): LocalSessionEntity? = rows.lastOrNull { it.endedAt == null }
    override fun observeCurrent(): Flow<LocalSessionEntity?> = current

    override suspend fun start(session: LocalSessionEntity) {
        if (failOnStart) throw IllegalStateException("disk full")
        endOpen(session.createdAt)
        rows += session
        current.value = session
    }

    override suspend fun endOpen(now: Long) {
        rows.replaceAll { if (it.endedAt == null) it.copy(endedAt = now) else it }
        current.value = null
    }
}

/** Real credentials (fake cipher) and a MockWebServer-backed ApiClient, as wired in production. */
class DataTestEnv(dir: File) {
    val server = MockWebServer().apply { start() }
    val tokenFile = File(dir, "session/refresh-token")
    val keyFile = File(dir, "session/refresh-idempotency-key")
    val accessTokens = AccessTokenHolder()
    val credentials = newCredentials()
    val sessions = FakeAccountSessionStore()

    /** Same files, fresh memory: models the process after a restart. */
    fun newCredentials() = SessionCredentials(accessTokens, RefreshTokenStore(tokenFile, FakeCipher()), RefreshKeyStore(keyFile))

    fun apiClient(refresher: TokenRefresher? = null): ApiClient {
        var okHttp = NetworkModule.apiOkHttpClient(ClientHeadersInterceptor({ DEVICE_ID }, accessTokens, "1.0-test"))
        if (refresher != null) okHttp = okHttp.newBuilder().authenticator(TokenAuthenticator(refresher)).build()
        return ApiClient(
            NetworkConfig.parse(server.url("/api/v1").toString(), "1.0-test", allowCleartext = true),
            okHttp, NetworkModule.apiJson(), FIXED_CLOCK,
        )
    }

    fun enqueue(code: Int, body: String = "", vararg headers: Pair<String, String>) {
        val builder = MockResponse.Builder().code(code)
        if (body.isNotEmpty()) builder.body(body).addHeader("Content-Type", "application/json")
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        server.enqueue(builder.build())
    }

    fun close() = server.close()
}

fun token(prefix: String, fill: Char = 'A') = prefix.padEnd(43, fill)

fun tokenPairJson(prefix: String): String =
    """{"tokenType":"Bearer","accessToken":"${token(prefix)}","expiresIn":900,"accessExpiresAt":"2026-09-30T00:20:10.000Z",
       "refreshToken":"${token(prefix, 'B')}","refreshExpiresIn":2591000,"refreshExpiresAt":"2026-10-29T23:48:30.000Z",
       "sessionId":"00000000-0000-4000-8000-00000000000b","credentialVersion":1}"""

fun errorJson(code: String, retryable: Boolean = false) =
    """{"error":{"code":"$code","message":"m","retryable":$retryable,"fields":[],"details":{"expectedSequence":null,
       "currentState":null,"currentVersion":null,"retryAfterSeconds":null,"serverTime":null,"conflictingFields":[]}},
       "meta":{"requestId":"00000000-0000-4000-8000-000000000099","serverTime":"2026-09-30T00:05:10.000Z"}}"""
