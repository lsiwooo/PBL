from typing import Literal

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.services.ledger_parser import parse_ledger_text

router = APIRouter(prefix="/api/v1/ledger", tags=["ledger"])


class ParseRequest(BaseModel):
    transcript: str = Field(min_length=1, max_length=5000)


class ParsedTransaction(BaseModel):
    item_name: str
    amount: int = Field(gt=0)
    transaction_type: Literal["expense", "income"]
    category: str


class ParseResponse(BaseModel):
    transactions: list[ParsedTransaction]


@router.post("/parse", response_model=ParseResponse)
async def parse_transactions(request: ParseRequest) -> ParseResponse:
    try:
        transactions = await parse_ledger_text(request.transcript)
    except Exception as error:
        raise HTTPException(status_code=502, detail="스크립트에서 가계부 항목을 추출하지 못했어요. 다시 시도해 주세요.") from error
    return ParseResponse(transactions=transactions)
