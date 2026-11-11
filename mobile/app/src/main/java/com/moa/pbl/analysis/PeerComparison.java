package com.moa.pbl.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 같은 성별·지역·연령대 테스트 사용자와 소비 구성(수입 대비 비율)을 비교한다. 화면 코드 없이 숫자와 문장만 만든다. */
public final class PeerComparison {

    /** 비교 그룹이 이보다 적으면 기준을 넓힌다 */
    public static final int MIN_GROUP_SIZE = 5;

    private PeerComparison() {
    }

    /** 한 달의 소비 구성. 저축 = 수입 - 지출(0 이상). 비율은 max(수입, 지출) 대비 %. */
    public static final class Composition {
        public final long income;
        public final long expense;
        public final long saving;
        /** 저축 + 지출 카테고리 순서 */
        public final Map<String, Long> amounts;
        public final Map<String, Double> shares;
        public final boolean empty;

        Composition(long income, long expense, long saving, Map<String, Long> amounts, Map<String, Double> shares) {
            this.income = income;
            this.expense = expense;
            this.saving = saving;
            this.amounts = amounts;
            this.shares = shares;
            this.empty = income == 0L && expense == 0L;
        }
    }

    public static final class Result {
        public final String groupLabel;
        public final int peerCount;
        public final boolean widened;
        public final Composition mine;
        public final Composition peer;
        public final List<String> feedback;

        Result(String groupLabel, int peerCount, boolean widened, Composition mine, Composition peer, List<String> feedback) {
            this.groupLabel = groupLabel;
            this.peerCount = peerCount;
            this.widened = widened;
            this.mine = mine;
            this.peer = peer;
            this.feedback = feedback;
        }
    }

    public static Composition composition(long income, Map<String, Long> rawCategories) {
        Map<String, Long> categories = CategoryPalette.normalize(rawCategories);
        long expense = CategoryPalette.sum(categories);
        long saving = Math.max(0L, income - expense);
        long base = Math.max(income, expense);

        Map<String, Long> amounts = new LinkedHashMap<>();
        amounts.put(CategoryPalette.SAVING, saving);
        amounts.putAll(categories);

        Map<String, Double> shares = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : amounts.entrySet())
            shares.put(entry.getKey(), base == 0L ? 0.0 : entry.getValue() * 100.0 / base);
        return new Composition(income, expense, saving, amounts, shares);
    }

    /** 5살 단위 연령대 (예: 22세 -> 20~24세) */
    public static String ageBandLabel(int age) {
        int start = (age / 5) * 5;
        return start + "~" + (start + 4) + "세";
    }

    private static boolean sameBand(int a, int b) {
        return a >= 0 && b >= 0 && a / 5 == b / 5;
    }

    /**
     * @param age 0 미만이면 나이 기준 없이 비교
     * @param region null이면 지역 기준 없이 비교
     */
    public static Result compare(long myIncome, Map<String, Long> myCategories, String gender, String region,
                                 int age, List<PeerDataGenerator.PeerRecord> dataset) {
        boolean hasAge = age >= 0;
        boolean hasRegion = region != null && !region.isEmpty();

        // 1순위: 성별 + 지역 + 연령대 → 2순위: 성별 + 연령대 → 3순위: 연령대 → 4순위: 전체
        List<PeerDataGenerator.PeerRecord> peers = new ArrayList<>();
        String label = null;
        boolean widened = false;

        if (hasAge && hasRegion) {
            peers = filter(dataset, gender, region, age);
            label = ageBandLabel(age) + " · " + gender + " · " + region;
        }
        if (peers.size() < MIN_GROUP_SIZE && hasAge) {
            widened = label != null;
            peers = filter(dataset, gender, null, age);
            label = ageBandLabel(age) + " · " + gender;
        }
        if (peers.size() < MIN_GROUP_SIZE && hasAge) {
            widened = true;
            peers = filter(dataset, null, null, age);
            label = ageBandLabel(age) + " 전체";
        }
        if (peers.size() < MIN_GROUP_SIZE) {
            widened = true;
            peers = new ArrayList<>(dataset);
            label = "전체 사용자";
        }

        int n = Math.max(peers.size(), 1);
        long incomeSum = 0L;
        Map<String, Long> peerSum = CategoryPalette.normalize(null);
        for (PeerDataGenerator.PeerRecord record : peers) {
            incomeSum += record.income;
            for (Map.Entry<String, Long> entry : record.expenses.entrySet())
                peerSum.put(entry.getKey(), peerSum.get(entry.getKey()) + entry.getValue());
        }
        Map<String, Long> peerAvg = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : peerSum.entrySet()) peerAvg.put(entry.getKey(), entry.getValue() / n);

        Composition mine = composition(myIncome, myCategories);
        Composition peer = composition(incomeSum / n, peerAvg);
        return new Result(label, peers.size(), widened, mine, peer, buildFeedback(mine, peer));
    }

    private static List<PeerDataGenerator.PeerRecord> filter(List<PeerDataGenerator.PeerRecord> dataset,
                                                             String gender, String region, int age) {
        List<PeerDataGenerator.PeerRecord> result = new ArrayList<>();
        for (PeerDataGenerator.PeerRecord record : dataset) {
            if (gender != null && !gender.equals(record.gender)) continue;
            if (region != null && !region.equals(record.region)) continue;
            if (!sameBand(record.age, age)) continue;
            result.add(record);
        }
        return result;
    }

    private static List<String> buildFeedback(Composition mine, Composition peer) {
        List<String> lines = new ArrayList<>();
        if (mine.empty) {
            lines.add("이 달에는 기록이 없어서 또래 평균 구성만 보여드려요. 거래를 기록하면 내 소비와 비교돼요.");
            return lines;
        }
        if (mine.income == 0L) {
            lines.add("이 달 수입 기록이 없어서 지출 기준으로 비율을 계산했어요. 수입도 기록하면 저축 비율까지 비교돼요.");
        } else if (mine.expense > mine.income) {
            lines.add("이 달은 수입보다 " + won(mine.expense - mine.income) + " 더 썼어요.");
        }

        double savingDiff = mine.shares.get(CategoryPalette.SAVING) - peer.shares.get(CategoryPalette.SAVING);
        if (mine.income > 0L) {
            if (savingDiff >= 5) lines.add("저축 비율이 또래보다 " + pp(savingDiff) + " 높아요. 잘하고 있어요!");
            else if (savingDiff <= -5) lines.add("저축 비율이 또래보다 " + pp(-savingDiff) + " 낮아요.");
        }

        // 또래보다 비율이 5%p 이상 높은 지출 항목 중 차이가 큰 순서로 2개
        List<String> over = new ArrayList<>();
        List<Double> gaps = new ArrayList<>();
        for (String category : CategoryPalette.EXPENSE_CATEGORIES) {
            double gap = mine.shares.get(category) - peer.shares.get(category);
            if (gap < 5) continue;
            int index = 0;
            while (index < gaps.size() && gaps.get(index) >= gap) index++;
            over.add(index, category);
            gaps.add(index, gap);
        }
        for (int i = 0; i < Math.min(2, over.size()); i++)
            lines.add(over.get(i) + " 비율이 또래보다 " + pp(gaps.get(i)) + " 높아요.");
        if (over.isEmpty()) lines.add("또래보다 비율이 눈에 띄게 높은 지출 항목은 없어요.");
        return lines;
    }

    public static String percent(double value) {
        return String.format(Locale.KOREA, "%.1f%%", value);
    }

    private static String pp(double value) {
        return String.format(Locale.KOREA, "%.1f%%p", value);
    }

    private static String won(long amount) {
        return java.text.NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }
}
