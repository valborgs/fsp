package dev.comon.fsp.core.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.comon.fsp.core.database.dao.LocalSessionDao
import dev.comon.fsp.core.database.dao.OutboxDao
import dev.comon.fsp.core.database.dao.SurveyDefinitionDao
import dev.comon.fsp.core.database.entity.LocalSessionEntity
import dev.comon.fsp.core.database.entity.OutboxEntity
import dev.comon.fsp.core.database.entity.SurveyDefinitionEntity
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@Database(
    version = FspDatabase.VERSION,
    entities = [LocalSessionEntity::class, SurveyDefinitionEntity::class, OutboxEntity::class],
    exportSchema = true,
)
abstract class FspDatabase : RoomDatabase() {
    abstract fun localSessionDao(): LocalSessionDao
    abstract fun surveyDefinitionDao(): SurveyDefinitionDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        const val VERSION = 1
        const val NAME = "fsp.db"

        /**
         * Single construction path for production and tests. Destructive fallback is never enabled:
         * operational records must not be wiped by a missing or failed migration. Add migrations here.
         */
        fun create(
            context: Context,
            name: String = NAME,
            driver: SQLiteDriver = BundledSQLiteDriver(),
        ): FspDatabase = Room.databaseBuilder(context, FspDatabase::class.java, name)
            .setDriver(driver)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}

/**
 * Runs a local change and the Outbox operations it creates in one IMMEDIATE write transaction.
 * DAO calls inside [block] share the transaction's connection; any exception rolls back everything
 * and is rethrown, so callers never report a save that did not commit.
 */
class LocalWriteTransaction @Inject constructor(private val database: FspDatabase) {
    suspend operator fun <R> invoke(block: suspend () -> R): R = database.withWriteTransaction { block() }
}
