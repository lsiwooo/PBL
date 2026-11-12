package com.moa.pbl.analysis;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 소비 피드백 화면의 카드들. 화면 코드만 있고 계산은 PeerComparison·MonthComparison이 한다. */
public final class ReportViews {

    public interface OnGenderPicked {
        void onPicked(String gender);
    }

    private static final int TEXT = 0xff172642;
    private static final int MUTED = 0xff7e8798;
    private static final int GREEN_TEXT = 0xff294a37;
    private static final int BLUE = 0xff1b4fa3;
    private static final int PEER_BAR = 0xffc9d1de;
    private static final int PREVIOUS_BAR = 0xffc9d1de;
    private static final int UP = 0xffa23f35;
    private static final int DOWN = 0xff397458;

    private ReportViews() {
    }

    // ───────── 월 선택 ─────────

    public static View monthSelector(Context context, YearMonth month, boolean canGoNext,
                                     Runnable onPrevious, Runnable onNext) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));
        row.setBackground(rounded(context, 0xffffffff, 18, 0xffe7e8e8));

        TextView previous = arrow(context, "‹", true);
        previous.setOnClickListener(v -> onPrevious.run());
        row.addView(previous, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 40)));

        TextView label = text(context, AnalysisMonth.label(month), 17, TEXT, true);
        label.setGravity(Gravity.CENTER);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        TextView next = arrow(context, "›", canGoNext);
        if (canGoNext) next.setOnClickListener(v -> onNext.run());
        row.addView(next, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 40)));
        return row;
    }

    // ───────── 1. 카테고리별 지출 도넛 ─────────

    public static View donutCard(Context context, YearMonth month, Map<String, Long> rawCategories) {
        Map<String, Long> categories = CategoryPalette.normalize(rawCategories);
        long total = CategoryPalette.sum(categories);

        LinearLayout card = AnalysisViews.card(context);
        card.addView(text(context, AnalysisMonth.shortLabel(month) + " 카테고리별 지출", 17, TEXT, true));

        List<DonutChartView.Slice> slices = new ArrayList<>();
        for (Map.Entry<String, Long> entry : categories.entrySet())
            if (entry.getValue() > 0L)
                slices.add(new DonutChartView.Slice(entry.getKey(), entry.getValue(), CategoryPalette.colorOf(entry.getKey())));
        Collections.sort(slices, (a, b) -> Long.compare(b.value, a.value));

        DonutChartView donut = new DonutChartView(context);
        donut.setData(slices, "총지출", total == 0L ? "기록 없음" : MonthComparison.won(total));
        LinearLayout.LayoutParams donutParams = new LinearLayout.LayoutParams(dp(context, 200), dp(context, 200));
        donutParams.gravity = Gravity.CENTER_HORIZONTAL;
        donutParams.setMargins(0, dp(context, 12), 0, dp(context, 12));
        card.addView(donut, donutParams);

        if (slices.isEmpty()) {
            card.addView(text(context, "이 달에는 지출 기록이 없어요. 거래를 기록하면 어디에 얼마나 썼는지 한눈에 보여드려요.", 13, MUTED, false));
            return card;
        }
        for (DonutChartView.Slice slice : slices) {
            LinearLayout line = new LinearLayout(context);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(dot(context, slice.color), dotParams(context));
            line.addView(text(context, slice.label, 14, TEXT, false), new LinearLayout.LayoutParams(0, -2, 1));
            line.addView(text(context, MonthComparison.won(slice.value), 14, TEXT, true));
            TextView share = text(context, PeerComparison.percent(slice.value * 100.0 / total), 13, MUTED, false);
            share.setGravity(Gravity.END);
            line.addView(share, new LinearLayout.LayoutParams(dp(context, 64), -2));
            card.addView(line);
        }
        return card;
    }

    // ───────── 2. 지난달과 비교 ─────────

    public static View monthChangeCard(Context context, MonthComparison.Result result) {
        String now = AnalysisMonth.shortLabel(result.current);
        String before = AnalysisMonth.shortLabel(result.previous);

        LinearLayout card = AnalysisViews.card(context);
        card.addView(text(context, before + "과 " + now + " 비교", 17, TEXT, true));
        for (String line : result.summary) card.addView(text(context, "• " + line, 14, GREEN_TEXT, false));
        card.addView(legend(context, before, PREVIOUS_BAR, now, BLUE));

        long max = 1L;
        for (MonthComparison.Row row : result.rows) max = Math.max(max, Math.max(row.current, row.previous));

        for (MonthComparison.Row row : result.rows) {
            LinearLayout block = new LinearLayout(context);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(0, dp(context, 8), 0, dp(context, 2));

            LinearLayout head = new LinearLayout(context);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(dot(context, CategoryPalette.colorOf(row.category)), dotParams(context));
            head.addView(text(context, row.category, 14, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
            double change = row.shareChange();
            int changeColor = change >= 3 ? UP : change <= -3 ? DOWN : MUTED;
            head.addView(text(context, MonthComparison.pp(change), 13, changeColor, true));
            block.addView(head);

            block.addView(bar(context, row.previous, max, PREVIOUS_BAR));
            block.addView(bar(context, row.current, max, CategoryPalette.colorOf(row.category)));
            block.addView(text(context,
                    before + " " + PeerComparison.percent(row.previousShare) + " → " + now + " "
                            + PeerComparison.percent(row.currentShare) + " · " + MonthComparison.signedWon(row.current - row.previous),
                    12, MUTED, false));
            card.addView(block);
        }
        card.addView(text(context, "※ 비율은 그 달 총지출 대비예요.", 12, MUTED, false));
        return card;
    }

    // ───────── 3. 또래와 소비 구성 비교 ─────────

    public static View genderPickerCard(Context context, OnGenderPicked onPicked) {
        LinearLayout card = AnalysisViews.card(context);
        card.addView(text(context, "또래와 비교하려면 성별을 골라주세요", 17, TEXT, true));
        card.addView(text(context, "같은 성별·지역·연령대 사용자와 소비 구성을 비교해 드려요. 이 기기에만 저장돼요.", 13, MUTED, false));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setPadding(0, dp(context, 10), 0, 0);
        for (String gender : new String[]{GenderPreference.MALE, GenderPreference.FEMALE}) {
            TextView chip = text(context, gender, 15, BLUE, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, dp(context, 12), 0, dp(context, 12));
            chip.setBackground(rounded(context, 0xffeaf0fb, 14, 0xffeaf0fb));
            chip.setOnClickListener(v -> onPicked.onPicked(gender));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMargins(dp(context, 4), 0, dp(context, 4), 0);
            buttons.addView(chip, params);
        }
        card.addView(buttons);
        return card;
    }

    public static View compositionCard(Context context, YearMonth month, PeerComparison.Result result, Runnable onChangeGender) {
        LinearLayout card = AnalysisViews.card(context);
        card.addView(text(context, AnalysisMonth.shortLabel(month) + " 또래와 소비 구성 비교", 17, TEXT, true));
        card.addView(text(context, result.groupLabel + " " + result.peerCount + "명 평균과 비교했어요"
                + (result.widened ? " (해당 그룹 인원이 적어 기준을 넓혔어요)" : ""), 13, MUTED, false));
        card.addView(legend(context, "나", BLUE, "또래 평균", PEER_BAR));

        double max = 1.0;
        for (String key : result.peer.shares.keySet())
            max = Math.max(max, Math.max(result.mine.shares.get(key), result.peer.shares.get(key)));

        for (String key : result.peer.shares.keySet()) {
            double mine = result.mine.shares.get(key);
            double peer = result.peer.shares.get(key);
            LinearLayout block = new LinearLayout(context);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(0, dp(context, 8), 0, dp(context, 2));

            LinearLayout head = new LinearLayout(context);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(dot(context, CategoryPalette.colorOf(key)), dotParams(context));
            head.addView(text(context, key, 14, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
            head.addView(text(context, "나 " + PeerComparison.percent(mine) + " · 또래 " + PeerComparison.percent(peer), 12, MUTED, false));
            block.addView(head);

            block.addView(barRatio(context, (float) (mine / max), CategoryPalette.colorOf(key)));
            block.addView(barRatio(context, (float) (peer / max), PEER_BAR));
            card.addView(block);
        }

        card.addView(text(context, "※ 비율은 수입 대비예요 (수입보다 많이 쓴 달은 지출 대비). 또래는 테스트용 가상 사용자 기준이에요.", 12, MUTED, false));

        LinearLayout feedback = new LinearLayout(context);
        feedback.setOrientation(LinearLayout.VERTICAL);
        feedback.setPadding(0, dp(context, 8), 0, 0);
        for (String line : result.feedback) feedback.addView(text(context, "• " + line, 14, GREEN_TEXT, false));
        card.addView(feedback);

        TextView change = text(context, "성별 다시 선택 ›", 13, 0xff597ab5, true);
        change.setOnClickListener(v -> onChangeGender.run());
        card.addView(change);
        return card;
    }

    // ───────── 공통 조각 ─────────

    private static View legend(Context context, String firstLabel, int firstColor, String secondLabel, int secondColor) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 6), 0, 0);
        row.addView(dot(context, firstColor), dotParams(context));
        row.addView(text(context, firstLabel, 12, MUTED, false));
        LinearLayout.LayoutParams gap = dotParams(context);
        gap.setMargins(dp(context, 14), 0, dp(context, 6), 0);
        row.addView(dot(context, secondColor), gap);
        row.addView(text(context, secondLabel, 12, MUTED, false));
        return row;
    }

    private static View bar(Context context, long value, long max, int color) {
        return barRatio(context, max <= 0L ? 0f : value / (float) max, color);
    }

    /** weightSum=1인 트랙 안에 비율만큼 weight를 준 막대 */
    private static View barRatio(Context context, float fraction, int color) {
        LinearLayout track = new LinearLayout(context);
        track.setWeightSum(1f);
        track.setBackground(rounded(context, 0xfff3f4f6, 4, 0xfff3f4f6));
        View fill = new View(context);
        fill.setBackground(rounded(context, color, 4, color));
        float clamped = fraction <= 0f ? 0f : Math.max(0.01f, Math.min(1f, fraction));
        track.addView(fill, new LinearLayout.LayoutParams(0, -1, clamped));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(context, 10));
        params.setMargins(0, dp(context, 3), 0, 0);
        track.setLayoutParams(params);
        return track;
    }

    private static View dot(Context context, int color) {
        View dot = new View(context);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        dot.setBackground(circle);
        return dot;
    }

    private static LinearLayout.LayoutParams dotParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, 10), dp(context, 10));
        params.setMargins(0, 0, dp(context, 8), 0);
        return params;
    }

    private static TextView arrow(Context context, String symbol, boolean enabled) {
        TextView view = text(context, symbol, 26, enabled ? BLUE : 0xffd0d4da, true);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private static TextView text(Context context, String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(context, 3), 0, dp(context, 3));
        return view;
    }

    private static GradientDrawable rounded(Context context, int fill, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (fill != stroke) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
