package com.moa.pbl.analysis;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;

/** 한 달 달력에 날짜별 지출 금액을 보여준다. 많이 쓴 날일수록 칸 색이 진해진다. */
public final class SpendingCalendar {

    private static final int TEXT = 0xff172642;
    private static final int MUTED = 0xff7e8798;
    private static final int SUNDAY = 0xffc0453a;
    private static final int SATURDAY = 0xff3f6fc6;
    private static final int BLUE = 0xff1b4fa3;
    private static final String[] WEEK = {"일", "월", "화", "수", "목", "금", "토"};

    /** 달력에서 누른 날짜. 화면을 다시 그려도 유지된다. */
    private static LocalDate selected;

    private SpendingCalendar() {
    }

    /** 선택한 날짜가 이 달에 있으면 그 날짜, 아니면 null */
    public static LocalDate selectedDate(YearMonth month) {
        if (selected == null || !YearMonth.from(selected).equals(month)) return null;
        return selected;
    }

    /** "2026-11-05..." 형태의 날짜 문자열에서 일(5)을 꺼낸다. 형식이 다르면 -1 */
    public static int dayOf(String occurredOn) {
        try {
            return Integer.parseInt(occurredOn.substring(8, 10));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /**
     * @param daily 일 → 그 날 지출 합계 (기록 없는 날은 없어도 됨)
     * @param onChanged 날짜를 눌렀을 때 화면을 다시 그리는 콜백
     */
    public static View card(Context context, YearMonth month, Map<Integer, Long> daily, Runnable onChanged) {
        LinearLayout card = AnalysisViews.card(context);
        card.addView(text(context, AnalysisMonth.shortLabel(month) + " 소비 달력", 17, TEXT, true));
        card.addView(text(context, "날짜를 누르면 그날 쓴 내역을 볼 수 있어요.", 12, MUTED, false));

        LinearLayout head = new LinearLayout(context);
        head.setPadding(0, dp(context, 8), 0, dp(context, 4));
        for (int i = 0; i < 7; i++) {
            TextView name = text(context, WEEK[i], 12, i == 0 ? SUNDAY : i == 6 ? SATURDAY : MUTED, true);
            name.setGravity(Gravity.CENTER);
            head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        }
        card.addView(head);

        long max = 0L, total = 0L;
        int spentDays = 0, maxDay = 0;
        for (Map.Entry<Integer, Long> entry : daily.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) continue;
            total += entry.getValue();
            spentDays++;
            if (entry.getValue() > max) { max = entry.getValue(); maxDay = entry.getKey(); }
        }

        LocalDate today = LocalDate.now();
        LocalDate picked = selectedDate(month);
        int offset = month.atDay(1).getDayOfWeek().getValue() % 7; // 일요일 = 0
        int length = month.lengthOfMonth();
        int cells = ((offset + length + 6) / 7) * 7;

        LinearLayout week = null;
        for (int i = 0; i < cells; i++) {
            if (i % 7 == 0) {
                week = new LinearLayout(context);
                card.addView(week, new LinearLayout.LayoutParams(-1, dp(context, 54)));
            }
            int day = i - offset + 1;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
            params.setMargins(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2));
            if (day < 1 || day > length) {
                week.addView(new View(context), params);
                continue;
            }
            LocalDate date = month.atDay(day);
            Long spentValue = daily.get(day);
            long spent = spentValue == null ? 0L : spentValue;
            week.addView(dayCell(context, date, spent, max, date.equals(today), date.equals(picked), () -> {
                selected = date.equals(selected) ? null : date;
                onChanged.run();
            }), params);
        }

        String summary = spentDays == 0
                ? "이 달에는 지출 기록이 없어요."
                : "지출한 날 " + spentDays + "일 · 하루 평균 " + won(total / spentDays)
                  + " · 가장 많이 쓴 날 " + maxDay + "일(" + won(max) + ")";
        TextView summaryView = text(context, summary, 12, MUTED, false);
        summaryView.setPadding(0, dp(context, 8), 0, 0);
        card.addView(summaryView);
        return card;
    }

    private static View dayCell(Context context, LocalDate date, long spent, long max, boolean today, boolean picked,
                                Runnable onClick) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(0, dp(context, 4), 0, dp(context, 2));

        // 지출이 많을수록 진한 주황 (최대 지출일 기준 비율)
        int fillColor = 0xffffffff;
        if (spent > 0L && max > 0L) {
            float level = 0.15f + 0.6f * spent / (float) max;
            fillColor = blend(0xffffffff, 0xfff2a93b, level);
        }
        GradientDrawable background = new GradientDrawable();
        background.setColor(fillColor);
        background.setCornerRadius(dp(context, 10));
        if (picked) background.setStroke(dp(context, 2), BLUE);
        else if (today) background.setStroke(dp(context, 1), 0xff9fb4dc);
        cell.setBackground(background);

        DayOfWeek weekday = date.getDayOfWeek();
        int dayColor = weekday == DayOfWeek.SUNDAY ? SUNDAY : weekday == DayOfWeek.SATURDAY ? SATURDAY : TEXT;
        TextView number = text(context, String.valueOf(date.getDayOfMonth()), 13, dayColor, today || picked);
        number.setPadding(0, 0, 0, 0);
        number.setGravity(Gravity.CENTER);
        cell.addView(number);

        if (spent > 0L) {
            TextView amount = text(context, shortAmount(spent), 10, TEXT, true);
            amount.setPadding(0, dp(context, 2), 0, 0);
            amount.setGravity(Gravity.CENTER);
            amount.setMaxLines(1);
            cell.addView(amount);
        }
        cell.setOnClickListener(v -> onClick.run());
        return cell;
    }

    /** 칸에 들어가도록 짧게: 125,000 → 12.5만, 8,500 → 8.5천, 700 → 700 */
    public static String shortAmount(long amount) {
        if (amount >= 10_000L) return trim(String.format(Locale.KOREA, "%.1f", amount / 10_000.0)) + "만";
        if (amount >= 1_000L) return trim(String.format(Locale.KOREA, "%.1f", amount / 1_000.0)) + "천";
        return String.valueOf(amount);
    }

    private static String trim(String number) {
        return number.endsWith(".0") ? number.substring(0, number.length() - 2) : number;
    }

    private static int blend(int from, int to, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        int a = 0xff;
        int red = (int) (((from >> 16) & 0xff) * (1 - r) + ((to >> 16) & 0xff) * r);
        int green = (int) (((from >> 8) & 0xff) * (1 - r) + ((to >> 8) & 0xff) * r);
        int blue = (int) ((from & 0xff) * (1 - r) + (to & 0xff) * r);
        return (a << 24) | (red << 16) | (green << 8) | blue;
    }

    private static String won(long amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
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

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
