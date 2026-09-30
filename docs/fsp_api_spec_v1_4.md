# 현장 설문조사 앱 백엔드 API 명세서 v1.4

> AI 에이전트의 구현 기준. v1.3의 제품 요구사항을 유지하면서 요청·응답 DTO, 오류, 동시성, 동기화 및 보관 규칙을 구체화한 완성 명세다. 본문의 MUST/반드시/금지는 필수 계약이다. 실제 서버 구현·테스트 완료 보고서는 아니다.

| 항목 | 값 |
| --- | --- |
| 명세 버전 | 1.4 |
| 작성일 | 2026-09-30 |
| 기준 | `backend_api_spec_v1_3.md`, Android 전용 앱 기획 v1.3 |
| Base path | `/api/v1` |
| 전송 | HTTPS, UTF-8 JSON; CSV만 multipart |
| 업무 시간대 | Asia/Seoul |
| 클라이언트 | Android 8.0(API 26) 이상; 폰·태블릿·폴드; 오프라인 우선 저장 |
| 엔드포인트 | 33개. 프레임워크·물리 DB는 구현체 선택이며 외부 계약에 영향 없음 |
| 문서 상태 | 구현 계약 확정; 사용자에게 추가 확인할 제품 결정 없음 |

## 1. 에이전트 적용 원칙과 변경 요약

1. 이 문서만으로 API를 구현한다. v1.3의 미정의 문구와 충돌하면 v1.4가 우선한다. v1.3 DOCX는 수정하지 않았다.
2. 사용자 확정 사항: 권한 1/2/3, 로그인 후 개인별 설문 수신, 출근 상태에서 신규 시작, 하루 반복 출퇴근, 진행 중 설문 유지, 다음 신규 시작에 할당 변경 반영, 객관식 단일 선택, Android 8.0(API 26), 달력 3개월 보관, 팀 없음.
3. 이번에 정한 기술 계약: 모든 DTO의 타입·필수 여부·null, 토큰과 세션, 버전 증가, 날짜 필터, 페이지·변경 커서, 오류 코드, 중복 처리 순서, 초안·반려 revision, 일괄 처리 결과, 보관 경계. 기존 기능을 확대하지 않는다.
4. 프런트엔드는 요청 스키마를 지키고 응답의 알 수 없는 추가 필드는 무시한다. 서버 v1.4는 명세의 응답 필드를 누락하지 않는다. 요청의 알 수 없는 필드는 422로 거절한다.
5. JSON 예시는 일부 상태의 예다. 필드 표·공통 규칙·업무 불변 조건이 함께 계약을 구성한다. 예시 값이나 비밀번호를 배포 환경에 그대로 사용하지 않는다.
6. 다른 프로젝트의 서명·GPS·쿼터·웹 설문 연동·다중 프로젝트 요구사항을 혼합하지 않는다.
7. 확인이 끝난 명세이지 성능·보안·기기 테스트 결과가 아니다. §15 수용 기준을 구현 후 실행한다.

주요 형식 변경: 인증 결과는 `data.tokens`로 통일했고, 일반 오류 및 익명 항목 오류는 `ApiError`를 공유한다. 응답 작성 요청에 `baseRevision`과 `attendanceEventId`를 명시했다. 기존 v1.3 클라이언트가 있다면 이 변경을 함께 반영한다.

## 2. 범위와 권한

### 2.1 역할

| 약어 | grade | role | 범위 |
| --- | --- | --- | --- |
| A | 1 | `ADMIN` | 전체 조사원 관리, 계정·CSV·기기 연결 관리, 할당·현황·검수 |
| S | 2 | `SUPERVISOR` | 전체 조사원의 할당·현황·검수 |
| R | 3 | `INTERVIEWER` | 본인 근태·응답·할당 조회, 본인 할당 설문 다운로드 |

- `전체`는 인증된 A/S/R을 뜻한다. 비로그인 사용자를 포함하지 않는다.
- 모든 슈퍼바이저가 전체 조사원을 관리한다. 팀·담당 슈퍼바이저 연결은 없다.
- 어드민과 슈퍼바이저는 실제 설문 응답을 수집하지 않고 미리보기·검수를 수행한다.
- 권한은 토큰과 서버 데이터로 결정한다. 요청에 담긴 `grade`, 소유자 ID, 담당자 ID를 권한의 근거로 신뢰하지 않는다.
- 목록 권한과 개별 객체 접근 권한을 모두 검사한다.

### 2.2 계정 없는 오프라인 모드

- 로그인 화면에서 계정 없이 로컬 조사원 작업 영역으로 진입한다.
- 진입 시 로그인 API를 호출하거나 익명 API 토큰을 발급하지 않는다.
- 설문 다운로드는 반드시 온라인 로그인 후에만 허용한다.
- 오프라인 모드에서는 기기에 이미 다운로드된 사용 가능한 설문 중 선택한다. 최초 다운로드한 계정은 달라도 된다.
- 설문 정의만 공유하고, 로그인 계정의 응답·현황·토큰은 공유하지 않는다.
- 사용 가능한 설문이 없으면 앱에서 정확히 `설문 데이터가 없습니다`를 표시한다.
- 계정 없이 수집한 응답은 로컬에 쌓고, 조사원 로그인 후 사용자가 선택한 항목만 가져오기 API로 전송한다.
- 계정 없는 근태는 서버 근태로 전송하거나 실명 근태에 자동 합산하지 않는다.

### 2.3 이번 버전에서 제외

- 설문 문항 편집기와 설문 JSON 등록용 운영 도구의 API
- 복수 프로젝트 운영, 조사원당 복수 활성 설문
- 복수 선택, 분기, 쿼터, 무작위 보기
- 사진·음성·서명, GPS 출근 인증, 급여 계산
- 근태 정정, 승인 취소, 관리자의 응답 대리 수정
- 팀 관리, 조사원 간 기기 직접 동기화, 응답 파일 오프라인 회수
- 최초 어드민 생성 API, 어드민 승격 API, 계정 영구 삭제 API
- 설문 일괄 할당

## 3. 반드시 유지할 업무 규칙

### 3.1 할당과 새 설문 시작

| 규칙 ID | 규칙 |
| --- | --- |
| ASG-01 | 조사원당 현재 활성 할당은 최대 1개다. 미할당은 0개다. |
| ASG-02 | 할당은 설문 ID뿐 아니라 설문 버전까지 지정한다. |
| ASG-03 | 진행 중 응답은 시작 당시 설문·버전·할당과 문항 스냅샷을 유지한다. |
| ASG-04 | 할당 변경만으로 기존 응답의 이어하기·초안 저장·제출을 막지 않는다. |
| ASG-05 | 온라인 새 설문 시작마다 현재 할당을 조회한다. 변경된 정의를 다운로드·검증·로컬 저장한 뒤 할당을 다시 확인한다. |
| ASG-06 | 최종 확인 할당과 일치하는 스냅샷으로 로컬 DRAFT를 생성한 시점이 시작 경계다. 이후 변경은 그 응답에 적용하지 않는다. |
| ASG-07 | 확인·다운로드 실패 시 이전 설문으로 자동 시작하지 않는다. 재시도를 제공한다. |
| ASG-08 | 오프라인에서는 마지막 확인 할당·시각을 표시하고 유효 캐시로 시작한다. 서버 변경을 즉시 알 수 있다고 가정하지 않는다. |
| ASG-09 | 할당 해제는 본인 로그인 모드의 신규 시작 권한 해제다. 기기 캐시 삭제나 설문 자체 중지와 동일하지 않다. |
| ASG-10 | 게시된 설문 정의는 제자리에서 수정하지 않는다. 새 버전을 게시한 후 명시적으로 재할당한다. |

로그인 조사원의 온라인 새 시작 흐름:

1. 로컬 출근 `ON` 확인.
2. `GET /me/survey-assignment` 호출.
3. 할당이 없으면 새 시작 차단.
4. 필요한 설문 버전을 `GET /surveys/{id}/versions/{version}`으로 수신.
5. 스키마 검증과 로컬 저장 완료.
6. 현재 할당 재조회.
7. 동일하면 응답 ID와 설문·할당 스냅샷을 로컬 트랜잭션으로 고정하고 시작.
8. 다시 바뀌었으면 새 대상 확인 화면으로 돌아간다. 무한 반복하지 않는다.

별도 서버 `start` 엔드포인트는 제공하지 않는다. 서버 초안이 아직 없어도 최종 제출을 수용해야 한다.

### 3.2 계정 응답과 익명 응답의 차이

| 구분 | 계정으로 시작한 기존 응답 | 계정 없이 수집한 응답의 신규 가져오기 |
| --- | --- | --- |
| 사용 API | `/responses/{id}/draft`, `/responses/{id}/submit` | `/offline-response-imports` |
| 할당 기준 | 시작 당시 본인 소유의 원래 할당 | 전송 시 로그인 조사원의 현재 ACTIVE 할당 |
| 현재 할당 불일치 | 원래 할당·설문·버전이 유효하면 접수 | 설문 ID 또는 버전이 다르면 항목 실패 |
| 불일치 결과 | `SUBMITTED`, `reviewFlags`에 `ASSIGNMENT_CHANGED` | `ASSIGNMENT_MISMATCH`, `retryable=false` |
| 원래 수집자 | 인증 계정 소유권 유지 | `capturedByUserId=null` 유지 |
| 기존 성공 재전송 | 중복 생성 없이 기존 결과 | 보관 기간 내 동일 계정·원본의 기존 성공을 현재 할당 변경과 무관하게 반환 |

`ASSIGNMENT_CHANGED`는 검수 표시이며 자동 반려가 아니다. 설문 자체의 접수 금지와 보관 만료는 별도 검사다.

### 3.3 근태

- 새 설문은 출근 `ON`일 때만 시작한다.
- 같은 날 `ON → OFF → ON → OFF`를 횟수 제한 없이 허용한다.
- 자정이 되어도 자동 퇴근시키지 않는다.
- 퇴근해도 기존 임시 응답을 삭제하지 않는다.
- 클라이언트가 출근을 로컬 저장한 뒤 서버 전송이 늦을 수 있다. 서버 현재 근태만으로 과거 설문 시작 시점의 출근 여부를 단정하지 않는다. `attendanceEventId`로 시작 시 로컬 ON 이벤트를 참조하고 §8의 서버 검증을 적용한다.

## 4. 공통 HTTP·타입·검증 계약

### 4.1 헤더와 요청 형식

| 헤더 | 필수·규칙 |
| --- | --- |
| Authorization | 인증 API에서 `Bearer {accessToken}`. login·refresh만 제외 |
| Content-Type | 본문 있는 JSON: `application/json`(UTF-8). CSV: multipart/form-data + boundary |
| X-Device-ID | 모든 요청 필수, 앱 설치별 UUID v4. body의 deviceId와 항상 일치 |
| X-App-Version | 모든 요청 필수, 1~32자 `[A-Za-z0-9._+-]+` |
| X-Request-ID | 선택 UUID v4. 생략 시 서버 생성, 문법 오류 400. 요청마다 새 값 |
| Idempotency-Key | §6에 명시한 요청 필수 UUID v4. 없으면 400 MISSING_HEADER |
| If-Match | 명시한 수정 API 필수. 강한 ETag `"7"` 형식; 누락 428, 형식 오류 400, 불일치 412 |
| If-None-Match | 설문 정의 조회만 선택. 서버에서 받은 강한 ETag 1개만 지원 |

- 본문 없는 GET·commit은 body를 보내지 않는다. logout은 Refresh Token 본문이 있다.
- JSON 요청 본문 최대 1,048,576 bytes. 응답 본문은 이 요청 한도를 적용하지 않는다. 압축 요청 본문은 지원하지 않고 Content-Encoding 지정 시 415. 한도는 토큰 인증 이전에도 적용한다.
- multipart 파일 bytes 최대 1 MiB, 전체 multipart는 1 MiB+16 KiB. file 1개 외 part는 거절한다. filename/MIME을 신뢰하지 않고 UTF-8/CSV 내용으로 검증한다.
- JSON 파싱 실패·중복 JSON 키·잘못된 path UUID·헤더 오류는 400. 스키마·필드 값 위반은 422. 지원하지 않는 HTTP 메서드는 405.
- 모든 API 응답은 `Cache-Control: no-store`. 앱의 인증된 로컬 설문 저장은 별도 정책이다. 304는 유효 로컬 정의가 있을 때만 재사용한다.
- 응답에는 `X-Request-ID`를 항상 넣는다. 204/304 외 성공은 `{data,meta}`, 실패는 `{error,meta}`다. 둘을 동시에 넣지 않는다.
- `meta.serverTime`은 현재 응답 생성 시각이다. 업무 `createdAt`/`savedAt` 등과 혼동하지 않는다. 멱등 재응답도 새 meta를 생성한다.
- UUID는 canonical 소문자·하이픈 형태, 새 클라이언트 ID는 UUID v4. 문항/보기 ID는 별도 문자열이다.
- 정수는 JSON number, 안전 정수 범위 0~9,007,199,254,740,991. version/sequence는 1 이상. boolean을 정수로 받지 않는다.
- 시각은 UTC `YYYY-MM-DDTHH:mm:ssZ` 또는 밀리초 3자리 `...ss.SSSZ`; 오프셋 입력·초과 정밀도·윤초는 거절한다. 서버 출력은 밀리초 3자리로 통일한다.
- 길이는 Unicode code point 기준. 텍스트 응답은 trim/정규화하지 않고 원문 보존. 필수 여부 검사에만 Unicode 공백 제거 후 빈 값인지 확인한다.
- 이름은 `[가-힣A-Za-z]{1,50}`, ID는 영문·숫자 4~30자이고 저장/검색은 소문자로 정규화한다. name의 공백은 허용하지 않는다.
- 비밀번호는 ASCII 12~64자, 영문·숫자·ASCII 구두점 각각 1개 이상; ASCII 공백·제어문자 불가. 비밀번호 trim/정규화 금지.
- 사유와 문항/보기 제목은 공백만인 값 금지. 나머지 범위는 DTO 표를 따른다. nullable 필드는 null만 허용하며 빈 문자열로 대체하지 않는다.

### 4.2 공통 오류와 필드 경로

`ApiError`의 모든 필드는 필수다. 일반 오류의 fields는 `[]`, details 내 비해당 값은 null/빈 배열. `fields.path`는 `answers[0].value`, query는 `query.limit`, 헤더는 `headers.If-Match`처럼 표기한다. 비밀번호·응답 값·CSV 원문은 message/details에 넣지 않는다.

- 역할 자체가 불허면 403 FORBIDDEN. 존재 여부를 숨겨야 하는 다른 사용자의 개별 응답·작업·정의는 404 NOT_FOUND.
- R이 `userId` 필터로 타인을 지정하면 403. 필터 생략이면 본인만 적용한다.
- 429는 `Retry-After` 정수 초 및 details.retryAfterSeconds를 함께 반환한다. 409는 일반 자동 재시도 대상이 아니다.
- `retryable=true`는 같은 요청의 일시 오류 재시도가 유효하다는 뜻이다. 401은 false이며 인증 갱신 흐름에서 별도 처리한다.
- batch의 인증·본문 전체 구조 오류는 최상위 HTTP 오류. 정상 batch 내 개별 업무 검증 실패는 HTTP 200의 항목별 오류다.

### 4.3 목록·날짜 필터·정렬

- cursor 선택, limit 기본 50·최소 1·최대 100. 잘못된 limit를 보정하지 않고 422로 거절한다. query 중복 키·알 수 없는 query는 400 INVALID_QUERY.
- query boolean은 정확히 `true`/`false`; 정수는 10진 숫자, 배열 query 없음. q는 앞뒤 공백 제거 후 1~50자, ID/이름의 부분 일치(영문 대소문자 무시).
- 일반 목록 응답 `{items,nextCursor}`; 현황 목록은 `asOf` 추가. 빈 목록은 `[]`, 마지막 nextCursor는 null. 총 건수 필드는 제공하지 않는다.
- 일반 페이지 커서는 사용자·역할·필터·정렬·기준 시각·마지막 키를 포함한 서명된 불투명 문자열, 최초 발급 후 15분 만료. 만료는 409 CURSOR_EXPIRED. 필터·limit 변경은 새 조회로 시작한다. 타 계정/변조/다른 endpoint 커서는 400 INVALID_CURSOR.
- 일반 목록은 keyset 순회다. 첫 조회의 생성 시각 상한은 유지하나, 수정·만료·권한 변경은 매 요청 현재 기준이다. 완전한 역사 스냅샷/총합 일치는 보장하지 않는다. 삭제된 항목은 건너뛴다.
- 기본 정렬: users/interviewers=`id ASC,userId ASC`; surveys=`publishedAt DESC,surveyId ASC,version DESC`; 할당 이력=`changedAt DESC,eventId DESC`; 근태=`effectiveAt DESC,idx DESC`; responses=`startedAt DESC,responseId DESC`; 근태 현황=`userId ASC`; 진행 현황=`userId ASC,surveyId ASC,surveyVersion ASC`.
- `from` 포함, `to` 제외인 UTC 범위 `[from,to)`, 둘 모두 제공, from<to, 최대 93일. attendance는 timestamp, responses는 startedAt을 필터링한다. responses에서 둘 다 생략하면 보관 중 전체 대상. 한쪽만 있으면 422.
- 대시보드 date는 Asia/Seoul 날짜, 오늘 또는 이전 날짜만 허용. 운영 기록 생성 이전/삭제 후 자료는 없는 것으로 반환하며 과거 기록을 만들어내지 않는다.
- cursor의 asOf는 첫 조회 고정. 마지막 수신 이후 현장 상태는 알 수 없으므로 UI에 asOf/lastReceivedAt 표시.

### 4.4 멱등 처리와 If-Match 순서

1. 크기·형식·인증·현재 역할·계정/기기 유효성·객체 접근을 먼저 검사한다.
2. 범위 `(actorUserId,HTTP method,canonical path,Idempotency-Key)`의 저장 결과를 조회한다. refresh는 session family+key로 별도 처리한다.
3. fingerprint는 method/path/업무 본문을 키 순서에 무관하게 정규화한 뒤 서버 비밀키 HMAC-SHA-256. 배열 순서·null·생략은 구분, 헤더의 If-Match/추적값은 제외한다. 비밀번호 원문/일반 SHA만 저장하지 않는다.
4. 동일 key·다른 fingerprint는 409 IDEMPOTENCY_CONFLICT. 처리 중이면 409 OPERATION_IN_PROGRESS(retryable=true, Retry-After=2).
5. 성공 기록이 있으면 원래 업무 결과를 반환하고 If-Match를 다시 검사하지 않는다. 인증 철회·자원 보관 만료는 성공 캐시보다 우선한다. 생성을 중복하지 않으며 성공 HTTP는 재전송 200이다.
6. 새 작업만 If-Match와 업무 상태를 검사하고, 변경·업무 유일키·성공 결과를 한 트랜잭션으로 확정한다. 실패로 롤백한 결과는 키를 선점하지 않는다.
7. 응답/근태/검수 멱등 결과는 대상 expiresAt까지 유지. 계정 변경·할당·CSV 완료 결과는 완료 시각부터 3개월. 패스워드 reset/기기 해제의 기존 성공 재반환은 버전을 다시 증가시키지 않는다.
8. 키 보관이 끝난 요청을 다시 보내지 않는다. 앱은 수동 재시도에도 원본 ID·revision·본문 유지; 수정한 요청은 새 키 사용. 영구 업무 유일키(계정 ID 등)는 별도로 보장한다.

출퇴근·응답 revision·익명 원본은 key와 별개로 업무 식별자로 중복을 차단한다. 새 키로 같은 응답 revision을 보내도 같은 내용이면 기존 성공, 다른 내용이면 충돌이다. 반환하는 data는 해당 성공 당시 스냅샷이며 최신 상태는 GET으로 확인한다.

