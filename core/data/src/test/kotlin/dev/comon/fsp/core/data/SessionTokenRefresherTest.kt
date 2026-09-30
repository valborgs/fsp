package dev.comon.fsp.core.data

import dev.comon.fsp.core.database.entity.LocalSessionEntity
import dev.comon.fsp.core.database.entity.SessionMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class SessionTokenRefresherTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var env: DataTestEnv
    private lateinit var refresher: SessionTokenRefresher

    @Before fun setUp() = runTest {
        env = DataTestEnv(folder.root)
        refresher = SessionTokenRefresher(env.credentials, env.apiClient(), { DEVICE_ID }, env.sessions, FIXED_CLOCK)
        env.credentials.store(USER_ID, token("old"), token("old", 'B'))
        env.sessions.start(LocalSessionEntity("s1", SessionMode.ACCOUNT, USER_ID, DEVICE_ID, 3, 1))
    }

    @After fun tearDown() = env.close()

    @Test fun refreshRotatesTokensWithIdempotencyKey() {
        env.enqueue(200, """{"data":${tokenPairJson("new")}}""")

        assertEquals(token("new"), refresher.refreshAfterUnauthorized(token("old")))

        val request = env.server.takeRequest()
        assertEquals("/api/v1/auth/refresh", request.url.encodedPath)
        UUID.fromString(request.headers["Idempotency-Key"])
        assertNull(request.headers["Authorization"])
        val body = Json.parseToJsonElement(request.body!!.utf8()) as JsonObject
        assertEquals(token("old", 'B'), body["refreshToken"]!!.jsonPrimitive.content)
        assertEquals(DEVICE_ID, body["deviceId"]!!.jsonPrimitive.content)
        assertEquals(token("new"), env.accessTokens.accessToken())
        assertEquals(token("new", 'B'), env.credentials.refreshToken(USER_ID))
        assertFalse("key is done once the pair rotated", env.keyFile.exists())
    }

    @Test fun tokenAlreadyReplacedByAnotherCallerIsReused() {
        assertEquals(token("old"), refresher.refreshAfterUnauthorized("some-stale-token"))
        assertEquals(0, env.server.requestCount)
    }

    @Test fun transientFailureKeepsSessionAndReusesTheSameKey() = runTest {
        env.enqueue(503, errorJson("SERVICE_UNAVAILABLE", true))
        env.enqueue(503, errorJson("SERVICE_UNAVAILABLE", true))

        assertNull(refresher.refreshAfterUnauthorized(token("old")))
        assertNull(refresher.refreshAfterUnauthorized(token("old")))

        val first = env.server.takeRequest().headers["Idempotency-Key"]
        val second = env.server.takeRequest().headers["Idempotency-Key"]
        assertEquals(first, second)
        assertEquals(token("old", 'B'), env.credentials.refreshToken(USER_ID))
        assertNotNull(env.sessions.current())
    }

    @Test fun keySurvivesProcessRestart() {
        env.enqueue(503, errorJson("SERVICE_UNAVAILABLE", true))
        env.enqueue(200, """{"data":${tokenPairJson("new")}}""")
        refresher.refreshAfterUnauthorized(token("old"))

        val restarted = env.newCredentials().apply { activate(USER_ID) }
        env.accessTokens.clear()
        SessionTokenRefresher(restarted, env.apiClient(), { DEVICE_ID }, env.sessions, FIXED_CLOCK).refreshAfterUnauthorized(null)

        assertEquals(env.server.takeRequest().headers["Idempotency-Key"], env.server.takeRequest().headers["Idempotency-Key"])
    }

    @Test fun rejectedRefreshEndsTheSession() = runTest {
        listOf(401 to "TOKEN_REUSE_DETECTED", 401 to "INVALID_REFRESH_TOKEN", 403 to "DEVICE_REVOKED", 409 to "DEVICE_MISMATCH")
            .forEach { (status, code) ->
                env.credentials.store(USER_ID, token("old"), token("old", 'B'))
                env.sessions.start(LocalSessionEntity("s-$code", SessionMode.ACCOUNT, USER_ID, DEVICE_ID, 3, 2))
                env.enqueue(status, errorJson(code))

                assertNull(code, refresher.refreshAfterUnauthorized(token("old")))
                assertNull(code, env.credentials.currentUserId)
                assertFalse(code, env.tokenFile.exists())
                assertNull(code, env.sessions.current())
            }
    }

    @Test fun noSignedInAccountMeansNoRequest() {
        env.credentials.clear()
        assertNull(refresher.refreshAfterUnauthorized(null))
        assertEquals(0, env.server.requestCount)
    }
}
