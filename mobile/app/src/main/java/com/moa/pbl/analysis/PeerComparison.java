package com.moa.pbl.analysis;

import androidx.annotation.Nullable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 또래 비교와 전월 비교 계산. 화면 코드 없이 숫자와 문장만 만든다. */
public final class PeerComparison {

    private PeerComparison() {
    }

    public static final class Result {
        public final int peerCount;
        public final long myIncome;
        public final long peerAvgIncome;
        public final long myExpense;
        public final long peerAvgExpense;
        public final Map<String, Long> myCategories;
        public final Map<String, Long> peerAvgCategories;
        public final List<String> feedback;

        Result(int peerCount, long myIncome, long peerAvgIncome, long myExpense, long peerAvgExpense,
               Map<String, Long> myCategories, Map<String, Long> peerAvgCategories, List<String> feedback) {
            this.peerCount = peerCount;
            this.myIncome = myIncome;
            this.peerAvgIncome = peerAvgIncome;
            this.myExpense = myExpense;
            this.peerAvgExpense = peerAvgExpense;
            this.myCategories = myCategories;
            this.peerAvgCategories = peerAvgCategories;
            this.feedback = feedback;
        }
    }

    /** base 대비 mine이 몇 % 많은지(음수면 적음). 기준값이 0이면 null */
    @Nullable
    public static Integer percentDiff(long mine, long base) {
        if (base == 0L) return null;
        return (int) Math.round((mine - base) * 100.0 / base);
    }

    /** 가계부 카테고리를 비교용 카테고리로 맞춘다 (목록에 없으면 "기타") */
    public static Map<String, Long> normalize(Map<String, Long> raw) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String category : PeerDataGenerator.CATEGORIES) result.put(category, 0L);
        for (Map.Entry<String, Long> entry : raw.entrySet()) {
            String key = result.containsKey(entry.getKey()) ? entry.getKey() : "기타";
            result.put(key, result.get(key) + entry.getValue());
        }
        return result;
    }

    /** 이번 달 실제 기록을 같은 성별·나이 또래 평균과 비교한다 */
    public static Result compare(long myIncome, long myExpense, Map<String, Long> myCategoriesRaw,
                                 PeerProfile profile, List<PeerDataGenerator.PeerRecord> dataset) {
        List<PeerDataGenerator.PeerRecord> peers = new ArrayList<>();
        for (PeerDataGenerator.PeerRecord record : dataset) {
            if (record.profile.isPeerOf(profile)) peers.add(record);
        }
        int n = Math.max(peers.size(), 1);

        long incomeSum = 0L;
        Map<String, Long> peerAvg = new LinkedHashMap<>();
        for (String category : PeerDataGenerator.CATEGORIES) peerAvg.put(category, 0L);
        for (PeerDataGenerator.PeerRecord record : peers) {
            incomeSum += record.income;
            for (String category : PeerDataGenerator.CATEGORIES) {
                Long value = record.expenses.get(category);
                peerAvg.put(category, peerAvg.get(category) + (value == null ? 0L : value));
            }
        }
        long peerAvgExpense = 0L;
        for (String category : PeerDataGenerator.CATEGORIES) {
            long avg = peerAvg.get(category) / n;
            peerAvg.put(category, avg);
            peerAvgExpense += avg;
        }
        long peerAvgIncome = incomeSum / n;
        Map<String, Long> myCategories = normalize(myCategoriesRaw);

        List<String> feedback = new ArrayList<>();
        Integer totalDiff = percentDiff(myExpense, peerAvgExpense);
        if (totalDiff != null) {
            if (totalDiff >= 10) feedback.add("총지출이 또래 평균보다 " + totalDiff + "% 많아요.");
            else if (totalDiff <= -10) feedback.add("총지출이 또래 평균보다 " + Math.abs(totalDiff) + "% 적어요.");
            else feedback.add("총지출은 또래 평균과 비슷해요.");
        }

        // 평균보다 30% 이상, 1만 원 이상 더 쓴 항목 중 차액이 큰 순서로 2개
        List<long[]> over = new ArrayList<>();
        for (int i = 0; i < PeerDataGenerator.CATEGORIES.length; i++) {
            String category = PeerDataGenerator.CATEGORIES[i];
            long mine = myCategories.get(category);
            long avg = peerAvg.get(category);
            Integer diff = percentDiff(mine, avg);
            if (diff != null && diff >= 30 && mine - avg >= 10_000L) over.add(new long[]{i, diff, mine - avg});
        }
        Collections.sort(over, (a, b) -> Long.compare(b[2], a[2]));
        if (over.isEmpty()) {
            feedback.add("또래보다 눈에 띄게 많이 쓴 항목은 없어요.");
        } else {
            for (int i = 0; i < Math.min(2, over.size()); i++) {
                long[] item = over.get(i);
                feedback.add(PeerDataGenerator.CATEGORIES[(int) item[0]] + " 지출이 또래 평균보다 " + item[1]
                        + "% 많아요. 월 " + won(item[2]) + "을 줄이면 평균 수준이 돼요.");
            }
        }

        return new Result(peers.size(), myIncome, peerAvgIncome, myExpense, peerAvgExpense,
                myCategories, peerAvg, feedback);
    }

    /** 지난달 대비 이번 달 지출 변화 */
    public static List<String> monthOverMonth(long thisExpense, long lastExpense,
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
            lines.add("가장 많이 늘어난 항목은 " + biggest + copula(biggest) + ". 지난달보다 " + won(biggestIncrease) + " 더 썼어요.");
        }
        return lines;
    }

    /** 받침 유무에 따라 "이에요"/"예요"를 고른다 (예: 교통이에요, 식비예요) */
    public static String copula(String word) {
        if (word.isEmpty()) return "이에요";
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return "이에요";
        return (last - 0xAC00) % 28 == 0 ? "예요" : "이에요";
    }

    public static String won(long amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }

    public static String man(long amount) {
        if (Math.abs(amount) >= 10_000L) return String.format(Locale.KOREA, "%.1f만", amount / 10_000.0);
        return won(amount);
    }
}
