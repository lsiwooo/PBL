package com.moa.pbl.data;

import com.moa.pbl.BuildConfig;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONObject;

public final class TranscriptionRepository {
    private static final long MAX_AUDIO_BYTES = 25L * 1024 * 1024;
    private final String baseUrl;

    public TranscriptionRepository() {
        baseUrl = BuildConfig.TRANSCRIPTION_API_BASE_URL.replaceAll("/+$", "");
    }

    public String transcribe(File audio) throws Exception {
        if (audio == null || !audio.isFile() || audio.length() == 0) {
            throw new Exception("녹음 파일이 비어 있습니다. 다시 녹음해 주세요.");
        }
        if (audio.length() > MAX_AUDIO_BYTES) {
            throw new Exception("음성 파일은 25MB 이하여야 합니다.");
        }

        String boundary = "MoaBoundary" + UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection connection = (HttpURLConnection) new URL(
                baseUrl + "/api/v1/transcriptions"
        ).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(120_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("Accept", "application/json");
            try (OutputStream output = connection.getOutputStream();
                 FileInputStream input = new FileInputStream(audio)) {
                write(output, "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"recording.m4a\"\r\n"
                        + "Content-Type: audio/mp4\r\n\r\n");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                write(output, "\r\n--" + boundary + "--\r\n");
            }

            int code = connection.getResponseCode();
            InputStream responseStream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String response = responseStream == null ? "{}" : readResponse(responseStream);
            JSONObject json = new JSONObject(response);
            if (code < 200 || code >= 300) {
                String detail = json.optString("detail", "백엔드 음성 변환 요청에 실패했습니다.");
                throw new Exception("서버 오류 (" + code + "): " + detail);
            }

            if (!json.has("text")) {
                throw new Exception("서버 응답에 전사 결과(text)가 없습니다: " + response);
            }
            String transcript = json.optString("text", "").trim();
            if (transcript.isEmpty()) {
                throw new Exception("서버가 빈 전사 결과를 반환했습니다. 백엔드 코드를 다시 시작해 주세요.");
            }
            return transcript;
        } finally {
            connection.disconnect();
        }
    }

    private void write(OutputStream output, String value) throws Exception {
        output.write(value.getBytes(StandardCharsets.UTF_8));
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