## 5. DTO 사전

표의 `필수=예`는 항상 키를 보내야 함을 뜻한다. `null 가능`과 키 생략 가능은 다르다. 객체는 표에 정의한 키만 허용한다. Query DTO의 타입은 query를 공통 규칙대로 파싱한 후의 타입이다. `A | B`는 둘 중 하나, 배열의 범위는 항목 수다. UUID/UTC/date에는 공통 형식 검증을 적용한다.

### 5.1 `Meta`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `requestId` | UUID | 예 |
| `serverTime` | UTC timestamp | 예 |

### 5.2 `FieldError`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `path` | string(1~256자) | 예 |
| `code` | "REQUIRED" / "UNKNOWN_FIELD" / "INVALID_TYPE" / "INVALID_FORMAT" / "OUT_OF_RANGE" / "DUPLICATE" / "INVALID_VALUE" / "TOO_LONG" | 예 |
| `message` | string(1~300자) | 예 |

### 5.3 `ErrorDetails`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `expectedSequence` | integer(1~9007199254740991) 또는 null | 예 |
| `currentState` | "ON" / "OFF" 또는 null | 예 |
| `currentVersion` | integer(1~9007199254740991) 또는 null | 예 |
| `retryAfterSeconds` | integer(0~9007199254740991) 또는 null | 예 |
| `serverTime` | UTC timestamp 또는 null | 예 |
| `conflictingFields` | string(1~64자)[] (0~20개) | 예 |

전 필드는 항상 존재한다. 해당하지 않는 값은 null, conflictingFields는 빈 배열. 다른 계정 ID·본문은 넣지 않는다.

### 5.4 `ApiError`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `code` | string(1~64자) | 예 |
| `message` | string(1~500자) | 예 |
| `retryable` | boolean | 예 |
| `fields` | FieldError[] (0~1000개) | 예 |
| `details` | ErrorDetails | 예 |

### 5.5 `ErrorEnvelope`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `error` | ApiError | 예 |
| `meta` | Meta | 예 |

### 5.6 `User`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `userId` | UUID | 예 |
| `id` | string(4~30자); regex `^[A-Za-z0-9]+$` | 예 |
| `name` | string(1~50자); regex `^[가-힣A-Za-z]+$` | 예 |
| `grade` | 1 / 2 / 3 | 예 |
| `role` | "ADMIN" / "SUPERVISOR" / "INTERVIEWER" | 예 |
| `active` | boolean | 예 |
| `resourceVersion` | integer(1~9007199254740991) | 예 |

### 5.7 `TokenPair`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `tokenType` | "Bearer" | 예 |
| `accessToken` | string(43~43자); regex `^[A-Za-z0-9_-]{43}$` | 예 |
| `expiresIn` | 900 | 예 |
| `accessExpiresAt` | UTC timestamp | 예 |
| `refreshToken` | string(43~43자); regex `^[A-Za-z0-9_-]{43}$` | 예 |
| `refreshExpiresIn` | integer(1~2592000) | 예 |
| `refreshExpiresAt` | UTC timestamp | 예 |
| `sessionId` | UUID | 예 |
| `credentialVersion` | integer(1~9007199254740991) | 예 |

### 5.8 `LoginResult`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `tokens` | TokenPair | 예 |
| `user` | User | 예 |
| `activeDeviceId` | UUID 또는 null | 예 |
| `deviceNextSequence` | integer(1~9007199254740991) 또는 null | 예 |

R은 activeDeviceId=현재 기기, deviceNextSequence=다음 근태 sequence. S/A는 두 값 모두 null. v1.3의 토큰 평면 필드를 tokens로 통일했다.

### 5.9 `AnonymousImportPolicy`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `enabled` | boolean | 예 |
| `requiresCurrentAssignment` | true | 예 |
| `requiresExactSurveyVersion` | true | 예 |
| `requiresExplicitSelection` | true | 예 |
| `maxItemsPerRequest` | 20 | 예 |
| `maxBodyBytes` | 1048576 | 예 |

### 5.10 `MeResult`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `user` | User | 예 |
| `credentialVersion` | integer(1~9007199254740991) | 예 |
| `activeDeviceId` | UUID 또는 null | 예 |
| `deviceNextSequence` | integer(1~9007199254740991) 또는 null | 예 |
| `anonymousImportPolicy` | AnonymousImportPolicy | 예 |

### 5.11 `PasswordResetResult`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `userId` | UUID | 예 |
| `credentialVersion` | integer(1~9007199254740991) | 예 |
| `resourceVersion` | integer(1~9007199254740991) | 예 |
| `sessionsRevoked` | true | 예 |

### 5.12 `DeviceReleaseResult`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `user` | User | 예 |
| `activeDeviceId` | null | 예 |
| `releasedDeviceId` | UUID | 예 |
| `releasedAt` | UTC timestamp | 예 |
| `sessionsRevoked` | true | 예 |

### 5.13 `LoginRequest`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `id` | string(4~30자); regex `^[A-Za-z0-9]+$` | 예 |
| `pw` | string(12~64자); regex `^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9])[!-~]+$` | 예 |
| `deviceId` | UUID | 예 |

### 5.14 `RefreshRequest`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `refreshToken` | string(43~43자); regex `^[A-Za-z0-9_-]{43}$` | 예 |
| `deviceId` | UUID | 예 |

### 5.15 `LogoutRequest`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `refreshToken` | string(43~43자); regex `^[A-Za-z0-9_-]{43}$` | 예 |

### 5.16 `CreateUserRequest`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `id` | string(4~30자); regex `^[A-Za-z0-9]+$` | 예 |
| `pw` | string(12~64자); regex `^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9])[!-~]+$` | 예 |
| `name` | string(1~50자); regex `^[가-힣A-Za-z]+$` | 예 |
| `grade` | 2 / 3 | 예 |

### 5.17 `PatchUserRequest`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `name` | string(1~50자); regex `^[가-힣A-Za-z]+$` | 아니요 |
| `active` | boolean | 아니요 |

최소 한 필드가 필요하다. null, 빈 객체, grade 변경은 거절한다.

### 5.18 `PasswordResetRequest`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `newPassword` | string(12~64자); regex `^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9])[!-~]+$` | 예 |

### 5.19 `DeviceReleaseRequest`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `deviceId` | UUID | 예 |
| `reason` | string(1~1000자) | 예 |

### 5.20 `UsersQuery`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `q` | string(1~50자) | 아니요 |
| `role` | "ADMIN" / "SUPERVISOR" / "INTERVIEWER" | 아니요 |
| `active` | boolean | 아니요 |
| `cursor` | string(1~2048자) | 아니요 |
| `limit` | integer(1~100) | 아니요 |

### 5.21 `PageQuery`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `cursor` | string(1~2048자) | 아니요 |
| `limit` | integer(1~100) | 아니요 |

### 5.22 `InterviewerQuery`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `q` | string(1~50자) | 아니요 |
| `cursor` | string(1~2048자) | 아니요 |
| `limit` | integer(1~100) | 아니요 |

### 5.23 `CsvError`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `row` | integer(1~1001) | 예 |
| `field` | "id" / "pw" / "name" / "grade" / "row" | 예 |
| `code` | "REQUIRED" / "INVALID_FORMAT" / "INVALID_GRADE" / "DUPLICATE_IN_FILE" / "ID_ALREADY_EXISTS" / "INVALID_COLUMNS" | 예 |
| `message` | string(1~300자) | 예 |

### 5.24 `CreatedUserRow`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `row` | integer(2~1001) | 예 |
| `userId` | UUID | 예 |
| `id` | string(4~30자); regex `^[A-Za-z0-9]+$` | 예 |
| `grade` | 2 / 3 | 예 |

### 5.25 `ImportValidation`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `importId` | UUID | 예 |
| `status` | "VALID" / "INVALID" / "EXPIRED" | 예 |
| `totalRows` | integer(1~1000) | 예 |
| `validRows` | integer(0~1000) | 예 |
| `expiresAt` | UTC timestamp | 예 |
| `errors` | CsvError[] (0~5000개) | 예 |

### 5.26 `ImportResult`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `importId` | UUID | 예 |
| `status` | "COMPLETED" | 예 |
| `createdCount` | integer(1~1000) | 예 |
| `users` | CreatedUserRow[] (1~1000개) | 예 |
| `completedAt` | UTC timestamp | 예 |
| `resultExpiresAt` | UTC timestamp | 예 |

### 5.27 `ImportStatusResult`

타입: ImportValidation 또는 ImportResult. type 값으로 구분하며 한 변형만 만족해야 한다.

### 5.28 `Assignment`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `assignmentId` | UUID | 예 |
| `userId` | UUID | 예 |
| `surveyId` | UUID | 예 |
| `surveyVersion` | integer(1~9007199254740991) | 예 |
| `assignmentRevision` | integer(1~9007199254740991) | 예 |
| `status` | "ACTIVE" / "REPLACED" / "REVOKED" | 예 |
| `assignedBy` | UUID | 예 |
| `assignedAt` | UTC timestamp | 예 |
| `endedAt` | UTC timestamp 또는 null | 예 |

### 5.29 `AssignmentResult`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `assignment` | Assignment 또는 null | 예 |
| `slotVersion` | integer(1~9007199254740991) | 예 |

### 5.30 `AssignmentRequest`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `surveyId` | UUID 또는 null | 예 |
| `surveyVersion` | integer(1~9007199254740991) 또는 null | 예 |
| `reason` | string(1~1000자) | 예 |

ID와 버전은 둘 다 null이거나 둘 다 값이 있어야 한다. reason은 공백만 불가.

### 5.31 `AssignmentAudit`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `eventId` | UUID | 예 |
| `userId` | UUID | 예 |
| `beforeAssignmentId` | UUID 또는 null | 예 |
| `afterAssignmentId` | UUID 또는 null | 예 |
| `beforeAssignment` | Assignment 또는 null | 예 |
| `afterAssignment` | Assignment 또는 null | 예 |
| `actorId` | UUID | 예 |
| `reason` | string(1~1000자) | 예 |
| `changedAt` | UTC timestamp | 예 |

### 5.32 `InterviewerSummary`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `userId` | UUID | 예 |
| `id` | string(4~30자); regex `^[A-Za-z0-9]+$` | 예 |
| `name` | string(1~50자); regex `^[가-힣A-Za-z]+$` | 예 |
| `active` | boolean | 예 |
| `currentAssignment` | Assignment 또는 null | 예 |

### 5.33 `SurveySummary`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `surveyId` | UUID | 예 |
| `title` | string(1~200자) | 예 |
| `version` | integer(1~9007199254740991) | 예 |
| `status` | "PUBLISHED" / "SUSPENDED" / "CLOSED" | 예 |
| `publishedAt` | UTC timestamp | 예 |

### 5.34 `SurveyQuery`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `status` | "PUBLISHED" / "SUSPENDED" / "CLOSED" | 아니요 |
| `cursor` | string(1~2048자) | 아니요 |
| `limit` | integer(1~100) | 아니요 |

status 생략 시 PUBLISHED. CLOSED/SUSPENDED 조회는 검토용이다.

### 5.35 `Option`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `id` | string(1~64자); regex `^[A-Za-z0-9_-]+$` | 예 |
| `label` | string(1~500자) | 예 |

### 5.36 `ChoiceQuestion`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `id` | string(1~64자); regex `^[A-Za-z0-9_-]+$` | 예 |
| `required` | boolean | 예 |
| `title` | string(1~2000자) | 예 |
| `type` | "SINGLE_CHOICE" | 예 |
| `options` | Option[] (2~5개) | 예 |

### 5.37 `TextValidation`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `maxLength` | integer(1~2000) | 예 |

### 5.38 `TextQuestion`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `id` | string(1~64자); regex `^[A-Za-z0-9_-]+$` | 예 |
| `required` | boolean | 예 |
| `title` | string(1~2000자) | 예 |
| `type` | "TEXT" | 예 |
| `validation` | TextValidation | 예 |

### 5.39 `ScaleLabels`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `1` | string(1~100자) | 예 |
| `7` | string(1~100자) | 예 |

### 5.40 `ScaleQuestion`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `id` | string(1~64자); regex `^[A-Za-z0-9_-]+$` | 예 |
| `required` | boolean | 예 |
| `title` | string(1~2000자) | 예 |
| `type` | "SCALE_7" | 예 |
| `min` | 1 | 예 |
| `max` | 7 | 예 |
| `labels` | ScaleLabels | 예 |

### 5.41 `Question`

타입: ChoiceQuestion 또는 TextQuestion 또는 ScaleQuestion. type 값으로 구분하며 한 변형만 만족해야 한다.

### 5.42 `SurveyDefinition`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `surveyId` | UUID | 예 |
| `version` | integer(1~9007199254740991) | 예 |
| `schemaVersion` | 1 | 예 |
| `title` | string(1~200자) | 예 |
| `status` | "PUBLISHED" / "SUSPENDED" / "CLOSED" | 예 |
| `publishedAt` | UTC timestamp | 예 |
| `questions` | Question[] (1~200개) | 예 |

### 5.43 `ChoiceAnswer`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `questionId` | string(1~64자); regex `^[A-Za-z0-9_-]+$` | 예 |
| `type` | "SINGLE_CHOICE" | 예 |
| `value` | string(1~64자); regex `^[A-Za-z0-9_-]+$` 또는 null | 예 |

### 5.44 `TextAnswer`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `questionId` | string(1~64자); regex `^[A-Za-z0-9_-]+$` | 예 |
| `type` | "TEXT" | 예 |
| `value` | string(0~2000자) 또는 null | 예 |

### 5.45 `ScaleAnswer`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `questionId` | string(1~64자); regex `^[A-Za-z0-9_-]+$` | 예 |
| `type` | "SCALE_7" | 예 |
| `value` | integer(1~7) 또는 null | 예 |

### 5.46 `Answer`

타입: ChoiceAnswer 또는 TextAnswer 또는 ScaleAnswer. type 값으로 구분하며 한 변형만 만족해야 한다.

### 5.47 `DraftRequest`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `assignmentId` | UUID | 예 |
| `assignmentRevision` | integer(1~9007199254740991) | 예 |
| `surveyId` | UUID | 예 |
| `surveyVersion` | integer(1~9007199254740991) | 예 |
| `assignmentCheckedAt` | UTC timestamp 또는 null | 예 |
| `assignmentCheckMode` | "ONLINE_VERIFIED" / "OFFLINE_CACHED" 또는 null | 예 |
| `startedAt` | UTC timestamp | 예 |
| `attendanceEventId` | UUID 또는 null | 예 |
| `revision` | integer(1~9007199254740991) | 예 |
| `baseRevision` | integer(1~9007199254740991) 또는 null | 예 |
| `draftVersion` | integer(1~9007199254740991) | 예 |
| `answers` | Answer[] (0~200개) | 예 |

### 5.48 `SubmitRequest`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `assignmentId` | UUID | 예 |
| `assignmentRevision` | integer(1~9007199254740991) | 예 |
| `surveyId` | UUID | 예 |
| `surveyVersion` | integer(1~9007199254740991) | 예 |
| `assignmentCheckedAt` | UTC timestamp 또는 null | 예 |
| `assignmentCheckMode` | "ONLINE_VERIFIED" / "OFFLINE_CACHED" 또는 null | 예 |
| `startedAt` | UTC timestamp | 예 |
| `attendanceEventId` | UUID 또는 null | 예 |
| `revision` | integer(1~9007199254740991) | 예 |
| `baseRevision` | integer(1~9007199254740991) 또는 null | 예 |
| `completedAt` | UTC timestamp | 예 |
| `answers` | Answer[] (1~200개) | 예 |

### 5.49 `StoredDraft`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `revision` | integer(1~9007199254740991) | 예 |
| `baseRevision` | integer(1~9007199254740991) 또는 null | 예 |
| `draftVersion` | integer(1~9007199254740991) | 예 |
| `answers` | Answer[] (0~200개) | 예 |
| `updatedAt` | UTC timestamp | 예 |

### 5.50 `DraftResult`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `responseId` | UUID | 예 |
| `revision` | integer(1~9007199254740991) | 예 |
| `draftRevision` | integer(1~9007199254740991) | 예 |
| `draftVersion` | integer(1~9007199254740991) | 예 |
| `resourceVersion` | integer(1~9007199254740991) | 예 |
| `status` | "DRAFT" / "RETURNED" | 예 |
| `savedAt` | UTC timestamp | 예 |
| `expiresAt` | UTC timestamp | 예 |
| `reviewFlags` | "ASSIGNMENT_CHANGED" / "ATTENDANCE_UNVERIFIED" / "CLOCK_SKEW"[] (0~3개) | 예 |

### 5.51 `ReviewRequest`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `revision` | integer(1~9007199254740991) | 예 |
| `decision` | "APPROVE" / "RETURN" | 예 |
| `reason` | string(1~1000자) 또는 null | 아니요 |
| `questionIds` | string(1~64자); regex `^[A-Za-z0-9_-]+$`[] (0~200개) | 아니요 |

RETURN은 reason 필수·공백만 불가. APPROVE 생략 reason은 null, questionIds 생략은 []. 중복 questionIds 거절.

### 5.52 `ReviewResult`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `reviewId` | UUID | 예 |
| `responseId` | UUID | 예 |
| `revision` | integer(1~9007199254740991) | 예 |
| `decision` | "APPROVE" / "RETURN" | 예 |
| `reason` | string(1~1000자) 또는 null | 예 |
| `questionIds` | string(1~64자); regex `^[A-Za-z0-9_-]+$`[] (0~200개) | 예 |
| `reviewerId` | UUID | 예 |
| `reviewedAt` | UTC timestamp | 예 |
| `resourceVersion` | integer(1~9007199254740991) | 예 |

### 5.53 `RevisionSummary`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `revision` | integer(1~9007199254740991) | 예 |
| `submittedAt` | UTC timestamp | 예 |
| `completedAt` | UTC timestamp | 예 |
| `status` | "SUBMITTED" / "RETURNED" / "APPROVED" | 예 |

### 5.54 `ResponseSummary`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `responseId` | UUID | 예 |
| `ownerUserId` | UUID | 예 |
| `ownerName` | string(1~50자); regex `^[가-힣A-Za-z]+$` | 예 |
| `surveyId` | UUID | 예 |
| `surveyTitle` | string(1~200자) | 예 |
| `surveyVersion` | integer(1~9007199254740991) | 예 |
| `revision` | integer(1~9007199254740991) | 예 |
| `resourceVersion` | integer(1~9007199254740991) | 예 |
| `status` | "DRAFT" / "SUBMITTED" / "RETURNED" / "APPROVED" | 예 |
| `collectionMode` | "AUTHENTICATED" / "ANONYMOUS_OFFLINE" | 예 |
| `capturedByUserId` | UUID 또는 null | 예 |
| `importedByUserId` | UUID 또는 null | 예 |
| `startedAt` | UTC timestamp | 예 |
| `completedAt` | UTC timestamp 또는 null | 예 |
| `firstSubmittedAt` | UTC timestamp 또는 null | 예 |
| `lastSubmittedAt` | UTC timestamp 또는 null | 예 |
| `serverReceivedAt` | UTC timestamp | 예 |
| `updatedAt` | UTC timestamp | 예 |
| `expiresAt` | UTC timestamp | 예 |
| `reviewFlags` | "ASSIGNMENT_CHANGED" / "ATTENDANCE_UNVERIFIED" / "CLOCK_SKEW"[] (0~3개) | 예 |
| `hasWorkingDraft` | boolean | 예 |

