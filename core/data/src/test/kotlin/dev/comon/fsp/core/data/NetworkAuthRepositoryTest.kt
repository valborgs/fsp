package dev.comon.fsp.core.data

import dev.comon.fsp.core.database.entity.SessionMode
import dev.comon.fsp.core.network.ApiClient
import dev.comon.fsp.core.network.ClientHeadersInterceptor
import dev.comon.fsp.core.network.NetworkConfig
import dev.comon.fsp.core.network.di.NetworkModule
import dev.comon.fsp.domain.AccountSession
import dev.comon.fsp.domain.LoginFailure
import dev.comon.fsp.domain.LoginResult
import dev.comon.fsp.domain.Role
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NetworkAuthRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var env: DataTestEnv
    private lateinit var repository: NetworkAuthRepository

    @Before fun setUp() {
        env = DataTestEnv(folder.root)
        repository = NetworkAuthRepository(env.apiClient(), { DEVICE_ID }, env.credentials, env.sessions, FIXED_CLOCK)
    }

    @After fun tearDown() = env.close()

    private fun loginSuccess(grade: Int = 3, role: String = "INTERVIEWER", nextSequence: String = "1") =
        """{"data":{"tokens":${tokenPairJson("login")},
            "user":{"userId":"$USER_ID","id":"Worker001","name":"홍길동","grade":$grade,"role":"$role","active":true,"resourceVersion":2},
            "activeDeviceId":"$DEVICE_ID","deviceNextSequence":$nextSequence},
           "meta":{"requestId":"00000000-0000-4000-8000-000000000065","serverTime":"2026-09-30T00:05:10.000Z"}}"""

    @Test fun successStoresTokensAndOpensAccountSession() = runTest {
        env.enqueue(200, loginSuccess())

        val result = repository.login(" Worker001 ", "Example!1234A")

        assertEquals(LoginResult.Success(AccountSession(USER_ID, "worker001", "홍길동", Role.INTERVIEWER)), result)
        val request = env.server.takeRequest()
        val body = Json.parseToJsonElement(request.body!!.utf8()) as JsonObject
        assertEquals("Worker001", body["id"]!!.jsonPrimitive.content)
        assertEquals("Example!1234A", body["pw"]!!.jsonPrimitive.content)
        assertEquals(request.headers["X-Device-ID"], body["deviceId"]!!.jsonPrimitive.content)

        assertEquals(token("login"), env.accessTokens.accessToken())
        assertEquals(token("login", 'B'), env.credentials.refreshToken(USER_ID))
        assertFalse(env.tokenFile.readText().contains(token("login", 'B')))
        val session = env.sessions.current()!!
        assertEquals(SessionMode.ACCOUNT, session.mode)
        assertEquals(USER_ID, session.userId)
        assertEquals(3, session.roleGrade)
        assertEquals("worker001", session.loginId)
        assertEquals("홍길동", session.displayName)
        assertEquals(DEVICE_ID, session.deviceId)
        assertEquals("00000000-0000-4000-8000-00000000000b", session.serverSessionId)
        assertEquals(1L, session.deviceNextSequence)
    }

    @Test fun managersHaveNoDeviceSequence() = runTest {
        env.enqueue(200, loginSuccess(grade = 2, role = "SUPERVISOR", nextSequence = "null"))
        val result = repository.login("manager001", "Example!1234A") as LoginResult.Success
        assertEquals(Role.SUPERVISOR, result.account.role)
        assertNull(env.sessions.current()!!.deviceNextSequence)
    }

    @Test fun inputOutsideTheRulesIsRejectedWithoutARequest() = runTest {
        listOf("abc" to "Example!1234A", "worker001" to "short1!", "worker001" to "NoDigits!!!!!", "work er01" to "Example!1234A")
            .forEach { (id, pw) ->
                assertEquals(LoginResult.Failure(LoginFailure.INVALID_CREDENTIALS), repository.login(id, pw))
            }
        assertEquals(0, env.server.requestCount)
    }

    @Test fun serverFailuresMapToLoginFailures() = runTest {
        val cases = listOf(
            Triple(401, errorJson("INVALID_CREDENTIALS"), LoginResult.Failure(LoginFailure.INVALID_CREDENTIALS)),
            Triple(409, errorJson("ACTIVE_DEVICE_EXISTS"), LoginResult.Failure(LoginFailure.ACTIVE_DEVICE_EXISTS)),
            Triple(403, errorJson("DEVICE_REVOKED"), LoginResult.Failure(LoginFailure.DEVICE_REVOKED)),
            Triple(422, errorJson("VALIDATION_FAILED"), LoginResult.Failure(LoginFailure.INVALID_CREDENTIALS)),
            Triple(500, errorJson("INTERNAL_ERROR", true), LoginResult.Failure(LoginFailure.SERVER_ERROR)),
            Triple(200, """{"data":{"unexpected":true}}""", LoginResult.Failure(LoginFailure.SERVER_ERROR)),
        )
        for ((status, body, expected) in cases) {
            env.enqueue(status, body)
            assertEquals("HTTP $status", expected, repository.login("worker001", "Example!1234A"))
        }
        assertNull(env.accessTokens.accessToken())
        assertNull(env.sessions.current())
        assertFalse(env.tokenFile.exists())
    }

    @Test fun rateLimitCarriesWaitTime() = runTest {
        env.enqueue(429, errorJson("RATE_LIMITED", true), "Retry-After" to "30")
        assertEquals(LoginResult.Failure(LoginFailure.RATE_LIMITED, 30), repository.login("worker001", "Example!1234A"))
    }

    @Test fun roleGradeMismatchIsRejected() = runTest {
        env.enqueue(200, loginSuccess(grade = 3, role = "ADMIN"))
        assertEquals(LoginResult.Failure(LoginFailure.SERVER_ERROR), repository.login("worker001", "Example!1234A"))
        assertNull(env.accessTokens.accessToken())
    }

    @Test fun unreachableServerIsNetworkFailure() = runTest {
        env.server.close()
        assertEquals(LoginResult.Failure(LoginFailure.NETWORK), repository.login("worker001", "Example!1234A"))
    }

    @Test fun missingBaseUrlIsNotConfiguredWithoutARequest() = runTest {
        val unconfigured = ApiClient(
            NetworkConfig.parse("", "1.0-test"),
            NetworkModule.apiOkHttpClient(ClientHeadersInterceptor({ DEVICE_ID }, env.accessTokens, "1.0-test")),
            NetworkModule.apiJson(), FIXED_CLOCK,
        )
        val repo = NetworkAuthRepository(unconfigured, { DEVICE_ID }, env.credentials, env.sessions, FIXED_CLOCK)
        assertEquals(LoginResult.Failure(LoginFailure.SERVER_NOT_CONFIGURED), repo.login("worker001", "Example!1234A"))
        assertEquals(0, env.server.requestCount)
    }

    @Test fun failingToSaveTheSessionKeepsNothing() = runTest {
        env.sessions.failOnStart = true
        env.enqueue(200, loginSuccess())

        assertEquals(LoginResult.Failure(LoginFailure.STORAGE), repository.login("worker001", "Example!1234A"))
        assertNull(env.accessTokens.accessToken())
        assertNull(env.credentials.currentUserId)
        assertFalse(env.tokenFile.exists())
    }
}
