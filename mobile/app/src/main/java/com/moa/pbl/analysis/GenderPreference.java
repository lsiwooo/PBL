package com.moa.pbl.analysis;

import android.content.Context;
import android.content.SharedPreferences;

/** 기존 기기 저장 성별을 프로필 DB 컬럼으로 한 번 옮길 때 사용합니다. */
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
