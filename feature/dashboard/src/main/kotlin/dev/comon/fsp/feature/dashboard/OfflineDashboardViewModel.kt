package dev.comon.fsp.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.comon.fsp.domain.SurveyCacheRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class OfflineDashboardViewModel @Inject constructor(
    surveyCacheRepository: SurveyCacheRepository,
) : ViewModel() {
    /** Anonymous mode may use any available cached definition on the device, regardless of who downloaded it. */
    val state: StateFlow<OfflineDashboardUiState> = surveyCacheRepository.observeCachedSurveys()
        .map { cached ->
            val usable = cached.filter { it.available }.map { it.key }.distinct()
            if (usable.isEmpty()) OfflineDashboardUiState.NoSurvey else OfflineDashboardUiState.Ready(usable)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OfflineDashboardUiState.Loading)

    private val _effects = Channel<OfflineDashboardEffect>(Channel.BUFFERED)
    val effects: Flow<OfflineDashboardEffect> = _effects.receiveAsFlow()

    fun onIntent(intent: OfflineDashboardIntent) {
        when (intent) {
            OfflineDashboardIntent.BackToLogin -> _effects.trySend(OfflineDashboardEffect.NavigateToLogin)
        }
    }
}
