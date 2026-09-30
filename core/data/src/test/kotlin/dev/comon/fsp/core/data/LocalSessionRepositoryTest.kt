package dev.comon.fsp.core.data

import dev.comon.fsp.core.database.entity.LocalSessionEntity
import dev.comon.fsp.core.database.entity.SessionMode
import dev.comon.fsp.core.network.TokenRefresher
import dev.comon.fsp.domain.AccountSession
import dev.comon.fsp.domain.Role
import kotlinx.coroutines.flow.first
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

class LocalSessionRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var env: DataTestEnv
    private val account = AccountSession(USER_ID, "worker001", "홍길동", Role.INTERVIEWER)
    private val row = LocalSessionEntity(
        "s1", SessionMode.ACCOUNT, USER_ID, DEVICE_ID, 3, 1, loginId = "worker001", displayName = "홍길동",
    )

    @Before fun setUp() {
        env = DataTestEnv(folder.root)
    }

    @After fun tearDown() = env.close()

    private fun repository(
        credentials: SessionCredentials = env.credentials,
        refresher: TokenRefresher = TokenRefresher { null },
    ) = LocalSessionRepository(env.sessions, credentials, env.apiClient(), refresher, FIXED_CLOCK)

    @Test fun restoreReattachesStoredSessionAfterRestart() = runTest {
        env.credentials.store(USER_ID, token("at"), token("at", 'B'))
        env.sessions.start(row)
        env.accessTokens.clear()
        val restarted = env.newCredentials()

        assertEquals(account, repository(restarted).restore())
        assertEquals(USER_ID, restarted.currentUserId)
        assertEquals(account, repository(restarted).observeCurrentAccount().first())
    }

    @Test fun sessionWithoutStoredTokenIsEndedOnRestore() = runTest {
        env.sessions.start(row)

        assertNull(repository().restore())
        assertNull(env.sessions.current())
    }

    @Test fun anonymousSessionIsNotAnAccount() = runTest {
        env.sessions.start(LocalSessionEntity("a1", SessionMode.ANONYMOUS, null, DEVICE_ID, null, 1))
        assertNull(repository().restore())
    }

    @Test fun logoutRevokesServerSessionAndClearsLocally() = runTest {
        env.credentials.store(USER_ID, token("at"), token("at", 'B'))
        env.sessions.start(row)
        env.enqueue(204)

        repository().logout()

        val request = env.server.takeRequest()
        assertEquals("/api/v1/auth/logout", request.url.encodedPath)
        assertEquals("Bearer ${token("at")}", request.headers["Authorization"])
        val body = Json.parseToJsonElement(request.body!!.utf8()) as JsonObject
        assertEquals(token("at", 'B'), body["refreshToken"]!!.jsonPrimitive.content)
        assertNull(env.accessTokens.accessToken())
        assertFalse(env.tokenFile.exists())
        assertNull(env.sessions.current())
    }

    @Test fun logoutStillSignsOutWhenServerRejectsOrIsUnreachable() = runTest {
        env.credentials.store(USER_ID, token("at"), token("at", 'B'))
        env.sessions.start(row)
        env.enqueue(401, errorJson("UNAUTHENTICATED"))

        repository().logout()

        assertNull(env.credentials.currentUserId)
        assertNull(env.sessions.current())
        env.credentials.store(USER_ID, token("at"), token("at", 'B'))
        env.sessions.start(row.copy(sessionId = "s2"))
        env.server.close()

        repository().logout()

        assertNull(env.credentials.currentUserId)
        assertNull(env.sessions.current())
    }

    @Test fun logoutAfterRestartRefreshesFirstToAuthenticate() = runTest {
        env.credentials.store(USER_ID, token("at"), token("at", 'B'))
        env.sessions.start(row)
        env.accessTokens.clear()
        val refresher = TokenRefresher { env.credentials.store(USER_ID, token("fresh"), token("fresh", 'B')); token("fresh") }
        env.enqueue(204)

        repository(refresher = refresher).logout()

        val request = env.server.takeRequest()
        assertEquals("Bearer ${token("fresh")}", request.headers["Authorization"])
        val body = Json.parseToJsonElement(request.body!!.utf8()) as JsonObject
        assertEquals("rotated token is the one revoked", token("fresh", 'B'), body["refreshToken"]!!.jsonPrimitive.content)
    }
}
