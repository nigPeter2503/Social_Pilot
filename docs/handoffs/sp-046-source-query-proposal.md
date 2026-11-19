# SP-046.02 — Source port và query đề xuất

**Local draft 0.1.0, TV4 / Tô Lãng Nghị. Chưa ACK, chưa phải Room schema/DAO.**

Triển khai độc lập dùng `domain/analytics/AnalyticsSource.java`, `AnalyticsCalculator.java`
và `data/repository/analytics/{AnalyticsProjector,DefaultAnalyticsRepository}.java`.
Không nối vào AppContainer. Source MO_PHONG duy nhất nằm trong `src/test`.
DTO/AnalyticsRepository giữ nguyên byte so với SP-046.01 draft 0.1.0.

## API cần nối khi mở gate

- `readCached(query) -> Batch`: một snapshot đọc nhất quán của registry, Channel,
  metadata PublishedPost theo **local ID**, normalized samples và attempt state.
  Query thành công không có hàng khác với lỗi đọc; lỗi trả `SourceFailure` có
  `DashboardError` đã chuẩn hóa. `available=false` chưa có projection dùng được.
- `refreshProjection(query) -> Batch`: chạy trên IO executor, bàn giao cho coordinator
  SP-045.04 tương lai; không có HTTP, lịch thu hoặc Worker trong SP-046.02.
  Coordinator chịu trách nhiệm lưu attempt/success trước trả. Partial có dữ liệu và errors.
- `watch(invalidated) -> Cancelable`: đăng ký nhanh, không IO/refresh; báo sau commit
  thay đổi projection hoặc Channel. Repository reload đúng query đang observe.
- `withCurrentScopes(scopes, action) -> boolean`: kiểm Channel hiện CONNECTED, đúng
  platform/channel/revision và ownership của projection, serialize action với
  invalidation/reconnect. Không dùng preflight tách khỏi publication. Adapter thật
  cần cùng coordination boundary với invalidation của Minh; không giữ Room write
  transaction qua callback UI/network. Callback này chỉ chạy nền, consumer phải nhanh.
- `production(source, staleAfterMillis, io, callbackWorker, clock)`: namespace chỉ
  API_THAT. Threshold dương bắt buộc, hai executor nền do composition root sở hữu;
  không truyền executor chạy inline/UI. Callback được serialize. `close()` gỡ watch,
  hủy việc riêng, xóa cache trong bộ nhớ; executor vẫn do caller quản lý.

Batch bất biến; Definition ID pin nghĩa/provider filter/unit/subject/kind/aggregation.
Derived ENGAGEMENT pin đúng ba component definition ID (reaction/comment/share),
ENGAGEMENT_RATE pin thêm impression ở vị trí thứ tư. Không dò tên gần giống, không
đổi like/views thành reaction/impression. Kind chưa được xác minh dùng UNVERIFIED.
`alignmentId` phải tham chiếu bằng chứng cùng measurement window đã review; cùng
collectionId không đủ. Provenance chỉ nhận nguồn đã che, không token/reference bí mật,
body thô hoặc URL credential. Port không có trường credential.

## DAO/query projection đề xuất cho SP-045.03

Các tên bảng dưới đây là placeholder thiết kế, **không chạy SQL**, không chọn version.
Đạt xác nhận tên thật và slot trước khi chuyển thành `@Query`/migration. Truyền scope
tuple với bind arguments; danh sách scope/cohort dùng bind table/CTE phù hợp giới hạn
SQLite, không ghép chuỗi ID. UUID/local IDs/URN giữ nguyên case và toàn chuỗi.

```sql
-- Snapshot selection includes the measured end boundary.
SELECT sample.*, snapshot.*, definition.*
FROM metric_sample AS sample
JOIN snapshot ON snapshot.snapshot_id = sample.snapshot_id
JOIN channel ON channel.channel_id = snapshot.channel_id
JOIN metric_definition AS definition ON definition.definition_id = sample.definition_id
WHERE channel.platform = :platform AND channel.channel_id = :channelId
  AND channel.connection_revision = :connectionRevision
  AND channel.connection_status = 'CONNECTED'
  AND snapshot.platform = channel.platform
  AND snapshot.connection_revision = channel.connection_revision
  AND snapshot.source = 'API_THAT' AND sample.source = snapshot.source
  AND snapshot.subject_type = :subjectType AND snapshot.subject_id = :localSubjectId
  AND sample.definition_id IN (:pinnedDefinitionIds)
  AND sample.observed_at >= :start AND sample.observed_at <= :end
ORDER BY sample.observed_at, sample.collected_at, snapshot.snapshot_id;

-- Latest follower uses all earlier cache, still subject/definition/scoped and as-of end.
-- Same joins/guards as above; subject CHANNEL must equal channel_id.
-- WHERE observed_at <= :end; descending time/receipt/id, keep latest good before failed attempt.

-- Post metadata ownership, independent of provider/native ID or PublishJob:
SELECT post.* FROM published_post AS post JOIN channel ON channel.channel_id = post.channel_id
WHERE post.id IN (:fixedLocalPostIds) AND post.channel_id = :channelId
  AND channel.platform = :platform AND channel.connection_revision = :connectionRevision
  AND channel.connection_status = 'CONNECTED';

-- Separate event filter (never reuse this strict end predicate for snapshot delta):
-- post.published_at >= :start AND post.published_at < :end
```

Đề xuất index, cần EXPLAIN QUERY PLAN trên schema và dữ liệu thật:

