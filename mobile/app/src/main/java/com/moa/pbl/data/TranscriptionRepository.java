package com.moa.pbl.data;

import com.moa.pbl.BuildConfig;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONObject;

public final class TranscriptionRepository {
    public String transcribe(File audio) throws Exception {
        if (audio == null || !audio.isFile() || audio.length() == 0) throw new Exception("녹음 파일이 비어 있습니다. 다시 녹음해 주세요.");
        if (audio.length() > 25L * 1024 * 1024) throw new Exception("음성 파일은 25MB 이하여야 합니다.");
        String baseUrl = BuildConfig.TRANSCRIPTION_API_BASE_URL.replaceAll("/+$", "");
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/api/v1/transcriptions").openConnection();
        String boundary = "MoaBoundary-" + UUID.randomUUID();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(90_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            try (OutputStream output = connection.getOutputStream(); InputStream input = new FileInputStream(audio)) {
                output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + audio.getName() + "\"\r\nContent-Type: audio/mp4\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            InputStream responseStream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String response = responseStream == null ? "{}" : readResponse(responseStream);
            JSONObject json = new JSONObject(response);
            if (code < 200 || code >= 300) throw new Exception(json.optString("detail", "음성 변환에 실패했습니다."));
            String text = json.optString("text").trim();
            if (text.isEmpty()) throw new Exception("음성을 인식하지 못했습니다. 다시 녹음해 주세요.");
            return text;
        } finally { connection.disconnect(); }
    }

    private String readResponse(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
