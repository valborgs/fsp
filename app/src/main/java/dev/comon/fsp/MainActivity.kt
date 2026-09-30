package dev.comon.fsp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.comon.fsp.ui.theme.FspTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FspTheme { EntryScreen() } }
    }
}

/** Foundation screen: no simulated authentication or survey data. */
@Composable
private fun EntryScreen() {
    var offline by rememberSaveable { mutableStateOf(false) }
    var userId by rememberSaveable { mutableStateOf("") }
    // Never persist passwords in saved instance state.
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler(offline) { offline = false }
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("현장 설문", style = MaterialTheme.typography.headlineLarge)
                if (offline) {
                    Text("오프라인 모드 · 조사원", style = MaterialTheme.typography.labelLarge)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("설문 데이터가 없습니다", style = MaterialTheme.typography.titleLarge)
                            Text("온라인에서 로그인한 후 할당된 설문을 먼저 다운로드해 주세요.")
                        }
                    }
                    Button(onClick = { offline = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("로그인 화면으로 돌아가기")
                    }
                } else {
                    Text("로그인하여 할당된 설문을 받거나, 저장된 설문으로 오프라인 조사를 진행하세요.")
                    OutlinedTextField(userId, { userId = it }, label = { Text("아이디") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("비밀번호") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        message = "서버 연결이 아직 설정되지 않았습니다. 로그인은 서버 연동 단계에서 제공됩니다."
                        password = ""
                    }, enabled = userId.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text("로그인")
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(onClick = {
                        password = ""
                        message = null
                        offline = true
                    }, modifier = Modifier.fillMaxWidth()) { Text("오프라인 모드") }
                    Text("계정 없이 진입할 수 있습니다. 최초 사용 전에는 로그인과 설문 다운로드가 필요합니다.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
