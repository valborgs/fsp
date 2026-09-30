# 현장 설문 앱 개발 진행표

기준 문서와 우선순위:

1. **서버 API 계약은 [`docs/fsp_api_spec_v1_4.md`](fsp_api_spec_v1_4.md)(백엔드 API 명세 v1.4)를 따른다.** 실제 백엔드 서버의 API다. 이 문서나 v1.3과 다르면 v1.4가 우선한다(2026-09-30 지시).
2. 제품 요구사항은 `fsp_api_spec.docx`(기획서·API 명세 v1.3)와 참조 대화의 확정 요구사항을 따른다.
3. 최소 지원 버전은 Android 8.0(API 26)이다(2026-09-30 확정). v1.4 명세의 클라이언트 최소 버전 표기도 이 기준으로 맞췄다.

서버는 별도 구현 범위다. 단계별로 구현과 검증을 완료하고 진행 기록을 갱신한다.

## 개발 단계

| 단계 | 작업 | 완료 기준 | 상태 |
| --- | --- | --- | --- |
| 1A | 프로젝트 기반, 권한·시작 정책, 최초 진입 화면 | 핵심 정책 테스트, API 26 설정 빌드 | 완료 |
| 1B | MVI, Hilt, Navigation 3, Room 3, Retrofit 3 통합 | DI 생성, 탐색 복원, DB 트랜잭션·마이그레이션, HTTP 계약 테스트 | 완료 (2026-09-30, 1B-1~1B-4) |
| 2 | 로그인·세션·계정·CSV·할당 관리 (API-01~12, 14, 16~19) | 역할별 접근, refresh 회전·single-flight, CSV 원자적 등록, 할당 If-Match 412 처리 | 진행 중 (2-1 완료, 2-2 다음) |
| 3 | 할당 확인·다운로드·오프라인 선택·반복 출퇴근 (API-13, 15, 20~23) | 유효 캐시만 사용, 설문 상태 PUBLISHED/SUSPENDED/CLOSED 반영, 재실행 복원, 근태 sequence·로컬/Outbox 원자적 저장 | 대기 |
| 4 | 3종 문항·자동 저장·초안·제출 (API-26, 27) | 단일 선택/주관식/7점 척도, 버전·할당·`attendanceEventId` 고정, draftVersion, 종료 후 복원 | 대기 |
| 5 | 선택 전송·현황·검수 (API-24, 25, 28~33) | 멱등성, 최초+추가 3회(10/20/40초·Retry-After), 반려 revision, If-Match 412, 익명 가져오기 항목별 결과 | 대기 |
| 6 | 다중 화면·보안·장애 복구·현장 파일럿 | API 26/최신 OS, 태블릿/폴드, 만료·프로세스 종료 검증 | 대기 |

## 현재 구현 범위

- `app`: Hilt Application, Navigation 3 루트 백스택(`FspNavigation`: 시작 → 로그인/계정 홈, 오프라인), 서버 주소 설정(`AppConfigModule`), 테마.
- `core:navigation`: 직렬화 가능한 route 키(`StartupRoute`, `LoginRoute`, `AccountHomeRoute`, `OfflineDashboardRoute`).
- `core:database`: Room 3 `FspDatabase` v2(`local_session`, `survey_definition`, `outbox`), 마이그레이션 1→2, DAO, `LocalWriteTransaction`, Hilt 제공.
- `core:network`: Retrofit 3/OkHttp 5 `ApiClient`, v1.4 공통 envelope·오류 매핑(`ApiResult`/`ApiFailure`, `ApiError.details`), 공통 헤더 인터셉터, 401 refresh `TokenAuthenticator`, `AuthApi`(API-01~04), `NetworkConfig`(HTTPS 기본 주소·X-App-Version 검증).
- `core:security`: Android Keystore AES-256-GCM `DataCipher`(`KeystoreDataCipher`), 암호화된 Refresh Token 저장소(`RefreshTokenStore`).
- `core:data`: Repository 구현과 Hilt 바인딩.
  - 로그인 `NetworkAuthRepository`, refresh `SessionTokenRefresher`, 세션 복원·로그아웃 `LocalSessionRepository`
  - 설문 캐시 `RoomSurveyCacheRepository`, 설치 ID `InstallationIdStore`
  - 토큰 `AccessTokenHolder`/`SessionCredentials`/`RefreshKeyStore`, Outbox 페이로드 암호화 `OutboxOperations`
- `feature:auth`: 로그인 화면 MVI와 `LoginViewModel`, 시작 화면(`StartupViewModel`: 저장된 세션 복원).
- `feature:dashboard`: 계정 없는 모드 대시보드(`OfflineDashboardViewModel`), 계정 홈 골격(`AccountHomeViewModel`: 이름·역할 표시, 로그아웃).
- `core:domain`: Android에 의존하지 않는 역할·세션·할당·설문 시작 정책, 계정 입력 규칙(`CredentialRules`), Repository 인터페이스.
- 계정 등급 1/2/3, 관리자 실제 수집 금지, 출근 조건, 인증 후 다운로드 정책.
- 로그인 계정은 본인 할당 캐시만 사용. 익명 모드는 유효 캐시를 선택.
- 온라인 신규 시작은 최종 할당 확인 필수. 실패 시 오래된 할당으로 시작 불가.
- 신규 응답 시작 시 설문·할당을 값으로 고정. 이후 할당 변경은 다음 시작에 적용.
- 익명 응답 가져오기는 조사원의 현재 할당 설문·버전 일치 여부 확인.

- **로그인·세션**
  - 로그인(API-01), 토큰 갱신(API-02), 로그아웃(API-03)이 구현되어 있다.
  - 서버 주소(`fsp.apiBaseUrl`)가 없는 빌드는 네트워크 호출 없이 “서버 미설정” 실패를 표시한다(가짜 성공 없음).
  - 실서버 연동은 아직 검증하지 않았다.
- **미구현**: 다운로드, 출퇴근, 설문 입력, 계정·할당 관리 화면은 아직 없다. 계정 홈은 이름·역할 표시와 로그아웃만 있다.
- **DB에 기록되는 것**: 로그인 세션(`local_session`)만 기록된다. 업무 데이터를 쓰는 기능은 아직 없다.
오프라인 진입은 Room `survey_definition`을 조회한다. 다운로드 기능이 없어 테이블이 비어 있으므로 설문 없음 화면을 표시한다. 샘플 설문은 내장하지 않는다.
화면은 스크롤·키보드 여백·최대 본문 너비를 적용한 기본형이며 폴더블 힌지 대응은 6단계다.
정책 단위 테스트는 실제 DB에 저장된 응답의 프로세스 종료 복원을 검증하지 않는다.

## 1B 하위 단계

