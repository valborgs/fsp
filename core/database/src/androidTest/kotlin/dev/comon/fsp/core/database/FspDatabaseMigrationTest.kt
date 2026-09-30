package dev.comon.fsp.core.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration baseline. Every future version adds a test here that creates the previous exported
 * schema, inserts data, migrates, and validates. Destructive fallback is never allowed.
 */
@RunWith(AndroidJUnit4::class)
class FspDatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @get:Rule val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = context.getDatabasePath(NAME),
        driver = BundledSQLiteDriver(),
        databaseClass = FspDatabase::class,
    )

    @Before fun clean() {
        context.deleteDatabase(NAME)
    }

    private val insertDefinition =
        "INSERT INTO survey_definition (surveyId, version, schemaVersion, title, contentJson, checksum, " +
            "downloadedAt, lastVerifiedAt, availability) VALUES ('survey01', 1, 1, 't', '{}', 'c', 1, 1, 'AVAILABLE')"

    @Test fun exportedV1SchemaMatchesEntities() = runTest {
        helper.createDatabase(1).close()
        helper.runMigrationsAndValidate(1, emptyList()).close()
    }

    @Test fun existingV1DataIsKeptWhenAppOpensDatabase() = runTest {
        helper.createDatabase(1).apply { execSQL(insertDefinition) }.close()
        val db = FspDatabase.create(context, NAME)
        try {
            assertNotNull(db.surveyDefinitionDao().get("survey01", 1))
        } finally {
            db.close()
        }
    }

    @Test fun unknownNewerVersionFailsWithoutWipingData() = runTest {
        helper.createDatabase(1).apply {
            execSQL(insertDefinition)
            execSQL("PRAGMA user_version = ${FspDatabase.VERSION + 1}")
        }.close()

        val db = FspDatabase.create(context, NAME)
        try {
            db.surveyDefinitionDao().get("survey01", 1)
            fail("opening a newer schema without a migration must fail")
        } catch (_: IllegalStateException) {
        } finally {
            db.close()
        }

        val raw = BundledSQLiteDriver().open(context.getDatabasePath(NAME).path)
        try {
            val count = raw.prepare("SELECT COUNT(*) FROM survey_definition").use { it.step(); it.getLong(0) }
            assertEquals(1L, count)
        } finally {
            raw.close()
        }
    }

    private companion object {
        const val NAME = "migration-test.db"
    }
}
