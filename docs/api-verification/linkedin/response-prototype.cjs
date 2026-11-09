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

function commentPage(httpStatus, body, c) {
    const context = contextOf(c);
    if (!postUrn(c.postUrn) || !(postUrn(c.targetUrn) || commentParts(c.targetUrn))) invalid();
    const failure = readFailure(httpStatus, body);
    if (failure) return failure;
    if (!Array.isArray(body.elements)) invalid();
    const items = body.elements.map(raw => {
        if (!object(raw) || !nativeId(raw.id) || !threadUrn(raw.object)) invalid();
        if (raw.commentUrn != null && (!commentParts(raw.commentUrn) || commentParts(raw.commentUrn)[2] !== raw.id)) invalid();
        if (raw.parentComment != null && !commentParts(raw.parentComment)) invalid();
        if (raw.message != null && (!object(raw.message) || (raw.message.text != null && typeof raw.message.text !== 'string'))) invalid();
        if (raw.actor != null && (typeof raw.actor !== 'string' || !/^urn:li:(person|organization|organizationBrand):[A-Za-z0-9_-]+$/.test(raw.actor))) invalid();
        for (const field of ['created', 'lastModified']) {
            if (raw[field] != null && !object(raw[field])) invalid();
        }
        const createdAt = epoch(raw.created?.time);
        const providerUpdatedAt = epoch(raw.lastModified?.time);
        return {
            ...context, postUrn: c.postUrn, commentId: raw.id, objectUrn: raw.object,
            // Numeric comment ID alone and x-resourceidentity-urn are insufficient keys.
            commentUrn: `urn:li:comment:(${raw.object},${raw.id})`,
            parentCommentUrn: raw.parentComment ?? null, parentState: raw.parentComment ? 'KNOWN' : 'UNKNOWN',
            message: raw.message?.text ?? null, contentState: raw.message?.text == null ? 'OMITTED' : 'AVAILABLE',
            actorUrn: raw.actor ?? null, createdAt, providerUpdatedAt,
            timeMissingReason: createdAt ? null : 'PROVIDER_OMITTED', replyCapability: 'UNVERIFIED'
        };
    });
    let hasMore = null;
    let nextStart = null;
    if (body.paging != null) {
        const p = body.paging;
        if (!object(p) || !nonnegative(p.start) || !nonnegative(p.count) || p.count === 0 ||
            !nonnegative(p.start + p.count) || !Array.isArray(p.links) ||
            (p.total != null && !nonnegative(p.total))) invalid();
        for (const link of p.links) if (!object(link) || !text(link.rel)) invalid();
        const nextLink = p.links.some(link => link.rel === 'next');
        if (p.total != null) hasMore = p.start + p.count < p.total;
        if (nextLink && hasMore === false) invalid();
        if (nextLink) hasMore = true;
        if (hasMore === true) nextStart = p.start + p.count;
    }
    return {
        ...context, status: 'SUCCESS', postUrn: c.postUrn, targetUrn: c.targetUrn, items,
        hasMore, nextStart, paginationReason: hasMore == null ? 'NOT_ESTABLISHED' : null
    };
}

function mergePages(pages) {
    const items = new Map();
    let scope;
    for (const page of pages) {
        if (page.status !== 'SUCCESS' || page.source !== 'MO_PHONG') throw new Error('FAILED_PAGE_NOT_EMPTY');
        const current = JSON.stringify([page.channelId, page.channelUrn, page.postUrn, page.targetUrn,
            page.accountType, page.connectionRevision, page.apiVersion, page.queryKey]);
        if (scope && scope !== current) throw new Error('PAGE_SCOPE_MISMATCH');
        scope = current;
        for (const item of page.items) {
            const key = JSON.stringify([item.platform, item.channelId, item.commentUrn]);
            const old = items.get(key);
            // Observation time resolves overlap only. This is not a production race/transaction policy.
            if (!old || Date.parse(item.collectedAt) >= Date.parse(old.collectedAt)) items.set(key, item);
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
    const {httpStatus, body, headers = {}, transportUncertain = false} = envelope;
    if (typeof transportUncertain !== 'boolean' || !object(headers)) invalid();
    if (transportUncertain || !Number.isInteger(httpStatus) || httpStatus < 100 || httpStatus > 599 || httpStatus === 408 || httpStatus >= 500) return unknown;
    if (httpStatus >= 400) return {status: 'REJECTED', reason: readFailure(httpStatus, body).reason, canRetryAutomatically: false};
    if (httpStatus !== 201) return unknown;
    if (body != null && (!object(body) || body.status >= 400 || body.serviceErrorCode != null)) return unknown;
    const ids = Object.entries(headers).filter(([key]) => key.toLowerCase() === 'x-restli-id').map(([, value]) => value);
    if (body?.id != null) ids.push(body.id);
    if (!ids.length || ids.some(value => !nativeId(value) || value !== ids[0])) return unknown;
    if (body?.commentUrn != null && (!commentParts(body.commentUrn) || commentParts(body.commentUrn)[2] !== ids[0])) return unknown;
    return {status: 'ACCEPTED_NEEDS_RECONCILIATION', replyId: ids[0], canRetryAutomatically: false};
}

module.exports = {epoch, readFailure, commentPage, mergePages, followerCount, organizationPostStatistics, replyOutcome};
