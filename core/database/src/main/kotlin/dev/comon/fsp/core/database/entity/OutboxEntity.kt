package dev.comon.fsp.core.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

enum class OutboxKind { ATTENDANCE_EVENT, RESPONSE_DRAFT, RESPONSE_SUBMIT, OFFLINE_RESPONSE_IMPORT }

enum class OutboxState { PENDING, SENDING, SUCCEEDED, FAILED, BLOCKED }

/**
 * Durable send queue. [ownerUserId] is NOT NULL: anonymous records are never queued automatically.
 * An anonymous response enters the queue only when a signed-in interviewer explicitly selects it,
 * and then the owner is that interviewer. Resends reuse [operationId] and [payloadJson] unchanged.
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["ownerUserId", "state", "nextAttemptAt"]),
        Index(value = ["kind", "entityId"]),
    ],
)
data class OutboxEntity(
    @PrimaryKey val operationId: String,
    val ownerUserId: String,
    val entityId: String,
    val kind: OutboxKind,
    /** Plain text until the Keystore boundary lands in 1B-4. */
    val payloadJson: String,
    val state: OutboxState = OutboxState.PENDING,
    /** Persisted before each HTTP call so the call budget survives process death. */
    val attemptCount: Int = 0,
    val retryCycle: Int = 0,
    val nextAttemptAt: Long,
    /** Sanitized error code only; never request/response bodies. */
    val lastError: String? = null,
    val createdAt: Long,
)