### 5.55 `ResponseDetail`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `responseId` | UUID | 예 |
| `ownerUserId` | UUID | 예 |
| `ownerName` | string(1~50자); regex `^[가-힣A-Za-z]+$` | 예 |
| `surveyId` | UUID | 예 |
| `surveyTitle` | string(1~200자) | 예 |
| `surveyVersion` | integer(1~9007199254740991) | 예 |
| `revision` | integer(1~9007199254740991) | 예 |
| `resourceVersion` | integer(1~9007199254740991) | 예 |
| `status` | "DRAFT" / "SUBMITTED" / "RETURNED" / "APPROVED" | 예 |
| `collectionMode` | "AUTHENTICATED" / "ANONYMOUS_OFFLINE" | 예 |
| `capturedByUserId` | UUID 또는 null | 예 |
| `importedByUserId` | UUID 또는 null | 예 |
| `startedAt` | UTC timestamp | 예 |
| `completedAt` | UTC timestamp 또는 null | 예 |
| `firstSubmittedAt` | UTC timestamp 또는 null | 예 |
| `lastSubmittedAt` | UTC timestamp 또는 null | 예 |
| `serverReceivedAt` | UTC timestamp | 예 |
| `updatedAt` | UTC timestamp | 예 |
| `expiresAt` | UTC timestamp | 예 |
| `reviewFlags` | "ASSIGNMENT_CHANGED" / "ATTENDANCE_UNVERIFIED" / "CLOCK_SKEW"[] (0~3개) | 예 |
| `hasWorkingDraft` | boolean | 예 |
| `assignmentId` | UUID | 예 |
| `assignmentRevision` | integer(1~9007199254740991) | 예 |
| `assignmentCheckedAt` | UTC timestamp 또는 null | 예 |
| `assignmentCheckMode` | "ONLINE_VERIFIED" / "OFFLINE_CACHED" 또는 null | 예 |
| `attendanceEventId` | UUID 또는 null | 예 |
| `localResponseId` | UUID 또는 null | 예 |
| `localSessionId` | UUID 또는 null | 예 |
| `answers` | Answer[] (0~200개) | 예 |
| `surveyDefinition` | SurveyDefinition | 예 |
| `draft` | StoredDraft 또는 null | 예 |
| `reviews` | ReviewResult[] (0~100000개) | 예 |
| `revisions` | RevisionSummary[] (0~100000개) | 예 |

### 5.56 `ResponseQuery`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `status` | "DRAFT" / "SUBMITTED" / "RETURNED" / "APPROVED" | 아니요 |
| `userId` | UUID | 아니요 |
| `surveyId` | UUID | 아니요 |
| `from` | UTC timestamp | 아니요 |
| `to` | UTC timestamp | 아니요 |
| `cursor` | string(1~2048자) | 아니요 |
| `limit` | integer(1~100) | 아니요 |

### 5.57 `ResponseDetailQuery`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `revision` | integer(1~9007199254740991) | 아니요 |

### 5.58 `TimeWarning`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `code` | "FUTURE_WITHIN_TOLERANCE" / "CLOCK_MOVED_BACKWARD" | 예 |
| `observedAt` | UTC timestamp | 예 |
| `referenceTime` | UTC timestamp | 예 |
| `differenceSeconds` | integer(-9007199254740991~9007199254740991) | 예 |

### 5.59 `AttendanceRequest`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `idx` | UUID | 예 |
| `status` | "ON" / "OFF" | 예 |
| `timestamp` | UTC timestamp | 예 |
| `deviceId` | UUID | 예 |
| `sequence` | integer(1~9007199254740991) | 예 |
| `zoneId` | "Asia/Seoul" | 예 |

### 5.60 `EventResult`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `idx` | UUID | 예 |
| `status` | "ON" / "OFF" | 예 |
| `timestamp` | UTC timestamp | 예 |
| `userId` | UUID | 예 |
| `deviceId` | UUID | 예 |
| `sequence` | integer(1~9007199254740991) | 예 |
| `serverReceivedAt` | UTC timestamp | 예 |
| `effectiveState` | "ON" / "OFF" | 예 |
| `effectiveAt` | UTC timestamp | 예 |
| `expiresAt` | UTC timestamp | 예 |
| `conflict` | false | 예 |
| `timeWarning` | TimeWarning 또는 null | 예 |

### 5.61 `AttendanceRow`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `userId` | UUID | 예 |
| `name` | string(1~50자); regex `^[가-힣A-Za-z]+$` | 예 |
| `active` | boolean | 예 |
| `date` | date (YYYY-MM-DD) | 예 |
| `state` | "ON" / "OFF" / "UNKNOWN" | 예 |
| `lastEventAt` | UTC timestamp 또는 null | 예 |
| `lastReceivedAt` | UTC timestamp 또는 null | 예 |
| `stateAsOf` | UTC timestamp | 예 |

### 5.62 `ProgressRow`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `userId` | UUID | 예 |
| `name` | string(1~50자); regex `^[가-힣A-Za-z]+$` | 예 |
| `surveyId` | UUID | 예 |
| `surveyTitle` | string(1~200자) | 예 |
| `surveyVersion` | integer(1~9007199254740991) | 예 |
| `date` | date (YYYY-MM-DD) | 예 |
| `draftCount` | integer(0~9007199254740991) | 예 |
| `submittedTotal` | integer(0~9007199254740991) | 예 |
| `pendingReviewCount` | integer(0~9007199254740991) | 예 |
| `approvedCount` | integer(0~9007199254740991) | 예 |
| `returnedCount` | integer(0~9007199254740991) | 예 |
| `lastReceivedAt` | UTC timestamp 또는 null | 예 |

### 5.63 `DashboardMe`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `date` | date (YYYY-MM-DD) | 예 |
| `attendance` | AttendanceRow | 예 |
| `progress` | ProgressRow[] (0~100000개) | 예 |
| `currentAssignment` | AssignmentResult | 예 |
| `asOf` | UTC timestamp | 예 |

### 5.64 `AttendanceQuery`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `from` | UTC timestamp | 예 |
| `to` | UTC timestamp | 예 |
| `userId` | UUID | 아니요 |
| `cursor` | string(1~2048자) | 아니요 |
| `limit` | integer(1~100) | 아니요 |

### 5.65 `DashboardMeQuery`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `date` | date (YYYY-MM-DD) | 예 |
| `surveyId` | UUID | 아니요 |

### 5.66 `AttendanceDashboardQuery`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `date` | date (YYYY-MM-DD) | 예 |
| `q` | string(1~50자) | 아니요 |
| `status` | "ON" / "OFF" / "UNKNOWN" | 아니요 |
| `cursor` | string(1~2048자) | 아니요 |
| `limit` | integer(1~100) | 아니요 |

### 5.67 `ProgressDashboardQuery`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `date` | date (YYYY-MM-DD) | 예 |
| `q` | string(1~50자) | 아니요 |
| `surveyId` | UUID | 아니요 |
| `cursor` | string(1~2048자) | 아니요 |
| `limit` | integer(1~100) | 아니요 |

### 5.68 `ChangeItem`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `entityType` | "ASSIGNMENT" / "SURVEY" / "RESPONSE" / "USER" | 예 |
| `entityId` | UUID | 예 |
| `surveyVersion` | integer(1~9007199254740991) 또는 null | 예 |
| `resourceVersion` | integer(1~9007199254740991) | 예 |
| `changeType` | "UPSERT" / "DELETE" | 예 |
| `changedAt` | UTC timestamp | 예 |

### 5.69 `SyncResult`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `items` | ChangeItem[] (0~100개) | 예 |
| `nextCursor` | string(1~2048자) | 예 |
| `hasMore` | boolean | 예 |
| `serverTime` | UTC timestamp | 예 |

### 5.70 `OfflineItem`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `localResponseId` | UUID | 예 |
| `deviceId` | UUID | 예 |
| `localSessionId` | UUID | 예 |
| `surveyId` | UUID | 예 |
| `surveyVersion` | integer(1~9007199254740991) | 예 |
| `startedAt` | UTC timestamp | 예 |
| `completedAt` | UTC timestamp | 예 |
| `answers` | Answer[] (1~200개) | 예 |

### 5.71 `OfflineImportRequest`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `operationId` | UUID | 예 |
| `items` | OfflineItem[] (1~20개) | 예 |

### 5.72 `OfflineItemResult`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `localResponseId` | UUID | 예 |
| `status` | "PENDING" / "IMPORTED" / "ALREADY_IMPORTED" / "FAILED" | 예 |
| `responseId` | UUID 또는 null | 예 |
| `ownerUserId` | UUID 또는 null | 예 |
| `revision` | integer(1~9007199254740991) 또는 null | 예 |
| `resourceVersion` | integer(1~9007199254740991) 또는 null | 예 |
| `expiresAt` | UTC timestamp 또는 null | 예 |
| `error` | ApiError 또는 null | 예 |

IMPORTED/ALREADY_IMPORTED만 responseId·ownerUserId·revision·resourceVersion·expiresAt에 값, error=null. FAILED는 이 값 모두 null, error 필수. PENDING은 값과 error 모두 null.

### 5.73 `OfflineImportResult`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `operationId` | UUID | 예 |
| `status` | "PROCESSING" / "COMPLETED" | 예 |
| `createdAt` | UTC timestamp | 예 |
| `completedAt` | UTC timestamp 또는 null | 예 |
| `resultExpiresAt` | UTC timestamp | 예 |
| `results` | OfflineItemResult[] (1~20개) | 예 |

### 5.74 `ErrorEvent`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `eventId` | UUID | 예 |
| `occurredAt` | UTC timestamp | 예 |
| `code` | string(1~64자); regex `^[A-Z0-9_]+$` | 예 |
| `severity` | "WARNING" / "ERROR" / "FATAL" | 예 |
| `screenId` | string(1~64자); regex `^[A-Za-z0-9_.-]+$` | 예 |
| `appVersion` | string(1~32자) | 예 |
| `osVersion` | string(1~32자) | 예 |
| `requestId` | UUID 또는 null | 예 |
| `sanitizedStack` | string(0~8192자) 또는 null | 예 |
| `networkState` | "ONLINE" / "OFFLINE" / "UNKNOWN" | 예 |
| `sessionMode` | "AUTHENTICATED" / "ANONYMOUS_OFFLINE" / "UNAUTHENTICATED" | 예 |

### 5.75 `ErrorBatchRequest`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `events` | ErrorEvent[] (1~20개) | 예 |

### 5.76 `ErrorRejection`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `eventId` | UUID | 예 |
| `code` | "VALIDATION_FAILED" / "RETENTION_EXPIRED" / "EVENT_ID_CONFLICT" / "SENSITIVE_DATA_REJECTED" / "INTERNAL_ERROR" | 예 |
| `retryable` | boolean | 예 |

### 5.77 `ErrorBatchResult`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `acceptedIds` | UUID[] (0~20개) | 예 |
| `rejected` | ErrorRejection[] (0~20개) | 예 |

### 5.78 `UserList`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `items` | User[] (0~100개) | 예 |
| `nextCursor` | string(1~2048자) 또는 null | 예 |

### 5.79 `InterviewerList`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `items` | InterviewerSummary[] (0~100개) | 예 |
| `nextCursor` | string(1~2048자) 또는 null | 예 |

### 5.80 `SurveyList`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `items` | SurveySummary[] (0~100개) | 예 |
| `nextCursor` | string(1~2048자) 또는 null | 예 |

### 5.81 `AssignmentHistory`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `items` | AssignmentAudit[] (0~100개) | 예 |
| `nextCursor` | string(1~2048자) 또는 null | 예 |

### 5.82 `AttendanceList`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `items` | EventResult[] (0~100개) | 예 |
| `nextCursor` | string(1~2048자) 또는 null | 예 |

### 5.83 `ResponseList`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `items` | ResponseSummary[] (0~100개) | 예 |
| `nextCursor` | string(1~2048자) 또는 null | 예 |

### 5.84 `AttendanceDashboard`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `items` | AttendanceRow[] (0~100개) | 예 |
| `nextCursor` | string(1~2048자) 또는 null | 예 |
| `asOf` | UTC timestamp | 예 |

### 5.85 `ProgressDashboard`

| 필드 | 타입·제한 | 필수 |
| --- | --- | --- |
| `items` | ProgressRow[] (0~100개) | 예 |
| `nextCursor` | string(1~2048자) 또는 null | 예 |
| `asOf` | UTC timestamp | 예 |

## 6. 엔드포인트별 계약

각 응답 DTO는 성공 envelope의 `data` 값이다. 공통 헤더는 전 API에 적용하고 아래 표에는 추가 헤더만 쓴다. 빈 요청·query는 `없음`이다. 경로의 id/userId/importId/operationId는 UUID, version은 양의 정수다.

ETag: User 조회를 반환하는 API-06/07/09 및 `/me`는 User.resourceVersion; API-13/17/18은 slotVersion; API-26/27/29/30은 응답 resourceVersion을 반환한다. User 목록 각 항목의 resourceVersion도 If-Match 작성에 사용 가능하다. 설문 ETag는 정의 JSON+현재 운영 상태의 SHA-256 hex를 따옴표로 감싼 값이다. 304는 API-15에만 적용한다.

| ID | Method | Path | 권한 |
| --- | --- | --- | --- |
| API-01 | POST | `/auth/login` | 공개 |
| API-02 | POST | `/auth/refresh` | Refresh Token |
| API-03 | POST | `/auth/logout` | 전체 |
| API-04 | GET | `/me` | 전체 |
| API-05 | GET | `/users` | A |
| API-06 | POST | `/users` | A |
| API-07 | PATCH | `/users/{userId}` | A |
| API-08 | POST | `/users/{userId}/password-reset` | A |
| API-09 | POST | `/users/{userId}/device-release` | A |
| API-10 | POST | `/user-imports/validate` | A |
| API-11 | POST | `/user-imports/{importId}/commit` | A |
| API-12 | GET | `/user-imports/{importId}` | A |
| API-13 | GET | `/me/survey-assignment` | R |
| API-14 | GET | `/surveys` | S/A |
| API-15 | GET | `/surveys/{id}/versions/{version}` | 전체+객체 권한 |
| API-16 | GET | `/interviewers` | S/A |
| API-17 | GET | `/interviewers/{userId}/survey-assignment` | S/A |
| API-18 | PUT | `/interviewers/{userId}/survey-assignment` | S/A |
| API-19 | GET | `/interviewers/{userId}/survey-assignment-history` | S/A |
| API-20 | GET | `/sync/changes` | 전체 |
| API-21 | POST | `/attendance-events` | R |
| API-22 | GET | `/attendance-events` | 전체; R 본인 |
| API-23 | GET | `/dashboards/me` | R |
| API-24 | GET | `/dashboards/attendance` | S/A |
| API-25 | GET | `/dashboards/progress` | S/A |
| API-26 | PUT | `/responses/{id}/draft` | R 본인 |
| API-27 | POST | `/responses/{id}/submit` | R 본인 |
| API-28 | GET | `/responses` | 전체; R 본인 |
| API-29 | GET | `/responses/{id}` | 전체+객체 권한 |
| API-30 | POST | `/responses/{id}/reviews` | S/A |
| API-31 | POST | `/offline-response-imports` | R |
| API-32 | GET | `/offline-response-imports/{operationId}` | 해당 R 본인 |
| API-33 | POST | `/client-errors/batch` | 전체 |

### API-01 POST /auth/login

| 항목 | 계약 |
| --- | --- |
| 권한 | 공개 |
| 요청 body | `LoginRequest` |
| Query | 없음 |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `LoginResult` |
| 업무 오류 | INVALID_CREDENTIALS, ACTIVE_DEVICE_EXISTS, DEVICE_REVOKED, RATE_LIMITED |

### API-02 POST /auth/refresh

| 항목 | 계약 |
| --- | --- |
| 권한 | Refresh Token |
| 요청 body | `RefreshRequest` |
| Query | 없음 |
| 추가 헤더 | Idempotency-Key |
| 성공 HTTP | 200 |
| 성공 data | `TokenPair` |
| 업무 오류 | INVALID_REFRESH_TOKEN, TOKEN_REUSE_DETECTED, DEVICE_MISMATCH |

### API-03 POST /auth/logout

| 항목 | 계약 |
| --- | --- |
| 권한 | 전체 |
| 요청 body | `LogoutRequest` |
| Query | 없음 |
| 추가 헤더 | 없음 |
| 성공 HTTP | 204 |
| 성공 data | 없음 |
| 업무 오류 | UNAUTHENTICATED |

204는 body 없음. 로그인한 현재 세션의 refreshToken만 허용. 다른 세션 token은 403. 성공 시 현재 세션 폐기; 이미 폐기된 Access Token 재요청은 401이며 앱은 로그아웃 완료로 처리. 활성 수집 기기 연결은 유지한다.

### API-04 GET /me

| 항목 | 계약 |
| --- | --- |
| 권한 | 전체 |
| 요청 body | 없음 |
| Query | 없음 |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `MeResult` |
| 업무 오류 | 공통 |

### API-05 GET /users

| 항목 | 계약 |
| --- | --- |
| 권한 | A |
| 요청 body | 없음 |
| Query | `UsersQuery` |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `UserList` |
| 업무 오류 | 공통 |

### API-06 POST /users

| 항목 | 계약 |
| --- | --- |
| 권한 | A |
| 요청 body | `CreateUserRequest` |
| Query | 없음 |
| 추가 헤더 | Idempotency-Key |
| 성공 HTTP | 201 / 재전송 200 |
| 성공 data | `User` |
| 업무 오류 | ID_ALREADY_EXISTS |

### API-07 PATCH /users/{userId}

| 항목 | 계약 |
| --- | --- |
| 권한 | A |
| 요청 body | `PatchUserRequest` |
| Query | 없음 |
| 추가 헤더 | If-Match + Idempotency-Key |
| 성공 HTTP | 200 |
| 성공 data | `User` |
| 업무 오류 | ADMIN_ACCOUNT_PROTECTED |

대상은 grade 2 또는 3. grade 1 관리·자기 역할 승격은 제공하지 않는다. 계정 비활성화와 비밀번호 초기화는 모든 세션을 즉시 폐기한다. 기기 연결·기존 응답·기존 할당을 삭제하지 않는다.

### API-08 POST /users/{userId}/password-reset

| 항목 | 계약 |
| --- | --- |
| 권한 | A |
| 요청 body | `PasswordResetRequest` |
| Query | 없음 |
| 추가 헤더 | If-Match + Idempotency-Key |
| 성공 HTTP | 200 |
| 성공 data | `PasswordResetResult` |
| 업무 오류 | ADMIN_ACCOUNT_PROTECTED |

대상은 grade 2 또는 3. grade 1 관리·자기 역할 승격은 제공하지 않는다. 계정 비활성화와 비밀번호 초기화는 모든 세션을 즉시 폐기한다. 기기 연결·기존 응답·기존 할당을 삭제하지 않는다.

### API-09 POST /users/{userId}/device-release

| 항목 | 계약 |
| --- | --- |
| 권한 | A |
| 요청 body | `DeviceReleaseRequest` |
| Query | 없음 |
| 추가 헤더 | If-Match + Idempotency-Key |
| 성공 HTTP | 200 |
| 성공 data | `DeviceReleaseResult` |
| 업무 오류 | DEVICE_MISMATCH, INVALID_TARGET_ROLE |

### API-10 POST /user-imports/validate

| 항목 | 계약 |
| --- | --- |
| 권한 | A |
| 요청 body | `multipart(file)` |
| Query | 없음 |
| 추가 헤더 | Idempotency-Key |
| 성공 HTTP | 201 / 재전송 200 |
| 성공 data | `ImportValidation` |
| 업무 오류 | INVALID_CSV, PAYLOAD_TOO_LARGE |

CSV 헤더 id,pw,name,grade 순서. 데이터 행 1~1,000개(헤더 제외), row 번호는 헤더=1. UTF-8 BOM·RFC 4180 인용부호 처리. 파일 구조 파싱 불가 400 INVALID_CSV; 파싱 가능하지만 행 오류면 201 status=INVALID. 한 행 여러 필드 오류를 모두 반환. VALID은 validRows=totalRows. max errors 5,000. 원문 비밀번호 반환 금지.