| 하위 단계 | 범위 | 완료 기준 | 상태 |
| --- | --- | --- | --- |
| 1B-1 | 멀티모듈 골격, Hilt/KSP, Navigation 3 백스택·엔트리 범위 ViewModel, MVI 전환 | Hilt 그래프 생성, MVI 단위 테스트, 백스택 복원 계측 테스트(API 26), 빌드·Lint | 완료 (2026-09-30) |
| 1B-2 | `core:database`: Room 3 SQLiteDriver, 스키마 내보내기, 캐시·세션·Outbox 트랜잭션 | 로컬 저장+Outbox 원자성·롤백 테스트, 마이그레이션 테스트, `EmptySurveyCacheRepository` 교체 | 완료 (2026-09-30) |
| 1B-3 | `core:network`: Retrofit 3, 성공/오류 envelope, 401/403/408/429/5xx·시간 제한 매핑 | MockWebServer 계약 테스트, 기본 주소 미설정 시 `SERVER_NOT_CONFIGURED` 유지 | 완료 (2026-09-30) |
| 1B-4 | 익명/계정 영역 분리, 백업 제외 규칙, Keystore 암호화 경계 | 백업 규칙 검증(`fsp.db` 제외 포함), 암호화 왕복 계측 테스트(API 26) | 완료 (2026-09-30) |

1B 전체는 1B-4까지 끝나고 API 26 기기 검증을 통과해 2026-09-30 완료로 표시했다. 다음은 2단계다.

공식 릴리스 확인: Room 3.0.3은 androidx.room3 패키지, KSP 및 SQLiteDriver를 사용한다.
Navigation 3 1.2.0 + Room 3.0.3 + Hilt 2.60.1 + Kotlin 2.2.10 조합은 1B-2에서 빌드와 API 26 계측 테스트로 확인했다.

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

v1.4 API 계약에서 이후 단계가 반드시 지킬 항목:

- **요청 본문**: DTO 표에 있는 키만 보낸다(알 수 없는 필드는 422). nullable 필드는 빈 문자열이 아니라 null로 보낸다. 응답의 알 수 없는 필드는 무시한다(`ignoreUnknownKeys`).
- **공통 헤더와 형식**
  - 헤더: `X-Device-ID`(설치 UUID v4, 본문 deviceId와 같아야 함), `X-App-Version`(`[A-Za-z0-9._+-]{1,32}`), `X-Request-ID`(요청마다 새 UUID v4).
  - 시각은 UTC `YYYY-MM-DDTHH:mm:ss(.SSS)Z`이다. 오프셋 형식은 거절된다.
  - UUID는 소문자 canonical 형식이다.
- **`Idempotency-Key`**(UUID v4) 필수 API
  - API-02(refresh), 06, 07, 08, 09, 10, 11, 18, 21, 27, 30, 31
  - 재전송할 때는 같은 키와 같은 본문을 쓴다. 본문이 바뀌면 새 키를 쓴다.
- **If-Match**
  - 강한 ETag `"7"` 형식이다. 누락은 428, 형식 오류는 400, 불일치는 412(`details.currentVersion`)다.
  - 412가 나면 최신 상태를 다시 조회해 보여 주고, 자동으로 덮어쓰지 않는다.
- **인증**
  - login·refresh를 뺀 모든 API는 Bearer 토큰이 필요하다.
  - 401은 요청당 refresh 1회 후 원래 요청을 1회만 재개한다.
  - refresh는 single-flight로 수행하고 Idempotency-Key를 영속 저장한다. `TOKEN_REUSE_DETECTED`, `INVALID_REFRESH_TOKEN`, `DEVICE_REVOKED`, `ACCOUNT_DISABLED`면 세션을 종료한다.
- **재시도**
  - 연결 오류, 타임아웃, 408, 429, 5xx, `409 OPERATION_IN_PROGRESS`는 최초 + 추가 최대 3회 재시도한다. 간격은 최소 10/20/40초이고, Retry-After가 더 길면 그 값을 쓴다.
  - 403은 BLOCKED로 두고 자동 반복하지 않는다. 그 밖의 4xx는 실패로 표시하고 내용을 자동 수정하거나 삭제하지 않는다.
- **근태**
  - sequence는 (userId, deviceId)마다 따로 관리한다. 초기값은 로그인과 `/me`의 `deviceNextSequence`이고, `local_session.deviceNextSequence`에 저장되어 있다.
  - `EVENT_GAP`·`SEQUENCE_CONFLICT`·`ATTENDANCE_STATE_CONFLICT`가 나면 자동으로 삭제하거나 바꿔치기하지 않는다.
- **응답**
  - 새 응답은 로컬 ON 이벤트의 `attendanceEventId`와 함께 생성한다. OFF 상태에서는 응답 ID를 만들지 않는다.
  - 초안 `draftVersion`은 단조 증가한다. 서버 초안이 없어도 전체 답변으로 제출할 수 있다.
- **설문 운영 상태**: SUSPENDED·CLOSED 설문으로는 새로 시작하지 않는다. CLOSED 설문은 서버에 새로 저장하거나 제출할 수 없다(409 `SURVEY_CLOSED`).

## 외부 연동 준비 사항

실서버 연동 시 HTTPS API 기본 주소와 역할별 테스트 계정이 필요하다.
기본 주소는 Gradle 속성 `fsp.apiBaseUrl`(예: `-Pfsp.apiBaseUrl=https://host/api/v1`)로 주입하며 HTTPS가 아니면 빌드가 실패한다.
`User.resourceVersion`의 JSON 타입과 refresh 응답 형식은 v1.4에서 확정되었다(정수, `TokenPair`). 2-1에서 반영했다.
주소·계정이 없어도 1B의 로컬 DB 및 HTTP 계약 테스트는 진행할 수 있다.
API 26은 에뮬레이터(`Fsp_API26`)로 확인했다. 현장 파일럿용 실기기 모델 목록은 아직 확보하지 않았다.

## 1A 검증 결과 (2026-09-30)

아래는 최소 지원 버전을 확정하기 전의 초기 검증 이력이다. 현재 지원 기준(API 26)의 재검증 결과는 다음 절을 따른다.

- `:core:domain:test`: 10개 통과, 실패 0개.
- `:app:assembleDebug`: 성공.
- `:app:lintDebug`: 오류 0개, 경고 18개. 경고는 후속 통합 시 정리 대상.
- adaptive icon을 `mipmap-anydpi-v26`로 분리했다. minSdk 26에서는 불필요한 분리라 Lint `ObsoleteSdkInt` 경고 대상이다.
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
- Lint 버전 안내 경고: Kotlin 2.4.x, AGP 9.4.1 업그레이드는 1B-2에서도 보류했다. 현재 조합이 Room 3·KSP·Hilt와 함께 동작하는 것을 확인했으므로, 업그레이드는 별도 작업으로 한다.

## 1B-2 완료 기록 (2026-09-30)

### 구현 범위

- `core:database` 모듈 추가.
  - Room 3.0.3(KSP, `androidx.room3` Gradle 플러그인)과 `sqlite-bundled` 2.7.1(`BundledSQLiteDriver`)을 사용한다.
  - 스키마는 `core/database/schemas/dev.comon.fsp.core.database.FspDatabase/1.json`로 내보낸다. 커밋 대상이며 계측 테스트 assets로도 쓰인다.
