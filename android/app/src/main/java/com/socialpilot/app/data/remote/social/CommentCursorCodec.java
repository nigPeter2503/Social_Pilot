package com.socialpilot.app.data.remote.social;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import okio.ByteString;

/** Caller supplies a stable, per-install Keystore key. No raw cursor/key persistence here. */
public final class CommentCursorCodec {
    private final SecretKey key;
    public CommentCursorCodec(SecretKey key) { this.key = Objects.requireNonNull(key, "key"); }

    String encode(CommentPageRequest request, String continuation) {
        try {
            // Let the provider generate the IV: Android Keystore randomized-encryption keys
            // reject caller-selected IVs during encryption.
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD(("sp041v1:" + request.queryFingerprint).getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != 12) throw new GeneralSecurityException();
            byte[] encrypted = cipher.doFinal(continuation.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = Arrays.copyOf(iv, iv.length + encrypted.length);
            System.arraycopy(encrypted, 0, envelope, iv.length, encrypted.length);
            return "sp041v1." + ByteString.of(envelope).base64Url();
        } catch (GeneralSecurityException exception) { throw new IllegalStateException("Cursor encryption failed"); }
    }

    String decode(CommentPageRequest request) {
        if (request.cursor == null) return null;
        try {
            if (!request.cursor.startsWith("sp041v1.") || request.cursor.length() > 24000) CommentPageRequest.invalid();
            ByteString bytes = ByteString.decodeBase64(request.cursor.substring(8));
            if (bytes == null || bytes.size() < 29) CommentPageRequest.invalid();
            byte[] envelope = bytes.toByteArray();
            String value = new String(cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(envelope, 12), request)
                    .doFinal(Arrays.copyOfRange(envelope, 12, envelope.length)), StandardCharsets.UTF_8);
            return CommentPageRequest.text(value);
        } catch (GeneralSecurityException | RuntimeException ignored) {
            throw new IllegalArgumentException("Invalid comment cursor");
        }
    }

    private Cipher cipher(int mode, byte[] iv, CommentPageRequest request) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(("sp041v1:" + request.queryFingerprint).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