### API-11 POST /user-imports/{importId}/commit

| 항목 | 계약 |
| --- | --- |
| 권한 | A |
| 요청 body | 없음 |
| Query | 없음 |
| 추가 헤더 | Idempotency-Key |
| 성공 HTTP | 200 |
| 성공 data | `ImportResult` |
| 업무 오류 | IMPORT_INVALID, IMPORT_EXPIRED, ID_ALREADY_EXISTS |

body 없음. 업로드한 A 본인만 commit. INVALID는 409 IMPORT_INVALID. 만료 시 409 IMPORT_EXPIRED. commit 시 ID 중복을 재검사하고 전 행 생성/전부 롤백. 실패 충돌 시 검증 데이터를 INVALID로 표시하고 안전한 행 오류만 저장. 동일 importId 완료 재요청은 새 key여도 기존 결과를 반환한다.

### API-12 GET /user-imports/{importId}

| 항목 | 계약 |
| --- | --- |
| 권한 | A |
| 요청 body | 없음 |
| Query | 없음 |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `ImportStatusResult` |
| 업무 오류 | NOT_FOUND |

업로드한 A 본인만 조회. VALID/INVALID/EXPIRED이면 ImportValidation, COMPLETED이면 ImportResult. status가 타입 구분 필드다. 임시 원본은 10분 후 또는 완료 즉시 삭제; EXPIRED 결과의 errors=[]이며 원래 행 통계만 보관. 비밀 없는 작업 결과는 생성/완료 시각부터 각각 3개월 보관한다.

### API-13 GET /me/survey-assignment

| 항목 | 계약 |
| --- | --- |
| 권한 | R |
| 요청 body | 없음 |
| Query | 없음 |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `AssignmentResult` |
| 업무 오류 | 공통 |

미할당도 200, assignment=null, slotVersion 존재. 서버 시각은 meta.serverTime에만 둔다.

### API-14 GET /surveys

| 항목 | 계약 |
| --- | --- |
| 권한 | S/A |
| 요청 body | 없음 |
| Query | `SurveyQuery` |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `SurveyList` |
| 업무 오류 | 공통 |

설문 버전별 한 행. 기본 PUBLISHED. S/A만 게시/중지/마감 버전을 조회한다. R의 개인 다운로드 목록으로 사용하지 않는다.

### API-15 GET /surveys/{id}/versions/{version}

| 항목 | 계약 |
| --- | --- |
| 권한 | 전체+객체 권한 |
| 요청 body | 없음 |
| Query | 없음 |
| 추가 헤더 | If-None-Match 선택 |
| 성공 HTTP | 200 / 304 |
| 성공 data | `SurveyDefinition` |
| 업무 오류 | NOT_FOUND |

R은 현재 본인 할당 버전 또는 보관 중인 본인 기존 응답이 참조하는 버전만 조회. S/A는 모든 운영 설문 버전 조회. 권한을 먼저 검사하고 ETag가 맞으면 304. 본인 과거 할당만 있고 기존 응답도 없으면 이 경로로 새로 다운로드하지 않는다.

### API-16 GET /interviewers

| 항목 | 계약 |
| --- | --- |
| 권한 | S/A |
| 요청 body | 없음 |
| Query | `InterviewerQuery` |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `InterviewerList` |
| 업무 오류 | 공통 |

### API-17 GET /interviewers/{userId}/survey-assignment

| 항목 | 계약 |
| --- | --- |
| 권한 | S/A |
| 요청 body | 없음 |
| Query | 없음 |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `AssignmentResult` |
| 업무 오류 | INVALID_TARGET_ROLE |

미할당도 200, assignment=null, slotVersion 존재. 서버 시각은 meta.serverTime에만 둔다.

### API-18 PUT /interviewers/{userId}/survey-assignment

| 항목 | 계약 |
| --- | --- |
| 권한 | S/A |
| 요청 body | `AssignmentRequest` |
| Query | 없음 |
| 추가 헤더 | If-Match + Idempotency-Key |
| 성공 HTTP | 200 |
| 성공 data | `AssignmentResult` |
| 업무 오류 | INTERVIEWER_INACTIVE, SURVEY_NOT_ASSIGNABLE |

활성 조사원과 PUBLISHED 버전만 새 할당. 해제는 surveyId/surveyVersion 모두 null. 같은 대상이면 no-op(버전·이력 불변); 미할당→해제도 no-op. reason 필수 1~1,000자, 공백만 금지. 현재 슬롯 If-Match는 no-op에도 검사한다.

### API-19 GET /interviewers/{userId}/survey-assignment-history

| 항목 | 계약 |
| --- | --- |
| 권한 | S/A |
| 요청 body | 없음 |
| Query | `PageQuery` |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `AssignmentHistory` |
| 업무 오류 | INVALID_TARGET_ROLE |

감사 이력의 before/after는 당시 스냅샷. before는 종료 상태/endedAt 반영, after는 새 ACTIVE. source 할당 만료 후에도 감사 이력의 독립 보관 기한 내 스냅샷을 유지한다.

### API-20 GET /sync/changes

| 항목 | 계약 |
| --- | --- |
| 권한 | 전체 |
| 요청 body | 없음 |
| Query | `PageQuery` |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `SyncResult` |
| 업무 오류 | SYNC_CURSOR_EXPIRED |

§10 변경 동기화 계약을 적용한다. 일반 PageQuery를 쓰지만 cursor 수명/nextCursor null 규칙은 이 API의 특례가 우선한다.

### API-21 POST /attendance-events

| 항목 | 계약 |
| --- | --- |
| 권한 | R |
| 요청 body | `AttendanceRequest` |
| Query | 없음 |
| 추가 헤더 | Idempotency-Key=idx |
| 성공 HTTP | 201 / 재전송 200 |
| 성공 data | `EventResult` |
| 업무 오류 | EVENT_GAP, SEQUENCE_CONFLICT, ATTENDANCE_STATE_CONFLICT, RETENTION_EXPIRED |

### API-22 GET /attendance-events

| 항목 | 계약 |
| --- | --- |
| 권한 | 전체; R 본인 |
| 요청 body | 없음 |
| Query | `AttendanceQuery` |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `AttendanceList` |
| 업무 오류 | 공통 |

### API-23 GET /dashboards/me

| 항목 | 계약 |
| --- | --- |
| 권한 | R |
| 요청 body | 없음 |
| Query | `DashboardMeQuery` |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `DashboardMe` |
| 업무 오류 | 공통 |

surveyId 필터는 progress에만 적용. 현재 근태/현재 할당을 숨기지 않는다. date가 과거여도 currentAssignment는 지금의 할당, attendance/progress는 지정일 기준이다.

### API-24 GET /dashboards/attendance

| 항목 | 계약 |
| --- | --- |
| 권한 | S/A |
| 요청 body | 없음 |
| Query | `AttendanceDashboardQuery` |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `AttendanceDashboard` |
| 업무 오류 | 공통 |

### API-25 GET /dashboards/progress

| 항목 | 계약 |
| --- | --- |
| 권한 | S/A |
| 요청 body | 없음 |
| Query | `ProgressDashboardQuery` |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `ProgressDashboard` |
| 업무 오류 | 공통 |

### API-26 PUT /responses/{id}/draft

| 항목 | 계약 |
| --- | --- |
| 권한 | R 본인 |
| 요청 body | `DraftRequest` |
| Query | 없음 |
| 추가 헤더 | 없음; draftVersion 사용 |
| 성공 HTTP | 200 |
| 성공 data | `DraftResult` |
| 업무 오류 | DRAFT_VERSION_CONFLICT, RESPONSE_STATE_CONFLICT, ASSIGNMENT_INVALID, SURVEY_CLOSED, RETENTION_EXPIRED |

If-Match/Idempotency-Key 불필요. (responseId, draft revision, draftVersion)으로 중복·순서를 검증한다. 상세 계약은 §9. 반려 보완 중 status=RETURNED, revision은 직전 제출 revision, draftRevision은 보완 revision이다.

### API-27 POST /responses/{id}/submit

| 항목 | 계약 |
| --- | --- |
| 권한 | R 본인 |
| 요청 body | `SubmitRequest` |
| Query | 없음 |
| 추가 헤더 | Idempotency-Key |
| 성공 HTTP | 201 / 재전송 200 |
| 성공 data | `ResponseDetail` |
| 업무 오류 | REVISION_CONFLICT, RESPONSE_STATE_CONFLICT, ASSIGNMENT_INVALID, SURVEY_CLOSED, RETENTION_EXPIRED |

### API-28 GET /responses

| 항목 | 계약 |
| --- | --- |
| 권한 | 전체; R 본인 |
| 요청 body | 없음 |
| Query | `ResponseQuery` |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `ResponseList` |
| 업무 오류 | 공통 |

### API-29 GET /responses/{id}

| 항목 | 계약 |
| --- | --- |
| 권한 | 전체+객체 권한 |
| 요청 body | 없음 |
| Query | `ResponseDetailQuery` |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `ResponseDetail` |
| 업무 오류 | RETENTION_EXPIRED |

revision 생략: 최신 업무 상태와 현재 작업 초안. revision 지정: 해당 제출 snapshot과 그 revision의 검수, draft=null; resourceVersion은 최신 서버 자원 버전이다. 지정 revision이 아직 미제출이면 404. 과거 revision을 조회해도 검수는 현재 SUBMITTED revision만 가능하다.

### API-30 POST /responses/{id}/reviews

| 항목 | 계약 |
| --- | --- |
| 권한 | S/A |
| 요청 body | `ReviewRequest` |
| Query | 없음 |
| 추가 헤더 | If-Match + Idempotency-Key |
| 성공 HTTP | 201 / 재전송 200 |
| 성공 data | `ReviewResult` |
| 업무 오류 | RESPONSE_STATE_CONFLICT, REVISION_CONFLICT, RETENTION_EXPIRED |

### API-31 POST /offline-response-imports

| 항목 | 계약 |
| --- | --- |
| 권한 | R |
| 요청 body | `OfflineImportRequest` |
| Query | 없음 |
| 추가 헤더 | Idempotency-Key=operationId |
| 성공 HTTP | 200 |
| 성공 data | `OfflineImportResult` |
| 업무 오류 | OPERATION_ID_CONFLICT; 업무 오류는 항목별 |

POST는 동기 실행해 COMPLETED 결과를 반환한다. 처리 도중 별도 GET은 PROCESSING과 PENDING 항목을 볼 수 있다. 일부 실패도 200. operationId와 Idempotency-Key는 동일 UUID; 개별 항목은 서로 독립적으로 원자 처리한다.

### API-32 GET /offline-response-imports/{operationId}

| 항목 | 계약 |
| --- | --- |
| 권한 | 해당 R 본인 |
| 요청 body | 없음 |
| Query | 없음 |
| 추가 헤더 | 없음 |
| 성공 HTTP | 200 |
| 성공 data | `OfflineImportResult` |
| 업무 오류 | RETENTION_EXPIRED |

본인 작업만 조회, 다른 사람 것은 404. PROCESSING/COMPLETED를 모두 200으로 반환. PROCESSING 조회 시 Retry-After=2. GET은 작업을 다시 실행하지 않는다.

### API-33 POST /client-errors/batch

| 항목 | 계약 |
| --- | --- |
| 권한 | 전체 |
| 요청 body | `ErrorBatchRequest` |
| Query | 없음 |
| 추가 헤더 | 없음; eventId 사용 |
| 성공 HTTP | 200 |
| 성공 data | `ErrorBatchResult` |
| 업무 오류 | 업무 오류는 항목별 |

배열/객체 구조 오류는 최상위 422. 식별 가능한 각 이벤트의 시각·민감 내용·중복 충돌 등 업무 오류는 rejected에 반환. 같은 ID·같은 내용은 acceptedIds에 포함한다. 정상 접수 ID는 입력 순서대로, rejected도 입력 순서대로 반환한다.

## 7. 인증·기기·계정·할당 버전

### 7.1 인증 구현 계약

- Access/Refresh Token은 각각 CSPRNG 32 bytes를 base64url(패딩 없음, 43자)한 불투명 문자열. JWT를 전제로 구현하지 않는다. 서버에는 토큰 원문 대신 SHA-256 digest, sessionId, 계정·기기·만료·폐기 정보를 저장한다.
- Access 유효 900초, Refresh 세션 family는 로그인 시점부터 최대 30×24시간. refresh 회전이 family의 절대 만료를 연장하지 않는다. refreshExpiresIn은 해당 TokenPair 발급 시각에서 family 만료까지 남은 정수 초, 올림 없이 계산한다. 같은 발급 결과 재전송에서 이 값을 갱신하지 않으므로 실제 만료는 accessExpiresAt/refreshExpiresAt으로 판단한다.
- refresh는 원자적으로 기존 Refresh Token 사용 완료+새 TokenPair 발급. Idempotency-Key 필수. 같은 key/동일 요청의 응답 유실은 60초 동안 같은 발급 결과를 복구한다. 이 짧은 결과 캐시의 토큰 원문은 암호화하고 60초 후 삭제한다.
- 사용된 Refresh Token을 다른 key로 재사용하거나 복구 유예 60초 이후 재사용하면 401 TOKEN_REUSE_DETECTED, 해당 family 전체 폐기. 앱은 refresh single-flight 및 키 영속 저장을 사용한다. 401 처리에서 API 요청당 refresh는 최대 한 번.
- refresh 재전송도 계정 활성/기기 유효성 검사가 우선한다. 이전 Access Token은 원래 만료까지 유효하되 family 폐기·계정 비활성·password reset·device-release 시 즉시 무효다.
- 같은 계정·같은 기기 새 login은 기존 해당 기기 session family를 폐기하고 새 family 생성. R의 활성 기기는 하나만 연결; S/A는 계정당 동시 기기 수를 제한하지 않되 각 기기당 family 1개만 유지한다.
- 로그인 실패는 401 INVALID_CREDENTIALS로 존재/비밀번호/비활성 여부를 구분해 노출하지 않는다. 올바른 비밀번호 이후 활성 기기 충돌은 409, 폐기 기기 재로그인은 403 DEVICE_REVOKED.
- 사용자별 실패 제한: 정규화한 ID+IP마다 15분 5회 실패; IP당 15분 50회 실패. 초과 429이며 영구 계정 잠금 없음. 로그인 성공은 해당 ID+IP 실패 계수만 초기화. refresh 기기당 분당 10회, 일반 인증 API는 계정+기기당 분당 300회(서버 수신 기준 고정 60초 구간). 더 엄격한 운영 제한은 별도 계약 변경 없이 임의 적용하지 않는다.
- 비밀번호는 Argon2id(m=19,456 KiB,t=2,p=1, 계정별 무작위 salt 최소 16 bytes)로 저장. 이 매개변수는 OWASP 최소 권장 설정을 기준으로 선택했다. 평문·복호화 가능한 계정 비밀번호 저장 금지. CSV 원본은 짧은 암호화 임시 파일로만 존재한다.
- 계정/자원 데이터는 토큰의 클라이언트 주장으로 결정하지 않는다. 매 요청 session→현재 계정 권한·credentialVersion·폐기 상태 확인.

참고: [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), 2026-09-30 확인. 나머지 만료·제한 수치는 이 앱의 설계 결정이다.

### 7.2 버전 규칙

| 버전 | 최초 | 증가 시점 |
| --- | --- | --- |
| User.resourceVersion | 1 | 이름/활성 상태 실제 변경, password reset, 활성 기기 연결/해제 |
| credentialVersion | 1 | password reset만 +1. 계정 비활성·기기 해제는 세션 폐기로 처리 |
| slotVersion | 조사원 생성 시 1 | 할당/교체/해제의 실제 변경마다 +1 |
| assignmentRevision | 첫 할당 1 | 새 assignmentId 생성 때 조사원별 기존 최대+1. 해제/no-op는 새 revision 없음 |
| Response.resourceVersion | 첫 서버 저장 1 | 새 초안 내용 버전 접수, 제출, 검수, 검수 표시 변경 등 실제 변경마다 +1 |
| draftVersion | 각 draft revision의 1 이상 | 클라이언트에서 증가. 서버 첫 접수도 1보다 큰 값 허용 |
| revision | 첫 응답 1 | RETURNED 보완 제출만 직전+1 |
| surveyVersion | 설문별 1 | 새 정의 게시 때 기존 최대+1. 제자리 문항 수정 불가 |

- 무변경 no-op와 동일 성공 재전송은 버전 증가 없음. User.role과 grade는 항상 1/ADMIN, 2/SUPERVISOR, 3/INTERVIEWER 대응.
- 최초 어드민은 배포 관리 명령으로 생성. 이 API로 grade=1 생성·수정·초기화하지 않는다.
- R 새 기기 연결은 User.resourceVersion 증가. S/A의 세션 추가는 activeDeviceId가 null이므로 User.resourceVersion을 바꾸지 않는다.
- 기기 해제는 연결 해제·세션 폐기·감사 로그·User 버전 증가를 원자 처리. 기존 근태의 ON/OFF·sequence는 유지한다. 새로운 기기 sequence는 1부터 시작한다.
- 해제된 (userId,deviceId)는 폐기 기록으로 유지하여 해당 ID의 재접속을 차단한다. 같은 물리 기기를 새 설치 ID로 바꾸면 별도 기기로 취급한다. 원격 로컬 삭제를 보장하지 않는다.
- CSV 검증의 fingerprint는 파일 bytes 자체의 서버 HMAC. 같은 key·파일 재전송이면 동일 importId, 다른 파일이면 409. 신규 업로드마다 10분 expiresAt 고정, 재조회로 연장하지 않는다. 원본은 앱 비밀 관리 시스템의 별도 키로 암호화하여 보관한다.

### 7.3 설문 운영 상태

| status | 새 할당/신규 시작 | 기존 초안·응답 제출 | 정의 조회 |
| --- | --- | --- | --- |
| PUBLISHED | 허용 | 허용 | 객체 권한에 따라 허용 |
| SUSPENDED | 금지 | 이미 시작한 응답은 허용 | 허용 |
| CLOSED | 금지 | 신규 서버 저장·새 제출 금지, 409 SURVEY_CLOSED | 허용 |

- 동일 성공 재전송은 CLOSED 이후에도 만료 전 기존 성공을 반환. 기존 SUBMITTED 응답 검수는 CLOSED여도 허용.
- 설문 운영 상태만 별도 메타데이터로 변경 가능. title/questions/schemaVersion/publishedAt은 게시 후 불변. 상태 변경 시 정의 ETag와 변경 feed resourceVersion 증가.
- 게시 작업은 API 범위 밖의 인증된 운영 명령/배포 절차로 한다. 반드시 문항 스키마/ID 유일성/1 MiB 한도를 검사 후 원자 등록. 앱에서 임의 JSON을 제출해 설문을 등록할 수 없다.
- 보관 중 응답의 surveyDefinition은 시작/접수된 문항 버전 스냅샷이다. 운영 status 변경으로 기존 질문을 교체하지 않는다. 조회 API의 현재 status와 응답 스냅샷 status는 다를 수 있다.
- 종료 할당에도 userId·surveyId·surveyVersion·revision 최소 참조를 보관한다. 계정 응답은 원래 본인 할당을 검증하며 현재 할당과 다르면 ASSIGNMENT_CHANGED를 표시한다. OFFLINE_CACHED 응답이 할당 종료 후 시작되었더라도 유효 캐시/원래 권한이 있으면 이 표시와 함께 수용한다. 실제 기기 캐시 유효성은 앱 책임이며 서버가 증명했다고 표현하지 않는다.

## 8. 근태·시각·현황 집계

