package com.moa.pbl.analysis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** 카테고리별 금액을 도넛 그래프로 그린다. 값이 없으면 회색 고리만 그린다. */
public class DonutChartView extends View {

    public static final class Slice {
        public final String label;
        public final long value;
        public final int color;

        public Slice(String label, long value, int color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }
    }

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint topPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bottomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private final List<Slice> slices = new ArrayList<>();
    private final float strokeWidth;
    private String centerTop = "";
    private String centerBottom = "";

    public DonutChartView(Context context) {
        super(context);
        strokeWidth = dp(26);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(strokeWidth);

        topPaint.setTextAlign(Paint.Align.CENTER);
        topPaint.setColor(0xff7e8798);
        topPaint.setTextSize(sp(12));

        bottomPaint.setTextAlign(Paint.Align.CENTER);
        bottomPaint.setColor(0xff172642);
        bottomPaint.setTextSize(sp(17));
        bottomPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public void setData(List<Slice> data, String centerTop, String centerBottom) {
        slices.clear();
        slices.addAll(data);
        this.centerTop = centerTop;
        this.centerBottom = centerBottom;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float diameter = Math.min(getWidth(), getHeight()) - strokeWidth;
        float left = (getWidth() - diameter) / 2f;
        float top = (getHeight() - diameter) / 2f;
        arcRect.set(left, top, left + diameter, top + diameter);

        arcPaint.setColor(0xffeef0f3);
        canvas.drawArc(arcRect, 0f, 360f, false, arcPaint);

        long total = 0L;
        for (Slice slice : slices) total += Math.max(0L, slice.value);
        if (total > 0L) {
            float start = -90f;
            for (Slice slice : slices) {
                if (slice.value <= 0L) continue;
                float sweep = 360f * slice.value / total;
                arcPaint.setColor(slice.color);
                // 조각이 여러 개면 사이에 1도 간격을 둔다
                canvas.drawArc(arcRect, start, slices.size() > 1 ? Math.max(sweep - 1f, 0.5f) : sweep, false, arcPaint);
                start += sweep;
            }
        }

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        canvas.drawText(centerTop, centerX, centerY - dp(4), topPaint);
        canvas.drawText(centerBottom, centerX, centerY + dp(18), bottomPaint);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
}
