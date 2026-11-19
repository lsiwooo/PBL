# Android 앱 실행 안내

이 디렉터리는 자연어 가계부의 Android 앱 모듈입니다. 화면과 앱 로직은 Java로 작성했습니다.

## Android Studio에서 실행하기

1. GitHub에서 저장소를 복제합니다.
2. Android Studio에서 저장소의 `mobile` 폴더를 엽니다.
3. Gradle 동기화를 마칩니다.
4. Supabase 설정을 `local.properties`에 추가하고, 필요한 데이터베이스 스키마를 Supabase SQL Editor에서 실행합니다. 자세한 절차는 저장소 루트의 README를 참고하세요.
5. Android 에뮬레이터를 시작하거나 USB 디버깅이 허용된 휴대폰을 연결합니다.
6. `app` 실행 구성을 선택하고 **Run**을 누릅니다.

명령줄 빌드:

```powershell
.\gradlew.bat :app:assembleDebug
```

## 기능별 코드 위치

| 기능 | 주요 파일 |
| --- | --- |
| 앱 화면 및 하단 메뉴 | `app/src/main/java/com/moa/pbl/MainActivity.java` |
| Supabase 인증, 거래 및 프로필 저장·조회 | `app/src/main/java/com/moa/pbl/data/SupabaseRepository.java` |
| 음성 전사 API 호출 | `app/src/main/java/com/moa/pbl/data/TranscriptionRepository.java` |
| 문장·영수증 거래 분석 API 호출 | `app/src/main/java/com/moa/pbl/data/LedgerParserRepository.java` |
| 사용자별 거래·프로필 테이블과 RLS | `supabase/schema.sql` |

홈 입력창의 카메라 버튼에서 영수증을 촬영하거나 앨범 이미지를 선택할 수 있습니다. JPG, PNG, WEBP, HEIC, HEIF 형식과 10MB 이하 이미지를 지원합니다. 백엔드의 `POST /api/v1/ledger/parse-image`가 Gemini로 거래 항목을 추출하고, 앱은 결과를 기존 거래 저장 API로 로그인 계정의 가계부에 추가합니다. 이미지 내용은 분석 요청을 위해 백엔드로 전송되며 서버에 영수증 파일로 보관하지 않습니다.

하단의 **마이페이지**에서 성별, 생년월일, 직업, 학생의 학교 종류·학교 이름·학년, 시·도를 입력하고 계정을 관리합니다. 소비피드백은 로그인 계정의 거래 내역을 기준으로 계산하고, 또래 비교에는 저장된 생년월일·성별·지역을 사용합니다. 프로필은 `consumption_profiles` 테이블에 계정별로 저장하며 로그아웃해도 유지됩니다. RLS는 사용자 본인의 프로필만 허용합니다. `gender` 컬럼을 추가하려면 `supabase/schema.sql`을 Supabase SQL Editor에서 실행합니다.

## Google·네이버 로그인 설정

앱은 Supabase Auth를 통해 Google 기본 OAuth와 네이버 OIDC 로그인을 처리합니다. 콜백 주소는 `com.moa.pbl://auth/callback`입니다. OAuth 클라이언트 비밀번호는 앱 코드나 `local.properties`에 저장하지 말고 각 제공자 설정에만 입력합니다.

### Supabase 공통 설정

Supabase Dashboard의 **Authentication → URL Configuration → Redirect URLs**에 아래 주소를 추가합니다.

```text
com.moa.pbl://auth/callback
```

### Google

1. Google Cloud에서 Web application 유형 OAuth 클라이언트를 만듭니다.
2. Authorized redirect URI에 `https://<Supabase 프로젝트 ID>.supabase.co/auth/v1/callback`을 등록합니다.
3. Supabase Dashboard의 **Authentication → Sign In / Providers → Google**에서 Client ID와 Client Secret을 입력하고 사용 설정합니다.

### 네이버

1. NAVER Developers에서 애플리케이션을 등록하고 로그인 및 OpenID Connect를 설정합니다.
2. 네이버의 Callback URL에 `https://<Supabase 프로젝트 ID>.supabase.co/auth/v1/callback`을 등록합니다.
3. Supabase Dashboard의 **Authentication → Sign In / Providers → Custom OAuth/OIDC**에서 OIDC 자동 검색 공급자를 추가합니다.
4. 식별자는 `custom:naver`, Issuer URL은 `https://nid.naver.com`으로 설정하고 네이버 Client ID와 Client Secret을 입력합니다. 네이버 응답에 이메일이 없을 수 있으므로 `email_optional`을 켭니다.

Google·네이버 로그인은 해당 Supabase 프로젝트에서 각 공급자를 설정한 뒤 사용할 수 있습니다. 신규 소셜 계정은 Supabase Auth에 생성되며, 가계부와 프로필은 해당 계정으로 저장됩니다.

Android 코드는 Java로 유지합니다. API 요청·응답 또는 데이터베이스 스키마를 변경할 경우 Android 호출부와 루트 README도 함께 갱신합니다.

## 백엔드 연결

백엔드 키 설정, 서버 실행 및 실기기 네트워크 설정은 저장소 루트 README의 **백엔드 실행** 절을 따릅니다. 에뮬레이터 기본 주소는 `http://10.0.2.2:8000`이며, 실기기는 PC의 사설 IP 주소를 사용합니다. Gemini 키를 Android 앱에 넣지 않습니다.
