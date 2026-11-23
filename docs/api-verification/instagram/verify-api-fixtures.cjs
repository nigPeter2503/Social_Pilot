'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {comments, follower, reach, mediaCount, impression, readError} = require('./response-prototype.cjs');
const root = path.join(__dirname, 'evidence', 'responses');
const get = name => JSON.parse(fs.readFileSync(path.join(root, name), 'utf8'));
const page = '1427548503779474';
const account = '17841459976957663';
const media = '18111630677338097';
const parent = '18105580130575258';
const reply = '18120004997517397';

assert.equal(get('IG-01_identity_response.json').id, page);
assert.equal(get('IG-01_identity_response.json').instagram_business_account.id, account);
assert.deepEqual(comments(get('IG-02_comments_response.json')).items.map(x => x.id), [
    parent, '18133111096696939', '17998313334104655', '18030188072700402'
]);
// Sanitized paging fields cannot be replayed as provider cursors. Verify only visible IDs.
const first = get('IG-03_page1_response.json').data.map(x => x.id);
const second = get('IG-03_page2_response.json').data.map(x => x.id);
assert.deepEqual(first, [parent]);
assert.deepEqual(second, ['18133111096696939']);
assert.deepEqual(get('IG-03_refresh_page1_response.json').data.map(x => x.id), first);
assert.equal(new Set([...first, ...second]).size, 2);
assert.equal(new Set([...first, ...second, ...get('IG-03_refresh_page1_response.json').data.map(x => x.id)]).size, 2);
const terminal = get('IG-03_terminal_page_response.json');
assert.deepEqual(terminal.data.map(x => x.id), ['18030188072700402']);
assert.equal(terminal.paging, undefined);
assert.equal(comments(terminal).after, null);
assert.deepEqual(comments(get('IG-04_replies_before_response.json'), parent).items, []);
assert.equal(get('IG05_post_reply.json').id, reply);
assert.equal(comments(get('IG05_get_replies_readback.json'), parent).items[0].id, reply);
assert.deepEqual(follower(get('IG06_followers.json'), account), {value:1, missingReason:null});
assert.deepEqual(reach(get('IG07_reach.json'), media), {value:0, missingReason:null});
for (const [name, field, value] of [
    ['IG08_like_count.json', 'like_count', 1],
    ['IG09_comments_count.json', 'comments_count', 6],
    ['IG10_shares_count.json', 'shares_count', 0]
]) assert.deepEqual(mediaCount(get(name), media, field), {value, missingReason:null});
assert.deepEqual(impression(get('IG11_impressions_error.json'), '2026-11-21T09:11:22Z'),
    {value:null, missingReason:'UNSUPPORTED'});
assert.deepEqual(reach(get('IG12_token_A_success.json'), media), {value:0, missingReason:null});
// HTTP status was not captured; classification uses the observed Graph code and owner-confirmed token binding.
const denied = get('IG12_token_B_denied.json');
assert.equal(denied.error.code, 10);
assert.equal(denied.error.type, 'OAuthException');
assert.equal(readError(undefined, denied, true), 'PERMISSION_REQUIRED');
assert.equal(readError(undefined, {error: {code: 100, message: 'Invalid parameter'}}, true), 'UNVERIFIED');
assert.deepEqual(follower(get('IG13_followers_recheck.json'), account), {value:1, missingReason:null});
assert.deepEqual(reach(get('IG13_reach_recheck.json'), media), {value:0, missingReason:null});
console.log('PASS sanitized API response fixtures IG-01–IG-13; timing, token binding and production integration not asserted');
