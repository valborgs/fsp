package dev.comon.fsp.core.data

import dev.comon.fsp.core.database.entity.OutboxEntity
import dev.comon.fsp.core.database.entity.OutboxKind
import dev.comon.fsp.core.security.DataCipher
import java.util.UUID
import javax.inject.Inject

/**
 * Builds Outbox rows with an encrypted payload and reads it back. The associated data binds the
 * ciphertext to operation, owner, kind and entity, so a row whose owner or target was altered cannot
 * be decrypted and resent under another account.
 *
 * Create the row before opening [dev.comon.fsp.core.database.LocalWriteTransaction] (Keystore work
 * stays outside the DB lock), then insert it inside the transaction together with the local change.
 */
class OutboxOperations @Inject constructor(private val cipher: DataCipher) {
    fun create(
        ownerUserId: String,
        kind: OutboxKind,
        entityId: String,
        payloadJson: String,
        now: Long,
        operationId: String = UUID.randomUUID().toString(),
    ): OutboxEntity = OutboxEntity(
        operationId = operationId,
        ownerUserId = ownerUserId,
        entityId = entityId,
        kind = kind,
        payloadJson = cipher.encrypt(payloadJson, aad(operationId, ownerUserId, kind, entityId)),
        nextAttemptAt = now,
        createdAt = now,
    )

    /** @throws dev.comon.fsp.core.security.CipherException when the row was altered or the key is gone. */
    fun payload(operation: OutboxEntity): String = with(operation) {
        cipher.decrypt(payloadJson, aad(operationId, ownerUserId, kind, entityId))
    }

    private fun aad(operationId: String, ownerUserId: String, kind: OutboxKind, entityId: String) =
        "outbox:v1:$operationId:$ownerUserId:$kind:$entityId"
}
