package com.socialpilot.app.domain.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pure layout of the local template: margins, headline font size and wrapped lines. Rendering
 * code only draws what this returns, so wrapping and truncation are unit tested on the JVM.
 */
public final class TemplateLayout {
    /** Width of {@code text} in pixels at {@code textSizePx}. */
    public interface Measurer {
        float width(String text, float textSizePx);
    }

    public static final String TEMPLATE_ID = "sp032-gradient-headline-v1";
    static final int MAX_LINES = 5;
    private static final String ELLIPSIS = "…";

    public final int marginPx;
    public final float headlineSizePx;
    public final List<String> lines;
    public final float footerSizePx;
    public final String footer;
    public final int accentBarHeightPx;

    private TemplateLayout(int marginPx, float headlineSizePx, List<String> lines, float footerSizePx, String footer,
                           int accentBarHeightPx) {
        this.marginPx = marginPx;
        this.headlineSizePx = headlineSizePx;
        this.lines = Collections.unmodifiableList(lines);
        this.footerSizePx = footerSizePx;
        this.footer = footer;
        this.accentBarHeightPx = accentBarHeightPx;
    }

    /**
     * Lays out a headline (the post topic or first line) and a footer label. Starts large and
     * shrinks until the text fits in {@link #MAX_LINES}; at the smallest size it truncates.
     */
    public static TemplateLayout of(ImageSpec spec, String headline, String footer, Measurer measurer) {
        Objects.requireNonNull(measurer);
        String text = headline == null ? "" : headline.trim().replaceAll("\\s+", " ");
        int short_ = Math.min(spec.widthPx, spec.heightPx);
        int margin = Math.round(short_ * 0.08f);
        float maxWidth = spec.widthPx - 2f * margin;
        float largest = short_ * 0.09f;
        float smallest = short_ * 0.045f;
        List<String> lines = Collections.emptyList();
        float size = largest;
        for (; size >= smallest; size -= short_ * 0.005f) {
            lines = wrap(text, maxWidth, size, measurer, Integer.MAX_VALUE);
            if (lines.size() <= MAX_LINES) break;
        }
        if (size < smallest) {
            size = smallest;
            lines = wrap(text, maxWidth, size, measurer, MAX_LINES);
        }
        String label = footer == null ? "" : footer.trim();
        return new TemplateLayout(margin, size, new ArrayList<>(lines), short_ * 0.035f, label,
                Math.max(4, Math.round(short_ * 0.012f)));
    }

    static List<String> wrap(String text, float maxWidth, float size, Measurer measurer, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) return lines;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (measurer.width(candidate, size) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            // A single word wider than the line is broken by characters.
            String rest = word;
            while (measurer.width(rest, size) > maxWidth && rest.length() > 1) {
                int cut = rest.length() - 1;
                while (cut > 1 && measurer.width(rest.substring(0, cut), size) > maxWidth) cut--;
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        if (line.length() > 0) lines.add(line.toString());
        if (lines.size() <= maxLines) return lines;
        List<String> kept = new ArrayList<>(lines.subList(0, maxLines));
        String last = kept.get(maxLines - 1);
        while (!last.isEmpty() && measurer.width(last + ELLIPSIS, size) > maxWidth) {
            last = last.substring(0, last.length() - 1).trim();
        }
        kept.set(maxLines - 1, last + ELLIPSIS);
        return kept;
    }
}
