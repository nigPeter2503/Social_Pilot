package com.socialpilot.app.data.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import com.google.gson.Gson;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Versioned, authenticated OAuth records. A Page and its credentials are read/written together. */
public final class EncryptedOAuthStore {
    private static final String KEY_ALIAS = "socialpilot.oauth.v1";
    private static final String PENDING = "pending_v2";
    private static final String CONNECTED = "connected_v2";
    private static final String[] LEGACY_PENDING = {"pending_session", "pending_verifier"};
    private static final String[] LEGACY_CONNECTED = {"page_id", "page_name", "access_token", "token_type", "expires_at"};
    private final SharedPreferences preferences;
    private final RecordCipher cipher;
    private final Gson gson = new Gson();
    private final Set<String> unavailable = new HashSet<>();

    public EncryptedOAuthStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences("oauth_secure_v1", Context.MODE_PRIVATE),
                new KeystoreCipher());
    }

    // Injection keeps failure tests independent of a device's Keystore and disk availability.
    EncryptedOAuthStore(SharedPreferences preferences, RecordCipher cipher) {
        this.preferences = preferences;
        this.cipher = cipher;
    }

    public synchronized void savePending(String sessionId, String verifier) {
        PendingRecord record = new PendingRecord(sessionId, verifier);
        record.validate();
        write(PENDING, record, LEGACY_PENDING);
    }

    public synchronized Pending getPending() {
        PendingRecord record = read(PENDING, PendingRecord.class, LEGACY_PENDING);
        if (record == null) return null;
        try {
            record.validate();
            return new Pending(record.sessionId, record.verifier);
        } catch (RuntimeException exception) {
            invalidate(PENDING, LEGACY_PENDING);
            return null;
        }
    }

    public synchronized void clearPending() { invalidate(PENDING, LEGACY_PENDING); }

    public synchronized void saveConnected(OAuthExchangeResponse response) {
        ConnectedRecord record = new ConnectedRecord(response);
        record.validate();
        write(CONNECTED, record, LEGACY_CONNECTED);
    }

    public synchronized String pageSummary() {
        ConnectedRecord record = connected();
        return record == null ? null : record.connection.displayName + " (" + record.connection.externalAccountId + ")";
    }

    /** Token access is kept out of Room and should only be consumed by a provider adapter. */
    public synchronized String accessToken() {
        ConnectedRecord record = connected();
        return record == null ? null : record.connection.accessToken;
    }

    private ConnectedRecord connected() {
        ConnectedRecord record = read(CONNECTED, ConnectedRecord.class, LEGACY_CONNECTED);
        if (record == null) return null;
        try {
            record.validate();
            return record;
        } catch (RuntimeException exception) {
            invalidate(CONNECTED, LEGACY_CONNECTED);
            return null;
        }
    }

    private void write(String name, Object record, String[] legacy) {
        final String encrypted;
        try {
            // Encryption/serialization must succeed before touching preferences.
            encrypted = cipher.encrypt(name, gson.toJson(record));
        } catch (Exception exception) {
            throw storageUnavailable();
        }
        try {
            SharedPreferences.Editor editor = preferences.edit().putString(name, encrypted);
            for (String key : legacy) editor.remove(key);
            if (!editor.commit()) throw storageUnavailable();
            unavailable.remove(name);
        } catch (RuntimeException exception) {
            // commit(false) can still change SharedPreferences' in-memory map. Do not trust it.
            invalidate(name, legacy);
            throw storageUnavailable();
        }
    }

    private <T> T read(String name, Class<T> type, String[] legacy) {
        if (unavailable.contains(name)) return null;
        try {
            String encrypted = preferences.getString(name, null);
            if (encrypted == null) {
                // v1 fields may already contain mixed credentials. Require reconnect rather than migrate them.
                for (String key : legacy) {
                    if (preferences.contains(key)) { invalidate(name, legacy); break; }
                }
                return null;
            }
            T record = gson.fromJson(cipher.decrypt(name, encrypted), type);
            if (record == null) throw storageUnavailable();
            return record;
        } catch (Exception exception) {
            invalidate(name, legacy);
            return null;
        }
    }

    private void invalidate(String name, String[] legacy) {
        unavailable.add(name);
        try {
            SharedPreferences.Editor editor = preferences.edit().remove(name);
            for (String key : legacy) editor.remove(key);
            // Remain unavailable even if cleanup cannot reach disk; a successful save restores access.
            editor.commit();
        } catch (RuntimeException ignored) { }
    }

    private static IllegalStateException storageUnavailable() {
        return new IllegalStateException("Secure storage unavailable");
    }

    interface RecordCipher {
        String encrypt(String name, String plaintext) throws Exception;
        String decrypt(String name, String ciphertext) throws Exception;
    }

    private static final class KeystoreCipher implements RecordCipher {
        @Override public String encrypt(String name, String plaintext) throws Exception {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            return Base64.encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array(), Base64.NO_WRAP);
        }

        @Override public String decrypt(String name, String ciphertext) throws Exception {
            byte[] packed = Base64.decode(ciphertext, Base64.NO_WRAP);
            if (packed.length < 12 + 16) throw storageUnavailable();
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] iv = new byte[12]; buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        }

        private static SecretKey key() throws Exception {
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
    }

    private static final class PendingRecord {
        private int version = 2;
        private final String sessionId, verifier;
        PendingRecord(String sessionId, String verifier) { this.sessionId = sessionId; this.verifier = verifier; }
        void validate() {
            if (version != 2 || sessionId == null || sessionId.trim().isEmpty() || verifier == null
                    || !verifier.matches("[A-Za-z0-9._~-]{43,128}")) throw storageUnavailable();
        }
    }

    private static final class ConnectedRecord {
        private int version = 2;
        private final OAuthExchangeResponse connection;
        ConnectedRecord(OAuthExchangeResponse connection) { this.connection = connection; }
        void validate() {
            if (version != 2 || connection == null) throw storageUnavailable();
            connection.validate();
            if (!"FACEBOOK".equals(connection.provider) || !"PAGE".equals(connection.accountType)) throw storageUnavailable();
        }
    }

    public static final class Pending {
        public final String sessionId;
        public final String verifier;
        private Pending(String sessionId, String verifier) { this.sessionId = sessionId; this.verifier = verifier; }
    }
}
