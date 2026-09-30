# 현장 설문 앱 개발 진행표

기준: fsp_api_spec.docx (현장설문앱 기획서·API 명세 v1.3) 및 참조 대화의 확정 요구사항.
최소 지원 버전은 2026-09-30 추가 요청에 따라 Android 8.0(API 26)으로 변경한다. 원본 기획서의 Android 6.0 조건보다 이 변경을 우선한다.
서버는 별도 구현 범위다. 단계별로 구현과 검증을 완료하고 진행 기록을 갱신한다.

## 개발 단계

| 단계 | 작업 | 완료 기준 | 상태 |
| --- | --- | --- | --- |
| 1A | 프로젝트 기반, 권한·시작 정책, 최초 진입 화면 | 핵심 정책 테스트, API 26 설정 빌드 | 완료 |
| 1B | MVI, Hilt, Navigation 3, Room 3, Retrofit 3 통합 | DI 생성, 탐색 복원, DB 트랜잭션·마이그레이션, HTTP 계약 테스트 | 진행 중 (1B-1 완료, 1B-2 다음) |
| 2 | 로그인·계정·CSV·할당 관리 | 역할별 접근, CSV 원자적 등록, 할당 충돌 처리 | 대기 |
| 3 | 다운로드·오프라인 선택·반복 출퇴근 | 유효 캐시만 사용, 재실행 복원, 로컬/Outbox 원자적 저장 | 대기 |
| 4 | 3종 문항·자동 저장·제출 | 단일 선택/주관식/7점 척도, 버전 고정, 종료 후 복원 | 대기 |
| 5 | 선택 전송·현황·검수 | 멱등성, 추가 3회 재시도, 반려 revision, If-Match 충돌 | 대기 |
| 6 | 다중 화면·보안·장애 복구·현장 파일럿 | API 26/최신 OS, 태블릿/폴드, 만료·프로세스 종료 검증 | 대기 |

## 현재 구현 범위

- `app`: Hilt Application, Navigation 3 루트 백스택(`FspNavigation`), 테마.
- `core:navigation`: 직렬화 가능한 route 키(`LoginRoute`, `OfflineDashboardRoute`).
- `core:data`: Repository 구현과 Hilt 바인딩. 현재는 서버·DB가 없음을 그대로 보고하는 구현만 있다.
- `feature:auth`: 로그인 화면 MVI(State/Intent/Effect)와 `LoginViewModel`.
- `feature:dashboard`: 계정 없는 모드 대시보드 MVI와 `OfflineDashboardViewModel`.
- `core:domain`: Android에 의존하지 않는 역할·세션·할당·설문 시작 정책, Repository 인터페이스.
- 계정 등급 1/2/3, 관리자 실제 수집 금지, 출근 조건, 인증 후 다운로드 정책.
- 로그인 계정은 본인 할당 캐시만 사용. 익명 모드는 유효 캐시를 선택.
- 온라인 신규 시작은 최종 할당 확인 필수. 실패 시 오래된 할당으로 시작 불가.
- 신규 응답 시작 시 설문·할당을 값으로 고정. 이후 할당 변경은 다음 시작에 적용.
- 익명 응답 가져오기는 조사원의 현재 할당 설문·버전 일치 여부 확인.

아직 실제 로그인, 다운로드, DB, 출퇴근, 설문 입력은 제공하지 않는다.
로그인은 `UnconfiguredAuthRepository`가 네트워크 호출 없이 `SERVER_NOT_CONFIGURED` 실패를 반환한다(가짜 성공 없음).
오프라인 진입은 `EmptySurveyCacheRepository`가 빈 목록을 반환하므로 항상 설문 없음 화면을 표시한다. 샘플 설문은 내장하지 않는다.
화면은 스크롤·키보드 여백·최대 본문 너비를 적용한 기본형이며 폴더블 힌지 대응은 6단계다.
정책 단위 테스트는 실제 DB에 저장된 응답의 프로세스 종료 복원을 검증하지 않는다.

## 1B 하위 단계