- v1 테이블은 기획서 8장 기준이다. 시각은 epoch millis, enum은 이름(TEXT)으로 저장한다.
  - `local_session`: `sessionId` PK, `mode`(ACCOUNT/ANONYMOUS), `userId?`, `deviceId`, `roleGrade?`, `createdAt`. 엔티티 `init`에서 계정/익명 불변식을 검사한다.
  - `survey_definition`: (`surveyId`, `version`) PK, `schemaVersion`, `title`, `contentJson`, `checksum`, `downloadedAt`, `lastVerifiedAt`, `availability`. `title`과 `lastVerifiedAt`은 오프라인 목록 표시와 캐시 3개월 만료 기준을 위해 기획서 필드에 추가했다.
  - `outbox`: `operationId` PK, `ownerUserId` NOT NULL, `entityId`, `kind`, `payloadJson`, `state`, `attemptCount`, `retryCycle`, `nextAttemptAt`, `lastError?`, `createdAt`. (`ownerUserId`, `state`, `nextAttemptAt`)와 (`kind`, `entityId`)에 인덱스가 있다.
- `LocalWriteTransaction`: `withWriteTransaction`(IMMEDIATE)으로 로컬 변경과 Outbox 추가를 한 트랜잭션에서 커밋한다. 예외가 나면 전체를 롤백하고 예외를 다시 던진다.
- `FspDatabase.create()`가 운영과 테스트의 유일한 생성 경로다. destructive fallback은 설정하지 않는다.
- `DatabaseModule`(Hilt)로 DB와 DAO를 싱글턴으로 제공한다.
- `EmptySurveyCacheRepository`를 삭제하고 `RoomSurveyCacheRepository`로 교체했다. 오프라인 대시보드는 실제 DB를 조회한다.

### 주요 변경 파일

- `gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`, `core/database/build.gradle.kts`, `core/data/build.gradle.kts`
- `core/database/src/main/kotlin/dev/comon/fsp/core/database/{FspDatabase,dao/Daos,di/DatabaseModule}.kt`, `entity/{LocalSessionEntity,SurveyDefinitionEntity,OutboxEntity}.kt`
- `core/data/.../{RoomSurveyCacheRepository,di/DataModule}.kt`
- 테스트: `core/database/src/androidTest/.../{LocalWriteTransactionTest,SurveyDefinitionDaoTest,FspDatabaseMigrationTest}.kt`, `core/data/src/test/.../RoomSurveyCacheRepositoryTest.kt`

### 설계 결정

- Outbox 소유자는 NOT NULL이다. 익명 원본은 자동 등록할 수 없고, 사용자가 로그인 후 선택한 경우에만 로그인 조사원을 소유자로 해서 추가된다.
- Outbox 중복 `operationId`는 덮어쓰지 않고 실패시킨다(ABORT). 재전송은 같은 행을 그대로 다시 사용한다.
- DB 테스트는 JVM 단위 테스트가 아니라 API 26 계측 테스트로 실행한다. Android용 번들 SQLite는 호스트 JVM에서 실행되지 않고, 대상 기기의 실제 동작을 검증해야 하기 때문이다.
- 실제 업무 쓰기(근태·응답과 Outbox)는 3~4단계에서 `LocalWriteTransaction`을 사용한다. 이번 단계에서는 세션과 Outbox 쓰기로 트랜잭션 원자성을 검증했다.
- 설문 캐시의 3개월 만료(`lastVerifiedAt` 기준)는 다운로드와 함께 3단계에서 적용한다. 지금은 `availability`만 반영한다.

### 실행한 검증과 결과

- 단위 테스트 합계 27개, 실패 0개.
  - `:core:domain:test` 10개
  - `:core:data:testDebugUnitTest` 3개
  - `:feature:auth:testDebugUnitTest` 7개
  - `:feature:dashboard:testDebugUnitTest` 4개
  - `:app:testDebugUnitTest` 3개
- `:core:database:connectedDebugAndroidTest`(API 26 `Fsp_API26`): 11개 모두 통과.
  - 커밋: 세션과 Outbox가 함께 저장된다.
  - 롤백: 두 쓰기 이후 예외가 나거나, Outbox 제약 위반(중복 ID)이 나면 앞선 로컬 변경까지 모두 롤백된다.
  - `ownerUserId` NULL 삽입을 거부한다.
  - 대기 목록은 소유자 범위로 조회되고 생성 순서로 정렬된다.
  - 세션 불변식이 지켜진다.
  - DAO: 같은 키는 교체되고, 다른 버전은 함께 저장된다.
  - v1 내보낸 스키마가 엔티티와 일치한다.
  - 기존 v1 데이터는 앱이 DB를 열 때 보존된다.
  - 알 수 없는 상위 버전 DB는 데이터를 지우지 않고 열기에 실패한다.
- `:app:connectedDebugAndroidTest`: 4개 통과. 오프라인 화면이 실제 Room DB를 조회한다.
- 반복 실행: app과 DB 계측 스위트를 각각 5회 반복했고 모두 통과했다.
- 기기에서 `run-as`로 `databases/fsp.db` 생성을 확인했다.
- `:app:assembleDebug`: 성공.
- Lint: app은 오류 0개, 경고 17개로 1B-1과 같다. `core:database`를 포함한 나머지 모듈은 이슈가 없다.
- 참고: 첫 전체 실행에서 app 계측 4개가 실패했다. 원인은 에뮬레이터 화면이 꺼진 상태였던 것이다(`mWakefulness=Asleep`, 액티비티가 RESUMED 상태가 되지 않음). `input keyevent KEYCODE_WAKEUP`, `wm dismiss-keyguard`, `svc power stayon true`로 화면을 켜고 잠금을 해제한 뒤 다시 실행해 통과했다. 코드는 바꾸지 않았다.

### 남은 문제·미검증 항목

- **백업**(1B-4에서 해결): 당시 `allowBackup=true`이고 백업 규칙이 비어 있어 `fsp.db`가 자동 백업에 포함된다. 지금은 DB에 업무 데이터를 쓰는 기능이 없다. 1B-4에서 백업 제외 규칙을 적용해야 하며, 3단계에서 근태·응답을 쓰기 전에 반드시 완료해야 한다.
- **암호화**(1B-4에서 해결): 당시 `outbox.payloadJson`은 평문이었다. 1B-4에서 `OutboxOperations`로 암호화한다. 앞으로 저장할 응답 JSON도 같은 방식으로 암호화해야 한다.
- **스키마 범위**: `AttendanceEvent/Meta`, `Response`, `ResponseRevision`, `ReviewEvent`, `DashboardCache`, `ErrorEvent`는 아직 없다(3~5단계). 추가할 때마다 버전을 올리고 마이그레이션 테스트를 추가한다.
- **1.json 수정 금지**: 운영 배포 전이더라도 커밋된 `1.json`을 제자리에서 수정하지 않는다. 스키마를 바꾸면 버전 2와 마이그레이션으로 처리한다.
- **미검증**: 실제 프로세스 종료 후 DB 복원과 최신 OS·실기기 동작은 미검증이다(6단계).

## 1B-3 완료 기록 (2026-09-30)

### 구현 범위