| Quan hệ | Index/constraint cần review |
|---|---|
| Channel | PK local channel ID; identity tuple theo HO-CHANNEL; lookup revision/status |
| Snapshot | `(platform, channel_id, connection_revision, source, subject_type, subject_id, snapshot_id)`; business unique theo contract collection |
| MetricSample | `(snapshot_id, definition_id, observed_at, collected_at)` hoặc index bắt đầu definition/observed nếu plan cần; unique thêm provider period key khi một collection có nhiều kỳ |
| PublishedPost | local PK + FK Channel; unique `(channel_id, external_post_id)`; metadata/event index `(channel_id, published_at, id)` |
| Attempt | exact scope/operation/query/namespace key, lastSuccessAt tách lastAttemptAt/error |

Không dùng INNER JOIN thiếu hàng để che orphan/ownership corruption: source adapter phải
phân biệt integrity failure với query rỗng. Snapshot POST phải FK đúng PublishedPost
cùng Channel; CHANNEL phải trỏ đúng Channel. FK, revision check, sample+attempt writes
và rollback cần transaction thật của SP-045.03/.04, không được suy ra từ in-memory source.
Đối chiếu schema fresh/migrate và EXPLAIN còn **NOT_RUN**.

## Quy tắc projection đang chạy

1. Lọc exact namespace, scope, subject, local cohort và registry definition. Mẫu khác
   registry/namespace làm lỗi đọc, không empty success. Mẫu khác scope/revision/subject
   bị loại. Card lấy follower tốt mới nhất <= end; lỗi lần thử thuộc attempt riêng.
2. Trong [start,end], chọn đầu/cuối thực của **mỗi component**. Hai mốc phải tăng,
   cùng definition/unit/kind/source/API version/observedAtBasis/provider period.
   Component E/ER cần alignment evidence, collection, timestamp/basis/kind và provider
   period khớp. Không đặt tolerance. PERIOD/UNVERIFIED chưa có policy query interval
   thì trả null/INCOMPATIBLE_PERIOD; engine không trừ PERIOD khác kỳ.
3. Chỉ cộng/xếp các bài có cùng cặp actual bounds và vector provenance semantics
   của các component. Nếu các bài có số nhưng khác cặp, loại **tất cả** bài xung đột,
   không tùy ý chọn một bài làm mốc. Các bài thiếu baseline bị loại riêng; subtotal
   còn lại có PARTIAL_COVERAGE. Mốc 00:15 giữ 00:15. Không bịa baseline 0 cho bài mới.
4. Top và group tách từng scope; rank bắt đầu 1 trong từng scope. Value giảm, publishedAt
   giảm/null cuối, rồi platform/channel/post tăng. Long so sánh chính xác quá 2^53.
   Primary topic duy nhất, format hoặc publish hour theo IANA zone; thiếu vào UNKNOWN.
   Không multi-label. Group ER = sum(delta E)/sum(delta impression), không trung bình ER.
5. Temporal coverage xét cặp đo, cohort coverage xét included/total/exclusions riêng.
   Local ID không resolve được có exclusion INCOMPATIBLE_SCOPE; weekly subtotal ghi
   cả phần cohort chưa resolve. Group không đoán metadata cho local ID chưa resolve.
   Chart grid một ngày lịch từ requestedStart theo zone: ngày không mẫu có gap/null/
   NOT_SYNCED, provenance rỗng; ngày có mẫu giữ timestamp thật. Mẫu đúng end giữ riêng.
6. Count dùng Long/addExact/subtractExact, ratio BigDecimal DECIMAL128 rồi Double hữu hạn.
   Không làm tròn UI. Overflow trả null, engine diagnostic COUNT_OVERFLOW. Do draft DTO
   chưa có OVERFLOW, bridge hiện trả INVALID_TARGET. Đề xuất Thiện ACK bổ sung reason
   riêng; chưa thay enum chung. Chưa đồng nghĩa hoàn tất policy lỗi production.
7. Freshness threshold truyền bắt buộc. 48h chỉ ở fixture. Card theo sample tốt; phép
   tính nhiều mốc theo timestamp thu cũ nhất của input thực được dùng, tránh che mẫu cũ.
   SourceSnapshotIds lấy từ input, subtotal chỉ dùng provenance của các bài được cộng.
8. Cache key gồm toàn query và namespace cố định của repository. Observe không refresh;
   LOADING/terminal tuần tự nền, initial UNAVAILABLE khác EMPTY. Source error giữ last
   good, lastSuccessAt/provenance và cập nhật lastAttemptAt/offline; freshness tính lại.
   Reattach không xóa lỗi attempt mới bằng trạng thái source cũ hơn. Cancel idempotent,
   chặn delivery đã queued; cancel khi loading khôi phục cache đọc được. Attempt mới
   cùng query thắng attempt cũ. Consumer đổi query phải cancel hai handle và check UI
   generation theo draft. Revision guard kiểm ở publication/callback dưới source lock.

## Artifact còn cần người xác nhận

- **Thiện:** ACK SP-046.01 draft 0.1.0 đúng blob; review overflow reason, derived metric
  interval/definition semantics, chart daily grid, rank per scope và stale threshold.
- **Minh:** Channel identity/scoped lookup, revision/invalidation serialization và ownership.
- **Nghị/SP-045.03 + Đạt:** DAO/schema thật, period keys/FK, slot/base/version/order,
  consistent read/watch/attempt persistence và query-plan/migration/device tests.

Các API/SQL trên là gói review local, không phải ACK hay nghiệm thu tích hợp/live.
