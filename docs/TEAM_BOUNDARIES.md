# 팀 역할 및 코드 수정 범위

## 기술 구성

- Android 앱: Java (`mobile/`)
- 백엔드 API: Python (`backend/`)
- 계정 및 기록 저장: Supabase (`mobile/supabase/schema.sql`)
- `frontend/`는 현재 Android 앱에서 사용하지 않는 기존 웹 시안입니다.

## 역할별 소유 범위

### 소비 패턴 진단 담당

담당 기능은 월별 수입·지출 통계, 카테고리별 지출, 전월 비교, 소비 피드백입니다.

- `MainActivity.java`: `showStatisticsTab`, `showAnalysisTab`, `addDonutCard`, `categoryTotals`, `totalForMonth`
- 데이터 계약이나 홈/기록/로그인 화면은 수정하지 않습니다. 추가 데이터가 필요하면 챗봇 담당자와 조회 계약을 먼저 합의합니다.

### 디자인 및 프론트엔드 담당

담당 기능은 공용 디자인, 홈 화면, 하단 메뉴, 화면 전환 및 공통 UI입니다.

- `MainActivity.java`: `showJournal`, `addNavigationItem`, `showTab`, `showHomeTab`, `navArrow`, `quickCard`, `clickableCard`, 공용 UI 생성 메서드
- Android 리소스와 테마를 수정할 수 있습니다.
- 다른 담당 화면의 기능 처리나 Supabase/API 저장 형식은 변경하지 않습니다. 새 화면 경로를 추가하거나 공용 `showTab` 동작을 바꿀 때는 해당 화면 담당자와 협의합니다.

### 음성 녹음·스크립트·로그인 담당

이 프로젝트에서 로그인(Auth) 및 음성 원문 기록의 소유자는 이 역할입니다.

- `MainActivity.java`: `showLogin`, `authenticate`, `showRecordTab`, `toggleRecording`, `startRecording`, `stopRecording`, `saveScript`, `refreshEntries`, `releaseRecorder`
- `mobile/app/src/main/java/com/moa/pbl/data/TranscriptionRepository.java`
- `mobile/app/src/main/java/com/moa/pbl/data/ScriptEntry.java`
- `SupabaseRepository.java`의 인증·스크립트 메서드: `authenticate`, `logout`, `insertEntry`, `fetchEntries`
- 전사 백엔드: `backend/app/routers/transcriptions.py`, `backend/app/services/transcription.py`
- 공유 화면 골격, 공통 내비게이션, 거래 파싱/저장 메서드는 음성 기능 작업 범위가 아닙니다.

### 챗봇 및 자연어 거래 담당

담당 기능은 자연어 문장에서 거래를 추출하고, 확인한 거래를 가계부에 저장·조회하는 것입니다.

- `MainActivity.java`: `analyzeLedger`, `showParsedTransactions`, `saveLedgerEntries`, `refreshLedgerEntries`
- `mobile/app/src/main/java/com/moa/pbl/data/LedgerParserRepository.java`
- `mobile/app/src/main/java/com/moa/pbl/data/LedgerEntry.java`
- `mobile/app/src/main/java/com/moa/pbl/data/ParsedTransaction.java`
- `SupabaseRepository.java`의 거래 메서드: `insertLedgerEntries`, `fetchLedgerEntries`
- 거래 분석 백엔드: `backend/app/routers/ledger.py`, `backend/app/services/ledger_parser.py`
- 인증 및 음성 스크립트 저장 메서드는 수정하지 않습니다.

## 공용 코드 규칙

1. Android 구현 언어는 Java로 유지합니다. 백엔드는 Python입니다.
2. `MainActivity.java`는 여러 담당의 화면 메서드가 모인 통합 파일입니다. 각자 위 표의 소유 메서드만 수정합니다. 파일이 같더라도 소유 범위는 메서드 단위로 나눕니다.
3. `SupabaseRepository.java`는 인증·스크립트·거래 저장 코드가 함께 있습니다. 각 담당자는 자기 표에 명시된 메서드만 수정하고, 공용 요청 처리·세션·RLS 동작은 변경 전에 관련 담당자와 합의합니다.
4. 공용 메서드, 데이터 모델, API 경로/요청/응답, Supabase 스키마를 바꿔야 하면 영향을 받는 담당자 모두와 먼저 계약을 정합니다. 변경한 계약과 문서를 같은 PR에서 갱신합니다.
5. 팀원별 기능 브랜치에서 작업하고 PR로 통합합니다. 다른 담당자의 커밋을 되돌리거나 소유 범위 밖 파일을 포맷 전체 변경하지 않습니다.
6. 구현이 한 파일에 집중되어 있어 향후 충돌이 반복되면 화면별 Java 클래스와 인증/저장 Repository를 독립 파일로 분리하는 작업을 별도 합의합니다.

## 데이터 및 보안 경계

- Supabase Auth가 계정 및 세션을 관리합니다.
- 스크립트는 `voice_entries`, 거래는 `ledger_entries`에 저장하고 두 테이블 모두 사용자별 RLS를 적용합니다.
- 원본 음성은 임시 파일로 전사 요청에 사용한 뒤 정리합니다.
- Gemini API 키는 백엔드의 로컬 `backend/.env`에서만 읽습니다. Android 앱, GitHub, 문서에 키를 넣지 않습니다.
- `backend/.env`, `mobile/local.properties`, 서비스 역할 키와 개인 설정은 커밋하지 않습니다.
