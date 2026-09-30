package dev.comon.fsp.feature.dashboard

import dev.comon.fsp.domain.SurveyKey

sealed interface OfflineDashboardUiState {
    data object Loading : OfflineDashboardUiState
    /** No usable definition on the device: shows "설문 데이터가 없습니다". */
    data object NoSurvey : OfflineDashboardUiState
    data class Ready(val surveys: List<SurveyKey>) : OfflineDashboardUiState
}

sealed interface OfflineDashboardIntent {
    data object BackToLogin : OfflineDashboardIntent
}

sealed interface OfflineDashboardEffect {
    data object NavigateToLogin : OfflineDashboardEffect
}
