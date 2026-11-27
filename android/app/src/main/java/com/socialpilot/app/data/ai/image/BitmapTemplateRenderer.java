package com.socialpilot.app.data.ai.image;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import com.socialpilot.app.domain.agent.ImageSpec;
import com.socialpilot.app.domain.agent.TemplateLayout;
import java.io.ByteArrayOutputStream;

/** Android Canvas implementation of the gradient-headline template. */
public final class BitmapTemplateRenderer implements TemplateRenderer {
    @Override
    public byte[] render(ImageSpec spec, String headline, String footer) {
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setColor(spec.textColor);
        TemplateLayout layout = TemplateLayout.of(spec, headline, footer, (value, size) -> {
            text.setTextSize(size);
            return text.measureText(value);
        });
        Bitmap bitmap = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            Paint background = new Paint();
            background.setShader(new LinearGradient(0, 0, 0, spec.heightPx, spec.backgroundTop,
                    spec.backgroundBottom, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, spec.widthPx, spec.heightPx, background);
            Paint accent = new Paint();
            accent.setColor(spec.accent);
            canvas.drawRect(layout.marginPx, layout.marginPx, layout.marginPx * 3f,
                    layout.marginPx + layout.accentBarHeightPx, accent);
            text.setTextSize(layout.headlineSizePx);
            float lineHeight = layout.headlineSizePx * 1.25f;
            float block = lineHeight * layout.lines.size();
            float y = (spec.heightPx - block) / 2f + layout.headlineSizePx;
            for (String line : layout.lines) {
                canvas.drawText(line, layout.marginPx, y, text);
                y += lineHeight;
            }
            if (!layout.footer.isEmpty()) {
                Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
                label.setColor(spec.textColor);
                label.setTextSize(layout.footerSizePx);
                canvas.drawText(layout.footer, layout.marginPx, spec.heightPx - layout.marginPx, label);
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)) throw new IllegalStateException("PNG encode failed");
            return png.toByteArray();
        } finally {
            bitmap.recycle();
        }
    }
}
