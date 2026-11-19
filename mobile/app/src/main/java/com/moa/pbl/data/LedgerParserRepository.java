package com.moa.pbl.data;

import android.content.Context;
import android.net.Uri;
import com.moa.pbl.BuildConfig;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

            return readTransactions(connection);
        } finally {
            connection.disconnect();
        }
    }

    public List<ParsedTransaction> parseReceipt(Context context, Uri imageUri) throws Exception {
        String mimeType = context.getContentResolver().getType(imageUri);
        if (mimeType == null || !(mimeType.equals("image/jpeg") || mimeType.equals("image/png")
                || mimeType.equals("image/webp") || mimeType.equals("image/heic") || mimeType.equals("image/heif")))
            throw new Exception("JPG, PNG, WEBP 또는 HEIC 사진을 선택해 주세요.");
        byte[] imageBytes;
        try (InputStream input = context.getContentResolver().openInputStream(imageUri)) {
            if (input == null) throw new Exception("사진을 열지 못했어요. 다시 선택해 주세요.");
            imageBytes = readBounded(input, 10 * 1024 * 1024);
        }

        String boundary = "----LedgerReceipt" + UUID.randomUUID();
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/api/v1/ledger/parse-image").openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(60_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("Accept", "application/json");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"receipt" + extensionFor(mimeType) + "\"\r\n"
                        + "Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(imageBytes);
                output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            return readTransactions(connection);
        } finally {
            connection.disconnect();
        }
    }

    private List<ParsedTransaction> readTransactions(HttpURLConnection connection) throws Exception {
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
            String occurredOn = item.optString("occurred_on", null);
            if (occurredOn != null) {
                try { occurredOn = LocalDate.parse(occurredOn).toString(); }
                catch (java.time.format.DateTimeParseException error) { occurredOn = null; }
            }
            if (!name.isEmpty() && amount > 0) parsed.add(new ParsedTransaction(name, amount, type, category, occurredOn));
        }
        return parsed;
    }

    private byte[] readBounded(InputStream input, int maxBytes) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > maxBytes) throw new Exception("사진은 10MB 이하로 선택해 주세요.");
                output.write(buffer, 0, count);
            }
            if (output.size() == 0) throw new Exception("사진이 비어 있어요. 다시 선택해 주세요.");
            return output.toByteArray();
        }
    }

    private String extensionFor(String mimeType) {
        if ("image/png".equals(mimeType)) return ".png";
        if ("image/webp".equals(mimeType)) return ".webp";
        if ("image/heic".equals(mimeType)) return ".heic";
        if ("image/heif".equals(mimeType)) return ".heif";
        return ".jpg";
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
