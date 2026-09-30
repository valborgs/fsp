package dev.comon.fsp.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.comon.fsp.core.database.entity.LocalSessionEntity
import dev.comon.fsp.core.database.entity.SessionMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSessionDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: FspDatabase

    @Before fun open() {
        context.deleteDatabase(NAME)
        db = FspDatabase.create(context, NAME)
    }

    @After fun close() {
        db.close()
        context.deleteDatabase(NAME)
    }

    private fun account(id: String, createdAt: Long) = LocalSessionEntity(
        id, SessionMode.ACCOUNT, "u-1", "d-1", 3, createdAt,
        loginId = "worker001", displayName = "홍길동", serverSessionId = "srv", deviceNextSequence = 4,
    )

    @Test fun latestOpenSessionIsCurrentUntilEnded() = runTest {
        val dao = db.localSessionDao()
        dao.insert(account("old", 1).copy(endedAt = 2))
        dao.insert(account("new", 3))

        assertEquals(account("new", 3), dao.current())
        assertEquals(account("new", 3), dao.observeCurrent().first())

        assertEquals(1, dao.endOpenSessions(10))
        assertNull(dao.current())
        assertNull(dao.observeCurrent().first())
        assertEquals(10L, dao.get("new")!!.endedAt)
        assertEquals("ended history keeps its original end", 2L, dao.get("old")!!.endedAt)
    }

    @Test fun signInTransactionEndsPreviousSession() = runTest {
        val dao = db.localSessionDao()
        dao.insert(account("first", 1))
        LocalWriteTransaction(db).invoke {
            dao.endOpenSessions(5)
            dao.insert(account("second", 5))
        }
        assertEquals("second", dao.current()!!.sessionId)
        assertEquals(5L, dao.get("first")!!.endedAt)
    }

    private companion object {
        const val NAME = "session-dao-test.db"
    }
}
