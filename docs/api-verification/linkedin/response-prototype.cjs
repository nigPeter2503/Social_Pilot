'use strict';

// SP-019 offline exploration only. Synthetic input; no HTTP, credentials or app integration.
const invalid = () => { throw new Error('INVALID_FIXTURE_RESPONSE'); };
const object = v => v !== null && typeof v === 'object' && !Array.isArray(v);
const text = v => typeof v === 'string' && v.trim().length > 0;
const nonnegative = v => Number.isSafeInteger(v) && v >= 0;
const nativeId = v => typeof v === 'string' && /^\d+$/.test(v);
const postUrn = v => typeof v === 'string' && /^urn:li:(share|ugcPost):\d+$/.test(v);
const threadUrn = v => typeof v === 'string' && /^urn:li:(activity|share|ugcPost):\d+$/.test(v);
const commentParts = v => typeof v === 'string' && /^urn:li:comment:\((urn:li:(?:activity|share|ugcPost):\d+),(\d+)\)$/.exec(v);

function epoch(value) {
    if (value == null) return null;
    if (!nonnegative(value) || value > 8640000000000000) invalid();
    return new Date(value).toISOString();
}

function contextOf(c) {
    if (!object(c) || c.source !== 'MO_PHONG' || !text(c.sourceRef) || !text(c.channelId) ||
        !['MEMBER', 'ORGANIZATION'].includes(c.accountType) ||
        !Number.isSafeInteger(c.connectionRevision) || c.connectionRevision < 1 ||
        typeof c.apiVersion !== 'string' || !/^\d{4}(0[1-9]|1[0-2])$/.test(c.apiVersion) ||
        !text(c.queryKey) || !epoch(c.collectedAtMs)) invalid();
    const channelPattern = c.accountType === 'MEMBER' ? /^urn:li:person:[A-Za-z0-9_-]+$/ : /^urn:li:organization:\d+$/;
    if (!text(c.channelUrn) || !channelPattern.test(c.channelUrn)) invalid();
    return {
        source: 'MO_PHONG', sourceRef: c.sourceRef, platform: 'LINKEDIN',
        channelId: c.channelId, channelUrn: c.channelUrn, accountType: c.accountType,
        connectionRevision: c.connectionRevision, apiVersion: c.apiVersion,
        queryKey: c.queryKey, collectedAt: epoch(c.collectedAtMs)
    };
}

function readFailure(httpStatus, body) {
    if (!Number.isInteger(httpStatus) || httpStatus < 100 || httpStatus > 599) invalid();
    if (httpStatus >= 200 && httpStatus < 300) {
        if (!object(body) || body.status >= 400 || body.serviceErrorCode != null) invalid();
        return null;
    }
    const reason = httpStatus === 401 ? 'AUTH_REQUIRED' : httpStatus === 429 ? 'RATE_LIMITED' :
        httpStatus === 403 ? 'ACCESS_DENIED_UNVERIFIED_CAUSE' : httpStatus >= 500 ? 'TEMPORARILY_UNAVAILABLE' : 'UNVERIFIED';
    // Provider messages, URLs and headers are intentionally absent from the projection.
    return {status: 'FAILURE', reason, source: 'MO_PHONG'};
}

function commentPagination(p, request) {
    const unknown = {hasMore: null, nextStart: null, paginationReason: 'NOT_ESTABLISHED'};
    if (p == null) return unknown;
    if (!object(p) || !nonnegative(p.start) || !nonnegative(p.count) || p.count === 0 ||
        !nonnegative(p.start + p.count) || !Array.isArray(p.links) ||
        (p.total != null && !nonnegative(p.total))) invalid();
    for (const link of p.links) if (!object(link) || !text(link.rel)) invalid();
    if (!request) return unknown;

    const nextStart = request.start + request.count;
    const hasMoreByTotal = p.total == null ? null : nextStart < p.total;
    const links = p.links.filter(link => link.rel === 'next');
    const conflict = {...unknown, paginationReason: 'PAGING_CONFLICT'};
    if (p.start !== request.start || p.count !== request.count) return conflict;
    for (const link of links) {
        if (!text(link.href)) return conflict;
        // Inspect offsets only. The caller rebuilds the request from its approved endpoint.
        let url;
        try { url = new URL(link.href, 'https://example.invalid'); }
        catch { return conflict; }
        const start = url.searchParams.getAll('start');
        const count = url.searchParams.getAll('count');
        if (start.length !== 1 || start[0] !== String(nextStart) ||
            count.length !== 1 || count[0] !== String(request.count)) return conflict;
    }
    if (links.length && hasMoreByTotal === false) return conflict;
    const hasMore = links.length ? true : hasMoreByTotal;
    return hasMore == null ? unknown : {hasMore, nextStart: hasMore ? nextStart : null, paginationReason: null};
}

