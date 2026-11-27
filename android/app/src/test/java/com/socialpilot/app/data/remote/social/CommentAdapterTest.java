package com.socialpilot.app.data.remote.social;

import com.google.gson.*;
import com.socialpilot.app.data.local.comment.CommentPageWriter;
import com.socialpilot.app.data.local.publishedpost.PublishedPostScope;
import com.socialpilot.app.data.local.publishedpost.SimulatedCommentStore;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.social.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.*;
import static org.junit.Assert.*;
import static com.socialpilot.app.data.remote.social.CommentPageRequest.*;
import static com.socialpilot.app.data.remote.social.CommentPageResult.*;
import static com.socialpilot.app.data.remote.social.CommentRecord.*;

/** MO_PHONG only. Includes sanitized SP-018/SP-019/SP-066 evidence; mutations are synthetic. */
public class CommentAdapterTest {
    private static final List<Account> LEGACY_FIXTURE_ACCOUNTS =
            Arrays.asList(Account.FACEBOOK_PAGE, Account.LINKEDIN_MEMBER, Account.LINKEDIN_ORGANIZATION);
    private static final long NOW = 1795075200000L;
    private static final String TOKEN = "synthetic-comment-secret-DO-NOT-EXPOSE";
    private static final String THREAD = "urn:li:activity:9000000000000000002";
    private MockWebServer server;
    private ExecutorService executor;
    private AtomicReference<CommentAdapter.Binding> binding;
    private AtomicInteger credentialReads;
    private AtomicReference<ChannelInfo> credentialSubject;
    private CommentCursorCodec codec;
    private OkHttpClient client;

    @Before public void setUp() throws Exception {
        server = new MockWebServer(); server.start();
        executor = Executors.newSingleThreadExecutor();
        binding = new AtomicReference<>(); credentialReads = new AtomicInteger();
        credentialSubject = new AtomicReference<>();
        codec = new CommentCursorCodec(new SecretKeySpec(new byte[32], "AES"));
        client = new OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build();
    }
    @After public void tearDown() throws Exception {
        executor.shutdownNow(); executor.awaitTermination(3, TimeUnit.SECONDS);
        client.dispatcher().executorService().shutdownNow(); client.connectionPool().evictAll();
        server.shutdown();
    }

    private CommentPageRequest request(Account account) {
        if (account == Account.INSTAGRAM_PROFESSIONAL)
            return new CommentPageRequest(account, "channel-ig", 1, "ig-user", "media", Target.POST,
                    "media", null, Query.IG_COMMENTS, 2, 1000L, "v27.0", null);
        boolean fb = account == Account.FACEBOOK_PAGE;
        String post = fb ? "000Post_Aa" : "urn:li:share:9000000000000000001";
        return new CommentPageRequest(account, "channel-000A", 1,
                fb ? "000Page_Aa" : account == Account.LINKEDIN_MEMBER ? "urn:li:person:synthetic-Member"
                        : "urn:li:organization:00010", post, Target.POST, post, fb ? null : THREAD,
                fb ? Query.FB_TOPLEVEL : Query.LI_COMMENTS, 2, 1000L, fb ? "v27.0" : "202610", null);
    }
    private ChannelCapability capability(SocialOperation op, CapabilityAvailability availability) {
        return new ChannelCapability(op, availability, availability == CapabilityAvailability.AVAILABLE ? null : "fixture-only",
                availability == CapabilityAvailability.UNVERIFIED ? null : "2026-11-19T01:00:00Z");
    }
    private CommentAdapter.Binding makeBinding(CommentPageRequest r, CapabilityAvailability read,
            CapabilityAvailability reply, ConnectionStatus status, String tokenRef, String generation,
            String mapping, Map<String, CommentAdapter.ItemEvidence> facts) {
        AccountType type = r.account.type;
        ChannelInfo c = new ChannelInfo(r.channelId, r.platform, r.externalAccountId, type, "Synthetic account",
                status, r.connectionRevision, status == ConnectionStatus.DISCONNECTED ? null : tokenRef);
        return new CommentAdapter.Binding(c, r, capability(SocialOperation.READ_COMMENTS, read),
                capability(SocialOperation.REPLY_COMMENT, reply), generation, mapping, 2, facts);
    }
    private void bind(CommentPageRequest r) {
        binding.set(makeBinding(r, CapabilityAvailability.AVAILABLE, CapabilityAvailability.UNVERIFIED,
                ConnectionStatus.CONNECTED, "secret-ref-1", "generation-1", "synthetic-post-thread", null));
    }
    private CommentAdapter adapter(CommentPageRequest r) { return adapter(r, () -> NOW); }
    private CommentAdapter adapter(CommentPageRequest r, CommentAdapter.Clock clock) {
        CommentAdapter.CredentialSource credentials = c -> {
            credentialSubject.set(c); credentialReads.incrementAndGet(); return TOKEN;
        };
        return createAdapter(r, clock, credentials);
    }
    private CommentAdapter createAdapter(CommentPageRequest r, CommentAdapter.Clock clock, CommentAdapter.CredentialSource creds) {
        if (r.platform == Platform.INSTAGRAM)
            return new InstagramCommentAdapter(client, ignored -> binding.get(), creds, clock, executor, codec,
                    server.url("/"), Source.MO_PHONG, "synthetic-comments");
        return r.platform == Platform.FACEBOOK
                ? new FacebookCommentAdapter(client, ignored -> binding.get(), creds, clock, executor, codec,
                        server.url("/"), Source.MO_PHONG, "synthetic-comments")
                : new LinkedInCommentAdapter(client, ignored -> binding.get(), creds, clock, executor, codec,
                        server.url("/rest/"), Source.MO_PHONG, "synthetic-comments");
    }
    private CommentPageResult read(CommentAdapter adapter, CommentPageRequest r) throws Exception {
        CompletableFuture<CommentPageResult> result = new CompletableFuture<>();
        adapter.read(r, result::complete); return result.get(4, TimeUnit.SECONDS);
    }
    private CommentPageResult response(CommentPageRequest r, JsonObject body) throws Exception {
        server.enqueue(new MockResponse().setBody(body.toString())); return read(adapter(r), r);
    }
    private void failure(CommentPageResult result, Failure reason) {
        assertEquals(Status.FAILURE, result.status); assertNull(result.page); assertEquals(reason, result.failure);
    }
    private void rejectedWithoutCredential(CommentPageRequest r, Failure reason) throws Exception {
        failure(read(adapter(r), r), reason); assertEquals(0, server.getRequestCount()); assertEquals(0, credentialReads.get());
    }