| 하위 단계 | 범위 | 완료 기준 | 상태 |
| --- | --- | --- | --- |
| 1B-1 | 멀티모듈 골격, Hilt/KSP, Navigation 3 백스택·엔트리 범위 ViewModel, MVI 전환 | Hilt 그래프 생성, MVI 단위 테스트, 백스택 복원 계측 테스트(API 26), 빌드·Lint | 완료 (2026-09-30) |
| 1B-2 | `core:database`: Room 3 SQLiteDriver, 스키마 내보내기, 캐시·세션·Outbox 트랜잭션 | 로컬 저장+Outbox 원자성·롤백 테스트, 마이그레이션 테스트, `EmptySurveyCacheRepository` 교체 | 다음 작업 |
| 1B-3 | `core:network`: Retrofit 3, 성공/오류 envelope, 401/403/408/429/5xx·시간 제한 매핑 | MockWebServer 계약 테스트, 기본 주소 미설정 시 `SERVER_NOT_CONFIGURED` 유지 | 대기 |
| 1B-4 | 익명/계정 영역 분리, 백업 제외 규칙, Keystore 암호화 경계 | 백업 규칙 검증, 암호화 왕복 계측 테스트(API 26) | 대기 |

1B 전체는 1B-4까지 끝나고 API 26 기기 검증을 통과한 뒤 완료로 표시하고 2단계에 진입한다.

공식 릴리스 확인: Room 3.0.3은 androidx.room3 패키지, KSP 및 SQLiteDriver를 사용한다.
Navigation 3과 Room 3의 실제 전이 의존성 조합은 1B에서 검증한다.

- https://developer.android.com/jetpack/androidx/releases/room3
- https://developer.android.com/jetpack/androidx/releases/navigation3

## 후속 단계에서 반드시 유지할 계약

- 로그인 전 다운로드 금지. 익명 모드는 재연결해도 자동 로그인·자동 전송 금지.
- 로그인 계정의 신규 시작: 현재 할당 조회 → 정의 다운로드/검증/저장 → 할당 재조회 → 일치할 때만 초안 생성.
- 진행 중 응답은 과거 할당으로 계속 완료·제출 가능. 익명 가져오기에만 현재 할당 일치 조건 적용.
- OFF에서도 기존 응답 보존. ON/OFF 반복 제한 없음. 익명 근태는 서버로 전송하지 않음.
- 업무 상태와 전송 상태 분리. 최초 호출 후 추가 3회만 자동 재시도.
- 달력 기준 Asia/Seoul 3개월. 미전송·결과 미확인·진행 중 응답 자동 삭제 금지.
- 모든 슈퍼바이저는 전체 조사원 관리. 팀 모델 추가 금지.

## 외부 연동 준비 사항

실서버 연동 시 HTTPS API 기본 주소와 역할별 테스트 계정이 필요하다.
주소·계정이 없어도 1B의 로컬 DB 및 HTTP 계약 테스트는 진행할 수 있다.
API 26은 에뮬레이터(`Fsp_API26`)로 확인했다. 현장 파일럿용 실기기 모델 목록은 아직 확보하지 않았다.

## 1A 검증 결과 (2026-09-30)

아래는 최초 API 23 설정 당시의 검증 이력이다. 현재 지원 기준과 재검증 결과는 다음 절을 따른다.

- `:core:domain:test`: 10개 통과, 실패 0개.
- `:app:assembleDebug`: 성공. APK의 minSdkVersionForDexing=23 확인.
- `:app:lintDebug`: 오류 0개, 경고 18개. 경고는 후속 통합 시 정리 대상.
- API 26 adaptive icon을 `mipmap-anydpi-v26`로 분리해 API 23 리소스 링크 오류 해결.
- 빌드는 기존 Android Studio JDK와 사용자 Gradle 캐시로 실행.
- 실기기/에뮬레이터 실행, 화면 시각 검증, DB 영속화, 서버 통신은 아직 검증하지 않음.

## 최소 지원 버전 변경 검증 (2026-09-30)

- `minSdk = 26`: Android 8.0 이상 지원으로 변경.
- `:app:assembleDebug`: 성공. APK의 minSdkVersionForDexing=26 확인.
- `:app:lintDebug`: 오류 0개, 경고 12개.
- README와 후속 검증 대상도 API 26 기준으로 갱신. 실기기 실행은 미검증.

## 1B-1 완료 기록 (2026-09-30)

### 구현 범위

