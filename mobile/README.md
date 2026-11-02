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
| Supabase 인증 및 기록 저장 | `app/src/main/java/com/moa/pbl/data/SupabaseRepository.java` |
| 음성 전사 API 호출 | `app/src/main/java/com/moa/pbl/data/TranscriptionRepository.java` |
| 자연어 거래 분석 API 호출 | `app/src/main/java/com/moa/pbl/data/LedgerParserRepository.java` |
| 사용자별 테이블 및 RLS | `supabase/schema.sql` |

Android 코드는 Java로 유지합니다. API 요청·응답 또는 데이터베이스 스키마를 변경할 경우 Android 호출부와 루트 README도 함께 갱신합니다.

## 백엔드 연결

백엔드 키 설정, 서버 실행 및 실기기 네트워크 설정은 저장소 루트 README의 **백엔드 실행** 절을 따릅니다. 에뮬레이터 기본 주소는 `http://10.0.2.2:8000`이며, 실기기는 PC의 사설 IP 주소를 사용합니다. Gemini 키를 Android 앱에 넣지 않습니다.
