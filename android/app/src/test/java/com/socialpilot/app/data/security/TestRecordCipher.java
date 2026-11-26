package com.socialpilot.app.data.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Real AES-GCM with associated data and a fixed test key, so authentication failures are real. */
final class TestRecordCipher implements RecordCipher {
    boolean failEncryption;
    boolean failDecryption;
    private final SecretKeySpec key;

    TestRecordCipher() {
        this((byte) 0);
    }

    /** A different seed models a lost or regenerated Keystore key. */
    TestRecordCipher(byte seed) {
        byte[] material = new byte[32];
        java.util.Arrays.fill(material, seed);
        key = new SecretKeySpec(material, "AES");
    }

    @Override public String encrypt(String name, String plaintext) throws Exception {
        if (failEncryption) throw new IllegalStateException("Injected encryption failure");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + encrypted.length).put(iv).put(encrypted).array());
    }

    @Override public String decrypt(String name, String ciphertext) throws Exception {
        if (failDecryption) throw new IllegalStateException("Injected Keystore failure");
        ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(ciphertext));
        byte[] iv = new byte[12];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
}
