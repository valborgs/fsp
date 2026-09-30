package dev.comon.fsp.feature.dashboard

import dev.comon.fsp.domain.CachedSurvey
import dev.comon.fsp.domain.SurveyCacheRepository
import dev.comon.fsp.domain.SurveyKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineDashboardViewModelTest {
    private val cache = MutableStateFlow<List<CachedSurvey>>(emptyList())
    private val repository = object : SurveyCacheRepository {
        override fun observeCachedSurveys(): Flow<List<CachedSurvey>> = cache
    }
    private val a = SurveyKey("A", 1)
    private val b = SurveyKey("B", 2)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun startsLoadingUntilObserved() {
        assertEquals(OfflineDashboardUiState.Loading, OfflineDashboardViewModel(repository).state.value)
    }

    @Test fun noCacheOrOnlyUnavailableCacheShowsNoSurvey() = runTest {
        val vm = OfflineDashboardViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(OfflineDashboardUiState.NoSurvey, vm.state.value)
        cache.value = listOf(CachedSurvey(a, available = false))
        assertEquals(OfflineDashboardUiState.NoSurvey, vm.state.value)
    }

    @Test fun anyAvailableDefinitionOnDeviceIsListedOnce() = runTest {
        val vm = OfflineDashboardViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        cache.value = listOf(CachedSurvey(a, true), CachedSurvey(b, false), CachedSurvey(a, true))
        assertEquals(OfflineDashboardUiState.Ready(listOf(a)), vm.state.value)
        // Cache removal falls back to the no-survey screen rather than keeping a stale list.
        cache.value = emptyList()
        assertEquals(OfflineDashboardUiState.NoSurvey, vm.state.value)
    }

    @Test fun backToLoginIsOneShotEffect() = runTest {
        val vm = OfflineDashboardViewModel(repository)
        vm.onIntent(OfflineDashboardIntent.BackToLogin)
        assertEquals(OfflineDashboardEffect.NavigateToLogin, vm.effects.first())
    }
}
