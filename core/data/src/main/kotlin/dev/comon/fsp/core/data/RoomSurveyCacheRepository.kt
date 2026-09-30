package dev.comon.fsp.core.data

import dev.comon.fsp.core.database.dao.SurveyDefinitionDao
import dev.comon.fsp.core.database.entity.SurveyAvailability
import dev.comon.fsp.domain.CachedSurvey
import dev.comon.fsp.domain.SurveyCacheRepository
import dev.comon.fsp.domain.SurveyKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Survey definitions stored in Room. Nothing writes this table until download is implemented
 * (stage 3), so the device reports no survey; no sample definition is seeded.
 * The 3-month cache expiry based on lastVerifiedAt is applied with download in stage 3.
 */
class RoomSurveyCacheRepository @Inject constructor(
    private val dao: SurveyDefinitionDao,
) : SurveyCacheRepository {
    override fun observeCachedSurveys(): Flow<List<CachedSurvey>> = dao.observeAll().map { rows ->
        rows.map {
            CachedSurvey(SurveyKey(it.surveyId, it.version), available = it.availability == SurveyAvailability.AVAILABLE)
        }
    }
}
