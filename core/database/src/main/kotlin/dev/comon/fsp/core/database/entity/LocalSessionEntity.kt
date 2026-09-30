package dev.comon.fsp.core.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

enum class SessionMode { ACCOUNT, ANONYMOUS }

/**
 * Separates the authenticated account area from the anonymous (no account) work area on this device.
 * An ACCOUNT session always has a user and role grade; an ANONYMOUS session never has either.
 */
@Entity(tableName = "local_session")
data class LocalSessionEntity(
    @PrimaryKey val sessionId: String,
    val mode: SessionMode,
    val userId: String?,
    val deviceId: String,
    val roleGrade: Int?,
    val createdAt: Long,
) {
    init {
        when (mode) {
            SessionMode.ACCOUNT -> require(userId != null && roleGrade != null) { "Account session needs user and role" }
            SessionMode.ANONYMOUS -> require(userId == null && roleGrade == null) { "Anonymous session has no user" }
        }
    }
}
