package com.moa.pbl.analysis;

import java.text.NumberFormat;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 선택한 달과 그 이전 달의 카테고리별 지출 금액·비율 변화를 계산한다. */
public final class MonthComparison {

    private MonthComparison() {
    }

    public static final class Row {
        public final String category;
        public final long previous;
        public final long current;
        /** 그 달 총지출 대비 % */
        public final double previousShare;
        public final double currentShare;

        Row(String category, long previous, long current, double previousShare, double currentShare) {
            this.category = category;
            this.previous = previous;
            this.current = current;
            this.previousShare = previousShare;
            this.currentShare = currentShare;
        }

        public double shareChange() {
            return currentShare - previousShare;
        }
    }

    public static final class Result {
        public final YearMonth current;
        public final YearMonth previous;
        public final long currentTotal;
        public final long previousTotal;
        public final List<Row> rows;
        public final List<String> summary;

        Result(YearMonth current, YearMonth previous, long currentTotal, long previousTotal, List<Row> rows, List<String> summary) {
            this.current = current;
            this.previous = previous;
            this.currentTotal = currentTotal;
            this.previousTotal = previousTotal;
            this.rows = rows;
            this.summary = summary;
        }
    }

    public static Result compare(YearMonth month, Map<String, Long> currentRaw, Map<String, Long> previousRaw) {
        Map<String, Long> current = CategoryPalette.normalize(currentRaw);
        Map<String, Long> previous = CategoryPalette.normalize(previousRaw);
        long currentTotal = CategoryPalette.sum(current);
        long previousTotal = CategoryPalette.sum(previous);

        List<Row> rows = new ArrayList<>();
        for (String category : CategoryPalette.EXPENSE_CATEGORIES) {
            long now = current.get(category);
            long before = previous.get(category);
            // 두 달 모두 기록이 있으면 쓴 항목만, 둘 다 없으면 기본 화면을 위해 모든 항목을 0으로 보여준다
            if (now == 0L && before == 0L && (currentTotal > 0L || previousTotal > 0L)) continue;
            rows.add(new Row(category, before, now,
                    previousTotal == 0L ? 0.0 : before * 100.0 / previousTotal,
                    currentTotal == 0L ? 0.0 : now * 100.0 / currentTotal));
        }
        Collections.sort(rows, (a, b) -> Long.compare(Math.max(b.current, b.previous), Math.max(a.current, a.previous)));

        YearMonth previousMonth = month.minusMonths(1);
        return new Result(month, previousMonth, currentTotal, previousTotal, rows,
                buildSummary(month, previousMonth, currentTotal, previousTotal, rows));
    }

    private static List<String> buildSummary(YearMonth month, YearMonth previousMonth, long currentTotal,
                                             long previousTotal, List<Row> rows) {
        String now = AnalysisMonth.shortLabel(month);
        String before = AnalysisMonth.shortLabel(previousMonth);
        List<String> lines = new ArrayList<>();

        if (currentTotal == 0L && previousTotal == 0L) {
            lines.add(before + "과 " + now + " 모두 기록이 없어요. 거래를 기록하면 항목별 변화를 보여드려요.");
            return lines;
        }
        if (previousTotal == 0L) {
            lines.add(before + " 기록이 없어서 " + now + " 지출만 보여드려요.");
            return lines;
        }
        if (currentTotal == 0L) {
            lines.add(now + " 기록이 아직 없어요. " + before + "에는 " + won(previousTotal) + "을 썼어요.");
            return lines;
        }

        long diff = currentTotal - previousTotal;
        int rate = (int) Math.round(diff * 100.0 / previousTotal);
        if (diff > 0) lines.add(before + "보다 지출이 " + rate + "% (" + won(diff) + ") 늘었어요.");
        else if (diff < 0) lines.add(before + "보다 지출이 " + Math.abs(rate) + "% (" + won(-diff) + ") 줄었어요.");
        else lines.add(before + "과 총지출이 같아요.");

        Row up = null;
        Row down = null;
        for (Row row : rows) {
            if (up == null || row.shareChange() > up.shareChange()) up = row;
            if (down == null || row.shareChange() < down.shareChange()) down = row;
        }
        if (up != null && up.shareChange() >= 3)
            lines.add("비중이 가장 커진 항목은 " + up.category + "(" + pp(up.shareChange()) + ")예요.");
        if (down != null && down.shareChange() <= -3)
            lines.add("비중이 가장 줄어든 항목은 " + down.category + "(" + pp(down.shareChange()) + ")예요.");
        return lines;
    }

    public static String pp(double value) {
        return String.format(Locale.KOREA, "%+.1f%%p", value);
    }

    public static String won(long amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }

    public static String signedWon(long amount) {
        return (amount > 0 ? "+" : amount < 0 ? "-" : "") + won(Math.abs(amount));
    }
}
