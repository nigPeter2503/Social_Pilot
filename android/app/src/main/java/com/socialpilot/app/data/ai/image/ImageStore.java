package com.socialpilot.app.data.ai.image;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Images in one app-private directory ({@code files/ai_images}). Callers only see an opaque
 * reference ({@code img_<uuid>.png}); paths, URLs and credentials never leave this class.
 * Writes go to a temp file and are renamed when complete, so a crash never leaves a half image
 * under a valid reference. Deleting is reference-counted by the editor: {@link #cleanup} keeps
 * every reference it is given.
 */
public final class ImageStore {
    private static final Pattern REF = Pattern.compile("img_[0-9a-f-]{36}\\.png");
    private static final String TEMP_SUFFIX = ".tmp";
    private final File root;

    public ImageStore(File root) {
        this.root = Objects.requireNonNull(root);
    }

    public static final class Stored {
        public final String ref;
        public final long byteSize;
        public final String sha256;

        Stored(String ref, long byteSize, String sha256) {
            this.ref = ref;
            this.byteSize = byteSize;
            this.sha256 = sha256;
        }
    }

    /** Writes a complete image and returns its reference. */
    public Stored save(byte[] png) throws IOException {
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create image directory");
        String ref = "img_" + UUID.randomUUID() + ".png";
        File temp = new File(root, ref + TEMP_SUFFIX);
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(png);
            out.getFD().sync();
        } catch (IOException failed) {
            delete(temp);
            throw failed;
        }
        File target = new File(root, ref);
        if (!temp.renameTo(target)) {
            delete(temp);
            throw new IOException("Cannot commit image");
        }
        return new Stored(ref, png.length, sha256(png));
    }

    /** Resolves a reference to its file, or null for an unknown or malformed reference. */
    public File file(String ref) {
        if (ref == null || !REF.matcher(ref).matches()) return null;
        File file = new File(root, ref);
        return file.isFile() ? file : null;
    }

    public boolean delete(String ref) {
        File file = file(ref);
        return file != null && file.delete();
    }

    /**
     * Removes leftover temp files and images that no version references. The caller passes every
     * reference still in use (all versions, all channels); anything in that set is kept.
     */
    public int cleanup(Set<String> referenced) {
        File[] files = root.listFiles();
        if (files == null) return 0;
        int removed = 0;
        for (File file : files) {
            String name = file.getName();
            boolean temp = name.endsWith(TEMP_SUFFIX);
            boolean orphan = REF.matcher(name).matches() && !referenced.contains(name);
            if ((temp || orphan) && file.delete()) removed++;
        }
        return removed;
    }

    private static void delete(File file) {
        if (file.exists() && !file.delete()) file.deleteOnExit();
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16)).append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
