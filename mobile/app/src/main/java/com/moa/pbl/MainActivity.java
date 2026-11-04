package com.moa.pbl;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import com.moa.pbl.data.LedgerEntry;
import com.moa.pbl.data.LedgerParserRepository;
import com.moa.pbl.data.ParsedTransaction;
import com.moa.pbl.data.SupabaseRepository;
import com.moa.pbl.data.TranscriptionRepository;
import com.moa.pbl.analysis.AnalysisViews;
import com.moa.pbl.analysis.PeerComparison;
import com.moa.pbl.analysis.PeerDataGenerator;
import com.moa.pbl.analysis.PeerProfile;
import com.moa.pbl.analysis.ProfileFormView;
import com.moa.pbl.analysis.ProfileStore;
import java.io.File;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
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
    private LinearLayout root;
    private EditText emailField;
    private EditText passwordField;
    private EditText scriptField;
    private EditText ledgerTextField;
    private TextView status;
    private TextView records;
    private TextView ledgerRecords;
    private Button recordButton;
    private LinearLayout homeContent;
    private final List<TextView> navigationItems = new ArrayList<>();
    private List<ParsedTransaction> parsedTransactions = new ArrayList<>();
    private String selectedTab = "home";
    private String pendingTranscript = "";
    private String ledgerDraft = "";
    private boolean recordingForLedger;
    private boolean editingProfile;
    private boolean logoutInProgress;
    private List<LedgerEntry> ledgerEntries = new ArrayList<>();
    private String ledgerLoadError = "";
    private YearMonth selectedMonth = YearMonth.now();
    private File recordingFile;
    private android.media.MediaRecorder recorder;
    private boolean recording;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable recordingLimit = this::stopRecording;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supabase = new SupabaseRepository(this);
        transcription = new TranscriptionRepository();
        ledgerParser = new LedgerParserRepository();
        microphonePermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startRecording(); else showMessage("음성 녹음을 위해 마이크 권한이 필요합니다.");
        });
        if (supabase.isConfigured() && supabase.hasSession()) showJournal(); else showLogin();
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
        TextView mark = new TextView(this);
        mark.setText("₩"); mark.setTextSize(22); mark.setTextColor(0xffffffff); mark.setGravity(android.view.Gravity.CENTER);
        mark.setBackground(rounded(0xff1b4fa3, dp(16), 0xff1b4fa3));
        brand.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView brandName = new TextView(this); brandName.setText("자연어 가계부"); brandName.setTextSize(18);
        brandName.setTextColor(0xff172642); brandName.setTypeface(null, Typeface.BOLD);
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
        if (!supabase.isConfigured()) status.setText("설정 필요: mobile/local.properties에 Supabase URL과 Anon Key를 입력하세요.");
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
        shell.setPadding(dp(20), dp(10), dp(20), 0);
        shell.setBackgroundColor(0xfffffcf7);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("자연어 가계부"); title.setTextSize(22); title.setTextColor(0xff1b4fa3);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView myPage = new TextView(this);
        myPage.setText("내 정보"); myPage.setTextSize(14); myPage.setTextColor(0xff1b4fa3);
        myPage.setOnClickListener(v -> showTab("mypage"));
        header.addView(myPage);
        shell.addView(header, new LinearLayout.LayoutParams(-1, dp(54)));

        ScrollView scroll = new ScrollView(this);
        homeContent = new LinearLayout(this);
        homeContent.setOrientation(LinearLayout.VERTICAL);
        homeContent.setPadding(0, dp(8), 0, dp(18));
        scroll.addView(homeContent);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(android.view.Gravity.CENTER);
        navigation.setPadding(0, dp(8), 0, dp(10));
        navigation.setBackgroundColor(0xffffffff);
        navigationItems.clear();
        addNavigationItem(navigation, "home", "⌂", "홈");
        addNavigationItem(navigation, "stats", "▥", "통계");
        addNavigationItem(navigation, "record", "▤", "기록");
        addNavigationItem(navigation, "feedback", "♧", "AI 피드백");
        addNavigationItem(navigation, "mypage", "◎", "마이페이지");
        shell.addView(navigation, new LinearLayout.LayoutParams(-1, dp(68)));
        setContentView(shell);
        showTab("home");
        refreshLedgerEntries();
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
        boolean lightStatusBar = !"home".equals(tab);
        getWindow().setStatusBarColor(lightStatusBar ? 0xfffffcf7 : 0xff16468f);
        getWindow().getDecorView().setSystemUiVisibility(lightStatusBar ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0);
        String[] tabs = {"home", "stats", "record", "feedback", "mypage"};
        for (int index = 0; index < tabs.length; index++) {
            navigationItems.get(index).setTextColor(tabs[index].equals(tab) ? 0xff1b4fa3 : 0xff929990);
            navigationItems.get(index).setTypeface(null, tabs[index].equals(tab) ? Typeface.BOLD : Typeface.NORMAL);
        }
        homeContent.removeAllViews();
        status = null;
        if ("home".equals(tab)) showHomeTab();
        else if ("stats".equals(tab)) showStatisticsTab();
        else if ("record".equals(tab)) showRecordTab();
        else if ("feedback".equals(tab)) showAnalysisTab();
        else showMyPageTab();
    }

    private void showRecordTab() {
        addText("자연어로 기록하기", 27, 0xff172642, false);
        addText("평소 말투 그대로 입력해 보세요", 15, 0xff7e8798, false);
        addCardLabel("무엇을 사고, 얼마를 썼는지 알려주세요.", "수입도 같은 방식으로 기록할 수 있어요.");
        recordButton = new Button(this);
        recordButton.setText("●  음성으로 입력"); styleButton(recordButton, 0xff1b4fa3, 0xffffffff);
        recordButton.setOnClickListener(v -> { recordingForLedger = true; toggleRecording(); });
        homeContent.addView(recordButton, matchWrap());
        ledgerTextField = addInput("예: 오늘 점심 김치찌개 9,000원 먹었어", false);
        ledgerTextField.setSingleLine(false);
        ledgerTextField.setMinLines(5);
        ledgerTextField.setText(ledgerDraft);
        addButton("✧  AI로 분석하기", v -> analyzeLedger());
        status = addText("", 13, 0xff42644b, false);
        addText("이렇게 말해 보세요", 18, 0xff172642, false);
        addExample("어제 카페에서 4,500원 썼어");
        addExample("이번 달 알바비 65만원 들어왔어");
        addExample("친구 생일 선물로 3만원 결제했어");
        addCardLabel("AI가 문장에서 금액·날짜·카테고리를 찾아드려요.", "분석 후 결과를 확인하고 직접 수정할 수 있어요.");
        if (!parsedTransactions.isEmpty()) showParsedTransactions();
        addButton("원문 스크립트도 날짜별로 저장", v -> saveScript());
        scriptField = addInput("저장할 전체 스크립트 (선택)", false);
        scriptField.setSingleLine(false); scriptField.setMinLines(2); scriptField.setText(pendingTranscript);
        addText("저장한 음성 스크립트", 18, 0xff172642, true);
        records = addText("불러오는 중…", 14, 0xff454d45, false);
        refreshEntries();
    }

    private void addExample(String example) {
        TextView chip = new TextView(this);
        chip.setText("▤   " + example); chip.setTextSize(14); chip.setTextColor(0xff66758e);
        chip.setPadding(dp(14), dp(14), dp(14), dp(14));
        chip.setBackground(rounded(0xffffffff, dp(14), 0xffe2e5eb));
        chip.setOnClickListener(v -> { ledgerTextField.setText(example); ledgerTextField.setSelection(example.length()); });
        LinearLayout.LayoutParams params = matchWrap(); params.setMargins(0, dp(4), 0, dp(4));
        homeContent.addView(chip, params);
    }

    private void showHomeTab() {
        String month = LocalDate.now().getYear() + "년 " + LocalDate.now().getMonthValue() + "월";
        long income = totalForMonth("income", YearMonth.now());
        long expenses = totalForMonth("expense", YearMonth.now());
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(22), dp(22), dp(22), dp(20));
        hero.setBackground(rounded(0xff16468f, dp(28), 0xff16468f));
        addTo(hero, "자연어 가계부", 18, 0xffffffff, true);
        addTo(hero, month + "  ⌄", 20, 0xffffffff, true);
        addTo(hero, "이번 달 남은 금액", 15, 0xffb6c9e7, false);
        addTo(hero, formatAmount(income - expenses) + "원", 39, 0xffffffff, true);
        LinearLayout totals = new LinearLayout(this);
        addTo(totals, "총수입\n" + formatAmount(income) + "원", 14, 0xffffffff, true, 1);
        addTo(totals, "총지출\n" + formatAmount(expenses) + "원", 14, 0xffffffff, true, 1);
        hero.addView(totals);
        LinearLayout.LayoutParams heroParams = matchWrap(); heroParams.setMargins(0, dp(8), 0, dp(18));
        homeContent.addView(hero, heroParams);

        LinearLayout prompt = clickableCard("✧", "챗봇 비서에게 말해 볼까요?", "오늘 쓴 돈 기록하기", 0xfffff5df, () -> showTab("record"));
        homeContent.addView(prompt, matchWrap());
        addText("한눈에 보기", 20, 0xff172642, false);
        LinearLayout quickRow = new LinearLayout(this);
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(0, dp(108), 1); quickParams.setMargins(0, 0, dp(6), 0);
        quickRow.addView(quickCard("월간 통계", "어디에 많이 썼을까요?", 0xffe8f0ed, "stats"), quickParams);
        LinearLayout.LayoutParams feedbackParams = new LinearLayout.LayoutParams(0, dp(108), 1); feedbackParams.setMargins(dp(6), 0, 0, 0);
        quickRow.addView(quickCard("AI 소비 피드백", "이번 달 습관을 확인해요", 0xffedf1fa, "feedback"), feedbackParams);
        homeContent.addView(quickRow);
        LinearLayout recentHeader = new LinearLayout(this); recentHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView recent = new TextView(this); recent.setText("최근 소비"); recent.setTextSize(20); recent.setTextColor(0xff172642);
        recentHeader.addView(recent, new LinearLayout.LayoutParams(0, -2, 1));
        TextView all = new TextView(this); all.setText("전체보기"); all.setTextColor(0xff597ab5); all.setOnClickListener(v -> showTab("stats")); recentHeader.addView(all);
        homeContent.addView(recentHeader);
        addRecentTransactions(5);
        if (!ledgerLoadError.isEmpty()) addText("Supabase 테이블 설정을 확인해 주세요: " + ledgerLoadError, 12, 0xffa23f35, false);
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
        addDonutCard(expenses);
        addText("거래 내역", 19, 0xff172642, true);
        addRecentTransactionsForMonth(selectedMonth);
    }

    private void showAnalysisTab() {
        addText("AI 소비 피드백", 27, 0xff172642, false);
        addText("데이터로 나의 소비 습관을 돌아봐요", 15, 0xff7e8798, false);
        YearMonth thisMonth = YearMonth.now();
        YearMonth lastMonth = thisMonth.minusMonths(1);
        long expenses = totalForMonth("expense", thisMonth);
        long income = totalForMonth("income", thisMonth);
        Map<String, Long> categories = categoryTotals(thisMonth);
        if (expenses == 0L) {
            addCardLabel("거래를 기록하면 소비 피드백이 생겨요", "가계부에 품목과 금액을 저장하면 월간 소비 습관을 볼 수 있습니다.");
            addButton("자연어로 기록하기", v -> showTab("record"));
            return;
        }
        String topCategory = "기타"; long topAmount = 0L;
        for (Map.Entry<String, Long> entry : categories.entrySet()) if (entry.getValue() > topAmount) { topCategory = entry.getKey(); topAmount = entry.getValue(); }
        int share = Math.round(topAmount * 100f / expenses);
        addCardLabel("이번 달 가장 큰 지출은 " + topCategory + PeerComparison.copula(topCategory), "이번 달 지출의 " + share + "%를 차지해요. 거래 내역을 살펴보고 다음 달 예산을 정해보세요.");
        addCardLabel("이번 달 지출", formatAmount(expenses) + "원 · 등록된 " + ledgerEntries.size() + "건을 기준으로 계산했어요.");

        // 전월 비교: 실제 가계부 기록으로 지난달과 비교
        addText("지난달과 비교", 19, 0xff172642, true);
        LinearLayout monthCard = AnalysisViews.card(this);
        for (String line : PeerComparison.monthOverMonth(expenses, totalForMonth("expense", lastMonth), categories, categoryTotals(lastMonth)))
            monthCard.addView(AnalysisViews.text(this, "• " + line, 14, 0xff294a37, false));
        homeContent.addView(monthCard, AnalysisViews.cardParams(this));

        // 또래 비교: 이번 달 실제 기록을 같은 성별·나이 테스트 사용자 평균과 비교
        addText("또래와 비교", 19, 0xff172642, true);
        PeerProfile profile = ProfileStore.load(this);
        if (profile == null) {
            addCardLabel("성별과 나이를 알려주세요", "같은 성별·나이 사용자와 이번 달 소비를 비교해 드려요.");
            homeContent.addView(new ProfileFormView(this, null, saved -> { ProfileStore.save(this, saved); showTab("feedback"); }), AnalysisViews.cardParams(this));
            return;
        }
        PeerComparison.Result result = PeerComparison.compare(income, expenses, categories, profile, PeerDataGenerator.getDefault());
        LinearLayout peerCard = AnalysisViews.card(this);
        peerCard.addView(AnalysisViews.text(this, profile.age + "세 " + profile.gender + " " + result.peerCount + "명과 비교했어요", 16, 0xff172642, true));
        peerCard.addView(AnalysisViews.text(this, "※ 또래 데이터는 테스트용 가상 사용자 100명 기준이에요.", 12, 0xff7e8798, false));
        long maxIncome = Math.max(result.myIncome, result.peerAvgIncome);
        peerCard.addView(AnalysisViews.text(this, "월 수입", 14, 0xff172642, true));
        peerCard.addView(AnalysisViews.compareRow(this, "나", result.myIncome, maxIncome, 0xff1b4fa3));
        peerCard.addView(AnalysisViews.compareRow(this, "또래", result.peerAvgIncome, maxIncome, 0xffa7b4cc));
        long maxExpense = Math.max(result.myExpense, result.peerAvgExpense);
        peerCard.addView(AnalysisViews.text(this, "월 지출", 14, 0xff172642, true));
        peerCard.addView(AnalysisViews.compareRow(this, "나", result.myExpense, maxExpense, 0xff1b4fa3));
        peerCard.addView(AnalysisViews.compareRow(this, "또래", result.peerAvgExpense, maxExpense, 0xffa7b4cc));
        peerCard.addView(AnalysisViews.categoryHeader(this));
        for (String category : PeerDataGenerator.CATEGORIES)
            peerCard.addView(AnalysisViews.categoryRow(this, category, result.myCategories.get(category), result.peerAvgCategories.get(category)));
        homeContent.addView(peerCard, AnalysisViews.cardParams(this));

        LinearLayout feedbackCard = AnalysisViews.card(this);
        feedbackCard.addView(AnalysisViews.text(this, "또래 비교 피드백", 16, 0xff294a37, true));
        for (String line : result.feedback) feedbackCard.addView(AnalysisViews.text(this, "• " + line, 14, 0xff294a37, false));
        homeContent.addView(feedbackCard, AnalysisViews.cardParams(this));

        TextView editProfile = AnalysisViews.text(this, "프로필 수정 ›", 14, 0xff597ab5, true);
        editProfile.setOnClickListener(v -> {
            editProfile.setVisibility(View.GONE);
            homeContent.addView(new ProfileFormView(this, profile, saved -> { ProfileStore.save(this, saved); showTab("feedback"); }), AnalysisViews.cardParams(this));
        });
        homeContent.addView(editProfile);
    }

    private void showMyPageTab() {
        addText("마이페이지", 27, 0xff172642, true);
        addText("계정과 소비 분석 프로필을 관리해요", 15, 0xff7e8798, false);

        LinearLayout accountCard = AnalysisViews.card(this);
        accountCard.addView(AnalysisViews.text(this, "계정", 14, 0xff597ab5, true));
        String accountEmail = supabase.currentAccountEmail();
        accountCard.addView(AnalysisViews.text(this, accountEmail.isEmpty() ? "로그인 상태" : accountEmail, 17, 0xff172642, true));
        accountCard.addView(AnalysisViews.text(this, "계정 인증과 세션은 Supabase에서 관리합니다.", 13, 0xff7e8798, false));
        homeContent.addView(accountCard, AnalysisViews.cardParams(this));

        PeerProfile profile = ProfileStore.load(this);
        LinearLayout profileCard = AnalysisViews.card(this);
        profileCard.addView(AnalysisViews.text(this, "소비 분석 프로필", 14, 0xff597ab5, true));
        if (profile == null) {
            profileCard.addView(AnalysisViews.text(this, "아직 등록된 프로필이 없습니다.", 17, 0xff172642, true));
            profileCard.addView(AnalysisViews.text(this, "프로필을 등록하면 또래 소비 비교에 사용할 수 있어요.", 13, 0xff7e8798, false));
        } else {
            profileCard.addView(AnalysisViews.text(this, profile.name, 20, 0xff172642, true));
            profileCard.addView(AnalysisViews.text(this, profile.age + "세 · " + profile.gender, 14, 0xff52617a, false));
            profileCard.addView(AnalysisViews.text(this, profile.school + " · " + profile.grade + "학년 · " + profile.region, 14, 0xff7e8798, false));
        }
        homeContent.addView(profileCard, AnalysisViews.cardParams(this));

        Button profileButton = new Button(this);
        profileButton.setText(editingProfile ? "프로필 수정 닫기" : (profile == null ? "프로필 등록" : "프로필 수정"));
        styleButton(profileButton, 0xffeaf0fb, 0xff1b4fa3);
        profileButton.setOnClickListener(v -> { editingProfile = !editingProfile; showTab("mypage"); });
        LinearLayout.LayoutParams profileButtonParams = matchWrap(); profileButtonParams.setMargins(0, dp(4), 0, dp(8));
        homeContent.addView(profileButton, profileButtonParams);
        if (editingProfile) {
            homeContent.addView(new ProfileFormView(this, profile, saved -> {
                ProfileStore.save(this, saved);
                editingProfile = false;
                showTab("mypage");
                showMessage("프로필을 저장했습니다.");
            }), AnalysisViews.cardParams(this));
        }

        Button logoutButton = new Button(this);
        logoutButton.setText("로그아웃"); styleButton(logoutButton, 0xffffffff, 0xffa23f35);
        logoutButton.setBackground(rounded(0xffffffff, dp(14), 0xffe5c7c3));
        logoutButton.setOnClickListener(v -> confirmLogout());
        LinearLayout.LayoutParams logoutParams = matchWrap(); logoutParams.setMargins(0, dp(18), 0, dp(12));
        homeContent.addView(logoutButton, logoutParams);
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
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(18), dp(16), dp(18), dp(16)); card.setBackground(rounded(0xffffffff, dp(24), 0xffe7e8e8));
        addTo(card, "카테고리별 지출", 19, 0xff172642, false);
        Map<String, Long> totals = categoryTotals(selectedMonth);
        if (totals.isEmpty()) addTo(card, "아직 이 달의 지출이 없습니다.", 14, 0xff7e8798, false);
        else {
            DonutView donut = new DonutView(this, new ArrayList<>(totals.values()), expenses);
            card.addView(donut, new LinearLayout.LayoutParams(-1, dp(220)));
            int[] colors = {0xff1b4fa3, 0xff6484bc, 0xffc7d9d0, 0xffe8d6a4, 0xffd9dce3, 0xffa7b4cc};
            int index = 0;
            for (Map.Entry<String, Long> entry : totals.entrySet()) {
                int share = expenses == 0 ? 0 : Math.round(entry.getValue() * 100f / expenses);
                addTo(card, "●   " + entry.getKey() + "     " + formatAmount(entry.getValue()) + "원     " + share + "%", 14, colors[index++ % colors.length], true);
            }
        }
        LinearLayout.LayoutParams params = matchWrap(); params.setMargins(0, 0, 0, dp(16)); homeContent.addView(card, params);
    }

    private void addRecentTransactions(int limit) {
        int shown = 0;
        for (LedgerEntry entry : ledgerEntries) {
            if (!entry.transactionType().equals("expense")) continue;
            addTransactionRow(entry);
            if (++shown >= limit) break;
        }
        if (shown == 0) addText(ledgerLoadError.isEmpty() ? "가계부에 저장된 거래가 없습니다." : "저장하려면 먼저 Supabase 스키마를 실행해 주세요.", 14, 0xff7e8798, false);
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
        boolean ledgerDestination = recordingForLedger;
        io.execute(() -> {
            try {
                String text = transcription.transcribe(audio);
                runOnUiThread(() -> {
                    if (ledgerDestination) {
                        ledgerDraft = text;
                        if (ledgerTextField != null) ledgerTextField.setText(text);
                        pendingTranscript = text;
                        if (scriptField != null) scriptField.setText(text);
                    } else {
                        pendingTranscript = text;
                        if (scriptField != null) scriptField.setText(text);
                    }
                    if (status != null) status.setText("음성 변환이 완료됐습니다. 내용을 확인하세요.");
                });
            } catch (Exception error) { runOnUiThread(() -> { if (status != null) status.setText(error.getMessage()); }); }
            finally { if (audio != null) audio.delete(); recordingFile = null; runOnUiThread(() -> { if (recordButton != null) { recordButton.setEnabled(true); recordButton.setText(recordingForLedger ? "●  음성으로 입력" : "●  음성으로 입력"); } }); }
        });
    }

    private void saveScript() {
        String text = scriptField.getText().toString().trim();
        if (text.isEmpty()) { status.setText("저장할 스크립트를 입력하세요."); return; }
        pendingTranscript = text;
        status.setText("저장 중…");
        io.execute(() -> {
            try { supabase.insertEntry(text); runOnUiThread(() -> { scriptField.setText(""); pendingTranscript = ""; status.setText("날짜별 음성 기록에 저장했습니다."); refreshEntries(); }); }
            catch (Exception error) { runOnUiThread(() -> status.setText(error.getMessage())); }
        });
    }

    private void analyzeLedger() {
        String transcript = ledgerTextField.getText().toString().trim();
        if (transcript.isEmpty()) { status.setText("먼저 말하거나 스크립트를 입력하세요."); return; }
        ledgerDraft = transcript;
        status.setText("항목을 정리하고 있어요…");
        io.execute(() -> {
            try {
                List<ParsedTransaction> result = ledgerParser.parse(transcript);
                runOnUiThread(() -> {
                    parsedTransactions = result;
                    status.setText(result.isEmpty() ? "금액이 포함된 거래를 찾지 못했어요. 스크립트를 확인해 주세요." : "항목과 금액을 확인한 뒤 저장하세요.");
                    showParsedTransactions();
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText(error.getMessage()));
            }
        });
    }

    private void showParsedTransactions() {
        if (homeContent == null || !"ledger".equals(selectedTab)) return;
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        for (ParsedTransaction transaction : parsedTransactions) {
            String label = transaction.transactionType().equals("income") ? "수입" : "지출";
            String row = transaction.itemName() + "  ·  " + formatAmount(transaction.amount()) + "원\n" + transaction.category() + " / " + label;
            TextView item = new TextView(this);
            item.setText(row); item.setTextSize(15); item.setTextColor(0xff34463a);
            item.setBackground(rounded(0xffffffff, dp(12), 0xffe7ebe3));
            item.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams params = matchWrap(); params.setMargins(0, dp(4), 0, dp(4));
            preview.addView(item, params);
        }
        homeContent.addView(preview);
        if (!parsedTransactions.isEmpty()) addButton("확인하고 가계부에 저장", v -> saveLedgerEntries());
    }

    private void saveLedgerEntries() {
        if (parsedTransactions.isEmpty()) return;
        status.setText("가계부에 저장 중…");
        List<ParsedTransaction> toSave = new ArrayList<>(parsedTransactions);
        io.execute(() -> {
            try {
                supabase.insertLedgerEntries(toSave);
                runOnUiThread(() -> {
                    parsedTransactions = new ArrayList<>();
                    ledgerDraft = "";
                    if (ledgerTextField != null) ledgerTextField.setText("");
                    status.setText("가계부에 저장했습니다.");
                    showTab("home");
                    refreshLedgerEntries();
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText(error.getMessage()));
            }
        });
    }

    private void refreshEntries() {
        TextView target = records;
        io.execute(() -> {
            try {
                List<ScriptEntry> entries = supabase.fetchEntries();
                runOnUiThread(() -> {
                    if (records != target || target == null) return;
                    StringBuilder text = new StringBuilder();
                    String lastDate = "";
                    for (ScriptEntry entry : entries) {
                        String date = entry.occurredOn();
                        if (!date.equals(lastDate)) text.append("\n").append(date).append("\n");
                        text.append("  ").append(entry.transcript()).append("\n\n");
                        lastDate = date;
                    }
                    records.setText(text.length() == 0 ? "아직 저장된 기록이 없습니다." : text.toString());
                });
            } catch (Exception error) { runOnUiThread(() -> { if (records == target && target != null) target.setText(error.getMessage()); }); }
        });
    }

    private void refreshLedgerEntries() {
        io.execute(() -> {
            try {
                List<LedgerEntry> entries = supabase.fetchLedgerEntries();
                runOnUiThread(() -> {
                    ledgerEntries = entries;
                    ledgerLoadError = "";
                    if (homeContent != null && !recording && !"record".equals(selectedTab)) showTab(selectedTab);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    ledgerEntries = new ArrayList<>();
                    ledgerLoadError = error.getMessage() == null ? "가계부를 불러오지 못했습니다." : error.getMessage();
                    if (homeContent != null && !recording && !"record".equals(selectedTab)) showTab(selectedTab);
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

    @Override protected void onDestroy() {
        mainHandler.removeCallbacks(recordingLimit);
        if (recordingFile != null) recordingFile.delete();
        io.shutdownNow(); releaseRecorder(); super.onDestroy();
    }
}
