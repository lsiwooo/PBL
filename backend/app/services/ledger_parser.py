"""자연어 문장 -> 수입/지출 항목 목록 (Gemini 사용).

advisor_v3.py 의 프롬프트 규칙, 카테고리 분류, 재시도/모델 전환 로직을 가져왔습니다.
이 파일은 '분석만' 합니다. 저장은 Android 앱이 사용자 확인 후 Supabase에 직접 합니다.
함수 이름과 반환 형태는 기존과 같아서 routers/ledger.py 와 앱은 고치지 않아도 됩니다.
"""
import asyncio
import logging
from typing import Literal

from google import genai
from google.genai import types
from pydantic import BaseModel, ConfigDict, Field

from app.config import settings

logger = logging.getLogger("uvicorn.error")  # 서버 터미널에 진행 상황을 보여주기 위함

# 앱 화면(MainActivity)이 쓰는 카테고리 이름과 맞춥니다. 목록 밖의 값은 '기타'로 바꿉니다.
CATEGORIES = ["식비", "카페", "교통", "쇼핑", "생활", "의료", "문화", "교육", "급여", "용돈", "기타"]

MAX_ATTEMPTS = 3        # 모델 하나당 시도 횟수
PER_CALL_TIMEOUT = 15   # Gemini 한 번 호출을 기다리는 최대 시간(초). 정상이면 보통 2~6초
TOTAL_BUDGET = 50       # 전체 최대 시간(초). 앱의 응답 대기(60초)보다 짧게 잡아 앱이 먼저 포기하지 않게 함

SYSTEM_PROMPT = f"""당신은 한국어 가계부 문장 분석기입니다.
사용자 문장에서 '명시된' 수입 또는 지출 항목만 추출하세요.

규칙:
- transaction_type 은 expense(돈을 씀) 또는 income(돈을 받음) 중 하나입니다.
- category 는 다음 중 하나만 씁니다: {", ".join(CATEGORIES)}
- amount 는 원 단위 양의 정수입니다. '5천원'은 5000, '만 오천원'은 15000, '4,500원'은 4500 입니다.
- 한 문장에 여러 건이 있으면 건마다 항목을 만듭니다. item_name 은 품목이나 장소를 짧게 씁니다.
- '아아'는 아이스 아메리카노입니다.
- 금액이 불명확한 항목은 제외합니다. 거래가 없으면 빈 배열을 반환하세요.
- 숫자를 지어내지 마세요."""


class LedgerTransaction(BaseModel):
    # 주의: extra="forbid"를 쓰면 스키마에 additionalProperties가 들어가 Gemini API가 400으로 거부합니다.
    model_config = ConfigDict(frozen=True)

    item_name: str = Field(description="구매하거나 받은 항목 이름")
    # 주의: Field(gt=0)을 쓰면 Gemini 스키마 변환에서 오류가 납니다. 0 이하 금액은 아래에서 직접 걸러냅니다.
    amount: int = Field(description="원 단위 양의 정수 금액")
    transaction_type: Literal["expense", "income"]
    category: str = Field(description="식비, 카페, 교통, 쇼핑, 생활, 의료, 문화, 교육, 급여, 용돈, 기타 중 하나")


class LedgerParseResult(BaseModel):
    # 주의: extra="forbid"를 쓰면 스키마에 additionalProperties가 들어가 Gemini API가 400으로 거부합니다.
    model_config = ConfigDict(frozen=True)

    transactions: list[LedgerTransaction]


def _is_transient(error: Exception) -> bool:
    """서버가 바쁘거나 한도에 걸린 일시적 오류인지 (503/429 등)"""
    if isinstance(error, asyncio.TimeoutError):
        return True
    if getattr(error, "code", None) in (429, 500, 502, 503, 504):
        return True
    text = str(error)
    return any(key in text for key in ("503", "UNAVAILABLE", "429", "RESOURCE_EXHAUSTED"))


def _model_candidates() -> list[str]:
    models = [settings.gemini_ledger_model]
    fallback = settings.gemini_ledger_fallback_model
    if fallback and fallback not in models:
        models.append(fallback)
    return models


async def _generate_with_fallback(async_client, transcript: str):
    """모델이 바쁘면(503/429) 잠깐 기다렸다 재시도하고, 응답이 멈추면(시간 초과) 바로 다음 모델로 넘어갑니다.

    전체 대기 시간은 TOTAL_BUDGET 을 넘기지 않아서, 앱이 timeout 으로 끊기기 전에
    서버가 먼저 명확한 오류를 돌려줍니다.
    """
    loop = asyncio.get_running_loop()
    started = loop.time()
    deadline = started + TOTAL_BUDGET
    last_error: Exception | None = None

    for model in _model_candidates():
        for attempt in range(MAX_ATTEMPTS):
            remaining = deadline - loop.time()
            if remaining <= 1:
                raise last_error or asyncio.TimeoutError("가계부 분석 시간이 너무 오래 걸려요.")
            try:
                response = await asyncio.wait_for(
                    async_client.models.generate_content(
                        model=model,
                        contents=transcript,
                        config=types.GenerateContentConfig(
                            system_instruction=SYSTEM_PROMPT,
                            response_mime_type="application/json",
                            response_schema=LedgerParseResult,
                            temperature=0,
                        ),
                    ),
                    timeout=min(PER_CALL_TIMEOUT, remaining),
                )
                logger.info("ledger parse ok: model=%s attempt=%d %.1fs",
                            model, attempt + 1, loop.time() - started)
                return response
            except Exception as error:
                last_error = error
                logger.warning("ledger parse failed: model=%s attempt=%d/%d %.1fs -> %s: %s",
                               model, attempt + 1, MAX_ATTEMPTS, loop.time() - started,
                               type(error).__name__, str(error)[:300])
                if isinstance(error, asyncio.TimeoutError):
                    break  # 응답이 멈춘 모델은 같은 모델로 다시 기다리지 않고 다음 모델로 전환
                if not _is_transient(error):
                    raise
                if attempt < MAX_ATTEMPTS - 1:
                    await asyncio.sleep(max(0, min(2 ** (attempt + 1), deadline - loop.time() - 1)))
    assert last_error is not None
    raise last_error


async def parse_ledger_text(transcript: str) -> list[dict]:
    if not settings.gemini_api_key:
        raise RuntimeError("Gemini API 키 설정이 필요해요. backend/.env를 확인해 주세요.")

    client = genai.Client(api_key=settings.gemini_api_key)
    async_client = client.aio
    try:
        response = await _generate_with_fallback(async_client, transcript)
        if not response.text:
            return []
        result = LedgerParseResult.model_validate_json(response.text)
        return [
            {
                "item_name": item.item_name.strip()[:120],
                "amount": item.amount,
                "transaction_type": item.transaction_type,
                "category": item.category if item.category in CATEGORIES else "기타",
            }
            for item in result.transactions
            if item.item_name.strip() and item.amount > 0
        ]
    finally:
        await async_client.aclose()
