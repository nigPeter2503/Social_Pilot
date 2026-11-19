package com.socialpilot.app.data.local.publishedpost;

import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Unwired SP-041.01 logic; only a MO_PHONG transactional store exists until handoff. */
public final class PublishedPostRepository {
    public enum Status {
        COMMITTED, FOUND, MISSING, CHANNEL_MISSING, SCOPE_MISMATCH, DISCONNECTED,
        AUTH_REQUIRED, OWNERSHIP_CONFLICT, METADATA_CONFLICT, PROVENANCE_REJECTED, STORAGE_ERROR
    }
    public enum Action { INSERTED, UPDATED, UNCHANGED, STALE }

    public static final class Entry {
        public final Action action;
        public final PublishedPost post;
        Entry(Action action, PublishedPost post) { this.action = action; this.post = post; }
    }

    public static final class WriteResult {
        public final Status status;
        /** Empty on failure: never expose rolled-back intermediate writes as successes. */
        public final List<Entry> entries;
        WriteResult(Status status, List<Entry> entries) {
            this.status = status;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }

    public static final class LookupResult {
        public final Status status;
        public final PublishedPost post;
        LookupResult(Status status, PublishedPost post) { this.status = status; this.post = post; }
    }

    private static final class Rejected extends RuntimeException {
        final Status status;
        Rejected(Status status) { super(status.name()); this.status = status; }
    }

    private final PublishedPostStore store;
    private final PublishedPostInput.Source allowedSource;

    private PublishedPostRepository(PublishedPostStore store, PublishedPostInput.Source source) {
        this.store = Objects.requireNonNull(store, "store");
        allowedSource = source;
    }

    /** Still requires a real, reviewed transactional Channel/Room port before app wiring. */
    public static PublishedPostRepository forProduction(PublishedPostStore store) {
        return new PublishedPostRepository(store, PublishedPostInput.Source.API_THAT);
    }

    /** Explicitly isolated test history; never use this instance in AppContainer. */
    public static PublishedPostRepository forSimulation(PublishedPostStore store) {
        return new PublishedPostRepository(store, PublishedPostInput.Source.MO_PHONG);
    }

    public WriteResult upsert(PublishedPostInput input) {
        return upsertAll(Collections.singletonList(Objects.requireNonNull(input, "input")));
    }

    public WriteResult upsertAll(List<PublishedPostInput> inputs) {
        // Snapshot caller-owned collections before entering the transaction.
        List<PublishedPostInput> batch = new ArrayList<>(Objects.requireNonNull(inputs, "inputs"));
        if (batch.isEmpty()) throw PublishedPostScope.invalid();
        for (PublishedPostInput input : batch) Objects.requireNonNull(input, "input");
        try {
            return store.transaction(tx -> {
                List<Entry> entries = new ArrayList<>();
                for (PublishedPostInput input : batch) {
                    requireCurrent(tx, input.scope);
                    if (input.source != allowedSource) reject(Status.PROVENANCE_REJECTED);
                    if (!input.scope.same(input.ownership.scope)
                            || !input.externalPostId.equals(input.ownership.externalPostId))
                        reject(Status.OWNERSHIP_CONFLICT);
                    PublishedPost old = tx.byExternalId(input.scope.channelId, input.externalPostId);
                    if (old != null && (!old.scope.same(input.scope) || old.source != allowedSource
                            || !old.externalPostId.equals(input.externalPostId)))
                        reject(Status.OWNERSHIP_CONFLICT);
                    if (old == null) {
                        PublishedPost created = new PublishedPost(UUID.randomUUID().toString(), input, null);
                        tx.insert(created);
                        entries.add(new Entry(Action.INSERTED, created));
                    } else {
                        entries.add(merge(tx, old, input));
                    }
                }
                // Defense in depth; the port MUST also serialize external invalidation with this commit.
                for (PublishedPostInput input : batch) requireCurrent(tx, input.scope);
                return new WriteResult(Status.COMMITTED, entries);
            });
        } catch (Rejected rejected) {
            return new WriteResult(rejected.status, Collections.emptyList());
        } catch (RuntimeException storageFailure) {
            // Do not attach exception messages, SQL or provider data to outward results/logs.
            return new WriteResult(Status.STORAGE_ERROR, Collections.emptyList());
        }
    }

