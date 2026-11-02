package com.moa.pbl.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.moa.pbl.BuildConfig;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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

    public void authenticate(String email, String password, boolean signup) throws Exception {
        requireConfigured();
        String endpoint = signup ? "/auth/v1/signup" : "/auth/v1/token?grant_type=password";
        JSONObject body = new JSONObject().put("email", email).put("password", password);
        JSONObject result = request("POST", endpoint, body, null, null);
        String accessToken = result.optString("access_token");
        if (accessToken.isEmpty()) {
            if (signup) throw new Exception("가입 요청이 접수됐습니다. 이메일 인증을 마친 뒤 로그인해 주세요.");
            throw new Exception("로그인 응답에 세션 정보가 없습니다.");
        }
        preferences.edit().putString("access_token", accessToken)
                .putString("refresh_token", result.optString("refresh_token"))
                .putLong("expires_at", System.currentTimeMillis() / 1000L + result.optLong("expires_in", 3600L)).apply();
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
        JSONObject item = new JSONObject().put("transcript", transcript);
        request("POST", "/rest/v1/voice_entries", item, accessToken(), "return=minimal");
    }

    public List<ScriptEntry> fetchEntries() throws Exception {
        requireSession();
        String query = "?select=transcript,created_at&order=created_at.desc&limit=100";
        JSONObject[] noBody = new JSONObject[0];
        String response = requestRaw("GET", "/rest/v1/voice_entries" + query, null, accessToken(), null);
        JSONArray rows = new JSONArray(response);
        List<ScriptEntry> entries = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            entries.add(new ScriptEntry(row.optString("transcript"), row.optString("created_at")));
        }
        return entries;
    }

    private JSONObject request(String method, String path, JSONObject body, String bearer, String preference) throws Exception {
        String response = requestRaw(method, path, body, bearer, preference);
        return response.isEmpty() ? new JSONObject() : new JSONObject(response);
    }

    private String requestRaw(String method, String path, JSONObject body, String bearer, String preference) throws Exception {
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
            if (code < 200 || code >= 300) throw new Exception(extractMessage(response, code));
            return response;
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

    private String extractMessage(String body, int status) {
        try {
            JSONObject error = new JSONObject(body);
            String message = error.optString("msg", error.optString("message", error.optString("error_description")));
            if (!message.isEmpty()) return message;
        } catch (Exception ignored) { /* Use the HTTP status when the response is not JSON. */ }
        return "Supabase 요청에 실패했습니다 (HTTP " + status + "). 프로젝트 URL, 키, 테이블/RLS 설정을 확인하세요.";
    }

    private String accessToken() throws Exception {
        String token = preferences.getString("access_token", "");
        if (token.isEmpty()) throw new Exception("로그인이 필요합니다.");
        long expiresAt = preferences.getLong("expires_at", Long.MAX_VALUE);
        if (expiresAt <= System.currentTimeMillis() / 1000L + 60L) {
            String refreshToken = preferences.getString("refresh_token", "");
            if (refreshToken.isEmpty()) throw new Exception("로그인 세션이 만료됐습니다. 다시 로그인해 주세요.");
            JSONObject refreshBody = new JSONObject().put("refresh_token", refreshToken);
            JSONObject refreshed = request("POST", "/auth/v1/token?grant_type=refresh_token", refreshBody, null, null);
            String nextToken = refreshed.optString("access_token");
            if (nextToken.isEmpty()) throw new Exception("로그인 세션이 만료됐습니다. 다시 로그인해 주세요.");
            preferences.edit().putString("access_token", nextToken)
                    .putString("refresh_token", refreshed.optString("refresh_token", refreshToken))
                    .putLong("expires_at", System.currentTimeMillis() / 1000L + refreshed.optLong("expires_in", 3600L)).apply();
            token = nextToken;
        }
        return token;
    }

    private void requireSession() throws Exception { requireConfigured(); accessToken(); }
    private void requireConfigured() throws Exception {
        if (!isConfigured()) throw new Exception("Supabase 설정이 없습니다. mobile/local.properties를 확인하세요.");
    }
}
