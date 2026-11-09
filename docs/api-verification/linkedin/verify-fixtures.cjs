'use strict';

const assert = require('node:assert/strict');
const f = require('./fixtures.json');
const p = require('./response-prototype.cjs');
const copy = value => structuredClone(value);
let passed = 0;
function check(name, run) {
    try { run(); passed++; }
    catch { console.error(`FAIL: ${name}`); process.exitCode = 1; }
}
function invalidPage(name, mutate) {
    check(name, () => {
        const body = copy(f.pages[0]);
        mutate(body);
        assert.throws(() => p.commentPage(200, body, f.context), /INVALID_FIXTURE_RESPONSE/);
    });
}
const first = p.commentPage(200, f.pages[0], f.context);
const laterContext = {...f.context, collectedAtMs: f.context.collectedAtMs + 60000};
const second = p.commentPage(200, f.pages[1], laterContext);
const memberContext = {...f.context, queryKey: 'memberFollowersCount:me'};
const orgPostContext = {...f.organizationContext, queryKey: 'organizationalEntityShareStatistics:singlePost:noTimeIntervals'};
const stats = p.organizationPostStatistics(200, f.organizationPostStatistics, orgPostContext);
const stat = key => stats.metrics.find(m => m.metricKey === key);

check('synthetic provenance is explicit', () => assert.equal(f.source, 'MO_PHONG'));
check('no live version claimed', () => assert.equal(f.liveApiVersion, null));
check('epoch milliseconds normalize to UTC', () => assert.equal(first.items[0].createdAt, '2026-11-09T01:00:00.000Z'));
check('native ID above JS integer precision stays string', () => assert.equal(first.items[0].commentId, '9000000000000000101'));
check('key uses actual object, not request share alias', () => assert.equal(first.items[0].commentUrn, 'urn:li:comment:(urn:li:activity:9000000000000000002,9000000000000000101)'));
check('missing actor remains null', () => assert.equal(first.items[1].actorUrn, null));
check('empty content is present', () => assert.equal(first.items[1].contentState, 'AVAILABLE'));
check('omitted content is not deletion', () => assert.equal(second.items[1].contentState, 'OMITTED'));
check('missing time retains reason', () => assert.equal(first.items[1].timeMissingReason, 'PROVIDER_OMITTED'));
check('missing parent is unknown', () => assert.equal(first.items[0].parentState, 'UNKNOWN'));
check('nested parent is composite URN', () => assert.equal(second.items[1].parentCommentUrn, first.items[0].commentUrn));
check('read does not prove reply capability', () => assert.equal(first.items[0].replyCapability, 'UNVERIFIED'));
check('next offset is reconstructed', () => assert.equal(first.nextStart, 2));
check('next page signalled', () => assert.equal(first.hasMore, true));
check('paging links absent from projection', () => assert.equal(JSON.stringify(first).includes('https://'), false));
check('terminal total recognized', () => assert.equal(second.hasMore, false));
check('terminal offset absent', () => assert.equal(second.nextStart, null));
check('no paging does not prove completion', () => assert.equal(p.commentPage(200, {elements: []}, f.context).hasMore, null));
check('no total and no next does not prove completion', () => assert.equal(p.commentPage(200, {elements: [], paging: {start: 0, count: 2, links: []}}, f.context).hasMore, null));
check('empty success is preserved', () => assert.deepEqual(p.commentPage(200, f.empty, f.context).items, []));
check('overlap deduplicates', () => assert.equal(p.mergePages([first, second]).length, 3));
check('latest observation wins', () => assert.equal(p.mergePages([first, second])[0].message, 'Nội dung cập nhật mô phỏng'));
check('late older page cannot revert', () => assert.equal(p.mergePages([second, first])[0].message, 'Nội dung cập nhật mô phỏng'));
check('repeated page is idempotent', () => assert.equal(p.mergePages([first, first]).length, 2));
check('equal numeric IDs on different objects stay distinct', () => {
    const body = copy(f.pages[0]);
    body.elements = [{id: first.items[0].commentId, object: 'urn:li:activity:9000000000000000099'}];
    assert.equal(p.mergePages([first, p.commentPage(200, body, f.context)]).length, 3);
});
for (const [field, value] of Object.entries({channelId: 'another-channel', connectionRevision: 2,
    postUrn: 'urn:li:share:9', targetUrn: 'urn:li:ugcPost:9', apiVersion: '202609', queryKey: 'other-query'})) {
    check(`scope mismatch rejected: ${field}`, () => assert.throws(() => p.mergePages([
        first, p.commentPage(200, f.empty, {...f.context, [field]: value})
    ]), /PAGE_SCOPE_MISMATCH/));
}
for (const e of f.errors) {
    check(`HTTP ${e.httpStatus} is failure, not empty comments`, () => assert.deepEqual(p.commentPage(e.httpStatus, e.body, f.context),
        {status: 'FAILURE', reason: e.reason, source: 'MO_PHONG'}));
    check(`HTTP ${e.httpStatus} is failure, not zero followers`, () => assert.equal(p.followerCount(e.httpStatus, e.body, memberContext).status, 'FAILURE'));
}
check('failed page cannot merge as empty', () => assert.throws(() => p.mergePages([first, p.commentPage(403, {}, f.context)]), /FAILED_PAGE_NOT_EMPTY/));
for (const [name, mutate] of [
    ['numeric ID precision loss rejected', b => { b.elements[0].id = 9000000000000000101; }],
    ['wrong URN suffix rejected', b => { b.elements[0].commentUrn = 'urn:li:comment:(urn:li:activity:1,2)'; }],
    ['missing object cannot use naked ID', b => { delete b.elements[0].object; }],
    ['invalid parent rejected', b => { b.elements[0].parentComment = '123'; }],
    ['timestamp string not silently coerced', b => { b.elements[0].created.time = '1794186000000'; }],
    ['timestamp out of range rejected', b => { b.elements[0].created.time = 8640000000000001; }],
    ['message wrong shape rejected', b => { b.elements[0].message = []; }],
    ['actor array not coerced to URN', b => { b.elements[0].actor = ['urn:li:person:synthetic']; }],
    ['page count zero rejected', b => { b.paging.count = 0; }],
    ['negative start rejected', b => { b.paging.start = -1; }],
    ['unsafe next offset rejected', b => { b.paging.start = Number.MAX_SAFE_INTEGER; }],
    ['contradictory total/next rejected', b => { b.paging.total = 1; }],
    ['paging links wrong shape rejected', b => { b.paging.links = {}; }],
    ['error body inside 200 rejected', b => { b.status = 403; }]
]) invalidPage(name, mutate);
check('missing HTTP evidence rejected', () => assert.throws(() => p.commentPage(undefined, f.pages[0], f.context), /INVALID_FIXTURE_RESPONSE/));
check('real provenance refused', () => assert.throws(() => p.commentPage(200, f.empty, {...f.context, source: 'API_THAT'}), /INVALID_FIXTURE_RESPONSE/));
for (const apiVersion of [202610, '202613']) check(`invalid version ${apiVersion} rejected`, () => assert.throws(() => p.commentPage(200, f.empty, {...f.context, apiVersion}), /INVALID_FIXTURE_RESPONSE/));
check('member follower zero remains zero', () => assert.equal(p.followerCount(200, f.memberFollowers, memberContext).value, 0));
check('missing follower remains null', () => assert.equal(p.followerCount(200, {elements: [{}]}, memberContext).value, null));
check('empty follower response is not zero', () => assert.equal(p.followerCount(200, {elements: []}, memberContext).missingReason, 'PROVIDER_OMITTED'));
check('organization follower total uses firstDegreeSize', () => assert.equal(p.followerCount(200, f.organizationFollowers, f.organizationContext).value, 150));
check('old totalFollowerCounts cannot replace total', () => assert.equal(p.followerCount(200, {totalFollowerCounts: 150}, f.organizationContext).value, null));
check('connections field cannot replace member followers', () => assert.equal(p.followerCount(200, {elements: [{firstDegreeSize: 150}]}, memberContext).value, null));
check('follower time series cannot pass as current total', () => assert.throws(() => p.followerCount(200, f.memberFollowers, {...memberContext, queryKey: 'memberFollowersCount:dateRange'}), /INVALID_FIXTURE_RESPONSE/));
check('dateRange row cannot pass as current total', () => assert.throws(() => p.followerCount(200, {elements: [{memberFollowersCount: 5, dateRange: {}}]}, memberContext), /INVALID_FIXTURE_RESPONSE/));
for (const value of [-1, 1.5, '150', Number.MAX_SAFE_INTEGER + 1]) {
    check(`invalid follower count ${typeof value}:${value}`, () => assert.throws(() => p.followerCount(200, {firstDegreeSize: value}, f.organizationContext), /INVALID_FIXTURE_RESPONSE/));
}
check('organic negative likeCount preserved', () => assert.equal(stat('PROVIDER_LIKE_COUNT').value, -1));
check('like is not all-reaction count', () => assert.equal(stat('REACTION_COUNT').missingReason, 'DEFINITION_MISMATCH'));
check('organic rolling window not full lifetime', () => assert.equal(stats.measurementKind, 'PROVIDER_ROLLING_12_MONTHS_ORGANIC'));
check('zero comments retained', () => assert.equal(stat('COMMENT_COUNT').value, 0));
check('provider engagement not reused as app ER', () => assert.equal(stats.metrics.some(m => m.metricKey === 'ENGAGEMENT_RATE'), false));
check('no statistics means null values', () => assert.equal(p.organizationPostStatistics(200, {elements: []}, orgPostContext).metrics[0].value, null));
for (const [name, mutate] of [
    ['wrong organization', b => { b.elements[0].organizationalEntity = 'urn:li:organization:9'; }],
    ['wrong post', b => { b.elements[0].share = 'urn:li:share:9'; }],
    ['aggregate row', b => { delete b.elements[0].share; }],
    ['null row', b => { b.elements[0] = null; }],
    ['conflicting entity aliases', b => { b.elements[0].ugcPost = 'urn:li:ugcPost:9'; }],
    ['period row in total parser', b => { b.elements[0].timeRange = {start: 0}; }],
    ['negative impression', b => { b.elements[0].totalShareStatistics.impressionCount = -1; }]
]) check(`statistics reject ${name}`, () => {
    const body = copy(f.organizationPostStatistics);
    mutate(body);
    assert.throws(() => p.organizationPostStatistics(200, body, orgPostContext), /INVALID_FIXTURE_RESPONSE/);
});
check('201 ID accepted but needs reconciliation', () => assert.deepEqual(p.replyOutcome(f.replyAccepted),
    {status: 'ACCEPTED_NEEDS_RECONCILIATION', replyId: '9000000000000000104', canRetryAutomatically: false}));
