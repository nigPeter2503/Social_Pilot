package com.socialpilot.app.data.local.publishedpost;

import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import java.util.Objects;

/**
 * Integration port, NOT a production DAO. A Room implementation must share the same database
 * transaction with Channel invalidation/reconnect and roll back on every thrown exception.
 * Reads/writes are synchronous and must run on an IO executor. No pre-HTTP guard substitutes this.
 */
public interface PublishedPostStore {
    final class ChannelState {
        public final PublishedPostScope scope;
        public final ConnectionStatus status;

        public ChannelState(PublishedPostScope scope, ConnectionStatus status) {
            this.scope = Objects.requireNonNull(scope, "scope");
            this.status = Objects.requireNonNull(status, "status");
        }
    }

    interface Transaction {
        ChannelState channel(String channelId);
        PublishedPost byExternalId(String channelId, String externalPostId);
        PublishedPost byLocalId(String localId);
        /** Abort on PK/business key conflict; never INSERT OR REPLACE. */
        void insert(PublishedPost post);
        /** Update the same PK/business key only; throw if no row is updated. */
        void update(PublishedPost post);
    }

    interface Work<T> { T run(Transaction transaction); }
    <T> T transaction(Work<T> work);
}
