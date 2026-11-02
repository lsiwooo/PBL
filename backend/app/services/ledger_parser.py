from typing import Literal

from google import genai
from google.genai import types
from pydantic import BaseModel, ConfigDict, Field

from app.config import settings


class LedgerTransaction(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    item_name: str = Field(description="구매하거나 받은 항목 이름")
    amount: int = Field(gt=0, description="원 단위 정수 금액")
    transaction_type: Literal["expense", "income"]
    category: str = Field(description="식비, 카페, 교통, 쇼핑, 생활, 의료, 문화, 급여, 기타 중 하나")


class LedgerParseResult(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    transactions: list[LedgerTransaction]


async def parse_ledger_text(transcript: str) -> list[LedgerTransaction]:
    if not settings.gemini_api_key:
        raise RuntimeError("Gemini API 키 설정이 필요해요. backend/.env를 확인해 주세요.")

    client = genai.Client(api_key=settings.gemini_api_key)
    async_client = client.aio
    prompt = (
        "다음 한국어 가계부 발화에서 명시된 수입 또는 지출 항목만 추출하세요. "
        "항목별 상품명과 원화 금액을 보존하고, 지출/수입 유형과 간단한 한국어 카테고리를 붙이세요. "
        "금액이 불명확한 항목은 제외하고, 거래가 없으면 빈 배열을 반환하세요. "
        "스크립트: " + transcript
    )
    try:
        response = await async_client.models.generate_content(
            model=settings.gemini_ledger_model,
            contents=prompt,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=LedgerParseResult,
            ),
        )
        if not response.text:
            return []
        result = LedgerParseResult.model_validate_json(response.text)
        return result.transactions
    finally:
        await async_client.aclose()