- `core:network` 모듈 추가: Retrofit 3.0.0, `converter-kotlinx-serialization` 3.0.0, OkHttp 5.5.0, `kotlinx-serialization-json` 1.9.0. `INTERNET` 권한을 선언한다.
- 공통 응답 계약(기획서 11장): `ApiSuccess{data, meta}`, `ApiErrorBody{error{code, message, retryable, fields[{path, code}]}, meta{requestId, serverTime}}`.
- `ApiClient.call(Service::class) { ... }`는 모든 결과를 `ApiResult`로 변환하며, 코루틴 취소만 예외로 전파한다.
  - HTTP 상태별 매핑(`HttpFailureKind`)
    - 재시도 불가: 400, 401, 403, 404, 409, 410 `RETENTION_EXPIRED`, 412, 413, 422, 428, 기타 상태(`UNEXPECTED`)
    - 일시 실패(재시도 가능): 408, 429, 5xx
  - 예외 매핑: 시간 초과(`InterruptedIOException`)는 `Network(TIMEOUT)`, 그 밖의 IO 오류는 `Network(CONNECTIVITY)`로 둘 다 재시도 가능하다. 계약과 다른 2xx 본문은 `MalformedResponse`로 재시도 불가다.
  - 오류 본문이 envelope이 아니어도(프록시 HTML 등) 상태 코드로 분류하고, `X-Request-ID` 응답 헤더를 보존한다.
  - `Retry-After`는 초 단위와 HTTP-date를 모두 해석하며 음수가 되지 않는다.
  - 기본 주소가 없으면 Retrofit 인스턴스를 만들지 않고 `NotConfigured`를 반환한다(요청 0회).
- `ClientHeadersInterceptor`
  - 요청마다 새 UUID `X-Request-ID`를 붙이고, `X-Device-ID`와 `X-App-Version`도 붙인다.
  - `Authorization: Bearer`는 토큰이 있을 때만 붙인다.
  - 공개 API는 `@Headers(ClientHeadersInterceptor.NO_AUTH)`로 토큰 전송을 막는다. 내부 표시 헤더는 서버로 나가지 않는다.
  - (2-1에서 변경: v1.4에 맞춰 login·refresh 경로 기준 `AuthPaths`로 바꾸고 `NO_AUTH` 표시는 삭제했다.)
- OkHttp 설정: 연결 15초, 읽기 30초, 쓰기 30초, 전체 60초. `retryOnConnectionFailure(false)`로 재시도는 Outbox 정책에만 맡긴다. HTTP 로깅은 넣지 않았다(비밀번호·토큰 보호).
- `AuthApi.login`(`POST auth/login`, 공개)과 DTO `LoginRequest{id, pw, deviceId}`, `LoginResponse`, `UserDto`를 추가했다. `toString`은 비밀번호와 토큰을 마스킹한다.
  - (2-1에서 변경: v1.4 형식에 맞춰 `LoginResultDto{tokens, user, activeDeviceId, deviceNextSequence}`와 `TokenPairDto`로 교체했다.)
- 설정: `fsp.apiBaseUrl` Gradle 속성을 `BuildConfig.API_BASE_URL`로 넘기고, `AppConfigModule`이 `NetworkConfig`를 제공한다.
  - HTTPS가 아닌 주소는 Gradle 설정 단계와 `NetworkConfig.parse` 양쪽에서 거부한다.
  - 매니페스트에 `usesCleartextTraffic="false"`를 적용했다.
- `InstallationIdStore`: 설치 단위 UUID를 `noBackupFilesDir/installation-id`에 원자적으로 저장한다. 백업·복원 대상에서 빠지고 재설치 시 새 ID가 되며, 파일이 손상되면 다시 만든다.
- `NetworkIdentityModule`: `DeviceIdProvider`와 `AccessTokenProvider`를 제공한다. 토큰 제공자는 계정 세션이 아직 없으므로 항상 null을 반환한다.
- `NetworkGraphEntryPoint`(app): 아직 네트워크를 쓰는 화면이 없어도 Hilt가 네트워크 그래프를 컴파일 시점에 검증하도록 강제한다. 2단계에서 저장소가 `ApiClient`를 주입받으면 제거한다.
- 로그인 화면 동작은 바꾸지 않았다. `UnconfiguredAuthRepository`가 계속 `SERVER_NOT_CONFIGURED`를 반환한다. 로그인 성공 처리와 토큰 저장은 2단계와 1B-4 범위다.

### 주요 변경 파일

- `gradle/libs.versions.toml`, `settings.gradle.kts`, `core/network/build.gradle.kts`, `core/network/src/main/AndroidManifest.xml`, `core/data/build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- `core/network/src/main/kotlin/dev/comon/fsp/core/network/{NetworkConfig,ApiEnvelope,ApiResult,ApiClient,ClientHeadersInterceptor}.kt`, `auth/AuthApi.kt`, `di/NetworkModule.kt`
- `core/data/.../{InstallationIdStore,di/NetworkIdentityModule}.kt`, `app/.../di/{AppConfigModule,NetworkGraphEntryPoint}.kt`
- 테스트: `core/network/src/test/.../{ApiClientContractTest,NetworkConfigTest}.kt`, `core/data/src/test/.../InstallationIdStoreTest.kt`, `app/src/androidTest/.../NetworkGraphTest.kt`

### 설계 결정

- **재시도 판단 기준**: 재시도 가능 여부는 HTTP 상태로 판단하고, 서버 본문의 `retryable`은 참고하지 않는다. 기획서 6장이 “타임아웃·연결 실패·408·429·5xx”로 정했기 때문이다. 재시도 횟수(최초 + 추가 3회)와 간격은 5단계 Outbox Worker에서 구현한다.
  - (2-1에서 변경: v1.4 §11.3에 따라 `409 OPERATION_IN_PROGRESS`도 재시도 가능으로 분류한다.)
- **`resourceVersion` 제외**: `UserDto.resourceVersion`은 기획서에 JSON 타입이 없어 제외했다. `ignoreUnknownKeys`이므로 서버가 보내도 파싱에는 영향이 없다.
  - (2-1에서 변경: v1.4에서 정수로 확정되어 추가했다.)
- **Hilt 검증 방식**: Dagger `fullBindingGraphValidation` 옵션을 시험했지만 쓰지 않았다.
  - KSP와 `hiltJavaCompile` 양쪽에 넣어 봐도 쓰이지 않는 모듈의 누락 바인딩을 보고하지 않았다(음성 시험으로 확인).
  - 그래서 `@EntryPoint` 방식을 채택했다.
  - `AccessTokenProvider` 바인딩을 빼면 `[Dagger/MissingBinding]`으로 빌드가 실패하고, 복원하면 성공하는 것을 확인했다.

### 실행한 검증과 결과

- 단위 테스트 합계 48개, 실패 0개.
  - `:core:domain:test` 10개
  - `:core:data:testDebugUnitTest` 6개
  - `:core:network:testDebugUnitTest` 18개
  - `:feature:auth:testDebugUnitTest` 7개
  - `:feature:dashboard:testDebugUnitTest` 4개
  - `:app:testDebugUnitTest` 3개
- 네트워크 계약 테스트(MockWebServer, 로컬 HTTP): 다음을 확인했다.
  - 로그인 경로, 메서드, JSON 본문(비밀번호를 가공 없이 전송)
  - 공통 헤더, 공개 API에 토큰 미전송, 인증 API의 Bearer 헤더
  - 요청마다 다른 요청 ID
  - 오류 envelope 파싱과 상태 코드 15종의 매핑·재시도 가능 여부
  - 410 최종 실패, 429 `Retry-After`
  - 비 envelope 오류 본문, 계약과 다른 성공 본문
  - 시간 초과, 연결 실패, 주소 미설정 시 요청 0회
- `:core:database:connectedDebugAndroidTest`(API 26): 11개 통과.
- `:app:connectedDebugAndroidTest`(API 26): 6개 통과. 기존 4개에 `NetworkGraphTest` 2개가 추가됐다.
  - 실제 Hilt 그래프에서 주소 미설정 시 `NotConfigured`가 반환된다.
  - 설치 ID가 UUID이고 `no_backup`에 저장된다.
  - 추가로 `am instrument`로 3회 반복 실행했고 모두 통과했다.
- `:app:assembleDebug`: 성공. `BuildConfig.API_BASE_URL=""` 기본값을 확인했다. `-Pfsp.apiBaseUrl=http://...`는 “fsp.apiBaseUrl must use HTTPS”로 빌드가 실패한다.
- Lint: app은 오류 0개, 경고 18개다. 새 경고 1개는 `kotlinx-serialization-json` 새 버전 안내다. `core:network`를 포함한 나머지 모듈은 이슈가 없다.

