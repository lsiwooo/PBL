package com.moa.pbl;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;
import com.moa.pbl.data.ScriptEntry;
import com.moa.pbl.data.SupabaseRepository;
import com.moa.pbl.data.TranscriptionRepository;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends ComponentActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SupabaseRepository supabase;
    private TranscriptionRepository transcription;
    private ActivityResultLauncher<String> microphonePermission;
    private LinearLayout root;
    private EditText emailField;
    private EditText passwordField;
    private EditText scriptField;
    private TextView status;
    private TextView records;
    private Button recordButton;
    private File recordingFile;
    private android.media.MediaRecorder recorder;
    private boolean recording;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable recordingLimit = this::stopRecording;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supabase = new SupabaseRepository(this);
        transcription = new TranscriptionRepository();
        microphonePermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startRecording(); else showMessage("음성 녹음을 위해 마이크 권한이 필요합니다.");
        });
        if (supabase.isConfigured() && supabase.hasSession()) showJournal(); else showLogin();
    }

    private void showLogin() {
        buildRoot();
        addText("모아", 32, 0xff42644b, true);
        addText("음성 가계부에 로그인하세요", 16, 0xff526052, false);
        emailField = addInput("이메일", false);
        passwordField = addInput("비밀번호", true);
        addButton("로그인", view -> authenticate(false));
        addButton("회원가입", view -> authenticate(true));
        status = addText("", 13, 0xffa23f35, false);
        if (!supabase.isConfigured()) status.setText("설정 필요: mobile/local.properties에 Supabase URL과 Anon Key를 입력하세요.");
    }

    private void authenticate(boolean signup) {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();
        if (email.isEmpty() || password.length() < 6) { status.setText("이메일과 6자 이상의 비밀번호를 입력하세요."); return; }
        setBusy(true);
        io.execute(() -> {
            try {
                supabase.authenticate(email, password, signup);
                runOnUiThread(() -> { setBusy(false); showJournal(); });
            } catch (Exception error) {
                runOnUiThread(() -> { setBusy(false); status.setText(error.getMessage()); });
            }
        });
    }

    private void showJournal() {
        buildRoot();
        LinearLayout header = row();
        TextView title = new TextView(this); title.setText("모아 음성 가계부"); title.setTextSize(24); title.setTextColor(0xff35533d); title.setTypeface(null, 1); title.setPadding(0, 12, 0, 12);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button logout = new Button(this); logout.setText("로그아웃"); logout.setOnClickListener(v -> {
            supabase.logout(); showLogin();
        }); header.addView(logout);
        root.addView(header);
        addText("오늘의 소비를 말하면 글로 바꿔 기록합니다.", 14, 0xff727b72, false);
        recordButton = new Button(this); recordButton.setText("● 녹음 시작"); recordButton.setOnClickListener(v -> toggleRecording()); root.addView(recordButton);
        scriptField = addInput("변환된 스크립트를 확인하고 수정하세요", false);
        scriptField.setMinLines(3);
        addButton("스크립트 저장", v -> saveScript());
        status = addText("", 13, 0xff42644b, false);
        addText("저장한 기록", 20, 0xff35533d, true);
        records = addText("불러오는 중…", 14, 0xff454d45, false);
        refreshEntries();
    }

    private void toggleRecording() {
        if (recording) { stopRecording(); return; }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO); return;
        }
        startRecording();
    }

    private void startRecording() {
        try {
            recordingFile = File.createTempFile("moa-voice-", ".m4a", getCacheDir());
            recorder = new android.media.MediaRecorder();
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(recordingFile.getAbsolutePath());
            recorder.prepare(); recorder.start(); recording = true;
            mainHandler.postDelayed(recordingLimit, 120_000L);
            recordButton.setText("■ 녹음 종료"); status.setText("녹음 중… 최대 2분까지 가능합니다.");
        } catch (Exception error) { releaseRecorder(); showMessage("녹음을 시작하지 못했습니다. 마이크 권한을 확인하세요."); }
    }

    private void stopRecording() {
        recording = false;
        mainHandler.removeCallbacks(recordingLimit);
        recordButton.setEnabled(false); recordButton.setText("음성 변환 중…");
        try { recorder.stop(); } catch (RuntimeException error) { releaseRecorder(); recordButton.setEnabled(true); recordButton.setText("● 녹음 시작"); showMessage("녹음이 너무 짧습니다. 다시 시도하세요."); return; }
        releaseRecorder();
        File audio = recordingFile;
        io.execute(() -> {
            try {
                String text = transcription.transcribe(audio);
                runOnUiThread(() -> { scriptField.setText(text); status.setText("음성 변환이 완료됐습니다. 내용을 확인한 뒤 저장하세요."); });
            } catch (Exception error) { runOnUiThread(() -> status.setText(error.getMessage())); }
            finally { if (audio != null) audio.delete(); recordingFile = null; runOnUiThread(() -> { recordButton.setEnabled(true); recordButton.setText("● 녹음 시작"); }); }
        });
    }

    private void saveScript() {
        String text = scriptField.getText().toString().trim();
        if (text.isEmpty()) { status.setText("저장할 스크립트를 입력하세요."); return; }
        status.setText("저장 중…");
        io.execute(() -> {
            try { supabase.insertEntry(text); runOnUiThread(() -> { scriptField.setText(""); status.setText("Supabase에 저장했습니다."); refreshEntries(); }); }
            catch (Exception error) { runOnUiThread(() -> status.setText(error.getMessage())); }
        });
    }

    private void refreshEntries() {
        io.execute(() -> {
            try {
                List<ScriptEntry> entries = supabase.fetchEntries();
                runOnUiThread(() -> {
                    StringBuilder text = new StringBuilder();
                    for (ScriptEntry entry : entries) text.append("• ").append(entry.createdAt()).append("\n  ").append(entry.transcript()).append("\n\n");
                    records.setText(text.length() == 0 ? "아직 저장된 기록이 없습니다." : text.toString());
                });
            } catch (Exception error) { runOnUiThread(() -> records.setText(error.getMessage())); }
        });
    }

    private void releaseRecorder() {
        if (recorder != null) { recorder.release(); recorder = null; }
    }

    private void buildRoot() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28, 28, 28, 28); root.setBackgroundColor(0xfff5f7f3);
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
    }

    private LinearLayout row() { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(android.view.Gravity.CENTER_VERTICAL); root.addView(row); return row; }
    private TextView addText(String value, int size, int color, boolean bold) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); if (bold) view.setTypeface(null, 1); view.setPadding(0, 12, 0, 12); root.addView(view); return view; }
    private EditText addInput(String hint, boolean password) { EditText input = new EditText(this); input.setSingleLine(!hint.startsWith("변환")); input.setHint(hint); if (password) input.setInputType(129); else if (hint.equals("이메일")) input.setInputType(33); root.addView(input, new LinearLayout.LayoutParams(-1, -2)); return input; }
    private void addButton(String label, View.OnClickListener action) { Button button = new Button(this); button.setText(label); button.setOnClickListener(action); root.addView(button, new LinearLayout.LayoutParams(-1, -2)); }
    private void showMessage(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void setBusy(boolean busy) { if (status != null) status.setText(busy ? "처리 중…" : ""); }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacks(recordingLimit);
        if (recordingFile != null) recordingFile.delete();
        io.shutdownNow(); releaseRecorder(); super.onDestroy();
    }
}