check('header-only 201 supported', () => assert.equal(p.replyOutcome({...f.replyAccepted, body: null}).status, 'ACCEPTED_NEEDS_RECONCILIATION'));
for (const [name, overrides] of [
    ['missing ID', {headers: {}, body: {}}], ['transport uncertain', {transportUncertain: true}],
    ['missing HTTP', {httpStatus: null}], ['500', {httpStatus: 500}], ['408 timeout', {httpStatus: 408}], ['redirect', {httpStatus: 302}],
    ['200 outside conservative fixture acceptance rule', {httpStatus: 200}],
    ['header/body disagree', {body: {id: '9'}}], ['header numeric precision loss', {headers: {'x-restli-id': 9000000000000000104}}],
    ['unreliable resourceidentity header alone', {headers: {'x-resourceidentity-urn': f.replyAccepted.body.commentUrn}, body: {}}],
    ['body comment ID disagrees', {body: {id: f.replyAccepted.body.id, commentUrn: 'urn:li:comment:(urn:li:activity:1,9)'}}]
]) check(`reply ${name} remains unknown without retry`, () => assert.deepEqual(p.replyOutcome({...f.replyAccepted, ...overrides}), {status: 'UNKNOWN', canRetryAutomatically: false}));
for (const status of [401, 403, 429]) check(`reply ${status} rejected without retry`, () => {
    const outcome = p.replyOutcome({source: 'MO_PHONG', httpStatus: status});
    assert.equal(outcome.status, 'REJECTED');
    assert.equal(outcome.canRetryAutomatically, false);
});
check('reply real provenance refused', () => assert.throws(() => p.replyOutcome({...f.replyAccepted, source: 'API_THAT'}), /INVALID_FIXTURE_RESPONSE/));

console.log(`${passed} checks PASS; source=MO_PHONG; API_THAT=0`);
