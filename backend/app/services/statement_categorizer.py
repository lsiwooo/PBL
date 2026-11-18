"""규칙으로 분류하지 못한 거래 항목을 Gemini로 한 번에 분류합니다. (실패해도 '기타'로 두고 계속 진행)"""
import asyncio
import json
import logging

from google import genai
from google.genai import types

from app.config import settings
from app.services.bank_statement import CATEGORIES

logger = logging.getLogger("uvicorn.error")

MAX_ITEMS = 80          # 한 번에 보낼 항목 수 (서로 다른 항목명 기준)
PER_CALL_TIMEOUT = 15   # 모델 하나당 기다리는 시간(초)

SYSTEM_PROMPT = f"""다음은 은행 거래내역의 항목명입니다. 각 항목을 아래 카테고리 중 하나로 분류하세요.
카테고리: {", ".join(CATEGORIES)}
- type 이 income(수입)이면 급여, 용돈, 기타 중에서 고르세요.
- 확실하지 않으면 기타로 분류하세요.
JSON 객체 하나로만 답하세요. 키는 id 문자열, 값은 카테고리입니다. 예: {{"0": "카페", "1": "기타"}}"""


def _models() -> list[str]:
    models = [settings.gemini_ledger_model]
    fallback = settings.gemini_ledger_fallback_model
    if fallback and fallback not in models:
        models.append(fallback)
    return models


async def categorize_with_gemini(items: list[tuple[str, str]]) -> dict[int, str]:
    """items: [(항목명, 'expense'|'income'), ...] -> {위치: 카테고리}. 실패하면 빈 딕셔너리."""
    items = items[:MAX_ITEMS]
    if not items or not settings.gemini_api_key:
        return {}
    payload = json.dumps(
        [{"id": i, "text": text[:60], "type": kind} for i, (text, kind) in enumerate(items)],
        ensure_ascii=False,
    )
    client = genai.Client(api_key=settings.gemini_api_key)
    async_client = client.aio
    try:
        for model in _models():
            try:
                response = await asyncio.wait_for(
                    async_client.models.generate_content(
                        model=model,
                        contents=payload,
                        config=types.GenerateContentConfig(
                            system_instruction=SYSTEM_PROMPT,
                            response_mime_type="application/json",
                            temperature=0,
                        ),
                    ),
                    timeout=PER_CALL_TIMEOUT,
                )
                data = json.loads(response.text or "{}")
                return {int(k): v for k, v in data.items() if str(k).isdigit() and v in CATEGORIES}
            except Exception as error:  # 분류 실패는 치명적이지 않으므로 다음 모델 시도
                logger.warning("statement categorize failed: model=%s -> %s: %s",
                               model, type(error).__name__, str(error)[:200])
        return {}
    finally:
        await async_client.aclose()