### 8.1 근태 이벤트 순서

- 근태 핵심 기록은 idx/status/timestamp. userId/deviceId/sequence/서버 수신/전송 상태는 메타데이터 또는 별도 테이블로 보관한다. 표시용 `YYYY:MM:DD:HH:MM:SS`와 API UTC 형식을 혼동하지 않는다.
- 계정의 초기 근태 상태는 OFF. 서버 현재 근태 상태는 계정당 하나이고 순서 카운터는 `(userId,deviceId)`마다 따로 유지한다. login/me의 deviceNextSequence로 확인한다.
- 허용 처리 순서: 권한→기존 idx 조회→만료→sequence→현재 상태→저장. 동일 idx/내용은 과거 성공 반환. 동일 idx/다른 내용은 IDEMPOTENCY_CONFLICT. 동일 sequence/다른 idx 또는 지난 sequence 신규 idx는 409 SEQUENCE_CONFLICT.
- 기대보다 큰 sequence는 409 EVENT_GAP, details.expectedSequence 제공. 이 실패는 sequence를 소비하지 않는다. 앱은 누락된 앞선 이벤트부터 전송한다.
- 새로운 같은 상태 ON→ON 또는 OFF→OFF는 409 ATTENDANCE_STATE_CONFLICT, details.currentState/expectedSequence 제공. 실패는 sequence를 소비하지 않는다. 앱은 사용자에게 충돌을 표시하고 자동으로 이벤트를 삭제·바꿔치기하지 않는다.
- 정상 수용은 서버 계정 근태 상태와 이벤트/sequence를 같은 트랜잭션으로 갱신. 성공 conflict=false. idx의 성공 data.effectiveState는 당시 적용 결과이며 재전송 시 현재 상태로 덮어쓰지 않는다.
- 기기 교체 시에도 계정 상태 ON은 그대로다. 새 기기가 login/me 및 본인 dashboard 상태를 동기화한 뒤 토글한다. 기기가 새롭다고 자동 ON을 생성하지 않는다.

### 8.2 시각 검증

- serverReceivedAt은 최초 서버 접수 시각으로 불변, 재전송 시 새 시각으로 바꾸지 않는다.
- 현재 서버 시각보다 300초 초과 미래의 시작/완료/근태/오류 발생 시각은 422 CLOCK_INVALID. 0~300초 미래는 원시 시각 보존, warning/검수 표시. 미래 시각으로 3개월 보관을 늘리지 않도록 만료 기산은 min(원시 기산 시각, 최초 serverReceivedAt).
- 응답 completedAt>=startedAt, assignmentCheckedAt<=startedAt+300초. 시작/완료 시각은 기기 기록이라는 한계가 있으며 서버는 원격 오프라인 시각의 진위를 보장하지 않는다.
- 근태 timestamp가 직전 정상 effectiveAt보다 과거여도 sequence가 맞으면 수용한다. effectiveAt=max(직전 계정 effectiveAt, min(timestamp,serverReceivedAt)). timeWarning=CLOCK_MOVED_BACKWARD; 미래 허용 오차이면 FUTURE_WITHIN_TOLERANCE. 두 조건이 겹치면 CLOCK_MOVED_BACKWARD를 우선한다.
- timeWarning.differenceSeconds=timestamp-referenceTime의 정수 초(0 방향 절삭), observedAt=timestamp. 미래 warning referenceTime=serverReceivedAt, 역행 warning referenceTime=직전 effectiveAt.
- 늦게 도착한 과거 기록은 3개월 내라면 수용. 이미 만료된 근태는 410 RETENTION_EXPIRED, sequence/현재 상태를 변경하지 않는다. 로컬 원본 보존·관리자 확인 대상으로 표시하고 임의 새 시각으로 전송하지 않는다.

### 8.3 출근 상태에서만 신규 설문

- 앱은 로컬 ON 이벤트와 DRAFT 생성 시 attendanceEventId를 함께 저장한다. OFF에서는 새 응답 ID를 만들지 않는다. 계정 없는 모드도 로컬 ON 필수다.
- 서버는 계정 응답의 attendanceEventId가 본인 ON인지 확인한다. 존재하는 타인 이벤트/본인 OFF 참조는 422 INVALID_ATTENDANCE_REFERENCE. 요청자가 보낸 ON 문자열은 인증 근거가 아니다.
- 근태 업로드가 아직 없거나 보관 정책으로 상세 근태가 사라졌으면 응답을 막지 않고 ATTENDANCE_UNVERIFIED를 표시한다. 근태 조회에서 시작 시 OFF였음이 확인되어도 검수 표시를 부여하고 자동 반려하지 않는다. 출근이라는 앱의 시작 조건과 서버 수신 순서를 혼동하지 않는다.
- 시작 당시 ON 검증은 해당 이벤트와 계정의 effectiveAt 타임라인으로 한다. 같은 effectiveAt이면 서버 적용 순서로 결정. 시작 시각 자체가 오차 범위이면 확정하지 않고 검수 표시한다.
- 근태가 뒤늦게 동기화되면 서버는 해당 참조를 재검사해 ATTENDANCE_UNVERIFIED를 해소할 수 있다. 상태/답변/검수 결정은 변경하지 않고 resourceVersion과 변경 feed만 갱신한다. 익명 수집은 서버 실명 근태 검증 대상이 아니므로 이 표시를 부여하지 않는다.

### 8.4 대시보드

- attendance date가 오늘이면 기준 시각은 asOf, 과거면 다음 날 00:00 KST 직전 상태. stateAsOf는 오늘 asOf, 과거 다음 날 00:00 KST(배타적 경계)를 반환한다.
- 과거 상태는 당시 effectiveAt 타임라인으로 복원. 필요한 근태/초기 상태 근거가 삭제되어 복원 불가하면 UNKNOWN과 lastEventAt=null,lastReceivedAt=null. 아무 이벤트도 없던 신규 계정은 OFF. 오늘은 삭제 후에도 별도 보존한 현재 운영 상태를 사용한다.
- 근태 현황은 활성·비활성 모든 조사원을 포함하며 active를 표시. q/status는 표시 필터. 미전송 현장 기록이 포함된 것처럼 표현하지 않는다.
- 응답 firstSubmittedAt은 최초 SUBMITTED로 서버가 전환한 시각이다. 이 시각의 Asia/Seoul 날짜로 제출 집계. 오프라인에서 어제 완료해 오늘 업로드하면 오늘 제출 건수다. completedAt은 현장 완료 시각으로 별도 보존한다.
- draftCount는 현재 상태 DRAFT이고 startedAt의 업무일이 date인 서로 다른 responseId 수. 반려 보완 초안은 RETURNED에 속하며 draftCount에 중복 합산하지 않는다.
- submittedTotal은 firstSubmittedAt 업무일이 date인 서로 다른 responseId 수. 현재 상태로 pendingReviewCount/approvedCount/returnedCount를 나누고 합계는 submittedTotal과 같아야 한다.
- 반려 재제출은 lastSubmittedAt만 갱신하고 firstSubmittedAt 유지. 이전 날짜 제출분을 오늘 검수해도 이전 날짜 행의 분류만 바뀐다.
- 진행 현황은 현재 할당 설문 행과 지정일에 실적이 있는 과거 설문/버전 행의 합집합. 키 `(userId,surveyId,surveyVersion,date)` 중복 없음. 할당만 있고 실적 없으면 0, 미할당·실적 없음이면 진행 행 없음(조사원은 근태 현황에서 확인).
- lastReceivedAt은 해당 행 자료의 마지막 업무 수신 시각. 아무 실적 없으면 null. 만료 응답은 모든 집계에서 제외한다. asOf를 마지막 현장 활동 시각으로 표시하지 않는다.

## 9. 설문 응답·초안·검수 계약

### 9.1 질문과 값 검증

- 정의의 문항 ID는 설문 버전 내 유일, 보기 ID는 문항 내 유일. 문항 1~200, 객관식 보기 2~5.
- SINGLE_CHOICE value는 보기 ID 하나 또는 null. 배열 금지. TEXT는 문자열/null이며 해당 validation.maxLength 이하. SCALE_7은 정수 1~7/null. 질문 type과 answer.type은 일치해야 한다.
- 최종 제출: 모든 문항을 정의 순서대로 정확히 한 번 포함. 선택 문항 미응답도 value=null로 포함. 필수 null/공백만 TEXT 불가; 선택 TEXT의 빈 문자열/공백만 값도 미응답으로 간주하여 null로 보내야 한다(서버 자동 변환 없음).
- 초안: 문항 일부 생략과 필수 value=null 허용. 제공한 non-null 값은 타입·범위·보기 ID·길이 검사. TEXT 빈 값은 초안에서 허용. 중복/알 수 없는 questionId는 항상 422.
- 순서 오류는 422 VALIDATION_FAILED. 미지원 schemaVersion은 422 UNSUPPORTED_SCHEMA_VERSION. 서버 실행 코드/HTML/원격 컴포넌트 실행 금지.

### 9.2 불변 수집 정보

- responseId는 로컬 UUID v4, 최초 서버 저장 시 ownerUserId/collectionMode/시작 시각/설문 버전/할당/attendanceEventId 고정.
- 동일 responseId에 대한 후속 초안·제출에서 COLLECT 필드(assignmentId,assignmentRevision,surveyId,surveyVersion,assignmentCheckedAt,assignmentCheckMode,startedAt,attendanceEventId)는 최초와 같아야 한다. 위반 409 RESPONSE_ID_CONFLICT.
- AUTHENTICATED는 capturedByUserId=ownerUserId, importedByUserId/localResponseId/localSessionId=null. ANONYMOUS_OFFLINE는 capturedByUserId=null, importedByUserId=ownerUserId, local ID/session 기록, assignmentCheckedAt/assignmentCheckMode/attendanceEventId=null.
- 익명 가져온 응답의 반려 보완은 API-26/27로 가능하다. 이때 기존 nullable 수집 메타 필드는 null을 그대로 보내며 assignmentId/revision 및 원래 시각은 고정한다. 아래 DTO의 해당 3개 필드는 이 경우에만 null 허용한다. 일반 계정 신규 응답의 null은 422.
- 최초 serverReceivedAt·expiresAt·firstSubmittedAt은 재시도/검수/보완으로 연장하지 않는다.

### 9.3 초안과 revision

| 경우 | 입력 | 처리 |
| --- | --- | --- |
| 서버에 없는 첫 초안 | revision=1, baseRevision=null, draftVersion>=1 | 상태 DRAFT, resourceVersion=1 |
| DRAFT 수정 | revision=1, baseRevision=null, 이전보다 큰 draftVersion | 저장, resourceVersion+1 |
| 같은 draftVersion·같은 본문 | 동일 revision | 기존 성공 재반환, 증가 없음 |
| 작은 draftVersion 또는 동일 버전 다른 본문 | 임의 | 409 DRAFT_VERSION_CONFLICT |
| RETURNED 보완 초안 | revision=직전+1, baseRevision=직전, 별도 draftVersion | 업무 status/revision은 직전 RETURNED 유지, draft만 갱신 |
| SUBMITTED/APPROVED에 초안 쓰기 | 임의 | 409 RESPONSE_STATE_CONFLICT |

- 초안 If-Match를 요구하지 않는다. draftVersion 비교와 상태 검사를 응답 잠금/트랜잭션에서 수행한다. 빠르게 수정한 초안이 늦은 네트워크 요청으로 덮어써지지 않아야 한다.
- ResponseDetail의 answers는 최신 제출된 내용(첫 DRAFT만 현재 초안 내용), draft는 현재 미제출 작업 내용. RETURNED 보완 중 answers를 보완 초안으로 덮어쓰지 않는다.
- ResponseDetail.status/revision은 현재 제출 workflow 기준, draft.revision은 별도 작업 revision. hasWorkingDraft는 draft!=null과 같다. 첫 DRAFT도 true.

### 9.4 최종 제출과 재제출

- 서버 초안이 없어도 전체 답변 POST로 최초 제출 가능. revision=1/baseRevision=null. 응답 생성+제출을 같은 트랜잭션에서 처리하고 resourceVersion=1.
- 초안이 있으면 제출 내용이 최종 본문. draft를 제거하고 SUBMITTED, resourceVersion+1. 별도 draftVersion 제출 필드 없음.
- 반려 뒤에만 baseRevision=직전 RETURNED revision, revision=baseRevision+1. 원본 제출 snapshot을 보관하고 새 제출본을 추가한다.
- SUBMITTED/APPROVED의 새 revision 요청은 409. 같은 revision·같은 본문의 성공 재전송은 검수 후에도 원래 성공 결과, 다른 본문은 409 REVISION_CONFLICT. 익명 가져오기 첫 revision에 직접 submit을 덮어쓰는 것도 금지.
- 현재 할당과 원래 할당이 다르면 ASSIGNMENT_CHANGED 표시. 현재 slot이 null이어도 동일. 이전 설문/버전 답변을 새 설문으로 옮기지 않는다.
- 미래 허용 오차 시 CLOCK_SKEW 표시. reviewFlags는 중복 없이 고정 순서 ASSIGNMENT_CHANGED,ATTENDANCE_UNVERIFIED,CLOCK_SKEW.
- 클라이언트가 status, ownerUserId, resourceVersion을 body로 지정할 수 없다.

### 9.5 검수·상세 조회

- 현재 SUBMITTED revision만 APPROVE/RETURN. If-Match=Response.resourceVersion. 요청 revision이 현재와 다르면 409 REVISION_CONFLICT(단 If-Match 불일치는 412 우선).
- RETURN의 reason은 1~1,000자 공백만 불가. APPROVE는 reason 생략/null 허용. questionIds 생략은 [], 중복 불가, 존재하는 질문만. 전체 응답 반려이며 부분 승인 기능 없음.
- 성공 시 ReviewResult 저장+상태 변경+resourceVersion 증가+변경 feed 원자 처리. 같은 key 재전송은 그대로 반환. 두 검수 동시 시 하나만 성공하고 다른 요청은 412.
- APPROVED는 답변 수정·승인 취소 금지. 서버가 근태 검증 표시를 해소하는 것과 답변 승인 잠금은 별개다.
- ResponseDetail.reviews는 조회한 revision까지의 검수 이력, reviewedAt ASC/reviewId ASC. revisions는 제출된 revision 요약 목록 ASC. historical revision query 시 answers/상태/완료 시각/검수는 해당 snapshot, firstSubmittedAt/owner/최신 resourceVersion은 공통 값이다.
- ownerName과 현황 name은 현재 계정 이름, surveyTitle은 불변 설문 버전 title이다. 과거 revision query의 draft=null, hasWorkingDraft=false. 응답 snapshot을 API-15의 최신 정의로 덮어쓰지 않는다.

## 10. 변경 동기화와 커서 복구

- 결과 data는 SyncResult. items는 changedAt/내부 이벤트 순서 ASC. nextCursor는 마지막 페이지라도 항상 문자열; hasMore=false일 때 그 커서로 다음 polling. 일반 목록의 null 규칙과 다르다.
- cursor 없으면 현재 사용자의 보관 중 변경 로그(최대 최근 30일) 첫 페이지를 반환. 이벤트가 없으면 현재 high-watermark 커서. feed는 재전달될 수 있으므로 (entityType,entityId,surveyVersion,resourceVersion,changeType)로 중복 처리.
- 커서는 사용자·권한·시작 sequence·필터를 서명해 고정. 30일 동안 미사용 또는 필요한 로그가 정리되면 409 SYNC_CURSOR_EXPIRED. 임의 변조/타인 커서는 400 INVALID_CURSOR. limit는 1~100, 기본 50.
- changeType UPSERT는 상세 재조회 지시, DELETE는 로컬 목록에서 제거/만료 표시하는 tombstone. 삭제했다고 미전송/진행 중 로컬 자료를 지우지 않는다. tombstone에는 본문·응답자 정보 없음.

| entityType | entityId | surveyVersion | resourceVersion | 의미 |
| --- | --- | --- | --- | --- |
| ASSIGNMENT | 조사원 userId | null | slotVersion | 슬롯 교체/해제 모두 UPSERT; 조회 결과 assignment=null이면 해제 |
| SURVEY | surveyId | 해당 version | 운영 메타 버전 | 게시/중지/마감 UPSERT, 제거 DELETE |
| RESPONSE | responseId | null | Response.resourceVersion | 초안/제출/검수/표시 갱신 UPSERT, 만료 DELETE |
| USER | userId | null | User.resourceVersion | 계정/기기 상태 갱신 UPSERT |

- R 범위: 본인 USER/ASSIGNMENT/RESPONSE, 현재 할당 또는 보관 중 본인 응답이 참조하는 SURVEY. S/A는 모든 조사원 관련 자원; USER의 계정 세부 관리는 A만 가능하므로 S는 interviewer summary만 조회한다. feed에는 비밀번호/세션/권한 없는 본문을 담지 않는다.
- 로그는 권한 상실 시 원문을 전달하지 않는다. 401/403이면 먼저 인증 문제 해결. role/ownership을 cursor만 믿고 검사 생략하지 않는다.
- 커서 복구는 시작 시 cursor 없는 요청의 모든 페이지를 우선 읽어 high-watermark C를 확보→me/현재 할당/보관 중 응답 목록 전체/필요 설문 및 현황 재조회→C 이후 변경을 끝까지 반영 순서. 새 데이터와 겹치는 이벤트는 버전으로 무시한다. 복구 중 처리된 로그를 잃지 않도록 C를 마지막까지 보관한다.
- 서버 응답 목록에서 없어진 자료는 로컬에서 만료/권한 상태 확인. 로컬 미전송·DRAFT·고정 질문을 서버 목록으로 덮어쓰거나 삭제하지 않는다. 검수 누락 복구는 응답 목록+필요 상세 재조회로 수행한다.

## 11. 익명 응답 가져오기·오류 수집·재시도

### 11.1 익명 응답 일괄 가져오기

- 계정 없는 모드는 로컬 조사원 권한일 뿐 서버 계정/토큰이 아니다. 로그인 후 사용자가 고른 완료 응답만 전송한다. guest 근태 전송 API는 없다.
- 모든 item.deviceId는 요청 X-Device-ID 및 현재 R 활성 기기와 일치. 배열 내 localResponseId 중복은 최상위 422. operationId는 클라이언트 UUID v4로 Idempotency-Key와 같아야 한다.
- 작업 body와 입력 순서를 고정한다. 같은 operationId·다른 본문은 409 OPERATION_ID_CONFLICT. 같은 성공 작업 재전송은 저장된 항목 결과 반환. 다른 계정의 작업은 404.
- `(deviceId,localResponseId)`는 전체 계정 유일. 기존 같은 계정·같은 원본 성공이면 ALREADY_IMPORTED(현재 할당 재검사 생략), 다른 본문이면 SOURCE_CONTENT_CONFLICT. 다른 계정에 이미 귀속이면 SOURCE_ALREADY_CLAIMED, 다른 계정 ID/성공 정보 반환 금지.
- 새로운 항목은 현재 ACTIVE 할당과 surveyId/version이 정확히 일치해야 한다. 불일치/미할당은 ASSIGNMENT_MISMATCH. 슬롯 잠금으로 검사→계정 귀속→응답 생성 원자 실행. 아이템 사이 슬롯이 바뀌면 앞 항목 성공·뒤 항목 실패할 수 있다.
- 성공 응답은 SUBMITTED/revision=1/resourceVersion=1, 서버가 새 responseId 생성. 원본 startedAt/completedAt와 localSessionId를 유지. capturedByUserId=null; importedByUserId=ownerUserId=현재 R. 과거 실명 응답으로 위장하지 않는다.
- 원본 expired면 HTTP 200 항목 FAILED/error.code=RETENTION_EXPIRED/retryable=false. 모든 항목 expired여도 HTTP 200이다. 단일 응답 API의 410과 구분한다.
- 실패 항목을 수정/재시도하려면 기존 작업 결과를 확정 확인 후 실패 항목만 새 operationId로 요청. 같은 완료 operationId는 실패 결과도 재평가하지 않는다. 기존 성공 항목 포함 시 ALREADY_IMPORTED만 반환하여 중복 방지한다.
- POST는 COMPLETED를 반환하되 서버 작업은 durable하게 항목별 결과를 저장한다. 워커 중단 시 완료 항목 재실행 금지, 미완료 PENDING만 재개. 30초 lease 만료 후 재개 가능. 5분 넘게 재개 불가하면 미완료 항목을 INTERNAL_ERROR/retryable=true로 종료한다.
- 동시 같은 operationId POST는 409 OPERATION_IN_PROGRESS; GET에서 PROCESSING 상태 확인. 입력 순서대로 results 배열을 유지한다.
- 작업 결과 보관 만료는 min(작업 생성+3개월, 각 원본 응답의 유효 만료시각)로 결정한다. 이미 만료된 항목은 운영 결과를 접수일+3개월까지 보관 가능하지만 민감 응답 본문을 저장하지 않는다. 유효 항목의 가장 빠른 만료 후 작업 전체 결과를 제거한다. POST/GET 재시도도 보관 기한을 연장하지 않는다. 확정된 항목의 원본 body는 즉시 작업 저장소에서 제거하고 응답 저장소와 만료까지의 HMAC fingerprint만 유지한다.
- 어떤 항목이 성공했는지 불명확하면 로컬 자료를 잠그고 조회/같은 key 재전송. 다른 계정으로 전송하거나 새 responseId로 만들어 전송하지 않는다.

