package com.socialpilot.app.data.repository.social;

import com.google.gson.Gson;
import com.socialpilot.app.data.security.EncryptedOAuthStoreTest.MemoryPreferences;
import com.socialpilot.app.domain.contract.social.*;
import org.junit.*;
import static org.junit.Assert.*;

public class LinkedInAttemptLedgerTest {
    MemoryPreferences preferences;
    LinkedInAttemptLedger ledger;
    long now=1000;
    ChannelInfo channel(String id,long revision) {
        return new ChannelInfo(id,Platform.LINKEDIN,"member",AccountType.PERSON,"Member",ConnectionStatus.CONNECTED,revision,"reference");
    }
    PublishRequest request(String id,String channel,long revision) { return new PublishRequest(id,channel,revision,"version"); }
    @Before public void setup() { preferences=new MemoryPreferences(); ledger=new LinkedInAttemptLedger(preferences,()->now); }
    @Test public void rejectedSurvivesReloadAndOnlyNewApprovedRevisionCanRetry() {
        var old=request("first","LINKEDIN:member",1);
        String key=ledger.key(channel(old.channelId,1),old.approvedVersionId);
        assertTrue(ledger.begin(key,old,null));
        ledger.record(key,PublishResult.rejected(old,new SocialError(SocialError.Code.AUTH_REQUIRED,SocialError.NextAction.RECONNECT)),0);
        ledger=new LinkedInAttemptLedger(preferences.reboot(),()->now);
        var next=request("next",old.channelId,2);
        assertEquals(SocialError.Code.AUTH_REQUIRED,ledger.existing(key,next).error.code);
        assertFalse(ledger.begin(key,next,null));
        assertFalse(ledger.begin(key,request("same",old.channelId,1),"first"));
        assertTrue(ledger.begin(key,next,"first"));
        assertFalse(ledger.begin(key,request("double",old.channelId,2),"first"));
    }
    @Test public void permissionAndRateLimitRequireExplicitApprovalAndDeadline() {
        for(var error:new SocialError[]{new SocialError(SocialError.Code.PERMISSION_REQUIRED,SocialError.NextAction.RESOLVE_PERMISSION),new SocialError(SocialError.Code.RATE_LIMITED,SocialError.NextAction.WAIT)}) {
            preferences=new MemoryPreferences();ledger=new LinkedInAttemptLedger(preferences,()->now);
            var first=request("first","c",1);String key=ledger.key(channel("c",1),"version");
            assertTrue(ledger.begin(key,first,null));ledger.record(key,PublishResult.rejected(first,error),2000);
            assertFalse(ledger.begin(key,request("early","c",1),"first"));
            now=2000;
            assertFalse(ledger.begin(key,request("unapproved","c",1),null));
            assertTrue(ledger.begin(key,request("approved","c",1),"first"));
            now=1000;
        }
    }
    @Test public void uncertainAndPublishedStayBlockedAcrossChangedChannelAndReload() {
        for(int outcome=0;outcome<3;outcome++) {
            preferences=new MemoryPreferences();ledger=new LinkedInAttemptLedger(preferences,()->now);
            var first=request("first","old-channel",1);String key=ledger.key(channel("old-channel",1),"version");
            assertTrue(ledger.begin(key,first,null));
            PublishResult result=outcome==0?PublishResult.unknown(first):outcome==1?
                PublishResult.publishedUrlPending(first,"urn:li:share:123"):
                PublishResult.success(first,"urn:li:share:123","https://www.linkedin.com/posts/example");
            ledger.record(key,result,0);
            ledger=new LinkedInAttemptLedger(preferences.reboot(),()->now);
            assertEquals(key,ledger.key(channel("recovered-channel",1),"version"));
            assertFalse(ledger.begin(key,request("next","recovered-channel",1),"first"));
            assertEquals(result.outcome,ledger.existing(key,request("next","recovered-channel",1)).outcome);
        }
    }
    @Test public void confirmationCapturedForAKeepsNewerBUnchanged() {
        var a=request("A","c",1);String keyA=ledger.key(channel("c",1),"A");
        assertTrue(ledger.begin(keyA,a,null));ledger.record(keyA,PublishResult.publishedUrlPending(a,"urn:li:share:123"),0);
        var target=ledger.target();
        var b=request("B","c",1);String keyB=ledger.key(channel("c",1),"B");
        assertTrue(ledger.begin(keyB,b,null));ledger.record(keyB,PublishResult.publishedUrlPending(b,"urn:li:share:456"),0);
        String before=preferences.getString("last_payload",null);
        ledger.confirm(target,"https://www.linkedin.com/feed/update/urn:li:share:123/");
        assertEquals(before,preferences.getString("last_payload",null));
        assertEquals(PublishResult.Outcome.PUBLISHED_URL_PENDING,ledger.existing(keyB,b).outcome);
        ledger=new LinkedInAttemptLedger(preferences.reboot(),()->now);
        ledger.confirm(target,"https://www.linkedin.com/feed/update/urn:li:share:123/");
        assertEquals(PublishResult.Outcome.SUCCESS,ledger.existing(keyA,a).outcome);
        preferences.edit().putString(keyA,new Gson().toJson(PublishResult.publishedUrlPending(b,"urn:li:share:456"))).commit();
        assertThrows(IllegalStateException.class,()->ledger.confirm(target,"https://www.linkedin.com/posts/A"));
    }
    @Test public void legacyAndMalformedRecordsRemainReserved() {
        var first=request("first","LINKEDIN:member",1);
        String oldKey="attempt_"+LinkedInAttemptLedger.hash(first.channelId+":version");
        preferences.edit().putString(oldKey,new Gson().toJson(PublishResult.publishedUrlPending(first,"urn:li:share:123")))
            .putString("last_key",oldKey).putString("last_payload",new Gson().toJson(PublishResult.publishedUrlPending(first,"urn:li:share:123"))).commit();
        String key=ledger.key(channel("LINKEDIN:member:new",1),"version");
        assertEquals(key,ledger.target().key);
        assertFalse(ledger.begin(key,request("retry","LINKEDIN:member:new",1),null));
        preferences.edit().putString(key,"broken").commit();
        assertFalse(ledger.begin(key,first,"first"));
        assertEquals(PublishResult.Outcome.UNKNOWN,ledger.existing(key,first).outcome);
    }
    @Test public void legacyRejectionCannotHidePublishedAttemptForSameAccountAndContent() {
        var published=request("published","LINKEDIN:member",1);
        var rejected=request("rejected","LINKEDIN:member:recovered",1);
        String firstKey="attempt_"+LinkedInAttemptLedger.hash(published.channelId+":version");
        String secondKey="attempt_"+LinkedInAttemptLedger.hash(rejected.channelId+":version");
        preferences.edit().putString(firstKey,new Gson().toJson(PublishResult.publishedUrlPending(published,"urn:li:share:123")))
            .putString(secondKey,new Gson().toJson(PublishResult.rejected(rejected,new SocialError(SocialError.Code.PERMISSION_REQUIRED,SocialError.NextAction.RESOLVE_PERMISSION)))).commit();
        String key=ledger.key(channel(rejected.channelId,1),"version");
        assertFalse(ledger.begin(key,request("retry",rejected.channelId,1),"rejected"));
        assertEquals(PublishResult.Outcome.PUBLISHED_URL_PENDING,ledger.existing(key,rejected).outcome);
    }
    @Test public void retryAfterParsesSecondsAndHttpDate() {
        assertEquals(11000,LinkedInSocialConnector.retryAfterMillis("10",1000));
        assertEquals(0,LinkedInSocialConnector.retryAfterMillis("bad",1000));
        assertEquals(10000,LinkedInSocialConnector.retryAfterMillis("Thu, 01 Jan 1970 00:00:10 GMT",1000));
        assertEquals(Long.MAX_VALUE,LinkedInSocialConnector.retryAfterMillis(Long.toString(Long.MAX_VALUE),1000));
    }
}
