package com.moa.pbl.analysis;

import java.time.YearMonth;

/** 소비 피드백 화면에서 보고 있는 달. 화면을 다시 그려도 선택한 달이 유지된다. */
public final class AnalysisMonth {

    private static YearMonth selected = YearMonth.now();

    private AnalysisMonth() {
    }

    public static YearMonth get() {
        YearMonth now = YearMonth.now();
        if (selected.isAfter(now)) selected = now;
        return selected;
    }

    public static void previous() {
        selected = get().minusMonths(1);
    }

    public static void next() {
        YearMonth next = get().plusMonths(1);
        if (!next.isAfter(YearMonth.now())) selected = next;
    }

    /** 이번 달을 보고 있으면 true (다음 달로 넘어갈 수 없음) */
    public static boolean isCurrent() {
        return !get().isBefore(YearMonth.now());
    }

    public static String label(YearMonth month) {
        return month.getYear() + "년 " + month.getMonthValue() + "월";
    }

    public static String shortLabel(YearMonth month) {
        return month.getMonthValue() + "월";
    }
}
