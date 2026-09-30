package dev.comon.fsp.core.database

import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.comon.fsp.core.database.entity.LocalSessionEntity
import dev.comon.fsp.core.database.entity.OutboxEntity
import dev.comon.fsp.core.database.entity.OutboxKind
import dev.comon.fsp.core.database.entity.SessionMode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Local change + Outbox either commit together or not at all, on the real bundled SQLite driver. */
@RunWith(AndroidJUnit4::class)
class LocalWriteTransactionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: FspDatabase
    private lateinit var transaction: LocalWriteTransaction

    @Before fun open() {
        context.deleteDatabase(NAME)
        db = FspDatabase.create(context, NAME)
        transaction = LocalWriteTransaction(db)
    }

    @After fun close() {
        db.close()
        context.deleteDatabase(NAME)
    }

    private fun session(id: String) = LocalSessionEntity(id, SessionMode.ACCOUNT, "user-1", "device-1", 3, 1_000)
    private fun op(id: String, owner: String = "user-1", createdAt: Long = 1_000) = OutboxEntity(
        operationId = id, ownerUserId = owner, entityId = "entity-$id", kind = OutboxKind.ATTENDANCE_EVENT,
        payloadJson = "{}", nextAttemptAt = createdAt, createdAt = createdAt,
    )

    private suspend fun expectFailure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (e: Throwable) {
            return e
        }
        fail("expected failure")
        error("unreachable")
    }

    @Test fun commitsLocalChangeAndOutboxTogether() = runTest {
        transaction {
            db.localSessionDao().insert(session("s1"))
            db.outboxDao().insert(op("op1"))
        }
        assertNotNull(db.localSessionDao().get("s1"))
        assertEquals(1, db.outboxDao().count())
    }

    @Test fun failureAfterBothWritesRollsBackBoth() = runTest {
        val error = expectFailure {
            transaction {
                db.localSessionDao().insert(session("s1"))
                db.outboxDao().insert(op("op1"))
                throw IllegalStateException("simulated failure before commit")
            }
        }
        assertEquals("simulated failure before commit", error.message)
        assertNull(db.localSessionDao().get("s1"))
        assertEquals(0, db.outboxDao().count())
    }

    @Test fun outboxConstraintFailureRollsBackLocalChange() = runTest {
        db.outboxDao().insert(op("op1"))
        expectFailure {
            transaction {
                db.localSessionDao().insert(session("s2"))
                db.outboxDao().insert(op("op1")) // duplicate operationId must abort, not overwrite
            }
        }
        assertNull(db.localSessionDao().get("s2"))
        assertEquals(1, db.outboxDao().count())
    }

    @Test fun outboxRejectsRowWithoutOwner() = runTest {
        expectFailure {
            db.useWriterConnection {
                it.executeSQL(
                    "INSERT INTO outbox (operationId, ownerUserId, entityId, kind, payloadJson, state, " +
                        "attemptCount, retryCycle, nextAttemptAt, createdAt) " +
                        "VALUES ('op-anon', NULL, 'e', 'RESPONSE_SUBMIT', '{}', 'PENDING', 0, 0, 0, 0)",
                )
            }
        }
        assertEquals(0, db.outboxDao().count())
    }

    @Test fun pendingIsScopedToOwnerInCreationOrder() = runTest {
        db.outboxDao().insert(op("late", createdAt = 3_000))
        db.outboxDao().insert(op("early", createdAt = 2_000))
        db.outboxDao().insert(op("other", owner = "user-2", createdAt = 1_000))
        assertEquals(listOf("early", "late"), db.outboxDao().pendingFor("user-1").map { it.operationId })
    }

    @Test fun sessionModeInvariants() {
        expectIllegalArgument { LocalSessionEntity("a", SessionMode.ANONYMOUS, "user-1", "d", null, 0) }
        expectIllegalArgument { LocalSessionEntity("b", SessionMode.ACCOUNT, null, "d", 3, 0) }
        LocalSessionEntity("c", SessionMode.ANONYMOUS, null, "d", null, 0)
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    private companion object {
        const val NAME = "tx-test.db"
    }
}
