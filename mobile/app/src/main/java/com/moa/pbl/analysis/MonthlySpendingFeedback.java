package com.moa.pbl.analysis;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Month-over-month spending feedback based on the user's ledger entries. */
public final class MonthlySpendingFeedback {

    private MonthlySpendingFeedback() {
    }

    private static Integer percentDiff(long mine, long base) {
        if (base == 0L) return null;
        return (int) Math.round((mine - base) * 100.0 / base);
    }

    public static List<String> compareMonths(long thisExpense, long lastExpense,
                                              Map<String, Long> thisCategories,
                                              Map<String, Long> lastCategories) {
        List<String> lines = new ArrayList<>();
        if (lastExpense == 0L) {
            lines.add("지난달 기록이 없어서 전월 비교는 다음 달부터 볼 수 있어요.");
            return lines;
        }

        Integer diff = percentDiff(thisExpense, lastExpense);
        if (diff != null && diff > 0) lines.add("지난달보다 지출이 " + diff + "% (" + won(thisExpense - lastExpense) + ") 늘었어요.");
        else if (diff != null && diff < 0) lines.add("지난달보다 지출이 " + Math.abs(diff) + "% (" + won(lastExpense - thisExpense) + ") 줄었어요.");
        else lines.add("지난달과 지출이 같아요.");

        String biggest = null;
        long biggestIncrease = 0L;
        for (Map.Entry<String, Long> entry : thisCategories.entrySet()) {
            Long last = lastCategories.get(entry.getKey());
            long increase = entry.getValue() - (last == null ? 0L : last);
            if (increase > biggestIncrease) {
                biggest = entry.getKey();
                biggestIncrease = increase;
            }
        }
        if (biggest != null && biggestIncrease >= 10_000L) {
            lines.add("가장 많이 늘어난 항목은 " + biggest + categoryCopula(biggest) + ". 지난달보다 " + won(biggestIncrease) + " 더 썼어요.");
        }
        return lines;
    }

    public static String categoryCopula(String word) {
        if (word.isEmpty()) return "이에요";
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return "이에요";
        return (last - 0xAC00) % 28 == 0 ? "예요" : "이에요";
    }

    private static String won(long amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }
}
