# 현장 설문 Android 앱

Compose 기반 앱. 기획서 v1.3에 따라 단계별 개발 중입니다.
전체 진행표와 다음 작업은 [개발 계획](docs/DEVELOPMENT_PLAN.md)을 참고하세요.

현재는 멀티모듈(app, core:domain/data/database/network/security/navigation, feature:auth/dashboard) 구조에서
Hilt·Navigation 3·MVI·Room 3·Retrofit 3로 로그인 입력 화면과 오프라인 설문 없음 화면을 제공합니다.
실제 인증·다운로드·설문 수집은 아직 구현하지 않았습니다. 로그인은 서버 미설정 실패를 표시합니다.

## 빌드와 테스트

Android Studio의 JDK와 SDK를 사용하며 최소 지원은 Android 8.0(API 26)입니다.
SDK 위치는 각 개발 환경의 `local.properties`에 설정합니다.

```powershell
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :core:network:testDebugUnitTest :core:security:testDebugUnitTest :feature:auth:testDebugUnitTest :feature:dashboard:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :core:data:lintDebug :core:database:lintDebug :core:network:lintDebug :core:security:lintDebug :core:navigation:lintDebug :feature:auth:lintDebug :feature:dashboard:lintDebug
```

기기 테스트(에뮬레이터 또는 기기 연결, 화면 켜짐 필요): `.\gradlew.bat :core:database:connectedDebugAndroidTest :app:connectedDebugAndroidTest`

APK: `app/build/outputs/apk/debug/app-debug.apk`

정책 테스트: `core/domain/build/reports/tests/test/index.html`

서버 주소 설정(미설정 시 로그인은 "서버 미설정"으로 실패하며 네트워크 호출 없음, HTTPS만 허용):
`.\gradlew.bat :app:assembleDebug -Pfsp.apiBaseUrl=https://<host>/api/v1`
