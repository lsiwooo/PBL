package com.moa.pbl.analysis;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * 프로필을 기기 안에만 저장한다 (Supabase 스키마는 건드리지 않음).
 * 추후 Supabase에 저장하기로 팀이 합의하면 이 클래스만 바꾸면 된다.
 */
public final class ProfileStore {

    private static final String PREFS = "peer_profile";

    private ProfileStore() {
    }

    @Nullable
    public static PeerProfile load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String name = prefs.getString("name", null);
        if (name == null) {
            return null;
        }
        return new PeerProfile(
                name,
                prefs.getString("gender", PeerProfile.MALE),
                prefs.getInt("age", PeerProfile.MIN_AGE),
                prefs.getString("school", ""),
                prefs.getInt("grade", 1),
                prefs.getString("region", PeerProfile.REGIONS[0])
        );
    }

    public static void save(Context context, PeerProfile profile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("name", profile.name)
                .putString("gender", profile.gender)
                .putInt("age", profile.age)
                .putString("school", profile.school)
                .putInt("grade", profile.grade)
                .putString("region", profile.region)
                .apply();
    }
}
