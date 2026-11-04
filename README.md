# 자연어 가계부

자연어 문장 또는 음성으로 수입과 지출을 기록하고, 월별 소비 내역과 요약을 확인하는 Android 앱입니다. Android 앱은 Java로 작성했으며, Python 백엔드가 음성 전사와 문장 분석을 처리합니다. Supabase는 계정과 기록을 저장합니다.

현재 실행 대상은 `mobile/`의 Android 앱입니다. 저장소의 `frontend/`는 기존 웹 시안으로, 현재 Android 앱 실행 절차와 연결되어 있지 않습니다. 별도 요청 없이 웹 앱을 현재 제품 흐름으로 간주하지 않습니다.

## 기능

- 이메일과 비밀번호를 사용하는 Supabase Auth 회원가입 및 로그인
- 마이페이지에서 계정 이메일과 소비 분석 프로필을 확인·수정하고 로그아웃
- 음성 녹음, 한국어 스크립트 변환, 원문 스크립트 저장
- 자연어 문장에서 거래 품목, 금액, 수입·지출 유형 및 카테고리 추출
- 사용자가 분석 결과를 확인한 후 가계부 거래로 저장
- 월별 잔액, 수입·지출, 카테고리별 통계와 소비 요약

## 팀 담당 영역

| 담당 | 작업 범위 | 주요 경로 |
| --- | --- | --- |
| 소비 패턴 진단 | 월별 소비 집계, 카테고리별 통계, 소비 피드백 및 비교 기준 | `MainActivity.java`의 `showStatisticsTab`, `showAnalysisTab`, `addDonutCard`, `categoryTotals`, `totalForMonth`; 추후 분석 전용 클래스 |
| 디자인 및 프론트엔드 | 공용 화면 틀, 디자인 시스템, 하단 메뉴, 마이페이지 레이아웃 | `MainActivity.java`의 `showJournal`, `addNavigationItem`, `showTab`, `showHomeTab`, 마이페이지 화면 구성, 공용 뷰 메서드 및 Android 리소스 |
| 녹음·스크립트·로그인 | 마이크 권한·녹음, 음성 전사, 날짜별 원문 저장, Supabase 로그인·가입·로그아웃 | `MainActivity.java`의 인증·전사·스크립트·로그아웃 메서드; `SupabaseRepository`의 인증·스크립트 메서드; `TranscriptionRepository`, `ScriptEntry`; 전사 백엔드 |
| 챗봇 및 자연어 가계부 | 자연어 거래 분석, 사용자 확인, 거래 저장 및 조회 | `MainActivity.java`의 `analyzeLedger`, `showParsedTransactions`, `saveLedgerEntries`, `refreshLedgerEntries`; `LedgerParserRepository`, `LedgerEntry`, `ParsedTransaction`; `backend/app/routers/ledger.py`, `backend/app/services/ledger_parser.py` |

세부 담당 파일·메서드와 수정 제한은 [`docs/TEAM_BOUNDARIES.md`](docs/TEAM_BOUNDARIES.md)에 정리했습니다. `MainActivity.java`는 화면들이 한 파일에 있는 통합 지점입니다. 각 담당자는 표에 지정된 메서드만 수정하고, 공용 내비게이션·공용 뷰 생성·화면 라우팅 또는 다른 담당자의 메서드를 바꿔야 할 때는 먼저 협의합니다. 팀원별 기능 브랜치를 사용하고 PR에서 통합합니다.

## 개발 환경

- Android Studio 및 Android SDK 35
- Android 기기 API 26 이상 또는 Android 에뮬레이터
- Java 17 호환 빌드 도구와 Gradle Wrapper
- Python 3.11 이상
- Gemini API 키: 백엔드에서 전사와 자연어 분석을 사용할 때 필요
- Supabase 프로젝트: 로그인과 데이터 저장을 사용할 때 필요

## Android 앱 실행

1. 저장소를 복제하고 Android Studio에서 저장소 안의 `mobile` 폴더를 엽니다.
2. Gradle 동기화를 완료합니다.
3. `mobile/local.properties`에서 Android SDK 경로(`sdk.dir`)를 유지하고 Supabase 설정을 추가합니다.

