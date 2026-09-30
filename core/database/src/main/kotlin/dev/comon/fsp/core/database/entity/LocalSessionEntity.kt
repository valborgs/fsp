package dev.comon.fsp.core.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

enum class SessionMode { ACCOUNT, ANONYMOUS }

/**
 * Separates the authenticated account area from the anonymous (no account) work area on this device.
 * An ACCOUNT session always has a user and role grade; an ANONYMOUS session never has either.
 * The open session is the latest row with [endedAt] null; ending keeps the row for history.
 */
@Entity(tableName = "local_session")
data class LocalSessionEntity(
    @PrimaryKey val sessionId: String,
    val mode: SessionMode,
    val userId: String?,
    val deviceId: String,
    val roleGrade: Int?,
    val createdAt: Long,
    /** v2: server login ID (lower-case) for display. */
    val loginId: String? = null,
    /** v2: server account name at sign-in, for display offline. */
    val displayName: String? = null,
    /** v2: TokenPair.sessionId (server session family). */
    val serverSessionId: String? = null,
    /** v2: next attendance sequence for this user and device from login/me (interviewers only). */
    val deviceNextSequence: Long? = null,
    /** v2: sign-out or expiry time; null while the session is open. */
    val endedAt: Long? = null,
) {
    init {
        when (mode) {
            SessionMode.ACCOUNT -> require(userId != null && roleGrade != null) { "Account session needs user and role" }
            SessionMode.ANONYMOUS -> require(userId == null && roleGrade == null && loginId == null) {
                "Anonymous session has no user"
            }
        }
    }
}
