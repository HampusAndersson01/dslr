package com.hampus.dslraicoach;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

public final class MarkdownText {
    private MarkdownText() {}

    public static Spanned render(String markdown) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        for (MarkdownBlocks.Block block : MarkdownBlocks.parse(markdown)) {
            int start = out.length();
            switch (block.type) {
                case HEADING:
                    appendWithBreak(out, block.text);
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new RelativeSizeSpan(1.08f), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(0xFF1F2933), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case BULLET:
                    appendWithBreak(out, block.text);
                    out.setSpan(new BulletSpan(18, 0xFF25636B), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case PARAGRAPH:
                    appendWithBreak(out, block.text);
                    break;
            }
        }
        return out;
    }

    private static void appendWithBreak(SpannableStringBuilder out, String text) {
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(text);
    }
}
