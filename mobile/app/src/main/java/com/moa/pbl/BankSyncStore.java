package com.moa.pbl;

import android.content.Context;
import android.content.SharedPreferences;
import com.moa.pbl.data.ParsedTransaction;
import com.moa.pbl.data.SupabaseRepository;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 이 폰에만 저장하는 정보: 계좌별 잔고, 저장하지 못한 거래 대기열, 최근 자동 저장 기록.
 * (Supabase 데이터베이스에는 올라가지 않습니다.)
 */
public final class BankSyncStore {
    private BankSyncStore() {}

    private static final String PREFS = "moa_bank_sync";
    private static final String KEY_BALANCES = "balances";
    private static final String KEY_PENDING = "pending";
    private static final String KEY_LOGS = "logs";
    private static final String KEY_IMPORT_ACCOUNT = "import_account";
    private static final int MAX_PENDING = 200;
    private static final int MAX_LOGS = 30;

    public static final class Balance {
        public final String account;
        public final long balance;
        public final long asOfMillis;

        Balance(String account, long balance, long asOfMillis) {
            this.account = account;
            this.balance = balance;
            this.asOfMillis = asOfMillis;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- 변경 알림
    /** 알림·파일로 거래가 저장될 때(자동 저장 기록이 바뀔 때) 호출받고 싶은 화면에서 등록합니다. */
    public static void registerChangeListener(Context context, SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener);
    }

    public static void unregisterChangeListener(Context context, SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener);
    }

    public static boolean isLogKey(String key) {
        return KEY_LOGS.equals(key);
    }

    // ---------------------------------------------------------------- 잔고
    /** 더 최근 시각의 잔액만 반영합니다. */
    public static synchronized void updateBalance(Context context, String account, Long balance, long asOfMillis) {
        if (account == null || account.isEmpty() || balance == null) return;
        try {
            JSONObject all = new JSONObject(prefs(context).getString(KEY_BALANCES, "{}"));
            JSONObject current = all.optJSONObject(account);
            if (current != null && current.optLong("asOf", 0L) > asOfMillis) return;
            all.put(account, new JSONObject().put("balance", balance.longValue()).put("asOf", asOfMillis));
            prefs(context).edit().putString(KEY_BALANCES, all.toString()).apply();
        } catch (JSONException ignored) {
            // 잔고 저장 실패는 거래 저장에 영향을 주지 않습니다.
        }
    }

    public static synchronized List<Balance> getBalances(Context context) {
        List<Balance> list = new ArrayList<>();
        try {
            JSONObject all = new JSONObject(prefs(context).getString(KEY_BALANCES, "{}"));
            Iterator<String> keys = all.keys();
            while (keys.hasNext()) {
                String account = keys.next();
                JSONObject item = all.optJSONObject(account);
                if (item != null) list.add(new Balance(account, item.optLong("balance"), item.optLong("asOf")));
            }
        } catch (JSONException ignored) {
            // 비어 있는 목록을 돌려줍니다.
        }
        Collections.sort(list, new Comparator<Balance>() {
            @Override public int compare(Balance a, Balance b) { return a.account.compareTo(b.account); }
        });
        return list;
    }

    // ---------------------------------------------------------------- 대기열 (로그인 전이거나 네트워크 오류로 저장하지 못한 거래)
    public static synchronized void addPending(Context context, BankEvent event) {
        List<BankEvent> pending = loadPending(context);
        pending.add(event);
        while (pending.size() > MAX_PENDING) pending.remove(0);
        savePending(context, pending);
    }

    public static synchronized int pendingCount(Context context) {
        return loadPending(context).size();
    }

    /** 대기 중인 거래를 하나씩 저장합니다. 실패하면 거기서 멈추고 나머지는 대기열에 남깁니다. */
    public static synchronized int flushPending(Context context, SupabaseRepository repository) {
        List<BankEvent> pending = loadPending(context);
        if (pending.isEmpty()) return 0;
        List<BankEvent> remaining = new ArrayList<>();
        int saved = 0;
        boolean failed = false;
        for (BankEvent event : pending) {
            if (failed) {
                remaining.add(event);
                continue;
            }
            try {
                repository.insertLedgerEntries(Collections.singletonList(event.transaction));
                updateBalance(context, event.accountName, event.balanceAfter, event.occurredAtMillis);
                saved++;
            } catch (Exception error) {
                failed = true;
                remaining.add(event);
            }
        }
        savePending(context, remaining);
        return saved;
    }

    private static List<BankEvent> loadPending(Context context) {
        List<BankEvent> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY_PENDING, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                ParsedTransaction transaction = new ParsedTransaction(
                        o.getString("name"), o.getLong("amount"), o.getString("type"),
                        o.getString("category"), o.isNull("on") ? null : o.getString("on"));
                Long balance = o.isNull("balance") ? null : Long.valueOf(o.getLong("balance"));
                list.add(new BankEvent(transaction, o.optString("account", ""), balance, o.optLong("at")));
            }
        } catch (JSONException ignored) {
            // 읽을 수 없는 대기열은 비어 있는 것으로 봅니다.
        }
        return list;
    }

    private static void savePending(Context context, List<BankEvent> events) {
        JSONArray array = new JSONArray();
        try {
            for (BankEvent event : events) {
                ParsedTransaction t = event.transaction;
                array.put(new JSONObject()
                        .put("name", t.itemName())
                        .put("amount", t.amount())
                        .put("type", t.transactionType())
                        .put("category", t.category())
                        .put("on", t.occurredOn() == null ? JSONObject.NULL : t.occurredOn())
                        .put("account", event.accountName)
                        .put("balance", event.balanceAfter == null ? JSONObject.NULL : event.balanceAfter)
                        .put("at", event.occurredAtMillis));
            }
        } catch (JSONException ignored) {
            // 만들지 못한 항목은 건너뜁니다.
        }
        prefs(context).edit().putString(KEY_PENDING, array.toString()).apply();
    }

    // ---------------------------------------------------------------- 최근 자동 저장 기록
    public static synchronized void appendLog(Context context, String message) {
        List<String> logs = getLogs(context);
        String time = new SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(new Date());
        logs.add(0, time + "  " + message);
        while (logs.size() > MAX_LOGS) logs.remove(logs.size() - 1);
        JSONArray array = new JSONArray();
        for (String line : logs) array.put(line);
        prefs(context).edit().putString(KEY_LOGS, array.toString()).apply();
    }

    public static synchronized List<String> getLogs(Context context) {
        List<String> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY_LOGS, "[]"));
            for (int i = 0; i < array.length(); i++) list.add(array.getString(i));
        } catch (JSONException ignored) {
            // 빈 목록
        }
        return list;
    }

    // ---------------------------------------------------------------- 파일 가져오기 때 입력한 계좌 이름
    public static String getImportAccount(Context context) {
        return prefs(context).getString(KEY_IMPORT_ACCOUNT, "내 계좌");
    }

    public static void setImportAccount(Context context, String account) {
        prefs(context).edit().putString(KEY_IMPORT_ACCOUNT, account).apply();
    }
}