    private Entry merge(PublishedPostStore.Transaction tx, PublishedPost old, PublishedPostInput in) {
        int order;
        if (old.providerUpdatedAt != null && in.providerUpdatedAt != null) {
            order = Long.compare(in.providerUpdatedAt, old.providerUpdatedAt);
        } else {
            order = Long.compare(in.receivedAt, old.receivedAt);
        }
        if (order < 0) return new Entry(Action.STALE, old);
        boolean conflict = differs(in.content, old.content) || differs(in.permalink, old.permalink)
                || differs(in.publishedAt, old.publishedAt);
        // Equal versions cannot resolve conflicts. Once provider time is known, an undated
        // response may fill gaps but cannot replace known metadata even if received later.
        if (conflict && (order == 0 || old.providerUpdatedAt != null && in.providerUpdatedAt == null))
            reject(Status.METADATA_CONFLICT);
        PublishedPost merged = new PublishedPost(old.id, in, old);
        if (Objects.equals(old.content, merged.content) && Objects.equals(old.permalink, merged.permalink)
                && Objects.equals(old.publishedAt, merged.publishedAt)
                && Objects.equals(old.providerUpdatedAt, merged.providerUpdatedAt)
                && old.receivedAt == merged.receivedAt && old.sourceRef.equals(merged.sourceRef)
                && old.ownershipEvidenceRef.equals(merged.ownershipEvidenceRef))
            return new Entry(Action.UNCHANGED, old);
        tx.update(merged);
        return new Entry(Action.UPDATED, merged);
    }

    private static boolean differs(Object incoming, Object previous) {
        return incoming != null && previous != null && !incoming.equals(previous);
    }

    public LookupResult lookupByExternalId(PublishedPostScope scope, String externalPostId) {
        return lookup(scope, PublishedPostScope.opaque(externalPostId), false);
    }

    public LookupResult lookupByLocalId(PublishedPostScope scope, String localId) {
        return lookup(scope, PublishedPostScope.opaque(localId), true);
    }

    private LookupResult lookup(PublishedPostScope scope, String key, boolean local) {
        Objects.requireNonNull(scope, "scope");
        try {
            return store.transaction(tx -> {
                requireCurrent(tx, scope);
                PublishedPost post = local ? tx.byLocalId(key) : tx.byExternalId(scope.channelId, key);
                if (post != null && !scope.same(post.scope)) reject(Status.SCOPE_MISMATCH);
                if (post != null && post.source != allowedSource) reject(Status.PROVENANCE_REJECTED);
                requireCurrent(tx, scope);
                return new LookupResult(post == null ? Status.MISSING : Status.FOUND, post);
            });
        } catch (Rejected rejected) {
            return new LookupResult(rejected.status, null);
        } catch (RuntimeException storageFailure) {
            return new LookupResult(Status.STORAGE_ERROR, null);
        }
    }

    private static void requireCurrent(PublishedPostStore.Transaction tx, PublishedPostScope scope) {
        PublishedPostStore.ChannelState channel = tx.channel(scope.channelId);
        if (channel == null) reject(Status.CHANNEL_MISSING);
        if (channel.status == ConnectionStatus.DISCONNECTED) reject(Status.DISCONNECTED);
        if (!scope.same(channel.scope)) reject(Status.SCOPE_MISMATCH);
        if (channel.status != ConnectionStatus.CONNECTED) reject(Status.AUTH_REQUIRED);
    }

    private static void reject(Status status) { throw new Rejected(status); }
}
