"""비밀번호가 걸린 거래내역 파일(엑셀, PDF)을 풉니다.

- 비밀번호는 이 요청을 처리하는 동안 메모리에서만 쓰고, 저장하거나 로그에 남기지 않습니다.
- 엑셀(.xlsx, 암호 설정): msoffcrypto-tool 필요
- PDF: pypdf + cryptography 필요
"""
from __future__ import annotations

import io

from app.services.bank_statement import StatementError

MAX_PDF_PAGES = 40
OLE_MAGIC = b"\xd0\xcf\x11\xe0\xa1\xb1\x1a\xe1"  # 암호가 걸린 신형 엑셀과 구형 .xls 모두 이 모양으로 시작합니다.


class PasswordRequired(Exception):
    """비밀번호가 필요한 파일인데 비밀번호가 없음"""


class PasswordInvalid(Exception):
    """비밀번호가 맞지 않음"""


def unlock_file(data: bytes, password: str | None) -> tuple[str, bytes]:
    """('pdf' 또는 'sheet', 열 수 있게 된 파일 내용)을 돌려줍니다."""
    if data[:5] == b"%PDF-":
        return "pdf", _unlock_pdf(data, password)
    if data[:8] == OLE_MAGIC:
        return "sheet", _unlock_office(data, password)
    return "sheet", data


# ---------------------------------------------------------------------------
def _unlock_office(data: bytes, password: str | None) -> bytes:
    try:
        import msoffcrypto
    except ImportError as error:
        raise StatementError("암호가 걸린 엑셀을 읽으려면 서버에 msoffcrypto-tool 설치가 필요해요.") from error

    try:
        office = msoffcrypto.OfficeFile(io.BytesIO(data))
        encrypted = office.is_encrypted()
    except Exception:
        return data  # 암호가 없는 구형 .xls 등은 이후 단계에서 안내합니다.
    if not encrypted:
        return data
    if not password:
        raise PasswordRequired()

    try:
        try:
            office.load_key(password=password, verify_password=True)
        except TypeError:  # verify_password 를 지원하지 않는 버전
            office.load_key(password=password)
        output = io.BytesIO()
        office.decrypt(output)
    except Exception as error:
        name = type(error).__name__
        if "Key" in name or "Password" in name or "Invalid" in name:
            raise PasswordInvalid() from error
        raise StatementError("암호를 푸는 중 오류가 났어요. 파일이 손상되지 않았는지 확인해 주세요.") from error

    decrypted = output.getvalue()
    if not decrypted.startswith(b"PK"):  # 잘못된 비밀번호로 풀면 알아볼 수 없는 데이터가 나옵니다.
        raise PasswordInvalid()
    return decrypted


# ---------------------------------------------------------------------------
def _try_decrypt(reader, password: str) -> bool:
    try:
        return int(reader.decrypt(password)) != 0
    except Exception as error:
        if type(error).__name__ == "DependencyError":
            raise StatementError("암호가 걸린 PDF를 읽으려면 서버에 cryptography 설치가 필요해요.") from error
        return False


def _unlock_pdf(data: bytes, password: str | None) -> bytes:
    try:
        from pypdf import PdfReader, PdfWriter
    except ImportError as error:
        raise StatementError("PDF를 읽으려면 서버에 pypdf 설치가 필요해요.") from error

    try:
        reader = PdfReader(io.BytesIO(data))
    except Exception as error:
        raise StatementError("PDF 파일을 열 수 없어요. 파일이 손상되지 않았는지 확인해 주세요.") from error

    if reader.is_encrypted:
        if not _try_decrypt(reader, ""):  # 열람 비밀번호 없이 편집만 막아 둔 PDF는 자동으로 열립니다.
            if not password:
                raise PasswordRequired()
            if not _try_decrypt(reader, password):
                raise PasswordInvalid()

    if len(reader.pages) > MAX_PDF_PAGES:
        raise StatementError(f"PDF가 너무 길어요. {MAX_PDF_PAGES}쪽 이하로 나눠서 올려 주세요.")
    if not reader.is_encrypted:
        return data

    writer = PdfWriter()
    for page in reader.pages:
        writer.add_page(page)
    output = io.BytesIO()
    writer.write(output)
    return output.getvalue()
