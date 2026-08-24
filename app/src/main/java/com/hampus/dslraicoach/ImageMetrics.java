package com.hampus.dslraicoach;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.Locale;

public final class ImageMetrics {
    private ImageMetrics() {}

    public static final class Result {
        public final double brightness, highlightClipPct, shadowClipPct, sharpness;
        Result(double brightness, double highlightClipPct, double shadowClipPct, double sharpness) {
            this.brightness = brightness;
            this.highlightClipPct = highlightClipPct;
            this.shadowClipPct = shadowClipPct;
            this.sharpness = sharpness;
        }
        public String asPromptText() {
            return String.format(Locale.US, "Measured image data: mean brightness %.1f/100; clipped highlights %.2f%%; clipped shadows %.2f%%; edge sharpness %.1f/100.", brightness, highlightClipPct, shadowClipPct, sharpness);
        }
        public String shortSummary() {
            return String.format(Locale.US, "Brightness %.0f/100  •  Highlights %.1f%%  •  Shadows %.1f%%  •  Sharpness %.0f/100", brightness, highlightClipPct, shadowClipPct, sharpness);
        }
    }

    public static Result analyze(Bitmap source) {
        int targetW = Math.min(360, source.getWidth());
        int targetH = Math.max(1, Math.round(source.getHeight() * (targetW / (float) source.getWidth())));
        Bitmap b = source.getWidth() == targetW ? source : Bitmap.createScaledBitmap(source, targetW, targetH, true);
        int w = b.getWidth(), h = b.getHeight();
        long n = (long) w * h, highlight = 0, shadow = 0, gradientN = 0;
        double luminanceSum = 0, gradientSum = 0;
        int[] row = new int[w], prev = new int[w];
        for (int y = 0; y < h; y++) {
            b.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                int c = row[x];
                int lum = (int)(0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c));
                luminanceSum += lum;
                if (lum >= 250) highlight++;
                if (lum <= 5) shadow++;
                if (x > 0) {
                    int c2 = row[x - 1];
                    int l2 = (int)(0.2126 * Color.red(c2) + 0.7152 * Color.green(c2) + 0.0722 * Color.blue(c2));
                    gradientSum += Math.abs(lum - l2); gradientN++;
                }
                if (y > 0) {
                    int c2 = prev[x];
                    int l2 = (int)(0.2126 * Color.red(c2) + 0.7152 * Color.green(c2) + 0.0722 * Color.blue(c2));
                    gradientSum += Math.abs(lum - l2); gradientN++;
                }
            }
            int[] tmp = prev; prev = row; row = tmp;
        }
        if (b != source) b.recycle();
        double brightness = (luminanceSum / n) / 255.0 * 100.0;
        double sharpness = Math.min(100.0, (gradientN == 0 ? 0 : gradientSum / gradientN) * 4.0);
        return new Result(brightness, highlight * 100.0 / n, shadow * 100.0 / n, sharpness);
    }

    public static String deterministicAdvice(Result r) {
        StringBuilder s = new StringBuilder();
        if (r.highlightClipPct > 3) s.append("• Highlights are clipping; reduce exposure or protect bright areas.\n");
        if (r.shadowClipPct > 8) s.append("• Deep shadows are heavily clipped; consider more exposure or softer light.\n");
        if (r.brightness < 30) s.append("• The frame is quite dark; consider +EV, a wider aperture, slower shutter, or higher ISO.\n");
        if (r.brightness > 72) s.append("• The frame is very bright; consider lowering exposure.\n");
        if (r.sharpness < 18) s.append("• Fine-detail sharpness is low; check focus and shutter speed.\n");
        if (s.length() == 0) s.append("• Exposure and measured sharpness look technically healthy.\n");
        return s.toString();
    }
}
