package dev.comon.fsp.core.data

import dev.comon.fsp.core.database.entity.OutboxKind
import dev.comon.fsp.core.database.entity.OutboxState
import dev.comon.fsp.core.security.CipherException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OutboxOperationsTest {
    private val operations = OutboxOperations(FakeCipher())
    private val payload = """{"idx":"a1","status":"ON"}"""

    private fun create() = operations.create("user-1", OutboxKind.ATTENDANCE_EVENT, "a1", payload, now = 5_000, operationId = "op-1")

    @Test fun storesEncryptedPayloadAsPendingRow() {
        val row = create()
        assertFalse(row.payloadJson.contains("ON"))
        assertEquals(payload, operations.payload(row))
        assertEquals(OutboxState.PENDING, row.state)
        assertEquals(0, row.attemptCount)
        assertEquals(5_000, row.nextAttemptAt)
    }

    @Test(expected = CipherException.Tampered::class)
    fun changingOwnerMakesPayloadUnreadable() {
        operations.payload(create().copy(ownerUserId = "user-2"))
    }

    @Test(expected = CipherException.Tampered::class)
    fun movingPayloadToAnotherOperationIsDetected() {
        operations.payload(create().copy(operationId = "op-2"))
    }

    @Test fun eachOperationGetsItsOwnId() {
        val a = operations.create("user-1", OutboxKind.RESPONSE_SUBMIT, "r1", "{}", 0)
        val b = operations.create("user-1", OutboxKind.RESPONSE_SUBMIT, "r1", "{}", 0)
        assertNotEquals(a.operationId, b.operationId)
    }
}
