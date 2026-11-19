package com.moa.pbl.analysis;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 내 소비 구성을 테스트 사용자 그룹과 비교하고 설명 문장을 만든다. 화면 코드는 없다.
 * 기준 그룹: 같은 연령대·성별 / 같은 지역 또래(지역 + 연령대) / 같은 연령대 전체
 */
public final class PeerComparison {

    /** 그룹 인원이 이보다 적으면 그 그룹은 비교하지 않는다 */
    public static final int MIN_GROUP_SIZE = 5;

    private PeerComparison() {
    }

    /** 한 달의 소비 구성. 저축 = 수입 - 지출(0 이상). 비율은 max(수입, 지출) 대비 %. */
    public static final class Composition {
        public final long income;
        public final long expense;
        public final long saving;
        /** 저축 + 지출 묶음 순서 */
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

    /** 비교 그룹 하나 (띠그래프 한 줄) */
    public static final class Group {
        public final String title;
        public final String detail;
        public final int count;
        public final Composition composition;

        Group(String title, String detail, int count, Composition composition) {
            this.title = title;
            this.detail = detail;
            this.count = count;
            this.composition = composition;
        }
    }

    public static final class Result {
        /** 주 비교 그룹 (같은 연령대·성별) 설명 */
        public final String groupLabel;
        public final int peerCount;
        public final boolean widened;
        public final Composition mine;
        /** 주 비교 그룹의 평균 구성 */
        public final Composition peer;
        /** 띠그래프로 보여줄 그룹들 (주 그룹 포함) */
        public final List<Group> groups;
        /** 설명 문장 */
        public final List<String> feedback;

        Result(String groupLabel, int peerCount, boolean widened, Composition mine, Composition peer,
               List<Group> groups, List<String> feedback) {
            this.groupLabel = groupLabel;
            this.peerCount = peerCount;
            this.widened = widened;
            this.mine = mine;
            this.peer = peer;
            this.groups = groups;
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
     * @param region null이면 지역 비교를 생략
     */
    public static Result compare(long myIncome, Map<String, Long> myCategories, String gender, String region,
                                 int age, List<PeerDataGenerator.PeerRecord> dataset) {
        boolean hasAge = age >= 0;
        boolean hasRegion = region != null && !region.isEmpty();
        String band = hasAge ? ageBandLabel(age) : "";

        // 주 비교 그룹은 입력된 정보만 사용한다. 성별이 없어도 연령·지역 기준 비교를 계속한다.
        List<PeerDataGenerator.PeerRecord> main = hasAge ? filter(dataset, gender, null, age) : new ArrayList<>();
        String mainTitle = gender == null ? "같은 연령대" : "같은 연령대·성별";
        String mainDetail = band + (gender == null ? "" : " " + gender);
        boolean widened = false;
        if (main.size() < MIN_GROUP_SIZE) {
            widened = true;
            if (gender != null) {
                main = filter(dataset, gender, null, -1);
                mainTitle = "같은 성별";
                mainDetail = gender + " 전체";
            } else if (hasRegion) {
                main = filter(dataset, null, region, -1);
                mainTitle = "같은 지역";
                mainDetail = region + " 전체";
            } else {
                main = new ArrayList<>(dataset);
                mainTitle = "전체 사용자";
                mainDetail = "전체";
            }
        }
        if (main.size() < MIN_GROUP_SIZE) {
            main = new ArrayList<>(dataset);
            mainTitle = "전체 사용자";
            mainDetail = "전체";
        }

        Composition mine = composition(myIncome, myCategories);
        Composition peer = average(main);

        List<Group> groups = new ArrayList<>();
        groups.add(new Group(mainTitle, mainDetail, main.size(), peer));

        Composition regionPeer = null;
        if (hasRegion && hasAge) {
            List<PeerDataGenerator.PeerRecord> local = filter(dataset, null, region, age);
            if (local.size() >= MIN_GROUP_SIZE) {
                regionPeer = average(local);
                groups.add(new Group("같은 지역 또래", region + " · " + band, local.size(), regionPeer));
            }
        }

        Composition agePeer = null;
        if (hasAge) {
            List<PeerDataGenerator.PeerRecord> sameAge = filter(dataset, null, null, age);
            if (sameAge.size() >= MIN_GROUP_SIZE) {
                agePeer = average(sameAge);
                groups.add(new Group("같은 연령대", band + " 전체", sameAge.size(), agePeer));
            }
        }

        List<String> feedback = explain(mine, peer, mainTitle, regionPeer, region, agePeer);
        return new Result(mainDetail, main.size(), widened, mine, peer, groups, feedback);
    }

    private static Composition average(List<PeerDataGenerator.PeerRecord> records) {
        int n = Math.max(records.size(), 1);
        long incomeSum = 0L;
        Map<String, Long> sum = CategoryPalette.normalize(null);
        for (PeerDataGenerator.PeerRecord record : records) {
            incomeSum += record.income;
            for (Map.Entry<String, Long> entry : record.expenses.entrySet()) {
                String key = CategoryPalette.group(entry.getKey());
                sum.put(key, sum.get(key) + entry.getValue());
            }
        }
        Map<String, Long> avg = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : sum.entrySet()) avg.put(entry.getKey(), entry.getValue() / n);
        return composition(incomeSum / n, avg);
    }

