package com.moa.pbl;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.NotificationManagerCompat;
import com.moa.pbl.data.LedgerImportRepository;
import com.moa.pbl.data.LedgerImportRepository.ImportResult;
import com.moa.pbl.data.LedgerImportRepository.PasswordRequiredException;
import com.moa.pbl.data.ParsedTransaction;
import com.moa.pbl.data.SupabaseRepository;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 문자·알림 자동 저장 설정, 은행 거래내역 파일 가져오기, 계좌 잔고(이 폰에만 저장)를 한 화면에서 다룹니다. */
public final class BankSyncActivity extends ComponentActivity {
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    private TextView accessStatus;
    private TextView pendingText;
    private Button flushButton;
    private TextView logText;
    private EditText accountInput;
    private TextView importSummary;
    private Button saveImportButton;
    private TextView balanceText;
    private ImportResult pendingImport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(32));
        scroll.addView(root);
        setContentView(scroll);

        Button back = button("← 돌아가기", v -> finish());
        root.addView(back, wrap());
        root.addView(text("은행 자동 입력", 26, 0xff172642, true), wrap());
        root.addView(text("문자·알림과 거래내역 파일로 가계부를 채워요", 14, 0xff7e8798, false), wrap());

        // ---- 1. 문자·알림 자동 저장
        LinearLayout notificationCard = card(root);
        notificationCard.addView(text("문자·알림으로 자동 저장", 18, 0xff172642, true), wrap());
        notificationCard.addView(text("은행 앱 푸시와 문자 알림에서 입출금 내용을 읽어, 알림이 오는 즉시 가계부에 저장해요. "
                + "입출금과 관련 없는 알림이나 허용하지 않은 앱의 알림은 읽지 않아요.", 13, 0xff454d45, false), wrap());
        accessStatus = text("", 14, 0xff42644b, true);
        notificationCard.addView(accessStatus, wrap());
        notificationCard.addView(button("알림 접근 설정 열기", v -> openNotificationAccessSettings()), wrap());
        pendingText = text("", 13, 0xff454d45, false);
        notificationCard.addView(pendingText, wrap());
        flushButton = button("대기 중인 거래 지금 저장", v -> flushPendingNow());
        notificationCard.addView(flushButton, wrap());
        notificationCard.addView(text("최근 자동 저장 기록", 14, 0xff172642, true), wrap());
        logText = text("", 12, 0xff66758e, false);
        notificationCard.addView(logText, wrap());

        // ---- 2. 거래내역 파일 가져오기
        LinearLayout fileCard = card(root);
        fileCard.addView(text("거래내역 파일 가져오기", 18, 0xff172642, true), wrap());
        fileCard.addView(text("은행 앱에서 내려받은 거래내역 파일(엑셀 .xlsx, CSV, PDF)을 올리면 지난 거래와 현재 잔고를 한 번에 채워요. "
                + "비밀번호가 걸린 파일은 비밀번호를 물어봐요. 카드 가승인(임시 승인)은 수입·지출로 세지 않아요. "
                + "PDF는 AI가 읽기 때문에 결과를 꼭 확인해 주세요.", 13, 0xff454d45, false), wrap());
        accountInput = new EditText(this);
        accountInput.setSingleLine(true);
        accountInput.setHint("계좌 이름 (예: 토스뱅크)");
        accountInput.setText(BankSyncStore.getImportAccount(this));
        accountInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        accountInput.setBackground(rounded(0xffffffff, dp(14), 0xffdfe5dc));
        fileCard.addView(accountInput, wrap());
        fileCard.addView(button("거래내역 파일 선택", v -> filePicker.launch(new String[]{"*/*"})), wrap());
        importSummary = text("", 13, 0xff454d45, false);
        fileCard.addView(importSummary, wrap());
        saveImportButton = button("", v -> saveImport());
        saveImportButton.setVisibility(View.GONE);
        fileCard.addView(saveImportButton, wrap());

        // ---- 3. 잔고
        LinearLayout balanceCard = card(root);
        balanceCard.addView(text("내 계좌 잔고", 18, 0xff172642, true), wrap());
        balanceCard.addView(text("파일과 알림에서 읽은 가장 최근 잔액이에요. 이 폰에만 저장되고 서버에는 올라가지 않아요.", 13, 0xff454d45, false), wrap());
        balanceText = text("", 15, 0xff172642, false);
        balanceCard.addView(balanceText, wrap());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        // 로그인된 상태라면 밀려 있던 거래를 자동으로 저장해 봅니다.
        final SupabaseRepository repository = new SupabaseRepository(this);
        if (repository.isConfigured() && repository.hasSession() && BankSyncStore.pendingCount(this) > 0) {
            io.execute(() -> {
                BankSyncStore.flushPending(this, repository);
                runOnUiThread(this::refresh);
            });
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ 화면 갱신
    private void refresh() {
        boolean enabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName());
        accessStatus.setText(enabled
                ? "✅ 알림 읽기가 켜져 있어요"
                : "⚠️ 알림 읽기가 꺼져 있어요. 아래 버튼을 눌러 '자연어 가계부'를 켜 주세요.");

        int pending = BankSyncStore.pendingCount(this);
        pendingText.setText(pending > 0 ? "저장 대기 중인 거래 " + pending + "건 (로그인이 필요했거나 네트워크 오류였어요)" : "저장 대기 중인 거래가 없어요.");
        flushButton.setVisibility(pending > 0 ? View.VISIBLE : View.GONE);

        List<String> logs = BankSyncStore.getLogs(this);
        if (logs.isEmpty()) {
            logText.setText("아직 기록이 없어요.");
        } else {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < Math.min(logs.size(), 8); i++) {
                if (i > 0) builder.append('\n');
                builder.append(logs.get(i));
            }
            logText.setText(builder.toString());
        }

        List<BankSyncStore.Balance> balances = BankSyncStore.getBalances(this);
        if (balances.isEmpty()) {
            balanceText.setText("아직 잔고 정보가 없어요. 거래내역 파일을 가져오거나 입출금 알림이 오면 표시돼요.");
        } else {
            StringBuilder builder = new StringBuilder();
            SimpleDateFormat format = new SimpleDateFormat("M/d HH:mm", Locale.KOREA);
            for (BankSyncStore.Balance balance : balances) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(balance.account).append("   ").append(won(balance.balance))
                        .append("   (").append(format.format(new Date(balance.asOfMillis))).append(" 기준)");
            }
            balanceText.setText(builder.toString());
        }
    }

    private void openNotificationAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "설정 > 알림 > 알림 접근 권한에서 직접 켜 주세요.", Toast.LENGTH_LONG).show();
        }
    }

    private void flushPendingNow() {
        final SupabaseRepository repository = new SupabaseRepository(this);
        if (!repository.isConfigured() || !repository.hasSession()) {
            Toast.makeText(this, "앱에서 먼저 로그인해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        flushButton.setEnabled(false);
        io.execute(() -> {
            int saved = BankSyncStore.flushPending(this, repository);
            runOnUiThread(() -> {
                flushButton.setEnabled(true);
                Toast.makeText(this, saved + "건을 저장했어요.", Toast.LENGTH_SHORT).show();
                refresh();
            });
        });
    }

    // ------------------------------------------------------------------ 파일 가져오기
    private void onFilePicked(Uri uri) {
        if (uri == null) return;
        pendingImport = null;
        saveImportButton.setVisibility(View.GONE);
        importSummary.setText("파일을 읽는 중이에요…");
        io.execute(() -> {
            try {
                String extension = extensionOf(displayName(uri));
                byte[] bytes = readAll(uri);
                uploadFile(extension, bytes, null);
            } catch (Exception error) {
                showImportError(error);
            }
        });
    }

    /** 서버에 올려 분석합니다. 비밀번호가 필요하면 입력창을 띄우고, 입력하면 같은 파일로 다시 올립니다. */
    private void uploadFile(String extension, byte[] bytes, String password) {
        runOnUiThread(() -> importSummary.setText(".pdf".equals(extension)
                ? "PDF를 AI가 읽는 중이에요… 1분 가까이 걸릴 수 있어요."
                : "파일을 분석하는 중이에요… (처음엔 서버 응답에 시간이 걸릴 수 있어요)"));
        io.execute(() -> {
            try {
                ImportResult result = new LedgerImportRepository().upload(extension, bytes, password);
                runOnUiThread(() -> showPreview(result));
            } catch (PasswordRequiredException needPassword) {
                runOnUiThread(() -> askPassword(extension, bytes, needPassword.wrongPassword));
            } catch (Exception error) {
                showImportError(error);
            }
        });
    }

    private void showImportError(Exception error) {
        final String message = error.getMessage() == null ? "알 수 없는 오류" : error.getMessage();
        runOnUiThread(() -> importSummary.setText("파일을 가져오지 못했어요: " + message
                + "\n(서버가 켜져 있는지, 폰과 서버가 같은 네트워크인지도 확인해 주세요.)"));
    }

    /** 비밀번호 입력창. 입력한 비밀번호는 화면과 서버 요청에만 쓰고 어디에도 저장하지 않습니다. */
    private void askPassword(String extension, byte[] bytes, boolean wrongPassword) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("비밀번호");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        String message = wrongPassword
                ? "비밀번호가 맞지 않아요. 다시 입력해 주세요."
                : "비밀번호가 걸린 파일이에요. 비밀번호를 입력하면 파일을 열어 거래내역을 가져와요.\n(비밀번호는 저장하지 않고, 파일을 여는 데에만 한 번 쓰여요.)";
        importSummary.setText("비밀번호를 기다리는 중이에요…");
        new AlertDialog.Builder(this)
                .setTitle("비밀번호가 필요해요")
                .setMessage(message)
                .setView(input)
                .setPositiveButton("열기", (dialog, which) -> {
                    String password = input.getText() == null ? "" : input.getText().toString();
                    if (password.isEmpty()) {
                        askPassword(extension, bytes, false);
                    } else {
                        uploadFile(extension, bytes, password);
                    }
                })
                .setNegativeButton("취소", (dialog, which) -> importSummary.setText("가져오기를 취소했어요."))
                .show();
    }

    private void showPreview(ImportResult result) {
        if (result.transactions.isEmpty()) {
            importSummary.setText("저장할 거래가 없어요. (가승인 " + result.skippedHolds + "건, 읽지 못한 줄 " + result.skippedInvalid + "건)");
            return;
        }
        pendingImport = result;

        int incomeCount = 0;
        int expenseCount = 0;
        long incomeSum = 0L;
        long expenseSum = 0L;
        String first = null;
        String last = null;
        for (ParsedTransaction transaction : result.transactions) {
            if ("income".equals(transaction.transactionType())) {
                incomeCount++;
                incomeSum += transaction.amount();
            } else {
                expenseCount++;
                expenseSum += transaction.amount();
            }
            String day = transaction.occurredOn();
            if (day != null) {
                if (first == null || day.compareTo(first) < 0) first = day;
                if (last == null || day.compareTo(last) > 0) last = day;
            }
        }

        StringBuilder builder = new StringBuilder();
        if (result.notice != null) builder.append("⚠️ ").append(result.notice).append("\n\n");
        builder.append("거래 ").append(result.transactions.size()).append("건")
                .append("  (수입 ").append(incomeCount).append("건 ").append(won(incomeSum))
                .append(" / 지출 ").append(expenseCount).append("건 ").append(won(expenseSum)).append(")");
        if (first != null) builder.append("\n기간: ").append(first).append(" ~ ").append(last);
        if (result.skippedHolds > 0) builder.append("\n가승인(임시 승인) ").append(result.skippedHolds).append("건은 제외했어요.");
        if (result.skippedInvalid > 0) builder.append("\n읽지 못한 줄 ").append(result.skippedInvalid).append("건은 제외했어요.");
        if (result.latestBalance != null) builder.append("\n파일 기준 최근 잔고: ").append(won(result.latestBalance));
        builder.append("\n\n미리보기 (처음 5건)");
        for (int i = 0; i < Math.min(5, result.transactions.size()); i++) {
            ParsedTransaction t = result.transactions.get(i);
            String sign = "income".equals(t.transactionType()) ? "+" : "-";
            builder.append("\n· ").append(t.occurredOn()).append("  ").append(t.itemName())
                    .append("  ").append(sign).append(won(t.amount())).append("  [").append(t.category()).append("]");
        }
        builder.append("\n\n같은 파일을 두 번 저장하면 거래가 중복돼요. 저장 전에 한 번 더 확인해 주세요.");
        importSummary.setText(builder.toString());

        saveImportButton.setText("확인하고 " + result.transactions.size() + "건 저장");
        saveImportButton.setEnabled(true);
        saveImportButton.setVisibility(View.VISIBLE);
    }

    private void saveImport() {
        final ImportResult result = pendingImport;
        if (result == null) return;
        final SupabaseRepository repository = new SupabaseRepository(this);
        if (!repository.isConfigured() || !repository.hasSession()) {
            Toast.makeText(this, "앱에서 먼저 로그인해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        String typed = accountInput.getText() == null ? "" : accountInput.getText().toString().trim();
        final String account = typed.isEmpty() ? "내 계좌" : typed;

        saveImportButton.setEnabled(false);
        importSummary.setText("저장하는 중이에요…");
        io.execute(() -> {
            try {
                repository.insertLedgerEntries(result.transactions);
                BankSyncStore.setImportAccount(this, account);
                BankSyncStore.updateBalance(this, account, result.latestBalance, result.latestBalanceAtMillis);
                BankSyncStore.appendLog(this, "파일에서 " + result.transactions.size() + "건 가져왔어요 (" + account + ")");
                runOnUiThread(() -> {
                    pendingImport = null;
                    saveImportButton.setVisibility(View.GONE);
                    importSummary.setText("✅ " + result.transactions.size() + "건을 저장했어요. 가계부 탭에서 확인해 보세요.");
                    refresh();
                });
            } catch (Exception error) {
                final String message = error.getMessage() == null ? "알 수 없는 오류" : error.getMessage();
                runOnUiThread(() -> {
                    saveImportButton.setEnabled(true);
                    importSummary.setText("저장하지 못했어요: " + message);
                });
            }
        });
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) return cursor.getString(column);
            }
        } catch (Exception ignored) {
            // 이름을 못 읽으면 확장자 없이 보내고, 서버가 파일 내용을 보고 판단합니다.
        }
        return "";
    }

    private static String extensionOf(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return ".pdf";
        if (lower.endsWith(".xlsx")) return ".xlsx";
        if (lower.endsWith(".xls")) return ".xls";
        if (lower.endsWith(".csv")) return ".csv";
        return ".txt";
    }

    private byte[] readAll(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new Exception("파일을 열 수 없어요.");
            byte[] buffer = new byte[8192];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FILE_BYTES) throw new Exception("파일이 너무 커요. 5MB 이하 파일을 올려 주세요.");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    // ------------------------------------------------------------------ 화면 도우미
    private static String won(long amount) {
        return NumberFormat.getInstance(Locale.KOREA).format(amount) + "원";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams wrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(4), 0, dp(4));
        return params;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private Button button(String label, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(action);
        return button;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(0xffffffff, dp(18), 0xffe2e5eb));
        LinearLayout.LayoutParams params = wrap();
        params.setMargins(0, dp(14), 0, dp(4));
        parent.addView(card, params);
        return card;
    }
}
