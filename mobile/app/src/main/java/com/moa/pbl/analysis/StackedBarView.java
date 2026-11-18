package com.moa.pbl.analysis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** 100% 띠그래프 한 줄. 각 조각의 길이가 전체에서 차지하는 비율이고, 충분히 넓은 조각 안에는 %를 쓴다. */
public class StackedBarView extends View {

    public static final class Segment {
        public final long value;
        public final int color;

        public Segment(long value, int color) {
            this.value = value;
            this.color = color;
        }
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final List<Segment> segments = new ArrayList<>();

    public StackedBarView(Context context) {
        super(context);
        label.setColor(0xffffffff);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));
        label.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public void setSegments(List<Segment> data) {
        segments.clear();
        segments.addAll(data);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float radius = height / 2f;

        rect.set(0, 0, width, height);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);

        fill.setColor(0xffeef0f3);
        canvas.drawRect(rect, fill);

        long total = 0L;
        for (Segment segment : segments) total += Math.max(0L, segment.value);
        if (total > 0L) {
            float x = 0f;
            float minLabelWidth = dp(30);
            for (Segment segment : segments) {
                if (segment.value <= 0L) continue;
                float w = width * segment.value / total;
                fill.setColor(segment.color);
                canvas.drawRect(x, 0, x + w, height, fill);
                if (w >= minLabelWidth) {
                    String text = Math.round(segment.value * 100f / total) + "%";
                    float baseline = height / 2f - (label.descent() + label.ascent()) / 2f;
                    canvas.drawText(text, x + w / 2f, baseline, label);
                }
                x += w;
            }
        }
        canvas.restore();
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
