# 팀 통합 경계: 음성 입력 담당

## 모듈 소유권

- `frontend/src/components/voice-input` (화면 분리 시): 녹음, 권한, 상태, 전사된 원문 편집
- `frontend/src/lib/voice.ts`: 백엔드 전사 클라이언트
- `backend/app/routers/transcriptions.py`: 전사 HTTP 계약
- `backend/app/services/transcription.py`: 외부 전사 제공자 어댑터

현재 최초 통합 편의를 위해 화면은 `frontend/src/App.tsx`에 있습니다. 페이지 통합 시 음성 입력 섹션을 컴포넌트로 이동하고 제출 콜백만 앱 셸에서 주입할 수 있습니다.

## 요청/응답

`POST /api/v1/transcriptions`, multipart field `file`.

```json
{"text":"아침에 커피 4천 원 썼어","language":"ko"}
```

이 API는 **음성을 텍스트로만 변환**합니다. 금액 추출/카테고리 분류/저장 권한은 챗봇 또는 거래 담당자에게 있습니다.

## 앱 연결 이벤트

```ts
type VoiceEntryPayload = {
  text: string
  source: 'voice' | 'manual'
  recordedAt: string
}
```

확인 버튼을 누르면 이 payload로 챗봇 입력창을 채우거나 챗봇 제출 콜백을 실행하도록 통합합니다. 이 모듈은 거래 DB를 직접 호출하지 않습니다. 다른 팀원은 이 네 경로 외의 파일을 고치지 않아도 연결할 수 있습니다.
