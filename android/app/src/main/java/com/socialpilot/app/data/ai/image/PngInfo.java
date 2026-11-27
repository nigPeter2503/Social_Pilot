package com.socialpilot.app.data.ai.image;

/** Reads width and height from a PNG header; anything that is not a PNG is rejected. */
final class PngInfo {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    final int width;
    final int height;

    private PngInfo(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Null when the bytes are not a PNG with a valid IHDR chunk. */
    static PngInfo read(byte[] bytes) {
        if (bytes == null || bytes.length < 24) return null;
        for (int i = 0; i < SIGNATURE.length; i++) if (bytes[i] != SIGNATURE[i]) return null;
        if (bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R') return null;
        int width = readInt(bytes, 16);
        int height = readInt(bytes, 20);
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192) return null;
        return new PngInfo(width, height);
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }
}
