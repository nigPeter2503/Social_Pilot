package com.socialpilot.app.data.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores OAuth verifier and provider credentials as AES-GCM ciphertext backed by Android Keystore. */
public final class EncryptedOAuthStore {
    private static final String KEY_ALIAS = "socialpilot.oauth.v1";
    private final SharedPreferences preferences;

    public EncryptedOAuthStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("oauth_secure_v1", Context.MODE_PRIVATE);
    }

    public synchronized void savePending(String sessionId, String verifier) {
        put("pending_session", sessionId); put("pending_verifier", verifier);
    }

    public synchronized Pending getPending() {
        String session = get("pending_session"), verifier = get("pending_verifier");
        if (session == null || verifier == null) { clearPending(); return null; }
        return new Pending(session, verifier);
    }

    public synchronized void clearPending() { remove("pending_session"); remove("pending_verifier"); }

    public synchronized void saveConnected(OAuthExchangeResponse response) {
        put("page_id", response.externalAccountId); put("page_name", response.displayName);
        put("access_token", response.accessToken); put("token_type", response.tokenType);
        put("expires_at", response.expiresAt == null ? "" : response.expiresAt);
    }

    public synchronized String pageSummary() {
        String id = get("page_id"), name = get("page_name");
        return id == null || name == null ? null : name + " (" + id + ")";
    }

    /** Token access is kept out of Room and should only be consumed by a provider adapter. */
    public synchronized String accessToken() { return get("access_token"); }

    private void put(String name, String value) {
        if (value == null) { remove(name); return; }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            ByteBuffer packed = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted);
            if (!preferences.edit().putString(name, Base64.encodeToString(packed.array(), Base64.NO_WRAP)).commit()) {
                throw new IllegalStateException("Secure storage unavailable");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Secure storage unavailable");
        }
    }

    private String get(String name) {
        String encoded = preferences.getString(name, null);
        if (encoded == null) return null;
        try {
            byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
            if (packed.length <= 12) throw new IllegalArgumentException();
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] iv = new byte[12]; buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()]; buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            remove(name);
            return null;
        }
    }

    private void remove(String name) { preferences.edit().remove(name).commit(); }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        java.security.Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    public static final class Pending {
        public final String sessionId;
        public final String verifier;
        private Pending(String sessionId, String verifier) { this.sessionId = sessionId; this.verifier = verifier; }
    }
}
