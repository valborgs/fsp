package dev.comon.fsp.core.data

import dev.comon.fsp.core.database.dao.SurveyDefinitionDao
import dev.comon.fsp.core.database.entity.SurveyAvailability
import dev.comon.fsp.core.database.entity.SurveyDefinitionEntity
import dev.comon.fsp.domain.CachedSurvey
import dev.comon.fsp.domain.SurveyKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomSurveyCacheRepositoryTest {
    private val rows = MutableStateFlow<List<SurveyDefinitionEntity>>(emptyList())
    private val dao = object : SurveyDefinitionDao {
        override fun observeAll(): Flow<List<SurveyDefinitionEntity>> = rows
        override suspend fun get(surveyId: String, version: Int) = error("unused")
        override suspend fun upsert(definition: SurveyDefinitionEntity) = error("unused")
    }

    private fun row(id: String, version: Int, availability: SurveyAvailability) = SurveyDefinitionEntity(
        surveyId = id, version = version, schemaVersion = 1, title = "t", contentJson = "{}",
        checksum = "c", downloadedAt = 0, lastVerifiedAt = 0, availability = availability,
    )

    @Test fun emptyTableMeansNoSurvey() = runTest {
        assertEquals(emptyList<CachedSurvey>(), RoomSurveyCacheRepository(dao).observeCachedSurveys().first())
    }

    @Test fun mapsKeyAndAvailability() = runTest {
        rows.value = listOf(row("A", 1, SurveyAvailability.AVAILABLE), row("A", 2, SurveyAvailability.DISABLED))
        assertEquals(
            listOf(CachedSurvey(SurveyKey("A", 1), true), CachedSurvey(SurveyKey("A", 2), false)),
            RoomSurveyCacheRepository(dao).observeCachedSurveys().first(),
        )
    }
}
