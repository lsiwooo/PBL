package com.moa.pbl.analysis;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 또래 비교 결과 카드. 다른 파일에 의존하지 않도록 필요한 뷰 조각을 여기 모았다. */
public final class PeerViews {

    private static final int TEXT = 0xff172642;
    private static final int MUTED = 0xff7e8798;
    private static final int GREEN_TEXT = 0xff294a37;
    private static final int MINE = 0xff1b4fa3;
    private static final int PEER = 0xffa7b4cc;
    private static final int OVER = 0xffa23f35;
    private static final int UNDER = 0xff397458;

    private PeerViews() {
    }

    public static LinearLayout comparisonCard(Context context, PeerComparison.Result result) {
        LinearLayout card = AnalysisViews.card(context);
        card.addView(text(context, result.groupLabel + " " + result.peerCount + "명과 비교했어요", 16, TEXT, true));
        card.addView(text(context, "※ 비교 그룹은 테스트용 가상 사용자 기준이에요.", 12, MUTED, false));

        long maxIncome = Math.max(result.myIncome, result.peerAvgIncome);
        card.addView(text(context, "월 수입", 14, TEXT, true));
        card.addView(compareRow(context, "나", result.myIncome, maxIncome, MINE));
        card.addView(compareRow(context, "또래", result.peerAvgIncome, maxIncome, PEER));

        long maxExpense = Math.max(result.myExpense, result.peerAvgExpense);
        card.addView(text(context, "월 지출", 14, TEXT, true));
        card.addView(compareRow(context, "나", result.myExpense, maxExpense, MINE));
        card.addView(compareRow(context, "또래", result.peerAvgExpense, maxExpense, PEER));

        card.addView(categoryLine(context, "항목", "나", "또래", "차이", MUTED, MUTED));
        for (String category : PeerDataGenerator.CATEGORIES) {
            long mine = result.myCategories.get(category);
            long peer = result.peerAvgCategories.get(category);
            Integer diff = PeerComparison.percentDiff(mine, peer);
            String diffText = diff == null ? "-" : (diff > 0 ? "+" : "") + diff + "%";
            int diffColor = diff == null ? MUTED : diff >= 20 ? OVER : diff <= -20 ? UNDER : TEXT;
            card.addView(categoryLine(context, category, PeerComparison.man(mine), PeerComparison.man(peer),
                    diffText, TEXT, diffColor));
        }
        return card;
    }

    public static LinearLayout feedbackCard(Context context, PeerComparison.Result result) {
        LinearLayout card = AnalysisViews.card(context);
        card.addView(text(context, "또래 비교 피드백", 16, GREEN_TEXT, true));
        for (String line : result.feedback) card.addView(text(context, "• " + line, 14, GREEN_TEXT, false));
        return card;
    }

    /** "나 ▬▬▬▬ 123,000원" 형태의 가로 막대 한 줄 */
    private static View compareRow(Context context, String label, long value, long max, int color) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 3), 0, dp(context, 3));
        row.addView(text(context, label, 13, MUTED, false), new LinearLayout.LayoutParams(dp(context, 40), -2));

        // weightSum=1인 트랙 안에 비율만큼 weight를 준 막대를 넣어 길이를 표현한다
        LinearLayout track = new LinearLayout(context);
        track.setWeightSum(1f);
        float fraction = max <= 0 ? 0f : Math.max(0.02f, Math.min(1f, value / (float) max));
        View bar = new View(context);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, 4));
        bar.setBackground(shape);
        track.addView(bar, new LinearLayout.LayoutParams(0, -1, fraction));
        row.addView(track, new LinearLayout.LayoutParams(0, dp(context, 14), 1));

        TextView amount = text(context, PeerComparison.won(value), 13, TEXT, true);
        amount.setGravity(Gravity.END);
        row.addView(amount, new LinearLayout.LayoutParams(dp(context, 104), -2));
        return row;
    }

    private static View categoryLine(Context context, String label, String mine, String peer, String diff,
                                     int color, int diffColor) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(context, label, 13, color, false), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(cell(context, mine, color), new LinearLayout.LayoutParams(dp(context, 64), -2));
        row.addView(cell(context, peer, color), new LinearLayout.LayoutParams(dp(context, 64), -2));
        row.addView(cell(context, diff, diffColor), new LinearLayout.LayoutParams(dp(context, 56), -2));
        return row;
    }

    private static TextView cell(Context context, String value, int color) {
        TextView view = text(context, value, 13, color, false);
        view.setGravity(Gravity.END);
        return view;
    }

    private static TextView text(Context context, String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(context, 4), 0, dp(context, 4));
        return view;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
