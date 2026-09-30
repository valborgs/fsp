package dev.comon.fsp.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.comon.fsp.domain.LoginFailure

/** Navigation entry: obtains the entry-scoped ViewModel and forwards effects as navigation callbacks. */
@Composable
fun LoginEntry(onOpenOfflineMode: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openOffline by rememberUpdatedState(onOpenOfflineMode)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LoginEffect.OpenOfflineMode -> openOffline()
            }
        }
    }
    LoginScreen(state, viewModel::onIntent)
}

@Composable
fun LoginScreen(state: LoginUiState, onIntent: (LoginIntent) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("현장 설문", style = MaterialTheme.typography.headlineLarge)
                Text("로그인하여 할당된 설문을 받거나, 저장된 설문으로 오프라인 조사를 진행하세요.")
                OutlinedTextField(
                    state.loginId, { onIntent(LoginIntent.LoginIdChanged(it)) },
                    label = { Text("아이디") }, singleLine = true, enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    state.password, { onIntent(LoginIntent.PasswordChanged(it)) },
                    label = { Text("비밀번호") }, singleLine = true, enabled = !state.submitting,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onIntent(LoginIntent.Submit) },
                    enabled = state.canSubmit, modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.submitting) "로그인 중…" else "로그인") }
                state.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
                OutlinedButton(
                    onClick = { onIntent(LoginIntent.EnterOfflineMode) },
                    enabled = !state.submitting, modifier = Modifier.fillMaxWidth(),
                ) { Text("오프라인 모드") }
                Text(
                    "계정 없이 진입할 수 있습니다. 최초 사용 전에는 로그인과 설문 다운로드가 필요합니다.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

internal fun LoginFailure.message(): String = when (this) {
    LoginFailure.SERVER_NOT_CONFIGURED -> "서버 연결이 아직 설정되지 않았습니다. 로그인은 서버 연동 단계에서 제공됩니다."
    LoginFailure.INVALID_CREDENTIALS -> "아이디 또는 비밀번호를 확인해 주세요."
    LoginFailure.NETWORK -> "서버에 연결하지 못했습니다. 네트워크 상태를 확인한 후 다시 시도해 주세요."
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    LoginScreen(LoginUiState(loginId = "worker001"), onIntent = {})
}
