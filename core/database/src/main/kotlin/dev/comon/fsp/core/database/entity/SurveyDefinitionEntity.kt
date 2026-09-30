package dev.comon.fsp.core.database.entity

import androidx.room3.Entity

enum class SurveyAvailability { AVAILABLE, DISABLED }

/**
 * Downloaded survey definition, shared across accounts and the anonymous area on this device.
 * Rows are written only after download and schema validation succeed; a failed download must not
 * overwrite an existing valid version.
 */
@Entity(tableName = "survey_definition", primaryKeys = ["surveyId", "version"])
data class SurveyDefinitionEntity(
    val surveyId: String,
    val version: Int,
    val schemaVersion: Int,
    val title: String,
    val contentJson: String,
    val checksum: String,
    val downloadedAt: Long,
    /** Last successful download or online validity check; basis of the 3-month cache expiry. */
    val lastVerifiedAt: Long,
    val availability: SurveyAvailability,
)
