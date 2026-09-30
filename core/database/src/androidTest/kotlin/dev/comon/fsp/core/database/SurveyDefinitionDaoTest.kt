package dev.comon.fsp.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.comon.fsp.core.database.entity.SurveyAvailability
import dev.comon.fsp.core.database.entity.SurveyDefinitionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SurveyDefinitionDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: FspDatabase

    @Before fun open() {
        context.deleteDatabase(NAME)
        db = FspDatabase.create(context, NAME)
    }

    @After fun close() {
        db.close()
        context.deleteDatabase(NAME)
    }

    private fun def(version: Int, title: String, availability: SurveyAvailability = SurveyAvailability.AVAILABLE) =
        SurveyDefinitionEntity(
            surveyId = "survey01", version = version, schemaVersion = 1, title = title, contentJson = "{}",
            checksum = "sha256:$version", downloadedAt = 1_000, lastVerifiedAt = 1_000, availability = availability,
        )

    @Test fun freshDatabaseHasNoDefinitions() = runTest {
        assertEquals(emptyList<SurveyDefinitionEntity>(), db.surveyDefinitionDao().observeAll().first())
    }

    @Test fun versionsCoexistAndSameKeyIsReplaced() = runTest {
        val dao = db.surveyDefinitionDao()
        dao.upsert(def(1, "v1"))
        dao.upsert(def(2, "v2"))
        dao.upsert(def(1, "v1 disabled", SurveyAvailability.DISABLED))
        assertEquals(
            listOf(def(1, "v1 disabled", SurveyAvailability.DISABLED), def(2, "v2")),
            dao.observeAll().first(),
        )
    }

    private companion object {
        const val NAME = "survey-dao-test.db"
    }
}
