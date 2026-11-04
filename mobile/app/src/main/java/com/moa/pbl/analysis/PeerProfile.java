package com.moa.pbl.analysis;

/** 또래 비교에 쓰는 사용자 프로필 */
public final class PeerProfile {

    public static final String MALE = "남성";
    public static final String FEMALE = "여성";
    public static final int MIN_AGE = 20;
    public static final int MAX_AGE = 30;
    public static final String[] REGIONS = {
            "서울", "전남광주", "부산", "대구", "인천", "대전", "울산", "세종",
            "경기", "강원", "충북", "충남", "전북", "경북", "경남", "제주"
    };

    public final String name;
    public final String gender;
    public final int age;
    public final String school;
    public final int grade;
    public final String region;

    public PeerProfile(String name, String gender, int age, String school, int grade, String region) {
        this.name = name;
        this.gender = gender;
        this.age = age;
        this.school = school;
        this.grade = grade;
        this.region = region;
    }

    /** 비교 그룹 기준: 같은 성별 + 같은 나이 */
    public boolean isPeerOf(PeerProfile other) {
        return gender.equals(other.gender) && age == other.age;
    }
}
