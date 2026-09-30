package dev.comon.fsp.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import dev.comon.fsp.core.database.entity.LocalSessionEntity
import dev.comon.fsp.core.database.entity.OutboxEntity
import dev.comon.fsp.core.database.entity.SurveyDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyDefinitionDao {
    @Query("SELECT * FROM survey_definition ORDER BY surveyId, version")
    fun observeAll(): Flow<List<SurveyDefinitionEntity>>

    @Query("SELECT * FROM survey_definition WHERE surveyId = :surveyId AND version = :version")
    suspend fun get(surveyId: String, version: Int): SurveyDefinitionEntity?

    @Upsert
    suspend fun upsert(definition: SurveyDefinitionEntity)
}

@Dao
interface LocalSessionDao {
    @Insert
    suspend fun insert(session: LocalSessionEntity)

    @Query("SELECT * FROM local_session WHERE sessionId = :sessionId")
    suspend fun get(sessionId: String): LocalSessionEntity?

    @Query("SELECT * FROM local_session WHERE endedAt IS NULL ORDER BY createdAt DESC, sessionId DESC LIMIT 1")
    suspend fun current(): LocalSessionEntity?

    @Query("SELECT * FROM local_session WHERE endedAt IS NULL ORDER BY createdAt DESC, sessionId DESC LIMIT 1")
    fun observeCurrent(): Flow<LocalSessionEntity?>

    /** Closes every open session; used on sign-in (before the new row), sign-out and expiry. */
    @Query("UPDATE local_session SET endedAt = :endedAt WHERE endedAt IS NULL")
    suspend fun endOpenSessions(endedAt: Long): Int
}

@Dao
interface OutboxDao {
    /** Plain insert (ABORT on conflict): a duplicate operationId is a bug, never a silent overwrite. */
    @Insert
    suspend fun insert(operation: OutboxEntity)

    @Query(
        "SELECT * FROM outbox WHERE ownerUserId = :ownerUserId AND state = 'PENDING' " +
            "ORDER BY createdAt, operationId",
    )
    suspend fun pendingFor(ownerUserId: String): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int
}
