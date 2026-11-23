"""PDF 거래내역서 -> 거래 목록.

Gemini가 PDF를 직접 읽어서 표의 거래를 JSON으로 돌려줍니다. (글자가 들어 있는 PDF와 스캔·이미지 PDF 모두 가능)
주의: PDF 내용이 Google Gemini API로 전송됩니다.
"""
from __future__ import annotations

import asyncio
import json
import logging
import re

from google import genai
from google.genai import types

from app.config import settings
from app.services.bank_statement import (
    ParsedRow,
    StatementError,
    StatementResult,
    build_result,
    finish_row,
    parse_datetime,
    to_int,
)

logger = logging.getLogger("uvicorn.error")

PER_CALL_TIMEOUT = 50   # 모델 하나당 기다리는 시간(초)
TOTAL_BUDGET = 110      # 전체 최대 시간(초). 앱의 응답 대기(150초)보다 짧게

SYSTEM_PROMPT = """당신은 은행 거래내역서(PDF) 판독기입니다. 문서의 거래 표에 있는 거래를 빠짐없이, 문서에 나온 순서대로 추출하세요.
JSON 객체 하나로만 답하세요. 형식: {"rows": [{"datetime": "YYYY-MM-DD HH:MM:SS", "description": "적요 또는 거래내용", "amount": -5400, "balance": 33633}]}
규칙:
- datetime: 시각이 없으면 "YYYY-MM-DD". 연도가 표에 없으면 문서의 조회 기간을 보고 판단하세요.
- amount: 입금(들어온 돈)은 양수, 출금(나간 돈)은 음수인 원 단위 정수입니다. 입금액/출금액 열이 따로 있어도 하나의 숫자로 합치세요.
- balance: 거래 후 잔액 정수. 표에 없으면 null.
- 머리글, 합계, 요약, 계좌 정보, 안내 문구는 제외하세요.
- 읽기 어려운 글자나 숫자는 추측하지 말고 그 줄을 건너뛰세요."""


def _models() -> list[str]:
    names: list[str] = []
    for attr in ("gemini_ledger_model", "gemini_ledger_fallback_model",
                 "gemini_ledger_fallback_model_2", "gemini_ledger_fallback_model_3"):
        value = getattr(settings, attr, "")
        if value and value not in names:
            names.append(value)
    return names


def _can_try_next_model(error: Exception) -> bool:
    if isinstance(error, asyncio.TimeoutError):
        return True
    if getattr(error, "code", None) in (404, 429, 500, 502, 503, 504):
        return True
    text = str(error)
    return any(key in text for key in ("UNAVAILABLE", "NOT_FOUND", "RESOURCE_EXHAUSTED", "503", "429"))


async def _ask_gemini(pdf_bytes: bytes) -> str:
    client = genai.Client(api_key=settings.gemini_api_key)
    async_client = client.aio
    loop = asyncio.get_running_loop()
    deadline = loop.time() + TOTAL_BUDGET
    last_error: Exception | None = None
    try:
        for model in _models():
            remaining = deadline - loop.time()
            if remaining <= 5:
                break
            try:
                response = await asyncio.wait_for(
                    async_client.models.generate_content(
                        model=model,
                        contents=[
                            types.Part.from_bytes(data=pdf_bytes, mime_type="application/pdf"),
                            "이 거래내역서의 거래를 모두 추출해 주세요.",
                        ],
                        config=types.GenerateContentConfig(
                            system_instruction=SYSTEM_PROMPT,
                            response_mime_type="application/json",
                            temperature=0,
                        ),
                    ),
                    timeout=min(PER_CALL_TIMEOUT, remaining),
                )
                return response.text or ""
            except Exception as error:
                last_error = error
                logger.warning("statement pdf failed: model=%s -> %s: %s",
                               model, type(error).__name__, str(error)[:200])
                if not _can_try_next_model(error):
                    raise
        if last_error is not None:
            raise last_error
        raise StatementError("PDF를 읽을 AI 모델이 설정되어 있지 않아요.")
    finally:
        await async_client.aclose()


def parse_rows_text(text: str) -> tuple[list[dict], bool]:
    """AI 응답에서 거래 행을 꺼냅니다. 응답이 잘렸거나 형식이 달라도 읽을 수 있는 행은 건집니다.
    두 번째 값은 '일부만 건졌는지' 입니다."""
    text = (text or "").strip()
    try:
        data = json.loads(text)
        rows = data.get("rows") if isinstance(data, dict) else data
        if isinstance(rows, list):
            return [row for row in rows if isinstance(row, dict)], False
    except json.JSONDecodeError:
        pass
    rows = []
    for match in re.finditer(r"\{[^{}]*\}", text):
        try:
            obj = json.loads(match.group(0))
        except json.JSONDecodeError:
            continue
        if isinstance(obj, dict) and "amount" in obj:
            rows.append(obj)
    return rows, True


def rows_to_parsed(rows: list[dict]) -> tuple[list[ParsedRow], int]:
    parsed: list[ParsedRow] = []
    invalid = 0
    for row in rows:
        when = parse_datetime(str(row.get("datetime") or ""))
        raw_amount = row.get("amount")
        amount = to_int(str(raw_amount)) if raw_amount is not None else None
        if when is None or amount is None:
            invalid += 1
            continue
        raw_balance = row.get("balance")
        balance = to_int(str(raw_balance)) if raw_balance is not None else None
        status, item = finish_row(when, str(row.get("description") or ""), amount, balance)
        if status == "ok" and item is not None:
            parsed.append(item)
        else:
            invalid += 1
    return parsed, invalid


async def parse_pdf_statement(pdf_bytes: bytes) -> tuple[StatementResult, str]:
    if not settings.gemini_api_key:
        raise StatementError("Gemini API 키 설정이 필요해요. backend/.env를 확인해 주세요.")
    try:
        text = await _ask_gemini(pdf_bytes)
    except StatementError:
        raise
    except Exception as error:
        logger.exception("statement pdf failed")  # PDF 내용은 로그에 남기지 않습니다.
        raise StatementError("PDF를 읽는 AI가 지금 응답하지 않아요. 잠시 뒤에 다시 시도해 주세요.") from error

    rows, partial = parse_rows_text(text)
    parsed, invalid = rows_to_parsed(rows)
    if not parsed:
        raise StatementError("PDF에서 거래 내역을 찾지 못했어요. 거래 표가 있는 거래내역서인지 확인해 주세요.")
    notice = "PDF는 AI가 읽은 결과예요. 날짜·금액·잔액이 맞는지 꼭 확인한 뒤 저장해 주세요."
    if partial:
        notice += " 일부 줄만 읽었을 수 있어요. 기간을 나눠서 다시 올려 보세요."
    return build_result(parsed, invalid), notice
