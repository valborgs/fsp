package dev.comon.fsp.domain

import kotlinx.coroutines.flow.Flow

/** Survey definitions stored on this device. Only fully downloaded and validated definitions appear here. */
interface SurveyCacheRepository {
    fun observeCachedSurveys(): Flow<List<CachedSurvey>>
}
