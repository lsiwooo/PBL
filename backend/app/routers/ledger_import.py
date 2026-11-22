import logging
from typing import Literal

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from pydantic import BaseModel, Field

from app.services.bank_statement import StatementError, parse_statement
from app.services.statement_categorizer import categorize_with_gemini
from app.services.statement_pdf import parse_pdf_statement
from app.services.statement_secure import PasswordInvalid, PasswordRequired, unlock_file

logger = logging.getLogger("uvicorn.error")

router = APIRouter(prefix="/api/v1/ledger", tags=["ledger"])

MAX_BYTES = 5 * 1024 * 1024  # 5MB


class ImportedTransaction(BaseModel):
    item_name: str
    amount: int = Field(gt=0)
    transaction_type: Literal["expense", "income"]
    category: str
    occurred_on: str   # yyyy-MM-dd
    occurred_at: str   # yyyy-MM-ddTHH:mm:ss


class ImportResponse(BaseModel):
    transactions: list[ImportedTransaction]
    latest_balance: int | None = None
    latest_balance_at: str | None = None
    skipped_holds: int = 0
    skipped_invalid: int = 0
    total_rows: int = 0
    notice: str | None = None  # 화면에 함께 보여줄 안내 (예: PDF는 AI가 읽은 결과)


@router.post("/import", response_model=ImportResponse)
async def import_statement(file: UploadFile = File(...), password: str | None = Form(None)) -> ImportResponse:
    data = await file.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise HTTPException(status_code=413, detail="파일이 너무 커요. 5MB 이하 파일을 올려 주세요.")

    notice = None
    try:
        kind, data = unlock_file(data, password)  # 비밀번호가 걸려 있으면 여기서 풉니다. (비밀번호는 저장·기록하지 않음)
        if kind == "pdf":
            result, notice = await parse_pdf_statement(data)
        else:
            result = parse_statement(file.filename or "", data)
    except PasswordRequired as error:
        raise HTTPException(status_code=400, detail={
            "code": "password_required", "message": "비밀번호가 걸린 파일이에요. 비밀번호를 입력해 주세요."}) from error
    except PasswordInvalid as error:
        raise HTTPException(status_code=400, detail={
            "code": "password_invalid", "message": "비밀번호가 맞지 않아요. 다시 입력해 주세요."}) from error
    except StatementError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    except Exception as error:
        logger.exception("statement parse failed")  # 파일 내용과 비밀번호는 로그에 남기지 않습니다.
        raise HTTPException(status_code=400, detail="파일을 읽지 못했어요. 엑셀(.xlsx), CSV, PDF 파일인지 확인해 주세요.") from error

    # 규칙으로 분류하지 못한 항목만 AI에 물어봅니다. (실패해도 '기타'로 두고 계속)
    if result.unknown_indices:
        unique: dict[tuple[str, str], list[int]] = {}
        for position in result.unknown_indices:
            tx = result.transactions[position]
            unique.setdefault((tx["item_name"], tx["transaction_type"]), []).append(position)
        keys = list(unique.keys())
        answers = await categorize_with_gemini(keys)
        for key_index, category in answers.items():
            if key_index < len(keys):
                for position in unique[keys[key_index]]:
                    result.transactions[position]["category"] = category

    return ImportResponse(
        transactions=result.transactions,
        latest_balance=result.latest_balance,
        latest_balance_at=result.latest_balance_at,
        skipped_holds=result.skipped_holds,
        skipped_invalid=result.skipped_invalid,
        total_rows=result.total_rows,
        notice=notice,
    )
