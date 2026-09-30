# 현장 설문 앱 개발 진행표

기준: fsp_api_spec.docx (현장설문앱 기획서·API 명세 v1.3) 및 참조 대화의 확정 요구사항.
최소 지원 버전은 2026-09-30 추가 요청에 따라 Android 8.0(API 26)으로 변경한다. 원본 기획서의 Android 6.0 조건보다 이 변경을 우선한다.
서버는 별도 구현 범위다. 단계별로 구현과 검증을 완료하고 진행 기록을 갱신한다.

## 개발 단계

| 단계 | 작업 | 완료 기준 | 상태 |
| --- | --- | --- | --- |
| 1A | 프로젝트 기반, 권한·시작 정책, 최초 진입 화면 | 핵심 정책 테스트, API 26 설정 빌드 | 완료 |
| 1B | MVI, Hilt, Navigation 3, Room 3, Retrofit 3 통합 | DI 생성, 탐색 복원, DB 트랜잭션·마이그레이션, HTTP 계약 테스트 | 진행 중 (1B-1·1B-2 완료, 1B-3 다음) |
| 2 | 로그인·계정·CSV·할당 관리 | 역할별 접근, CSV 원자적 등록, 할당 충돌 처리 | 대기 |
| 3 | 다운로드·오프라인 선택·반복 출퇴근 | 유효 캐시만 사용, 재실행 복원, 로컬/Outbox 원자적 저장 | 대기 |
| 4 | 3종 문항·자동 저장·제출 | 단일 선택/주관식/7점 척도, 버전 고정, 종료 후 복원 | 대기 |
| 5 | 선택 전송·현황·검수 | 멱등성, 추가 3회 재시도, 반려 revision, If-Match 충돌 | 대기 |
| 6 | 다중 화면·보안·장애 복구·현장 파일럿 | API 26/최신 OS, 태블릿/폴드, 만료·프로세스 종료 검증 | 대기 |

## 현재 구현 범위

- `app`: Hilt Application, Navigation 3 루트 백스택(`FspNavigation`), 테마.
- `core:navigation`: 직렬화 가능한 route 키(`LoginRoute`, `OfflineDashboardRoute`).
- `core:database`: Room 3 `FspDatabase` v1(`local_session`, `survey_definition`, `outbox`), DAO, `LocalWriteTransaction`, Hilt 제공.
- `core:data`: Repository 구현과 Hilt 바인딩. 설문 캐시는 Room(`RoomSurveyCacheRepository`), 로그인은 서버 미설정 구현.
- `feature:auth`: 로그인 화면 MVI(State/Intent/Effect)와 `LoginViewModel`.
- `feature:dashboard`: 계정 없는 모드 대시보드 MVI와 `OfflineDashboardViewModel`.
- `core:domain`: Android에 의존하지 않는 역할·세션·할당·설문 시작 정책, Repository 인터페이스.
- 계정 등급 1/2/3, 관리자 실제 수집 금지, 출근 조건, 인증 후 다운로드 정책.
- 로그인 계정은 본인 할당 캐시만 사용. 익명 모드는 유효 캐시를 선택.
- 온라인 신규 시작은 최종 할당 확인 필수. 실패 시 오래된 할당으로 시작 불가.
- 신규 응답 시작 시 설문·할당을 값으로 고정. 이후 할당 변경은 다음 시작에 적용.
- 익명 응답 가져오기는 조사원의 현재 할당 설문·버전 일치 여부 확인.

아직 실제 로그인, 다운로드, 출퇴근, 설문 입력은 제공하지 않는다. DB는 생성되지만 업무 데이터를 쓰는 기능은 없다.
로그인은 `UnconfiguredAuthRepository`가 네트워크 호출 없이 `SERVER_NOT_CONFIGURED` 실패를 반환한다(가짜 성공 없음).
오프라인 진입은 Room `survey_definition`을 조회한다. 다운로드 기능이 없어 테이블이 비어 있으므로 설문 없음 화면을 표시한다. 샘플 설문은 내장하지 않는다.
화면은 스크롤·키보드 여백·최대 본문 너비를 적용한 기본형이며 폴더블 힌지 대응은 6단계다.
정책 단위 테스트는 실제 DB에 저장된 응답의 프로세스 종료 복원을 검증하지 않는다.

## 1B 하위 단계

