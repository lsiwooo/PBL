package com.moa.pbl.analysis;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 소비 분석에서 쓰는 카테고리 묶음과 색상.
 * 가계부(ledger_parser)의 세부 카테고리 9개를 화면에서는 5개 묶음으로 보여준다.
 * 저장되는 데이터는 바꾸지 않고, 화면에 보여줄 때만 묶는다.
 */
public final class CategoryPalette {

    public static final String SAVING = "저축";

    public static final String FOOD = "식비";
    public static final String LIVING = "생활";
    public static final String TRANSPORT = "교통";
    public static final String LEISURE = "쇼핑·여가";
    public static final String ETC = "교육·기타";

    /** 화면에 보여주는 지출 묶음 (순서 고정) */
    public static final String[] EXPENSE_CATEGORIES = {FOOD, LIVING, TRANSPORT, LEISURE, ETC};

    private CategoryPalette() {
    }

    /** 가계부의 세부 카테고리를 묶음 이름으로 바꾼다 */
    public static String group(String raw) {
        if (raw == null) return ETC;
        switch (raw.trim()) {
            case "식비":
            case "카페":
                return FOOD;
            case "생활":
            case "의료":
                return LIVING;
            case "교통":
                return TRANSPORT;
            case "쇼핑":
            case "문화":
            case LEISURE:
                return LEISURE;
            default:
                return ETC; // 교육, 기타, 알 수 없는 값
        }
    }

    public static int colorOf(String category) {
        switch (category) {
            case FOOD: return 0xffe5674e;
            case LIVING: return 0xff4b5a6b;
            case TRANSPORT: return 0xff3f7fd6;
            case LEISURE: return 0xff8e5cc4;
            case ETC: return 0xffe0a83a;
            case SAVING: return 0xff2fa866;
            default: return 0xffa0a7ae;
        }
    }

    /** 세부 카테고리별 금액을 묶음별 금액으로 합친다. 순서는 항상 EXPENSE_CATEGORIES와 같다. */
    public static Map<String, Long> normalize(Map<String, Long> raw) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String category : EXPENSE_CATEGORIES) result.put(category, 0L);
        if (raw == null) return result;
        for (Map.Entry<String, Long> entry : raw.entrySet()) {
            if (entry.getValue() == null) continue;
            String key = group(entry.getKey());
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