- 모듈 추가: `core:navigation`, `core:data`, `feature:auth`, `feature:dashboard`. `settings.gradle.kts`에 등록.
- Hilt 2.60.1 + KSP 2.3.12: `FspApplication`(`@HiltAndroidApp`), `MainActivity`(`@AndroidEntryPoint`), `DataModule`(`@Binds`).
- Navigation 3 1.2.0: `FspNavigation`의 `rememberNavBackStack(LoginRoute)` + `NavDisplay`. 엔트리마다 `rememberSaveableStateHolderNavEntryDecorator`와 `rememberViewModelStoreNavEntryDecorator`(lifecycle-viewmodel-navigation3 2.11.0)를 적용한다.
- MVI: 화면별 `UiState`/`Intent`/`Effect`, `StateFlow` 상태, `Channel` 일회성 Effect. 화면 이동은 Effect → app 콜백으로 처리한다.
- 기존 1A `MainActivity.EntryScreen`의 화면과 문구는 `LoginScreen`, `OfflineDashboardScreen`으로 옮기고 동작을 유지했다.
- 도메인 인터페이스 추가: `AuthRepository`/`LoginResult`/`LoginFailure`, `SurveyCacheRepository`.

### 주요 변경 파일

- `gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, `core/domain/build.gradle.kts`
- `app/src/main/java/dev/comon/fsp/{FspApplication,MainActivity}.kt`, `app/.../navigation/{FspNavigation,BackStackOps}.kt`
- `core/navigation/.../FspRoutes.kt`, `core/data/.../{UnconfiguredAuthRepository,EmptySurveyCacheRepository,di/DataModule}.kt`
- `feature/auth/.../{LoginContract,LoginViewModel,LoginScreen}.kt`, `feature/dashboard/.../{OfflineDashboardContract,OfflineDashboardViewModel,OfflineDashboardScreen}.kt`
- 테스트: `LoginViewModelTest`, `OfflineDashboardViewModelTest`, `PlaceholderRepositoriesTest`, `BackStackOpsTest`, `FspNavigationRestorationTest`, `MainActivityFlowTest`. 템플릿 `Example*Test`는 삭제했다.

### 설계 결정

- feature 모듈은 서로 참조하지 않고 `core:navigation`도 참조하지 않는다. route 키와 화면 이동 연결은 `app`만 한다. feature는 `core:domain` 인터페이스에만 의존하고 `core:data` 구현은 `app`의 Hilt 조립으로만 연결한다.
- 비밀번호는 ViewModel 메모리에만 둔다. `SavedStateHandle`에는 `loginId`만 저장하고, 로그인 실패나 오프라인 진입 시 비밀번호를 비운다. `toString`도 비밀번호를 마스킹한다. 비밀번호는 trim하지 않고 아이디만 trim한다.
- 로그인 제출 중에는 중복 제출을 무시한다(`canSubmit`).
- 백스택 조작(`navigateTo`, `popTo`)은 같은 화면 중복 push를 막고 스택을 비우지 않는다. `onBack`은 루트를 제거하지 않는다.
- `FspNavigation`은 화면 슬롯을 인자로 받는다. 기본값은 실제 Hilt 화면이고, 테스트는 Hilt 없이 백스택 복원만 검증할 수 있다.
- 로그인 성공(`LoginResult.Success`)과 계정 대시보드 route는 서버 연동이 필요한 2단계에서 추가한다. 지금 추가하면 도달할 수 없는 코드가 되기 때문이다.
- Kotlin은 2.2.10을 유지한다. Kotlin 2.2와 호환되도록 kotlinx-serialization 1.9.0, coroutines 1.10.2로 고정했다. 해석된 kotlin-stdlib는 전이 의존성 때문에 2.3.21이다.

### 실행한 검증과 결과

- 단위 테스트: 모두 통과.
  - `:core:domain:test` 10개
  - `:core:data:testDebugUnitTest` 2개
  - `:feature:auth:testDebugUnitTest` 7개
  - `:feature:dashboard:testDebugUnitTest` 4개
  - `:app:testDebugUnitTest` 3개
  - 합계 26개, 실패 0개.
- `:app:assembleDebug`: 성공. Hilt `DaggerFspApplication_HiltComponents_SingletonC` 생성 확인. APK `minSdkVersion=26`, `targetSdkVersion=37`.
- Lint(`lintDebug`): app은 오류 0개, 경고 17개. `core:data`, `core:navigation`, `feature:auth`, `feature:dashboard`는 이슈 없음.
  - 늘어난 경고 5개는 모두 새 버전 안내다(Kotlin 플러그인, AGP 9.4.1, coroutines, serialization).
  - 나머지는 1A부터 있던 미사용 색상, `mipmap-anydpi-v26`, 중복 label 경고다.
- `:app:connectedDebugAndroidTest`: API 26 에뮬레이터(`Fsp_API26`, Android 8.0, x86, google_apis_playstore)에서 4개 모두 통과.
  - 백스택과 엔트리 저장 상태의 저장·복원(`StateRestorationTester`)
  - 실제 Hilt 그래프에서 서버 미설정 로그인 실패 표시
  - 오프라인 “설문 데이터가 없습니다” 표시와 액티비티 재생성 후 유지, 돌아가기 버튼
  - 시스템 뒤로 가기
  - 같은 계측 스위트를 `adb shell am instrument`로 추가 15회 반복 실행했고 모두 통과했다.
- 계측 테스트 안정화 과정:
  - 창 없이 실행한 에뮬레이터는 비터치 모드로 시작한다. 그래서 첫 입력 필드가 포커스를 받고 키보드가 화면 높이를 줄여, 아래쪽 버튼 클릭이 빗나갔다(약 30~40% 실패).
  - 테스트 시작 시 키보드를 닫고, 클릭 전에 스크롤하고, 전환 후 표시를 제한 시간 동안 기다리도록 수정했다.
  - 앱 동작은 바꾸지 않았다. 실제 터치 기기에서는 초기 포커스가 생기지 않으며, 버튼은 스크롤로 도달할 수 있다.

### 남은 문제·미검증 항목

- 최신 OS(API 36/37), 태블릿·폴더블 AVD, 실기기에서의 실행은 미검증이다(6단계 범위).
- 실제 프로세스 종료(`adb shell am kill`) 후 복원은 `StateRestorationTester`와 `SavedStateHandle` 재생성 단위 테스트로 대신했다. 실제 프로세스 종료 시나리오는 미검증이다.
- 서버 연동은 전혀 구현하지 않았다. HTTPS API 기본 주소와 역할별 테스트 계정이 여전히 필요하다.
- Lint 버전 안내 경고: Kotlin 2.4.x, AGP 9.4.1 업그레이드 여부는 1B-2에서 Room 3·KSP 조합을 검증할 때 함께 판단한다.

## 다음 단계: 1B-2 시작 작업

1. `core:database` Android 라이브러리 모듈을 추가한다. 의존성은 `androidx.room3:room3-runtime`/`room3-compiler` 3.0.3(KSP)과 `androidx.sqlite:sqlite-bundled` 2.7.1이다. `room3` 스키마 내보내기 디렉터리(`core/database/schemas`)를 설정한다.
2. 기획서 8장 테이블 중 1B 범위를 정의한다: `LocalSession`, `SurveyDefinition`, `Outbox`. `Outbox.ownerUserId`는 필수이며 익명 원본은 자동 등록하지 않는다.
3. 로컬 변경과 Outbox 생성을 하나의 트랜잭션으로 묶는 DAO를 만들고, 중간 실패 시 둘 다 롤백되는지 테스트한다(JVM 번들 SQLite 또는 API 26 계측).
4. `EmptySurveyCacheRepository`를 Room 기반 구현으로 교체한다. 다운로드 API가 없으므로 실제 캐시는 계속 비어 있어야 하며, 샘플 데이터를 삽입하지 않는다.
5. 마이그레이션 테스트 기반을 준비한다(v1 스키마 고정, destructive migration 금지).
6. 검증 명령: 아래 전체 명령 + `:core:database` 테스트·Lint.

전체 검증 명령(API 26 에뮬레이터 실행 상태 필요):

```powershell
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :feature:auth:testDebugUnitTest :feature:dashboard:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :core:data:lintDebug :core:navigation:lintDebug :feature:auth:lintDebug :feature:dashboard:lintDebug :app:connectedDebugAndroidTest
```

API 26 AVD 준비: `avdmanager create avd -n Fsp_API26 -k "system-images;android-26;google_apis_playstore;x86" -d pixel` 실행 후 `emulator -avd Fsp_API26 -no-window -no-audio -no-snapshot`.
