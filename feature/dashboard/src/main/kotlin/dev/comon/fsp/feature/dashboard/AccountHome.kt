package dev.comon.fsp.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.comon.fsp.domain.AccountSession
import dev.comon.fsp.domain.Role
import dev.comon.fsp.domain.SessionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AccountHomeUiState {
    data object Loading : AccountHomeUiState
    data class SignedIn(val account: AccountSession, val signingOut: Boolean = false) : AccountHomeUiState
}

sealed interface AccountHomeIntent {
    data object Logout : AccountHomeIntent
}

sealed interface AccountHomeEffect {
    data object NavigateToLogin : AccountHomeEffect
}

/**
 * Home of a signed-in account (skeleton for S03/S04). Leaves as soon as the session is gone,
 * whether by logout here or because a refresh was rejected (expired, revoked device, disabled account).
 */
@HiltViewModel
class AccountHomeViewModel @Inject constructor(private val sessions: SessionRepository) : ViewModel() {
    private val _state = MutableStateFlow<AccountHomeUiState>(AccountHomeUiState.Loading)
    val state: StateFlow<AccountHomeUiState> = _state.asStateFlow()

    private val _effects = Channel<AccountHomeEffect>(Channel.BUFFERED)
    val effects: Flow<AccountHomeEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            sessions.observeCurrentAccount().collect { account ->
                if (account == null) {
                    _effects.send(AccountHomeEffect.NavigateToLogin)
                } else {
                    _state.update { AccountHomeUiState.SignedIn(account, (it as? AccountHomeUiState.SignedIn)?.signingOut ?: false) }
                }
            }
        }
    }

    fun onIntent(intent: AccountHomeIntent) {
        when (intent) {
            AccountHomeIntent.Logout -> logout()
        }
    }

    private fun logout() {
        val current = _state.value as? AccountHomeUiState.SignedIn ?: return
        if (current.signingOut) return
        _state.value = current.copy(signingOut = true)
        // Navigation follows from the session store emitting null once the session is ended.
        viewModelScope.launch { sessions.logout() }
    }
}

@Composable
fun AccountHomeEntry(onSignedOut: () -> Unit, viewModel: AccountHomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedOut by rememberUpdatedState(onSignedOut)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AccountHomeEffect.NavigateToLogin -> signedOut()
            }
        }
    }
    AccountHomeScreen(state, viewModel::onIntent)
}

@Composable
fun AccountHomeScreen(state: AccountHomeUiState, onIntent: (AccountHomeIntent) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (state) {
                AccountHomeUiState.Loading -> CircularProgressIndicator()
                is AccountHomeUiState.SignedIn -> Column(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("현장 설문", style = MaterialTheme.typography.headlineLarge)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${state.account.name}님", style = MaterialTheme.typography.titleLarge)
                            Text("${state.account.loginId} · ${state.account.role.label()}")
                        }
                    }
                    Text(state.account.role.pendingFeatures(), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { onIntent(AccountHomeIntent.Logout) },
                        enabled = !state.signingOut,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.signingOut) "로그아웃 중…" else "로그아웃") }
                }
            }
        }
    }
}

internal fun Role.label(): String = when (this) {
    Role.ADMIN -> "어드민"
    Role.SUPERVISOR -> "슈퍼바이저"
    Role.INTERVIEWER -> "조사원"
}

private fun Role.pendingFeatures(): String = when (this) {
    Role.INTERVIEWER -> "할당 확인, 설문 다운로드, 출퇴근과 설문 시작은 다음 단계에서 제공됩니다."
    Role.SUPERVISOR -> "조사원 할당, 현황, 검수 기능은 다음 단계에서 제공됩니다."
    Role.ADMIN -> "계정·CSV 관리, 조사원 할당, 현황, 검수 기능은 다음 단계에서 제공됩니다."
}

@Preview(showBackground = true)
@Composable
private fun AccountHomePreview() {
    AccountHomeScreen(
        AccountHomeUiState.SignedIn(AccountSession("u-1", "worker001", "홍길동", Role.INTERVIEWER)),
        onIntent = {},
    )
}