### 11.2 오류 수집

- 자체 API는 로그인 필요. 비로그인/익명 상태 오류는 로컬 비식별 큐에 보관하고 다음 로그인 후 전송하되 sessionMode를 당시 값으로 유지. 서버는 수신 계정과 수집 시 사용자를 같다고 단정하지 않는다.
- 서버 진단 데이터에 collectedUserId 입력 필드 없음. 필요 시 서버 내부에서 수신 계정 ID를 HMAC한 비식별 참조를 생성한다. 다른 계정의 로컬 오류 본문을 포함하지 않는다.
- severity는 WARNING/ERROR/FATAL. FATAL 수집도 네이티브/OOM/OS 종료 등 모든 크래시 방지를 보장하지 않는다. 앱 복구와 진단 업로드를 분리한다.
- acceptedIds는 중복 성공도 포함. 같은 eventId·다른 본문은 EVENT_ID_CONFLICT. 이벤트 내용 검증 실패와 만료는 rejected 항목 처리. 동일 batch 내 eventId 중복은 최상위 422. 진단 시각의 형식은 맞지만 미래 오차를 초과하면 rejected.code=VALIDATION_FAILED로 매핑한다.
- 비밀번호/토큰/이름/설문 답변/CSV/HTTP 전체 본문은 수집 금지. stack에는 코드 위치와 예외 유형만 남긴다. 서버가 민감 패턴을 감지하면 SENSITIVE_DATA_REJECTED로 거절하고 내용 자체를 로그에 남기지 않는다.
- 네트워크 장애 보고가 다시 보고 이벤트를 무한 생성하지 않도록 이 API 자신의 오류는 큐에 넣지 않는다. 자체 큐 최대 100건/1 MiB, oldest-first 조기 폐기는 진단에만 적용.
- Crashlytics는 별도 앱 진단 선택 구현이며 비로그인 진단 경로로 사용할 수 있다. 자체 API의 인증을 우회하거나 외부 제공자 보관을 3개월로 자동 간주하지 않는다. 외부 진단 설정은 배포 시 검증한다.

### 11.3 재시도·로컬 Outbox

| 조건 | 근태·초안·제출·익명 작업 전송의 처리 |
| --- | --- |
| 인터넷 없음 | 대기, HTTP 호출 전 횟수 증가 없음 |
| 연결 오류/타임아웃/408/429/5xx | 최초+추가 최대 3회, 최소 10/20/40초; Retry-After가 더 길면 그 값 |
| 409 OPERATION_IN_PROGRESS | 같은 요청으로 최대 3회, 최소 Retry-After와 backoff 중 큰 값; 작업 GET 병행 가능 |
| 401 | refresh 1회, 성공 시 원래 업무 요청 재개. 갱신 실패는 인증 대기 |
| 403 | BLOCKED; 권한 회복 전 자동 반복 금지 |
| 나머지 4xx | 실패 정보 표시, 자동 내용 수정·삭제 금지 |
| 3회 추가 실패 | FAILED 영속 저장, 사용자 수동 재시도까지 자동 중단 |
| 수동 재시도 | 새 회차 카운터, 같은 업무 ID/키/본문 유지 |

- 횟수는 HTTP 실제 전송 단위로 DB 관리. 인증 갱신 자체는 업무 네트워크 재시도 예산과 별도이며 원래 요청의 인증 재개는 한 번만 허용한다. 라이브러리 숨은 자동 재시도로 예산을 늘리지 않는다.
- 로컬 응답/근태와 Outbox enqueue는 같은 DB 트랜잭션. 근태는 sequence 순서로 전송, 응답 업로드보다 먼저 시도하되 근태 장애만으로 응답을 폐기하지 않는다.
- 온라인 할당 확인 실패는 새 시작 재시도 UI로 처리. 네트워크 오류를 자동 오프라인 허가로 바꾸지 않는다. 사용자가 오프라인 모드/명시적 캐시 진행을 선택한 경우에만 원래 정책 적용.
- 최종 근태 실패 표시: ON=`출근 기록 서버 저장에 실패했습니다.`, OFF=`퇴근 기록 서버 저장에 실패했습니다.`. 로컬 ON/OFF 기록은 유지한다. 익명 로컬 근태에는 서버 실패 표시 없음.
- 서버 접수 성공 후 로컬 SYNCED 반영 직전 종료되어도 업무 ID/키로 중복 방지. sync 상태 LOCAL_ONLY/PENDING/SENDING/SYNCED/FAILED/BLOCKED와 업무 status는 별도다.

## 12. 보관·만료·삭제 계약

- 3개월은 Asia/Seoul 달력 월 3개. 말일 보정(1월31일→4월30일 같은 KST 시각), expiresAt UTC 변환. 서버 now>=expiresAt이면 즉시 접근/수정 차단. 기간 계산의 기준은 최초 저장한 원시 시각과 미래 허용 오차 보정값이다.

| 대상 | 기산/기간 | 예외 |
| --- | --- | --- |
| 응답·revision·검수·관련 멱등 결과 | 최초 startedAt+3개월 | 조회·검수·재제출로 연장 금지 |
| 근태 이벤트 | timestamp+3개월 | 계정 현재 상태/다음 sequence 최소 운영 정보는 유지 |
| 종료 할당/감사 이력 | endedAt/changedAt+3개월 | 유효 응답 검증에 필요한 최소 grant 참조는 응답 만료까지 |
| 자체 오류 | occurredAt+3개월 | 진단 큐 조기 삭제 허용 |
| CSV 원본/암호화된 임시 비밀번호 | 업로드+10분 | commit 성공 시 즉시 삭제 |
| CSV 비밀 없는 결과/기타 관리 멱등 결과 | 생성 또는 완료+3개월 | 비밀번호·원본 CSV는 포함 금지 |
| 변경 로그/tombstone | 발생+30일 | 업무 본문 저장 금지 |
| refresh 복구 토큰 원문 | 회전+60초 | 암호화, 이외 토큰은 digest만 저장 |
| 계정/현재 할당/설문 정의/기기 폐기 표식 | 운영 설정 | 3개월 주기 삭제 대상 아님 |
| 로컬 설문 캐시 | 마지막 다운로드/온라인 200·304 유효성 확인+3개월 | 진행 중 응답에 필요한 정의는 그 응답과 보관 |
| 로컬 미전송/결과 미확인/진행 중 자료 | 원래 expiresAt 유지 | 자동 삭제 제외, 만료 경고·관리자 확인 |

- 로컬 자동 삭제 예외는 서버 무기한 접수 허용이 아니다. 만료된 신규 초안/제출/근태는 410 RETENTION_EXPIRED. 익명/오류 batch는 항목 실패. 원래 ID/시각을 바꿔 접수시키지 않는다.
- 만료 데이터는 목록·집계에서 즉시 제외하고 매일 03:00 KST 정리 작업으로 본문·revision·검수·관련 멱등 성공 결과를 제거한다. 유효한 최소 참조 외에 만료 답변 복제본을 남기지 않는다.
- 상세 조회: 만료 사실이 아직 확인 가능하면 410, 정리 후 식별 근거가 없으면 404. 새로운 오래된 body 재전송은 원래 시각으로 다시 410 검사한다.
- 백업은 최대 30일 rolling 보관, 만료 레코드의 민감 payload는 레코드별 암호화 키 폐기 방식으로 expiresAt 이후 복호화 불가하게 한다. 키 vault 백업/복원에도 삭제 tombstone을 우선 적용한다. 단순 live DB 삭제만으로 백업 삭제 완료라고 표현하지 않는다.
- 백업 복원 시 만료 필터/키 폐기 기록을 먼저 적용하고 서비스 공개. 추출본은 이번 API 범위에서 만들지 않는다. 운영상 별도 추출본을 만들면 동일 만료·접근 정책 책임을 적용한다.
- 만료 로컬 자료의 별도 회수/폐기 API는 범위 밖이다. 앱은 보존/경고까지 구현하고 자동 서버 우회 전송을 추가하지 않는다. 앱 삭제/기기 분실로 잃은 미전송 데이터를 서버가 복구할 수 없다.

## 13. 오류 코드 카탈로그

코드 문자열은 아래 enum만 사용한다. 메시지는 사용자 표시용 한국어로 바뀔 수 있으므로 앱 분기는 code/HTTP로 한다. batch에서 공통 코드를 재사용할 경우 최상위 HTTP는 200이고 해당 item.error 또는 rejected에 넣는다. 형식·인증 오류까지 항목 성공으로 감싸지 않는다.

| HTTP/위치 | code | retryable | 의미·추가 정보 |
| --- | --- | --- | --- |
| 400 | `INVALID_REQUEST` | false | JSON/path/header 문법 오류, 중복 JSON 키 |
| 400 | `MISSING_HEADER` | false | X-Device-ID/X-App-Version/필수 Idempotency-Key 없음 |
| 400 | `INVALID_QUERY` | false | 알 수 없는 query 또는 같은 query 키 중복 |
| 400 | `INVALID_CURSOR` | false | 서명·사용자·endpoint·필터가 다른 커서 |
| 400 | `INVALID_CSV` | false | UTF-8/헤더/CSV 문법 오류 또는 빈 데이터 |
| 401 | `UNAUTHENTICATED` | false | Access Token 없음/만료/폐기 |
| 401 | `INVALID_CREDENTIALS` | false | 일반 로그인 실패 |
| 401 | `INVALID_REFRESH_TOKEN` | false | Refresh Token 없음/만료/미등록 |
| 401 | `TOKEN_REUSE_DETECTED` | false | 사용 완료 토큰 재사용, 해당 family 폐기 |
| 403 | `FORBIDDEN` | false | 역할/범위 불허 |
| 403 | `ACCOUNT_DISABLED` | false | 로그인 후 계정 비활성화 |
| 403 | `DEVICE_REVOKED` | false | 폐기된 기기 |
| 403 | `ADMIN_ACCOUNT_PROTECTED` | false | API로 관리자 계정 변경/초기화 시도 |
| 404 | `NOT_FOUND` | false | 없거나 권한 때문에 존재를 숨기는 개별 자원 |
| 405 | `METHOD_NOT_ALLOWED` | false | 지원하지 않는 HTTP method; Allow 헤더 반환 |
| 408 | `REQUEST_TIMEOUT` | true | 요청 시간 초과 |
| 409 | `ID_ALREADY_EXISTS` | false | 계정 ID 충돌; CSV면 안전한 행 오류 fields 제공 |
| 409 | `ACTIVE_DEVICE_EXISTS` | false | R 다른 활성 기기 있음 |
| 409 | `DEVICE_MISMATCH` | false | 헤더/body/세션/대상 활성 기기 불일치 |
| 409 | `IDEMPOTENCY_CONFLICT` | false | 같은 멱등 키/업무 이벤트 ID에 다른 내용 |
| 409 | `OPERATION_IN_PROGRESS` | true | 같은 작업 처리 중; Retry-After 2초 |
| 409 | `OPERATION_ID_CONFLICT` | false | 같은 operationId 다른 본문 |
| 409 | `IMPORT_INVALID` | false | CSV 행 검증 실패 |
| 409 | `IMPORT_EXPIRED` | false | CSV 10분 유효 기간 경과 |
| 409 | `EVENT_GAP` | false | 앞선 sequence 누락; details.expectedSequence |
| 409 | `SEQUENCE_CONFLICT` | false | 이미 사용한 sequence에 다른 이벤트; details.expectedSequence |
| 409 | `ATTENDANCE_STATE_CONFLICT` | false | 같은 ON/OFF 반복; details.currentState/expectedSequence |
| 409 | `DRAFT_VERSION_CONFLICT` | false | 낡은 초안 버전 또는 동일 버전 내용 충돌 |
| 409 | `RESPONSE_STATE_CONFLICT` | false | 허용하지 않는 workflow 상태 변경 |
| 409 | `REVISION_CONFLICT` | false | 잘못된 revision/baseRevision 또는 이미 제출된 내용 충돌 |
| 409 | `RESPONSE_ID_CONFLICT` | false | 응답 ID의 불변 수집 메타 변경 |
| 409 | `SURVEY_CLOSED` | false | 마감 설문의 신규 초안/제출 수용 금지 |
| 409 | `CURSOR_EXPIRED` | false | 일반 페이지 커서 15분 만료 |
| 409 | `SYNC_CURSOR_EXPIRED` | false | 동기화 커서/필요 변경 로그 만료 |
| 410 | `RETENTION_EXPIRED` | false | 자원 보관 기간 만료 |
| 412 | `VERSION_MISMATCH` | false | If-Match 불일치; details.currentVersion |
| 413 | `PAYLOAD_TOO_LARGE` | false | 본문·파일 bytes 한도 초과 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | false | 지원하지 않는 Content-Type/Content-Encoding |
| 422 | `VALIDATION_FAILED` | false | 요청 타입/필수/길이/교차 필드/답변 값 오류 |
| 422 | `INVALID_TARGET_ROLE` | false | 조사원 대상 경로에 다른 역할 지정 |
| 422 | `INTERVIEWER_INACTIVE` | false | 비활성 조사원에 새 할당 |
| 422 | `SURVEY_NOT_ASSIGNABLE` | false | PUBLISHED가 아닌 설문 버전 할당 |
| 422 | `ASSIGNMENT_INVALID` | false | 원래 할당·버전·revision을 확인할 수 없음; 타인 권한 숨김은 404 우선 |
| 422 | `INVALID_ATTENDANCE_REFERENCE` | false | 본인 ON이 아닌 확인 가능한 근태 참조 |
| 422 | `UNSUPPORTED_SCHEMA_VERSION` | false | 지원하지 않는 설문 schemaVersion |
| 422 | `CLOCK_INVALID` | false | 300초 초과 미래 시각 또는 시간 선후 위반 |
| 428 | `PRECONDITION_REQUIRED` | false | If-Match 없음 |
| 429 | `RATE_LIMITED` | true | 요청 제한; Retry-After 및 retryAfterSeconds |
| 500 | `INTERNAL_ERROR` | true | 처리 오류. 내부 stack/쿼리/비밀 값 노출 금지 |
| 502 | `UPSTREAM_ERROR` | true | 상위 서비스 오류 |
| 503 | `SERVICE_UNAVAILABLE` | true | 일시 서비스 불가 |
| 504 | `GATEWAY_TIMEOUT` | true | 상위 서비스 응답 시간 초과 |
| 항목 | `ASSIGNMENT_MISMATCH` | false | 익명 신규 가져오기 현재 설문 ID/버전 불일치 |
| 항목 | `SOURCE_ALREADY_CLAIMED` | false | 다른 계정이 원본을 가져옴; 소유자 정보 없음 |
| 항목 | `SOURCE_CONTENT_CONFLICT` | false | 같은 익명 원본 식별자에 내용 변경 |
| 항목 | `EVENT_ID_CONFLICT` | false | 같은 진단 eventId 다른 내용 |
| 항목 | `SENSITIVE_DATA_REJECTED` | false | 진단에 민감 정보 감지 |

오류 우선순위: 요청 크기·파싱 → 인증/계정/기기 → 역할/객체 접근 → 정상 중복 성공(보관 만료 우선) → If-Match → 자원 상태·본문 업무 검증. 객체가 존재하지 않거나 숨겨야 하면 버전/상태를 노출하지 않고 404. 하나의 본문에 여러 필드 오류는 fields에 모으되 서로 다른 최상위 오류를 섞지 않는다.

익명 item 허용 실패 code: ASSIGNMENT_MISMATCH, SOURCE_ALREADY_CLAIMED, SOURCE_CONTENT_CONFLICT, NOT_FOUND, SURVEY_CLOSED, RETENTION_EXPIRED, VALIDATION_FAILED, CLOCK_INVALID, INTERNAL_ERROR. PUBLISHED가 아닌 SUSPENDED 설문의 새 익명 귀속은 ASSIGNMENT_MISMATCH로 거절한다. 진단 항목 code는 ErrorRejection 표의 enum을 따른다.

## 14. JSON 요청·응답 예시

아래는 API별 성공 예시다. 각 JSON은 독립된 시나리오이며 이 순서대로 서버를 호출하라는 테스트 스크립트가 아니다. 응답은 전체 envelope를 표시한다. 예시 토큰·커서는 형식용 문자열이며 실제 인증·서명을 갖지 않는다. headers의 실제 값은 §4/§6을 적용한다.

### 14.1 API-01 `POST /auth/login`

요청 body:
```json
{
  "id": "worker001",
  "pw": "Example!1234A",
  "deviceId": "00000000-0000-4000-8000-000000000003"
}
```

