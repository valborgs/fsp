package dev.comon.fsp.feature.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.comon.fsp.domain.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StartupEffect {
    data object NavigateToLogin : StartupEffect
    data object NavigateToAccountHome : StartupEffect
}

/**
 * S01: restores a stored account session from the device only (no network wait). A session whose
 * credentials are gone, or any local read failure, leads to the login screen instead of a crash.
 */
@HiltViewModel
class StartupViewModel @Inject constructor(sessionRepository: SessionRepository) : ViewModel() {
    private val _effects = Channel<StartupEffect>(Channel.BUFFERED)
    val effects: Flow<StartupEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val account = try {
                sessionRepository.restore()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _effects.send(if (account != null) StartupEffect.NavigateToAccountHome else StartupEffect.NavigateToLogin)
        }
    }
}

@Composable
fun StartupEntry(
    onSignedOut: () -> Unit,
    onSignedIn: () -> Unit,
    viewModel: StartupViewModel = hiltViewModel(),
) {
    val signedOut by rememberUpdatedState(onSignedOut)
    val signedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                StartupEffect.NavigateToLogin -> signedOut()
                StartupEffect.NavigateToAccountHome -> signedIn()
            }
        }
    }
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.semantics { contentDescription = "시작 준비 중" })
        }
    }
}