function commentPage(httpStatus, body, c) {
    const context = contextOf(c);
    const targetParent = commentParts(c.targetUrn);
    if (!postUrn(c.postUrn) || !(c.targetUrn === c.postUrn || targetParent)) invalid();
    const {verifiedPostThread, requestPaging} = c;
    if (verifiedPostThread != null && (!object(verifiedPostThread) ||
        verifiedPostThread.postUrn !== c.postUrn || !threadUrn(verifiedPostThread.threadUrn) ||
        (targetParent && targetParent[1] !== verifiedPostThread.threadUrn))) invalid();
    if (requestPaging != null && (!object(requestPaging) || !nonnegative(requestPaging.start) ||
        !nonnegative(requestPaging.count) || requestPaging.count === 0 ||
        !nonnegative(requestPaging.start + requestPaging.count))) invalid();
    const failure = readFailure(httpStatus, body);
    if (failure) return failure;
    if (!Array.isArray(body.elements)) invalid();
    const expectedThread = targetParent?.[1] ?? verifiedPostThread?.threadUrn;
    const verifiedPostUrn = verifiedPostThread?.postUrn ?? null;
    const items = body.elements.map(raw => {
        if (!object(raw) || !nativeId(raw.id) || !threadUrn(raw.object)) invalid();
        const commentUrn = `urn:li:comment:(${raw.object},${raw.id})`;
        if (raw.commentUrn != null && raw.commentUrn !== commentUrn) invalid();
        if (expectedThread && raw.object !== expectedThread) invalid();
        if (raw.parentComment != null) {
            const parent = commentParts(raw.parentComment);
            if (!parent || parent[1] !== raw.object || (targetParent && raw.parentComment !== c.targetUrn)) invalid();
        }
        if (raw.message != null && (!object(raw.message) || (raw.message.text != null && typeof raw.message.text !== 'string'))) invalid();
        if (raw.actor != null && (typeof raw.actor !== 'string' || !/^urn:li:(person|organization|organizationBrand):[A-Za-z0-9_-]+$/.test(raw.actor))) invalid();
        for (const field of ['created', 'lastModified']) {
            if (raw[field] != null && !object(raw[field])) invalid();
        }
        const createdAt = epoch(raw.created?.time);
        const providerUpdatedAt = epoch(raw.lastModified?.time);
        return {
            ...context, postUrn: verifiedPostUrn, postState: verifiedPostUrn ? 'VERIFIED' : 'UNVERIFIED',
            commentId: raw.id, objectUrn: raw.object,
            // Numeric comment ID alone and x-resourceidentity-urn are insufficient keys.
            commentUrn,
            parentCommentUrn: raw.parentComment ?? null, parentState: raw.parentComment ? 'KNOWN' : 'UNKNOWN',
            message: raw.message?.text ?? null, contentState: raw.message?.text == null ? 'OMITTED' : 'AVAILABLE',
            actorUrn: raw.actor ?? null, createdAt, providerUpdatedAt,
            timeMissingReason: createdAt ? null : 'PROVIDER_OMITTED', replyCapability: 'UNVERIFIED',
            needsRefresh: false
        };
    });
    return {
        ...context, status: 'SUCCESS', requestedPostUrn: c.postUrn, targetUrn: c.targetUrn,
        postUrn: verifiedPostUrn, verifiedThreadUrn: verifiedPostThread?.threadUrn ?? null, items,
        ...commentPagination(body.paging, requestPaging)
    };
}

function mergeComment(old, item) {
    const bothUpdated = old.providerUpdatedAt != null && item.providerUpdatedAt != null;
    const fields = ['message', 'parentCommentUrn', 'actorUrn', 'createdAt'];
    const conflict = bothUpdated && item.providerUpdatedAt === old.providerUpdatedAt &&
        fields.some(field => old[field] != null && item[field] != null && old[field] !== item[field]);
    const newer = bothUpdated && item.providerUpdatedAt !== old.providerUpdatedAt
        ? Date.parse(item.providerUpdatedAt) > Date.parse(old.providerUpdatedAt)
        : Date.parse(item.collectedAt) > Date.parse(old.collectedAt);
    const [preferred, fallback] = newer && !conflict ? [item, old] : [old, item];
    const createdAt = preferred.createdAt ?? fallback.createdAt;
    return {
        ...preferred,
        actorUrn: preferred.actorUrn ?? fallback.actorUrn,
        createdAt,
        timeMissingReason: createdAt == null ? preferred.timeMissingReason : null,
        providerUpdatedAt: preferred.providerUpdatedAt ?? fallback.providerUpdatedAt,
        needsRefresh: conflict || preferred.needsRefresh
    };
}

function mergePages(pages) {
    const items = new Map();
    let scope;
    for (const page of pages) {
        if (page.status !== 'SUCCESS' || page.source !== 'MO_PHONG') throw new Error('FAILED_PAGE_NOT_EMPTY');
        const current = JSON.stringify([page.channelId, page.channelUrn, page.requestedPostUrn, page.targetUrn,
            page.postUrn, page.verifiedThreadUrn,
            page.accountType, page.connectionRevision, page.apiVersion, page.queryKey]);
        if (scope && scope !== current) throw new Error('PAGE_SCOPE_MISMATCH');
        scope = current;
        for (const item of page.items) {
            const key = JSON.stringify([item.platform, item.channelId, item.commentUrn]);
            const old = items.get(key);
            items.set(key, old ? mergeComment(old, item) : item);
        }
    }
    return [...items.values()];
}

