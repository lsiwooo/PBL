package com.moa.pbl.analysis;

import java.util.LinkedHashMap;
import java.util.Map;

/** 소비 분석에서 쓰는 카테고리 목록과 색상 */
public final class CategoryPalette {

    public static final String SAVING = "저축";

    /** backend/app/services/ledger_parser.py의 지출 카테고리와 이름이 같아야 한다 (급여·용돈은 수입이라 제외) */
    public static final String[] EXPENSE_CATEGORIES = {
            "식비", "카페", "교통", "쇼핑", "생활", "의료", "문화", "교육", "기타"
    };

    private CategoryPalette() {
    }

    public static int colorOf(String category) {
        switch (category) {
            case "식비": return 0xffe5674e;
            case "카페": return 0xfff2a93b;
            case "교통": return 0xff3f7fd6;
            case "쇼핑": return 0xff8e5cc4;
            case "생활": return 0xff4b5a6b;
            case "의료": return 0xffd65a8a;
            case "문화": return 0xff2fa866;
            case "교육": return 0xff1fa3a0;
            case SAVING: return 0xff1b4fa3;
            default: return 0xffa0a7ae;
        }
    }

    /** 가계부 카테고리를 분석용 목록에 맞춘다. 목록에 없는 값은 "기타"로 합친다. 순서는 항상 같다. */
    public static Map<String, Long> normalize(Map<String, Long> raw) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String category : EXPENSE_CATEGORIES) result.put(category, 0L);
        if (raw == null) return result;
        for (Map.Entry<String, Long> entry : raw.entrySet()) {
            if (entry.getValue() == null) continue;
            String key = result.containsKey(entry.getKey()) ? entry.getKey() : "기타";
            result.put(key, result.get(key) + entry.getValue());
        }
        return result;
    }

    public static long sum(Map<String, Long> values) {
        long total = 0L;
        for (long value : values.values()) total += value;
        return total;
    }
}
