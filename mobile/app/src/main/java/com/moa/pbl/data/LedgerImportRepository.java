package com.moa.pbl.data;

import com.moa.pbl.BuildConfig;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** 은행 거래내역 파일을 서버(/api/v1/ledger/import)에 올려 거래 목록으로 바꿔 받습니다. */
public final class LedgerImportRepository {
    public static final class ImportResult {
        public final List<ParsedTransaction> transactions = new ArrayList<>();
        public Long latestBalance;           // 파일 기준 가장 최근 잔액 (없으면 null)
        public long latestBalanceAtMillis;   // 그 잔액의 시각
        public int skippedHolds;             // 가승인으로 제외한 건수
        public int skippedInvalid;           // 읽지 못해 제외한 건수
        public int totalRows;
    }

    private final String baseUrl = BuildConfig.TRANSCRIPTION_API_BASE_URL.replaceAll("/+$", "");

    /** extension: ".csv", ".xlsx" 등 (파일 이름은 한글이 깨질 수 있어 확장자만 보냅니다) */
    public ImportResult upload(String extension, byte[] fileBytes) throws Exception {
        String boundary = "----MoaBoundary" + System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/api/v1/ledger/import").openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(120_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("Accept", "application/json");

            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"statement" + extension + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";
            try (OutputStream output = connection.getOutputStream()) {
                output.write(head.getBytes(StandardCharsets.UTF_8));
                output.write(fileBytes);
                output.write(tail.getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String response = stream == null ? "{}" : readAll(stream);
            JSONObject json = new JSONObject(response);
            if (code < 200 || code >= 300) {
                throw new Exception(json.optString("detail", "거래내역 파일을 읽지 못했어요."));
            }

            ImportResult result = new ImportResult();
            JSONArray items = json.optJSONArray("transactions");
            if (items != null) {
                for (int index = 0; index < items.length(); index++) {
                    JSONObject item = items.optJSONObject(index);
                    if (item == null) continue;
                    String name = item.optString("item_name", "").trim();
                    long amount = item.optLong("amount", 0L);
                    if (name.isEmpty() || amount <= 0) continue;
                    result.transactions.add(new ParsedTransaction(
                            name,
                            amount,
                            item.optString("transaction_type", "expense"),
                            item.optString("category", "기타"),
                            item.optString("occurred_on", null)));
                }
            }
            result.latestBalance = json.isNull("latest_balance") ? null : Long.valueOf(json.optLong("latest_balance"));
            result.latestBalanceAtMillis = parseMillis(json.isNull("latest_balance_at") ? null : json.optString("latest_balance_at"));
            result.skippedHolds = json.optInt("skipped_holds", 0);
            result.skippedInvalid = json.optInt("skipped_invalid", 0);
            result.totalRows = json.optInt("total_rows", 0);
            return result;
        } finally {
            connection.disconnect();
        }
    }

    private static long parseMillis(String isoLocalDateTime) {
        if (isoLocalDateTime == null) return System.currentTimeMillis();
        try {
            return LocalDateTime.parse(isoLocalDateTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static String readAll(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