### 남은 문제·미검증 항목

- **실서버 미검증**: 서버와의 실제 통신, TLS 인증서, 서버 측 오류 형식은 검증하지 않았다. 모든 HTTP 검증은 로컬 MockWebServer 기준이다. 실서버 주소와 계정이 필요하다.
- **로그인 흐름 미연결**: 로그인 화면은 아직 네트워크를 쓰지 않는다. 2단계에서 할 일은 다음과 같다.
  - `ApiFailure`를 `LoginFailure`로 매핑한다(401 → 자격 증명 오류, 네트워크 오류 → NETWORK, 409 `ACTIVE_DEVICE_EXISTS` 등).
  - 성공 시 세션과 토큰을 저장한다.
- **토큰 갱신 미구현**: 401 발생 시 토큰 갱신 1회 규칙은 2단계에서 구현한다.
- **백업·평문 저장(1B-2에서 이어짐)**: 1B-4에서 해결했다.

## 1B-4 완료 기록 (2026-09-30)

### 구현 범위

- **백업 제외**
  - `backup_rules.xml`(API 26~30)은 `database` 도메인 전체를 제외한다.
  - `data_extraction_rules.xml`(API 31+)은 `cloud-backup`과 `device-transfer` 모두에서 `database` 도메인 전체를 제외한다.
  - 토큰과 설치 ID는 `noBackupFilesDir`에 있으므로 플랫폼이 백업하지 않는다.
- **`core:security` 모듈 추가**
  - `DataCipher`/`KeystoreDataCipher`: AndroidKeyStore의 AES-256-GCM 비추출 키(별칭 `fsp.data.v1`)를 쓴다.
  - 출력 형식은 `v1:` + Base64(IV 12바이트 + 암호문 + 태그 16바이트)이고, 연관 데이터(AAD)를 필수로 받는다.
  - 오류는 `CipherException.Tampered`(변조·AAD 불일치), `KeyUnavailable`(키 없음·무효화), `Malformed`(형식 오류)로 구분한다.
  - 키는 암호화할 때만 만든다. 복호화는 키를 만들지 않으므로, 키가 없으면 조용히 새 키를 만드는 대신 오류로 드러난다.
  - `RefreshTokenStore`: `no_backup/session/refresh-token`에 `v1` 형식 표시, 사용자 ID, 암호문 세 줄을 원자적으로 저장한다.
    - AAD에 사용자 ID가 들어가 다른 계정으로는 읽을 수 없다.
    - 복호화할 수 없거나 형식이 틀린 파일은 삭제하고 null을 반환한다(재로그인으로 복구). 비밀번호는 저장하지 않는다.
  - `SecurityModule`(Hilt)이 `DataCipher`와 `RefreshTokenStore`를 제공한다.
- **`core:data` 추가**
  - `AccessTokenHolder`: 메모리 전용 Access Token이다. `AccessTokenProvider` 바인딩으로, 이전의 항상 null 구현을 대체했다.
  - `SessionCredentials`: `store`(로그인 성공 시)와 `clear`(로그아웃 시 두 토큰 삭제, 미전송 기록은 보존)를 한곳에서 처리한다.
  - `OutboxOperations`: Outbox 행을 만들 때 페이로드를 암호화하고, `payload()`로 복호화한다.
    - AAD는 `outbox:v1:{operationId}:{ownerUserId}:{kind}:{entityId}`다. 소유자나 대상이 바뀐 행은 복호화되지 않는다.
    - Keystore 작업은 DB 트랜잭션 밖에서 행을 만든 뒤, 트랜잭션 안에서 삽입하는 방식으로 사용한다.
- **도메인 `DataScopePolicy.canAccess`**
  - 계정 세션은 자기 소유 기록만, 익명 세션은 소유자가 없는 기록만 본다.
  - 슈퍼바이저도 기기에 있는 다른 조사원의 기록은 볼 수 없다. 조사원 기록은 서버를 통해 열람한다.
- **app**: 진입점을 `InfrastructureGraphEntryPoint`로 확장했다(네트워크·자격 증명·Outbox 암호화 그래프를 컴파일 시점에 검증).

### 주요 변경 파일

- `settings.gradle.kts`, `core/security/build.gradle.kts`, `core/data/build.gradle.kts`, `app/build.gradle.kts`
- `app/src/main/res/xml/{backup_rules,data_extraction_rules}.xml`
- `core/security/src/main/kotlin/dev/comon/fsp/core/security/{DataCipher,KeystoreDataCipher,RefreshTokenStore,di/SecurityModule}.kt`
- `core/data/.../{SessionCredentials,OutboxOperations,di/NetworkIdentityModule}.kt`, `core/domain/.../DataScopePolicy.kt`
- `app/.../di/InfrastructureGraphEntryPoint.kt`(이전 `NetworkGraphEntryPoint`)
- 테스트
  - JVM: `app/src/test/.../BackupRulesTest.kt`, `core/security/src/test/.../RefreshTokenStoreTest.kt`, `core/data/src/test/.../{FakeCipher,OutboxOperationsTest,SessionCredentialsTest}.kt`, `core/domain/src/test/.../DataScopePolicyTest.kt`
  - API 26 계측: `core/security/src/androidTest/.../KeystoreDataCipherTest.kt`, `app/src/androidTest/.../InfrastructureGraphTest.kt`(이전 `NetworkGraphTest`)

### 설계 결정

- **DB 전체 백업 제외**: 파일 단위로 나열하는 대신 DB 전체를 제외한다. 암호화 키는 백업되지 않으므로 복원된 암호문은 어차피 쓸 수 없다. 기기를 교체하기 전에는 동기화를 먼저 끝내야 한다(기획서 8장).
- **DB 전체 암호화 미채택**: SQLCipher 등은 쓰지 않고, 민감 열(Outbox 페이로드, 이후 응답 JSON)만 필드 단위로 암호화한다. 기획서 8장이 “Room 자체를 암호화 솔루션으로 간주하지 않는다”고 하고, Room 3 SQLiteDriver 호환성 검증 부담도 줄이기 위해서다.
- **스키마 유지**: Outbox 열 이름 `payloadJson`은 v1 스키마를 유지하려고 그대로 두었다. 이제 이 열에는 암호문이 저장된다.
- **Keystore 키 조건**: Keystore 키에 사용자 인증 조건은 걸지 않았다. 현장 오프라인 작업 중 잠금 화면 설정이 바뀌어도 키가 무효화되지 않게 하기 위해서다.

### 실행한 검증과 결과