    private static List<PeerDataGenerator.PeerRecord> filter(List<PeerDataGenerator.PeerRecord> dataset,
                                                             String gender, String region, int age) {
        List<PeerDataGenerator.PeerRecord> result = new ArrayList<>();
        for (PeerDataGenerator.PeerRecord record : dataset) {
            if (gender != null && !gender.equals(record.gender)) continue;
            if (region != null && !region.equals(record.region)) continue;
            if (age >= 0 && !sameBand(record.age, age)) continue;
            result.add(record);
        }
        return result;
    }

    /** 숫자는 모두 위에서 계산한 값으로 만들고, 여기서는 문장만 조립한다 */
    private static List<String> explain(Composition mine, Composition peer, String peerName,
                                        Composition regionPeer, String region, Composition agePeer) {
        List<String> lines = new ArrayList<>();
        if (mine.empty) {
            lines.add("이 달에는 기록이 없어서 비교 그룹의 평균 구성만 보여드려요. 거래를 기록하면 내 소비와 비교해 설명해 드려요.");
            if (regionPeer != null && agePeer != null) lines.add(regionNote(regionPeer, agePeer, region));
            return lines;
        }

        // 1. 총지출 비교
        Integer totalDiff = percentDiff(mine.expense, peer.expense);
        if (totalDiff != null) {
            if (totalDiff >= 10)
                lines.add(peerName + " 평균보다 총지출이 " + totalDiff + "% 많아요 (나 " + won(mine.expense) + " · 평균 " + won(peer.expense) + ").");
            else if (totalDiff <= -10)
                lines.add(peerName + " 평균보다 총지출이 " + Math.abs(totalDiff) + "% 적어요 (나 " + won(mine.expense) + " · 평균 " + won(peer.expense) + ").");
            else
                lines.add(peerName + " 평균과 총지출이 비슷해요 (나 " + won(mine.expense) + " · 평균 " + won(peer.expense) + ").");
        }

        // 2. 저축 비율
        if (mine.income == 0L) {
            lines.add("이 달 수입 기록이 없어서 지출 기준으로 비율을 계산했어요. 수입도 기록하면 저축 비율까지 비교돼요.");
        } else if (mine.expense > mine.income) {
            lines.add("수입보다 " + won(mine.expense - mine.income) + " 더 써서 저축할 여유가 없었어요.");
        } else {
            double gap = mine.shares.get(CategoryPalette.SAVING) - peer.shares.get(CategoryPalette.SAVING);
            if (gap >= 5) lines.add("수입 중 저축 비율이 또래보다 " + pp(gap) + " 높아요. 좋은 흐름이에요.");
            else if (gap <= -5) lines.add("수입 중 저축 비율이 또래보다 " + pp(-gap) + " 낮아요.");
        }

        // 3. 비중이 가장 크게 차이 나는 묶음 (많이 쓴 쪽 / 적게 쓴 쪽)
        String over = null, under = null;
        double overGap = 0, underGap = 0;
        for (String category : CategoryPalette.EXPENSE_CATEGORIES) {
            double gap = mine.shares.get(category) - peer.shares.get(category);
            if (gap > overGap) { overGap = gap; over = category; }
            if (gap < underGap) { underGap = gap; under = category; }
        }
        if (over != null && overGap >= 5) {
            long amountGap = mine.amounts.get(over) - peer.amounts.get(over);
            lines.add(over + " 비중이 또래보다 " + pp(overGap) + " 높아요"
                    + (amountGap > 0 ? " (평균보다 " + won(amountGap) + " 더 씀)" : "") + ". 줄일 수 있는지 살펴보세요.");
        }
        if (under != null && underGap <= -5) {
            lines.add(under + " 비중은 또래보다 " + pp(-underGap) + " 낮아요.");
        }
        if ((over == null || overGap < 5) && (under == null || underGap > -5)) {
            lines.add("묶음별 소비 비중이 또래와 크게 다르지 않아요.");
        }

        // 4. 지역 차이
        if (regionPeer != null && agePeer != null) lines.add(regionNote(regionPeer, agePeer, region));
        return lines;
    }

    private static String regionNote(Composition regionPeer, Composition agePeer, String region) {
        Integer diff = percentDiff(regionPeer.expense, agePeer.expense);
        if (diff == null || Math.abs(diff) < 3)
            return region + " 또래의 평균 지출은 전국 같은 연령대와 비슷해요.";
        return region + " 또래는 전국 같은 연령대보다 평균 " + Math.abs(diff) + "% " + (diff > 0 ? "더" : "덜") + " 써요.";
    }

    public static Integer percentDiff(long mine, long base) {
        if (base == 0L) return null;
        return (int) Math.round((mine - base) * 100.0 / base);
    }

    public static String percent(double value) {
        return String.format(Locale.KOREA, "%.1f%%", value);
    }

    private static String pp(double value) {
        return String.format(Locale.KOREA, "%.1f%%p", value);
    }

    private static String won(long amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }
}