function followerCount(httpStatus, body, c) {
    const context = contextOf(c);
    const failure = readFailure(httpStatus, body);
    if (failure) return failure;
    let value;
    if (c.accountType === 'MEMBER') {
        if (c.queryKey !== 'memberFollowersCount:me' || !Array.isArray(body.elements) || body.elements.length > 1) invalid();
        if (body.elements.length === 1 && (!object(body.elements[0]) || body.elements[0].dateRange != null)) invalid();
        value = body.elements[0]?.memberFollowersCount;
    } else {
        if (c.queryKey !== 'networkSizes:COMPANY_FOLLOWED_BY_MEMBER') invalid();
        value = body.firstDegreeSize;
    }
    if (value != null && !nonnegative(value)) invalid();
    return {...context, status: 'SUCCESS', metricKey: 'FOLLOWER_COUNT', measurementKind: 'CURRENT_TOTAL',
        value: value ?? null, missingReason: value == null ? 'PROVIDER_OMITTED' : null};
}

function organizationPostStatistics(httpStatus, body, c) {
    const context = contextOf(c);
    if (c.accountType !== 'ORGANIZATION' || !postUrn(c.postUrn) ||
        c.queryKey !== 'organizationalEntityShareStatistics:singlePost:noTimeIntervals') invalid();
    const failure = readFailure(httpStatus, body);
    if (failure) return failure;
    if (!Array.isArray(body.elements) || body.elements.length > 1) invalid();
    const row = body.elements[0];
    if (body.elements.length && (!object(row) || row.organizationalEntity !== c.channelUrn ||
        (row.share != null && row.ugcPost != null) ||
        (row.share ?? row.ugcPost) !== c.postUrn || row.timeRange != null || !object(row.totalShareStatistics))) invalid();
    const stats = row?.totalShareStatistics ?? {};
    const mapping = {IMPRESSION_COUNT: 'impressionCount', COMMENT_COUNT: 'commentCount',
        RESHARE_COUNT: 'shareCount', PROVIDER_LIKE_COUNT: 'likeCount'};
    const metrics = Object.entries(mapping).map(([metricKey, field]) => {
        const value = stats[field];
        // LinkedIn documents negative organic likeCount. Preserve it as provider-specific data.
        if (value != null && (!Number.isSafeInteger(value) || (field !== 'likeCount' && value < 0))) invalid();
        return {metricKey, providerField: field, value: value ?? null,
            missingReason: value == null ? 'PROVIDER_OMITTED' : null};
    });
    metrics.push({metricKey: 'REACTION_COUNT', providerField: null, value: null, missingReason: 'DEFINITION_MISMATCH'});
    return {...context, status: 'SUCCESS', postUrn: c.postUrn,
        measurementKind: 'PROVIDER_ROLLING_12_MONTHS_ORGANIC', metrics};
}

function replyOutcome(envelope) {
    if (!object(envelope) || envelope.source !== 'MO_PHONG') invalid();
    const unknown = {status: 'UNKNOWN', canRetryAutomatically: false};
    const {httpStatus, body, headers = {}, transportUncertain = false, verifiedRequest} = envelope;
    if (typeof transportUncertain !== 'boolean' || !object(headers)) invalid();
    if (transportUncertain || !Number.isInteger(httpStatus) || httpStatus < 100 || httpStatus > 599 || httpStatus === 408 || httpStatus >= 500) return unknown;
    if (httpStatus >= 400) return {status: 'REJECTED', reason: readFailure(httpStatus, body).reason, canRetryAutomatically: false};
    if (httpStatus !== 201) return unknown;
    if (body != null && (!object(body) || body.status >= 400 || body.serviceErrorCode != null)) return unknown;
    const ids = Object.entries(headers).filter(([key]) => key.toLowerCase() === 'x-restli-id').map(([, value]) => value);
    if (body?.id != null) ids.push(body.id);
    if (!ids.length || ids.some(value => !nativeId(value) || value !== ids[0])) return unknown;
    if (verifiedRequest != null && !object(verifiedRequest)) return unknown;
    const comment = commentParts(body?.commentUrn);
    const parent = commentParts(body?.parentComment);
    const expectedParent = commentParts(verifiedRequest?.parentCommentUrn);
    if (body?.commentUrn != null && (!comment || comment[2] !== ids[0])) return unknown;
    if (body?.parentComment != null && (!parent ||
        (expectedParent && body.parentComment !== verifiedRequest.parentCommentUrn))) return unknown;
    if (verifiedRequest?.parentCommentUrn != null && !expectedParent) return unknown;
    const threads = [body?.object, comment?.[1], parent?.[1], verifiedRequest?.threadUrn, expectedParent?.[1]]
        .filter(value => value != null);
    if (threads.some(value => !threadUrn(value) || value !== threads[0])) return unknown;
    return {status: 'ACCEPTED_NEEDS_RECONCILIATION', replyId: ids[0], canRetryAutomatically: false};
}

module.exports = {epoch, readFailure, commentPage, mergePages, followerCount, organizationPostStatistics, replyOutcome};
