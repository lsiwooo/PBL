import logging
from typing import Literal

from fastapi import APIRouter, File, HTTPException, UploadFile
from pydantic import BaseModel, Field

from app.services.ledger_parser import parse_ledger_image, parse_ledger_text

logger = logging.getLogger("uvicorn.error")  # 서버 터미널에 진짜 오류를 보여주기 위함

router = APIRouter(prefix="/api/v1/ledger", tags=["ledger"])


class ParseRequest(BaseModel):
    transcript: str = Field(min_length=1, max_length=5000)


class ParsedTransaction(BaseModel):
    item_name: str
    amount: int = Field(gt=0)
    transaction_type: Literal["expense", "income"]
    category: str
    occurred_on: str | None = None


class ParseResponse(BaseModel):
    transactions: list[ParsedTransaction]


MAX_RECEIPT_BYTES = 10 * 1024 * 1024
RECEIPT_MIME_TYPES = {"image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"}


@router.post("/parse", response_model=ParseResponse)
async def parse_transactions(request: ParseRequest) -> ParseResponse:
    try:
        transactions = await parse_ledger_text(request.transcript)
    except Exception as error:
        logger.exception("ledger parse failed")
        raise HTTPException(status_code=502, detail="스크립트에서 가계부 항목을 추출하지 못했어요. 다시 시도해 주세요.") from error
    return ParseResponse(transactions=transactions)


@router.post("/parse-image", response_model=ParseResponse)
async def parse_receipt_image(file: UploadFile = File(...)) -> ParseResponse:
    mime_type = (file.content_type or "").lower()
    if mime_type not in RECEIPT_MIME_TYPES:
        raise HTTPException(status_code=415, detail="JPG, PNG, WEBP 또는 HEIC 영수증 이미지를 선택해 주세요.")
    image_data = await file.read(MAX_RECEIPT_BYTES + 1)
    if not image_data:
        raise HTTPException(status_code=400, detail="이미지가 비어 있어요. 다시 선택해 주세요.")
    if len(image_data) > MAX_RECEIPT_BYTES:
        raise HTTPException(status_code=413, detail="이미지는 10MB 이하로 선택해 주세요.")
    try:
        transactions = await parse_ledger_image(image_data, mime_type)
    except Exception as error:
        logger.exception("receipt image parse failed")
        raise HTTPException(status_code=502, detail="영수증을 읽지 못했어요. 선명한 사진으로 다시 시도해 주세요.") from error
    return ParseResponse(transactions=transactions)
