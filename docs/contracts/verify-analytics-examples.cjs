// Draft fixture verification only. This does not exercise Android or a provider API.
// Run at repo root: node docs/contracts/verify-analytics-examples.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const fixturePath = process.argv[2] || path.join(__dirname, 'analytics-examples.json');
const fixture = JSON.parse(fs.readFileSync(fixturePath, 'utf8'));
let passed = 0;
function check(id, verify) {
    verify();
    passed++;
    process.stdout.write(`PASS ${id}\n`);
}
const result = (value, reason = null) => ({ value, reason });
const time = value => Date.parse(value);
check('provenance-and-dto-examples', () => {
    assert.equal(fixture.source, 'MO_PHONG');
    assert.equal(fixture.contractRevision, 'draft-2');
    const { comment, snapshot, metricDefinition, goal } = fixture.dtoExamples;
    assert.equal(comment.source, 'MO_PHONG');
    assert.equal(comment.parentState, 'ROOT');
    assert.equal(comment.parentCommentId, null);
    assert.equal(comment.contentState, 'AVAILABLE');
    assert.equal(typeof comment.message, 'string');
    assert.equal(comment.timeMissingReason, null);
    assert.equal(comment.replyCapability.availability, 'UNVERIFIED');
    assert.equal(comment.replyCapability.verifiedAt, null);
    assert.deepEqual(comment.scope, snapshot.scope);
    assert.deepEqual(goal.scope, snapshot.scope);
    assert.deepEqual(goal.baselineSnapshotIds, [snapshot.snapshotId]);
    assert.equal(goal.baselineValue, snapshot.samples[0].value);
    for (const sample of snapshot.samples) {
        assert.equal(sample.source, 'MO_PHONG');
        assert.deepEqual(sample.scope, snapshot.scope);
        assert.equal(sample.collectionId, snapshot.collectionId);
        assert.equal(sample.definitionId, metricDefinition.definitionId);
        assert.ok(Number.isFinite(time(sample.collectedAt)));
        assert.ok(time(sample.collectedAt) >= time(snapshot.collectionStartedAt));
        assert.ok(time(sample.collectedAt) <= time(snapshot.collectedAt));
        assert.equal(sample.observedAtBasis, 'COLLECTION');
        assert.equal(sample.observedAt, sample.collectedAt);
    }
    assert.equal(goal.metricDefinitionId, metricDefinition.definitionId);
    assert.equal(metricDefinition.measurementKind, 'GAUGE');
    assert.ok(Number.isInteger(goal.revision) && goal.revision > 0);
});
for (const sample of fixture.metricValues) check(`metric/${sample.id}`, () => {
    assert.equal(sample.value === null, sample.missingReason !== null);
    if (sample.value !== null) assert.ok(Number.isFinite(sample.value) && sample.value >= 0);
    if (sample.now) assert.equal(time(sample.now) - time(sample.collectedAt) > sample.thresholdHours * 3600000, sample.isStale);
    if (sample.missingReason === 'NOT_SYNCED') {
        assert.equal(sample.collectedAt, null);
        assert.equal(sample.isStale, null);
    }
});
for (const c of fixture.engagementCases) check(`engagement/${c.id}`, () => {
    // Metadata here is a projection of adapter-normalized identities/windows, not raw HTTP.
    const differs = values => values && new Set(values).size !== 1;
    let actual;
    if (differs(c.componentScopes)) actual = result(null, 'INCOMPATIBLE_SCOPE');
    else if (differs(c.componentWindows)) actual = result(null, 'INCOMPATIBLE_PERIOD');
    else if (differs(c.componentSources)) actual = result(null, 'INCOMPATIBLE_SOURCE');
    else if ([c.reaction, c.comment, c.share, c.impression].includes(null)) actual = result(null, 'MISSING_COMPONENT');
    else if (c.impression === 0) actual = result(null, 'ZERO_DENOMINATOR');
    else actual = result((c.reaction + c.comment + c.share) / c.impression * 100);
    assert.deepEqual(actual, c.expected);
});
for (const c of fixture.snapshotCases) check(`snapshot/${c.id}`, () => {
    const d = fixture.compatibilityDefaults;
    const same = key => JSON.stringify(c['before' + key] ?? d[key.toLowerCase()]) === JSON.stringify(c['after' + key] ?? d[key.toLowerCase()]);
    let actual;
    if (c.baseline === null || c.beforeAt === null) actual = { delta: null, growth: null, reason: 'MISSING_BASELINE' };
    else if (c.current === null || c.afterAt === null) actual = { delta: null, growth: null, reason: 'MISSING_CURRENT' };
    else if (!same('Scope')) actual = { delta: null, growth: null, reason: 'INCOMPATIBLE_SCOPE' };
    else if (c.beforeDefinition !== c.afterDefinition) actual = { delta: null, growth: null, reason: 'INCOMPATIBLE_DEFINITION' };
    else if (!same('Unit') || !same('Kind')) actual = { delta: null, growth: null, reason: 'INCOMPATIBLE_DEFINITION' };
    else if (!same('Source')) actual = { delta: null, growth: null, reason: 'INCOMPATIBLE_SOURCE' };
    else if (!same('Period')) actual = { delta: null, growth: null, reason: 'INCOMPATIBLE_PERIOD' };
    else if (!(time(c.beforeAt) < time(c.afterAt))) actual = { delta: null, growth: null, reason: 'INVALID_TIME_ORDER' };
    else {
        assert.ok(time(c.beforeAt) < time(c.afterAt));
        const delta = c.current - c.baseline;
        actual = { delta, growth: c.baseline === 0 ? null : delta / c.baseline * 100, reason: c.baseline === 0 ? 'ZERO_DENOMINATOR' : null };
    }
    assert.deepEqual(actual, c.expected);
});
for (const c of fixture.goalCases) check(`goal/${c.id}`, () => {
    let actual;
    if ([c.baseline, c.target, c.actual].includes(null)) actual = result(null, 'MISSING_COMPONENT');
    else if (c.target <= c.baseline) actual = result(null, 'INVALID_TARGET');
    else actual = result((c.actual - c.baseline) / (c.target - c.baseline) * 100);
    assert.deepEqual(actual, c.expected);
});
check('pagination/overlap-and-edit', () => {
    const comments = new Map();
    const { scope, pages } = fixture.commentPages;
    for (const page of pages) {
        assert.equal(page.hasMore, page.nextCursor !== null);
        for (const item of page.items) {
            const key = JSON.stringify([scope.platform, scope.channelId, item.commentId]);
            const previous = comments.get(key);
            if (previous) assert.equal(previous.postId, item.postId);
            if (!previous || time(item.providerUpdatedAt) > time(previous.providerUpdatedAt)) comments.set(key, item);
        }
    }
    assert.deepEqual([...comments.values()].map(c => c.commentId).sort(), fixture.commentPages.expectedIds);
    assert.equal([...comments.values()].find(c => c.commentId === 'mock-c1').message, fixture.commentPages.expectedEditedMessage);
});
for (const c of fixture.pageStates) check(`page-state/${c.id}`, () => {
    if (c.outcome === 'SUCCESS') {
        assert.equal(c.error, null);
        assert.deepEqual(c.page.items, []);
        assert.equal(c.page.hasMore, false);
    } else {
        assert.equal(c.page, null);
        assert.ok(['PERMISSION_REQUIRED', 'RATE_LIMITED'].includes(c.error.code));
    }
});
for (const c of fixture.paginationCases) check(`pagination-shape/${c.id}`, () => {
    const valid = c.hasMore === true ? typeof c.nextCursor === 'string' && c.nextCursor.length > 0
        : c.hasMore === false ? c.nextCursor === null
        : c.hasMore === null && (c.nextCursor === null || typeof c.nextCursor === 'string' && c.nextCursor.length > 0);
    assert.equal(valid, c.expectedValid);
});
for (const c of fixture.dedupCases) check(`dedup/${c.id}`, () => {
    const { previous: p, incoming: n } = c;
    let actual;
    if (p.postId !== n.postId) actual = { message: p.message, needsRefresh: true };
    else if (p.providerUpdatedAt && n.providerUpdatedAt) {
        if (time(n.providerUpdatedAt) > time(p.providerUpdatedAt)) actual = { message: n.message, needsRefresh: false };
        else actual = { message: p.message, needsRefresh: n.providerUpdatedAt === p.providerUpdatedAt && n.message !== p.message };
    } else actual = { message: n.message, needsRefresh: false };
    assert.deepEqual(actual, c.expected);
});
const week = fixture.week;
check('week/local-boundaries', () => {
    const format = new Intl.DateTimeFormat('en-GB', { timeZone: week.zoneId, weekday: 'long', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
    for (const boundary of [week.start, week.end]) {
        const parts = Object.fromEntries(format.formatToParts(new Date(boundary)).map(p => [p.type, p.value]));
        assert.equal(parts.weekday, 'Monday');
        assert.equal(parts.hour, '00');
        assert.equal(parts.minute, '00');
    }
    assert.equal(time(week.end) - time(week.start), 7 * 86400000);
});
for (const c of week.points) check(`week/${c.at}`, () => {
    assert.equal(time(c.at) >= time(week.start) && time(c.at) < time(week.end), c.expectedInside);
});
for (const c of fixture.coverageCases) check(`coverage/${c.id}`, () => {
    const times = [...new Set(c.times)].filter(t => time(t) >= time(week.start) && time(t) <= time(week.end)).sort();
    const actual = times.length < 2
        ? { actualStart: null, actualEnd: null, coverage: 'NONE', reason: 'MISSING_BASELINE' }
        : { actualStart: times[0], actualEnd: times[times.length - 1], coverage: times[0] === week.start && times[times.length - 1] === week.end ? 'FULL' : 'PARTIAL', reason: null };
    assert.deepEqual(actual, c.expected);
});
check('top-posts/tie-negative-and-missing', () => {
    const input = fixture.topPosts;
    const compareText = (a, b) => a < b ? -1 : a > b ? 1 : 0;
    const posts = input.posts.filter(p => p.value !== null).sort((a, b) => b.value - a.value
        || (b.publishedAt ? time(b.publishedAt) : -Infinity) - (a.publishedAt ? time(a.publishedAt) : -Infinity)
        || compareText(a.platform, b.platform) || compareText(a.channelId, b.channelId) || compareText(a.postId, b.postId));
    assert.deepEqual(posts.map(p => p.postId), input.expectedIds);
    assert.equal(input.posts.length - posts.length, input.expectedExcludedCount);
});
check('goal/ignore-sample-after-period', () => {
    const c = fixture.goalPeriod;
    const valid = c.samples.filter(s => time(s.at) >= time(c.start) && time(s.at) <= time(c.end)).sort((a, b) => time(a.at) - time(b.at));
    const latest = valid[valid.length - 1];
    assert.equal(latest.value, c.expectedActual);
    assert.equal(latest.at, c.expectedAt);
    assert.equal(latest.at === c.end ? 'FULL' : 'PARTIAL', c.expectedTemporalCoverage);
});
check('group/weighted-engagement-rate', () => {
    const c = fixture.groupRate;
    assert.equal(c.engagements.reduce((a, b) => a + b, 0) / c.impressions.reduce((a, b) => a + b, 0) * 100, c.expectedPercent);
});
process.stdout.write(`RESULT: ${passed} fixture checks passed; MO_PHONG only; no production or API tests.\n`);
