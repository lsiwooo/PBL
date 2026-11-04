package com.moa.pbl.analysis;

import java.time.LocalDate;
import java.time.Period;

/** Optional profile used for consumption analysis. */
public final class ConsumptionProfile {
    public static final String STUDENT = "학생";
    public static final String[] OCCUPATIONS = {STUDENT, "직장인", "자영업자", "프리랜서", "구직 중", "기타"};
    public static final String[] SCHOOL_TYPES = {"초등학교", "중학교", "고등학교", "대학교"};
    public static final String[] REGIONS = {"서울특별시", "전남광주통합특별시", "부산광역시", "대구광역시", "인천광역시", "대전광역시", "울산광역시", "세종특별자치시", "경기도", "강원특별자치도", "충청북도", "충청남도", "전북특별자치도", "경상북도", "경상남도", "제주특별자치도"};
    public final LocalDate birthDate;
    public final String occupation;
    public final String schoolType;
    public final String schoolName;
    public final int grade;
    public final String region;

    public ConsumptionProfile(LocalDate birthDate, String occupation, String schoolType, String schoolName, int grade, String region) {
        this.birthDate = birthDate;
        this.occupation = occupation;
        this.schoolType = schoolType;
        this.schoolName = schoolName;
        this.grade = grade;
        this.region = region;
    }

    public int ageOn(LocalDate date) { return Period.between(birthDate, date).getYears(); }
}
