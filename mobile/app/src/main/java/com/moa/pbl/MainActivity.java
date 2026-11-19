package com.moa.pbl;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.net.Uri;
import android.view.View;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.moa.pbl.data.LedgerEntry;
import com.moa.pbl.data.LedgerParserRepository;
import com.moa.pbl.data.ParsedTransaction;
import com.moa.pbl.data.SupabaseRepository;
import com.moa.pbl.data.TranscriptionRepository;
import com.moa.pbl.analysis.AnalysisViews;
import com.moa.pbl.analysis.CategoryPalette;
import com.moa.pbl.analysis.DonutChartView;
import com.moa.pbl.analysis.MonthlySpendingFeedback;
import com.moa.pbl.analysis.ConsumptionProfile;
import com.moa.pbl.analysis.ProfileFormView;
import com.moa.pbl.analysis.ProfileStore;
import com.moa.pbl.analysis.PeerComparison;
import com.moa.pbl.analysis.PeerDataGenerator;
import com.moa.pbl.analysis.AnalysisMonth;
import com.moa.pbl.analysis.GenderPreference;
import com.moa.pbl.analysis.MonthComparison;
import com.moa.pbl.analysis.ReportViews;
import com.moa.pbl.analysis.SpendingCalendar;

import java.io.File;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends ComponentActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SupabaseRepository supabase;
    private TranscriptionRepository transcription;
    private LedgerParserRepository ledgerParser;
    private ActivityResultLauncher<String> microphonePermission;
    private ActivityResultLauncher<Uri> receiptCapture;
    private ActivityResultLauncher<String> receiptPicker;
    private Uri pendingReceiptUri;
    private File pendingReceiptFile;
    private LinearLayout root;
    private EditText emailField;
    private EditText passwordField;
    private EditText chatInput;
    private TextView status;
    private Button recordButton;
    private LinearLayout homeContent;
    private LinearLayout homeHeader;
    private LinearLayout homeSummary;
    private LinearLayout chatComposer;
    private ScrollView contentScroll;
    private TextView monthSummaryLabel;
    private TextView remainingSummary;
    private TextView summaryDetails;
    private TextView chatStatus;
    private boolean summaryExpanded;
    private boolean chatBusy;
    private float summaryTouchY;
    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private final List<TextView> navigationItems = new ArrayList<>();
    private String selectedTab = "home";
    private boolean editingProfile;
    private boolean logoutInProgress;
    private boolean profileLoading;
    private boolean profileLoadFailed;
    private ConsumptionProfile consumptionProfile;
    private int profileRequestId;
    private List<LedgerEntry> ledgerEntries = new ArrayList<>();
    private String ledgerLoadError = "";
    private YearMonth selectedMonth = YearMonth.now();
    private File recordingFile;
    private android.media.MediaRecorder recorder;
    private boolean recording;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable recordingLimit = this::stopRecording;
    private boolean resumedOnce = false;
    // SharedPreferences는 리스너를 약하게만 붙잡으므로 필드로 들고 있어야 합니다.
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener bankChangeListener = (prefs, key) -> {
        if (BankSyncStore.isLogKey(key)) mainHandler.post(this::refreshLedgerEntries);
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supabase = new SupabaseRepository(this);
        transcription = new TranscriptionRepository();
        ledgerParser = new LedgerParserRepository();
        microphonePermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startRecording(); else showMessage("음성 녹음을 위해 마이크 권한이 필요합니다.");
        });
        receiptCapture = registerForActivityResult(new ActivityResultContracts.TakePicture(), captured -> {
            Uri image = pendingReceiptUri;
            pendingReceiptUri = null;
            File capturedFile = pendingReceiptFile;
            pendingReceiptFile = null;
            if (captured && image != null) processReceipt(image, capturedFile);
            else if (capturedFile != null) capturedFile.delete();
        });
        receiptPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), image -> {
            if (image != null) processReceipt(image, null);
        });
        if (isOAuthCallback(getIntent())) {
            completeOAuthCallback(getIntent().getData());
        } else if (supabase.isConfigured() && supabase.hasSession()) showJournal(); else showLogin();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (isOAuthCallback(intent)) completeOAuthCallback(intent.getData());
    }

    private void showLogin() {
        showAuthScreen(false);
    }

    private void showAuthScreen(boolean signup) {
        buildRoot();
        getWindow().setStatusBarColor(0xfffffcf7);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setImageResource(com.moa.pbl.R.drawable.app_brand);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brand.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView brandName = new TextView(this); brandName.setText("자연어 가계부"); brandName.setTextSize(18);
        brandName.setTextColor(0xff172642); brandName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brandName.setLetterSpacing(-0.015f);
        LinearLayout.LayoutParams brandNameParams = new LinearLayout.LayoutParams(-2, -2); brandNameParams.setMargins(dp(12), 0, 0, 0);
        brand.addView(brandName, brandNameParams);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(-1, -2); brandParams.setMargins(0, 0, 0, dp(30));
        root.addView(brand, brandParams);

        addText(signup ? "회원가입" : "다시 오셨네요", 29, 0xff172642, true);
        addText(signup ? "간편하게 계정을 만들고 기록을 시작하세요." : "수입과 지출을 자연어로 기록해 보세요.", 15, 0xff7e8798, false);

        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(18), dp(20), dp(20));
        form.setBackground(rounded(0xffffffff, dp(22), 0xffe7e8e8));
        LinearLayout.LayoutParams formParams = new LinearLayout.LayoutParams(-1, -2); formParams.setMargins(0, dp(16), 0, dp(10));
        root.addView(form, formParams);
        emailField = addAuthField(form, "이메일", false);
        passwordField = addAuthField(form, "비밀번호", true);
        TextView passwordHint = new TextView(this); passwordHint.setText("비밀번호는 6자 이상 입력해 주세요.");
        passwordHint.setTextSize(12); passwordHint.setTextColor(0xff7e8798);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2); hintParams.setMargins(0, dp(4), 0, 0); form.addView(passwordHint, hintParams);

        Button submit = new Button(this); submit.setText(signup ? "계정 만들기" : "로그인");
        styleButton(submit, 0xff1b4fa3, 0xffffffff); submit.setOnClickListener(view -> authenticate(signup));
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(-1, dp(54)); submitParams.setMargins(0, dp(10), 0, 0); form.addView(submit, submitParams);
        status = addText("", 13, 0xffa23f35, false);

        TextView switchMode = new TextView(this);
        switchMode.setText(signup ? "이미 계정이 있나요?  로그인" : "처음 이용하시나요?  회원가입");
        switchMode.setTextSize(14); switchMode.setTextColor(0xff1b4fa3); switchMode.setGravity(android.view.Gravity.CENTER);
        switchMode.setPadding(0, dp(14), 0, dp(14)); switchMode.setOnClickListener(view -> showAuthScreen(!signup));
        root.addView(switchMode, new LinearLayout.LayoutParams(-1, -2));

        TextView socialDivider = new TextView(this);
        socialDivider.setText("간편 로그인"); socialDivider.setTextSize(13); socialDivider.setTextColor(0xff7e8798);
        socialDivider.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams dividerParams = matchWrap(); dividerParams.setMargins(0, dp(10), 0, dp(8));
        root.addView(socialDivider, dividerParams);
        Button googleLogin = new Button(this); googleLogin.setText("Google로 계속하기");
        styleButton(googleLogin, 0xffffffff, 0xff172642);
        googleLogin.setBackground(rounded(0xffffffff, dp(14), 0xffdfe5dc));
        googleLogin.setOnClickListener(view -> startOAuthLogin("google"));
        LinearLayout.LayoutParams socialParams = new LinearLayout.LayoutParams(-1, dp(52));
        socialParams.setMargins(0, 0, 0, dp(8)); root.addView(googleLogin, socialParams);
        Button naverLogin = new Button(this); naverLogin.setText("네이버로 계속하기");
        styleButton(naverLogin, 0xff03c75a, 0xffffffff);
        naverLogin.setOnClickListener(view -> startOAuthLogin("custom:naver"));
        root.addView(naverLogin, new LinearLayout.LayoutParams(-1, dp(52)));
        if (!supabase.isConfigured()) status.setText("지금은 로그인할 수 없어요. 잠시 후 다시 시도해 주세요.");
    }

    private void startOAuthLogin(String provider) {
        try {
            String callback = "com.moa.pbl://auth/callback";
            String url = supabase.createOAuthAuthorizationUrl(provider, callback);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            if (status != null) status.setText(error.getMessage());
        }
    }

    private boolean isOAuthCallback(Intent intent) {
        Uri uri = intent == null ? null : intent.getData();
        return uri != null && "com.moa.pbl".equals(uri.getScheme())
                && "auth".equals(uri.getHost()) && "/callback".equals(uri.getPath());
    }

    private void completeOAuthCallback(Uri callback) {
        showAuthScreen(false);
        String error = callback.getQueryParameter("error_description");
        if (error == null || error.isEmpty()) error = callback.getQueryParameter("error");
        if (error != null && !error.isEmpty()) {
            status.setText("로그인하지 못했어요. 다시 시도해 주세요.");
            return;
        }
        String code = callback.getQueryParameter("code");
        if (code == null || code.isEmpty()) {
            status.setText("로그인 결과를 확인하지 못했어요. 다시 시도해 주세요.");
            return;
        }
        setBusy(true);
        io.execute(() -> {
            try {
                supabase.completeOAuthAuthorization(code);
                runOnUiThread(() -> { setBusy(false); showJournal(); });
            } catch (Exception authError) {
                runOnUiThread(() -> { setBusy(false); status.setText(authError.getMessage()); });
            }
        });
    }

    private EditText addAuthField(LinearLayout parent, String label, boolean password) {
        TextView caption = new TextView(this); caption.setText(label); caption.setTextSize(14);
        caption.setTextColor(0xff172642); caption.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-1, -2); captionParams.setMargins(0, dp(10), 0, dp(5)); parent.addView(caption, captionParams);
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint(password ? "6자 이상" : "example@email.com");
        input.setTextSize(15); input.setPadding(dp(14), dp(10), dp(14), dp(10)); input.setBackground(rounded(0xfffffcf7, dp(14), 0xffdfe5dc));
        input.setInputType(password ? 129 : 33); parent.addView(input, new LinearLayout.LayoutParams(-1, dp(52)));
        return input;
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
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(0xfffffcf7);

        homeHeader = createHomeHeader();
        shell.addView(homeHeader, new LinearLayout.LayoutParams(-1, dp(72)));
        homeSummary = createHomeSummary();
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.setMargins(dp(16), 0, dp(16), dp(8));
        shell.addView(homeSummary, summaryParams);

        contentScroll = new ScrollView(this);
        contentScroll.setFillViewport(true);
        contentScroll.setClipToPadding(false);
        contentScroll.setPadding(dp(16), dp(4), dp(16), dp(8));
        homeContent = new LinearLayout(this);
        homeContent.setOrientation(LinearLayout.VERTICAL);
        homeContent.setPadding(0, dp(8), 0, dp(12));
        contentScroll.addView(homeContent);
        shell.addView(contentScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        chatComposer = createChatComposer();
        shell.addView(chatComposer, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(android.view.Gravity.CENTER);
        navigation.setPadding(0, dp(8), 0, dp(10));
        navigation.setBackgroundColor(0xffffffff);
        navigationItems.clear();
        addNavigationItem(navigation, "home", "⌂", "홈");
        addNavigationItem(navigation, "stats", "▥", "통계");
        addNavigationItem(navigation, "feedback", "♧", "소비 피드백");
        addNavigationItem(navigation, "mypage", "◎", "마이페이지");
        shell.addView(navigation, new LinearLayout.LayoutParams(-1, dp(68)));
        setContentView(shell);
        contentScroll.setOnScrollChangeListener((View view, int x, int y, int oldX, int oldY) -> {
            if ("home".equals(selectedTab) && y > dp(12) && summaryExpanded) {
                summaryExpanded = false;
                renderHomeSummary();
            }
        });
        showTab("home");
        loadConsumptionProfile();
        refreshLedgerEntries();
    }

    private LinearLayout createHomeHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(6), dp(18), dp(4));
        header.setBackgroundColor(0xfffffcf7);
        ImageView mark = new ImageView(this);
        mark.setImageResource(com.moa.pbl.R.drawable.app_brand);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setPadding(dp(10), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("자연어 가계부"); title.setTextSize(19); title.setTextColor(0xff172642);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brandText.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("소비를 말하면 정리해드려요"); subtitle.setTextSize(13); subtitle.setTextColor(0xff66758e);
        brandText.addView(subtitle);
        header.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView profile = new TextView(this);
        profile.setText("♙"); profile.setTextSize(22); profile.setTextColor(0xff172642);
        profile.setGravity(android.view.Gravity.CENTER);
        profile.setBackground(rounded(0xffedf1fa, dp(24), 0xffedf1fa));
        profile.setOnClickListener(v -> showTab("mypage"));
        header.addView(profile, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return header;
    }

    private LinearLayout createHomeSummary() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(10), dp(16), dp(10));
        card.setBackground(rounded(0xff16468f, dp(22), 0xff16468f));
        card.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                summaryTouchY = event.getRawY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float delta = event.getRawY() - summaryTouchY;
                if (Math.abs(delta) > dp(24)) summaryExpanded = delta > 0;
                else summaryExpanded = !summaryExpanded;
                renderHomeSummary();
                return true;
            }
            return true;
        });
        return card;
    }

    private void renderHomeSummary() {
        if (homeSummary == null) return;
        homeSummary.removeAllViews();
        LinearLayout monthRow = new LinearLayout(this);
        monthRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView previous = new TextView(this); previous.setText("‹"); previous.setTextSize(24); previous.setTextColor(0xffffffff); previous.setGravity(android.view.Gravity.CENTER);
        previous.setOnClickListener(v -> { selectedMonth = selectedMonth.minusMonths(1); renderHomeSummary(); });
        monthRow.addView(previous, new LinearLayout.LayoutParams(dp(34), dp(32)));
        monthSummaryLabel = new TextView(this);
        monthSummaryLabel.setText(selectedMonth.getYear() + "년 " + selectedMonth.getMonthValue() + "월");
        monthSummaryLabel.setTextSize(15); monthSummaryLabel.setTextColor(0xffffffff); monthSummaryLabel.setTypeface(null, Typeface.BOLD);
        monthSummaryLabel.setGravity(android.view.Gravity.CENTER);
        monthRow.addView(monthSummaryLabel, new LinearLayout.LayoutParams(0, dp(32), 1));
        TextView next = new TextView(this); next.setText(selectedMonth.isBefore(YearMonth.now()) ? "›" : "⌄"); next.setTextSize(22); next.setTextColor(0xffffffff); next.setGravity(android.view.Gravity.CENTER);
        next.setOnClickListener(v -> {
            if (selectedMonth.isBefore(YearMonth.now())) selectedMonth = selectedMonth.plusMonths(1);
            else summaryExpanded = !summaryExpanded;
            renderHomeSummary();
        });
        monthRow.addView(next, new LinearLayout.LayoutParams(dp(34), dp(32)));
        homeSummary.addView(monthRow);
        long income = totalForMonth("income", selectedMonth);
        long expenses = totalForMonth("expense", selectedMonth);
        remainingSummary = new TextView(this);
        remainingSummary.setText("남은 금액  " + formatAmount(income - expenses) + "원");
        remainingSummary.setTextSize(summaryExpanded ? 20 : 17); remainingSummary.setTextColor(0xffffffff);
        remainingSummary.setTypeface(null, Typeface.BOLD); remainingSummary.setPadding(dp(4), dp(2), 0, dp(2));
        homeSummary.addView(remainingSummary);
        if (summaryExpanded) {
            summaryDetails = new TextView(this);
            summaryDetails.setText("총수입  " + formatAmount(income) + "원       총지출  " + formatAmount(expenses) + "원");
            summaryDetails.setTextSize(13); summaryDetails.setTextColor(0xffdbe7f8); summaryDetails.setPadding(dp(4), dp(4), 0, dp(2));
            homeSummary.addView(summaryDetails);
        }
    }

    private LinearLayout createChatComposer() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(12), dp(4), dp(12), dp(6));
        wrapper.setBackgroundColor(0xfffffcf7);
        chatStatus = new TextView(this);
        chatStatus.setTextSize(12); chatStatus.setTextColor(0xff597ab5);
        wrapper.addView(chatStatus, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(2), dp(4), dp(2));
        row.setBackground(rounded(0xffffffff, dp(28), 0xffd5ddea));
        chatInput = new EditText(this);
        chatInput.setSingleLine(true); chatInput.setTextSize(14);
        chatInput.setHint("소비 내역이나 궁금한 점을 입력하세요");
        chatInput.setBackgroundColor(0x00000000);
        chatInput.setPadding(dp(4), dp(8), dp(4), dp(8));
        chatInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        chatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { sendChatMessage(); return true; }
            return false;
        });
        row.addView(chatInput, new LinearLayout.LayoutParams(0, -2, 1));
        recordButton = new Button(this); recordButton.setText("🎙"); recordButton.setTextSize(17); recordButton.setAllCaps(false);
        recordButton.setTextColor(0xffffffff); recordButton.setBackground(rounded(0xff1b4fa3, dp(24), 0xff1b4fa3));
        recordButton.setOnClickListener(v -> toggleRecording());
        LinearLayout.LayoutParams micParams = new LinearLayout.LayoutParams(dp(44), dp(44)); micParams.setMargins(dp(4), 0, dp(4), 0);
        row.addView(recordButton, micParams);
        Button receiptButton = new Button(this); receiptButton.setText("📷"); receiptButton.setTextSize(19); receiptButton.setAllCaps(false);
        receiptButton.setContentDescription("영수증 사진 추가");
        receiptButton.setTextColor(0xff1b4fa3); receiptButton.setBackground(rounded(0xffedf3fc, dp(24), 0xffedf3fc));
        receiptButton.setOnClickListener(v -> showReceiptOptions());
        LinearLayout.LayoutParams receiptParams = new LinearLayout.LayoutParams(dp(44), dp(44)); receiptParams.setMargins(dp(2), 0, dp(2), 0);
        row.addView(receiptButton, receiptParams);
        TextView send = new TextView(this); send.setText("➤"); send.setTextSize(21); send.setTextColor(0xff1b4fa3); send.setGravity(android.view.Gravity.CENTER);
        send.setOnClickListener(v -> sendChatMessage());
        row.addView(send, new LinearLayout.LayoutParams(dp(42), dp(44)));
        wrapper.addView(row);
        return wrapper;
    }

    private void addNavigationItem(LinearLayout navigation, String tab, String icon, String label) {
        TextView item = new TextView(this);
        item.setText(icon + "\n" + label); item.setTextSize(12); item.setGravity(android.view.Gravity.CENTER);
        item.setPadding(0, dp(5), 0, dp(5)); item.setOnClickListener(v -> showTab(tab));
        navigationItems.add(item);
        navigation.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void showTab(String tab) {
        if (recording) return;
        selectedTab = tab;
        contentScroll.scrollTo(0, 0);
        getWindow().setStatusBarColor(0xfffffcf7);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        String[] tabs = {"home", "stats", "feedback", "mypage"};
        for (int index = 0; index < tabs.length; index++) {
            navigationItems.get(index).setTextColor(tabs[index].equals(tab) ? 0xff1b4fa3 : 0xff929990);
            navigationItems.get(index).setTypeface(null, tabs[index].equals(tab) ? Typeface.BOLD : Typeface.NORMAL);
        }
        homeContent.removeAllViews();
        status = null;
        boolean isHome = "home".equals(tab);
        homeHeader.setVisibility(isHome ? View.VISIBLE : View.GONE);
        homeSummary.setVisibility(isHome ? View.VISIBLE : View.GONE);
        chatComposer.setVisibility(isHome ? View.VISIBLE : View.GONE);
        if (isHome) {
            renderHomeSummary();
            showHomeTab();
        }
        else if ("stats".equals(tab)) showStatisticsTab();
        else if ("feedback".equals(tab)) showAnalysisTab();
        else showMyPageTab();
    }

    private void showHomeTab() {
        if (chatMessages.isEmpty()) chatMessages.add(ChatMessage.bot("안녕하세요! 소비 내역을 말하거나 궁금한 점을 물어보세요.\n예: 오늘 점심에 김치찌개 9,000원 먹었어"));
        renderChatMessages();
    }

    private void renderChatMessages() {
        if (homeContent == null) return;
        homeContent.removeAllViews();
        TextView date = new TextView(this);
        date.setText(LocalDate.now().getMonthValue() + "월 " + LocalDate.now().getDayOfMonth() + "일");
        date.setTextSize(12); date.setTextColor(0xff66758e); date.setGravity(android.view.Gravity.CENTER);
        date.setPadding(0, dp(4), 0, dp(10)); homeContent.addView(date, matchWrap());
        for (ChatMessage message : chatMessages) {
            if (message.user) addUserMessage(message.text);
            else addBotMessage(message);
        }
        if (!chatBusy && chatMessages.size() <= 1) {
            LinearLayout suggestions = new LinearLayout(this);
            suggestions.setOrientation(LinearLayout.VERTICAL);
            addSuggestion(suggestions, "이번 달 어디에 얼마 썼어?");
            addSuggestion(suggestions, "지금 소비가 어때?");
            addSuggestion(suggestions, "은행 거래내역 불러오기");
            homeContent.addView(suggestions, matchWrap());
        }
        if (!ledgerLoadError.isEmpty()) addText(ledgerLoadError, 12, 0xffa23f35, false);
        contentScroll.post(() -> contentScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addSuggestion(LinearLayout parent, String prompt) {
        TextView chip = new TextView(this);
        chip.setText("✧   " + prompt); chip.setTextSize(14); chip.setTextColor(0xff355b9a);
        chip.setPadding(dp(14), dp(10), dp(14), dp(10));
        chip.setBackground(rounded(0xffedf3fc, dp(22), 0xffd8e5f8));
        chip.setOnClickListener(v -> {
            if (prompt.startsWith("은행 거래내역")) {
                startActivity(new android.content.Intent(this, BankSyncActivity.class));
                return;
            }
            chatInput.setText(prompt); sendChatMessage();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(dp(54), dp(4), 0, dp(4)); parent.addView(chip, params);
    }

    private void addUserMessage(String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.END);
        TextView bubble = new TextView(this);
        bubble.setText(value); bubble.setTextSize(16); bubble.setTextColor(0xffffffff);
        bubble.setPadding(dp(16), dp(12), dp(16), dp(12));
        bubble.setBackground(rounded(0xff1b5bb8, dp(26), 0xff1b5bb8));
        row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams params = matchWrap(); params.setMargins(0, dp(4), 0, dp(10)); homeContent.addView(row, params);
    }

    private void addBotMessage(ChatMessage message) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.TOP);
        TextView avatar = new TextView(this); avatar.setText("✧"); avatar.setTextSize(18); avatar.setTextColor(0xff1b5bb8);
        avatar.setGravity(android.view.Gravity.CENTER); avatar.setBackground(rounded(0xffe5f0ff, dp(24), 0xffe5f0ff));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout bubble = new LinearLayout(this); bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(12), dp(14), dp(12)); bubble.setBackground(rounded(0xfff0f3fb, dp(22), 0xfff0f3fb));
        TextView text = new TextView(this); text.setText(message.text); text.setTextSize(15); text.setTextColor(0xff172642);
        bubble.addView(text);
        if (message.transactions != null && !message.transactions.isEmpty()) {
            for (ParsedTransaction transaction : message.transactions) bubble.addView(transactionChatCard(transaction));
        }
        if (message.categories != null) bubble.addView(chatSummaryCard(message));
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(0, -2, 1);
        bubbleParams.setMargins(dp(8), 0, 0, 0); row.addView(bubble, bubbleParams);
        LinearLayout.LayoutParams params = matchWrap(); params.setMargins(0, dp(2), 0, dp(12)); homeContent.addView(row, params);
    }

    private View transactionChatCard(ParsedTransaction transaction) {
        LinearLayout card = AnalysisViews.card(this);
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, dp(8), 0, 0);
        card.setBackground(rounded(0xffffffff, dp(16), 0xffd9e1ee));
        TextView category = new TextView(this);
        category.setText(transaction.category() + "  ·  " + transaction.itemName());
        category.setTextSize(14); category.setTextColor(0xff172642); category.setTypeface(null, Typeface.BOLD);
        card.addView(category);
        String prefix = "income".equals(transaction.transactionType()) ? "+" : "−";
        String date = transaction.occurredOn() == null ? "오늘" : transaction.occurredOn();
        TextView amount = new TextView(this); amount.setText(prefix + formatAmount(transaction.amount()) + "원 · " + date);
        amount.setTextSize(14); amount.setTextColor(0xff1b4fa3); card.addView(amount);
        card.setLayoutParams(cardParams);
        return card;
    }

    private View chatSummaryCard(ChatMessage message) {
        LinearLayout card = AnalysisViews.card(this);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(rounded(0xffffffff, dp(18), 0xffdfe5ee));
        YearMonth messageMonth = message.month == null ? selectedMonth : message.month;
        TextView title = new TextView(this); title.setText(messageMonth.getMonthValue() + "월 지출 요약");
        title.setTextSize(16); title.setTextColor(0xff172642); title.setTypeface(null, Typeface.BOLD); card.addView(title);
        TextView total = new TextView(this); total.setText("총지출  " + formatAmount(message.total) + "원");
        total.setTextSize(20); total.setTextColor(0xff172642); total.setTypeface(null, Typeface.BOLD); card.addView(total);
        if (message.total > 0) {
            DonutChartView donut = new DonutChartView(this);
            List<DonutChartView.Slice> slices = new ArrayList<>();
            List<Map.Entry<String, Long>> entries = new ArrayList<>(message.categories.entrySet());
            Collections.sort(entries, (a, b) -> Long.compare(b.getValue(), a.getValue()));
            for (Map.Entry<String, Long> entry : entries) if (entry.getValue() > 0)
                slices.add(new DonutChartView.Slice(entry.getKey(), entry.getValue(), CategoryPalette.colorOf(CategoryPalette.group(entry.getKey()))));
            donut.setData(slices, "지출", "비중");
            LinearLayout.LayoutParams donutParams = new LinearLayout.LayoutParams(dp(148), dp(148)); donutParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            card.addView(donut, donutParams);
            for (Map.Entry<String, Long> entry : entries) {
                if (entry.getValue() <= 0) continue;
                int share = (int) Math.round(entry.getValue() * 100.0 / message.total);
                TextView line = new TextView(this);
                line.setText(entry.getKey() + "    " + formatAmount(entry.getValue()) + "원    " + share + "%");
                line.setTextSize(13); line.setTextColor(0xff35445e); card.addView(line);
            }
        } else {
            TextView empty = new TextView(this); empty.setText("아직 이번 달 지출 기록이 없어요."); empty.setTextColor(0xff7e8798); card.addView(empty);
        }
        return card;
    }

    private void sendChatMessage() {
        if (chatBusy || chatInput == null) return;
        String transcript = chatInput.getText().toString().trim();
        if (transcript.isEmpty()) return;
        chatInput.setText("");
        chatMessages.add(ChatMessage.user(transcript));
        if (isSpendingQuestion(transcript)) {
            Map<String, Long> categories = categoryTotals(selectedMonth);
            boolean wantsBreakdown = transcript.replace(" ", "").contains("어디")
                    || transcript.replace(" ", "").contains("소비")
                    || transcript.replace(" ", "").contains("지출")
                    || transcript.replace(" ", "").contains("어때");
            if (wantsBreakdown) chatMessages.add(ChatMessage.summary(createSpendingAnswer(transcript), categories, totalForMonth("expense", selectedMonth), selectedMonth));
            else chatMessages.add(ChatMessage.botAnswer(createSpendingAnswer(transcript)));
            renderChatMessages();
            return;
        }
        chatBusy = true;
        chatStatus.setText("내용을 정리하고 있어요…");
        renderChatMessages();
        io.execute(() -> {
            try {
                List<ParsedTransaction> result = ledgerParser.parse(transcript);
                if (result.isEmpty()) {
                    runOnUiThread(() -> finishChatWithAnswer("금액이 포함된 거래를 찾지 못했어요. 예를 들어 ‘점심에 김치찌개 9,000원 먹었어’처럼 말해 주세요."));
                    return;
                }
                supabase.insertLedgerEntries(result);
                runOnUiThread(() -> {
                    chatMessages.add(ChatMessage.transactions("가계부에 반영했어요. 분류한 내용이 맞는지 확인해 주세요.", result));
                    finishChatRequest();
                    refreshLedgerEntries();
                });
            } catch (Exception error) {
                runOnUiThread(() -> finishChatWithAnswer("가계부에 추가하지 못했어요. 서버 연결과 로그인 상태를 확인한 뒤 다시 시도해 주세요."));
            }
        });
    }

    private void showReceiptOptions() {
        if (chatBusy || recording) return;
        new AlertDialog.Builder(this)
                .setTitle("영수증 추가")
                .setItems(new String[]{"사진 촬영", "앨범에서 선택"}, (dialog, which) -> {
                    if (which == 0) captureReceipt();
                    else receiptPicker.launch("image/*");
                })
                .show();
    }

    private void captureReceipt() {
        try {
            File receiptDirectory = new File(getCacheDir(), "receipts");
            if (!receiptDirectory.exists() && !receiptDirectory.mkdirs())
                throw new IllegalStateException("영수증 임시 폴더를 만들지 못했어요.");
            File imageFile = File.createTempFile("receipt-", ".jpg", receiptDirectory);
            pendingReceiptFile = imageFile;
            pendingReceiptUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", imageFile);
            receiptCapture.launch(pendingReceiptUri);
        } catch (Exception error) {
            pendingReceiptUri = null;
            if (pendingReceiptFile != null) pendingReceiptFile.delete();
            pendingReceiptFile = null;
            showMessage("카메라를 열지 못했어요. 앨범에서 사진을 선택해 주세요.");
        }
    }

    private void processReceipt(Uri image, File temporaryFile) {
        if (chatBusy || chatInput == null) {
            if (temporaryFile != null) temporaryFile.delete();
            return;
        }
        chatMessages.add(ChatMessage.user("영수증 사진을 보냈어요."));
        chatBusy = true;
        if (chatStatus != null) chatStatus.setText("영수증을 읽고 있어요…");
        renderChatMessages();
        io.execute(() -> {
            try {
                List<ParsedTransaction> result = ledgerParser.parseReceipt(this, image);
                if (result.isEmpty()) {
                    runOnUiThread(() -> finishChatWithAnswer("영수증에서 금액이 있는 거래를 찾지 못했어요. 글자가 선명하게 보이도록 다시 촬영해 주세요."));
                    return;
                }
                supabase.insertLedgerEntries(result);
                runOnUiThread(() -> {
                    chatMessages.add(ChatMessage.transactions("영수증에서 거래를 찾아 가계부에 반영했어요. 항목과 금액을 확인해 주세요.", result));
                    finishChatRequest();
                    refreshLedgerEntries();
                });
            } catch (Exception error) {
                runOnUiThread(() -> finishChatWithAnswer(error.getMessage() == null
                        ? "영수증을 처리하지 못했어요. 다시 시도해 주세요." : error.getMessage()));
            } finally {
                if (temporaryFile != null) temporaryFile.delete();
            }
        });
    }

    private boolean isSpendingQuestion(String text) {
        String value = text.replace(" ", "").toLowerCase(Locale.ROOT);
        return value.contains("어때") || value.contains("어디에") || value.contains("얼마썼")
                || value.contains("얼마사용") || value.contains("지출요약") || value.contains("수입은")
                || value.contains("남은금액") || value.contains("잔액") || value.contains("비교해")
                || value.contains("카테고리별") || value.contains("소비현황") || value.contains("보여줘")
                || (value.contains("얼마") && (value.contains("이번달") || value.contains("지난달") || value.contains("지금")));
    }

    private String createSpendingAnswer(String question) {
        YearMonth month = selectedMonth;
        long expenses = totalForMonth("expense", month);
        long income = totalForMonth("income", month);
        Map<String, Long> categories = categoryTotals(month);
        Map.Entry<String, Long> largest = null;
        for (Map.Entry<String, Long> entry : categories.entrySet())
            if (largest == null || entry.getValue() > largest.getValue()) largest = entry;
        String label = month.getMonthValue() + "월";
        if (question.replace(" ", "").contains("수입"))
            return label + " 수입은 " + formatAmount(income) + "원, 지출은 " + formatAmount(expenses) + "원이에요. 남은 금액은 " + formatAmount(income - expenses) + "원이에요.";
        String answer = label + " 총지출은 " + formatAmount(expenses) + "원이에요.";
        if (largest != null && largest.getValue() > 0) answer += " 가장 많이 쓴 항목은 " + largest.getKey() + "으로 " + formatAmount(largest.getValue()) + "원이에요.";
        if (question.replace(" ", "").contains("어때") && expenses > 0) {
            long previous = totalForMonth("expense", month.minusMonths(1));
            if (previous > 0) {
                long delta = expenses - previous;
                double pct = Math.abs(delta) * 100.0 / previous;
                answer += delta > 0 ? " 지난달보다 " + String.format(Locale.KOREA, "%.1f", pct) + "% 늘었어요." : delta < 0 ? " 지난달보다 " + String.format(Locale.KOREA, "%.1f", pct) + "% 줄었어요." : " 지난달과 같아요.";
            }
        }
        if (question.replace(" ", "").contains("비교")) {
            long previous = totalForMonth("expense", month.minusMonths(1));
            answer += " 지난달 지출은 " + formatAmount(previous) + "원이었어요.";
            if (previous > 0) {
                long delta = expenses - previous;
                double pct = Math.abs(delta) * 100.0 / previous;
                answer += delta > 0 ? " " + formatAmount(delta) + "원(" + String.format(Locale.KOREA, "%.1f", pct) + "%) 늘었어요."
                        : delta < 0 ? " " + formatAmount(-delta) + "원(" + String.format(Locale.KOREA, "%.1f", pct) + "%) 줄었어요." : " 지난달과 같아요.";
            }
        }
        return answer;
    }

    private void finishChatWithAnswer(String answer) {
        chatMessages.add(ChatMessage.botAnswer(answer));
        finishChatRequest();
    }

    private void finishChatRequest() {
        chatBusy = false;
        if (chatStatus != null) chatStatus.setText("");
        renderChatMessages();
    }

    private static final class ChatMessage {
        final boolean user;
        final String text;
        final List<ParsedTransaction> transactions;
        final Map<String, Long> categories;
        final long total;
        final YearMonth month;

        private ChatMessage(boolean user, String text, List<ParsedTransaction> transactions,
                            Map<String, Long> categories, long total, YearMonth month) {
            this.user = user;
            this.text = text;
            this.transactions = transactions;
            this.categories = categories;
            this.total = total;
            this.month = month;
        }

        static ChatMessage user(String text) { return new ChatMessage(true, text, null, null, 0L, null); }
        static ChatMessage bot(String text) { return new ChatMessage(false, text, null, null, 0L, null); }
        static ChatMessage botAnswer(String text) { return bot(text); }
        static ChatMessage transactions(String text, List<ParsedTransaction> items) {
            return new ChatMessage(false, text, new ArrayList<>(items), null, 0L, null);
        }
        static ChatMessage summary(String text, Map<String, Long> categories, long total, YearMonth month) {
            return new ChatMessage(false, text, null, new LinkedHashMap<>(categories), total, month);
        }
    }

    private void showStatisticsTab() {
        addText("월간 통계", 27, 0xff172642, false);
        addText("나의 소비 흐름을 한눈에 확인해요", 15, 0xff7e8798, false);
        LinearLayout monthRow = new LinearLayout(this); monthRow.setGravity(android.view.Gravity.CENTER);
        TextView previous = navArrow("‹", () -> { selectedMonth = selectedMonth.minusMonths(1); showTab("stats"); });
        TextView current = new TextView(this); current.setText(selectedMonth.getYear() + "년 " + selectedMonth.getMonthValue() + "월"); current.setTextSize(20); current.setTextColor(0xff172642); current.setTypeface(null, Typeface.BOLD); current.setGravity(android.view.Gravity.CENTER);
        TextView next = navArrow("›", () -> { if (selectedMonth.isBefore(YearMonth.now())) { selectedMonth = selectedMonth.plusMonths(1); showTab("stats"); } });
        monthRow.addView(previous); monthRow.addView(current, new LinearLayout.LayoutParams(0, dp(52), 1)); monthRow.addView(next); homeContent.addView(monthRow);
        long expenses = totalForMonth("expense", selectedMonth);
        long income = totalForMonth("income", selectedMonth);
        LinearLayout summary = new LinearLayout(this); summary.setOrientation(LinearLayout.VERTICAL); summary.setPadding(dp(20), dp(20), dp(20), dp(20));
        summary.setBackground(rounded(0xff1b4fa3, dp(24), 0xff1b4fa3));
        addTo(summary, "이번 달 총지출", 15, 0xffd3e0f5, false);
        addTo(summary, formatAmount(expenses) + "원", 34, 0xffffffff, true);
        addTo(summary, "총수입  " + formatAmount(income) + "원     남은 금액  " + formatAmount(income - expenses) + "원", 14, 0xffe1eafb, false);
        LinearLayout.LayoutParams summaryParams = matchWrap(); summaryParams.setMargins(0, dp(12), 0, dp(16)); homeContent.addView(summary, summaryParams);

        // 소비 달력: 날짜별 지출 합계 (거래가 없어도 빈 달력이 보인다)
        Map<Integer, Long> daily = new LinkedHashMap<>();
        for (LedgerEntry entry : ledgerEntries) {
            if (!entry.transactionType().equals("expense") || !entry.occurredOn().startsWith(selectedMonth.toString())) continue;
            int day = SpendingCalendar.dayOf(entry.occurredOn());
            if (day > 0) daily.merge(day, entry.amount(), Long::sum);
        }
        homeContent.addView(SpendingCalendar.card(this, selectedMonth, daily, () -> showTab("stats")), AnalysisViews.cardParams(this));

        // 달력에서 고른 날의 거래
        LocalDate pickedDay = SpendingCalendar.selectedDate(selectedMonth);
        if (pickedDay != null) {
            addText(pickedDay.getMonthValue() + "월 " + pickedDay.getDayOfMonth() + "일 내역", 19, 0xff172642, true);
            int shown = 0;
            for (LedgerEntry entry : ledgerEntries) {
                if (!entry.occurredOn().startsWith(pickedDay.toString())) continue;
                addTransactionRow(entry); shown++;
            }
            if (shown == 0) addText("이 날은 기록이 없어요.", 14, 0xff7e8798, false);
        }

        addDonutCard(expenses);
        addText("거래 내역", 19, 0xff172642, true);
        addRecentTransactionsForMonth(selectedMonth);
    }
    private void showAnalysisTab() {
        addText("소비 피드백", 27, 0xff172642, false);
        addText("데이터로 나의 소비 습관을 돌아봐요", 15, 0xff7e8798, false);

        // 월 선택: 거래가 없어도 항상 기본 화면이 보이도록 아래 카드들을 모두 그린다
        YearMonth month = AnalysisMonth.get();
        homeContent.addView(ReportViews.monthSelector(this, month, !AnalysisMonth.isCurrent(),
                () -> { AnalysisMonth.previous(); showTab("feedback"); },
                () -> { AnalysisMonth.next(); showTab("feedback"); }), AnalysisViews.cardParams(this));

        long income = totalForMonth("income", month);
        long expenses = totalForMonth("expense", month);
        Map<String, Long> categories = categoryTotals(month);

        // 1. 선택한 달의 카테고리별 지출 도넛
        homeContent.addView(ReportViews.donutCard(this, month, categories), AnalysisViews.cardParams(this));
        if (expenses == 0L && AnalysisMonth.isCurrent()) addButton("대화로 소비 기록하기", v -> showTab("home"));

        // 2. 이전 달과 항목별 비율 변화
        MonthComparison.Result monthResult = MonthComparison.compare(month, categories, categoryTotals(month.minusMonths(1)));
        homeContent.addView(ReportViews.monthChangeCard(this, monthResult), AnalysisViews.cardParams(this));

        // 3. 저장된 프로필에서 비교 기준을 가져온다. 월별 소비 피드백은 프로필이 없어도 표시한다.
        ConsumptionProfile profile = consumptionProfile;
        if (profile != null) {
            String gender = profile.gender == null || profile.gender.isEmpty() ? null : profile.gender;
            int age = profile.birthDate == null ? -1 : profile.ageOn(month.atEndOfMonth());
            PeerComparison.Result peerResult = PeerComparison.compare(income, categories, gender, profile.region, age,
                    PeerDataGenerator.getDefault());
            homeContent.addView(ReportViews.compositionCard(this, month, peerResult), AnalysisViews.cardParams(this));
        }
    }
    private void showMyPageTab() {
        addText("마이페이지", 27, 0xff172642, true);
        addText("계정과 소비 분석 프로필을 관리해요", 15, 0xff7e8798, false);

        LinearLayout accountCard = AnalysisViews.card(this);
        accountCard.addView(AnalysisViews.text(this, "계정", 14, 0xff597ab5, true));
        String accountEmail = supabase.currentAccountEmail();
        accountCard.addView(AnalysisViews.text(this, accountEmail.isEmpty() ? "로그인 상태" : accountEmail, 17, 0xff172642, true));
        homeContent.addView(accountCard, AnalysisViews.cardParams(this));

        ConsumptionProfile profile = consumptionProfile;
        LinearLayout profileCard = AnalysisViews.card(this);
        profileCard.addView(AnalysisViews.text(this, "소비 분석 프로필", 14, 0xff597ab5, true));
        if (profileLoading) {
            profileCard.addView(AnalysisViews.text(this, "프로필을 불러오고 있어요.", 15, 0xff7e8798, false));
        } else if (profile == null) {
            profileCard.addView(AnalysisViews.text(this, "아직 등록된 프로필이 없습니다.", 17, 0xff172642, true));
            profileCard.addView(AnalysisViews.text(this, profileLoadFailed ? "프로필을 불러오지 못했어요. 다시 시도해 주세요." : "등록하지 않아도 가계부를 이용할 수 있어요.", 13, 0xff7e8798, false));
        } else {
            profileCard.addView(AnalysisViews.text(this, profile.birthDate + " · " + profile.ageOn(LocalDate.now()) + "세", 16, 0xff172642, true));
            profileCard.addView(AnalysisViews.text(this, "성별 · " + (profile.gender.isEmpty() ? "미입력" : profile.gender), 14, 0xff52617a, false));
            profileCard.addView(AnalysisViews.text(this, profile.occupation, 14, 0xff52617a, false));
            if (ConsumptionProfile.STUDENT.equals(profile.occupation))
                profileCard.addView(AnalysisViews.text(this, profile.schoolType + " · " + profile.schoolName + " · " + profile.grade + "학년", 14, 0xff7e8798, false));
            profileCard.addView(AnalysisViews.text(this, profile.region, 14, 0xff7e8798, false));
        }
        profileCard.addView(AnalysisViews.text(this, "프로필은 소비 분석에 활용돼요.", 12, 0xff7e8798, false));
        homeContent.addView(profileCard, AnalysisViews.cardParams(this));

        Button profileButton = new Button(this);
        profileButton.setText(editingProfile ? "프로필 수정 닫기" : (profile == null ? "프로필 등록" : "프로필 수정"));
        styleButton(profileButton, 0xffeaf0fb, 0xff1b4fa3);
        profileButton.setOnClickListener(v -> { editingProfile = !editingProfile; showTab("mypage"); });
        LinearLayout.LayoutParams profileButtonParams = matchWrap(); profileButtonParams.setMargins(0, dp(4), 0, dp(8));
        homeContent.addView(profileButton, profileButtonParams);
        if (editingProfile) {
            homeContent.addView(new ProfileFormView(this, profile, saved -> {
                showMessage("프로필을 저장하고 있어요.");
        io.execute(() -> {
                    try {
                        supabase.upsertConsumptionProfile(saved);
                        ProfileStore.clearForAccount(MainActivity.this, supabase.currentAccountEmail());
                        GenderPreference.clear(MainActivity.this, supabase.currentAccountEmail());
                        runOnUiThread(() -> {
                            consumptionProfile = saved;
                            profileLoadFailed = false;
                            editingProfile = false;
                            showTab("mypage");
                            showMessage("프로필을 저장했습니다.");
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> showMessage("프로필을 저장하지 못했어요. 다시 시도해 주세요."));
                    }
                });
            }), AnalysisViews.cardParams(this));
        }

        Button logoutButton = new Button(this);
        logoutButton.setText("로그아웃"); styleButton(logoutButton, 0xffffffff, 0xffa23f35);
        logoutButton.setBackground(rounded(0xffffffff, dp(14), 0xffe5c7c3));
        logoutButton.setOnClickListener(v -> confirmLogout());
        LinearLayout.LayoutParams logoutParams = matchWrap(); logoutParams.setMargins(0, dp(18), 0, dp(12));
        homeContent.addView(logoutButton, logoutParams);
    }

    private void loadConsumptionProfile() {
        if (profileLoading) return;
        profileLoading = true;
        String accountEmail = supabase.currentAccountEmail();
        int requestId = ++profileRequestId;
        io.execute(() -> {
            ConsumptionProfile legacyProfile = ProfileStore.loadForAccount(MainActivity.this, accountEmail);
            try {
                ConsumptionProfile savedProfile = supabase.fetchConsumptionProfile();
                String legacyGender = GenderPreference.load(MainActivity.this, accountEmail);
                if (savedProfile == null && legacyProfile != null) {
                    if (legacyProfile.gender.isEmpty() && legacyGender != null)
                        legacyProfile = withGender(legacyProfile, legacyGender);
                    supabase.upsertConsumptionProfile(legacyProfile);
                    savedProfile = legacyProfile;
                } else if (savedProfile != null && savedProfile.gender.isEmpty() && legacyGender != null) {
                    savedProfile = withGender(savedProfile, legacyGender);
                    supabase.upsertConsumptionProfile(savedProfile);
                }
                if (savedProfile != null && legacyGender != null) GenderPreference.clear(MainActivity.this, accountEmail);
                ProfileStore.clearForAccount(MainActivity.this, accountEmail);
                ConsumptionProfile result = savedProfile;
                runOnUiThread(() -> {
                    if (requestId != profileRequestId) return;
                    consumptionProfile = result;
                    profileLoading = false;
                    profileLoadFailed = false;
                    if ("mypage".equals(selectedTab) || "feedback".equals(selectedTab)) showTab(selectedTab);
                });
            } catch (Exception error) {
                ConsumptionProfile fallbackProfile = legacyProfile;
                runOnUiThread(() -> {
                    if (requestId != profileRequestId) return;
                    consumptionProfile = fallbackProfile;
                    profileLoading = false;
                    profileLoadFailed = true;
                    if ("mypage".equals(selectedTab) || "feedback".equals(selectedTab)) showTab(selectedTab);
                });
            }
        });
    }

    private ConsumptionProfile withGender(ConsumptionProfile profile, String gender) {
        return new ConsumptionProfile(profile.birthDate, gender, profile.occupation, profile.schoolType,
                profile.schoolName, profile.grade, profile.region);
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("로그아웃")
                .setMessage("계정에서 로그아웃할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("로그아웃", (dialog, which) -> {
                    if (logoutInProgress) return;
                    logoutInProgress = true;
                    io.execute(() -> {
                        supabase.logout();
                        runOnUiThread(() -> {
                            logoutInProgress = false;
                            profileRequestId++;
                            profileLoading = false;
                            consumptionProfile = null;
                            editingProfile = false;
                            showLogin();
                        });
                    });
                })
                .show();
    }

    private TextView navArrow(String label, Runnable action) {
        TextView arrow = new TextView(this); arrow.setText(label); arrow.setTextSize(28); arrow.setTextColor(0xff71809a); arrow.setGravity(android.view.Gravity.CENTER); arrow.setOnClickListener(v -> action.run()); return arrow;
    }

    private LinearLayout quickCard(String heading, String detail, int color, String tab) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14), dp(12), dp(10), dp(10));
        card.setBackground(rounded(color, dp(20), color)); card.setOnClickListener(v -> showTab(tab));
        addTo(card, heading, 15, 0xff172642, true); addTo(card, detail, 12, 0xff77849a, false); return card;
    }

    private LinearLayout clickableCard(String icon, String heading, String detail, int color, Runnable action) {
        LinearLayout card = new LinearLayout(this); card.setGravity(android.view.Gravity.CENTER_VERTICAL); card.setPadding(dp(16), dp(16), dp(16), dp(16)); card.setBackground(rounded(color, dp(24), color)); card.setOnClickListener(v -> action.run());
        TextView badge = new TextView(this); badge.setText(icon); badge.setTextSize(24); badge.setTextColor(0xffffffff); badge.setGravity(android.view.Gravity.CENTER); badge.setBackground(rounded(0xff1b4fa3, dp(18), 0xff1b4fa3));
        card.addView(badge, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout text = new LinearLayout(this); text.setOrientation(LinearLayout.VERTICAL); text.setPadding(dp(12), 0, dp(8), 0);
        addTo(text, heading, 14, 0xff6484bc, true); addTo(text, detail, 18, 0xff172642, true);
        card.addView(text, new LinearLayout.LayoutParams(0, -2, 1)); addTo(card, "›", 28, 0xff1b4fa3, false); return card;
    }

    private void addTo(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color); if (bold) text.setTypeface(null, Typeface.BOLD); text.setPadding(0, dp(5), 0, dp(5)); parent.addView(text);
    }

    private void addTo(LinearLayout parent, String value, int size, int color, boolean bold, float weight) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color); if (bold) text.setTypeface(null, Typeface.BOLD); text.setPadding(0, dp(5), 0, dp(5)); parent.addView(text, new LinearLayout.LayoutParams(0, -2, weight));
    }

    private long totalForMonth(String type, YearMonth month) {
        long total = 0L;
        for (LedgerEntry entry : ledgerEntries) if (entry.occurredOn().startsWith(month.toString()) && entry.transactionType().equals(type)) total += entry.amount();
        return total;
    }

    private Map<String, Long> categoryTotals(YearMonth month) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (LedgerEntry entry : ledgerEntries) if (entry.occurredOn().startsWith(month.toString()) && entry.transactionType().equals("expense")) totals.merge(entry.category(), entry.amount(), Long::sum);
        return totals;
    }

    private void addDonutCard(long expenses) {
        homeContent.addView(ReportViews.donutCard(this, selectedMonth, categoryTotals(selectedMonth)), AnalysisViews.cardParams(this));
    }
    private void addRecentTransactions(int limit) {
        int shown = 0;
        for (LedgerEntry entry : ledgerEntries) {
            if (!entry.transactionType().equals("expense")) continue;
            addTransactionRow(entry);
            if (++shown >= limit) break;
        }
        if (shown == 0) addText(ledgerLoadError.isEmpty() ? "가계부에 저장된 거래가 없습니다." : ledgerLoadError, 14, 0xff7e8798, false);
    }

    private void addRecentTransactionsForMonth(YearMonth month) {
        int shown = 0;
        for (LedgerEntry entry : ledgerEntries) {
            if (!entry.occurredOn().startsWith(month.toString())) continue;
            addTransactionRow(entry); shown++;
        }
        if (shown == 0) addText("이 달의 거래가 없습니다.", 14, 0xff7e8798, false);
    }

    private void addTransactionRow(LedgerEntry entry) {
        LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL); row.setPadding(0, dp(9), 0, dp(9));
        TextView icon = new TextView(this); icon.setText(entry.category().equals("식비") || entry.category().equals("카페") ? "☕" : "•"); icon.setTextSize(20); icon.setGravity(android.view.Gravity.CENTER); icon.setBackground(rounded(0xffe8f0ed, dp(16), 0xffe8f0ed));
        row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout details = new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); details.setPadding(dp(10), 0, 0, 0);
        addTo(details, entry.itemName(), 15, 0xff172642, true); addTo(details, entry.category() + " · " + entry.occurredOn(), 12, 0xff7e8798, false);
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        String sign = entry.transactionType().equals("income") ? "+" : "−";
        TextView amount = new TextView(this); amount.setText(sign + formatAmount(entry.amount()) + "원"); amount.setTextSize(15); amount.setTextColor(entry.transactionType().equals("income") ? 0xff397458 : 0xff263650); amount.setTypeface(null, Typeface.BOLD); row.addView(amount);
        homeContent.addView(row);
    }

    private final class DonutView extends View {
        private final List<Long> values;
        private final long total;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private DonutView(android.content.Context context, List<Long> values, long total) { super(context); this.values = values; this.total = total; }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth() * 0.62f, getHeight() - dp(8));
            float left = (getWidth() - size) / 2f; float top = (getHeight() - size) / 2f;
            bounds.set(left, top, left + size, top + size);
            int[] colors = {0xff1b4fa3, 0xff6484bc, 0xffc7d9d0, 0xffe8d6a4, 0xffd9dce3, 0xffa7b4cc};
            float start = -90f; int index = 0;
            for (Long value : values) { float sweep = total == 0 ? 0 : value * 360f / total; paint.setColor(colors[index++ % colors.length]); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(size * 0.22f); canvas.drawArc(bounds, start, sweep, false, paint); start += sweep; }
        }
    }
    private void addCardLabel(String heading, String detail) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(18), dp(14), dp(18), dp(14));
        card.setBackground(rounded(0xffffffff, dp(18), 0xffe7ebe3));
        TextView title = new TextView(this); title.setText(heading); title.setTextSize(16); title.setTextColor(0xff294a37); title.setTypeface(null, Typeface.BOLD);
        TextView description = new TextView(this); description.setText(detail); description.setTextSize(13); description.setTextColor(0xff727b72); description.setPadding(0, dp(5), 0, 0);
        card.addView(title); card.addView(description);
        LinearLayout.LayoutParams params = matchWrap(); params.setMargins(0, dp(8), 0, dp(8)); homeContent.addView(card, params);
    }

    private void styleButton(Button button, int background, int foreground) {
        button.setTextColor(foreground); button.setAllCaps(false); button.setTextSize(15);
        button.setBackground(rounded(background, dp(14), background));
    }

    private GradientDrawable rounded(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(radius);
        if (fill != stroke) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

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
            recordButton.setText("■"); if (chatStatus != null) chatStatus.setText("녹음 중… 최대 2분까지 가능합니다.");
        } catch (Exception error) { releaseRecorder(); showMessage("녹음을 시작하지 못했습니다. 마이크 권한을 확인하세요."); }
    }

    private void stopRecording() {
        recording = false;
        mainHandler.removeCallbacks(recordingLimit);
        recordButton.setEnabled(false); recordButton.setText("…");
        try { recorder.stop(); } catch (RuntimeException error) { releaseRecorder(); recordButton.setEnabled(true); recordButton.setText("🎙"); if (chatStatus != null) chatStatus.setText("녹음이 너무 짧아요. 다시 녹음해 주세요."); return; }
        releaseRecorder();
        File audio = recordingFile;
        io.execute(() -> {
            try {
                String text = transcription.transcribe(audio);
                runOnUiThread(() -> {
                    if (chatInput != null) { chatInput.setText(text); chatInput.setSelection(text.length()); }
                    if (chatStatus != null) chatStatus.setText("인식했어요. 내용을 확인하고 보내기를 눌러 주세요.");
                });
            } catch (Exception error) { runOnUiThread(() -> { if (chatStatus != null) chatStatus.setText("음성을 변환하지 못했어요. 네트워크 연결을 확인하고 다시 시도해 주세요."); }); }
            finally { if (audio != null) audio.delete(); recordingFile = null; runOnUiThread(() -> { if (recordButton != null) { recordButton.setEnabled(true); recordButton.setText("🎙"); } }); }
        });
    }

    private boolean canRerenderAfterRefresh() {
        if (homeContent == null || recording || chatBusy) return false;
        return !("mypage".equals(selectedTab) && editingProfile); // 프로필 입력 중에는 건드리지 않음
    }

    private void refreshLedgerEntries() {
        io.execute(() -> {
            try {
                List<LedgerEntry> entries = supabase.fetchLedgerEntries();
                runOnUiThread(() -> {
                    ledgerEntries = entries;
                    ledgerLoadError = "";
                    if (canRerenderAfterRefresh()) showTab(selectedTab);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    ledgerEntries = new ArrayList<>();
                    ledgerLoadError = "거래 내역을 불러오지 못했어요. 네트워크 연결을 확인해 주세요.";
                    if (canRerenderAfterRefresh()) showTab(selectedTab);
                });
            }
        });
    }

    private String formatAmount(long amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount);
    }

    private void releaseRecorder() {
        if (recorder != null) { recorder.release(); recorder = null; }
    }

    private void buildRoot() {
        homeContent = null;
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(22), dp(24), dp(22), dp(24)); root.setBackgroundColor(0xfffffcf7);
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
    }

    private LinearLayout row() { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(android.view.Gravity.CENTER_VERTICAL); root.addView(row); return row; }
    private LinearLayout currentContent() { return homeContent == null ? root : homeContent; }
    private TextView addText(String value, int size, int color, boolean bold) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); if (bold) view.setTypeface(null, Typeface.BOLD); view.setPadding(0, dp(8), 0, dp(8)); currentContent().addView(view); return view; }
    private EditText addInput(String hint, boolean password) { EditText input = new EditText(this); input.setSingleLine(!hint.startsWith("변환")); input.setHint(hint); if (password) input.setInputType(129); else if (hint.equals("이메일")) input.setInputType(33); input.setPadding(dp(14), dp(10), dp(14), dp(10)); input.setBackground(rounded(0xffffffff, dp(14), 0xffdfe5dc)); currentContent().addView(input, new LinearLayout.LayoutParams(-1, -2)); return input; }
    private void addButton(String label, View.OnClickListener action) { Button button = new Button(this); button.setText(label); styleButton(button, 0xffdce8dc, 0xff254b37); button.setOnClickListener(action); LinearLayout.LayoutParams params = matchWrap(); params.setMargins(0, dp(6), 0, dp(8)); currentContent().addView(button, params); }
    private void showMessage(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void setBusy(boolean busy) { if (status != null) status.setText(busy ? "처리 중…" : ""); }

    /** 은행 자동 입력 화면에서 돌아오면 방금 저장된 거래가 바로 보이도록 다시 불러옵니다. */
    @Override protected void onResume() {
        super.onResume();
        if (resumedOnce) refreshLedgerEntries();
        resumedOnce = true;
    }

    /** 화면이 켜져 있는 동안 알림으로 거래가 자동 저장되면 바로 다시 불러옵니다. */
    @Override protected void onStart() {
        super.onStart();
        BankSyncStore.registerChangeListener(this, bankChangeListener);
    }

    @Override protected void onStop() {
        BankSyncStore.unregisterChangeListener(this, bankChangeListener);
        super.onStop();
    }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacks(recordingLimit);
        if (recordingFile != null) recordingFile.delete();
        io.shutdownNow(); releaseRecorder(); super.onDestroy();
    }
}
