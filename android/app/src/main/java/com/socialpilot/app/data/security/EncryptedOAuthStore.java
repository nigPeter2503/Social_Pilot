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

/** Provider-partitioned, authenticated records. Never stores OAuth credentials in Room. */
public final class EncryptedOAuthStore {
    private static EncryptedOAuthStore shared;
    public static synchronized EncryptedOAuthStore shared(Context context){
        if(shared==null)shared=new EncryptedOAuthStore(context.getApplicationContext());
        return shared;
    }
    private static final String KEY_ALIAS="socialpilot.oauth.v1";
    private static final String[] EMPTY=new String[0];
    private static final String[] LEGACY_PENDING={"pending_session","pending_verifier"};
    private static final String[] LEGACY_CONNECTED={"page_id","page_name","access_token","token_type","expires_at"};
    private final SharedPreferences preferences;
    private final RecordCipher cipher;
    private final Gson gson=new Gson();
    private final Set<String> unavailable=new HashSet<>();
    public EncryptedOAuthStore(Context context){this(context.getApplicationContext().getSharedPreferences("oauth_secure_v1",Context.MODE_PRIVATE),new KeystoreCipher());}
    EncryptedOAuthStore(SharedPreferences p,RecordCipher c){preferences=p;cipher=c;}
    private static String provider(String p){if(!"FACEBOOK".equals(p)&&!"LINKEDIN".equals(p)) throw storageUnavailable();return p;}
    private static String key(String type,String p){return type+"_v3_"+provider(p);}
    // Compatibility methods continue to mean Facebook, never whichever account connected last.
    public synchronized void savePending(String id,String verifier){savePending("FACEBOOK",id,verifier,null);}
    public synchronized Pending getPending(){return getPending("FACEBOOK");}
    public synchronized void clearPending(){clearPending("FACEBOOK");}
    public synchronized String pageSummary(){return summary("FACEBOOK");}
    public synchronized String accessToken(){return accessToken("FACEBOOK");}
    public synchronized void savePending(String p,String id,String verifier,String expiresAt){
        PendingRecord r=new PendingRecord();r.version=3;r.provider=provider(p);r.sessionId=id;r.verifier=verifier;r.expiresAt=expiresAt;r.validate(p);
        write(key("pending",p),r,EMPTY);
    }
    public synchronized Pending getPending(String p){
        migrate("pending",p);
        PendingRecord r=read(key("pending",p),PendingRecord.class,EMPTY);
        if(r==null)return null;
        try{r.validate(p);return new Pending(r.provider,r.sessionId,r.verifier,r.expiresAt);}
        catch(RuntimeException e){invalidate(key("pending",p),EMPTY);return null;}
    }
    public synchronized Pending findPending(String session){
        Pending found=null;
        for(String p:new String[]{"FACEBOOK","LINKEDIN"}){
            Pending next=getPending(p);
            if(next!=null&&next.sessionId.equals(session)){if(found!=null)return null;found=next;}
        }
        return found;
    }
    public synchronized void clearPending(String p){
        invalidate(key("pending",p), "FACEBOOK".equals(p)?new String[]{"pending_v2","pending_session","pending_verifier"}:EMPTY);
    }
    public synchronized void clearPending(String p,String expectedSession){
        Pending r=getPending(p);if(r!=null&&r.sessionId.equals(expectedSession))clearPending(p);
    }
    public synchronized void saveConnected(OAuthExchangeResponse response){saveConnected(response,false);}
    public synchronized void saveConnected(OAuthExchangeResponse response,boolean replaceAccount){
        response.validate();String p=provider(response.provider);
        ConnectedRecord previous=connected(p);
        if(previous!=null&&!previous.connection.externalAccountId.equals(response.externalAccountId)){
            if(!replaceAccount)throw storageUnavailable();
            previous=null;
        }
        ConnectedRecord r=new ConnectedRecord();r.version=3;r.connection=response;
        r.channelId=previous==null?p+":"+response.externalAccountId:previous.channelId;
        if(previous!=null&&previous.revision==Long.MAX_VALUE)throw storageUnavailable();
        r.revision=previous==null?1:previous.revision+1;
        r.validate(p);write(key("connected",p),r,EMPTY);
    }
    public synchronized String summary(String p){
        ConnectedRecord r=connected(p);return r==null?null:r.connection.displayName+" ("+r.connection.externalAccountId+")";
    }
    public synchronized OAuthExchangeResponse connection(String p){ConnectedRecord r=connected(p);return r==null?null:r.connection;}
    public synchronized String accessToken(String p){OAuthExchangeResponse r=connection(p);return r==null?null:r.accessToken;}
    public synchronized String tokenFor(String p,String channelId,long revision){
        ConnectedRecord r=connected(p);
        return r!=null && r.channelId.equals(channelId) && r.revision==revision && !r.reauth && !expired(r.connection.expiresAt)?r.connection.accessToken:null;
    }
    public synchronized com.socialpilot.app.domain.contract.social.ChannelInfo channel(String p){
        ConnectedRecord r=connected(p);if(r==null)return null;
        var c=r.connection;
        var status=r.reauth||expired(c.expiresAt)?com.socialpilot.app.domain.contract.social.ConnectionStatus.REAUTH_REQUIRED:com.socialpilot.app.domain.contract.social.ConnectionStatus.CONNECTED;
        return new com.socialpilot.app.domain.contract.social.ChannelInfo(r.channelId,
            com.socialpilot.app.domain.contract.social.Platform.valueOf(p),c.externalAccountId,
            com.socialpilot.app.domain.contract.social.AccountType.valueOf(c.accountType),c.displayName,status,r.revision,key("connected",p));
    }
    public synchronized void requireReauth(String p,long revision){
        ConnectedRecord r=connected(p);if(r!=null&&r.revision==revision){r.reauth=true;write(key("connected",p),r,EMPTY);}
    }
    private ConnectedRecord connected(String p){
        migrate("connected",p);ConnectedRecord r=read(key("connected",p),ConnectedRecord.class,EMPTY);
        if(r==null)return null;
        try{r.validate(p);return r;}catch(RuntimeException e){invalidate(key("connected",p),EMPTY);return null;}
    }
    private void migrate(String type,String p){
        provider(p);if(!"FACEBOOK".equals(p))return;
        String dest=key(type,p),old=type+"_v2";
        if(unavailable.contains(dest)||preferences.contains(dest))return;
        String[] legacy="pending".equals(type)?LEGACY_PENDING:LEGACY_CONNECTED;
        com.google.gson.JsonObject json=read(old,com.google.gson.JsonObject.class,legacy);
        if(json==null)return;
        try{
            if(!json.has("version")||json.get("version").getAsInt()!=2)throw storageUnavailable();
            json.addProperty("version",3);
            if("pending".equals(type)){
                json.addProperty("provider","FACEBOOK");PendingRecord r=gson.fromJson(json,PendingRecord.class);r.validate(p);
                write(dest,r,EMPTY);
            }else{
                ConnectedRecord r=gson.fromJson(json,ConnectedRecord.class);
                r.channelId="FACEBOOK:"+r.connection.externalAccountId;r.revision=1;r.validate(p);
                write(dest,r,EMPTY);
            }
            // Only remove the source after the new encrypted record was durably committed.
            preferences.edit().remove(old).commit();
        }catch(RuntimeException e){
            // Preserve source until a successful migration. Never claim a failed save is connected.
            unavailable.add(dest);
        }
    }
    private void write(String name,Object record,String[] legacy){
        final String encrypted;
        try{encrypted=cipher.encrypt(name,gson.toJson(record));}catch(Exception e){throw storageUnavailable();}
        try{
            SharedPreferences.Editor editor=preferences.edit().putString(name,encrypted);
            for(String k:legacy)editor.remove(k);
            if(!editor.commit())throw storageUnavailable();
            unavailable.remove(name);
        }catch(RuntimeException e){invalidate(name,EMPTY);throw storageUnavailable();}
    }
    private <T>T read(String name,Class<T> type,String[] legacy){
        if(unavailable.contains(name))return null;
        try{
            String encrypted=preferences.getString(name,null);
            if(encrypted==null){for(String k:legacy)if(preferences.contains(k)){invalidate(name,legacy);break;}return null;}
            T r=gson.fromJson(cipher.decrypt(name,encrypted),type);if(r==null)throw storageUnavailable();return r;
        }catch(Exception e){invalidate(name,legacy);return null;}
    }
    private void invalidate(String name,String[] legacy){
        unavailable.add(name);
        try{SharedPreferences.Editor e=preferences.edit().remove(name);for(String k:legacy)e.remove(k);e.commit();}catch(RuntimeException ignored){}
    }
    public static boolean expired(String timestamp){
        if(timestamp==null)return false;
        try{
            java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",java.util.Locale.ROOT);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));f.setLenient(false);
            return !f.parse(timestamp).after(new java.util.Date());
        }catch(Exception e){return true;}
    }
    private static IllegalStateException storageUnavailable(){return new IllegalStateException("Secure storage unavailable");}
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
        int version;String provider,sessionId,verifier,expiresAt;
        void validate(String p){
            if(version!=3||!p.equals(provider)||sessionId==null||sessionId.trim().isEmpty()||verifier==null||!verifier.matches("[A-Za-z0-9._~-]{43,128}"))throw storageUnavailable();
        }
    }
    private static final class ConnectedRecord {
        int version;OAuthExchangeResponse connection;String channelId;long revision;boolean reauth;
        void validate(String p){
            if(version!=3||connection==null||channelId==null||channelId.trim().isEmpty()||revision<=0)throw storageUnavailable();
            connection.validate();
            if(!p.equals(connection.provider)||!("FACEBOOK".equals(p)?"PAGE":"PERSON").equals(connection.accountType))throw storageUnavailable();
        }
    }
    public static final class Pending {
        public final String provider,sessionId,verifier,expiresAt;
        Pending(String p,String id,String v,String expiry){provider=p;sessionId=id;verifier=v;expiresAt=expiry;}
    }
}
