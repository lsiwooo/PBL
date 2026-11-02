package com.moa.pbl.analysis;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 소비 패턴 진단 화면에서만 쓰는 뷰 조각.
 * 공용 UI 메서드를 건드리지 않으려고 따로 두었고, 색과 모서리는 기존 화면에 맞췄다.
 */
public final class AnalysisViews {

    private static final int TEXT = 0xff172642;
    private static final int MUTED = 0xff7e8798;
    private static final int OVER = 0xffa23f35;
    private static final int UNDER = 0xff397458;

    private AnalysisViews() {
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(Context context, int fill, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (fill != stroke) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    /** 기존 addDonutCard와 같은 모양의 흰 카드 */
    public static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 18), dp(context, 16), dp(context, 18), dp(context, 16));
        card.setBackground(rounded(context, 0xffffffff, 24, 0xffe7e8e8));
        return card;
    }

    public static LinearLayout.LayoutParams cardParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(context, 8), 0, dp(context, 8));
        return params;
    }

    public static TextView text(Context context, String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(context, 4), 0, dp(context, 4));
        return view;
    }

    /** "나 ▬▬▬▬ 123,000원" 형태의 가로 막대 한 줄 */
    public static View compareRow(Context context, String label, long value, long max, int color) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 3), 0, dp(context, 3));

        TextView name = text(context, label, 13, MUTED, false);
        row.addView(name, new LinearLayout.LayoutParams(dp(context, 40), -2));

        // weightSum=1인 트랙 안에 비율만큼 weight를 준 막대를 넣어 길이를 표현한다
        LinearLayout track = new LinearLayout(context);
        track.setWeightSum(1f);
        float fraction = max <= 0 ? 0f : Math.max(0.02f, Math.min(1f, value / (float) max));
        View bar = new View(context);
        bar.setBackground(rounded(context, color, 4, color));
        track.addView(bar, new LinearLayout.LayoutParams(0, -1, fraction));
        row.addView(track, new LinearLayout.LayoutParams(0, dp(context, 14), 1));

        TextView amount = text(context, PeerComparison.won(value), 13, TEXT, true);
        amount.setGravity(Gravity.END);
        row.addView(amount, new LinearLayout.LayoutParams(dp(context, 104), -2));
        return row;
    }

    public static View categoryHeader(Context context) {
        return categoryLine(context, "항목", "나", "또래", "차이", MUTED, MUTED, false);
    }

    public static View categoryRow(Context context, String category, long mine, long peer) {
        Integer diff = PeerComparison.percentDiff(mine, peer);
        String diffText = diff == null ? "-" : (diff > 0 ? "+" : "") + diff + "%";
        int diffColor = diff == null ? MUTED : diff >= 20 ? OVER : diff <= -20 ? UNDER : TEXT;
        return categoryLine(context, category, PeerComparison.man(mine), PeerComparison.man(peer),
                diffText, TEXT, diffColor, true);
    }

    private static View categoryLine(Context context, String label, String mine, String peer, String diff,
                                     int color, int diffColor, boolean boldDiff) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(context, label, 13, color, false), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(cell(context, mine, color, false), new LinearLayout.LayoutParams(dp(context, 64), -2));
        row.addView(cell(context, peer, color, false), new LinearLayout.LayoutParams(dp(context, 64), -2));
        row.addView(cell(context, diff, diffColor, boldDiff), new LinearLayout.LayoutParams(dp(context, 56), -2));
        return row;
    }

    private static TextView cell(Context context, String value, int color, boolean bold) {
        TextView view = text(context, value, 13, color, bold);
        view.setGravity(Gravity.END);
        return view;
    }
}
