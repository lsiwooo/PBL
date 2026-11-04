package com.moa.pbl.analysis;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.time.LocalDate;

/** Reads the prior device-only profile once so it can be moved to the signed-in account. */
public final class ProfileStore {

    private static final String PREFS = "peer_profile";

    private ProfileStore() {
    }

    @Nullable
    public static ConsumptionProfile loadForAccount(Context context, String accountEmail) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String owner = prefs.getString("account_email", "");
        if (!owner.isEmpty() && !owner.equalsIgnoreCase(accountEmail)) return null;
        String birthDate = prefs.getString("birth_date", null);
        if (birthDate == null) {
            if (!prefs.getAll().isEmpty()) clear(context);
            return null;
        }
        try {
            if (owner.isEmpty()) prefs.edit().putString("account_email", accountEmail).apply();
            return new ConsumptionProfile(
                    LocalDate.parse(birthDate),
                    prefs.getString("occupation", ""),
                    prefs.getString("school_type", prefs.getString("school_level", "")),
                    prefs.getString("school_name", ""),
                    prefs.getInt("grade", 0),
                    prefs.getString("region", "")
            );
        } catch (RuntimeException invalidProfile) {
            clear(context);
            return null;
        }
    }

    public static void clearForAccount(Context context, String accountEmail) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String owner = prefs.getString("account_email", "");
        if (owner.isEmpty() || owner.equalsIgnoreCase(accountEmail)) {
            prefs.edit().clear().apply();
        }
    }

    private static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
