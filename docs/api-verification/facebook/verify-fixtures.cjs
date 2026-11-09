'use strict';

const assert = require('node:assert/strict');
const f = require('./fixtures.json');
const p = require('./response-prototype.cjs');
let passed = 0;
function check(name, run) {
    try { run(); passed++; }
    catch { console.error(`FAIL: ${name}`); process.exitCode = 1; }
}
const first = p.page(f.pages[0], f.context);
const second = p.page(f.pages[1], {...f.context, syncedAt: '2026-11-09T03:01:00Z'});
const merged = p.mergePages([first, second]);
check('fixture has explicit provenance', () => assert.equal(f.source, 'MO_PHONG'));
check('api version remains unverified', () => assert.equal(f.apiVersion, null));
check('normalize timezone to UTC', () => assert.equal(first.items[0].createdAt, '2026-11-09T02:00:00Z'));
check('missing author is nullable', () => assert.equal(first.items[1].authorDisplayName, null));
check('empty message is available content', () => assert.equal(first.items[1].contentState, 'AVAILABLE'));
check('missing message is omitted, not deleted', () => assert.equal(second.items[1].contentState, 'OMITTED'));
check('missing created time retains reason', () => assert.equal(first.items[1].timeMissingReason, 'PROVIDER_OMITTED'));
check('missing parent does not imply root', () => assert.equal(first.items[0].parentState, 'UNKNOWN'));
check('reply parent preserved', () => assert.equal(second.items[1].parentCommentId, 'synthetic-c1'));
check('read success does not establish reply capability', () => assert.equal(first.items[0].replyCapability, 'UNVERIFIED'));
check('next cursor extracted without URL', () => assert.equal(first.nextCursor, 'synthetic-cursor-1'));
check('provider URL absent from result', () => assert.equal(JSON.stringify(first).includes('graph.facebook.com'), false));
check('terminal cursor alone does not mean another page', () => assert.equal(second.hasMore, false));
check('terminal cursor discarded', () => assert.equal(second.nextCursor, null));
check('overlapping pages deduplicated', () => assert.equal(merged.length, 3));
check('latest observed content wins', () => assert.equal(merged[0].message, 'Nội dung cập nhật mô phỏng'));
check('late older page cannot revert content', () => assert.equal(p.mergePages([second, first])[0].message, 'Nội dung cập nhật mô phỏng'));
check('repeat page is idempotent', () => assert.equal(p.mergePages([first, first]).length, 2));
check('empty success remains success', () => assert.deepEqual(p.page(f.empty, f.context).items, []));
for (const key of ['channelId', 'postId', 'connectionRevision', 'queryKey']) {
    check(`cannot merge pages across ${key}`, () => assert.throws(() => p.mergePages([
        first, p.page(f.empty, {...f.context, [key]: key === 'connectionRevision' ? 2 : 'another-scope'})
    ]), /PAGE_SCOPE_MISMATCH/));
}
for (const item of f.errors) {
    check(`error ${item.caseId} remains failure`, () => assert.deepEqual(
        p.page(item.body, {...f.context, httpStatus: item.httpStatus}),
        {status: 'FAILURE', reason: item.expectedReason, source: 'MO_PHONG'}));
}
check('failed page cannot silently become empty', () => assert.throws(() => p.mergePages([
    first, p.page(f.errors[0].body, {...f.context, httpStatus: 400})
]), /FAILED_PAGE_NOT_EMPTY/));
check('zero follower is measured zero', () => assert.deepEqual(
    [p.metric(f.followerResponse.followers_count, f.metricMetadata).value,
        p.metric(0, f.metricMetadata).missingReason], [0, null]));
check('missing shares is null, not zero', () => assert.equal(p.metric(f.postResponse.shares?.count, f.metricMetadata).value, null));
check('permission loss is distinct from omitted', () => assert.equal(p.metric(null, f.metricMetadata, 'PERMISSION_REQUIRED').missingReason, 'PERMISSION_REQUIRED'));
check('metric preserves definition and collection time', () => assert.equal(p.metric(5, f.metricMetadata).collectedAt, '2026-11-09T03:00:00Z'));
for (const value of [-1, 1.2, '0', Infinity, Number.MAX_SAFE_INTEGER + 1]) {
    check(`invalid count ${String(value)} rejected`, () => assert.throws(() => p.metric(value, f.metricMetadata)));
}
for (const value of ['2026-02-30T00:00:00Z', '2026-11-09', '2026-11-09T25:00:00Z', '2026-11-09T00:00:00+14:30']) {
    check(`invalid time ${value} rejected`, () => assert.throws(() => p.utc(value)));
}
check('missing id rejected', () => assert.throws(() => p.page({data: [{}]}, f.context)));
check('missing data rejected', () => assert.throws(() => p.page({}, f.context)));
check('malformed paging rejected', () => assert.throws(() => p.page({data: [], paging: 'invalid'}, f.context)));
check('malformed cursors rejected', () => assert.throws(() => p.page({data: [], paging: {cursors: []}}, f.context)));
check('next link without cursor cannot silently finish', () => assert.throws(() => p.page({data: [], paging: {next: 'synthetic'}}, f.context)));
check('prototype rejects real provenance', () => assert.throws(() => p.page(f.empty, {...f.context, source: 'API_THAT'})));
check('reply id still needs Page reconciliation', () => assert.equal(p.replyOutcome(200, f.replyAccepted).status, 'ACCEPTED_NEEDS_RECONCILIATION'));
check('timeout cannot trigger retry', () => assert.deepEqual(p.replyOutcome(0, null, true), {status: 'UNKNOWN', canRetryAutomatically: false}));
check('absent HTTP response is uncertain', () => assert.equal(p.replyOutcome(0, null).status, 'UNKNOWN'));
check('server error is uncertain', () => assert.equal(p.replyOutcome(503, {}).status, 'UNKNOWN'));
check('success without id is uncertain', () => assert.equal(p.replyOutcome(200, {}).status, 'UNKNOWN'));
check('permission denial remains rejected', () => assert.equal(p.replyOutcome(400, f.errors[0].body).status, 'REJECTED'));
console.log(`${passed} checks PASS${process.exitCode ? '; failures above' : ''}; MO_PHONG only; 0 live API calls.`);
