package com.socialpilot.app.data.repository.social;
import com.google.gson.Gson;
import com.socialpilot.app.domain.contract.social.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class LinkedInPostVerificationTest {
    PublishRequest request=new PublishRequest("req","channel",1,"version");
    String json(PublishResult r){return new Gson().toJson(r);}
    @Test public void ownerCanConfirmActivityUrlForKnownShareId(){
        var r=LinkedInPostVerification.ownerConfirmed(json(PublishResult.publishedUrlPending(request,"urn:li:share:123")),"https://www.linkedin.com/feed/update/urn:li:activity:456/");
        assertEquals(PublishResult.Outcome.SUCCESS,r.outcome);assertEquals("urn:li:share:123",r.postId);assertEquals("req",r.requestId);
    }
    @Test public void unknownCannotBecomeSuccessJustByPastingUrl(){
        assertThrows(IllegalStateException.class,()->LinkedInPostVerification.ownerConfirmed(json(PublishResult.unknown(request)),"https://www.linkedin.com/feed/update/urn:li:activity:456/"));
    }
    @Test public void unrelatedAndNonHttpsLinksAreRejected(){
        String pending=json(PublishResult.publishedUrlPending(request,"urn:li:share:123"));
        for(String url:new String[]{"http://www.linkedin.com/feed/update/urn:li:activity:456/","https://attacker.test/feed/update/urn:li:activity:456/","https://www.linkedin.com/in/member","https://user@www.linkedin.com/feed/update/urn:li:activity:456/"})
            assertThrows(IllegalArgumentException.class,()->LinkedInPostVerification.ownerConfirmed(pending,url));
    }
}
