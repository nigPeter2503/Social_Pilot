package com.socialpilot.app.data.security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM with a non-exportable Android Keystore key. Each alias protects one namespace, so
 * deleting or regenerating one namespace key never affects another namespace.
 */
public final class KeystoreRecordCipher implements RecordCipher {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final String alias;
    private final boolean recoverInvalidatedKey;

    /**
     * @param recoverInvalidatedKey when true, a permanently invalidated key is replaced on the next
     *     write. Records encrypted with the old key were already unreadable, and the caller asks
     *     the user to enter them again.
     */
    KeystoreRecordCipher(String alias, boolean recoverInvalidatedKey) {
        this.alias = alias;
        this.recoverInvalidatedKey = recoverInvalidatedKey;
    }

    @Override
    public String encrypt(String name, String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key());
        } catch (KeyPermanentlyInvalidatedException invalidated) {
            if (!recoverInvalidatedKey) throw invalidated;
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            store.deleteEntry(alias);
            cipher.init(Cipher.ENCRYPT_MODE, key());
        }
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        return Base64.encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv).put(encrypted).array(), Base64.NO_WRAP);
    }

    @Override
    public String decrypt(String name, String ciphertext) throws Exception {
        byte[] packed = Base64.decode(ciphertext, Base64.NO_WRAP);
        if (packed.length < IV_BYTES + TAG_BITS / 8) throw new IllegalStateException("Truncated record");
        ByteBuffer buffer = ByteBuffer.wrap(packed);
        byte[] iv = new byte[IV_BYTES];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    public static synchronized com.socialpilot.app.data.remote.social.CommentCursorCodec commentCursors() {
        try {
            SecretKey key = new KeystoreRecordCipher("socialpilot.comments.cursor.v1", false).key();
            return new com.socialpilot.app.data.remote.social.CommentCursorCodec(key);
        } catch (Exception unavailable) {
            throw new IllegalStateException("Comment cursor key unavailable", unavailable);
        }
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing = store.getKey(alias, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
}
