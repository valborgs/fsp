package dev.comon.fsp.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.execSQL

/** v1 -> v2: account display and session-end columns on local_session (all nullable, no data rewrite). */
val MIGRATION_1_2 = Migration(1, 2) { connection ->
    listOf("loginId TEXT", "displayName TEXT", "serverSessionId TEXT", "deviceNextSequence INTEGER", "endedAt INTEGER")
        .forEach { connection.execSQL("ALTER TABLE local_session ADD COLUMN $it") }
}

/** Every migration, in order. Never replace this with destructive fallback. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
