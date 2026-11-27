package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.domain.contract.Cancelable;

/** Pairs a terminal callback with the exact request object that went to the adapter. */
public final class CommentPageReceipt {
    public interface Callback { void complete(CommentPageReceipt receipt); }
    public final CommentPageRequest request;
    public final CommentPageResult result;

    CommentPageReceipt(CommentPageRequest request, CommentPageResult result) {
        this.request = request;
        this.result = result;
    }

    public static Cancelable read(CommentAdapter adapter, CommentPageRequest request, Callback callback) {
        return adapter.read(request, result -> callback.complete(new CommentPageReceipt(request, result)));
    }
}
