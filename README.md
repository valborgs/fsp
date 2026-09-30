# 현장 설문 Android 앱

Compose 기반 앱. 기획서 v1.3에 따라 단계별 개발 중입니다.
전체 진행표와 다음 작업은 [개발 계획](docs/DEVELOPMENT_PLAN.md)을 참고하세요.

현재는 로그인 입력 화면, 오프라인 설문 없음 화면, 독립적인 핵심 업무 정책 모듈을 제공합니다.
실제 인증·저장·설문 수집은 아직 구현하지 않았습니다.

## 빌드와 테스트

Android Studio의 JDK와 SDK를 사용하며 최소 지원은 Android 8.0(API 26)입니다.
SDK 위치는 각 개발 환경의 `local.properties`에 설정합니다.

```powershell
.\gradlew.bat :core:domain:test :app:assembleDebug :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

정책 테스트: `core/domain/build/reports/tests/test/index.html`
