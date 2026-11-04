package com.moa.pbl.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 또래 비교용 테스트 사용자 데이터 (한 달 수입·카테고리별 지출) */
public final class PeerDataGenerator {

    /**
     * 비교에 쓰는 카테고리. 가계부(LedgerEntry)의 category 값과 이름이 같아야 비교된다.
     * 목록에 없는 카테고리는 "기타"로 합쳐서 비교한다.
     * TODO: backend/app/services/ledger_parser.py의 카테고리 목록과 맞출 것
     */
    public static final String[] CATEGORIES = {
            "식비", "카페", "교통", "쇼핑", "문화·여가", "주거·통신", "교육", "기타"
    };

    /** 카테고리별 월 지출 범위(만 원) */
    private static final double[][] RANGES = {
            {25, 45}, {3, 12}, {3, 10}, {3, 25}, {2, 15}, {5, 40}, {0, 15}, {1, 8}
    };

    /** 나이가 많을수록 커지는 카테고리 (식비, 교통, 주거·통신) */
    private static final boolean[] AGE_SCALED = {
            true, false, true, false, false, true, false, false
    };

    private static final String[] SURNAMES = {"김", "이", "박", "최", "정", "강", "조", "윤", "장", "임"};
    private static final String[] SYLLABLES = {"민", "서", "지", "현", "준", "우", "하", "윤", "도", "예"};
    private static final String[] SCHOOLS = {"한빛대학교", "가람대학교", "누리대학교", "새솔대학교"};

    public static final class PeerRecord {
        public final PeerProfile profile;
        public final long income;
        public final Map<String, Long> expenses;

        PeerRecord(PeerProfile profile, long income, Map<String, Long> expenses) {
            this.profile = profile;
            this.income = income;
            this.expenses = Collections.unmodifiableMap(expenses);
        }

        public long totalExpense() {
            long sum = 0L;
            for (long value : expenses.values()) {
                sum += value;
            }
            return sum;
        }
    }

    private static List<PeerRecord> cache;

    private PeerDataGenerator() {
    }

    /** 앱 전체에서 같은 100명을 공유한다 */
    public static synchronized List<PeerRecord> getDefault() {
        if (cache == null) {
            cache = Collections.unmodifiableList(generate(100, 42L));
        }
        return cache;
    }

    /**
     * 성별은 번갈아, 나이는 20~30세를 순서대로 배정해서
     * 100명 기준 모든 (성별, 나이) 조합에 4~5명씩 들어가게 한다. 같은 seed면 같은 데이터.
     */
    public static List<PeerRecord> generate(int count, long seed) {
        Random random = new Random(seed);
        int ageCount = PeerProfile.MAX_AGE - PeerProfile.MIN_AGE + 1;
        List<PeerRecord> records = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            int age = PeerProfile.MIN_AGE + (i / 2) % ageCount;
            String gender = (i % 2 == 0) ? PeerProfile.MALE : PeerProfile.FEMALE;
            int grade = Math.max(1, Math.min(4, (age - 19) - random.nextInt(2)));
            PeerProfile profile = new PeerProfile(
                    pick(SURNAMES, random) + pick(SYLLABLES, random) + pick(SYLLABLES, random),
                    gender, age, pick(SCHOOLS, random), grade,
                    PeerProfile.REGIONS[random.nextInt(PeerProfile.REGIONS.length)]);

            double ageFactor = 1.0 + (age - 20) * 0.04;
            double allowanceFactor = age >= 25 ? 0.4 : 1.0;
            double partTimeChance = 0.55 + (age - 20) * 0.03;

            long income = man(random, 15, 60, allowanceFactor);
            if (random.nextDouble() < partTimeChance) income += man(random, 40, 120, 1.0);
            if (random.nextDouble() < 0.2) income += man(random, 10, 50, 1.0);

            Map<String, Long> expenses = new LinkedHashMap<>();
            for (int c = 0; c < CATEGORIES.length; c++) {
                double factor = AGE_SCALED[c] ? ageFactor : 1.0;
                expenses.put(CATEGORIES[c], man(random, RANGES[c][0], RANGES[c][1], factor));
            }
            records.add(new PeerRecord(profile, income, expenses));
        }
        return records;
    }

    /** min~max만 원 사이 랜덤 금액 (1,000원 단위로 절사) */
    private static long man(Random random, double min, double max, double factor) {
        double won = (min + random.nextDouble() * (max - min)) * 10_000 * factor;
        return ((long) (won / 1_000)) * 1_000;
    }

    private static String pick(String[] items, Random random) {
        return items[random.nextInt(items.length)];
    }
}
