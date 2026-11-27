package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.data.local.comment.CommentCache;
import com.socialpilot.app.data.local.comment.CommentPageWriter;
import com.socialpilot.app.data.local.comment.CommentStore;
import com.socialpilot.app.data.local.publishedpost.*;
import com.socialpilot.app.data.remote.instagram.InstagramGraphClient;
import com.socialpilot.app.data.repository.ChannelRepository;
import com.socialpilot.app.data.repository.comment.CommentRepository;
import com.socialpilot.app.data.repository.comment.CommentSyncCoordinator;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.social.*;
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle.Scope;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import okhttp3.OkHttpClient;

/** Meta reads authorized by the persisted Channel, including non-selected channels. */
public final class ChannelCommentAccess {
    private static final String VERIFIED_VERSION = "v27.0";
    private final ChannelRepository channels;
    private final OkHttpClient http;
    private final InstagramGraphClient graph;

    public ChannelCommentAccess(ChannelRepository channels, OkHttpClient http) {
        this.channels = channels;
        this.http = http;
        graph = new InstagramGraphClient(http);
    }

    /** Worker thread. A caller supplies a candidate ID, never the ownership attestation. */
    public PublishedPostRepository.WriteResult importPost(PublishedPostRepository posts,
            PublishedPostScope scope, String postId) throws IOException {
        ChannelRepository.Snapshot channel = channels.find(scope.channelId);
        if (postId == null || postId.trim().isEmpty() || ".".equals(postId) || "..".equals(postId))
            throw new IllegalArgumentException("Invalid provider post ID");
        if (channel == null || !PublishedPostScope.from(channel.info).same(scope) || !channel.connected()
                || channel.capability(SocialOperation.READ_COMMENTS).availability != CapabilityAvailability.AVAILABLE
                || !verifiedVersion(channel)) throw new IOException("Channel is not verified for comment reads");
        OAuthExchangeResponse grant = channels.credential(channel.scope);
        if (grant == null || !scope.externalAccountId.equals(grant.externalAccountId))
            throw new IOException("Channel credential unavailable");
        try {
            graph.verifyPostOwner(grant, VERIFIED_VERSION, postId);
        } catch (InstagramGraphClient.AuthorizationExpired expired) {
            channels.requireReconnect(channel.scope);
            throw expired;
        }
        ChannelRepository.Snapshot current = channels.find(scope.channelId);
        if (current == null || !channel.scope.matches(current.scope) || !current.connected()
                || current.capability(SocialOperation.READ_COMMENTS).availability != CapabilityAvailability.AVAILABLE)
            throw new IOException("Channel authorization changed during ownership read");
        String evidence = scope.platform == Platform.INSTAGRAM ? "ig-media-owner" : "fb-post-from";
        PublishedPostInput.Ownership owner = new PublishedPostInput.Ownership(scope, postId, evidence);
        return posts.upsert(new PublishedPostInput(scope, postId, owner, null, null, null, null,
                System.currentTimeMillis(), PublishedPostInput.Source.API_THAT, evidence));
    }

    private static boolean verifiedVersion(ChannelRepository.Snapshot channel) {
        // Legacy Page exchanges omitted the version. SP-018 and the SP-022 Page client use v27.0.
        return VERIFIED_VERSION.equals(channel.graphApiVersion)
                || channel.info.platform == Platform.FACEBOOK && channel.graphApiVersion == null;
    }

    public CommentRepository repository(CommentStore store, CommentCursorCodec codec,
            CommentSyncCoordinator.Network network, Executor io, long freshForMillis) {
        CommentPageWriter writer = CommentPageWriter.forProduction(store, codec);
        CommentSyncCoordinator coordinator = new CommentSyncCoordinator(new CommentCache(writer),
                query -> adapter(query, writer, codec), System::currentTimeMillis, network, io,
                CommentSyncCoordinator.MAX_PAGES);
        return new CommentRepository(coordinator, freshForMillis);
    }

    private CommentAdapter adapter(CommentPageRequest query, CommentPageWriter writer, CommentCursorCodec codec) {
        CommentAdapter.ConnectionSource source = new CommentAdapter.ConnectionSource() {
            @Override public CommentAdapter.Binding current(String channelId) {
                ChannelRepository.Snapshot channel = channels.find(channelId);
                if (channel == null || !query.matches(channel.info) || !verifiedVersion(channel)
                        || !VERIFIED_VERSION.equals(query.apiVersion)) return null;
                PublishedPost post = channel.connected() ? writer.ownedTarget(query) : null;
                return new CommentAdapter.Binding(channel.info, query, channel.capability(SocialOperation.READ_COMMENTS),
                        channel.capability(SocialOperation.REPLY_COMMENT), post == null ? "retired" : post.id,
                        post == null ? null : post.ownershipEvidenceRef, query.platform == Platform.INSTAGRAM ? 1 : 2, null);
            }

            @Override public void observed(CommentAdapter.Binding sent, CommentPageResult result) {
                Scope scope = scopeOf(sent.channel);
                if (result.error != null && result.error.code == SocialError.Code.AUTH_REQUIRED) {
                    channels.requireReconnect(scope);
                } else if (result.status == CommentPageResult.Status.SUCCESS) {
                    SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
                    utc.setTimeZone(TimeZone.getTimeZone("UTC"));
                    String verifiedAt = utc.format(new Date(result.page.fetchedAt));
                    if (sent.readCapability.verifiedAt != null
                            && verifiedAt.compareTo(sent.readCapability.verifiedAt) <= 0) return;
                    channels.recordCapability(scope, sent.readCapability, new ChannelCapability(
                            SocialOperation.READ_COMMENTS, CapabilityAvailability.AVAILABLE,
                            sent.readCapability.reasonCode, verifiedAt));
                }
            }
        };
        CommentAdapter.CredentialSource tokens = channel -> {
            OAuthExchangeResponse grant = channels.credential(scopeOf(channel));
            return grant != null && channel.externalAccountId.equals(grant.externalAccountId)
                    && channel.accountType.name().equals(grant.accountType) ? grant.accessToken : null;
        };
        // Adapter HTTP runs on OkHttp's dispatcher, separate from the coordinator awaiting its callback.
        if (query.platform == Platform.INSTAGRAM) return new InstagramCommentAdapter(http, source, tokens,
                System::currentTimeMillis, null, codec, "ig-graph-comments");
        if (query.platform == Platform.FACEBOOK) return new FacebookCommentAdapter(http, source, tokens,
                System::currentTimeMillis, null, codec, "fb-graph-comments");
        throw new IllegalArgumentException("Unsupported comment platform");
    }

    private static Scope scopeOf(ChannelInfo channel) {
        return new Scope(channel.platform.name(), channel.channelId, channel.connectionRevision);
    }
}
