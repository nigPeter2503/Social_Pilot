'use strict';

// Offline projection of already scoped Graph responses. No credentials or network I/O.
function id(value) {
    if (typeof value !== 'string' || !value.trim()) throw new Error('Invalid provider ID');
    return value;
}

function metric(value) {
    if (value === undefined || value === null) return {value: null, missingReason: 'PROVIDER_OMITTED'};
    if (!Number.isSafeInteger(value) || value < 0) throw new Error('Invalid count');
    return {value, missingReason: null};
}

function success(body) {
    if (!body || typeof body !== 'object' || Array.isArray(body) || body.error) throw new Error('Expected successful Graph response');
    return body;
}

function readError(httpStatus, body, permissionDeniedForOperation = false) {
    const code = body?.error?.code;
    if (httpStatus === 401 || code === 190) return 'AUTH_REQUIRED';
    if (httpStatus === 429 || [4, 17, 32, 613].includes(code)) return 'RATE_LIMITED';
    if (httpStatus >= 200 && httpStatus < 300 && !body?.error) return null;
    return permissionDeniedForOperation ? 'PERMISSION_REQUIRED' : 'UNVERIFIED';
}

function comments(body, parentId = null) {
    success(body);
    if (!Array.isArray(body.data)) throw new Error('Missing comment page');
    const items = body.data.map(row => {
        const parent = row.parent_id == null ? parentId : id(row.parent_id);
        if (parentId !== null && parent !== parentId || parent === row.id) throw new Error('Invalid reply parent');
        if (row.text != null && typeof row.text !== 'string') throw new Error('Invalid text');
        return {id: id(row.id), parent, text: row.text ?? null};
    });
    const after = body.paging?.next ? id(body.paging.cursors?.after) : null;
    if (after && /:\/\/|access_token|bearer /i.test(after)) throw new Error('Invalid cursor');
    return {items, after};
}

function follower(body, accountId) {
    if (id(success(body).id) !== id(accountId)) throw new Error('Wrong account');
    return metric(body.followers_count);
}

function reach(body, mediaId) {
    success(body);
    if (!Array.isArray(body.data)) throw new Error('Missing insight list');
    if (!body.data.length) return metric(undefined);
    const [row] = body.data;
    if (body.data.length !== 1 || row.name !== 'reach' || row.period !== 'lifetime'
            || row.id !== id(mediaId) + '/insights/reach/lifetime') throw new Error('Wrong insight');
    if (!Array.isArray(row.values) || row.values.length > 1) throw new Error('Invalid insight values');
    return metric(row.values.length ? row.values[0].value : undefined);
}

module.exports = {comments, follower, reach, readError};
