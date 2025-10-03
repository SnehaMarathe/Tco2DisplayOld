package com.example.tco2display.legacy;

import android.content.Context;
import android.graphics.*;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import java.util.Locale;

/** Canvas seven-segment renderer for API 18. */
public class SevenSegmentView extends View {

    private double value = 0.0;
    private int intDigits = 1;               // current integer digits (no leading zeros)
    private final int fracDigits = 3;
    private final float lastScale = 1.22f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();

    private final int colorOn = Color.WHITE;
    private final int colorGhost = Color.argb(26, 255, 255, 255); // ~10% alpha
    private final int colorGreen = Color.rgb(57, 211, 83);
    private final float gapPx;

    public SevenSegmentView(Context c) { this(c, null); }
    public SevenSegmentView(Context c, @Nullable AttributeSet a) { this(c, a, 0); }
    public SevenSegmentView(Context c, @Nullable AttributeSet a, int s) {
        super(c, a, s);
        gapPx = dp(10);
    }

    public void setTco2(double v) {
        this.value = v;
        invalidate();
    }

    private String[] splitValue() {
        String s = String.format(Locale.US, "%." + fracDigits + "f", value);
        int dot = s.indexOf('.');
        String rawInt = dot >= 0 ? s.substring(0, dot) : s;
        String trimmed = rawInt.replaceFirst("^0+(?!$)", "");
        if (trimmed.length() == 0) trimmed = "0";
        intDigits = trimmed.length();
        String frac = dot >= 0 ? s.substring(dot + 1) : "000";
        return new String[]{ trimmed, frac.substring(0, 2), frac.substring(2, 3) };
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(Color.BLACK);

        // Build pieces
        String[] parts = splitValue();
        String iPart = parts[0], f2 = parts[1], last = parts[2];

        // Layout math (fit to width)
        float totalGaps = gapPx * (iPart.length() + fracDigits + 1 /*dot*/ - 1);
        float aspect = 0.56f;                // digit box aspect
        float dotWFactor = 0.28f, dotHFactor = 0.12f;

        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        float baseW = (w - totalGaps) / (iPart.length() + fracDigits + dotWFactor);
        float dW = baseW;
        float dH = dW / aspect;
        float dotW = baseW * dotWFactor;
        float dotH = dH * dotHFactor;

        float x = getPaddingLeft();
        float cy = getHeight() / 2f;

        // Ghost for present digits only
        for (int k = 0; k < iPart.length(); k++) {
            drawDigit(c, x, cy - dH/2f, dW, dH, '8', colorGhost, colorGhost);
            x += dW + gapPx;
        }
        // dot ghost
        paint.setColor(colorGhost);
        tmp.set(x, cy - dotH/2f, x + dotW, cy + dotH/2f);
        c.drawRoundRect(tmp, dp(2), dp(2), paint);
        x += dotW + gapPx;

        // fraction first two ghosts
        for (int k = 0; k < 2; k++) {
            drawDigit(c, x, cy - dH/2f, dW, dH, '8', colorGhost, colorGhost);
            x += dW + gapPx;
        }
        // last ghost (bigger)
        drawDigit(c, x, cy - (dH*lastScale)/2f, dW*lastScale, dH*lastScale, '8', colorGhost, colorGhost);

        // Draw actual digits
        x = getPaddingLeft();
        for (int k = 0; k < iPart.length(); k++) {
            drawDigit(c, x, cy - dH/2f, dW, dH, iPart.charAt(k), colorOn, colorGhost);
            x += dW + gapPx;
        }
        // dot
        paint.setColor(colorOn);
        tmp.set(x, cy - dotH/2f, x + dotW, cy + dotH/2f);
        c.drawRoundRect(tmp, dp(2), dp(2), paint);
        x += dotW + gapPx;

        // first two decimals
        for (int k = 0; k < 2; k++) {
            drawDigit(c, x, cy - dH/2f, dW, dH, f2.charAt(k), colorOn, colorGhost);
            x += dW + gapPx;
        }
        // last decimal (green + bigger)
        drawDigit(c, x, cy - (dH*lastScale)/2f, dW*lastScale, dH*lastScale, last.charAt(0), colorGreen, colorGhost);
    }

    private void drawDigit(Canvas c, float x, float y, float w, float h, char ch, int onColor, int offColor) {
        boolean[] seg = map(ch);              // a,b,c,d,e,f,g
        float t = h * 0.15f;                  // thickness
        float r = t * 0.35f;                  // corner radius

        // a (top)
        drawSeg(c, x + t, y, w - 2*t, t, seg[0], onColor, offColor, r);
        // d (bottom)
        drawSeg(c, x + t, y + h - t, w - 2*t, t, seg[3], onColor, offColor, r);
        // g (middle)
        drawSeg(c, x + t, y + h/2f - t/2f, w - 2*t, t, seg[6], onColor, offColor, r);
        // f (upper-left)
        drawSeg(c, x, y + t, t, h/2f - t, seg[5], onColor, offColor, r);
        // e (lower-left)
        drawSeg(c, x, y + h/2f, t, h/2f - t, seg[4], onColor, offColor, r);
        // b (upper-right)
        drawSeg(c, x + w - t, y + t, t, h/2f - t, seg[1], onColor, offColor, r);
        // c (lower-right)
        drawSeg(c, x + w - t, y + h/2f, t, h/2f - t, seg[2], onColor, offColor, r);
    }

    private void drawSeg(Canvas c, float left, float top, float w, float h,
                         boolean on, int onColor, int offColor, float radius) {
        paint.setColor(on ? onColor : offColor);
        tmp.set(left, top, left + w, top + h);
        c.drawRoundRect(tmp, radius, radius, paint);
    }

    private boolean[] map(char ch) {
        switch (ch) {
            case '0': return new boolean[]{true,true,true,true,true,true,false};
            case '1': return new boolean[]{false,true,true,false,false,false,false};
            case '2': return new boolean[]{true,true,false,true,true,false,true};
            case '3': return new boolean[]{true,true,true,true,false,false,true};
            case '4': return new boolean[]{false,true,true,false,false,true,true};
            case '5': return new boolean[]{true,false,true,true,false,true,true};
            case '6': return new boolean[]{true,false,true,true,true,true,true};
            case '7': return new boolean[]{true,true,true,false,false,false,false};
            case '8': return new boolean[]{true,true,true,true,true,true,true};
            case '9': return new boolean[]{true,true,true,true,false,true,true};
            default:  return new boolean[]{false,false,false,false,false,false,false};
        }
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
