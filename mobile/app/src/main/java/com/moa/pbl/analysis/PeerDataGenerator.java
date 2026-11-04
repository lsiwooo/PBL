package com.moa.pbl.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 또래 비교용 테스트 사용자 데이터 (나이·직업·지역, 한 달 수입·카테고리별 지출) */
public final class PeerDataGenerator {

    /** 테스트 사용자 수. 늘리면 그룹별 비교 인원이 늘어난다 (직업 6종 × 나이 18종 기준 300명이면 그룹당 약 2~3명, 5살 나이대로 묶으면 약 12명) */
    public static final int PEER_COUNT = 300;

    public static final int MIN_AGE = 18;
    public static final int MAX_AGE = 35;

    /**
     * 비교에 쓰는 지출 카테고리.
     * backend/app/services/ledger_parser.py의 CATEGORIES와 이름을 똑같이 맞췄다.
     * (급여·용돈은 수입 카테고리라 지출 비교에서 제외. 목록에 없는 값은 "기타"로 합쳐서 비교한다.)
     */
    public static final String[] CATEGORIES = {
            "식비", "카페", "교통", "쇼핑", "생활", "의료", "문화", "교육", "기타"
    };

    /** 카테고리별 월 지출 범위(만 원). CATEGORIES와 순서가 같다. */
    private static final double[][] RANGES = {
            {25, 45}, {3, 12}, {3, 10}, {3, 25}, {5, 40}, {0, 8}, {2, 15}, {0, 15}, {1, 8}
    };

    /** 나이가 많을수록 커지는 카테고리 (식비, 교통, 생활) */
    private static final boolean[] AGE_SCALED = {
            true, false, true, false, true, false, false, false, false
    };

    public static final class PeerRecord {
        public final int age;
        public final String occupation;
        public final String region;
        public final long income;
        public final Map<String, Long> expenses;

        PeerRecord(int age, String occupation, String region, long income, Map<String, Long> expenses) {
            this.age = age;
            this.occupation = occupation;
            this.region = region;
            this.income = income;
            this.expenses = Collections.unmodifiableMap(expenses);
        }

        public long totalExpense() {
            long sum = 0L;
            for (long value : expenses.values()) sum += value;
            return sum;
        }
    }

    private static List<PeerRecord> cache;

    private PeerDataGenerator() {
    }

    /** 앱 전체에서 같은 테스트 사용자들을 공유한다 */
    public static synchronized List<PeerRecord> getDefault() {
        if (cache == null) cache = Collections.unmodifiableList(generate(PEER_COUNT, 42L));
        return cache;
    }

    /**
     * 직업은 순서대로, 나이는 18~35세를 순서대로 배정해서 모든 (직업, 나이) 조합에 고르게 들어가게 한다.
     * 같은 seed면 항상 같은 데이터가 나온다.
     */
    public static List<PeerRecord> generate(int count, long seed) {
        Random random = new Random(seed);
        String[] occupations = ConsumptionProfile.OCCUPATIONS;
        int ageCount = MAX_AGE - MIN_AGE + 1;
        List<PeerRecord> records = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String occupation = occupations[i % occupations.length];
            int age = MIN_AGE + (i / occupations.length) % ageCount;
            String region = ConsumptionProfile.REGIONS[random.nextInt(ConsumptionProfile.REGIONS.length)];

            long income = incomeFor(occupation, age, random);
            double ageFactor = 1.0 + (age - 20) * 0.04;
            double occupationFactor = expenseFactor(occupation);

            Map<String, Long> expenses = new LinkedHashMap<>();
            for (int c = 0; c < CATEGORIES.length; c++) {
                double factor = occupationFactor * (AGE_SCALED[c] ? ageFactor : 1.0);
                expenses.put(CATEGORIES[c], man(random, RANGES[c][0], RANGES[c][1], factor));
            }
            records.add(new PeerRecord(age, occupation, region, income, expenses));
        }
        return records;
    }

    /** 직업별 한 달 수입 (가정한 값) */
    private static long incomeFor(String occupation, int age, Random random) {
        switch (occupation) {
            case ConsumptionProfile.STUDENT: {
                long income = man(random, 15, 60, age >= 25 ? 0.4 : 1.0);
                if (random.nextDouble() < 0.55) income += man(random, 40, 120, 1.0);
                if (random.nextDouble() < 0.2) income += man(random, 10, 50, 1.0);
                return income;
            }
            case "직장인": return man(random, 200, 350, 1.0 + Math.max(0, age - 25) * 0.03);
            case "자영업자": return man(random, 150, 450, 1.0);
            case "프리랜서": return man(random, 100, 350, 1.0);
            case "구직 중": return man(random, 0, 60, 1.0);
            default: return man(random, 50, 250, 1.0);
        }
    }

    /** 직업별 지출 규모 배율 (가정한 값) */
    private static double expenseFactor(String occupation) {
        switch (occupation) {
            case ConsumptionProfile.STUDENT: return 1.0;
            case "직장인": return 1.6;
            case "자영업자": return 1.5;
            case "프리랜서": return 1.3;
            case "구직 중": return 0.8;
            default: return 1.1;
        }
    }

    /** min~max만 원 사이 랜덤 금액 (1,000원 단위로 절사) */
    private static long man(Random random, double min, double max, double factor) {
        double won = (min + random.nextDouble() * (max - min)) * 10_000 * factor;
        return ((long) (won / 1_000)) * 1_000;
    }
}
