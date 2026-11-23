"""은행 거래내역 파일(CSV/TSV/XLSX/HTML 표)을 가계부 거래 목록으로 바꿉니다.

- 표준 라이브러리만 사용합니다. (새로 설치할 패키지 없음)
- 열 이름 줄이 있으면 이름으로 열을 찾고, 없으면 내용을 보고 추정합니다.
- 입금/출금은 '금액의 부호(+/-)'나 입금·출금 열로 판단합니다. 구분 열의 글자('체크카드결제' 등)는 신뢰하지 않습니다.
- '가승인'(카드 임시 승인/해제) 행은 수입·지출로 세지 않습니다. (잔고 계산에는 참고합니다.)
"""
from __future__ import annotations

import csv
import html
import io
import re
import zipfile
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from xml.etree import ElementTree as ET

MAX_ROWS = 3000

CATEGORIES = ["식비", "카페", "교통", "쇼핑", "생활", "의료", "문화", "교육", "급여", "용돈", "기타"]


class StatementError(Exception):
    """사용자에게 그대로 보여줘도 되는 오류"""


@dataclass
class ParsedRow:
    occurred_at: datetime
    description: str
    amount: int          # +입금 / -출금
    balance: int | None  # 거래 직후 잔액
    is_hold: bool        # 가승인(임시 승인/해제) 여부


@dataclass
class StatementResult:
    transactions: list[dict] = field(default_factory=list)
    unknown_indices: list[int] = field(default_factory=list)  # 규칙으로 분류하지 못한 거래 위치
    latest_balance: int | None = None
    latest_balance_at: str | None = None
    skipped_holds: int = 0
    skipped_invalid: int = 0
    total_rows: int = 0


# ---------------------------------------------------------------------------
# 1) 파일 -> 표(rows)
# ---------------------------------------------------------------------------
def read_rows(filename: str, data: bytes) -> list[list[str]]:
    name = (filename or "").lower()
    if data[:2] == b"PK" or name.endswith(".xlsx"):
        return _read_xlsx(data)
    if data[:4] == b"\xd0\xcf\x11\xe0":
        raise StatementError(
            "구형 엑셀(.xls)은 읽을 수 없어요. 엑셀에서 '다른 이름으로 저장'으로 .xlsx 또는 CSV로 저장한 뒤 다시 올려 주세요.")
    text = _decode(data)
    if re.search(r"<table", text, re.I):
        return _read_html(text)
    return _read_delimited(text)


