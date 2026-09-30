package dev.comon.fsp.core.data

import dev.comon.fsp.domain.CachedSurvey
import dev.comon.fsp.domain.SurveyCacheRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Bound until the Room survey cache exists (1B-2). Download is not implemented yet, so the device
 * genuinely holds no survey definitions; no sample survey is bundled.
 */
class EmptySurveyCacheRepository @Inject constructor() : SurveyCacheRepository {
    override fun observeCachedSurveys(): Flow<List<CachedSurvey>> = flowOf(emptyList())
}