- 단위 테스트 합계 66개, 실패 0개.
  - domain 13개, data 13개, network 18개, security 6개, auth 7개, dashboard 4개, app 5개
  - app에 `BackupRulesTest` 2개가 추가됐다.
- API 26 계측 테스트(`Fsp_API26`) 합계 24개, 모두 통과.
  - `:core:database:connectedDebugAndroidTest` 11개
  - `:core:security:connectedDebugAndroidTest` 5개
    - 실제 Keystore로 암호화 왕복과 무작위 IV를 확인했다.
    - AAD 불일치와 암호문 1비트 변조는 `Tampered`로 거부된다.
    - 외부 입력과 잘린 입력은 `Malformed`다.
    - 키를 삭제하면 `KeyUnavailable`이 나고 복호화 과정에서 키를 다시 만들지 않는다. 새 키로는 옛 데이터를 읽을 수 없다.
  - `:app:connectedDebugAndroidTest` 8개
    - `InfrastructureGraphTest`: 실제 Hilt 그래프에서 토큰이 저장 시 암호화되고 로그아웃 시 삭제되며, Outbox 페이로드가 Keystore로 암호화된다.
  - app 8개와 security 5개 스위트는 `am instrument`로 각각 3회 추가 반복했고 모두 통과했다.
- 최종 APK 매니페스트(`aapt2 dump xmltree`)에서 `fullBackupContent`, `dataExtractionRules` 연결과 `usesCleartextTraffic=false`, `INTERNET` 권한을 확인했다.
- `:app:assembleDebug`: 성공.
- Lint: app은 오류 0개, 경고 18개(1B-3과 같다). `core:security`를 포함한 나머지 모듈은 이슈가 없다.

### 남은 문제·미검증 항목

- **실제 백업 미검증**: `bmgr backupnow` 같은 실제 백업·복원 전송으로 제외가 적용되는지는 확인하지 않았다. google_apis_playstore 이미지는 루트 권한이 없어 백업 결과를 확인할 수 없다. 현재 검증 근거는 규칙 XML 테스트와 APK 매니페스트 연결 확인이다.
- **Keystore 무효화 미검증**: 실기기에서 Keystore 키가 무효화되는 경우(OS 업데이트, 잠금 화면 변경 등)의 동작은 미검증이다. 키 삭제로 흉내 낸 경우만 검증했다.
- **응답 JSON 암호화**: 응답 JSON 암호화는 응답 테이블을 추가하는 4단계에서 `DataCipher`로 적용해야 한다.
- **작업 영역 연결**: `local_session`에 현재 작업 영역을 실제로 기록하고 복원하는 흐름은 2단계(계정)와 3단계(익명 출퇴근)에서 연결한다.

## 2단계 하위 단계

| 하위 단계 | 범위 (v1.4 API) | 완료 기준 | 상태 |
| --- | --- | --- | --- |
| 2-1 | 로그인·refresh·로그아웃·세션 복원 (API-01~03, `/me` DTO) | 계약 테스트, refresh 1회·single-flight, 세션 만료 처리, DB v2 마이그레이션 | 완료 (2026-09-30) |
| 2-2 | 계정 입력 검증과 CSV 로컬 사전 검증 | RFC 4180·BOM·경계값, 서버 `CsvError` 코드와 같은 행 오류 | 다음 작업 |
| 2-3 | 어드민 계정·CSV·기기 관리 (API-05~12) | Idempotency-Key 재전송, If-Match 412/428, IMPORT_INVALID/EXPIRED | 대기 |
| 2-4 | 슈퍼바이저·어드민 할당 관리 (API-14, 16~19) | slotVersion If-Match, 동시 변경 412 후 재조회, no-op·해제 | 대기 |

## 2-1 완료 기록 (2026-09-30)

v1.4 명세를 받은 뒤 진행했다. 기존 1B 네트워크 계약 중 v1.4와 다른 부분도 이번에 함께 고쳤다.

### 구현 범위

- **네트워크 계약(v1.4)**
  - 오류 본문
    - `ApiError`에 `details`(`expectedSequence`, `currentState`, `currentVersion`, `retryAfterSeconds`, `serverTime`, `conflictingFields`)를 추가했다.
    - `FieldError`에 `message`를 추가했다.
    - `Retry-After` 헤더가 없으면 `details.retryAfterSeconds`를 쓴다.
  - 재시도 분류: `409 OPERATION_IN_PROGRESS`를 재시도 가능으로 분류하고, 405(`METHOD_NOT_ALLOWED`)와 415(`UNSUPPORTED_MEDIA_TYPE`) 분류를 추가했다.
  - 인증 헤더: 공개 API는 경로로 판단한다(`AuthPaths`: login·refresh는 Bearer 없음, logout은 Bearer 필요). 이전 `NO_AUTH` 표시 헤더는 삭제했다.
  - DTO: `AuthApi`에 login·refresh(blocking `Call`, `Idempotency-Key` 헤더)·logout(204)·me를 정의했다. `LoginResultDto`, `TokenPairDto`, `UserDto`(`resourceVersion` 정수), `MeResultDto`, `RefreshRequest`, `LogoutRequest`를 추가했다. 토큰과 비밀번호는 `toString`에서 마스킹한다.
  - `ApiClient`에 `callNoContent`(204)와 `callBlocking`(Authenticator용)을 추가했다.
  - `NetworkConfig`는 앱 버전이 `X-App-Version` 규칙을 지키는지 검사한다.
  - `TokenAuthenticator`(OkHttp Authenticator)
    - 401이 오면 `TokenRefresher`로 1회 갱신한 뒤, 새 `X-Request-ID`로 원래 요청을 1회만 재개한다.
    - 두 번째 401과 login·refresh·logout 경로에는 갱신하지 않는다.
    - refresh 자체는 Authenticator가 없는 `@Named(AUTH_CLIENT)` 클라이언트로 호출한다.
- **로그인(`NetworkAuthRepository`)**
  - 입력 사전 검증: `CredentialRules`(아이디 `^[A-Za-z0-9]{4,30}$`, 비밀번호 ASCII 12~64자·영문/숫자/구두점 포함, trim 없음)에 맞지 않으면 요청 없이 `INVALID_CREDENTIALS`를 반환한다.
  - 성공 처리
    - `grade`와 `role`이 일치하는지, 그리고 `tokenType=Bearer`인지 검사한다.
    - 토큰은 `SessionCredentials.store`로 저장한다. Refresh Token은 암호화해 저장하고 Access Token은 메모리에만 둔다.
    - 트랜잭션 안에서 이전 세션을 종료하고 ACCOUNT 세션 행을 연다. 이 행에는 loginId(소문자), 이름, serverSessionId, deviceNextSequence가 들어간다.
    - 저장에 실패하면 토큰을 지우고 `STORAGE`를 반환한다.
  - 실패 매핑(v1.4 §13): 오류 코드 `ACTIVE_DEVICE_EXISTS`와 `DEVICE_REVOKED`를 먼저 보고, 그다음 HTTP 상태를 본다.

    | 응답 | 처리 |
    | --- | --- |
    | 429 | `RATE_LIMITED`(대기 초 포함) |
    | 401·422 | `INVALID_CREDENTIALS`(계정 존재 여부 비노출) |
    | 5xx·계약 외 응답 | `SERVER_ERROR` |
    | 네트워크 오류 | `NETWORK` |
    | 주소 미설정 | `SERVER_NOT_CONFIGURED` |
