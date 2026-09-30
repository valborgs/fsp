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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OfflineDashboardEntry(
    onNavigateToLogin: () -> Unit,
    viewModel: OfflineDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigateToLogin by rememberUpdatedState(onNavigateToLogin)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OfflineDashboardEffect.NavigateToLogin -> navigateToLogin()
            }
        }
    }
    OfflineDashboardScreen(state, viewModel::onIntent)
}

@Composable
fun OfflineDashboardScreen(
    state: OfflineDashboardUiState,
    onIntent: (OfflineDashboardIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("현장 설문", style = MaterialTheme.typography.headlineLarge)
                Text("오프라인 모드 · 조사원", style = MaterialTheme.typography.labelLarge)
                when (state) {
                    OfflineDashboardUiState.Loading -> CircularProgressIndicator()
                    OfflineDashboardUiState.NoSurvey -> Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("설문 데이터가 없습니다", style = MaterialTheme.typography.titleLarge)
                            Text("온라인에서 로그인한 후 할당된 설문을 먼저 다운로드해 주세요.")
                        }
                    }
                    is OfflineDashboardUiState.Ready -> Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("기기에 저장된 설문", style = MaterialTheme.typography.titleLarge)
                            state.surveys.forEach { Text("${it.id} · 버전 ${it.version}") }
                        }
                    }
                }
                Button(
                    onClick = { onIntent(OfflineDashboardIntent.BackToLogin) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("로그인 화면으로 돌아가기") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OfflineDashboardNoSurveyPreview() {
    OfflineDashboardScreen(OfflineDashboardUiState.NoSurvey, onIntent = {})
}