    @Test public void facebookTwoPagesResumeOverlapAndSafeContinuation() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        JsonObject first = page(r, 0);
        first.getAsJsonObject("paging").addProperty("next", "https://evil.invalid/steal?access_token=" + TOKEN);
        first.getAsJsonObject("paging").getAsJsonObject("cursors").addProperty("after", "A+/=%2F Case");
        CommentPageResult a = response(r, first);
        assertEquals(Status.SUCCESS, a.status); assertEquals(Boolean.TRUE, a.page.hasMore);
        assertEquals(Source.MO_PHONG, a.page.source); assertEquals(NOW, a.page.fetchedAt);
        assertEquals("Tác giả mẫu", a.page.items.get(0).authorDisplayName);
        assertFalse(a.page.nextCursor.contains("A+/=")); assertFalse(a.page.nextCursor.contains(TOKEN));
        RecordedRequest firstWire = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("/v27.0/000Post_Aa/comments", firstWire.getRequestUrl().encodedPath());
        assertEquals("toplevel", firstWire.getRequestUrl().queryParameter("filter"));
        assertEquals("Bearer " + TOKEN, firstWire.getHeader("Authorization"));
        CommentPageRequest resumed = r.resume(a.page.nextCursor);
        // Fresh adapter and codec instance with same key can resume after process recreation.
        codec = new CommentCursorCodec(new SecretKeySpec(new byte[32], "AES"));
        CommentPageResult b = response(resumed, page(r, 1));
        assertEquals(Status.SUCCESS, b.status); assertEquals(Boolean.FALSE, b.page.hasMore); assertNull(b.page.nextCursor);
        RecordedRequest secondWire = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("A+/=%2F Case", secondWire.getRequestUrl().queryParameter("after"));
        assertEquals(firstWire.getRequestUrl().host(), secondWire.getRequestUrl().host());
        assertEquals(firstWire.getRequestUrl().encodedPath(), secondWire.getRequestUrl().encodedPath());
        assertEquals(a.page.items.get(0).commentId, b.page.items.get(0).commentId);
        assertEquals(2, b.page.items.size()); // Adapter keeps overlapping identity, no dedup.
        assertEquals(r.externalAccountId, credentialSubject.get().externalAccountId);
    }

    @Test public void instagramPagingReplyAndScope() throws Exception {
        CommentPageRequest r = new CommentPageRequest(Account.INSTAGRAM_PROFESSIONAL, "channel-ig", 2,
                "ig-user", "media", Target.POST, "media", null, Query.IG_COMMENTS, 2, null, "v27.0", null);
        bind(r);
        JsonObject first = JsonParser.parseString("{'data':[{'id':'c1','text':'hello'}],"
                + "'paging':{'cursors':{'after':'next-page'},'next':'https://example.invalid'}}").getAsJsonObject();
        SimulatedCommentStore store = new SimulatedCommentStore();
        store.seed(new PublishedPostScope(r.platform, r.account.type, r.channelId, r.connectionRevision,
                r.externalAccountId), r.postId);
        CommentPageWriter writer = CommentPageWriter.forSimulation(store, codec);
        CommentPageWriter.Attempt initial = writer.prepare(r);
        CommentPageResult a = response(initial.request, first);
        assertEquals(Status.SUCCESS, a.status);
        assertEquals("hello", a.page.items.get(0).message);
        assertEquals("/v27.0/media/comments", server.takeRequest().getRequestUrl().encodedPath());
        writer.commit(initial, new CommentPageReceipt(initial.request, a));
        CommentPageWriter.Attempt next = writer.prepare(r);
        CommentPageResult b = response(next.request, JsonParser.parseString(
                "{'data':[{'id':'c1'},{'id':'c2'}]}").getAsJsonObject());
        assertEquals("next-page", server.takeRequest().getRequestUrl().queryParameter("after"));
        assertEquals(a.page.items.get(0).commentId, b.page.items.get(0).commentId);
        assertEquals(ContentState.OMITTED, b.page.items.get(1).contentState);
        assertEquals(Boolean.FALSE, b.page.hasMore);
        writer.commit(next, new CommentPageReceipt(next.request, b));
        assertEquals(2, store.size());
        first.addProperty("media_id", "other-media");
        failure(response(r, first), Failure.INVALID_RESPONSE);
        server.takeRequest();
        CommentPageRequest replies = new CommentPageRequest(r.account, r.channelId, r.connectionRevision,
                r.externalAccountId, r.postId, Target.COMMENT, "c1", null, r.query, 2, null, r.apiVersion, null);
        bind(replies);
        JsonObject body = JsonParser.parseString("{'data':[{'id':'reply'}]}").getAsJsonObject();
        assertEquals("c1", response(replies, body).page.items.get(0).parentCommentId);
        assertEquals("/v27.0/c1/replies", server.takeRequest().getRequestUrl().encodedPath());
        body.getAsJsonArray("data").get(0).getAsJsonObject().addProperty("parent_id", "other");
        failure(response(replies, body), Failure.INVALID_RESPONSE);
    }

    @Test public void instagramRequiresMappingAndClassifiesGraphFailures() throws Exception {
        CommentPageRequest r = new CommentPageRequest(Account.INSTAGRAM_PROFESSIONAL, "ch-ig", 1, "ig-user",
                "media", Target.POST, "media", null, Query.IG_COMMENTS, 2, null, "v27.0", null);
        binding.set(makeBinding(r, CapabilityAvailability.AVAILABLE, CapabilityAvailability.UNVERIFIED,
                ConnectionStatus.CONNECTED, "secret-ref", "generation", null, null));
        rejectedWithoutCredential(r, Failure.MAPPING_UNVERIFIED);
        bind(r);
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{'error':{'code':190}}"));
        assertEquals(SocialError.Code.AUTH_REQUIRED, read(adapter(r), r).error.code);
        server.enqueue(new MockResponse().setResponseCode(429).addHeader("Retry-After", "3"));
        CommentPageResult limited = read(adapter(r), r);
        assertEquals(SocialError.Code.RATE_LIMITED, limited.error.code);
        assertEquals(Long.valueOf(NOW + 3000), limited.retryAfterAt);
    }

    private JsonObject instagramEvidence(String name) throws Exception {
        return JsonParser.parseString(new String(Files.readAllBytes(Paths.get(
                "../../docs/api-verification/instagram/evidence/responses", name)),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private CommentPageRequest evidenceQuery(Target target) {
        return new CommentPageRequest(Account.INSTAGRAM_PROFESSIONAL, "simulation-sp066", 7,
                "17841459976957663", "18111630677338097", target,
                target == Target.POST ? "18111630677338097" : "18105580130575258",
                null, Query.IG_COMMENTS, 2, null, "v27.0", null);
    }

    // Redacted provider cursors cannot establish continuity. Only the item payloads
    // are evidence-derived; paging, grants, receipt time and Channel are synthetic.
    private JsonObject simulatedContinuation(String file, String after) throws Exception {
        JsonObject body = instagramEvidence(file);
        JsonObject paging = new JsonObject();
        JsonObject cursors = new JsonObject();
        cursors.addProperty("after", after);
        paging.add("cursors", cursors);
        paging.addProperty("next", "https://example.invalid/synthetic-continuation");
        body.add("paging", paging);
        return body;
    }

    @Test public void sp066PagesRollbackResumeAndRefreshKeepOpaqueIds() throws Exception {
        CommentPageRequest q = evidenceQuery(Target.POST); bind(q);
        SimulatedCommentStore store = new SimulatedCommentStore();
        store.seed(new PublishedPostScope(q.platform, q.account.type, q.channelId,
                q.connectionRevision, q.externalAccountId), q.postId);
        CommentPageWriter writer = CommentPageWriter.forSimulation(store, codec);
        CommentPageWriter.Attempt first = writer.prepare(q);
        CommentPageResult a = response(first.request,
                simulatedContinuation("IG-03_page1_response.json", "simulation-page-two"));
        assertEquals(Status.SUCCESS, a.status);
        writer.commit(first, new CommentPageReceipt(first.request, a));
        String localPostId = store.row(q.platform, q.channelId, "18105580130575258").localPostId;
        // Reconstruct the writer/codec against the same RAM store; this is not process death or Room readback.
        codec = new CommentCursorCodec(new SecretKeySpec(new byte[32], "AES"));
        writer = CommentPageWriter.forSimulation(store, codec);
        CommentPageWriter.Attempt next = writer.prepare(q);
        JsonObject overlap = simulatedContinuation("IG-03_page2_response.json", "simulation-terminal");
        overlap.getAsJsonArray("data").add(instagramEvidence("IG-03_page1_response.json")
                .getAsJsonArray("data").get(0));
        CommentPageResult b = response(next.request, overlap);
        assertEquals(Status.SUCCESS, b.status);
        server.takeRequest();
        assertEquals("simulation-page-two", server.takeRequest().getRequestUrl().queryParameter("after"));
        store.failCheckpoint = true;
        try {
            writer.commit(next, new CommentPageReceipt(next.request, b));
            fail("checkpoint failure must roll back the new comment");
        } catch (CommentPageWriter.Rejected expected) {
            assertEquals(CommentPageWriter.Failure.STORAGE, expected.reason);
        }
        assertEquals(1, store.size());
        assertSame(next.previous, writer.prepare(q).previous);
        store.failCheckpoint = false;
        writer.commit(next, new CommentPageReceipt(next.request, b));
        assertEquals(2, store.size());
        CommentPageWriter.Attempt terminal = writer.prepare(q);
        CommentPageResult c = response(terminal.request, instagramEvidence("IG-03_terminal_page_response.json"));
        assertEquals(Boolean.FALSE, c.page.hasMore);
        assertNull(c.page.nextCursor);
        writer.commit(terminal, new CommentPageReceipt(terminal.request, c));
        CommentPageWriter.Attempt refresh = writer.prepare(q);
        assertNull(refresh.request.cursor);
        CommentPageResult d = response(refresh.request,
                simulatedContinuation("IG-03_refresh_page1_response.json", "simulation-page-two"));
        writer.commit(refresh, new CommentPageReceipt(refresh.request, d));
        assertEquals(3, store.size()); // Only observed pages: no invented missing C02 page.
        assertEquals(localPostId, store.row(q.platform, q.channelId, "18133111096696939").localPostId);
        assertEquals(localPostId, store.row(q.platform, q.channelId, "18105580130575258").localPostId);
        assertEquals(Source.MO_PHONG, store.row(q.platform, q.channelId, "18105580130575258").observation.source);
    }

    @Test public void sp066ReplyReadbackUsesBoundParentAndNeverPosts() throws Exception {
        CommentPageRequest q = evidenceQuery(Target.COMMENT); bind(q);
        CommentPageResult before = response(q, instagramEvidence("IG-04_replies_before_response.json"));
        assertEquals(Status.SUCCESS, before.status);
        assertTrue(before.page.items.isEmpty());
        assertEquals(Boolean.FALSE, before.page.hasMore);
        CommentPageResult after = response(q, instagramEvidence("IG05_get_replies_readback.json"));
        assertEquals(Status.SUCCESS, after.status);
        CommentRecord reply = after.page.items.get(0);
        assertEquals("18120004997517397", reply.commentId);
        assertEquals(q.targetId, reply.parentCommentId);
        assertEquals(ParentState.KNOWN, reply.parentState);
        assertEquals(q.postId, reply.postId);
        assertNull(reply.authorDisplayName); // username was not captured by Explorer.
        for (int i = 0; i < 2; i++) {
            RecordedRequest wire = server.takeRequest();
            assertEquals("GET", wire.getMethod());
            assertEquals("/v27.0/18105580130575258/replies", wire.getRequestUrl().encodedPath());
        }
    }

    @Test public void sp066InsightPermissionEvidenceDoesNotGrantCommentPermission() throws Exception {
        CommentPageRequest q = evidenceQuery(Target.POST); bind(q);
        // IG-12 is an insights response. It is not scoped READ_COMMENTS evidence.
        // HTTP 200 here is synthetic and proves a Graph error envelope is never an empty success.
        CommentPageResult denied = response(q, instagramEvidence("IG12_token_B_denied.json"));
        failure(denied, Failure.PROVIDER_ERROR);
        assertNull(denied.error);
    }

    private void linkedinTwoPages(Account account) throws Exception {
        CommentPageRequest r = request(account); bind(r);
        JsonObject first = page(r, 0);
        first.getAsJsonObject("paging").getAsJsonArray("links").get(0).getAsJsonObject()
                .addProperty("href", "https://evil.invalid/steal?start=2&count=2&access_token=" + TOKEN);
        CommentPageResult a = response(r, first);
        assertEquals(Status.SUCCESS, a.status); assertEquals(Boolean.TRUE, a.page.hasMore);
        RecordedRequest wire = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("/rest/socialActions/urn%3Ali%3Ashare%3A9000000000000000001/comments", wire.getRequestUrl().encodedPath());
        assertEquals("202610", wire.getHeader("LinkedIn-Version")); assertEquals("2.0.0", wire.getHeader("X-Restli-Protocol-Version"));
        assertEquals("0", wire.getRequestUrl().queryParameter("start"));
        CommentPageResult b = response(r.resume(a.page.nextCursor), page(r, 1));
        assertEquals(Status.SUCCESS, b.status); assertEquals(Boolean.FALSE, b.page.hasMore); assertNull(b.page.nextCursor);
        RecordedRequest next = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("2", next.getRequestUrl().queryParameter("start")); assertEquals("2", next.getRequestUrl().queryParameter("count"));
        assertEquals(wire.getRequestUrl().encodedPath(), next.getRequestUrl().encodedPath());
        assertEquals(a.page.items.get(0).commentId, b.page.items.get(0).commentId);
        assertTrue(a.page.items.get(0).commentId.startsWith("urn:li:comment:(" + THREAD));
        assertEquals(CapabilityAvailability.UNVERIFIED, a.page.items.get(0).replyCapability.availability);
        assertEquals(account == Account.LINKEDIN_MEMBER ? AccountType.PERSON : AccountType.ORGANIZATION, credentialSubject.get().accountType);
    }
    @Test public void linkedinMemberTwoPages() throws Exception { linkedinTwoPages(Account.LINKEDIN_MEMBER); }
    @Test public void linkedinOrganizationTwoPages() throws Exception { linkedinTwoPages(Account.LINKEDIN_ORGANIZATION); }

    @Test public void unverifiedAdmissionDoesNotReadCredentialOrSend() throws Exception { blocked(CapabilityAvailability.UNVERIFIED); }
    @Test public void permissionAdmissionDoesNotReadCredentialOrSend() throws Exception { blocked(CapabilityAvailability.PERMISSION_REQUIRED); }
    @Test public void unsupportedAdmissionDoesNotReadCredentialOrSend() throws Exception { blocked(CapabilityAvailability.UNSUPPORTED); }
    private void blocked(CapabilityAvailability availability) throws Exception {
        for (Account account : Account.values()) {
            CommentPageRequest r = request(account);
            binding.set(makeBinding(r, availability, CapabilityAvailability.AVAILABLE, ConnectionStatus.CONNECTED,
                    "ref", "generation", "mapping", null));
            CommentPageResult result = read(adapter(r), r);
            failure(result, Failure.CAPABILITY_BLOCKED); assertEquals(availability, result.blockedAvailability);
            if (availability == CapabilityAvailability.UNVERIFIED) assertNull(result.error);
            else assertEquals(availability == CapabilityAvailability.PERMISSION_REQUIRED
                    ? SocialError.Code.PERMISSION_REQUIRED : SocialError.Code.UNSUPPORTED_OPERATION, result.error.code);
        }
        assertEquals(0, server.getRequestCount()); assertEquals(0, credentialReads.get());
    }

    @Test public void wrongAccountSubjectIsBlocked() throws Exception {
        CommentPageRequest r = request(Account.LINKEDIN_MEMBER); bind(variant(r, "accountId"));
        rejectedWithoutCredential(r, Failure.SCOPE_MISMATCH);
    }
    @Test public void wrongAccountTypeIsBlocked() throws Exception {
        CommentPageRequest r = request(Account.LINKEDIN_MEMBER); bind(request(Account.LINKEDIN_ORGANIZATION));
        rejectedWithoutCredential(r, Failure.SCOPE_MISMATCH);
    }
    @Test public void wrongPostIsBlocked() throws Exception { scopeMismatch("post"); }
    @Test public void wrongQueryIsBlocked() throws Exception { scopeMismatch("query"); }
    @Test public void wrongRevisionIsBlocked() throws Exception { scopeMismatch("revision"); }
    private void scopeMismatch(String field) throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(variant(r, field)); rejectedWithoutCredential(r, Failure.SCOPE_MISMATCH);
    }
    @Test public void publishCapabilityCannotAdmitRead() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        CommentAdapter.Binding b = binding.get();
        binding.set(new CommentAdapter.Binding(b.channel, r, capability(SocialOperation.PUBLISH_TEXT, CapabilityAvailability.AVAILABLE),
                b.replyCapability, b.bindingId, null, 2, null));
        rejectedWithoutCredential(r, Failure.SCOPE_MISMATCH);
    }
    @Test public void missingPostThreadMappingBlocksLinkedIn() throws Exception {
        CommentPageRequest r = request(Account.LINKEDIN_MEMBER);
        binding.set(makeBinding(r, CapabilityAvailability.AVAILABLE, CapabilityAvailability.UNVERIFIED,
                ConnectionStatus.CONNECTED, "ref", "generation", null, null));
        rejectedWithoutCredential(r, Failure.MAPPING_UNVERIFIED);
    }
    @Test public void pageLimitComesFromScopedBinding() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r); CommentAdapter.Binding b = binding.get();
        binding.set(new CommentAdapter.Binding(b.channel, r, b.readCapability, b.replyCapability, b.bindingId, null, 1, null));
        rejectedWithoutCredential(r, Failure.SCOPE_MISMATCH);
    }
    @Test public void disconnectedAdmissionHasReconnectError() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE);
        binding.set(makeBinding(r, CapabilityAvailability.AVAILABLE, CapabilityAvailability.UNVERIFIED,
                ConnectionStatus.DISCONNECTED, null, "generation", null, null));
        CommentPageResult result = read(adapter(r), r); failure(result, Failure.CHANNEL_DISCONNECTED);
        assertEquals(SocialError.Code.CHANNEL_DISCONNECTED, result.error.code); assertEquals(SocialError.NextAction.RECONNECT, result.error.nextAction);
        assertEquals(0, credentialReads.get()); assertEquals(0, server.getRequestCount());
    }
    @Test public void reauthAdmissionDoesNotUseOldCredential() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE);
        binding.set(makeBinding(r, CapabilityAvailability.AVAILABLE, CapabilityAvailability.UNVERIFIED,
                ConnectionStatus.REAUTH_REQUIRED, "ref", "generation", null, null));
        CommentPageResult result = read(adapter(r), r); assertEquals(SocialError.Code.AUTH_REQUIRED, result.error.code);
        assertEquals(0, credentialReads.get());
    }

    @Test public void revisionChangeWhileCredentialLoadsPreventsSend() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        CommentAdapter a = createAdapter(r, () -> NOW, c -> { bind(variant(r, "revision")); return TOKEN; });
        failure(read(a, r), Failure.SCOPE_MISMATCH); assertEquals(0, server.getRequestCount());
    }
    @Test public void tokenReferenceChangeBeforeSendIsBlocked() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        CommentAdapter a = createAdapter(r, () -> NOW, c -> {
            binding.set(makeBinding(r, CapabilityAvailability.AVAILABLE, CapabilityAvailability.UNVERIFIED,
                    ConnectionStatus.CONNECTED, "replacement-ref", "generation-1", "synthetic-post-thread", null)); return TOKEN;
        });
        failure(read(a, r), Failure.SCOPE_MISMATCH); assertEquals(0, server.getRequestCount());
    }
    @Test public void wrongCredentialSubjectNeverRequested() throws Exception {
        CommentPageRequest r = request(Account.LINKEDIN_MEMBER); bind(r);
        response(r, one(r));
        ChannelInfo subject = credentialSubject.get();
        assertEquals(r.externalAccountId, subject.externalAccountId); assertEquals(r.channelId, subject.channelId);
        assertEquals(r.connectionRevision, subject.connectionRevision); assertEquals("secret-ref-1", subject.tokenReference);
    }
    @Test public void revisionChangeDuringHttpRejectsResponse() throws Exception { inFlight("revision"); }
    @Test public void disconnectDuringHttpRejectsResponse() throws Exception { inFlight("disconnect"); }
    @Test public void accountChangeDuringHttpRejectsResponse() throws Exception { inFlight("accountId"); }
    @Test public void bindingChangeDuringHttpRejectsResponse() throws Exception { inFlight("generation"); }
    @Test public void replyCapabilityChangeDuringHttpRejectsStaleRecord() throws Exception { inFlight("reply"); }
    private void inFlight(String change) throws Exception {
        CommentPageRequest r = request(Account.LINKEDIN_MEMBER); bind(r);
        server.enqueue(new MockResponse().setBody(one(r).toString()).setBodyDelay(150, TimeUnit.MILLISECONDS));
        CompletableFuture<CommentPageResult> result = new CompletableFuture<>();
        adapter(r).read(r, result::complete); assertNotNull(server.takeRequest(1, TimeUnit.SECONDS));
        if (change.equals("disconnect")) binding.set(makeBinding(r, CapabilityAvailability.AVAILABLE, CapabilityAvailability.UNVERIFIED,
                ConnectionStatus.DISCONNECTED, null, "generation-1", "synthetic-post-thread", null));
        else if (change.equals("generation") || change.equals("reply")) {
            binding.set(makeBinding(r, CapabilityAvailability.AVAILABLE, change.equals("reply") ? CapabilityAvailability.AVAILABLE
                    : CapabilityAvailability.UNVERIFIED, ConnectionStatus.CONNECTED, "secret-ref-1",
                    change.equals("generation") ? "generation-2" : "generation-1", "synthetic-post-thread", null));
        } else bind(variant(r, change));
        failure(result.get(3, TimeUnit.SECONDS), change.equals("disconnect") ? Failure.CHANNEL_DISCONNECTED : Failure.SCOPE_MISMATCH);
    }
    @Test public void guardRunsAfterReceiptClockAndBeforeCallback() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        CommentAdapter a = adapter(r, () -> { bind(variant(r, "revision")); return NOW; });
        server.enqueue(new MockResponse().setBody(one(r).toString())); failure(read(a, r), Failure.SCOPE_MISMATCH);
    }

    @Test public void opaqueIdsEmptyMessageAndMissingTimeArePreserved() throws Exception {
        for (Account account : LEGACY_FIXTURE_ACCOUNTS) {
            CommentPageRequest r = request(account); bind(r); CommentRecord item = response(r, one(r)).page.items.get(0);
            assertEquals(account == Account.FACEBOOK_PAGE ? "000Comment_Aa" : "urn:li:comment:(" + THREAD + ",00010)", item.commentId);
            assertEquals(r.postId, item.postId); assertEquals("", item.message); assertEquals(ContentState.AVAILABLE, item.contentState);
            assertEquals(ParentState.UNKNOWN, item.parentState); assertNull(item.parentCommentId);
            assertNull(item.createdAt); assertEquals(TimeMissingReason.PROVIDER_OMITTED, item.timeMissingReason);
            assertEquals(NOW, item.syncedAt); assertEquals(r.connectionRevision, item.scope.connectionRevision);
        }
    }
    @Test public void missingMessageDoesNotBecomeDeletion() throws Exception {
        for (Account account : LEGACY_FIXTURE_ACCOUNTS) {
            CommentPageRequest r = request(account); bind(r); JsonObject body = one(r); row(body, r).remove("message");
            CommentRecord item = response(r, body).page.items.get(0); assertEquals(ContentState.OMITTED, item.contentState); assertNull(item.message);
        }
    }
    @Test public void rootAndDeletedRequireIndependentScopedEvidence() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); JsonObject body = one(r); row(body, r).remove("message");
        binding.set(makeBinding(r, CapabilityAvailability.AVAILABLE, CapabilityAvailability.PERMISSION_REQUIRED,
                ConnectionStatus.CONNECTED, "ref", "generation", null,
                Collections.singletonMap("000Comment_Aa", new CommentAdapter.ItemEvidence(true, true, "MO_PHONG-root-deletion-evidence"))));
        CommentRecord item = response(r, body).page.items.get(0);
        assertEquals(ParentState.ROOT, item.parentState); assertNull(item.parentCommentId);
        assertEquals(ContentState.DELETED, item.contentState); assertNull(item.message);
        assertEquals(CapabilityAvailability.PERMISSION_REQUIRED, item.replyCapability.availability);
    }
    @Test public void conflictingDeletionEvidenceFailsWholePage() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE);
        binding.set(makeBinding(r, CapabilityAvailability.AVAILABLE, CapabilityAvailability.UNVERIFIED, ConnectionStatus.CONNECTED,
                "ref", "generation", null, Collections.singletonMap("000Comment_Aa", new CommentAdapter.ItemEvidence(false, true, "MO_PHONG-deleted"))));
        failure(response(r, one(r)), Failure.INVALID_RESPONSE);
    }
    @Test public void knownParentCanBeAbsentFromPage() throws Exception {
        for (Account account : LEGACY_FIXTURE_ACCOUNTS) {
            CommentPageRequest r = request(account); bind(r); JsonObject body = one(r);
            String parent = account == Account.FACEBOOK_PAGE ? "000Missing_Parent" : "urn:li:comment:(" + THREAD + ",00099)";
            if (account == Account.FACEBOOK_PAGE) { JsonObject p = new JsonObject(); p.addProperty("id", parent); row(body, r).add("parent", p); }
            else row(body, r).addProperty("parentComment", parent);
            CommentRecord item = response(r, body).page.items.get(0);
            assertEquals(ParentState.KNOWN, item.parentState); assertEquals(parent, item.parentCommentId);
        }
    }
    @Test public void nestedLinkedInParentAndUrnEncoding() throws Exception {
        CommentPageRequest base = request(Account.LINKEDIN_MEMBER);
        String parent = "urn:li:comment:(" + THREAD + ",00099)";
        CommentPageRequest r = new CommentPageRequest(base.account, base.channelId, 1, base.externalAccountId,
                base.postId, Target.COMMENT, parent, THREAD, base.query, 2, base.historyFrom, base.apiVersion, null);
        bind(r); JsonObject body = one(r); row(body, r).addProperty("parentComment", parent);
        assertEquals(parent, response(r, body).page.items.get(0).parentCommentId);
        RecordedRequest wire = server.takeRequest(1, TimeUnit.SECONDS);
        assertTrue(wire.getRequestUrl().encodedPath().contains("urn%3Ali%3Acomment%3A%28urn%3Ali%3Aactivity%3A"));
        assertEquals(parent, wire.getRequestUrl().pathSegments().get(2));
        assertFalse(wire.getRequestUrl().encodedPath().contains("%253A"));
        row(body, r).addProperty("parentComment", "urn:li:comment:(" + THREAD + ",00098)");
        failure(response(r, body), Failure.INVALID_RESPONSE);
        row(body, r).remove("parentComment");
        assertEquals(ParentState.UNKNOWN, response(r, body).page.items.get(0).parentState);
    }
    @Test public void nestedFacebookParentAndMissingParentRemainDistinct() throws Exception {
        CommentPageRequest base = request(Account.FACEBOOK_PAGE);
        CommentPageRequest r = new CommentPageRequest(base.account, base.channelId, 1, base.externalAccountId,
                base.postId, Target.COMMENT, "Parent_Aa", null, Query.FB_STREAM, 2, base.historyFrom, base.apiVersion, null);
        bind(r); JsonObject body = one(r); JsonObject parent = new JsonObject(); parent.addProperty("id", r.targetId);
        row(body, r).add("parent", parent);
        assertEquals(r.targetId, response(r, body).page.items.get(0).parentCommentId);
        parent.addProperty("id", "other-parent"); failure(response(r, body), Failure.INVALID_RESPONSE);
        row(body, r).remove("parent"); assertEquals(ParentState.UNKNOWN, response(r, body).page.items.get(0).parentState);
    }
    @Test public void providerCreationAndUpdateTimesAreNotReceiptTime() throws Exception {
        CommentPageRequest fb = request(Account.FACEBOOK_PAGE); bind(fb);
        JsonObject body = one(fb); row(body, fb).addProperty("created_time", "2026-11-19T08:00:00+0700");
        row(body, fb).addProperty("updated_time", "2026-11-19T01:05:00Z");
        CommentRecord item = response(fb, body).page.items.get(0);
        assertEquals(Long.valueOf(1795050000000L), item.createdAt); assertNull(item.timeMissingReason);
        assertEquals(Long.valueOf(item.createdAt + 300000L), item.providerUpdatedAt);
        CommentPageRequest li = request(Account.LINKEDIN_MEMBER); bind(li);
        CommentRecord second = response(li, page(li, 0)).page.items.get(0);
        assertEquals(Long.valueOf(1794186000000L), second.createdAt);
        assertEquals(second.createdAt, second.providerUpdatedAt);
    }
    @Test public void invalidFacebookTimestampFailsWholePage() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        for (String time : Arrays.asList("garbage", "2026-02-30T00:00:00Z", "2026-01-01T25:00:00Z", "2026-01-01T00:00:00+15:00")) {
            JsonObject body = one(r); row(body, r).addProperty("created_time", time); failure(response(r, body), Failure.INVALID_RESPONSE);
        }
    }
    @Test public void invalidLinkedInTimestampFailsWholePage() throws Exception {
        CommentPageRequest r = request(Account.LINKEDIN_MEMBER); bind(r);
        for (JsonElement time : Arrays.asList(new JsonPrimitive("1794186000000"), new JsonPrimitive(-1), new JsonPrimitive(1.5), new JsonPrimitive(8640000000000001L))) {
            JsonObject body = one(r); JsonObject created = new JsonObject(); created.add("time", time); row(body, r).add("created", created);
            failure(response(r, body), Failure.INVALID_RESPONSE);
        }
    }
    @Test public void badPostOrScopeInAnyItemFailsWholePage() throws Exception {
        for (Account account : LEGACY_FIXTURE_ACCOUNTS) {
            CommentPageRequest r = request(account); bind(r);
            for (String field : Arrays.asList("postId", "postUrn", "channelId", "externalAccountId")) {
                JsonObject body = page(r, 0); row(body, r).addProperty(field, "another-subject");
                failure(response(r, body), Failure.INVALID_RESPONSE);
            }
        }
    }
    @Test public void wrongLinkedInThreadCompositeAndParentAreRejected() throws Exception {
        CommentPageRequest r = request(Account.LINKEDIN_MEMBER); bind(r);
        for (String field : Arrays.asList("object", "commentUrn", "parentComment")) {
            JsonObject body = one(r); row(body, r).addProperty(field, "urn:li:comment:(urn:li:activity:999,00010)");
            failure(response(r, body), Failure.INVALID_RESPONSE);
        }
    }
    @Test public void malformedJsonInvalidItemAndWrongTypesNeverBecomeEmptySuccess() throws Exception {
        for (Account account : LEGACY_FIXTURE_ACCOUNTS) {
            CommentPageRequest r = request(account); bind(r);
            for (String body : Arrays.asList("{", "[]", "{}", "null")) {
                server.enqueue(new MockResponse().setBody(body)); failure(read(adapter(r), r), Failure.INVALID_RESPONSE);
            }
            JsonObject body = page(r, 0); body.getAsJsonArray(account == Account.FACEBOOK_PAGE ? "data" : "elements").add(JsonNull.INSTANCE);
            failure(response(r, body), Failure.INVALID_RESPONSE);
            body = one(r); row(body, r).addProperty("id", 100); failure(response(r, body), Failure.INVALID_RESPONSE);
            body = one(r); row(body, r).add("message", new JsonArray()); failure(response(r, body), Failure.INVALID_RESPONSE);
        }
    }
    @Test public void replyCapabilityIndependentOfRead() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE);
        for (CapabilityAvailability reply : CapabilityAvailability.values()) {
            binding.set(makeBinding(r, CapabilityAvailability.AVAILABLE, reply, ConnectionStatus.CONNECTED, "ref", "generation", null, null));
            CommentRecord item = response(r, one(r)).page.items.get(0);
            assertEquals(SocialOperation.REPLY_COMMENT, item.replyCapability.operation); assertEquals(reply, item.replyCapability.availability);
        }
    }

    @Test public void emptySuccessDoesNotRequireAnItem() throws Exception {
        for (Account account : LEGACY_FIXTURE_ACCOUNTS) {
            CommentPageRequest r = request(account); bind(r);
            JsonObject empty = JsonParser.parseString(account == Account.FACEBOOK_PAGE ? "{\"data\":[]}"
                    : "{\"elements\":[],\"paging\":{\"start\":0,\"count\":2,\"total\":0,\"links\":[]}}").getAsJsonObject();
            CommentPageResult result = response(r, empty);
            assertEquals(Status.SUCCESS, result.status); assertTrue(result.page.items.isEmpty()); assertEquals(Boolean.FALSE, result.page.hasMore);
        }
    }
    @Test public void emptyPageWithContinuationIsNotCompletion() throws Exception {
        for (Account account : LEGACY_FIXTURE_ACCOUNTS) {
            CommentPageRequest r = request(account); bind(r); JsonObject body = page(r, 0);
            body.add(account == Account.FACEBOOK_PAGE ? "data" : "elements", new JsonArray());
            CommentPageResult result = response(r, body);
            assertEquals(Status.SUCCESS, result.status); assertTrue(result.page.items.isEmpty()); assertEquals(Boolean.TRUE, result.page.hasMore);
            assertNotNull(result.page.nextCursor);
        }
    }
    @Test public void linkedInMissingPagingDoesNotProveCompletion() throws Exception {
        CommentPageRequest r = request(Account.LINKEDIN_MEMBER); bind(r);
        CommentPageResult result = response(r, one(r)); assertNull(result.page.hasMore); assertNull(result.page.nextCursor);
        assertEquals("NOT_ESTABLISHED", result.page.paginationReason);
        JsonObject body = one(r); body.add("paging", JsonParser.parseString("{\"start\":0,\"count\":2,\"links\":[]}"));
        assertNull(response(r, body).page.hasMore);
    }
    @Test public void linkedInPagingConflictRemainsUnknown() throws Exception {
        CommentPageRequest r = request(Account.LINKEDIN_MEMBER); bind(r);
        for (String paging : Arrays.asList(
                "{\"start\":1,\"count\":2,\"total\":5,\"links\":[]}",
                "{\"start\":0,\"count\":3,\"total\":5,\"links\":[]}",
                "{\"start\":0,\"count\":2,\"total\":5,\"links\":[{\"rel\":\"next\",\"href\":\"?start=4&count=2\"}]}",
                "{\"start\":0,\"count\":2,\"total\":2,\"links\":[{\"rel\":\"next\",\"href\":\"?start=2&count=2\"}]}",
                "{\"start\":0,\"count\":2,\"links\":[{\"rel\":\"next\",\"href\":\"?start=2&start=2&count=2\"}]}")) {
            JsonObject body = one(r); body.add("paging", JsonParser.parseString(paging));
            CommentPageResult result = response(r, body); assertEquals(Status.SUCCESS, result.status);
            assertNull(result.page.hasMore); assertNull(result.page.nextCursor); assertEquals("PAGING_CONFLICT", result.page.paginationReason);
        }
    }
    @Test public void facebookNextWithoutCursorIsFailureNotComplete() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r); JsonObject body = one(r);
        body.add("paging", JsonParser.parseString("{\"next\":\"https://evil.invalid/steal\"}")); failure(response(r, body), Failure.INVALID_RESPONSE);
    }
    @Test public void credentialBearingCursorIsRejected() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        for (String cursor : Arrays.asList(TOKEN, "https://evil.invalid/?access_token=x", "access_token=synthetic")) {
            JsonObject body = page(r, 0); body.getAsJsonObject("paging").getAsJsonObject("cursors").addProperty("after", cursor);
            failure(response(r, body), Failure.INVALID_RESPONSE);
        }
    }
    @Test public void cursorCannotResumeAnotherQueryScopeOrVersion() throws Exception {
        for (Account account : LEGACY_FIXTURE_ACCOUNTS) {
            CommentPageRequest r = request(account); bind(r);
            String cursor = response(r, page(r, 0)).page.nextCursor;
            int reads = credentialReads.get(); int requests = server.getRequestCount();
            for (String field : Arrays.asList("accountId", "post", "channel", "revision", "size", "history", "version", "target", "query")) {
                if (field.equals("query") && account != Account.FACEBOOK_PAGE) continue;
                CommentPageRequest other = variant(r, field).resume(cursor); bind(other);
                failure(read(adapter(other), other), Failure.INVALID_CURSOR);
            }
            assertEquals(reads, credentialReads.get()); assertEquals(requests, server.getRequestCount());
        }
    }
    @Test public void malformedAndTamperedCursorDoNotUseCredential() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        for (String cursor : Arrays.asList("raw-provider-cursor", "sp041v1.invalid", "sp041v1.")) {
            failure(read(adapter(r), r.resume(cursor)), Failure.INVALID_CURSOR);
        }
        String valid = codec.encode(r, "safe-continuation");
        String tampered = valid.substring(0, 12) + (valid.charAt(12) == 'A' ? 'B' : 'A') + valid.substring(13);
        failure(read(adapter(r), r.resume(tampered)), Failure.INVALID_CURSOR);
        assertEquals(0, credentialReads.get()); assertEquals(0, server.getRequestCount());
    }
    @Test public void cursorWrongKeyCannotResume() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r); String cursor = codec.encode(r, "continuation");
        byte[] different = new byte[32]; different[0] = 1; codec = new CommentCursorCodec(new SecretKeySpec(different, "AES"));
        rejectedWithoutCredential(r.resume(cursor), Failure.INVALID_CURSOR);
    }

    @Test public void authErrorsUseReconnect() throws Exception { httpErrors(401, "{\"error\":{\"code\":190}}", SocialError.Code.AUTH_REQUIRED); }
    @Test public void facebookPermissionErrorUsesResolvePermission() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        grantPermissionEvidence(r, 200);
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"error\":{\"code\":200,\"message\":\"" + TOKEN + "\"}}"));
        CommentPageResult result = read(adapter(r), r); failure(result, Failure.PROVIDER_ERROR);
        assertEquals(SocialError.Code.PERMISSION_REQUIRED, result.error.code); assertEquals(SocialError.NextAction.RESOLVE_PERMISSION, result.error.nextAction);
    }
    @Test public void unknown403NeverBecomesUnsupportedOrPermission() throws Exception { httpErrors(403, "{\"message\":\"denied\"}", null); }
    @Test public void rateLimitedWithoutRetryAfterIsNotEmpty() throws Exception { httpErrors(429, "{}", SocialError.Code.RATE_LIMITED); }
    @Test public void rateLimitRetryAfterSecondsAndDateAreNormalized() throws Exception {
        for (Account account : Account.values()) {
            CommentPageRequest r = request(account); bind(r);
            for (String header : Arrays.asList("15", "Thu, 19 Nov 2026 10:00:00 GMT", "garbage", "-1", "999999999999999999")) {
                server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", header).setBody("{}"));
                CommentPageResult result = read(adapter(r), r); failure(result, Failure.PROVIDER_ERROR);
                assertEquals(SocialError.Code.RATE_LIMITED, result.error.code); assertEquals(SocialError.NextAction.WAIT, result.error.nextAction);
                if (header.equals("15")) assertEquals(Long.valueOf(NOW + 15000), result.retryAfterAt);
                else if (header.startsWith("Thu")) assertEquals(Long.valueOf(1795082400000L), result.retryAfterAt);
                else assertNull(result.retryAfterAt);
            }
        }
    }
    @Test public void facebookBodyAuthAndRateErrorsAreMappedOnHttp200() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        for (int code : new int[]{190, 4, 17, 32, 613, 10, 200, 100}) {
            bind(r);
            if (code == 10 || code == 200) grantPermissionEvidence(r, code);
            JsonObject body = new JsonObject(), error = new JsonObject(); error.addProperty("code", code); body.add("error", error);
            CommentPageResult result = response(r, body); failure(result, Failure.PROVIDER_ERROR);
            if (code == 100) assertNull(result.error);
            else assertEquals(code == 190 ? SocialError.Code.AUTH_REQUIRED : code == 10 || code == 200
                    ? SocialError.Code.PERMISSION_REQUIRED : SocialError.Code.RATE_LIMITED, result.error.code);
        }
    }
    @Test public void linkedinProviderErrorInside200CannotBecomeEmptySuccess() throws Exception {
        CommentPageRequest r = request(Account.LINKEDIN_MEMBER); bind(r);
        for (String raw : Arrays.asList("{\"status\":403,\"elements\":[]}", "{\"serviceErrorCode\":100,\"elements\":[]}")) {
            server.enqueue(new MockResponse().setBody(raw)); failure(read(adapter(r), r), Failure.PROVIDER_ERROR);
        }
    }
    private void httpErrors(int status, String body, SocialError.Code expected) throws Exception {
        for (Account account : Account.values()) {
            CommentPageRequest r = request(account); bind(r); server.enqueue(new MockResponse().setResponseCode(status).setBody(body));
            CommentPageResult result = read(adapter(r), r); failure(result, Failure.PROVIDER_ERROR);
            if (expected == null) assertNull(result.error); else assertEquals(expected, result.error.code);
            assertNull(result.retryAfterAt);
        }
    }
    @Test public void retryAfterSecondsDateInvalidAndOverflow() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        String[] values = {"7", "Wed, 21 Oct 2015 07:28:00 GMT", "bad", "-1", "9223372036854775807"};
        Long[] expected = {NOW + 7000, 1445412480000L, null, null, null};
        for (int i = 0; i < values.length; i++) {
            server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", values[i]).setBody("{}"));
            CommentPageResult result = read(adapter(r), r); assertEquals(SocialError.NextAction.WAIT, result.error.nextAction);
            assertEquals(expected[i], result.retryAfterAt);
        }
    }
    @Test public void errorInsideHttp200IsFailure() throws Exception {
        for (Account account : LEGACY_FIXTURE_ACCOUNTS) {
            CommentPageRequest r = request(account); bind(r);
            JsonObject body = one(r);
            if (account == Account.FACEBOOK_PAGE) body.add("error", JsonParser.parseString("{\"code\":190}"));
            else body.addProperty("serviceErrorCode", 100);
            failure(response(r, body), Failure.PROVIDER_ERROR);
        }
    }
    @Test public void expiredCursorWithoutProviderEvidenceIsGenericFailure() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":{\"code\":100,\"message\":\"expired cursor\"}}"));
        CommentPageResult result = read(adapter(r), r.resume(codec.encode(r, "expired-synthetic")));
        failure(result, Failure.PROVIDER_ERROR); assertNull(result.error);
    }
    @Test public void timeoutIsNetworkFailure() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        client = client.newBuilder().readTimeout(100, TimeUnit.MILLISECONDS).build();
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        CommentPageResult result = read(adapter(r), r); failure(result, Failure.NETWORK_ERROR);
        assertEquals(SocialError.Code.NETWORK_ERROR, result.error.code); assertEquals(SocialError.NextAction.RETRY_WHEN_SAFE, result.error.nextAction);
    }
    @Test public void networkDisconnectIsFailureWithoutAutomaticRetry() throws Exception {
        CommentPageRequest r = request(Account.LINKEDIN_MEMBER); bind(r);
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        failure(read(adapter(r), r), Failure.NETWORK_ERROR); assertEquals(1, server.getRequestCount());
    }
    @Test public void redirectNeverForwardsCredential() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", server.url("/steal?access_token=bad")));
        failure(read(adapter(r), r), Failure.PROVIDER_ERROR); assertEquals(1, server.getRequestCount());
    }
    @Test public void missingCredentialRequestsReconnect() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        CommentPageResult result = read(createAdapter(r, () -> NOW, c -> null), r);
        assertEquals(SocialError.Code.AUTH_REQUIRED, result.error.code); assertEquals(0, server.getRequestCount());
    }
    @Test public void localLookupExceptionIsSanitizedFailure() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        CommentPageResult result = read(createAdapter(r, () -> NOW, c -> { throw new IllegalStateException(TOKEN); }), r);
        failure(result, Failure.LOCAL_ERROR); assertFalse(new Gson().toJson(result).contains(TOKEN));
    }

    @Test public void cancelBeforeSendIsIdempotentAndSuppressesCallback() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        CountDownLatch gate = new CountDownLatch(1); executor.submit(() -> { try { gate.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } });
        AtomicInteger callbacks = new AtomicInteger(); Cancelable call = adapter(r).read(r, result -> callbacks.incrementAndGet());
        call.cancel(); call.cancel(); gate.countDown(); executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
        assertEquals(0, callbacks.get()); assertEquals(0, credentialReads.get()); assertEquals(0, server.getRequestCount());
    }
    @Test public void cancelDuringHttpCancelsCallAndDropsLateCallback() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        server.enqueue(new MockResponse().setBody(one(r).toString()).setBodyDelay(300, TimeUnit.MILLISECONDS));
        AtomicInteger callbacks = new AtomicInteger(); Cancelable call = adapter(r).read(r, result -> callbacks.incrementAndGet());
        assertNotNull(server.takeRequest(1, TimeUnit.SECONDS)); call.cancel(); call.cancel();
        executor.submit(() -> {}).get(2, TimeUnit.SECONDS); assertEquals(0, callbacks.get()); assertEquals(1, server.getRequestCount());
    }
    @Test public void cancelDuringCredentialLoadPreventsHttp() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        CommentAdapter a = createAdapter(r, () -> NOW, c -> {
            entered.countDown(); try { release.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } return TOKEN;
        });
        AtomicInteger callbacks = new AtomicInteger(); Cancelable call = a.read(r, result -> callbacks.incrementAndGet());
        assertTrue(entered.await(1, TimeUnit.SECONDS)); call.cancel(); release.countDown(); executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
        assertEquals(0, callbacks.get()); assertEquals(0, server.getRequestCount());
    }
    @Test public void terminalCallbackOnceEvenWhenConsumerThrowsAndCancelFollows() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r); server.enqueue(new MockResponse().setBody(one(r).toString()));
        AtomicInteger calls = new AtomicInteger(); AtomicReference<Thread> callbackThread = new AtomicReference<>();
        Cancelable handle = adapter(r).read(r, result -> { callbackThread.set(Thread.currentThread()); calls.incrementAndGet(); throw new IllegalStateException("consumer"); });
        executor.submit(() -> {}).get(2, TimeUnit.SECONDS); handle.cancel(); handle.cancel();
        assertEquals(1, calls.get()); assertNotEquals(Thread.currentThread(), callbackThread.get());
    }
    @Test public void outputsNeverContainCredentialProviderErrorTextOrPagingUrl() throws Exception {
        for (Account account : LEGACY_FIXTURE_ACCOUNTS) {
            CommentPageRequest r = request(account); bind(r); JsonObject body = page(r, 0);
            if (account == Account.FACEBOOK_PAGE) body.getAsJsonObject("paging").addProperty("next", "https://evil.invalid/?access_token=" + TOKEN);
            else body.getAsJsonObject("paging").getAsJsonArray("links").get(0).getAsJsonObject().addProperty("href", "https://evil.invalid/?start=2&count=2&access_token=" + TOKEN);
            String output = new Gson().toJson(response(r, body)); assertFalse(output.contains(TOKEN)); assertFalse(output.contains("https://"));
            server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"message\":\"https://evil.invalid/?access_token=" + TOKEN + "\"}"));
            output = new Gson().toJson(read(adapter(r), r)); assertFalse(output.contains(TOKEN)); assertFalse(output.contains("evil.invalid"));
            assertEquals("CommentPageRequest[redacted]", r.resume(codec.encode(r, "secret-continuation")).toString());
        }
    }

    @Test public void pageAndRecordInvariantsRejectContradictoryShapes() {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE);
        assertThrows(IllegalArgumentException.class, () -> new CommentPage(r, Collections.emptyList(), null, true, null, NOW, Source.MO_PHONG, "fixture"));
        assertThrows(IllegalArgumentException.class, () -> new CommentPage(r, Collections.emptyList(), "cursor", false, null, NOW, Source.MO_PHONG, "fixture"));
        assertThrows(IllegalArgumentException.class, () -> new CommentRecord(r, "id", ParentState.KNOWN, null,
                ContentState.AVAILABLE, "", null, null, null, NOW, Source.MO_PHONG, "fixture",
                capability(SocialOperation.REPLY_COMMENT, CapabilityAvailability.UNVERIFIED)));
        assertThrows(IllegalArgumentException.class, () -> new CommentRecord(r, "id", ParentState.ROOT, null,
                ContentState.DELETED, "still present", null, null, null, NOW, Source.MO_PHONG, "fixture",
                capability(SocialOperation.REPLY_COMMENT, CapabilityAvailability.UNVERIFIED)));
    }
    @Test public void factsCannotOverrideContradictoryResponse() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE);
        binding.set(makeBinding(r, CapabilityAvailability.AVAILABLE, CapabilityAvailability.UNVERIFIED,
                ConnectionStatus.CONNECTED, "ref", "generation", null,
                Collections.singletonMap("000Comment_Aa", new CommentAdapter.ItemEvidence(false, true, "MO_PHONG-deleted"))));
        failure(response(r, one(r)), Failure.INVALID_RESPONSE);
        binding.set(makeBinding(r, CapabilityAvailability.AVAILABLE, CapabilityAvailability.UNVERIFIED,
                ConnectionStatus.CONNECTED, "ref", "generation", null,
                Collections.singletonMap("000Comment_Aa", new CommentAdapter.ItemEvidence(true, false, "MO_PHONG-root"))));
        JsonObject body = one(r), parent = new JsonObject(); parent.addProperty("id", "other-parent"); row(body, r).add("parent", parent);
        failure(response(r, body), Failure.INVALID_RESPONSE);
    }
    @Test public void malformedPagingFailsInsteadOfClaimingComplete() throws Exception {
        for (Account account : LEGACY_FIXTURE_ACCOUNTS) {
            CommentPageRequest r = request(account); bind(r);
            JsonObject body = one(r); body.addProperty("paging", "bad-shape"); failure(response(r, body), Failure.INVALID_RESPONSE);
        }
    }
    @Test public void linkedinCursorOffsetsMustBeBoundedIntegers() throws Exception {
        CommentPageRequest r = request(Account.LINKEDIN_MEMBER); bind(r);
        for (String offset : Arrays.asList("-1", "0", "2&count=100", "2147483647", "1.5")) {
            failure(read(adapter(r), r.resume(codec.encode(r, offset))), Failure.INVALID_CURSOR);
        }
        assertEquals(0, credentialReads.get()); assertEquals(0, server.getRequestCount());
    }
    @Test public void noCredentialsInEvidenceLabels() {
        assertThrows(IllegalArgumentException.class, () -> new CommentAdapter.ItemEvidence(false, true, "https://secret.invalid/?access_token=x"));
    }

    private JsonObject fixture(CommentPageRequest r) {
        String path = r.platform == Platform.FACEBOOK ? "/comments/facebook-sp018.json" : "/comments/linkedin-sp019.json";
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            JsonObject fixture = JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals("MO_PHONG", fixture.get("source").getAsString()); return fixture;
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }
    private JsonObject page(CommentPageRequest r, int index) { return fixture(r).getAsJsonArray("pages").get(index).getAsJsonObject(); }
    private JsonObject row(JsonObject body, CommentPageRequest r) {
        return body.getAsJsonArray(r.platform == Platform.FACEBOOK ? "data" : "elements").get(0).getAsJsonObject();
    }
    private JsonObject one(CommentPageRequest r) {
        JsonObject body = new JsonObject(), row = new JsonObject(); JsonArray array = new JsonArray(); array.add(row);
        body.add(r.platform == Platform.FACEBOOK ? "data" : "elements", array);
        row.addProperty("id", r.platform == Platform.FACEBOOK ? "000Comment_Aa" : "00010");
        if (r.platform == Platform.FACEBOOK) row.addProperty("message", "");
        else { row.addProperty("object", THREAD); JsonObject message = new JsonObject(); message.addProperty("text", ""); row.add("message", message); }
        return body;
    }

    private void grantPermissionEvidence(CommentPageRequest r, int code) {
        CommentAdapter.Binding b = binding.get();
        binding.set(new CommentAdapter.Binding(b.channel, b.scope, b.readCapability, b.replyCapability,
                b.bindingId, b.postThreadEvidenceRef, b.maxPageSize, b.itemEvidence,
                new CommentAdapter.PermissionEvidence(r, code, "MO_PHONG-permission-evidence")));
    }

    @Test public void permissionCodeWithoutScopedEvidenceRemainsUnclassified() throws Exception {
        CommentPageRequest r = request(Account.FACEBOOK_PAGE); bind(r);
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"error\":{\"code\":200}}"));
        CommentPageResult result = read(adapter(r), r); failure(result, Failure.PROVIDER_ERROR); assertNull(result.error);
    }

    @Test public void permissionEvidenceForAnotherQueryCannotClassify403() throws Exception {
        CommentPageRequest r = request(Account.LINKEDIN_MEMBER); bind(r); grantPermissionEvidence(variant(r, "post"), 100);
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"serviceErrorCode\":100}"));
        CommentPageResult result = read(adapter(r), r); failure(result, Failure.PROVIDER_ERROR); assertNull(result.error);
    }

    @Test public void cursorCannotCrossProviderAccountTypeOrThreadMapping() throws Exception {
        CommentPageRequest member = request(Account.LINKEDIN_MEMBER); bind(member);
        String cursor = codec.encode(member, "2");
        for (CommentPageRequest other : Arrays.asList(request(Account.FACEBOOK_PAGE), request(Account.LINKEDIN_ORGANIZATION),
                new CommentPageRequest(member.account, member.channelId, member.connectionRevision, member.externalAccountId,
                        member.postId, Target.POST, member.postId, "urn:li:activity:0099", member.query,
                        member.pageSize, member.historyFrom, member.apiVersion, null))) {
            bind(other); failure(read(adapter(other), other.resume(cursor)), Failure.INVALID_CURSOR);
        }
        assertEquals(0, credentialReads.get()); assertEquals(0, server.getRequestCount());
    }

    @Test public void linkedinExplicitRootDeletionEvidenceIsSeparateFromOmission() throws Exception {
        for (Account account : Arrays.asList(Account.LINKEDIN_MEMBER, Account.LINKEDIN_ORGANIZATION)) {
            CommentPageRequest r = request(account); JsonObject body = one(r); row(body, r).remove("message");
            binding.set(makeBinding(r, CapabilityAvailability.AVAILABLE, CapabilityAvailability.UNVERIFIED,
                    ConnectionStatus.CONNECTED, "ref", "generation", "MO_PHONG-post-thread",
                    Collections.singletonMap("urn:li:comment:(" + THREAD + ",00010)",
                            new CommentAdapter.ItemEvidence(true, true, "MO_PHONG-root-deletion-evidence"))));
            CommentRecord item = response(r, body).page.items.get(0);
            assertEquals(ParentState.ROOT, item.parentState); assertEquals(ContentState.DELETED, item.contentState);
            assertNull(item.parentCommentId); assertNull(item.message);
        }
    }

    private CommentPageRequest variant(CommentPageRequest r, String field) {
        String post = field.equals("post") ? (r.platform == Platform.FACEBOOK ? "OtherPost" : "urn:li:ugcPost:0002") : r.postId;
        Target target = field.equals("target") ? Target.COMMENT : r.target;
        String targetId = field.equals("target") ? (r.platform == Platform.FACEBOOK ? "OtherParent" : "urn:li:comment:(" + THREAD + ",00099)")
                : target == Target.POST ? post : r.targetId;
        return new CommentPageRequest(r.account, field.equals("channel") ? "other-channel" : r.channelId,
                field.equals("revision") ? 2 : r.connectionRevision,
                field.equals("accountId") ? "opaque-other-account" : r.externalAccountId,
                post, target, targetId, r.threadId, field.equals("query") ? Query.FB_STREAM : r.query,
                field.equals("size") ? 1 : r.pageSize, field.equals("history") ? 2000L : r.historyFrom,
                field.equals("version") ? (r.platform == Platform.FACEBOOK ? "v26.0" : "202609") : r.apiVersion, null);
    }
}
