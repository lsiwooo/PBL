package com.moa.pbl.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.moa.pbl.BuildConfig;
import com.moa.pbl.analysis.ConsumptionProfile;
import java.time.LocalDate;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class SupabaseRepository {
    private static final String PREFS = "moa_supabase_session";
    private final SharedPreferences preferences;
    private final String baseUrl;
    private final String apiKey;

    public SupabaseRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        baseUrl = BuildConfig.SUPABASE_URL.trim().replaceAll("/+$", "");
        apiKey = BuildConfig.SUPABASE_ANON_KEY.trim();
    }

    public boolean isConfigured() { return !baseUrl.isEmpty() && !apiKey.isEmpty(); }
    public boolean hasSession() { return !preferences.getString("access_token", "").isEmpty(); }

    public String createOAuthAuthorizationUrl(String provider, String redirectUri) throws Exception {
        requireConfigured();
        if (!"google".equals(provider) && !"custom:naver".equals(provider))
            throw new Exception("지원하지 않는 로그인 방식입니다.");

        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        preferences.edit().putString("oauth_code_verifier", verifier).apply();

        return baseUrl + "/auth/v1/authorize?provider=" + urlEncode(provider)
                + "&redirect_to=" + urlEncode(redirectUri)
                + "&code_challenge=" + urlEncode(challenge)
                + "&code_challenge_method=s256";
    }

    public void completeOAuthAuthorization(String authorizationCode) throws Exception {
        requireConfigured();
        String verifier = preferences.getString("oauth_code_verifier", "");
        if (verifier.isEmpty()) throw new Exception("로그인 정보가 만료됐어요. 다시 로그인해 주세요.");
        try {
            JSONObject body = new JSONObject()
                    .put("auth_code", authorizationCode)
                    .put("code_verifier", verifier);
            JSONObject result = request("POST", "/auth/v1/token?grant_type=pkce", body, null, null);
            String accessToken = result.optString("access_token");
            String refreshToken = result.optString("refresh_token");
            if (accessToken.isEmpty() || refreshToken.isEmpty())
                throw new Exception("로그인 정보를 받지 못했어요. 다시 시도해 주세요.");
            JSONObject user = result.optJSONObject("user");
            String email = user == null ? "" : user.optString("email", "");
            preferences.edit().putString("access_token", accessToken)
                    .putString("refresh_token", refreshToken)
                    .putString("account_email", email)
                    .putLong("expires_at", System.currentTimeMillis() / 1000L + result.optLong("expires_in", 3600L))
                    .remove("oauth_code_verifier").apply();
        } catch (Exception error) {
            preferences.edit().remove("oauth_code_verifier").apply();
            throw error;
        }
    }

    private String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    public void authenticate(String email, String password, boolean signup) throws Exception {
        requireConfigured();
        String endpoint = signup ? "/auth/v1/signup" : "/auth/v1/token?grant_type=password";
        JSONObject body = new JSONObject().put("email", email).put("password", password);
        JSONObject result = request("POST", endpoint, body, null, null);
        String accessToken = result.optString("access_token");
        if (accessToken.isEmpty()) {
            if (signup) throw new Exception("가입이 완료되지 않았어요. 입력한 정보를 확인해 주세요.");
            throw new Exception("로그인하지 못했어요. 다시 시도해 주세요.");
        }
        preferences.edit().putString("access_token", accessToken)
                .putString("refresh_token", result.optString("refresh_token"))
                .putString("account_email", email)
                .putLong("expires_at", System.currentTimeMillis() / 1000L + result.optLong("expires_in", 3600L)).apply();
    }

    public String currentAccountEmail() {
        return preferences.getString("account_email", "");
    }

    public ConsumptionProfile fetchConsumptionProfile() throws Exception {
        requireSession();
        String path = "/rest/v1/consumption_profiles?select=birth_date,gender,occupation,school_type,school_name,grade,region&limit=1";
        JSONArray rows = new JSONArray(requestRaw("GET", path, null, accessToken(), null));
        if (rows.length() == 0) return null;
        JSONObject row = rows.getJSONObject(0);
        return new ConsumptionProfile(
                LocalDate.parse(row.getString("birth_date")),
                row.isNull("gender") ? "" : row.getString("gender"),
                row.getString("occupation"),
                row.optString("school_type", ""),
                row.optString("school_name", ""),
                row.optInt("grade", 0),
                row.getString("region")
        );
    }

    public void upsertConsumptionProfile(ConsumptionProfile profile) throws Exception {
        requireSession();
        JSONObject row = new JSONObject()
                .put("birth_date", profile.birthDate.toString())
                .put("gender", profile.gender.isEmpty() ? JSONObject.NULL : profile.gender)
                .put("occupation", profile.occupation)
                .put("school_type", profile.schoolType.isEmpty() ? JSONObject.NULL : profile.schoolType)
                .put("school_name", profile.schoolName.isEmpty() ? JSONObject.NULL : profile.schoolName)
                .put("grade", profile.grade == 0 ? JSONObject.NULL : profile.grade)
                .put("region", profile.region);
        requestRaw("POST", "/rest/v1/consumption_profiles?on_conflict=user_id", row, accessToken(),
                "resolution=merge-duplicates,return=minimal");
    }

    public void logout() {
        String token = preferences.getString("access_token", "");
        if (!token.isEmpty() && isConfigured()) {
            try { request("POST", "/auth/v1/logout", new JSONObject(), token, null); }
            catch (Exception ignored) { /* Local sign-out still clears the saved session. */ }
        }
        preferences.edit().clear().apply();
    }

    public void insertEntry(String transcript) throws Exception {
        requireSession();
        JSONObject item = new JSONObject()
                .put("transcript", transcript)
                .put("occurred_on", LocalDate.now().toString());
        request("POST", "/rest/v1/voice_entries", item, accessToken(), "return=minimal");
    }

    public List<ScriptEntry> fetchEntries() throws Exception {
        requireSession();
        String query = "?select=transcript,created_at,occurred_on&order=occurred_on.desc,created_at.desc&limit=100";
        String response = requestRaw("GET", "/rest/v1/voice_entries" + query, null, accessToken(), null);
        JSONArray rows = new JSONArray(response);
        List<ScriptEntry> entries = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            entries.add(new ScriptEntry(row.optString("transcript"), row.optString("created_at"), row.optString("occurred_on")));
        }
        return entries;
    }

    public void insertLedgerEntries(List<ParsedTransaction> transactions) throws Exception {
        requireSession();
        JSONArray items = new JSONArray();
        String today = LocalDate.now().toString();
        for (ParsedTransaction transaction : transactions) {
            items.put(new JSONObject()
                    .put("item_name", transaction.itemName())
                    .put("amount", transaction.amount())
                    .put("transaction_type", transaction.transactionType())
                    .put("category", transaction.category())
                    .put("occurred_on", transaction.occurredOn() == null ? today : transaction.occurredOn()));
        }
        if (items.length() == 0) throw new Exception("저장할 가계부 항목이 없습니다.");
        requestRaw("POST", "/rest/v1/ledger_entries", items, accessToken(), "return=minimal");
    }

    /** 거래 날짜(occurredOn)를 반영하고 200건씩 나눠 저장합니다. */
    public void insertLedgerEntriesBatch(List<ParsedTransaction> transactions) throws Exception {
        requireSession();
        String today = LocalDate.now().toString();
        int total = 0;
        JSONArray batch = new JSONArray();
        for (ParsedTransaction transaction : transactions) {
            // 문자·알림, 거래내역 파일에서 온 거래는 실제 거래 날짜로, 그 외에는 저장하는 날로 기록합니다.
            String occurredOn = transaction.occurredOn() != null ? transaction.occurredOn() : today;
            batch.put(new JSONObject()
                    .put("item_name", transaction.itemName())
                    .put("amount", transaction.amount())
                    .put("transaction_type", transaction.transactionType())
                    .put("category", transaction.category())
                    .put("occurred_on", occurredOn));
            total++;
            if (batch.length() >= 200) { // 거래내역 파일처럼 건수가 많을 때는 나눠서 보냅니다.
                requestRaw("POST", "/rest/v1/ledger_entries", batch, accessToken(), "return=minimal");
                batch = new JSONArray();
            }
        }
        if (total == 0) throw new Exception("저장할 가계부 항목이 없습니다.");
        if (batch.length() > 0) requestRaw("POST", "/rest/v1/ledger_entries", batch, accessToken(), "return=minimal");
    }

    public List<LedgerEntry> fetchLedgerEntries() throws Exception {
        requireSession();
        String query = "?select=item_name,amount,transaction_type,category,occurred_on&order=occurred_on.desc,created_at.desc&limit=200";
        String response = requestRaw("GET", "/rest/v1/ledger_entries" + query, null, accessToken(), null);
        JSONArray rows = new JSONArray(response);
        List<LedgerEntry> entries = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            entries.add(new LedgerEntry(
                    row.optString("item_name"),
                    row.optLong("amount"),
                    row.optString("transaction_type"),
                    row.optString("category", "기타"),
                    row.optString("occurred_on")
            ));
        }
        return entries;
    }

    private JSONObject request(String method, String path, JSONObject body, String bearer, String preference) throws Exception {
        String response = requestRaw(method, path, body, bearer, preference);
        return response.isEmpty() ? new JSONObject() : new JSONObject(response);
    }

    private String requestRaw(String method, String path, Object body, String bearer, String preference) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            connection.setRequestProperty("apikey", apiKey);
            connection.setRequestProperty("Accept", "application/json");
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
            if (preference != null) connection.setRequestProperty("Prefer", preference);
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String response = stream == null ? "" : readAll(stream);
            if (code < 200 || code >= 300) throw new Exception(extractMessage(response, path));
            return response;
        } catch (IOException connectionError) {
            throw new Exception("인터넷 연결을 확인하고 다시 시도해 주세요.");
        } finally { connection.disconnect(); }
    }

    private String readAll(InputStream stream) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) content.append(line);
        }
        return content.toString();
    }

    private String extractMessage(String body, String path) {
        String message = "";
        try {
            JSONObject error = new JSONObject(body);
            message = error.optString("msg", error.optString("message", error.optString("error_description"))).toLowerCase(Locale.ROOT);
        } catch (Exception ignored) { /* Use the HTTP status when the response is not JSON. */ }
        if (path.startsWith("/rest/v1/consumption_profiles")) return "프로필을 저장하거나 불러오지 못했어요. 다시 시도해 주세요.";
        if (path.startsWith("/auth/v1/")) {
            if (message.contains("invalid login credentials")) return "이메일 또는 비밀번호를 확인해 주세요.";
            if (message.contains("invalid email")) return "이메일 주소를 확인해 주세요.";
            if (message.contains("already registered") || message.contains("user already exists")) return "이미 가입된 이메일입니다. 로그인해 주세요.";
            if (message.contains("rate limit")) return "요청이 많습니다. 잠시 후 다시 시도해 주세요.";
            if (message.contains("password") && (message.contains("length") || message.contains("short"))) return "비밀번호를 확인해 주세요.";
            return "회원가입 또는 로그인에 실패했어요. 입력한 정보를 확인해 주세요.";
        }
        return "기록을 불러오거나 저장하지 못했어요. 잠시 후 다시 시도해 주세요.";
    }

    private String accessToken() throws Exception {
        String token = preferences.getString("access_token", "");
        if (token.isEmpty()) throw new Exception("로그인이 필요합니다.");
        long expiresAt = preferences.getLong("expires_at", Long.MAX_VALUE);
        if (expiresAt <= System.currentTimeMillis() / 1000L + 60L) {
            String refreshToken = preferences.getString("refresh_token", "");
            if (refreshToken.isEmpty()) throw new Exception("로그인이 만료됐어요. 다시 로그인해 주세요.");
            JSONObject refreshBody = new JSONObject().put("refresh_token", refreshToken);
            JSONObject refreshed = request("POST", "/auth/v1/token?grant_type=refresh_token", refreshBody, null, null);
            String nextToken = refreshed.optString("access_token");
            if (nextToken.isEmpty()) throw new Exception("로그인이 만료됐어요. 다시 로그인해 주세요.");
            preferences.edit().putString("access_token", nextToken)
                    .putString("refresh_token", refreshed.optString("refresh_token", refreshToken))
                    .putLong("expires_at", System.currentTimeMillis() / 1000L + refreshed.optLong("expires_in", 3600L)).apply();
            token = nextToken;
        }
        return token;
    }

    private void requireSession() throws Exception { requireConfigured(); accessToken(); }
    private void requireConfigured() throws Exception {
        if (!isConfigured()) throw new Exception("서비스에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.");
    }
}