- **refresh(`SessionTokenRefresher`)**
  - 모니터 락으로 single-flight 처리한다. 이미 다른 스레드가 토큰을 교체했으면 새 요청 없이 그 토큰을 반환한다.
  - `Idempotency-Key`는 `no_backup/session/refresh-idempotency-key`에 영속 저장한다. 일시 실패나 재시작 뒤에도 같은 키를 쓰고, 새 토큰 쌍을 저장하거나 세션이 끝나면 지운다.
  - 거절(400·401·403·409·422 중 재시도 불가 응답)이면 토큰을 지우고 `local_session`을 종료한다. 일시 실패(네트워크·408·429·5xx·`OPERATION_IN_PROGRESS`)면 세션과 키를 유지한다.
- **세션(`LocalSessionRepository`)**
  - 복원: 열린 ACCOUNT 행과 저장된 Refresh Token이 있어야 복원한다. 토큰이 없으면 세션을 종료한다.
  - 관찰: `observeCurrentAccount`로 현재 계정을 관찰한다.
  - 로그아웃
    - Access Token이 없으면(재시작 직후) 먼저 refresh한다. 회전된 토큰으로 API-03을 호출한다.
    - 서버 결과와 상관없이 로컬 토큰을 지우고 세션을 종료한다. 미전송 기록은 보존한다.
- **DB v2**
  - `local_session`에 `loginId`, `displayName`, `serverSessionId`, `deviceNextSequence`, `endedAt`(모두 nullable)을 추가했다.
  - `MIGRATION_1_2`는 `ALTER TABLE ADD COLUMN`만 한다. `2.json`을 내보냈고 `1.json`은 수정하지 않았다.
  - DAO에 `current`, `observeCurrent`, `endOpenSessions`를 추가했다.
- **화면·탐색**
  - 시작 화면(`StartupRoute`/`StartupViewModel`): 기기 안의 세션만 복원한다(네트워크 대기 없음). 실패하면 로그인 화면으로 간다.
  - 로그인에 성공하면 백스택 전체를 `[AccountHomeRoute]`로 교체한다(`resetTo`). 따라서 뒤로 가기로 로그인 화면에 돌아가지 않는다.
  - 계정 홈 골격: 이름, 아이디, 역할, 역할별 “다음 단계 제공 예정” 안내, 로그아웃이 있다. 세션이 사라지면(로그아웃, refresh 거절) 백스택 전체를 `[LoginRoute]`로 바꾼다.
  - 로그인 실패 문구를 추가했다: 다른 기기 연결, 기기 해제, 시도 제한(N초), 서버 오류, 저장 실패.
- **삭제**: `UnconfiguredAuthRepository`와 그 테스트를 지웠다. 주소가 없으면 `ApiClient`가 `NotConfigured`를 돌려주는 방식으로 대체했다.

### 주요 변경 파일

- network: `ApiEnvelope`, `ApiResult`, `ApiClient`, `ClientHeadersInterceptor`(`AuthPaths`), `TokenAuthenticator`(신규), `NetworkConfig`, `auth/AuthApi`, `di/NetworkModule`
- data: `NetworkAuthRepository`, `SessionTokenRefresher`, `LocalSessionRepository`, `AccountSessionStore`(신규), `SessionCredentials`, `InstallationIdStore`(`RefreshKeyStore` 포함), `di/DataModule`, `di/NetworkIdentityModule`
- database: `entity/LocalSessionEntity`, `dao/Daos`, `Migrations`(신규), `FspDatabase`(v2), `schemas/.../2.json`
- domain: `AuthRepository`(`LoginResult.Success`, `LoginFailure` 확장, `AccountSession`, `SessionRepository`), `CredentialRules`(신규)
- UI: `feature/auth/.../{LoginContract,LoginViewModel,LoginScreen,StartupEntry}`, `feature/dashboard/.../AccountHome`, `core/navigation/.../FspRoutes`, `app/.../navigation/{FspNavigation,BackStackOps}`
- 테스트
  - network: `ApiClientContractTest`, `TokenAuthenticatorTest`, `NetworkConfigTest`, `TestJson`
  - data: `NetworkAuthRepositoryTest`, `SessionTokenRefresherTest`, `LocalSessionRepositoryTest`, `SessionCredentialsTest`, `DataTestEnv`
  - domain: `CredentialRulesTest`
  - UI: `StartupViewModelTest`, `AccountHomeViewModelTest`, `LoginViewModelTest`, `BackStackOpsTest`
  - 계측: `FspDatabaseMigrationTest`, `LocalSessionDaoTest`, `FspNavigationRestorationTest`, `MainActivityFlowTest`

### 설계 결정

- **refresh 트리거**: OkHttp `Authenticator`로 구현했다. 호출 코드는 401을 신경 쓰지 않는다.
  - 재시작 직후처럼 Access Token이 없으면 첫 요청이 Bearer 없이 나가고, 401을 받은 뒤 refresh한다. 서버 요청이 한 번 늘지만 만료 시각을 계산할 필요가 없다.
  - `accessExpiresAt` 기준 선제 갱신은 하지 않았다.
- **refresh 거절 분류**: 400·401·403·409·422 중 재시도 불가 응답은 세션 종료로 본다. 해당 코드는 `INVALID_REFRESH_TOKEN`, `TOKEN_REUSE_DETECTED`, `DEVICE_REVOKED`, `ACCOUNT_DISABLED`, `DEVICE_MISMATCH` 등이다. 그 밖의 응답은 일시 실패로 보고 세션을 유지한다.
- **로그아웃은 로컬 우선**: 서버 폐기가 실패해도 로컬 토큰은 지운다. Refresh Token이 기기에 남지 않으므로 서버 세션은 만료될 때까지 쓸 수 없게 된다.
- **세션 표시 정보**: 로그인 응답의 이름·loginId를 `local_session`에 저장해 오프라인에서도 표시한다. 계정 정보를 갱신하는 흐름(`/me`)은 이후 단계에서 붙인다.
- **계정 홈 구성**: 역할별 대시보드(S03/S04)는 3~5단계 기능과 함께 만든다. 이번에는 하나의 `AccountHomeRoute`에서 역할만 표시한다.
- **최소 지원 버전**: Android 8.0(API 26)을 유지한다. v1.4 명세의 클라이언트 최소 버전 표기도 이 기준으로 수정했다(문서 머리말 참고).

### 실행한 검증과 결과

- 단위 테스트 합계 115개, 실패 0개. 오래된 결과 파일은 제외하고 모듈별로 다시 셌다.
  - domain 16개, data 35개, network 28개, security 6개, auth 13개, dashboard 9개, app 8개
  - 계약 테스트(MockWebServer, 로컬 HTTP)는 v1.4 §14.1 예시 JSON을 그대로 사용했다. 검증한 항목은 다음과 같다.
    - 로그인 요청 본문·헤더와 `X-Device-ID`=본문 deviceId
    - refresh `Idempotency-Key`·Bearer 없음, logout Bearer·204
    - 오류 `details`·`fields.message` 파싱, `OPERATION_IN_PROGRESS` 재시도 가능, `retryAfterSeconds` 대체값
    - 401 → refresh 1회 → 재개(새 요청 ID), 두 번째 401에서 반복 없음, 인증 경로 제외
    - 입력 사전 검증 시 요청 0회
    - 실패 코드 매핑, 역할 불일치 거절, 저장 실패 시 롤백
    - 일시 실패 시 같은 키 재사용(재시작 후 포함), 거절 시 세션 종료
    - 세션 복원·로그아웃(서버 실패 시 포함·재시작 후 refresh 선행)