```properties
SUPABASE_URL=https\://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=YOUR_PUBLISHABLE_OR_ANON_KEY
```

4. USB 디버깅을 허용한 Android 기기를 연결하거나 에뮬레이터를 시작합니다.
5. Android Studio에서 `app` 실행 구성을 선택하고 **Run**을 누릅니다.

명령줄 빌드:

```powershell
cd mobile
.\gradlew.bat :app:assembleDebug
```

APK 경로: `mobile/app/build/outputs/apk/debug/app-debug.apk`

## Supabase 설정

1. Supabase 프로젝트의 URL과 Publishable key(또는 기존 anon key)를 앱의 `mobile/local.properties`에 설정합니다.
2. Supabase Dashboard에서 **SQL Editor → New query**를 열고 `mobile/supabase/schema.sql` 내용을 실행합니다.
3. Supabase Auth에서 이메일·비밀번호 가입 정책을 구성합니다.

스키마는 `voice_entries`와 `ledger_entries` 테이블, 사용자별 Row Level Security 정책을 설정합니다. 앱에는 Publishable/anon key만 사용합니다. `service_role` 키는 앱이나 저장소에 넣지 않습니다. `local.properties`와 `.env`는 커밋하지 않습니다.

## 백엔드 실행

저장소 루트에서 PowerShell을 엽니다.

```powershell
if (-not (Test-Path backend\.env)) { Copy-Item backend\.env.example backend\.env }
notepad backend\.env
py -3.11 -m venv backend\.venv
backend\.venv\Scripts\python.exe -m pip install -r backend\requirements.txt
```

`backend/.env`에 서버 전용 Gemini 키와 모델을 설정합니다.

```dotenv
GEMINI_API_KEY=YOUR_GEMINI_API_KEY
GEMINI_TRANSCRIPTION_MODEL=gemini-3.5-transcribe
GEMINI_LEDGER_MODEL=gemini-3.6-flash
```

백엔드 실행:

```powershell
cd backend
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --reload --port 8000
```

서버 확인 주소: `http://localhost:8000/health`

API 주요 경로:

- `POST /api/v1/transcriptions`: 오디오 파일을 한국어 스크립트로 변환
- `POST /api/v1/ledger/parse`: 자연어 문장에서 거래 데이터를 추출

에뮬레이터의 기본 백엔드 주소는 `http://10.0.2.2:8000`입니다. 실제 기기는 PC와 같은 네트워크에 연결하고 PC의 사설 IP를 지정해야 합니다. 예를 들어 사용자 전용 Gradle 설정 `%USERPROFILE%/.gradle/gradle.properties`에 다음을 둡니다.

```properties
transcriptionApiBaseUrl=http://192.168.1.10:8000
```

예시 IP는 실제 PC의 사설 IP 주소로 교체한 다음 Gradle 동기화와 앱 재빌드를 수행합니다. 백엔드 방화벽에서 포트 8000의 기기 접근을 허용해야 합니다.

## 데이터 및 보안

- 사용자 계정과 세션은 Supabase Auth가 관리합니다.
- 스크립트와 거래는 Supabase에 사용자 ID와 연결해 저장합니다.
- 원본 음성 파일은 전사 요청을 위해 앱에서 임시 생성하고 처리 후 정리합니다.
- Gemini API 키는 `backend/.env`에만 둡니다. Android 앱, `local.properties`, 프런트엔드 설정, 문서 및 GitHub에 넣지 않습니다.
- `.env`, `local.properties`, Gradle 사용자 설정 및 서비스 역할 키는 저장소에 커밋하지 않습니다.

## Git 협업

```bash
git switch -c feature/작업-이름
git add 변경한-파일
git commit -m "작업 내용 요약"
git push -u origin feature/작업-이름
```

기능 브랜치를 `main`에 병합하기 전에 팀원 리뷰를 받고, 변경한 모듈의 실행 또는 빌드 절차를 확인합니다.
