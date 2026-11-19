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

/** 소비 화면의 카드들. 화면 코드만 있고 계산은 PeerComparison·MonthComparison이 한다. */
public final class ReportViews {

    private static final int TEXT = 0xff172642;
    private static final int MUTED = 0xff7e8798;
    private static final int GREEN_TEXT = 0xff294a37;
    private static final int BLUE = 0xff1b4fa3;
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

    // ───────── 묶음별 지출 도넛 ─────────

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
        card.addView(text(context, "※ 식비=식비·카페, 생활=생활·의료, 쇼핑·여가=쇼핑·문화, 교육·기타=교육·기타", 11, MUTED, false));
        return card;
    }

    // ───────── 지난달과 비교 (띠그래프) ─────────

    public static View monthChangeCard(Context context, MonthComparison.Result result) {
        String now = AnalysisMonth.shortLabel(result.current);
        String before = AnalysisMonth.shortLabel(result.previous);

        LinearLayout card = AnalysisViews.card(context);
        card.addView(text(context, before + "과 " + now + " 소비 구성 비교", 17, TEXT, true));
        for (String line : result.summary) card.addView(text(context, "• " + line, 14, GREEN_TEXT, false));
        card.addView(categoryLegend(context, false));

        card.addView(bandRow(context, before, MonthComparison.won(result.previousTotal), segments(result.previousAmounts, false)));
        card.addView(bandRow(context, now, MonthComparison.won(result.currentTotal), segments(result.currentAmounts, false)));

        if (result.currentTotal > 0L || result.previousTotal > 0L) {
            for (MonthComparison.Row row : result.rows) {
                if (row.current == 0L && row.previous == 0L) continue;
                LinearLayout line = new LinearLayout(context);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(0, dp(context, 4), 0, 0);
                line.addView(dot(context, CategoryPalette.colorOf(row.category)), dotParams(context));
                line.addView(text(context, row.category, 13, TEXT, true), new LinearLayout.LayoutParams(dp(context, 70), -2));
                line.addView(text(context, PeerComparison.percent(row.previousShare) + " → " + PeerComparison.percent(row.currentShare)
                        + " · " + MonthComparison.signedWon(row.current - row.previous), 12, MUTED, false),
                        new LinearLayout.LayoutParams(0, -2, 1));
                double change = row.shareChange();
                int color = change >= 3 ? UP : change <= -3 ? DOWN : MUTED;
                line.addView(text(context, MonthComparison.pp(change), 12, color, true));
                card.addView(line);
            }
        }
        card.addView(text(context, "※ 띠 길이는 그 달 총지출 대비 비율이에요.", 11, MUTED, false));
        return card;
    }

    // ───────── 또래와 비교 (띠그래프 + 설명) ─────────

    public static View compositionCard(Context context, YearMonth month, PeerComparison.Result result) {
        LinearLayout card = AnalysisViews.card(context);
        card.addView(text(context, AnalysisMonth.shortLabel(month) + " 또래와 소비 구성 비교", 17, TEXT, true));
        card.addView(text(context, "수입을 어디에 어떤 비율로 썼는지 비교해요"
                + (result.widened ? " (연령대 정보가 부족해 기준을 넓혔어요)" : ""), 13, MUTED, false));
        card.addView(categoryLegend(context, true));

        String mineCaption = result.mine.empty ? "기록 없음" : "수입 " + MonthComparison.won(result.mine.income);
        card.addView(bandRow(context, "나", mineCaption, segments(result.mine.amounts, true)));
        for (PeerComparison.Group group : result.groups) {
            card.addView(bandRow(context, group.title,
                    group.detail + " " + group.count + "명 · 평균 수입 " + MonthComparison.won(group.composition.income),
                    segments(group.composition.amounts, true)));
        }

        LinearLayout explain = new LinearLayout(context);
        explain.setOrientation(LinearLayout.VERTICAL);
        explain.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        explain.setBackground(rounded(context, 0xfff2f7f3, 14, 0xfff2f7f3));
        explain.addView(text(context, "비교 설명", 14, GREEN_TEXT, true));
        for (String line : result.feedback) explain.addView(text(context, "• " + line, 13, GREEN_TEXT, false));
        LinearLayout.LayoutParams explainParams = new LinearLayout.LayoutParams(-1, -2);
        explainParams.setMargins(0, dp(context, 12), 0, 0);
        card.addView(explain, explainParams);

        card.addView(text(context, "※ 띠 길이는 수입 대비 비율이에요 (수입보다 많이 쓴 달은 지출 대비). 비교 그룹은 테스트용 가상 사용자예요.", 11, MUTED, false));

        return card;
    }

    // ───────── 공통 조각 ─────────

    /** 이름·설명 한 줄 + 띠그래프 한 줄 */
    private static View bandRow(Context context, String title, String caption, List<StackedBarView.Segment> segments) {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(context, 8), 0, dp(context, 2));

        LinearLayout head = new LinearLayout(context);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(context, title, 14, TEXT, true), new LinearLayout.LayoutParams(-2, -2));
        TextView sub = text(context, caption, 11, MUTED, false);
        sub.setGravity(Gravity.END);
        head.addView(sub, new LinearLayout.LayoutParams(0, -2, 1));
        block.addView(head);

        StackedBarView bar = new StackedBarView(context);
        bar.setSegments(segments);
        block.addView(bar, new LinearLayout.LayoutParams(-1, dp(context, 22)));
        return block;
    }

    private static List<StackedBarView.Segment> segments(Map<String, Long> amounts, boolean withSaving) {
        List<StackedBarView.Segment> list = new ArrayList<>();
        if (withSaving) {
            Long saving = amounts.get(CategoryPalette.SAVING);
            list.add(new StackedBarView.Segment(saving == null ? 0L : saving, CategoryPalette.colorOf(CategoryPalette.SAVING)));
        }
        for (String category : CategoryPalette.EXPENSE_CATEGORIES) {
            Long value = amounts.get(category);
            list.add(new StackedBarView.Segment(value == null ? 0L : value, CategoryPalette.colorOf(category)));
        }
        return list;
    }

    /** 색 범례 (한 줄에 3개씩) */
    private static View categoryLegend(Context context, boolean withSaving) {
        List<String> names = new ArrayList<>();
        if (withSaving) names.add(CategoryPalette.SAVING);
        Collections.addAll(names, CategoryPalette.EXPENSE_CATEGORIES);

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(context, 6), 0, dp(context, 2));
        LinearLayout row = null;
        for (int i = 0; i < names.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(context);
                row.setGravity(Gravity.CENTER_VERTICAL);
                box.addView(row);
            }
            LinearLayout item = new LinearLayout(context);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.addView(dot(context, CategoryPalette.colorOf(names.get(i))), dotParams(context));
            item.addView(text(context, names.get(i), 12, MUTED, false));
            row.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        if (row != null) for (int i = names.size() % 3; i > 0 && i < 3; i++) row.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));
        return box;
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
        params.setMargins(0, 0, dp(context, 6), 0);
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
