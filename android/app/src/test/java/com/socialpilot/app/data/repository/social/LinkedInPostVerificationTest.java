package com.socialpilot.app.data.repository.social;
import com.google.gson.Gson;
import com.socialpilot.app.domain.contract.social.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class LinkedInPostVerificationTest {
    PublishRequest request=new PublishRequest("req","channel",1,"version");
    String json(PublishResult r){return new Gson().toJson(r);}
    @Test public void conflictingSameTypeUrnIsRejected(){
        for(String type:new String[]{"share","ugcPost"}){
            String payload=json(PublishResult.publishedUrlPending(request,"urn:li:"+type+":123"));
            assertThrows(IllegalArgumentException.class,()->LinkedInPostVerification.ownerConfirmed(
                payload,"https://www.linkedin.com/feed/update/urn:li:"+type+":456/"));
            assertEquals(PublishResult.Outcome.SUCCESS,LinkedInPostVerification.ownerConfirmed(
                payload,"https://www.linkedin.com/feed/update/urn:li:"+type+":123/").outcome);
        }
    }
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
    @Test public void fullPostsUrlWorksAndShortUrlIsExplicitlyRejected(){
        String pending=json(PublishResult.publishedUrlPending(request,"urn:li:share:123"));
        assertEquals(PublishResult.Outcome.SUCCESS,LinkedInPostVerification.ownerConfirmed(pending,"https://www.linkedin.com/posts/member-test-activity-456-abc?utm_source=share").outcome);
        assertThrows(IllegalArgumentException.class,()->LinkedInPostVerification.ownerConfirmed(pending,"https://lnkd.in/p/fake"));
    }
    @Test public void repeatedPublishRestoresKnownEvidenceWithCurrentRequestIdentity(){
        var pending=PublishResult.publishedUrlPending(request,"urn:li:share:123");
        var next=new PublishRequest("next","channel",2,"version");
        var result=LinkedInPostVerification.previousResult(next,json(pending));
        assertEquals("next",result.requestId);assertEquals(2,result.connectionRevision);
        assertEquals(PublishResult.Outcome.PUBLISHED_URL_PENDING,result.outcome);assertEquals("urn:li:share:123",result.postId);
        assertNull(LinkedInPostVerification.previousResult(next,json(PublishResult.unknown(request))));
        assertNull(LinkedInPostVerification.previousResult(next,"UNKNOWN:inflight"));
    }
    @Test public void confirmingTheSameVerifiedUrlIsIdempotent(){
        String url="https://www.linkedin.com/feed/update/urn:li:activity:456/";
        var success=LinkedInPostVerification.ownerConfirmed(json(PublishResult.publishedUrlPending(request,"urn:li:share:123")),url);
        assertEquals(PublishResult.Outcome.SUCCESS,LinkedInPostVerification.ownerConfirmed(json(success),url).outcome);
    }
}
