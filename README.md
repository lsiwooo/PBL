# 모아 음성 가계부 입력

자연어 가계부 프로젝트 중 **음성 녹음과 스크립트 변환 영역**을 독립적으로 구현한 MVP입니다.

## 실행

필요 환경: Node.js 20 이상, Python 3.11 이상.

```bash
npm install
cp backend/.env.example backend/.env
# backend/.env에 OPENAI_API_KEY 설정
python -m venv backend/.venv
backend/.venv/Scripts/activate
pip install -r backend/requirements.txt
uvicorn app.main:app --app-dir backend --reload --port 8000
```

다른 터미널에서:

```bash
npm run dev
```

브라우저에서 Vite가 출력한 주소(기본 `http://localhost:5173`)를 엽니다. 실제 마이크는 localhost 또는 HTTPS 환경에서 사용할 수 있습니다. 키보드 예시 칩은 마이크 권한 없이도 화면을 확인할 수 있습니다.

선택적으로 프론트엔드 `.env.local`에 `VITE_API_BASE_URL=http://localhost:8000`을 지정할 수 있습니다. 같은 출처에서 프록시를 설정한다면 생략합니다. OpenAI 키는 프론트엔드 환경 변수에 넣지 않습니다.

## 내 담당 범위

- 브라우저 마이크 녹음, 2분 제한, 시작/종료 상태와 권한 오류 안내
- 브라우저가 지원하는 경우 한국어 실시간 자막 표시
- 녹음 파일을 전사 API에 보내 최종 스크립트로 반영
- 전사 결과 확인/직접 수정 후 외부 핸들러로 넘기는 사용자 경계
- `POST /api/v1/transcriptions` 음성 파일 전사 API

저장된 거래 데이터베이스, LLM 소비 분석, 월별 통계 및 패턴 진단은 이 영역에서 구현하지 않습니다. 현재 내역은 화면 동작을 보여주는 메모리 기반 목업입니다.

## 다른 팀원과 연결

컴포넌트는 녹음 UI와 영수증(?) 없는 자연어 스크립트 상태를 자체 관리합니다. 통합 시 `handleSubmit`에서 사용하는 `parseSpokenEntry` 목업 대신 챗봇/거래 저장 담당의 입력 콜백을 연결하면 됩니다. 안전한 연동 경계는 아래와 같습니다.

```ts
type VoiceEntryPayload = {
  text: string
  source: 'voice' | 'manual'
  recordedAt: string
}

onSubmitVoiceEntry(payload: VoiceEntryPayload): Promise<void>
```

챗봇 담당자는 이 텍스트를 기존 자연어 입력과 같은 인텐트 분류/구조화 흐름에 보내면 됩니다. 마이크/음성 인식 API 호출은 사용자 브라우저에서 호출하지 말고 백엔드를 거쳐 API 키를 보호하세요.

### 전사 API 계약

`POST /api/v1/transcriptions` — `multipart/form-data`, 필드명 `file`; 허용: WebM, MP4/M4A, Ogg, MPEG, WAV; 최대 25 MiB.

```json
{"text":"점심에 김치찌개 9천 원 썼어","language":"ko"}
```

오류는 HTTP 상태와 `{ "detail": "사용자에게 보여줄 안내" }`로 반환합니다. `/health`에서 서버 준비 상태를 확인할 수 있습니다. 업로드 파일은 처리 후 저장하지 않습니다. 운영 환경에서는 CORS 허용 출처를 서비스 주소로 제한하세요.