def _decode(data: bytes) -> str:
    for encoding in ("utf-8-sig", "cp949"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    return data.decode("euc-kr", errors="replace")


def _read_delimited(text: str) -> list[list[str]]:
    sample = "\n".join(text.splitlines()[:10])
    delimiter = max(["\t", ",", ";"], key=lambda d: sample.count(d))
    if sample.count(delimiter) == 0:
        raise StatementError("표 형태의 파일이 아니에요. 은행 앱에서 내려받은 거래내역 파일(CSV, 엑셀)을 올려 주세요.")
    reader = csv.reader(io.StringIO(text), delimiter=delimiter)
    rows = [[cell.strip() for cell in row] for row in reader]
    return [row for row in rows if any(row)]


def _read_html(text: str) -> list[list[str]]:
    rows: list[list[str]] = []
    for tr in re.findall(r"<tr[^>]*>(.*?)</tr>", text, re.I | re.S):
        cells = re.findall(r"<t[dh][^>]*>(.*?)</t[dh]>", tr, re.I | re.S)
        row = [html.unescape(re.sub(r"<[^>]+>", "", cell)).replace("\xa0", " ").strip() for cell in cells]
        if any(row):
            rows.append(row)
    return rows


def _col_index(ref: str) -> int:
    letters = re.match(r"[A-Za-z]+", ref).group(0).upper()
    number = 0
    for ch in letters:
        number = number * 26 + (ord(ch) - 64)
    return number - 1


def _local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _rich_text(node) -> str:
    """<si> 또는 <is> 안의 글자를 이어 붙입니다. (루비/발음 표기 rPh 는 제외)"""
    parts: list[str] = []
    for child in node:
        name = _local(child.tag)
        if name == "t":
            parts.append(child.text or "")
        elif name == "r":
            parts.extend((t.text or "") for t in child if _local(t.tag) == "t")
    return "".join(parts)


def _number_text(text: str) -> str:
    try:
        value = float(text)
    except ValueError:
        return text
    return str(int(value)) if value == int(value) and abs(value) < 1e15 else text


def _read_xlsx(data: bytes) -> list[list[str]]:
    try:
        archive = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile as error:
        raise StatementError("엑셀 파일을 열 수 없어요. 파일이 손상되지 않았는지 확인해 주세요.") from error
    with archive:
        names = archive.namelist()
        shared: list[str] = []
        if "xl/sharedStrings.xml" in names:
            root = ET.fromstring(archive.read("xl/sharedStrings.xml"))
            for si in root.iterfind("{*}si"):
                shared.append(_rich_text(si))
        sheets = sorted(
            (n for n in names if re.fullmatch(r"xl/worksheets/sheet\d+\.xml", n)),
            key=lambda n: int(re.search(r"(\d+)\.xml$", n).group(1)),
        )
        if not sheets:
            raise StatementError("엑셀 파일에서 표를 찾지 못했어요.")
        root = ET.fromstring(archive.read(sheets[0]))

    rows: list[list[str]] = []
    for row in root.iterfind(".//{*}row"):
        cells: dict[int, str] = {}
        for position, cell in enumerate(row.iterfind("{*}c")):
            ref = cell.get("r")
            index = _col_index(ref) if ref else position
            kind = cell.get("t")
            value_node = cell.find("{*}v")
            if kind == "s":
                raw = value_node.text if value_node is not None and value_node.text else ""
                value = shared[int(raw)] if raw.isdigit() and int(raw) < len(shared) else ""
            elif kind == "inlineStr":
                inline = cell.find("{*}is")
                value = _rich_text(inline) if inline is not None else ""
            elif kind in ("str", "b", "e"):
                value = value_node.text if value_node is not None and value_node.text else ""
            else:
                value = _number_text(value_node.text) if value_node is not None and value_node.text else ""
            cells[index] = value.strip()
        if any(cells.values()):
            width = max(cells) + 1
            rows.append([cells.get(i, "") for i in range(width)])
    return rows


# ---------------------------------------------------------------------------
# 2) 값 해석
# ---------------------------------------------------------------------------
_DT_RE = re.compile(
    r"(\d{4})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})일?"
    r"(?:[T\s]+(\d{1,2})\s*[:시]\s*(\d{2})(?:\s*[:분]\s*(\d{2}))?)?"
)
_COMPACT_RE = re.compile(r"(\d{4})(\d{2})(\d{2})(?:(\d{2})(\d{2})(\d{2})?)?")
_NUMBER_RE = re.compile(r"[+\-−]?\d[\d,]*(?:\.\d+)?")


def parse_datetime(text: str, strict: bool = False) -> datetime | None:
    s = (text or "").strip()
    if not s:
        return None
    match = _DT_RE.search(s)
    if match:
        y, mo, d, h, mi, sec = (int(v) if v else 0 for v in match.groups())
        try:
            return datetime(y, mo, d, h, mi, sec)
        except ValueError:
            return None
    if strict:
        return None
    match = _COMPACT_RE.fullmatch(s)
    if match:
        y, mo, d, h, mi, sec = (int(v) if v else 0 for v in match.groups())
        try:
            return datetime(y, mo, d, h, mi, sec)
        except ValueError:
            return None
    if re.fullmatch(r"\d{5}(?:\.\d+)?", s) and 20000 <= float(s) <= 80000:  # 엑셀 날짜 숫자
        return datetime(1899, 12, 30) + timedelta(days=float(s))
    return None


def to_int(text: str) -> int | None:
    s = (text or "").replace("−", "-").replace("－", "-")
    s = re.sub(r"[,\s원₩+]", "", s)
    if not s or s in ("-", "."):
        return None
    try:
        return int(float(s))
    except ValueError:
        return None


def _is_number_cell(text: str) -> bool:
    return bool(_NUMBER_RE.fullmatch((text or "").strip()))


# ---------------------------------------------------------------------------
# 3) 열 찾기
# ---------------------------------------------------------------------------
FIELD_ORDER = ["balance", "deposit", "withdrawal", "datetime", "description", "amount"]
SYNONYMS = {
    "balance": ["거래후잔액", "거래후잔고", "잔액", "잔고"],
    "deposit": ["입금액", "입금금액", "맡기신금액", "입금"],
    "withdrawal": ["출금액", "출금금액", "찾으신금액", "출금"],
    "datetime": ["거래일시", "거래일자", "거래일", "거래시간", "일시", "날짜", "일자"],
    "description": ["적요", "거래내용", "내용", "거래처", "기재내용", "보낸분받는분", "받는분", "보낸분", "상대방", "가맹점"],
    "amount": ["거래금액", "금액"],
}


def _norm(cell: str) -> str:
    text = re.sub(r"\([^)]*\)", "", cell or "")
    return re.sub(r"[\s_\-/·.]", "", text)


def _map_header(row: list[str]) -> dict:
    normalized = [_norm(cell) for cell in row]
    used: set[int] = set()
    mapping: dict = {}
    kind_cols = [i for i, c in enumerate(normalized) if c and ("구분" in c or "유형" in c)]
    used.update(kind_cols)
    for name in FIELD_ORDER:
        found = None
        for synonym in SYNONYMS[name]:                      # 1순위: 이름이 정확히 같은 열
            for i, c in enumerate(normalized):
                if i not in used and c == synonym:
                    found = i
                    break
            if found is not None:
                break
        if found is None:
            for synonym in SYNONYMS[name]:                  # 2순위: 이름에 포함된 열
                for i, c in enumerate(normalized):
                    if i not in used and c and len(synonym) >= 2 and synonym in c:
                        found = i
                        break
                if found is not None:
                    break
        if found is not None:
            mapping[name] = found
            used.add(found)
    if kind_cols:
        mapping["kind"] = kind_cols
    return mapping


def _find_header(rows: list[list[str]]) -> tuple[int | None, dict]:
    for index, row in enumerate(rows[:15]):
        mapping = _map_header(row)
        has_money = any(k in mapping for k in ("balance", "amount", "deposit", "withdrawal"))
        if "datetime" in mapping and has_money:
            return index, mapping
    return None, {}


# ---------------------------------------------------------------------------
# 4) 한 줄 -> 거래
# ---------------------------------------------------------------------------
def _cell(row: list[str], index: int | None) -> str:
    if index is None or index >= len(row):
        return ""
    return row[index].strip()


def _row_from_header(row: list[str], mapping: dict) -> tuple[str, ParsedRow | None]:
    occurred_at = parse_datetime(_cell(row, mapping.get("datetime")))
    if occurred_at is None:
        return "ignore", None  # 합계·안내 문구 등
    description = _cell(row, mapping.get("description"))
    kind = " ".join(_cell(row, i) for i in mapping.get("kind", []))
    balance = to_int(_cell(row, mapping.get("balance")))

    if "deposit" in mapping or "withdrawal" in mapping:
        deposit = to_int(_cell(row, mapping.get("deposit"))) or 0
        withdrawal = to_int(_cell(row, mapping.get("withdrawal"))) or 0
        amount = abs(deposit) - abs(withdrawal)
    else:
        amount = to_int(_cell(row, mapping.get("amount")))
        if amount is None:
            return "invalid", None
        if amount > 0 and "출금" in kind and "입금" not in kind:
            amount = -amount  # 금액에 부호가 없는 파일
    return _finish(occurred_at, description or kind, amount, balance)


def _row_heuristic(row: list[str]) -> tuple[str, ParsedRow | None]:
    occurred_at = None
    dt_index = None
    for i, cell in enumerate(row):
        occurred_at = parse_datetime(cell, strict=True)
        if occurred_at:
            dt_index = i
            break
    if occurred_at is None:
        return "ignore", None
    numbers = [(i, c) for i, c in enumerate(row) if i != dt_index and _is_number_cell(c)]
    texts = [c for i, c in enumerate(row) if i != dt_index and c and not _is_number_cell(c)]
    if not numbers:
        return "invalid", None
    if len(numbers) >= 2:
        amount, balance = to_int(numbers[-2][1]), to_int(numbers[-1][1])
    else:
        amount, balance = to_int(numbers[-1][1]), None
    if amount is None:
        return "invalid", None
    description = texts[0] if texts else ""
    kind = texts[1] if len(texts) > 1 else ""
    if amount > 0 and "출금" in kind and "입금" not in kind:
        amount = -amount
    return _finish(occurred_at, description or kind, amount, balance)


def _finish(occurred_at, description, amount, balance) -> tuple[str, ParsedRow | None]:
    description = (description or "").strip()
    is_hold = "가승인" in description
    if amount == 0 and not is_hold:
        return "invalid", None
    return "ok", ParsedRow(occurred_at, description, amount, balance, is_hold)


# ---------------------------------------------------------------------------
# 5) 카테고리 규칙 (앱의 CategoryRules.java 와 같은 내용을 유지하세요)
# ---------------------------------------------------------------------------
EXPENSE_RULES: list[tuple[str, tuple[str, ...]]] = [
    ("교통", ("택시", "카카오t", "버스", "지하철", "티머니", "교통", "코레일", "ktx", "srt", "하이패스", "주유", "주차", "타다", "고속도로", "철도")),
    ("카페", ("스타벅스", "카페", "커피", "이디야", "투썸", "메가커피", "컴포즈", "빽다방", "할리스", "폴바셋", "파스쿠찌", "베이커리", "디저트")),
    ("식비", ("편의점", "gs25", "cu", "세븐일레븐", "이마트24", "마트", "식당", "치킨", "피자", "배달의민족", "배민", "요기요", "쿠팡이츠",
             "맥도날드", "롯데리아", "버거킹", "김밥", "분식", "국밥", "고기", "음식", "홈플러스", "이마트", "하나로", "푸드", "밥", "반점")),
    ("쇼핑", ("쿠팡", "네이버페이", "무신사", "올리브영", "다이소", "11번가", "지마켓", "옥션", "알리", "테무", "의류", "신발", "백화점", "스마트스토어")),
    ("의료", ("병원", "의원", "약국", "치과", "한의원", "클리닉", "의료")),
    ("문화", ("영화", "cgv", "메가박스", "롯데시네마", "넷플릭스", "유튜브", "디즈니", "왓챠", "티빙", "웨이브", "멜론", "스포티파이", "게임",
             "노래방", "pc방", "피시방", "서점", "교보문고", "영풍문고", "스팀")),
    ("생활", ("통신", "kt", "skt", "lgu", "요금", "관리비", "월세", "전기", "가스", "수도", "보험")),
    ("교육", ("학원", "교육", "학비", "등록금", "인강", "수강")),
]
INCOME_RULES: list[tuple[str, tuple[str, ...]]] = [
    ("급여", ("급여", "월급", "알바", "급료", "상여", "보너스")),
    ("용돈", ("용돈", "엄마", "아빠", "부모")),
]


def _has(text: str, keyword: str) -> bool:
    if keyword.isascii() and len(keyword) <= 4:  # cu, kt 같은 짧은 영문은 단어 경계를 확인
        return re.search(r"(?<![a-z0-9])" + re.escape(keyword) + r"(?![a-z0-9])", text) is not None
    return keyword in text


def categorize(description: str, is_income: bool) -> str | None:
    """규칙으로 분류합니다. 알 수 없으면 None (지출만), 수입은 '기타'."""
    text = (description or "").lower()
    rules = INCOME_RULES if is_income else EXPENSE_RULES
    for category, keywords in rules:
        if any(_has(text, k) for k in keywords):
            return category
    return "기타" if is_income else None


# ---------------------------------------------------------------------------
# 6) 전체 처리
# ---------------------------------------------------------------------------
def parse_statement(filename: str, data: bytes) -> StatementResult:
    rows = read_rows(filename, data)
    if not rows:
        raise StatementError("파일에 내용이 없어요.")
    if len(rows) > MAX_ROWS + 50:
        raise StatementError(f"한 번에 {MAX_ROWS}건까지 가져올 수 있어요. 기간을 나눠서 내려받아 주세요.")

    header_index, mapping = _find_header(rows)
    body = rows[header_index + 1:] if header_index is not None else rows

    parsed: list[ParsedRow] = []
    invalid = 0
    for row in body:
        status, item = _row_from_header(row, mapping) if header_index is not None else _row_heuristic(row)
        if status == "invalid":
            invalid += 1
        elif status == "ok" and item is not None:
            parsed.append(item)

    return build_result(parsed, invalid)


def build_result(parsed: list[ParsedRow], invalid: int) -> StatementResult:
    """읽어낸 행 목록을 가계부 거래 목록과 최근 잔고로 정리합니다. (파일/PDF 공통)"""
    if not parsed:
        raise StatementError("거래 내역을 찾지 못했어요. 거래일시, 금액, 잔액 같은 열이 있는 거래내역 파일인지 확인해 주세요.")

    # 시간 순서(오래된 것 -> 최신)로 맞춥니다. 은행마다 최신순/과거순이 달라요.
    chronological = list(reversed(parsed)) if parsed[0].occurred_at > parsed[-1].occurred_at else list(parsed)

    result = StatementResult(total_rows=len(parsed) + invalid, skipped_invalid=invalid)
    latest = next((r for r in reversed(chronological) if r.balance is not None), None)
    if latest:
        result.latest_balance = latest.balance
        result.latest_balance_at = latest.occurred_at.isoformat(timespec="seconds")

    for row in chronological:
        if row.is_hold:
            result.skipped_holds += 1
            continue
        is_income = row.amount > 0
        category = categorize(row.description, is_income)
        if category is None:
            result.unknown_indices.append(len(result.transactions))
            category = "기타"
        result.transactions.append({
            "item_name": (row.description or ("입금" if is_income else "출금"))[:120],
            "amount": abs(row.amount),
            "transaction_type": "income" if is_income else "expense",
            "category": category,
            "occurred_on": row.occurred_at.date().isoformat(),
            "occurred_at": row.occurred_at.isoformat(timespec="seconds"),
        })
    return result


finish_row = _finish  # 다른 모듈(PDF)에서 같은 규칙으로 행을 만들 때 사용