- API 26 계측 테스트 합계 28개, 모두 통과.
  - `:core:database:connectedDebugAndroidTest` 14개: 마이그레이션 1→2(기존 세션 보존·새 컬럼 NULL), `local_session` 현재·종료 쿼리 포함
  - `:core:security:connectedDebugAndroidTest` 5개
  - `:app:connectedDebugAndroidTest` 9개: 로그인 후 백스택 교체·복원·로그아웃, 시작 화면 경유 로그인·오프라인 흐름 포함
  - 반복 실행: app 스위트 5회, security 스위트 5회 모두 통과했다.
- 계측 테스트 중 수정한 것
  - 1B-4에서 작성한 테스트 3곳이 무작위 Base64 암호문에 “ON”이나 “q1”이 우연히 포함되면 실패하는 결함이 있었다(약 1~2% 확률). 반복 실행 중 1회 실패해서 발견했다.
  - 따옴표를 포함한 평문(`"status"`, `"q1"`)으로 검사하도록 고쳤다. 따옴표는 Base64에 나올 수 없으므로 결과가 결정적이다.
  - 에뮬레이터 화면 꺼짐으로 app UI 테스트가 다시 실패한 적이 있다. 화면을 켜고 `screen_off_timeout`을 30분으로 늘린 뒤 다시 실행해 통과했다. 코드 문제가 아니다.
- `:app:assembleDebug`, 모든 계측 APK 빌드: 성공.
- Lint: app은 오류 0개, 경고 18개(이전과 같다). 나머지 모듈은 이슈가 없다.

### 남은 문제·미검증 항목

- **실서버 연동 미검증**: 서버 주소와 역할별 계정이 없어 로그인·refresh·logout은 로컬 MockWebServer로만 검증했다. TLS·실제 오류 본문·속도 제한도 미검증이다.
- **동시성 검증 범위**: refresh single-flight는 “이미 교체된 토큰 재사용” 분기를 단위 테스트로 확인했다. 실제 동시 스레드 경쟁 테스트는 하지 않았다.
- **refresh 60초 유예 이후**: 응답을 잃고 60초가 지난 뒤 같은 키로 재시도하면 서버가 `TOKEN_REUSE_DETECTED`로 세션 전체를 폐기한다(v1.4 §7.1). 이 경우 앱은 로그인 화면으로 보낸다. 명세상 피할 수 없는 동작이다.
- **`/me` 미사용**: `MeResultDto`와 API 정의만 있다. 계정 상태·deviceNextSequence 재동기화는 3단계(근태)에서 사용한다.
- **실기기 E2E 미검증**: 기기 계측 테스트는 서버 주소가 없는 빌드에서만 실행했다. 앱이 `usesCleartextTraffic=false`라서 기기에서 HTTP MockWebServer로 로그인 E2E를 하려면 TLS 테스트 서버가 필요하다.
- **기기 업그레이드 경로**: 이전 v1 APK 설치 상태에서 새 APK로 업그레이드하는 과정은 `MigrationTestHelper`와 “기존 v1 데이터 보존” 계측 테스트로 대신했다.

## 다음 단계: 2-2 시작 작업 (계정 입력 검증과 CSV 로컬 사전 검증)

서버 없이 진행할 수 있다. 서버가 최종 검증하므로(v1.4 API-10), 앱의 사전 검증은 업로드 전 안내용이다.

1. **도메인 규칙 확장**
   - `CredentialRules`를 확장한다: 이름 `^[가-힣A-Za-z]{1,50}$`(공백 불가), grade 2/3만 허용.
   - 아이디는 소문자로 정규화해 파일 안 중복(대소문자 무시)을 판단한다.
2. **RFC 4180 CSV 파서**(`core:domain`, 순수 Kotlin)
   - UTF-8과 BOM을 지원한다. 헤더는 정확히 `id,pw,name,grade` 순서다.
   - 따옴표·이스케이프·따옴표 안 줄바꿈을 처리한다.
   - 데이터 행 1~1,000개, 파일 1 MiB 한도를 둔다. 행 번호는 헤더를 1로 센다.
3. **오류 형식**: v1.4 `CsvError`와 같게 만든다.
   - 필드: `row`(1~1001), `field`(`id`/`pw`/`name`/`grade`/`row`), `code`
   - 코드: `REQUIRED`, `INVALID_FORMAT`, `INVALID_GRADE`, `DUPLICATE_IN_FILE`, `INVALID_COLUMNS`. `ID_ALREADY_EXISTS`는 서버만 판단한다.
   - 한 행에 여러 오류가 있으면 모두 반환한다. 최대 5,000개다.
   - 파일 구조를 파싱할 수 없으면 서버의 `INVALID_CSV`에 해당하는 로컬 오류로 표시한다.
4. **보안**: 비밀번호 원문은 오류·로그·`toString`에 넣지 않는다.
5. **테스트**: BOM 유무, 따옴표 안 쉼표·따옴표·줄바꿈, 1,000/1,001행 경계, 1 MiB 경계, 헤더 순서 오류, 빈 셀, grade 1, 대소문자 중복, 한 행 복수 오류를 검증한다.
6. **2-3·2-4를 위한 준비**: 아래 API의 계약을 따른다.
   - 계정: API-05~12. `Idempotency-Key`는 06~11에 붙이고, `If-Match`는 07~09에 `User.resourceVersion`으로 붙인다. CSV `commit`은 본문 없이 보낸다.
   - 할당: API-14, 16~19. `If-Match`는 `slotVersion`을 쓴다. 해제는 surveyId와 surveyVersion을 모두 null로 보내고 reason은 필수다.

필요한 외부 정보(없으면 MockWebServer 계약 테스트까지만 진행하고 실서버 연동은 미검증으로 남긴다):
- HTTPS API 기본 주소(`-Pfsp.apiBaseUrl=https://host/api/v1`)
- 역할별(1/2/3) 테스트 계정

전체 검증 명령(API 26 에뮬레이터 실행 및 화면 켜짐 상태 필요):

```powershell
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :core:network:testDebugUnitTest :core:security:testDebugUnitTest :feature:auth:testDebugUnitTest :feature:dashboard:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :core:data:lintDebug :core:database:lintDebug :core:network:lintDebug :core:security:lintDebug :core:navigation:lintDebug :feature:auth:lintDebug :feature:dashboard:lintDebug :core:database:connectedDebugAndroidTest :core:security:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

API 26 AVD 준비:

1. `avdmanager create avd -n Fsp_API26 -k "system-images;android-26;google_apis_playstore;x86" -d pixel`
2. `emulator -avd Fsp_API26 -no-window -no-audio -no-snapshot`
3. 계측 테스트 전에 `adb shell input keyevent KEYCODE_WAKEUP`, `adb shell wm dismiss-keyguard`, `adb shell svc power stayon true`를 실행한다.
