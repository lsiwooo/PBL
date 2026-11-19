package com.moa.pbl.analysis;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 또래 비교용 성별을 계정별로 기기에 저장한다.
 * 프로필(ConsumptionProfile)에 성별 항목이 생기면 그 값을 쓰도록 바꾸고 이 클래스는 지운다.
 */
public final class GenderPreference {

    public static final String MALE = "남성";
    public static final String FEMALE = "여성";

    private static final String PREFS = "analysis_gender";

    private GenderPreference() {
    }

    public static String load(Context context, String account) {
        return prefs(context).getString(key(account), null);
    }

    public static void save(Context context, String account, String gender) {
        prefs(context).edit().putString(key(account), gender).apply();
    }

    public static void clear(Context context, String account) {
        prefs(context).edit().remove(key(account)).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String account) {
        return "gender_" + (account == null ? "default" : account);
    }
}
