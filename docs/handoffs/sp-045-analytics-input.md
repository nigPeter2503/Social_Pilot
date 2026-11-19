# SP-045.05 — Đầu vào analytics cho SP-046 và SP-047

**Version 0.1.0 · 18/11/2026 · Bàn giao sớm để triển khai từ fixture**

**Người nhận:** Tô Lãng Nghị (SP-046.01/.02/.03) và Đỗ Phước Thiện (SP-047, khung dashboard). Thiện xác nhận chữ ký qua HO-DTO và SP-046.01.

| Nguồn | Revision đối chiếu |
|---|---|
| [SP-020 §§3, 5–10](../analytics-data-contract.md) · [fixture](../contracts/analytics-examples.json) · [lệnh kiểm](../contracts/verify-analytics-examples.cjs) | draft-4, 11/11/2026, commit tích hợp **e51dfc3375e389ed39b7ae18d44a8c7525d88cc1**. Nội dung là đề xuất để review. |
| [SP-012 §§3, 5–6](../room-schema-contract.md) | Thiết kế logic trên Room v1, commit **5a5f82b732aeb97423925c6b2e2667c940777486**. |
| [Brief Thiện SP-047](../TaskBrief/W2-W3/tv3-thien-taskbrief.md#sp-047) · [quy trình repo](../../CONTRIBUTING.md) | **f4daaf23bc301df1be4f61da25f64832af5a9924** và **47ec6cba270a8d7d20301f6972e4144051f0977c**. |

Base đã đối chiếu: remote-tracking ref local **origin/main @ 230c2a6ebcd8785288e57764c4ebb5fb6bbd4afa**, cũng là merge-base với HEAD **7931f4dc5325f0550241f7d5188521bccec63b59** lúc chuẩn bị gói. Các tài liệu và fixture trong bảng giống nhau ở hai ref. SHA này định danh đầu vào của gói trong working tree. Kiểm tra ref GitHub live chưa thành công.

Changelog **0.1.0**: pin đầu vào, namespace fixture, DTO mẫu và danh sách quyết định cho HO-DTO. Phạm vi thay đổi là tài liệu và ví dụ; Room v1 vẫn là baseline. Entity/DAO, migration, lịch thu thật và adapter thuộc các task tiếp theo.

## 1. Shape logic và định nghĩa metric

Trong bảng, **ID** là chuỗi opaque không rỗng, giữ nguyên hoa/thường và số 0 đầu. **UTC** là chuỗi ISO 8601 đến giây với hậu tố Z trên wire; Room dùng UTC epoch millisecond kiểu long. Dấu **?** biểu thị nullable. Dùng Java long/Long cho count và delta nguyên, double/Double cho phần trăm, int cho giới hạn/đếm coverage. Kiểu Java cụ thể thuộc HO-DTO.

| Shape | Trường và bất biến pin theo draft-4 |
|---|---|
| ChannelScope | platform enum FACEBOOK/LINKEDIN, channelId ID nội bộ, connectionRevision long dương. Mọi kết quả gắn scope hiện hành. Dùng ChannelInfo để biết account type. Kiểm revision và quyền sở hữu trong cùng transaction ghi; hủy và bỏ callback thuộc revision cũ. |
| MetricDefinition | definitionId ID, metricKey enum, unit COUNT/PERCENT, entityType CHANNEL/POST, measurementKind GAUGE/CUMULATIVE/PERIOD, providerMetricName string?, aggregation, componentDefinitionIds list ID. Registry giữ revision theo definitionId; đổi nghĩa/phạm vi thì đổi ID. ID và mapping production chờ xác minh. |
| Snapshot | snapshotId, collectionId, scope, entityType, entityId, collectionStartedAt UTC, collectedAt UTC lúc hoàn tất, source, samples list MetricSample. Subject là chính kênh hoặc bài thuộc kênh. |
| MetricSample | scope, entityType, entityId, definitionId, value number?, missingReason enum?, observedAt UTC, observedAtBasis PROVIDER/COLLECTION, collectedAt UTC, providerPeriodStart/End UTC?, providerApiVersion string?, source enum, sourceRef ID bằng chứng đã che, collectionId. Mỗi collectedAt nằm trong thời gian thu của snapshot và giữ đúng thời điểm nhận. PERIOD có đủ bounds nửa mở; GAUGE/CUMULATIVE có bounds null. |
| Collection và khóa lưu trữ | collectionId + scope + startedAt/completedAt. Khóa logic Snapshot là (channelId, entityType, entityId, collectionId), tương ứng subjectType/subjectId của SP-012. Sample dùng (snapshotId, definitionId) nếu mỗi definition chỉ có một mẫu trong đợt thu. Retry cùng đợt giữ ID; lần đo mới cấp ID mới. Batch theo scope/subject/thời điểm và definition. FK đa hình, period key, index chính thức và version/migration chờ Đạt qua HO-SCHEMA. |

Mỗi sample có provenance API_THAT, FIXTURE_TU_API_DA_CHE, MO_PHONG hoặc PROTOTYPE. Chỉ API_THAT vào lịch sử production, với mapping/version đã xác minh và sourceRef đã che. Không lưu token, secret ref hoặc URL chứa credential trong analytics. Cùng collectionId vẫn cần bằng chứng alignment của measurement window trước khi ghép metric đa endpoint.

| Metric / đơn vị | Entity và kiểu đo | Định nghĩa, phép tính và trạng thái |
|---|---|---|
| FOLLOWER_COUNT · COUNT | CHANNEL · GAUGE | Mức follower tại observedAt. Fixture dùng **mock-followers-v1**, aggregation LATEST, providerMetricName null. Facebook PAGE có bằng chứng spike cho followers_count; mapping vào scope/revision app còn chờ SP-045.01. LinkedIn MEMBER/ORGANIZATION cần xác minh theo account; connections là metric khác. |
| REACTION_COUNT · COUNT | POST · CUMULATIVE hoặc PERIOD theo định nghĩa | Chọn tổng reaction hoặc like theo nguồn. Cần definition riêng cho hai nghĩa và mỗi phạm vi đo. |
| COMMENT_COUNT · COUNT | POST · CUMULATIVE hoặc PERIOD theo định nghĩa | Tách top-level và stream/bao gồm reply. Tên provider và phạm vi của từng definition còn chờ SP-045.02. |
| SHARE_COUNT · COUNT | POST · CUMULATIVE hoặc PERIOD theo định nghĩa | Share/reshare theo phạm vi provider. Facebook spike có trường bị bỏ qua, giữ null/PROVIDER_OMITTED. |
| IMPRESSION_COUNT · COUNT | POST · CUMULATIVE hoặc PERIOD theo định nghĩa | Impression theo mapping đã kiểm. Facebook media views và unique viewers có định nghĩa riêng; impression chưa xác minh. |
| ENGAGEMENT_COUNT · COUNT | POST · Kế thừa phạm vi đo của thành phần | E = reaction + comment + share cùng scope/định nghĩa thành phần và measurement window. Thiếu thành phần → null/MISSING_COMPONENT. Definition dẫn đúng componentDefinitionIds. |
| ENGAGEMENT_RATE · PERCENT | POST · Kế thừa phạm vi đo của thành phần | ER = E / impression × 100. Impression 0 → null/ZERO_DENOMINATOR. ER nhóm dùng sum(E) / sum(impression) × 100 trên dữ liệu tương thích. Kiểu đo, aggregation enum và ID derived production còn đề xuất. |
| Delta/growth và tổng tuần | So sánh trong cùng scope và entity | GAUGE/CUMULATIVE tương thích dùng current − baseline và delta / baseline × 100. Delta âm hợp lệ. Baseline 0 chỉ làm growth thành null. PERIOD khác kỳ giữ riêng. Tổng tuần cần tập bài cố định và hai mốc chung; subtotal thiếu bài mang PARTIAL_COVERAGE. |

Bằng chứng [Facebook SP-018 ở base 230c2a6](../facebook-comments-metrics-verification.md) pin Graph v27.0: FB-04/10 follower, FB-05/06 reaction/comment, FB-07 share bị bỏ qua, FB-08 media views/viewers. Các ca này là bằng chứng spike; adapter còn phải ràng buộc kênh, app revision và giờ thu.

[LinkedIn SP-019 ở 306a1da](https://github.com/nigPeter2503/Social_Pilot/blob/306a1da996b67ed47189373fe91efb7027b7750e/docs/linkedin-comments-metrics-verification.md) có prototype và version khảo sát 202610, LI-01–09 live ghi NOT_RUN. Ứng viên follower là memberFollowersCount cho MEMBER và networkSizes.firstDegreeSize với edge COMPANY_FOLLOWED_BY_MEMBER cho ORGANIZATION. Giữ riêng MEMBER TOTAL theo từng metric, ORGANIZATION organic single-post và thống kê tổ chức theo kỳ. Thay version provider chỉ giữ khả năng so sánh khi đã xác minh nghĩa và phạm vi tương đương.

## 2. Query và DTO dự thảo

| Shape | Trường và cách dùng |
|---|---|
| DashboardQuery | channelIds list ID, metricDefinitionId ID, requestedStart/End UTC, zoneId IANA, cohort, grouping enum TOPIC/FORMAT/PUBLISH_HOUR, topLimit int > 0. Đề xuất cohort = {cohortPostIds: list ID}; nếu dùng fingerprint thì lưu cả danh sách. Repository resolve channelIds sang scope hiện hành trước khi truy vấn/refresh; hình thức truyền scope/revision tường minh chờ HO-DTO. |
| DashboardData | List channelCards, postMetrics, growthPoints, weeklyEngagement, topPosts, groupedMetrics, goals; coverage, lastSuccessAt UTC?, lastAttemptAt UTC?, syncState enum IDLE/LOADING/ERROR, errors list lỗi chuẩn hóa; offline boolean riêng. Danh sách rỗng của query thành công được render empty. Cache có thể đi cùng lỗi refresh và offline. |
| ChannelCard / PostMetrics | Card có scope, displayName, follower MetricValue, followerCollectedAt UTC?, freshness, weeklyEngagement CalculatedValue, syncState, errors. PostMetrics có scope, postId ID, metadata nullable (title, publishedAt, primaryTopicId, format) và list metric entries có definitionId, MetricValue/CalculatedValue, collectedAt và freshness. Đề xuất gắn sourceSnapshotIds đến từng số đo và tra provenance bằng đúng ID khi cần. |
| CalculatedValue | value number?, reason enum?, missingDetails list {componentDefinitionId, missingReason}, definitionId, requestedStart/End UTC, actualStart/End UTC?, coverage, sourceSnapshotIds list ID và freshness. Dùng reason như MISSING_BASELINE, MISSING_CURRENT, INCOMPATIBLE_SCOPE/DEFINITION/PERIOD/SOURCE, INVALID_TIME_ORDER hoặc INVALID_TARGET theo phép tính. Kết quả đầy đủ có reason null; subtotal có số và PARTIAL_COVERAGE. |
| Coverage / Freshness | coverage = {temporal, cohort, includedCount int, totalCount int, excludedReasons}. Hai trục enum FULL/PARTIAL/NONE độc lập; đề xuất excludedReasons là list {postId, reason}. freshness = {isStale boolean?, lastSuccessAt UTC?}; chưa từng đo có isStale null. Ngưỡng 48 giờ là đề xuất theo lịch hằng ngày, dùng phép so sánh lớn hơn nghiêm ngặt. |
| GrowthPoint | scope, definitionId, snapshotId ID?, observedAt UTC, value number?, missingReason enum?, isGap boolean. Với gap, observedAt là mốc kỳ vọng của chart, snapshotId/value null. Điểm thật dùng observedAt của sample, kèm freshness và provenance từ sample tham chiếu; cách trả trực tiếp hai trường này chờ HO-DTO. |
| TopPost / GroupedMetric | TopPost có scope, postId, title/publishedAt nullable, selectedMetric, rank int, actualStart/End và coverage. Chỉ xếp số tương thích bằng value giảm, publishedAt giảm (null cuối), rồi platform/channelId/postId tăng khi hòa. GroupedMetric có groupKind, groupKey, label?, CalculatedValue, postCount và excludedCount. Giờ đăng theo zone của query, metadata thiếu vào UNKNOWN; dùng primaryTopicId hoặc khai báo overlap nếu nhiều nhãn. |
| Goal / GoalProgress | Goal của kênh có ID/revision, scope, metricDefinitionId, kind, unit, baselineValue, baselineSnapshotIds, targetValue, periodStart/End, zoneId, cohortPostIds, createdAt. Progress giữ actual/rawProgress nullable, reason, actual bounds, coverage, sourceSnapshotIds, calculatedAt, isStale và completionStatus. Coverage thiếu hoặc stale giữ UNKNOWN. Goal UI đầy đủ thuộc SP-049. |

MetricValue dùng value hữu hạn hoặc null; missingReason null khi có số. Khi thiếu, giữ reason gốc: PERMISSION_REQUIRED, UNSUPPORTED, UNVERIFIED, NOT_SYNCED, PROVIDER_OMITTED, AUTH_REQUIRED, NETWORK_ERROR hoặc RATE_LIMITED. Capability xác minh theo từng operation, scope và revision. Lỗi lần đọc dùng SocialError với code/nextAction, và retryAfterAt UTC? bên cạnh error khi có. Một refresh thất bại giữ mẫu tốt, giờ thu và lastSuccessAt; cập nhật riêng lastAttemptAt/lỗi.

Kỳ sự kiện là thứ Hai đến Chủ nhật theo zone cấu hình, [start,end). Riêng chọn snapshot cho delta có thể dùng mốc đúng end làm ranh giới đo. Lấy mốc đầu/cuối tương thích trong kỳ theo observedAt/basis, giữ actualStart/End. Thiếu biên → temporal PARTIAL; dưới hai mốc → không có delta. Không dùng mẫu sau end, bù điểm hoặc nội suy. Thiện format số/thời gian; Nghị cung cấp phép tính và provenance. Kênh/nền tảng không tương thích hiển thị riêng.

### Payload MO_PHONG dùng trong IDE

Mở gốc Social_Pilot trong IDE, đặt working directory ở gốc repo. Đọc fixture SP-020 bằng khóa/ID ở mục 3, rồi dùng các đoạn JSON sau để dựng fake repository. Các object trong views là phần DTO dùng cho từng trạng thái; hướng gắn sourceRef trực tiếp vào UI hoặc tra theo ID đang đề xuất. Chạy với clock cố định bằng now.

~~~json
{
  "handoffVersion": "0.1.0",
  "source": "MO_PHONG",
  "query": {
    "channelIds": ["mock-page-a"],
    "metricDefinitionId": "mock-followers-v1",
    "requestedStart": "2026-11-01T17:00:00Z",
    "requestedEnd": "2026-11-08T17:00:00Z",
    "zoneId": "Asia/Ho_Chi_Minh",
    "cohort": { "cohortPostIds": [] },
    "grouping": "TOPIC",
    "topLimit": 5
  },
  "resolvedScope": { "platform": "FACEBOOK", "channelId": "mock-page-a", "connectionRevision": 1 },
  "views": {
    "dashboard-empty": {
      "channelCards": [], "postMetrics": [], "growthPoints": [], "weeklyEngagement": [],
      "topPosts": [], "groupedMetrics": [], "goals": [],
      "coverage": { "temporal": "NONE", "cohort": "NONE", "includedCount": 0, "totalCount": 0, "excludedReasons": [] },
      "lastSuccessAt": null, "lastAttemptAt": null, "syncState": "IDLE", "offline": false, "errors": []
    },
    "dashboard-stale-refresh": {
      "now": "2026-11-11T18:00:00Z",
      "lastSuccessAt": "2026-11-08T17:00:00Z",
      "lastAttemptAt": "2026-11-11T18:00:00Z",
      "syncState": "ERROR",
      "offline": true,
      "errors": [{
        "error": { "code": "NETWORK_ERROR", "nextAction": "RETRY_WHEN_SAFE" },
        "retryAfterAt": null
      }],
      "followerEntry": {
        "scope": { "platform": "FACEBOOK", "channelId": "mock-page-a", "connectionRevision": 1 },
        "definitionId": "mock-followers-v1",
        "follower": { "value": 100, "missingReason": null },
        "followerCollectedAt": "2026-11-08T17:00:00Z",
        "freshness": { "isStale": true, "lastSuccessAt": "2026-11-08T17:00:00Z" },
        "sourceSnapshotIds": ["mock-snapshot-1"],
        "source": "MO_PHONG",
        "sourceRef": "analytics-draft-4-snapshot"
      }
    },
    "growth-gap": {
      "scope": { "platform": "FACEBOOK", "channelId": "mock-page-a", "connectionRevision": 1 },
      "definitionId": "mock-followers-v1",
      "snapshotId": null,
      "observedAt": "2026-11-01T17:00:00Z",
      "value": null,
      "missingReason": "NOT_SYNCED",
      "isGap": true
    },
    "follower-delta-missing-baseline": {
      "value": null,
      "reason": "MISSING_BASELINE",
      "missingDetails": [],
      "definitionId": "mock-followers-v1",
      "requestedStart": "2026-11-01T17:00:00Z",
      "requestedEnd": "2026-11-08T17:00:00Z",
      "actualStart": null,
      "actualEnd": null,
      "coverage": { "temporal": "NONE", "cohort": "NONE", "includedCount": 0, "totalCount": 0, "excludedReasons": [] },
      "sourceSnapshotIds": ["mock-snapshot-1"],
      "freshness": { "isStale": true, "lastSuccessAt": "2026-11-08T17:00:00Z" }
    }
  }
}
~~~

## 3. Fixture và kết quả để tái lập

Tất cả các ca dưới đây có nhãn **MO_PHONG** trên UI/test. Với ca có PASS, chạy **node docs/contracts/verify-analytics-examples.cjs** từ gốc repo và tìm tên PASS. Các ca view bổ sung dùng query/JSON mục 2 và được đối chiếu theo quy trình ở dưới bảng. Các ca công thức trong JSON nguồn là projection; lấy snapshotId cho DTO từ **dtoExamples.snapshot**. ID ca như decrease dùng để chọn input test, còn sourceSnapshotIds chứa ID của các snapshot dùng làm đầu vào.

| Ca / khóa JSON | Đầu vào và kết quả mong đợi | Kiểm lại |
|---|---|---|
| Rỗng · **coverageCases/no-history** + **views/dashboard-empty** | Query thành công, cohort rỗng và không có history. Coverage temporal NONE, actual bounds null, delta thiếu baseline. UI empty với IDLE, 0 bài, không lỗi; không có giá trị tổng đo được. | PASS coverage/no-history; nạp view dashboard-empty. |
| Số 0 · **metricValues/available-zero**, **engagementCases/zero-numerator**, **engagementCases/zero-denominator** | Metric hiện 0/reason null. E = 0, impression 400 → ER 0%; E = 20, impression 0 → ER null/ZERO_DENOMINATOR. Ca complete: 12 + 3 + 5 trên 400 → 5%. | PASS metric/available-zero, engagement/zero-numerator, engagement/zero-denominator, engagement/complete. |
| Thiếu quyền · **metricValues/permission**, **missingReasonCases/permission-revoked-after-verification** | Không có số tốt dùng được, lượt đọc báo PERMISSION_REQUIRED sau khi capability từng AVAILABLE. UI hiện null/PERMISSION_REQUIRED và gợi ý RESOLVE_PERMISSION, phân biệt với empty. | PASS metric/permission và missing-reason/permission-revoked-after-verification. pageStates/permission-error đối chiếu code/action SocialError, dùng projection trang comment. |
| Cache stale + lỗi · **dtoExamples.snapshot**, **metricValues/stale**, **missingReasonCases/network-error**, **views/dashboard-stale-refresh** | Giữ sample 100 từ mock-snapshot-1 thu 08/11 17:00Z. Clock/lượt refresh lỗi 11/11 18:00Z, cách 73 giờ > 48 giờ. UI hiện 100, timestamp cũ, stale, offline và NETWORK_ERROR; lastSuccessAt giữ 08/11, lastAttemptAt là 11/11. | PASS provenance-and-dto-examples, metric/stale, missing-reason/network-error cho thành phần; nạp view dashboard-stale-refresh để kiểm tổ hợp. |
| Gap · **coverageCases/one-snapshot** + **views/growth-gap**, **views/follower-delta-missing-baseline** | Ca coverage có một mốc 07/11 17:20Z → actual bounds null, temporal NONE, delta null/MISSING_BASELINE. Payload IDE dùng mốc duy nhất từ mock-snapshot-1 ở query end, cùng kết quả thiếu baseline. Ô chart chưa thu tại query start có snapshotId/value null, NOT_SYNCED và isGap true; giữ khoảng trống. | PASS coverage/one-snapshot và snapshot/missing-baseline; nạp hai view theo JSON để kiểm chart và kết quả delta. |
| Kỳ partial · **coverageCases/partial-week** | Kỳ fixture là 01/11 17:00Z đến 08/11 17:00Z. Hai mốc trong kỳ 02/11 17:15Z và 07/11 17:20Z → actualStart/End đúng hai mốc, temporal PARTIAL, reason null ở projection chọn mốc. Cohort tính riêng theo bài đủ cặp; ca này chỉ kiểm temporal. UI hiển thị khoảng thực. | PASS coverage/partial-week, week/local-boundaries. |
| Hai mốc tương thích · **snapshotCases/decrease**, **coverageCases/full-boundaries** | Cùng scope, GAUGE/COUNT, definition **followers-v1** trong projection, MO_PHONG, 100 tại 01/11 17:00Z → 95 tại 08/11 17:00Z. Delta −5, growth −5%, reason null; hai mốc đúng biên cho temporal FULL. Definition **followers-v1** thuộc projection này, còn DTO đầy đủ dùng **mock-followers-v1**. | PASS snapshot/decrease, coverage/full-boundaries; đối chiếu thêm snapshot/different-definition và snapshot/different-source cho cặp bị loại. |
| Top/nhóm · **topPosts**, **groupRate** | Top theo thứ tự mock-p1, mock-p2, mock-p5, mock-p6, mock-p3 (−2); loại mock-p4 null, excludedCount 1. Nhóm (20 + 10)/(400 + 100) × 100 = 6%. | PASS top-posts/tie-negative-and-missing, group/weighted-engagement-rate. |

Kiểm các view bổ sung trong PowerShell từ gốc repo. Lệnh đọc payload trực tiếp từ tài liệu và kiểm bằng dữ liệu nguồn, dùng cùng entry point format JSON cho IDE và QA.

~~~powershell
@'
const assert = require('node:assert/strict');
const fs = require('node:fs');
const fixture = require('./docs/contracts/analytics-examples.json');
const md = fs.readFileSync('docs/handoffs/sp-045-analytics-input.md', 'utf8');
const pack = JSON.parse(md.match(/^~~~json\r?\n([\s\S]*?)^~~~\s*$/m)[1]);
const { query, resolvedScope, views } = pack;
const { snapshot, metricDefinition } = fixture.dtoExamples;
const [sample] = snapshot.samples;
const byId = (group, id) => fixture[group].find(item => item.id === id);

assert.equal(pack.handoffVersion, '0.1.0');
assert.equal(pack.source, 'MO_PHONG');
assert.deepEqual(resolvedScope, snapshot.scope);
assert.deepEqual(query.channelIds, [resolvedScope.channelId]);
assert.equal(query.metricDefinitionId, metricDefinition.definitionId);
assert.equal(query.zoneId, fixture.week.zoneId);
assert.deepEqual([query.requestedStart, query.requestedEnd], [fixture.week.start, fixture.week.end]);
assert.ok(Number.isInteger(query.topLimit) && query.topLimit > 0);

const empty = views['dashboard-empty'];
for (const key of ['channelCards', 'postMetrics', 'growthPoints', 'weeklyEngagement', 'topPosts', 'groupedMetrics', 'goals', 'errors']) {
    assert.deepEqual(empty[key], []);
}
assert.deepEqual(query.cohort.cohortPostIds, []);
assert.deepEqual(empty.coverage, { temporal: byId('coverageCases', 'no-history').expected.coverage,
    cohort: 'NONE', includedCount: 0, totalCount: 0, excludedReasons: [] });
assert.deepEqual([empty.syncState, empty.offline, empty.lastSuccessAt, empty.lastAttemptAt], ['IDLE', false, null, null]);
console.log('PASS dashboard-empty');

const stale = views['dashboard-stale-refresh'];
const entry = stale.followerEntry;
const threshold = byId('metricValues', 'stale').thresholdHours;
assert.deepEqual(entry.scope, sample.scope);
assert.equal(entry.definitionId, sample.definitionId);
assert.deepEqual(entry.sourceSnapshotIds, [snapshot.snapshotId]);
assert.deepEqual([entry.source, entry.sourceRef], [sample.source, sample.sourceRef]);
assert.deepEqual(entry.follower, { value: sample.value, missingReason: sample.missingReason });
assert.equal(entry.followerCollectedAt, sample.collectedAt);
assert.equal(stale.lastSuccessAt, sample.collectedAt);
assert.equal(stale.lastAttemptAt, stale.now);
assert.equal((Date.parse(stale.now) - Date.parse(sample.collectedAt)) / 3600000, 73);
assert.deepEqual(entry.freshness, {
    isStale: Date.parse(stale.now) - Date.parse(sample.collectedAt) > threshold * 3600000,
    lastSuccessAt: sample.collectedAt
});
assert.deepEqual([stale.syncState, stale.offline], ['ERROR', true]);
assert.deepEqual(stale.errors, [{
    error: { code: byId('missingReasonCases', 'network-error').socialErrorCode, nextAction: 'RETRY_WHEN_SAFE' },
    retryAfterAt: null
}]);
console.log('PASS dashboard-stale-refresh');

const gap = views['growth-gap'];
const notSynced = byId('metricValues', 'not-synced');
assert.deepEqual(gap, { scope: resolvedScope, definitionId: query.metricDefinitionId, snapshotId: null,
    observedAt: query.requestedStart, value: notSynced.value, missingReason: notSynced.missingReason, isGap: true });
assert.notEqual(gap.observedAt, sample.observedAt);
console.log('PASS growth-gap');

const delta = views['follower-delta-missing-baseline'];
const onePoint = byId('coverageCases', 'one-snapshot').expected;
const missingBaseline = byId('snapshotCases', 'missing-baseline').expected;
assert.equal(sample.observedAt, query.requestedEnd);
assert.deepEqual(delta, {
    value: missingBaseline.delta, reason: missingBaseline.reason, missingDetails: [],
    definitionId: query.metricDefinitionId, requestedStart: query.requestedStart, requestedEnd: query.requestedEnd,
    actualStart: onePoint.actualStart, actualEnd: onePoint.actualEnd, coverage: empty.coverage,
    sourceSnapshotIds: [snapshot.snapshotId], freshness: entry.freshness
});
console.log('PASS follower-delta-missing-baseline');
'@ | node
~~~

Kết quả tại base nêu trên với Node v24.19.0: 75/75 ca SP-020 và 4/4 view/DTO bổ sung đạt. Đã parse hai payload JSON, đối chiếu ID, expected values và đích liên kết từ working tree hoặc Git object ở ref đã pin. Kết quả áp dụng cho fixture MO_PHONG.

## 4. Thiện xác nhận tại HO-DTO / SP-046.01

1. **Chữ ký/lifecycle.** Chọn LiveData, callback hay dạng observable hiện có cho observeCachedDashboard(query)? refresh(query, callback) trả Cancelable theo contract nào, callback chạy ở thread nào, hủy theo query cũ/đổi revision ra sao? Observer gắn lại sau xoay màn hình đọc cache theo cách nào để tránh gọi refresh lặp?
2. **Query và kỳ.** Consumer gửi channelIds để repository resolve revision, hay cần gửi ChannelScope tường minh? Chốt list cohortPostIds hoặc fingerprint + danh sách, granularity kỳ, zone mặc định Asia/Ho_Chi_Minh, grouping/topLimit và thứ tự tie-break. Khi đổi zone hoặc metric, UI lưu và tái lập query nào?
3. **Nullable và trạng thái.** Chốt Java types, presence của từng list/DTO, cách UI render null theo missingReason/reason, số 0 và EMPTY thành công. GrowthPoint gap có cần timestamp lưới, freshness/provenance inline hay tra theo snapshotId?
4. **Freshness/lỗi.** Xác nhận ngưỡng 48 giờ cho fixture và cách hiển thị lastSuccessAt, lastAttemptAt, lỗi refresh và offline độc lập trên cache. Chốt error theo scope/operation và các hành động retry/reconnect/resolve permission.
5. **Coverage và truy xuất nguồn.** Chốt cấu trúc excludedReasons, requested/actual bounds, hai trục coverage, cách đánh dấu subtotal và phạm vi so sánh khác platform. UI cần sourceSnapshotIds + source/sourceRef trực tiếp hay resolver? Với goal tạm tính hoặc stale, Thiện xác nhận render rawProgress cùng completionStatus UNKNOWN.

Nghị có thể chạy fixture và chuẩn bị logic SP-046.02/.03; Thiện dùng gói cho khung SP-047. Khi Thiện xác nhận, ghi ACK kèm version/SHA và quyết định vào HO-DTO/SP-046.01. Giao số và snapshot thật qua HO-DASH sau bước tích hợp.
