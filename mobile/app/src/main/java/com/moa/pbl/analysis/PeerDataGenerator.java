package com.moa.pbl.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 또래 비교용 테스트 사용자 데이터 (성별·지역·나이, 한 달 수입·카테고리별 지출) */
public final class PeerDataGenerator {

    /**
     * 테스트 사용자 수. 성별 2 × 지역 16 × 나이 18 조합에 고르게 배치한다.
     * 1,000명이면 같은 성별·지역·5살 연령대 그룹에 평균 8~9명이 들어간다. 늘리면 그룹 인원이 늘어난다.
     */
    public static final int PEER_COUNT = 1000;

    public static final int MIN_AGE = 18;
    public static final int MAX_AGE = 35;

    /** 카테고리별 월 지출 범위(만 원). CategoryPalette.EXPENSE_CATEGORIES와 순서가 같다. */
    private static final double[][] RANGES = {
            {25, 45}, {3, 12}, {3, 10}, {3, 25}, {5, 40}, {0, 8}, {2, 15}, {0, 15}, {1, 8}
    };

    /** 나이가 많을수록 커지는 카테고리 (식비, 교통, 생활) */
    private static final boolean[] AGE_SCALED = {
            true, false, true, false, true, false, false, false, false
    };

    public static final class PeerRecord {
        public final String gender;
        public final String region;
        public final int age;
        public final long income;
        public final Map<String, Long> expenses;

        PeerRecord(String gender, String region, int age, long income, Map<String, Long> expenses) {
            this.gender = gender;
            this.region = region;
            this.age = age;
            this.income = income;
            this.expenses = Collections.unmodifiableMap(expenses);
        }
    }

    private static List<PeerRecord> cache;

    private PeerDataGenerator() {
    }

    public static synchronized List<PeerRecord> getDefault() {
        if (cache == null) cache = Collections.unmodifiableList(generate(PEER_COUNT, 42L));
        return cache;
    }

    /** 같은 seed면 항상 같은 데이터가 나온다. 성별에 따라 소비 금액을 다르게 만들지 않는다. */
    public static List<PeerRecord> generate(int count, long seed) {
        Random random = new Random(seed);
        String[] regions = ConsumptionProfile.REGIONS;
        int ageCount = MAX_AGE - MIN_AGE + 1;
        List<PeerRecord> records = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String gender = (i % 2 == 0) ? GenderPreference.MALE : GenderPreference.FEMALE;
            String region = regions[(i / 2) % regions.length];
            int age = MIN_AGE + (i / (2 * regions.length)) % ageCount;

            // 나이가 많을수록 직장인일 확률이 높다고 가정
            boolean worker = random.nextDouble() < Math.max(0.0, Math.min(0.9, (age - 21) * 0.15));
            long income;
            if (worker) {
                income = man(random, 180, 330, 1.0 + Math.max(0, age - 25) * 0.03);
            } else {
                income = man(random, 15, 60, 1.0);
                if (random.nextDouble() < 0.6) income += man(random, 40, 120, 1.0);
            }

            double regionFactor = regionFactor(region);
            double jobFactor = worker ? 1.4 : 1.0;
            double ageFactor = 1.0 + (age - 20) * 0.04;

            Map<String, Long> expenses = new LinkedHashMap<>();
            for (int c = 0; c < CategoryPalette.EXPENSE_CATEGORIES.length; c++) {
                double factor = regionFactor * jobFactor * (AGE_SCALED[c] ? ageFactor : 1.0);
                expenses.put(CategoryPalette.EXPENSE_CATEGORIES[c], man(random, RANGES[c][0], RANGES[c][1], factor));
            }
            records.add(new PeerRecord(gender, region, age, income, expenses));
        }
        return records;
    }

    /** 지역별 물가 차이 (가정한 값) */
    private static double regionFactor(String region) {
        if (region.contains("서울")) return 1.15;
        if (region.contains("경기") || region.contains("세종")) return 1.05;
        if (region.contains("광역시") || region.contains("통합특별시")) return 1.0;
        return 0.92;
    }

    /** min~max만 원 사이 랜덤 금액 (1,000원 단위로 절사) */
    private static long man(Random random, double min, double max, double factor) {
        double won = (min + random.nextDouble() * (max - min)) * 10_000 * factor;
        return ((long) (won / 1_000)) * 1_000;
    }
}