성공 응답:
```json
{
  "data": {
    "tokens": {
      "tokenType": "Bearer",
      "accessToken": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
      "expiresIn": 900,
      "accessExpiresAt": "2026-09-30T00:20:10.000Z",
      "refreshToken": "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
      "refreshExpiresIn": 2592000,
      "refreshExpiresAt": "2026-10-30T00:05:10.000Z",
      "sessionId": "00000000-0000-4000-8000-00000000000b",
      "credentialVersion": 1
    },
    "user": {
      "userId": "00000000-0000-4000-8000-000000000001",
      "id": "worker001",
      "name": "홍길동",
      "grade": 3,
      "role": "INTERVIEWER",
      "active": true,
      "resourceVersion": 2
    },
    "activeDeviceId": "00000000-0000-4000-8000-000000000003",
    "deviceNextSequence": 1
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000065",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.2 API-02 `POST /auth/refresh`

요청 body:
```json
{
  "refreshToken": "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
  "deviceId": "00000000-0000-4000-8000-000000000003"
}
```

성공 응답:
```json
{
  "data": {
    "tokenType": "Bearer",
    "accessToken": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    "expiresIn": 900,
    "accessExpiresAt": "2026-09-30T00:20:10.000Z",
    "refreshToken": "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
    "refreshExpiresIn": 2591000,
    "refreshExpiresAt": "2026-10-29T23:48:30.000Z",
    "sessionId": "00000000-0000-4000-8000-00000000000b",
    "credentialVersion": 1
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000066",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.3 API-03 `POST /auth/logout`

요청 body:
```json
{
  "refreshToken": "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
}
```

성공: `204 No Content`, JSON 본문 없음.

### 14.4 API-04 `GET /me`

성공 응답:
```json
{
  "data": {
    "user": {
      "userId": "00000000-0000-4000-8000-000000000001",
      "id": "worker001",
      "name": "홍길동",
      "grade": 3,
      "role": "INTERVIEWER",
      "active": true,
      "resourceVersion": 2
    },
    "credentialVersion": 1,
    "activeDeviceId": "00000000-0000-4000-8000-000000000003",
    "deviceNextSequence": 2,
    "anonymousImportPolicy": {
      "enabled": true,
      "requiresCurrentAssignment": true,
      "requiresExactSurveyVersion": true,
      "requiresExplicitSelection": true,
      "maxItemsPerRequest": 20,
      "maxBodyBytes": 1048576
    }
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000068",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.5 API-05 `GET /users`

Query 예: `?role=INTERVIEWER&limit=50`

성공 응답:
```json
{
  "data": {
    "items": [
      {
        "userId": "00000000-0000-4000-8000-000000000001",
        "id": "worker001",
        "name": "홍길동",
        "grade": 3,
        "role": "INTERVIEWER",
        "active": true,
        "resourceVersion": 2
      }
    ],
    "nextCursor": null
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000069",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.6 API-06 `POST /users`

요청 body:
```json
{
  "id": "worker001",
  "pw": "Example!1234A",
  "name": "홍길동",
  "grade": 3
}
```

성공 응답:
```json
{
  "data": {
    "userId": "00000000-0000-4000-8000-000000000001",
    "id": "worker001",
    "name": "홍길동",
    "grade": 3,
    "role": "INTERVIEWER",
    "active": true,
    "resourceVersion": 1
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-00000000006a",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.7 API-07 `PATCH /users/{userId}`

요청 body:
```json
{
  "name": "김조사"
}
```

성공 응답:
```json
{
  "data": {
    "userId": "00000000-0000-4000-8000-000000000001",
    "id": "worker001",
    "name": "김조사",
    "grade": 3,
    "role": "INTERVIEWER",
    "active": true,
    "resourceVersion": 3
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-00000000006b",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.8 API-08 `POST /users/{userId}/password-reset`

요청 body:
```json
{
  "newPassword": "NewExample!1234A"
}
```

성공 응답:
```json
{
  "data": {
    "userId": "00000000-0000-4000-8000-000000000001",
    "credentialVersion": 2,
    "resourceVersion": 3,
    "sessionsRevoked": true
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-00000000006c",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.9 API-09 `POST /users/{userId}/device-release`

요청 body:
```json
{
  "deviceId": "00000000-0000-4000-8000-000000000003",
  "reason": "기기 교체"
}
```

성공 응답:
```json
{
  "data": {
    "user": {
      "userId": "00000000-0000-4000-8000-000000000001",
      "id": "worker001",
      "name": "홍길동",
      "grade": 3,
      "role": "INTERVIEWER",
      "active": true,
      "resourceVersion": 3
    },
    "activeDeviceId": null,
    "releasedDeviceId": "00000000-0000-4000-8000-000000000003",
    "releasedAt": "2026-09-30T00:05:10.000Z",
    "sessionsRevoked": true
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-00000000006d",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.10 API-10 `POST /user-imports/validate`

요청 multipart의 `file` 내용:

```csv
id,pw,name,grade
worker001,Example!1234A,홍길동,3
```

성공 응답:
```json
{
  "data": {
    "importId": "00000000-0000-4000-8000-000000000008",
    "status": "VALID",
    "totalRows": 1,
    "validRows": 1,
    "expiresAt": "2026-09-30T00:15:10.000Z",
    "errors": []
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-00000000006e",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.11 API-11 `POST /user-imports/{importId}/commit`

성공 응답:
```json
{
  "data": {
    "importId": "00000000-0000-4000-8000-000000000008",
    "status": "COMPLETED",
    "createdCount": 1,
    "users": [
      {
        "row": 2,
        "userId": "00000000-0000-4000-8000-000000000001",
        "id": "worker001",
        "grade": 3
      }
    ],
    "completedAt": "2026-09-30T00:05:10.000Z",
    "resultExpiresAt": "2026-12-30T00:05:10.000Z"
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-00000000006f",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.12 API-12 `GET /user-imports/{importId}`

성공 응답:
```json
{
  "data": {
    "importId": "00000000-0000-4000-8000-000000000008",
    "status": "VALID",
    "totalRows": 1,
    "validRows": 1,
    "expiresAt": "2026-09-30T00:15:10.000Z",
    "errors": []
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000070",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.13 API-13 `GET /me/survey-assignment`

성공 응답:
```json
{
  "data": {
    "assignment": {
      "assignmentId": "00000000-0000-4000-8000-000000000005",
      "userId": "00000000-0000-4000-8000-000000000001",
      "surveyId": "00000000-0000-4000-8000-000000000004",
      "surveyVersion": 1,
      "assignmentRevision": 1,
      "status": "ACTIVE",
      "assignedBy": "00000000-0000-4000-8000-000000000002",
      "assignedAt": "2026-09-29T00:00:00.000Z",
      "endedAt": null
    },
    "slotVersion": 2
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000071",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.14 API-14 `GET /surveys`

Query 예: `?status=PUBLISHED&limit=50`

성공 응답:
```json
{
  "data": {
    "items": [
      {
        "surveyId": "00000000-0000-4000-8000-000000000004",
        "title": "현장 만족도 조사",
        "version": 1,
        "status": "PUBLISHED",
        "publishedAt": "2026-09-28T00:00:00.000Z"
      }
    ],
    "nextCursor": null
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000072",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.15 API-15 `GET /surveys/{id}/versions/{version}`

성공 응답:
```json
{
  "data": {
    "surveyId": "00000000-0000-4000-8000-000000000004",
    "version": 1,
    "schemaVersion": 1,
    "title": "현장 만족도 조사",
    "status": "PUBLISHED",
    "publishedAt": "2026-09-28T00:00:00.000Z",
    "questions": [
      {
        "id": "q1",
        "type": "SINGLE_CHOICE",
        "required": true,
        "title": "서비스를 이용한 적이 있나요?",
        "options": [
          {
            "id": "yes",
            "label": "있다"
          },
          {
            "id": "no",
            "label": "없다"
          }
        ]
      },
      {
        "id": "q2",
        "type": "TEXT",
        "required": false,
        "title": "개선 의견을 적어 주세요.",
        "validation": {
          "maxLength": 2000
        }
      },
      {
        "id": "q3",
        "type": "SCALE_7",
        "required": true,
        "title": "만족도는 어느 정도인가요?",
        "min": 1,
        "max": 7,
        "labels": {
          "1": "매우 불만족",
          "7": "매우 만족"
        }
      }
    ]
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000073",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.16 API-16 `GET /interviewers`

Query 예: `?limit=50`

성공 응답:
```json
{
  "data": {
    "items": [
      {
        "userId": "00000000-0000-4000-8000-000000000001",
        "id": "worker001",
        "name": "홍길동",
        "active": true,
        "currentAssignment": {
          "assignmentId": "00000000-0000-4000-8000-000000000005",
          "userId": "00000000-0000-4000-8000-000000000001",
          "surveyId": "00000000-0000-4000-8000-000000000004",
          "surveyVersion": 1,
          "assignmentRevision": 1,
          "status": "ACTIVE",
          "assignedBy": "00000000-0000-4000-8000-000000000002",
          "assignedAt": "2026-09-29T00:00:00.000Z",
          "endedAt": null
        }
      }
    ],
    "nextCursor": null
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000074",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.17 API-17 `GET /interviewers/{userId}/survey-assignment`

성공 응답:
```json
{
  "data": {
    "assignment": {
      "assignmentId": "00000000-0000-4000-8000-000000000005",
      "userId": "00000000-0000-4000-8000-000000000001",
      "surveyId": "00000000-0000-4000-8000-000000000004",
      "surveyVersion": 1,
      "assignmentRevision": 1,
      "status": "ACTIVE",
      "assignedBy": "00000000-0000-4000-8000-000000000002",
      "assignedAt": "2026-09-29T00:00:00.000Z",
      "endedAt": null
    },
    "slotVersion": 2
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000075",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.18 API-18 `PUT /interviewers/{userId}/survey-assignment`

요청 body:
```json
{
  "surveyId": "00000000-0000-4000-8000-000000000004",
  "surveyVersion": 1,
  "reason": "담당 조사 배정"
}
```

성공 응답:
```json
{
  "data": {
    "assignment": {
      "assignmentId": "00000000-0000-4000-8000-000000000005",
      "userId": "00000000-0000-4000-8000-000000000001",
      "surveyId": "00000000-0000-4000-8000-000000000004",
      "surveyVersion": 1,
      "assignmentRevision": 1,
      "status": "ACTIVE",
      "assignedBy": "00000000-0000-4000-8000-000000000002",
      "assignedAt": "2026-09-29T00:00:00.000Z",
      "endedAt": null
    },
    "slotVersion": 2
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000076",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.19 API-19 `GET /interviewers/{userId}/survey-assignment-history`

Query 예: `?limit=50`

성공 응답:
```json
{
  "data": {
    "items": [
      {
        "eventId": "00000000-0000-4000-8000-000000000015",
        "userId": "00000000-0000-4000-8000-000000000001",
        "beforeAssignmentId": null,
        "afterAssignmentId": "00000000-0000-4000-8000-000000000005",
        "beforeAssignment": null,
        "afterAssignment": {
          "assignmentId": "00000000-0000-4000-8000-000000000005",
          "userId": "00000000-0000-4000-8000-000000000001",
          "surveyId": "00000000-0000-4000-8000-000000000004",
          "surveyVersion": 1,
          "assignmentRevision": 1,
          "status": "ACTIVE",
          "assignedBy": "00000000-0000-4000-8000-000000000002",
          "assignedAt": "2026-09-29T00:00:00.000Z",
          "endedAt": null
        },
        "actorId": "00000000-0000-4000-8000-000000000002",
        "reason": "담당 조사 배정",
        "changedAt": "2026-09-29T00:00:00.000Z"
      }
    ],
    "nextCursor": null
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000077",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.20 API-20 `GET /sync/changes`

Query 예: `?limit=50`

성공 응답:
```json
{
  "data": {
    "items": [
      {
        "entityType": "ASSIGNMENT",
        "entityId": "00000000-0000-4000-8000-000000000001",
        "surveyVersion": null,
        "resourceVersion": 2,
        "changeType": "UPSERT",
        "changedAt": "2026-09-29T00:00:00.000Z"
      }
    ],
    "nextCursor": "opaque-signed-change-cursor",
    "hasMore": false,
    "serverTime": "2026-09-30T00:05:10.000Z"
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000078",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.21 API-21 `POST /attendance-events`

요청 body:
```json
{
  "idx": "00000000-0000-4000-8000-000000000007",
  "status": "ON",
  "timestamp": "2026-09-30T00:00:00.000Z",
  "deviceId": "00000000-0000-4000-8000-000000000003",
  "sequence": 1,
  "zoneId": "Asia/Seoul"
}
```

성공 응답:
```json
{
  "data": {
    "idx": "00000000-0000-4000-8000-000000000007",
    "status": "ON",
    "timestamp": "2026-09-30T00:00:00.000Z",
    "deviceId": "00000000-0000-4000-8000-000000000003",
    "sequence": 1,
    "userId": "00000000-0000-4000-8000-000000000001",
    "serverReceivedAt": "2026-09-30T00:00:00.000Z",
    "effectiveState": "ON",
    "effectiveAt": "2026-09-30T00:00:00.000Z",
    "expiresAt": "2026-12-30T00:00:00.000Z",
    "conflict": false,
    "timeWarning": null
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000079",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.22 API-22 `GET /attendance-events`

Query 예: `?from=2026-09-29T15%3A00%3A00.000Z&to=2026-09-30T15%3A00%3A00.000Z&limit=50`

성공 응답:
```json
{
  "data": {
    "items": [
      {
        "idx": "00000000-0000-4000-8000-000000000007",
        "status": "ON",
        "timestamp": "2026-09-30T00:00:00.000Z",
        "deviceId": "00000000-0000-4000-8000-000000000003",
        "sequence": 1,
        "userId": "00000000-0000-4000-8000-000000000001",
        "serverReceivedAt": "2026-09-30T00:00:00.000Z",
        "effectiveState": "ON",
        "effectiveAt": "2026-09-30T00:00:00.000Z",
        "expiresAt": "2026-12-30T00:00:00.000Z",
        "conflict": false,
        "timeWarning": null
      }
    ],
    "nextCursor": null
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-00000000007a",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.23 API-23 `GET /dashboards/me`

Query 예: `?date=2026-09-30`

성공 응답:
```json
{
  "data": {
    "date": "2026-09-30",
    "attendance": {
      "userId": "00000000-0000-4000-8000-000000000001",
      "name": "홍길동",
      "active": true,
      "date": "2026-09-30",
      "state": "ON",
      "lastEventAt": "2026-09-30T00:00:00.000Z",
      "lastReceivedAt": "2026-09-30T00:00:00.000Z",
      "stateAsOf": "2026-09-30T00:05:10.000Z"
    },
    "progress": [
      {
        "userId": "00000000-0000-4000-8000-000000000001",
        "name": "홍길동",
        "surveyId": "00000000-0000-4000-8000-000000000004",
        "surveyTitle": "현장 만족도 조사",
        "surveyVersion": 1,
        "date": "2026-09-30",
        "draftCount": 0,
        "submittedTotal": 1,
        "pendingReviewCount": 1,
        "approvedCount": 0,
        "returnedCount": 0,
        "lastReceivedAt": "2026-09-30T00:05:10.000Z"
      }
    ],
    "currentAssignment": {
      "assignment": {
        "assignmentId": "00000000-0000-4000-8000-000000000005",
        "userId": "00000000-0000-4000-8000-000000000001",
        "surveyId": "00000000-0000-4000-8000-000000000004",
        "surveyVersion": 1,
        "assignmentRevision": 1,
        "status": "ACTIVE",
        "assignedBy": "00000000-0000-4000-8000-000000000002",
        "assignedAt": "2026-09-29T00:00:00.000Z",
        "endedAt": null
      },
      "slotVersion": 2
    },
    "asOf": "2026-09-30T00:05:10.000Z"
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-00000000007b",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.24 API-24 `GET /dashboards/attendance`

Query 예: `?date=2026-09-30&limit=50`

성공 응답:
```json
{
  "data": {
    "items": [
      {
        "userId": "00000000-0000-4000-8000-000000000001",
        "name": "홍길동",
        "active": true,
        "date": "2026-09-30",
        "state": "ON",
        "lastEventAt": "2026-09-30T00:00:00.000Z",
        "lastReceivedAt": "2026-09-30T00:00:00.000Z",
        "stateAsOf": "2026-09-30T00:05:10.000Z"
      }
    ],
    "nextCursor": null,
    "asOf": "2026-09-30T00:05:10.000Z"
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-00000000007c",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.25 API-25 `GET /dashboards/progress`

Query 예: `?date=2026-09-30&limit=50`

성공 응답:
```json
{
  "data": {
    "items": [
      {
        "userId": "00000000-0000-4000-8000-000000000001",
        "name": "홍길동",
        "surveyId": "00000000-0000-4000-8000-000000000004",
        "surveyTitle": "현장 만족도 조사",
        "surveyVersion": 1,
        "date": "2026-09-30",
        "draftCount": 0,
        "submittedTotal": 1,
        "pendingReviewCount": 1,
        "approvedCount": 0,
        "returnedCount": 0,
        "lastReceivedAt": "2026-09-30T00:05:10.000Z"
      }
    ],
    "nextCursor": null,
    "asOf": "2026-09-30T00:05:10.000Z"
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-00000000007d",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.26 API-26 `PUT /responses/{id}/draft`

요청 body:
```json
{
  "assignmentId": "00000000-0000-4000-8000-000000000005",
  "assignmentRevision": 1,
  "surveyId": "00000000-0000-4000-8000-000000000004",
  "surveyVersion": 1,
  "assignmentCheckedAt": "2026-09-30T00:00:00.000Z",
  "assignmentCheckMode": "ONLINE_VERIFIED",
  "startedAt": "2026-09-30T00:00:00.000Z",
  "attendanceEventId": "00000000-0000-4000-8000-000000000007",
  "revision": 1,
  "baseRevision": null,
  "draftVersion": 1,
  "answers": [
    {
      "questionId": "q1",
      "type": "SINGLE_CHOICE",
      "value": "yes"
    }
  ]
}
```

성공 응답:
```json
{
  "data": {
    "responseId": "00000000-0000-4000-8000-000000000006",
    "revision": 1,
    "draftRevision": 1,
    "draftVersion": 1,
    "resourceVersion": 1,
    "status": "DRAFT",
    "savedAt": "2026-09-30T00:00:00.000Z",
    "expiresAt": "2026-12-30T00:00:00.000Z",
    "reviewFlags": []
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-00000000007e",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.27 API-27 `POST /responses/{id}/submit`

요청 body:
```json
{
  "assignmentId": "00000000-0000-4000-8000-000000000005",
  "assignmentRevision": 1,
  "surveyId": "00000000-0000-4000-8000-000000000004",
  "surveyVersion": 1,
  "assignmentCheckedAt": "2026-09-30T00:00:00.000Z",
  "assignmentCheckMode": "ONLINE_VERIFIED",
  "startedAt": "2026-09-30T00:00:00.000Z",
  "attendanceEventId": "00000000-0000-4000-8000-000000000007",
  "revision": 1,
  "baseRevision": null,
  "completedAt": "2026-09-30T00:05:00.000Z",
  "answers": [
    {
      "questionId": "q1",
      "type": "SINGLE_CHOICE",
      "value": "yes"
    },
    {
      "questionId": "q2",
      "type": "TEXT",
      "value": null
    },
    {
      "questionId": "q3",
      "type": "SCALE_7",
      "value": 6
    }
  ]
}
```

성공 응답:
```json
{
  "data": {
    "responseId": "00000000-0000-4000-8000-000000000006",
    "ownerUserId": "00000000-0000-4000-8000-000000000001",
    "ownerName": "홍길동",
    "surveyId": "00000000-0000-4000-8000-000000000004",
    "surveyTitle": "현장 만족도 조사",
    "surveyVersion": 1,
    "revision": 1,
    "resourceVersion": 2,
    "status": "SUBMITTED",
    "collectionMode": "AUTHENTICATED",
    "capturedByUserId": "00000000-0000-4000-8000-000000000001",
    "importedByUserId": null,
    "startedAt": "2026-09-30T00:00:00.000Z",
    "completedAt": "2026-09-30T00:05:00.000Z",
    "firstSubmittedAt": "2026-09-30T00:05:10.000Z",
    "lastSubmittedAt": "2026-09-30T00:05:10.000Z",
    "serverReceivedAt": "2026-09-30T00:00:00.000Z",
    "updatedAt": "2026-09-30T00:05:10.000Z",
    "expiresAt": "2026-12-30T00:00:00.000Z",
    "reviewFlags": [],
    "hasWorkingDraft": false,
    "assignmentId": "00000000-0000-4000-8000-000000000005",
    "assignmentRevision": 1,
    "assignmentCheckedAt": "2026-09-30T00:00:00.000Z",
    "assignmentCheckMode": "ONLINE_VERIFIED",
    "attendanceEventId": "00000000-0000-4000-8000-000000000007",
    "localResponseId": null,
    "localSessionId": null,
    "answers": [
      {
        "questionId": "q1",
        "type": "SINGLE_CHOICE",
        "value": "yes"
      },
      {
        "questionId": "q2",
        "type": "TEXT",
        "value": null
      },
      {
        "questionId": "q3",
        "type": "SCALE_7",
        "value": 6
      }
    ],
    "surveyDefinition": {
      "surveyId": "00000000-0000-4000-8000-000000000004",
      "version": 1,
      "schemaVersion": 1,
      "title": "현장 만족도 조사",
      "status": "PUBLISHED",
      "publishedAt": "2026-09-28T00:00:00.000Z",
      "questions": [
        {
          "id": "q1",
          "type": "SINGLE_CHOICE",
          "required": true,
          "title": "서비스를 이용한 적이 있나요?",
          "options": [
            {
              "id": "yes",
              "label": "있다"
            },
            {
              "id": "no",
              "label": "없다"
            }
          ]
        },
        {
          "id": "q2",
          "type": "TEXT",
          "required": false,
          "title": "개선 의견을 적어 주세요.",
          "validation": {
            "maxLength": 2000
          }
        },
        {
          "id": "q3",
          "type": "SCALE_7",
          "required": true,
          "title": "만족도는 어느 정도인가요?",
          "min": 1,
          "max": 7,
          "labels": {
            "1": "매우 불만족",
            "7": "매우 만족"
          }
        }
      ]
    },
    "draft": null,
    "reviews": [],
    "revisions": [
      {
        "revision": 1,
        "submittedAt": "2026-09-30T00:05:10.000Z",
        "completedAt": "2026-09-30T00:05:00.000Z",
        "status": "SUBMITTED"
      }
    ]
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-00000000007f",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.28 API-28 `GET /responses`

Query 예: `?status=SUBMITTED&limit=50`

성공 응답:
```json
{
  "data": {
    "items": [
      {
        "responseId": "00000000-0000-4000-8000-000000000006",
        "ownerUserId": "00000000-0000-4000-8000-000000000001",
        "ownerName": "홍길동",
        "surveyId": "00000000-0000-4000-8000-000000000004",
        "surveyTitle": "현장 만족도 조사",
        "surveyVersion": 1,
        "revision": 1,
        "resourceVersion": 2,
        "status": "SUBMITTED",
        "collectionMode": "AUTHENTICATED",
        "capturedByUserId": "00000000-0000-4000-8000-000000000001",
        "importedByUserId": null,
        "startedAt": "2026-09-30T00:00:00.000Z",
        "completedAt": "2026-09-30T00:05:00.000Z",
        "firstSubmittedAt": "2026-09-30T00:05:10.000Z",
        "lastSubmittedAt": "2026-09-30T00:05:10.000Z",
        "serverReceivedAt": "2026-09-30T00:00:00.000Z",
        "updatedAt": "2026-09-30T00:05:10.000Z",
        "expiresAt": "2026-12-30T00:00:00.000Z",
        "reviewFlags": [],
        "hasWorkingDraft": false
      }
    ],
    "nextCursor": null
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000080",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.29 API-29 `GET /responses/{id}`

성공 응답:
```json
{
  "data": {
    "responseId": "00000000-0000-4000-8000-000000000006",
    "ownerUserId": "00000000-0000-4000-8000-000000000001",
    "ownerName": "홍길동",
    "surveyId": "00000000-0000-4000-8000-000000000004",
    "surveyTitle": "현장 만족도 조사",
    "surveyVersion": 1,
    "revision": 1,
    "resourceVersion": 2,
    "status": "SUBMITTED",
    "collectionMode": "AUTHENTICATED",
    "capturedByUserId": "00000000-0000-4000-8000-000000000001",
    "importedByUserId": null,
    "startedAt": "2026-09-30T00:00:00.000Z",
    "completedAt": "2026-09-30T00:05:00.000Z",
    "firstSubmittedAt": "2026-09-30T00:05:10.000Z",
    "lastSubmittedAt": "2026-09-30T00:05:10.000Z",
    "serverReceivedAt": "2026-09-30T00:00:00.000Z",
    "updatedAt": "2026-09-30T00:05:10.000Z",
    "expiresAt": "2026-12-30T00:00:00.000Z",
    "reviewFlags": [],
    "hasWorkingDraft": false,
    "assignmentId": "00000000-0000-4000-8000-000000000005",
    "assignmentRevision": 1,
    "assignmentCheckedAt": "2026-09-30T00:00:00.000Z",
    "assignmentCheckMode": "ONLINE_VERIFIED",
    "attendanceEventId": "00000000-0000-4000-8000-000000000007",
    "localResponseId": null,
    "localSessionId": null,
    "answers": [
      {
        "questionId": "q1",
        "type": "SINGLE_CHOICE",
        "value": "yes"
      },
      {
        "questionId": "q2",
        "type": "TEXT",
        "value": null
      },
      {
        "questionId": "q3",
        "type": "SCALE_7",
        "value": 6
      }
    ],
    "surveyDefinition": {
      "surveyId": "00000000-0000-4000-8000-000000000004",
      "version": 1,
      "schemaVersion": 1,
      "title": "현장 만족도 조사",
      "status": "PUBLISHED",
      "publishedAt": "2026-09-28T00:00:00.000Z",
      "questions": [
        {
          "id": "q1",
          "type": "SINGLE_CHOICE",
          "required": true,
          "title": "서비스를 이용한 적이 있나요?",
          "options": [
            {
              "id": "yes",
              "label": "있다"
            },
            {
              "id": "no",
              "label": "없다"
            }
          ]
        },
        {
          "id": "q2",
          "type": "TEXT",
          "required": false,
          "title": "개선 의견을 적어 주세요.",
          "validation": {
            "maxLength": 2000
          }
        },
        {
          "id": "q3",
          "type": "SCALE_7",
          "required": true,
          "title": "만족도는 어느 정도인가요?",
          "min": 1,
          "max": 7,
          "labels": {
            "1": "매우 불만족",
            "7": "매우 만족"
          }
        }
      ]
    },
    "draft": null,
    "reviews": [],
    "revisions": [
      {
        "revision": 1,
        "submittedAt": "2026-09-30T00:05:10.000Z",
        "completedAt": "2026-09-30T00:05:00.000Z",
        "status": "SUBMITTED"
      }
    ]
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000081",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.30 API-30 `POST /responses/{id}/reviews`

요청 body:
```json
{
  "revision": 1,
  "decision": "RETURN",
  "reason": "응답 내용을 확인해 주세요.",
  "questionIds": [
    "q1"
  ]
}
```

성공 응답:
```json
{
  "data": {
    "reviewId": "00000000-0000-4000-8000-00000000000c",
    "responseId": "00000000-0000-4000-8000-000000000006",
    "revision": 1,
    "decision": "RETURN",
    "reason": "응답 내용을 확인해 주세요.",
    "questionIds": [
      "q1"
    ],
    "reviewerId": "00000000-0000-4000-8000-000000000002",
    "reviewedAt": "2026-09-30T00:05:10.000Z",
    "resourceVersion": 3
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000082",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.31 API-31 `POST /offline-response-imports`

요청 body:
```json
{
  "operationId": "00000000-0000-4000-8000-000000000009",
  "items": [
    {
      "localResponseId": "00000000-0000-4000-8000-00000000000a",
      "deviceId": "00000000-0000-4000-8000-000000000003",
      "localSessionId": "00000000-0000-4000-8000-00000000000b",
      "surveyId": "00000000-0000-4000-8000-000000000004",
      "surveyVersion": 1,
      "startedAt": "2026-09-30T00:00:00.000Z",
      "completedAt": "2026-09-30T00:05:00.000Z",
      "answers": [
        {
          "questionId": "q1",
          "type": "SINGLE_CHOICE",
          "value": "yes"
        },
        {
          "questionId": "q2",
          "type": "TEXT",
          "value": null
        },
        {
          "questionId": "q3",
          "type": "SCALE_7",
          "value": 6
        }
      ]
    }
  ]
}
```

성공 응답:
```json
{
  "data": {
    "operationId": "00000000-0000-4000-8000-000000000009",
    "status": "COMPLETED",
    "createdAt": "2026-09-30T00:05:10.000Z",
    "completedAt": "2026-09-30T00:05:10.000Z",
    "resultExpiresAt": "2026-12-30T00:00:00.000Z",
    "results": [
      {
        "localResponseId": "00000000-0000-4000-8000-00000000000a",
        "status": "IMPORTED",
        "responseId": "00000000-0000-4000-8000-000000000006",
        "ownerUserId": "00000000-0000-4000-8000-000000000001",
        "revision": 1,
        "resourceVersion": 1,
        "expiresAt": "2026-12-30T00:00:00.000Z",
        "error": null
      }
    ]
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000083",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.32 API-32 `GET /offline-response-imports/{operationId}`

성공 응답:
```json
{
  "data": {
    "operationId": "00000000-0000-4000-8000-000000000009",
    "status": "COMPLETED",
    "createdAt": "2026-09-30T00:05:10.000Z",
    "completedAt": "2026-09-30T00:05:10.000Z",
    "resultExpiresAt": "2026-12-30T00:00:00.000Z",
    "results": [
      {
        "localResponseId": "00000000-0000-4000-8000-00000000000a",
        "status": "IMPORTED",
        "responseId": "00000000-0000-4000-8000-000000000006",
        "ownerUserId": "00000000-0000-4000-8000-000000000001",
        "revision": 1,
        "resourceVersion": 1,
        "expiresAt": "2026-12-30T00:00:00.000Z",
        "error": null
      }
    ]
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000084",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.33 API-33 `POST /client-errors/batch`

요청 body:
```json
{
  "events": [
    {
      "eventId": "00000000-0000-4000-8000-000000000014",
      "occurredAt": "2026-09-30T00:00:00.000Z",
      "code": "NETWORK_TIMEOUT",
      "severity": "WARNING",
      "screenId": "survey",
      "appVersion": "1.0.0",
      "osVersion": "8.0.0",
      "requestId": null,
      "sanitizedStack": null,
      "networkState": "OFFLINE",
      "sessionMode": "AUTHENTICATED"
    }
  ]
}
```

성공 응답:
```json
{
  "data": {
    "acceptedIds": [
      "00000000-0000-4000-8000-000000000014"
    ],
    "rejected": []
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-000000000085",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.34 근태 순서 누락 — HTTP 409

```json
{
  "error": {
    "code": "EVENT_GAP",
    "message": "앞선 출퇴근 기록을 먼저 전송해 주세요.",
    "retryable": false,
    "fields": [],
    "details": {
      "expectedSequence": 2,
      "currentState": null,
      "currentVersion": null,
      "retryAfterSeconds": null,
      "serverTime": null,
      "conflictingFields": []
    }
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-0000000000c8",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.35 익명 항목 일부 실패 — HTTP 200

```json
{
  "data": {
    "operationId": "00000000-0000-4000-8000-000000000009",
    "status": "COMPLETED",
    "createdAt": "2026-09-30T00:05:10.000Z",
    "completedAt": "2026-09-30T00:05:10.000Z",
    "resultExpiresAt": "2026-12-30T00:00:00.000Z",
    "results": [
      {
        "localResponseId": "00000000-0000-4000-8000-00000000000a",
        "status": "IMPORTED",
        "responseId": "00000000-0000-4000-8000-000000000006",
        "ownerUserId": "00000000-0000-4000-8000-000000000001",
        "revision": 1,
        "resourceVersion": 1,
        "expiresAt": "2026-12-30T00:00:00.000Z",
        "error": null
      },
      {
        "localResponseId": "00000000-0000-4000-8000-000000000016",
        "status": "FAILED",
        "responseId": null,
        "ownerUserId": null,
        "revision": null,
        "resourceVersion": null,
        "expiresAt": null,
        "error": {
          "code": "ASSIGNMENT_MISMATCH",
          "message": "현재 할당 설문과 일치하지 않습니다.",
          "retryable": false,
          "fields": [],
          "details": {
            "expectedSequence": null,
            "currentState": null,
            "currentVersion": null,
            "retryAfterSeconds": null,
            "serverTime": null,
            "conflictingFields": []
          }
        }
      }
    ]
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-0000000000c9",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.36 미할당 — HTTP 200

```json
{
  "data": {
    "assignment": null,
    "slotVersion": 1
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-0000000000ca",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

### 14.37 반려 보완 초안 성공 — HTTP 200

```json
{
  "data": {
    "responseId": "00000000-0000-4000-8000-000000000006",
    "revision": 1,
    "draftRevision": 2,
    "draftVersion": 1,
    "resourceVersion": 4,
    "status": "RETURNED",
    "savedAt": "2026-09-30T00:05:10.000Z",
    "expiresAt": "2026-12-30T00:00:00.000Z",
    "reviewFlags": []
  },
  "meta": {
    "requestId": "00000000-0000-4000-8000-0000000000cb",
    "serverTime": "2026-09-30T00:05:10.000Z"
  }
}
```

## 15. 데이터 무결성·구현 수용 기준

### 15.1 저장소 불변 조건

| 대상 | 필수 조건 |
| --- | --- |
| 계정 | 정규화 id 유일, grade-role 일치, credential/resource version 원자 갱신 |
| 활성 할당 | userId별 슬롯 유일, ACTIVE 최대 1개, 할당 교체·감사·feed 함께 commit |
| 설문 | surveyId/version 유일, 게시 내용 불변, 상태 메타 버전 별도 |
| 근태 | idx 유일, userId/deviceId/sequence 유일, 현재 상태·카운터 원자 갱신 |
| 응답 | responseId 소유 불변, responseId/revision 유일, 원래 정의 유지 |
| 초안 | 응답별 작업 revision 최대 1개, draftVersion 단조 증가 |
| 익명 | deviceId/localResponseId 전 계정 유일, item 귀속·현재 할당 검사·응답 생성 원자 |
| 검수 | If-Match·상태 검사·review·resourceVersion·feed 같은 transaction |
| 멱등 | key/fingerprint/성공 결과를 업무 commit과 함께 저장 |
| 진단 | eventId 유일, 민감 내용 비수집, 진단 장애가 업무 commit을 취소하지 않음 |

DB 제품은 자유지만 위 유일성·잠금·트랜잭션 의미를 보장해야 한다. 성공을 보내고 나서 비동기로 핵심 DB 저장을 시도하는 구현은 금지한다. 서버 Outbox로 변경 feed를 발행할 때도 본문 commit과 Outbox 저장은 원자 처리한다.

### 15.2 수용 테스트 목록

아래 체크박스는 구현 후 실행할 테스트이며 실행 완료를 의미하지 않는다.

- [ ] A/S/R 권한 및 개별 객체 접근을 모든 endpoint에서 검사한다.
- [ ] 로그인 전 설문 다운로드 401, 다른 조사원 설문/응답 접근 404 또는 명시 필터 403.
- [ ] ID 대소문자 중복, name/pw 검증, CSV BOM/인용부호/1,000행 경계/atomic commit.
- [ ] CSV 완료/만료 후 원본 비밀번호를 조회·로그·임시 저장에서 복구할 수 없다.
- [ ] refresh 동시 요청/응답 유실/60초 유예/토큰 재사용/family 폐기/절대30일 만료.
- [ ] 다른 활성 기기 login 충돌, 기기 해제 후 구 토큰·구 기기 차단, 근태 ON 유지.
- [ ] 미할당 200/null/ETag, 두 관리자의 동시 할당 변경 1건만 성공.
- [ ] 온라인 신규 시작 재확인 실패 시 이전 설문 자동 시작 금지.
- [ ] A 진행 중 B로 변경 후 A 제출 수용, 다음 시작 B, 자동 반려 없음.
- [ ] 실제 응답과 익명 가져오기의 할당 검증 기준을 구분한다.
- [ ] 객관식 배열/알 수 없는 보기/7점 소수·범위/필수 공백/미응답 null 검증.
- [ ] 같은 날 반복 ON/OFF, 자정 자동 OFF 없음, idx 중복/sequence gap/상태 충돌.
- [ ] 늦은 근태·시계 역행·미래5분 경계·기기 교체 sequence·현재 운영 상태 보존.
- [ ] 출근 동기화 지연 시 응답 보존/ATTENDANCE_UNVERIFIED, 이후 증거 반영.
- [ ] 초안 없이 제출, 낡은 초안이 최신 내용 덮어쓰기 금지, 동일 초안 재시도 성공.
- [ ] RETURNED 보완 중 workflow revision과 draft revision 구분, 승인본 불변.
- [ ] 같은 제출/review key 재전송은 If-Match가 낡아도 기존 결과; 권한 철회/만료는 차단.
- [ ] 동시 검수 1건만 성공, historical revision 조회와 현재 revision 검수 구분.
- [ ] 익명 batch 부분 실패/현재할당 경쟁/다른 계정 중복/미확인 성공 복구.
- [ ] 익명 기존 성공 재전송은 할당 변경 후에도 허용, 본문 변경은 거절.
- [ ] 업무일 경계, 최초 서버 제출일 집계, 반려 재제출 중복 합산 없음.
- [ ] 진행 현황에 이전 설문 실적을 새 설문에 합산하지 않는다.
- [ ] 일반 커서 15분/동기화30일 만료·복구·권한 변경·삭제 표식 처리.
- [ ] 보관 1월31일→4월30일, now==expiresAt 경계, 상세410/정리후404, batch 항목 만료.
- [ ] 백업 복원에서 만료 payload 키가 되살아나지 않는다.
- [ ] 네트워크 응답 유실/프로세스 종료 시 업무 저장과 Outbox/멱등 결과가 일치한다.
- [ ] 미전송·진행 중 로컬 원본은 만료에도 자동 삭제하지 않고 경고한다.
- [ ] 오류 보고 실패가 무한 진단 생성/업무 전송 중단으로 이어지지 않는다.
- [ ] API 예시뿐 아니라 null/누락/추가 필드/길이/타입 경계에 대한 계약 테스트를 실행한다.

## 16. v1.3 미정의 항목 해결표

| 기존 ID | 구체화 결과 |
| --- | --- |
| GAP-01 | 불투명 토큰/Argon2id/요청 제한/refresh 회전·동시성, §7.1 |
| GAP-02 | 전체 요청·응답 DTO와 API별 성공 JSON, §5·6·14 |
| GAP-03 | 최초·증가·no-op 버전 규칙, §7.2 |
| GAP-04 | 성공 재전송 우선·If-Match·password reset 멱등, §4.4 |
| GAP-05 | UTC 포함/제외 범위·93일·과거 근태 기준, §4.3·8.4 |
| GAP-06 | firstSubmittedAt=최초 서버 제출 시각, §8.4 |
| GAP-07 | feed enum/삭제/30일 커서/복구, §10 |
| GAP-08 | PUBLISHED/SUSPENDED/CLOSED 및 운영 게시 경계, §7.3 |
| GAP-09 | 초안 검증·동일 버전·반려 작업 초안, §9 |
| GAP-10 | 헤더 제외1,000행/CSV 상태/파일 멱등, API-10~12·§7.2 |
| GAP-11 | 오류 enum/details/severity/timeWarning, §5·8·13 |
| GAP-12 | batch는 HTTP200+항목 만료, 단건410, §11.1 |
| GAP-13 | 300초 시계 오차/오래된 근태/백업 키 폐기, §8·12 |
| GAP-14 | 로컬 미전송 만료 보존·경고, 회수API는 범위 제외, §12 |
| GAP-15 | 정렬/code point/문구 길이/limit 거절, §4.3·5 |
| GAP-16 | R1기기/S·A 기기 수 제한 없음/ID 일치, §7 |
| GAP-17 | 비로그인 비식별 진단 큐·나중 인증 업로드, §11.2 |
| GAP-18 | local ON 시작+attendanceEventId+서버 검수 표시, §8.3 |
| GAP-19 | PROCESSING/COMPLETED/복구 lease/결과 보관, §11.1 |
| GAP-20 | 계정 초안·제출도 최초+추가3회, §11.3 |

현재 제품 결정에 대한 추가 질문은 없다. 서버 프레임워크, DB 제품, 배포 주소/비밀키는 구현·운영 설정이며 이 API 계약을 바꾸지 않고 선택할 수 있다.
