package com.socialpilot.app.data.ai.image;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.socialpilot.app.domain.agent.ChannelPlatform;
import com.socialpilot.app.domain.agent.ChannelTarget;
import com.socialpilot.app.domain.agent.ImageAsset;
import com.socialpilot.app.domain.agent.ImageSpec;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import java.io.File;
import java.io.FileOutputStream;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real Canvas rendering and the template path of the image service on the device. */
@RunWith(AndroidJUnit4.class)
public final class TemplateRendererDeviceTest {
    @Test public void bothChannelsRenderDistinctTemplatesAtTheirSpecSize() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        BitmapTemplateRenderer renderer = new BitmapTemplateRenderer();
        File evidence = new File(context.getFilesDir(), "sp032_evidence");
        assertTrue(evidence.isDirectory() || evidence.mkdirs());
        for (ChannelPlatform platform : ChannelPlatform.values()) {
            ImageSpec spec = ImageSpec.defaults(platform);
            byte[] png = renderer.render(spec,
                    "Quán cà phê mới khai trương: giảm 20% cho sinh viên trong tuần đầu tiên", "SocialPilot");
            Bitmap decoded = BitmapFactory.decodeByteArray(png, 0, png.length);
            assertNotNull(decoded);
            assertEquals(spec.widthPx, decoded.getWidth());
            assertEquals(spec.heightPx, decoded.getHeight());
            // Background colours differ per channel (top-left pixel is the gradient start).
            int pixel = decoded.getPixel(1, 1);
            for (int shift = 0; shift <= 16; shift += 8) {
                int expected = (spec.backgroundTop >> shift) & 0xFF;
                int actual = (pixel >> shift) & 0xFF;
                assertTrue(platform + " colour", Math.abs(expected - actual) <= 4);
            }
            decoded.recycle();
            try (FileOutputStream out = new FileOutputStream(new File(evidence, platform.name().toLowerCase(java.util.Locale.ROOT) + ".png"))) {
                out.write(png);
            }
        }
    }

    @Test public void serviceTemplatePathStoresAPrivateReference() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File dir = new File(context.getCacheDir(), "sp032_store_test");
        ImageStore store = new ImageStore(dir);
        ImageService service = new ImageService(task -> null, new EnumMap<AiRequest.Provider, ImageProvider>(
                AiRequest.Provider.class), provider -> false, null, store, new BitmapTemplateRenderer(),
                ImageService.CALLER_REVISION, Runnable::run, System::currentTimeMillis);
        AtomicReference<ImageService.Result> result = new AtomicReference<>();
        service.generate(new ImageService.Request("s", new ChannelTarget("ig-1", ChannelPlatform.INSTAGRAM, 2), "image",
                ImageSpec.defaults(ChannelPlatform.INSTAGRAM), "", "Instagram template", "SocialPilot"), result::set);
        ImageAsset asset = result.get().asset;
        assertEquals(ImageAsset.Kind.TEMPLATE, asset.kind);
        assertEquals("NO_MODEL_SELECTED", asset.fallbackReason);
        assertEquals(1350, asset.heightPx);
        File file = store.file(asset.ref);
        assertNotNull(file);
        assertTrue(file.getCanonicalPath().startsWith(context.getCacheDir().getCanonicalPath()));
        assertTrue(store.delete(asset.ref));
        dir.delete();
    }
}
