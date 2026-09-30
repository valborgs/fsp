package dev.comon.fsp.core.data

import dev.comon.fsp.core.database.LocalWriteTransaction
import dev.comon.fsp.core.database.dao.LocalSessionDao
import dev.comon.fsp.core.database.entity.LocalSessionEntity
import dev.comon.fsp.core.database.entity.SessionMode
import dev.comon.fsp.domain.AccountSession
import dev.comon.fsp.domain.Role
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Open/close of the device's current work session (local_session). */
interface AccountSessionStore {
    suspend fun current(): LocalSessionEntity?
    fun observeCurrent(): Flow<LocalSessionEntity?>

    /** Ends any open session and opens [session] in one transaction. */
    suspend fun start(session: LocalSessionEntity)

    suspend fun endOpen(now: Long)
}

class RoomAccountSessionStore @Inject constructor(
    private val dao: LocalSessionDao,
    private val transaction: LocalWriteTransaction,
) : AccountSessionStore {
    override suspend fun current(): LocalSessionEntity? = dao.current()

    override fun observeCurrent(): Flow<LocalSessionEntity?> = dao.observeCurrent()

    override suspend fun start(session: LocalSessionEntity) = transaction {
        dao.endOpenSessions(session.createdAt)
        dao.insert(session)
    }

    override suspend fun endOpen(now: Long) {
        dao.endOpenSessions(now)
    }
}

/** Null for anonymous sessions or rows that do not describe a valid account. */
internal fun LocalSessionEntity.toAccountSession(): AccountSession? {
    if (mode != SessionMode.ACCOUNT) return null
    val user = userId ?: return null
    val role = roleGrade?.let(Role::fromGrade) ?: return null
    val id = loginId ?: user
    return AccountSession(userId = user, loginId = id, name = displayName ?: id, role = role)
}