| 하위 단계 | 범위 | 완료 기준 | 상태 |
| --- | --- | --- | --- |
| 1B-1 | 멀티모듈 골격, Hilt/KSP, Navigation 3 백스택·엔트리 범위 ViewModel, MVI 전환 | Hilt 그래프 생성, MVI 단위 테스트, 백스택 복원 계측 테스트(API 26), 빌드·Lint | 완료 (2026-09-30) |
| 1B-2 | `core:database`: Room 3 SQLiteDriver, 스키마 내보내기, 캐시·세션·Outbox 트랜잭션 | 로컬 저장+Outbox 원자성·롤백 테스트, 마이그레이션 테스트, `EmptySurveyCacheRepository` 교체 | 완료 (2026-09-30) |
| 1B-3 | `core:network`: Retrofit 3, 성공/오류 envelope, 401/403/408/429/5xx·시간 제한 매핑 | MockWebServer 계약 테스트, 기본 주소 미설정 시 `SERVER_NOT_CONFIGURED` 유지 | 다음 작업 |
| 1B-4 | 익명/계정 영역 분리, 백업 제외 규칙, Keystore 암호화 경계 | 백업 규칙 검증(`fsp.db` 제외 포함), 암호화 왕복 계측 테스트(API 26) | 대기 |

1B 전체는 1B-4까지 끝나고 API 26 기기 검증을 통과한 뒤 완료로 표시하고 2단계에 진입한다.

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

- **백업**: 현재 `allowBackup=true`이고 백업 규칙이 비어 있어 `fsp.db`가 자동 백업에 포함된다. 지금은 DB에 업무 데이터를 쓰는 기능이 없다. 1B-4에서 백업 제외 규칙을 적용해야 하며, 3단계에서 근태·응답을 쓰기 전에 반드시 완료해야 한다.
- **암호화**: `outbox.payloadJson`과 앞으로 저장할 응답 JSON은 평문이다. Keystore 암호화는 1B-4 범위다.
- **스키마 범위**: `AttendanceEvent/Meta`, `Response`, `ResponseRevision`, `ReviewEvent`, `DashboardCache`, `ErrorEvent`는 아직 없다(3~5단계). 추가할 때마다 버전을 올리고 마이그레이션 테스트를 추가한다.
- **1.json 수정 금지**: 운영 배포 전이더라도 커밋된 `1.json`을 제자리에서 수정하지 않는다. 스키마를 바꾸면 버전 2와 마이그레이션으로 처리한다.
- **미검증**: 실제 프로세스 종료 후 DB 복원과 최신 OS·실기기 동작은 미검증이다(6단계).

## 다음 단계: 1B-3 시작 작업

1. `core:network` Android 라이브러리 모듈을 추가한다.
   - 의존성은 Retrofit 3.0.0(`com.squareup.retrofit2:retrofit`, `converter-kotlinx-serialization`), OkHttp 5.x, `kotlinx-serialization-json`(Kotlin 2.2 호환 버전), 테스트용 `mockwebserver3`다.
   - 버전은 Kotlin 2.2.10과의 호환성을 먼저 확인한다.
2. 기획서 11장 공통 계약의 DTO를 만든다.
   - 성공: `{data, meta{requestId, serverTime}}`
   - 실패: `{error{code, message, retryable, fields[]}, meta}`
3. HTTP 결과를 도메인 오류로 매핑한다.
   - 400, 401, 403, 404, 409, 410 RETENTION_EXPIRED, 412, 422, 428
   - 일시 실패: 408, 429(Retry-After), 5xx, 시간 초과·연결 실패
   - 재시도 횟수 정책은 5단계에서 구현하고, 이번에는 retryable 분류까지만 한다.
4. 요청 헤더 인터셉터를 만든다: `X-Request-ID`, `X-Device-ID`, `X-App-Version`, `Authorization`.
   - 릴리스 로깅에서는 본문과 인증 헤더를 비활성화한다.
5. `POST /auth/login` 인터페이스와 DTO를 만들고 MockWebServer 계약 테스트를 작성한다.
   - 기본 주소(`BuildConfig`)가 비어 있으면 기존처럼 `SERVER_NOT_CONFIGURED`를 반환하고 네트워크를 호출하지 않는다.
   - 실제 로그인 성공 흐름과 토큰 저장은 2단계와 1B-4 범위다.
6. 검증: 아래 전체 명령과 `:core:network` 테스트·Lint.

전체 검증 명령(API 26 에뮬레이터 실행 및 화면 켜짐 상태 필요):

```powershell
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :feature:auth:testDebugUnitTest :feature:dashboard:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :core:data:lintDebug :core:database:lintDebug :core:navigation:lintDebug :feature:auth:lintDebug :feature:dashboard:lintDebug :core:database:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

API 26 AVD 준비:

1. `avdmanager create avd -n Fsp_API26 -k "system-images;android-26;google_apis_playstore;x86" -d pixel`
2. `emulator -avd Fsp_API26 -no-window -no-audio -no-snapshot`
3. 계측 테스트 전에 `adb shell input keyevent KEYCODE_WAKEUP`, `adb shell wm dismiss-keyguard`, `adb shell svc power stayon true`를 실행한다.
