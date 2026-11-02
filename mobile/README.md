# 모아 Android 앱 (Java)

Android Studio용 네이티브 앱입니다. 앱 화면, 녹음, HTTP 통신 코드는 Java로 작성했습니다.

## Android Studio에서 열기

저장소 루트가 아니라 이 `mobile` 폴더를 Android Studio에서 엽니다. Gradle 동기화가 끝나면 USB 디버깅을 허용한 Android 휴대폰을 선택하고 Run을 누릅니다.

## Supabase 연결

1. Supabase 프로젝트를 만들고 Project URL과 Publishable key(또는 기존 anon key)를 확인합니다.
2. `mobile/local.properties`에 다음 두 줄을 추가합니다. 기존 `sdk.dir` 줄은 유지하세요.

```properties
SUPABASE_URL=https\://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=YOUR_PUBLISHABLE_OR_ANON_KEY
```

3. Supabase Dashboard의 SQL Editor에서 [`supabase/schema.sql`](supabase/schema.sql)을 실행합니다.
4. Authentication 설정에서 이메일 인증 정책을 정합니다. 이메일 인증이 켜져 있으면 가입 후 이메일 인증을 끝내고 로그인합니다.

앱은 Supabase Auth REST API로 이메일/비밀번호 로그인하고, 로그인 사용자 토큰으로 `voice_entries` 테이블에 스크립트를 저장하거나 조회합니다. 비밀번호는 앱이나 테이블에 저장하지 않습니다. 앱에는 publishable/anon key만 사용하고 `service_role` 키는 넣지 마세요. 테이블 RLS 정책은 사용자별 데이터 접근을 제한합니다.

`local.properties`는 Git에 올리지 않습니다. 다른 팀원은 본인의 Supabase 프로젝트 URL/키를 각자 로컬 파일에 설정합니다.

## 음성 스크립트 변환

앱이 M4A 파일을 기존 API `POST /api/v1/transcriptions`로 보내 변환합니다. 기본 에뮬레이터 주소는 `http://10.0.2.2:8000`입니다. USB로 연결한 실제 기기는 `mobile/gradle.properties` 또는 Gradle 실행 설정에서 아래 속성을 컴퓨터 LAN 주소로 지정하세요.

```properties
transcriptionApiBaseUrl=http://컴퓨터의-LAN-IP:8000
```

백엔드는 저장소의 `backend` 폴더에서 별도로 실행해야 합니다. 전사 API 키는 백엔드의 환경 변수로만 설정하세요.

## 데이터 경계

- 로그인 계정과 인증 세션: Supabase Auth
- 변환된 스크립트 및 생성 시각: Supabase `voice_entries`
- 원본 녹음 파일: 앱 캐시에 임시 저장 후 전사 요청 완료 뒤 삭제
- 자연어에서 금액/분류를 구조화하고 월 통계를 내는 기능은 팀 챗봇/소비 분석 모듈 연동 시 추가합니다.
