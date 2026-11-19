package com.moa.pbl.data;

import com.moa.pbl.BuildConfig;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LedgerParserRepository {
    private final String baseUrl = BuildConfig.TRANSCRIPTION_API_BASE_URL.replaceAll("/+$", "");

    public List<ParsedTransaction> parse(String transcript) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/api/v1/ledger/parse").openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(60_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            JSONObject body = new JSONObject().put("transcript", transcript);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            InputStream responseStream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = responseStream == null ? "{}" : readResponse(responseStream);
            JSONObject json = new JSONObject(response);
            if (code < 200 || code >= 300) {
                throw new Exception(json.optString("detail", "가계부 항목 분석에 실패했습니다."));
            }

            JSONArray items = json.optJSONArray("transactions");
            List<ParsedTransaction> parsed = new ArrayList<>();
            if (items == null) return parsed;
            for (int index = 0; index < items.length(); index++) {
                JSONObject item = items.optJSONObject(index);
                if (item == null) continue;
                String name = item.optString("item_name", "").trim();
                long amount = item.optLong("amount", 0L);
                String type = item.optString("transaction_type", "expense");
                String category = item.optString("category", "기타");
                if (!name.isEmpty() && amount > 0) parsed.add(new ParsedTransaction(name, amount, type, category));
            }
            return parsed;
        } finally {
            connection.disconnect();
        }
    }

    private String readResponse(InputStream input) throws Exception {
        try (InputStream response = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = response.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
