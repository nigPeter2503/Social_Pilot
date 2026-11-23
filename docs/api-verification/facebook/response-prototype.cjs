'use strict';

// Offline SP-018 exploration only. No HTTP, credentials, database or app integration.
const fail = () => { throw new Error('INVALID_FIXTURE_RESPONSE'); };
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const id = value => typeof value === 'string' && value.trim().length > 0;
const count = value => Number.isSafeInteger(value) && value >= 0;

function utc(value) {
    if (value == null) return null;
    if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(Z|[+-]\d{2}:?\d{2})$/.test(value)) fail();
    const normalized = value.replace(/([+-]\d{2})(\d{2})$/, '$1:$2');
    const local = normalized.slice(0, 19);
    const localMs = Date.parse(local + 'Z');
    if (!Number.isFinite(localMs) || new Date(localMs).toISOString().slice(0, 19) !== local) fail();
    if (!normalized.endsWith('Z')) {
        const hours = Number(normalized.slice(-5, -3));
        const minutes = Number(normalized.slice(-2));
        if (hours > 14 || minutes > 59 || (hours === 14 && minutes !== 0)) fail();
    }
    const parsed = Date.parse(normalized);
    if (!Number.isFinite(parsed)) fail();
    return new Date(parsed).toISOString().replace('.000Z', 'Z');
}

// Heuristic proposal only; confirm provider codes/subcodes against current docs + live evidence.
function errorReason(httpStatus, body) {
    const code = body?.error?.code;
    if (code === 190 || httpStatus === 401) return 'AUTH_REQUIRED';
    if (httpStatus === 429 || [4, 17, 32, 613].includes(code)) return 'RATE_LIMITED';
    if ([10, 200].includes(code)) return 'PERMISSION_REQUIRED';
    return 'UNVERIFIED'; // 403 or code 100 alone does not establish unsupported/permission.
}

function page(body, context) {
    if (!object(context) || !id(context.channelId) || !id(context.postId) ||
        !Number.isSafeInteger(context.connectionRevision) || context.connectionRevision < 1 ||
        !id(context.queryKey) || context.source !== 'MO_PHONG' || !id(context.sourceRef)) fail();
    const syncedAt = utc(context.syncedAt);
    if (!syncedAt || !object(body)) fail();
    if (body.error) return {status: 'FAILURE', reason: errorReason(context.httpStatus, body), source: 'MO_PHONG'};
    if (!Number.isInteger(context.httpStatus) || context.httpStatus < 200 || context.httpStatus >= 300) {
        return {status: 'FAILURE', reason: errorReason(context.httpStatus, body), source: 'MO_PHONG'};
    }
    if (!Array.isArray(body.data)) fail();
    const items = body.data.map(raw => {
        if (!object(raw) || !id(raw.id)) fail();
        if (raw.message != null && typeof raw.message !== 'string') fail();
        if (raw.from != null && (!object(raw.from) || (raw.from.name != null && typeof raw.from.name !== 'string'))) fail();
        if (raw.parent != null && (!object(raw.parent) || !id(raw.parent.id))) fail();
        const createdAt = utc(raw.created_time);
        return {
            platform: 'FACEBOOK', channelId: context.channelId, postId: context.postId,
            connectionRevision: context.connectionRevision, commentId: raw.id,
            parentCommentId: raw.parent?.id ?? null, parentState: raw.parent ? 'KNOWN' : 'UNKNOWN',
            message: raw.message ?? null, contentState: raw.message == null ? 'OMITTED' : 'AVAILABLE',
            authorDisplayName: raw.from?.name ?? null, createdAt,
            timeMissingReason: createdAt ? null : 'PROVIDER_OMITTED', syncedAt,
            source: 'MO_PHONG', sourceRef: context.sourceRef, replyCapability: 'UNVERIFIED'
        };
    });
    // Do not retain/follow provider URLs. A next link signals a page, not a safe request URL.
    if (body.paging != null && !object(body.paging)) fail();
    if (body.paging?.cursors != null && !object(body.paging.cursors)) fail();
    const next = body.paging?.next;
    if (next != null && !id(next)) fail();
    const hasMore = next != null;
    const after = hasMore ? body.paging?.cursors?.after : null;
    if (hasMore && !id(after)) fail();
    return {status: 'SUCCESS', items, hasMore, nextCursor: after ?? null,
        queryKey: context.queryKey, channelId: context.channelId, postId: context.postId,
        connectionRevision: context.connectionRevision, source: 'MO_PHONG'};
}

function mergePages(pages) {
    const items = new Map();
    let scope;
    for (const result of pages) {
        if (result.status !== 'SUCCESS') throw new Error('FAILED_PAGE_NOT_EMPTY');
        const current = JSON.stringify([result.channelId, result.postId, result.connectionRevision, result.queryKey]);
        if (scope && current !== scope) throw new Error('PAGE_SCOPE_MISMATCH');
        scope = current;
        for (const item of result.items) {
            const key = JSON.stringify([item.platform, item.channelId, item.commentId]);
            const previous = items.get(key);
            if (!previous || item.syncedAt >= previous.syncedAt) items.set(key, item);
        }
    }
    return [...items.values()];
}

function metric(value, metadata, missingReason = 'PROVIDER_OMITTED') {
    if (!object(metadata) || metadata.source !== 'MO_PHONG' || !id(metadata.definitionId) ||
        !id(metadata.entityId) || !id(metadata.sourceRef) || !id(metadata.measurementKind)) fail();
    const collectedAt = utc(metadata.collectedAt);
    if (!collectedAt) fail();
    if (value != null && !count(value)) fail();
    const reasons = ['PROVIDER_OMITTED', 'PERMISSION_REQUIRED', 'AUTH_REQUIRED', 'RATE_LIMITED', 'UNVERIFIED', 'NOT_SYNCED'];
    if (value == null && !reasons.includes(missingReason)) fail();
    return {...metadata, collectedAt, value: value ?? null, missingReason: value == null ? missingReason : null};
}

function replyOutcome(httpStatus, body, transportUncertain = false) {
    if (transportUncertain || !Number.isInteger(httpStatus) || httpStatus < 100 || httpStatus >= 500) {
        return {status: 'UNKNOWN', canRetryAutomatically: false};
    }
    if (httpStatus >= 200 && httpStatus < 300 && !body?.error && id(body?.id)) {
        return {status: 'ACCEPTED_NEEDS_RECONCILIATION', replyId: body.id, canRetryAutomatically: false};
    }
    if (httpStatus >= 200 && httpStatus < 300 && !body?.error) return {status: 'UNKNOWN', canRetryAutomatically: false};
    return {status: 'REJECTED', reason: errorReason(httpStatus, body), canRetryAutomatically: false};
}

module.exports = {utc, errorReason, page, mergePages, metric, replyOutcome};
