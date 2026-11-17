package com.moa.pbl;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 항목명으로 카테고리를 추정합니다.
 * 서버(backend/app/services/bank_statement.py)의 규칙과 같은 내용을 유지하세요.
 */
public final class CategoryRules {
    private CategoryRules() {}

    private static final String[][] EXPENSE_RULES = {
            {"교통", "택시", "카카오t", "버스", "지하철", "티머니", "교통", "코레일", "ktx", "srt", "하이패스", "주유", "주차", "타다", "고속도로", "철도"},
            {"카페", "스타벅스", "카페", "커피", "이디야", "투썸", "메가커피", "컴포즈", "빽다방", "할리스", "폴바셋", "파스쿠찌", "베이커리", "디저트"},
            {"식비", "편의점", "gs25", "cu", "세븐일레븐", "이마트24", "마트", "식당", "치킨", "피자", "배달의민족", "배민", "요기요", "쿠팡이츠",
                    "맥도날드", "롯데리아", "버거킹", "김밥", "분식", "국밥", "고기", "음식", "홈플러스", "이마트", "하나로", "푸드", "밥", "반점"},
            {"쇼핑", "쿠팡", "네이버페이", "무신사", "올리브영", "다이소", "11번가", "지마켓", "옥션", "알리", "테무", "의류", "신발", "백화점", "스마트스토어"},
            {"의료", "병원", "의원", "약국", "치과", "한의원", "클리닉", "의료"},
            {"문화", "영화", "cgv", "메가박스", "롯데시네마", "넷플릭스", "유튜브", "디즈니", "왓챠", "티빙", "웨이브", "멜론", "스포티파이", "게임",
                    "노래방", "pc방", "피시방", "서점", "교보문고", "영풍문고", "스팀"},
            {"생활", "통신", "kt", "skt", "lgu", "요금", "관리비", "월세", "전기", "가스", "수도", "보험"},
            {"교육", "학원", "교육", "학비", "등록금", "인강", "수강"},
    };

    private static final String[][] INCOME_RULES = {
            {"급여", "급여", "월급", "알바", "급료", "상여", "보너스"},
            {"용돈", "용돈", "엄마", "아빠", "부모"},
    };

    /** 규칙에 맞는 카테고리를 돌려주고, 없으면 "기타" */
    public static String categorize(String text, boolean income) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        String[][] rules = income ? INCOME_RULES : EXPENSE_RULES;
        for (String[] rule : rules) {
            for (int i = 1; i < rule.length; i++) {
                if (contains(lower, rule[i])) return rule[0];
            }
        }
        return "기타";
    }

    private static boolean contains(String text, String keyword) {
        if (isShortAscii(keyword)) { // cu, kt 같은 짧은 영문은 단어 경계를 확인합니다.
            return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(keyword) + "(?![a-z0-9])").matcher(text).find();
        }
        return text.contains(keyword);
    }

    private static boolean isShortAscii(String keyword) {
        if (keyword.length() > 4) return false;
        for (int i = 0; i < keyword.length(); i++) {
            if (keyword.charAt(i) > 127) return false;
        }
        return true;
    }
}
