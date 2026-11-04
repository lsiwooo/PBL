package com.moa.pbl.analysis;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 이번 달 실제 기록을 비슷한 나이·직업의 테스트 사용자 평균과 비교한다. 화면 코드 없이 숫자와 문장만 만든다. */
public final class PeerComparison {

    /** 비교 그룹이 이보다 적으면 기준을 넓힌다 */
    public static final int MIN_GROUP_SIZE = 5;

    private PeerComparison() {
    }

    public static final class Result {
        public final String groupLabel;
        public final int peerCount;
        public final long myIncome;
        public final long peerAvgIncome;
        public final long myExpense;
        public final long peerAvgExpense;
        public final Map<String, Long> myCategories;
        public final Map<String, Long> peerAvgCategories;
        public final List<String> feedback;

        Result(String groupLabel, int peerCount, long myIncome, long peerAvgIncome, long myExpense,
               long peerAvgExpense, Map<String, Long> myCategories, Map<String, Long> peerAvgCategories,
               List<String> feedback) {
            this.groupLabel = groupLabel;
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

    /** 5살 단위 나이대 (예: 22세 -> 20~24세) */
    public static String ageBandLabel(int age) {
        int start = (age / 5) * 5;
        return start + "~" + (start + 4) + "세";
    }

    private static boolean sameAgeBand(int a, int b) {
        return a / 5 == b / 5;
    }

    /** base 대비 mine이 몇 % 많은지(음수면 적음). 기준값이 0이면 null */
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

    public static Result compare(long myIncome, long myExpense, Map<String, Long> myCategoriesRaw,
                                 int age, String occupation, List<PeerDataGenerator.PeerRecord> dataset) {
        // 1순위: 같은 나이대 + 같은 직업
        List<PeerDataGenerator.PeerRecord> peers = new ArrayList<>();
        for (PeerDataGenerator.PeerRecord record : dataset)
            if (sameAgeBand(record.age, age) && record.occupation.equals(occupation)) peers.add(record);
        String label = ageBandLabel(age) + " " + occupation;

        // 2순위: 같은 나이대 전체
        if (peers.size() < MIN_GROUP_SIZE) {
            peers.clear();
            for (PeerDataGenerator.PeerRecord record : dataset)
                if (sameAgeBand(record.age, age)) peers.add(record);
            label = ageBandLabel(age) + " 전체";
        }

        // 3순위: 전체 사용자
        if (peers.size() < MIN_GROUP_SIZE) {
            peers = new ArrayList<>(dataset);
            label = "전체 사용자";
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

        return new Result(label, peers.size(), myIncome, peerAvgIncome, myExpense, peerAvgExpense,
                myCategories, peerAvg, buildFeedback(myExpense, peerAvgExpense, myCategories, peerAvg));
    }

    private static List<String> buildFeedback(long myExpense, long peerAvgExpense,
                                              Map<String, Long> myCategories, Map<String, Long> peerAvg) {
        List<String> feedback = new ArrayList<>();
        Integer totalDiff = percentDiff(myExpense, peerAvgExpense);
        if (totalDiff != null) {
            if (totalDiff >= 10) feedback.add("총지출이 비교 그룹 평균보다 " + totalDiff + "% 많아요.");
            else if (totalDiff <= -10) feedback.add("총지출이 비교 그룹 평균보다 " + Math.abs(totalDiff) + "% 적어요.");
            else feedback.add("총지출은 비교 그룹 평균과 비슷해요.");
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
            feedback.add("비교 그룹보다 눈에 띄게 많이 쓴 항목은 없어요.");
        } else {
            for (int i = 0; i < Math.min(2, over.size()); i++) {
                long[] item = over.get(i);
                feedback.add(PeerDataGenerator.CATEGORIES[(int) item[0]] + " 지출이 비교 그룹 평균보다 " + item[1]
                        + "% 많아요. 월 " + won(item[2]) + "을 줄이면 평균 수준이 돼요.");
            }
        }
        return feedback;
    }

    public static String won(long amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }

    public static String man(long amount) {
        if (Math.abs(amount) >= 10_000L) return String.format(Locale.KOREA, "%.1f만", amount / 10_000.0);
        return won(amount);
    }
}
