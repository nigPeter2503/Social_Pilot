# Hợp đồng dữ liệu comment và analytics — SP-020

Revision: **draft-4**, 11/11/2026, Asia/Saigon. Owner: Tô Lãng Nghị (`bob35306`).
Trạng thái: **bản đề xuất để review**, chưa phải hợp đồng đã được nhóm phê duyệt.
Ticket: [SP-020 / #22](https://github.com/nigPeter2503/Social_Pilot/issues/22).
Reviewer: Minh và Thiện; Đạt xác nhận thiết kế lưu trữ trước khi sửa schema.

## 1. Căn cứ, ranh giới và phụ thuộc

- Base `origin/main`: `4f69a3936ff5e3d988d907f7f2f46083dcd967b5`.
- [Proposal F6/F7](SocialPilot_Proposal_Java.md), [requirements](requirements.md),
  [kiến trúc](architecture.md), [brief TV4](TaskBrief/W1/tv4-nghi-taskbrief.md).
- SP-009 dùng [hợp đồng Social v1](social-contract.md) đã merge qua
  [PR #66](https://github.com/nigPeter2503/Social_Pilot/pull/66). Đối chiếu capability,
  lỗi và ngắt kênh theo mục 3, 4 và 7 của hợp đồng đó.
- SP-012 đã có [đề xuất Room tại 8124f3a](https://github.com/nigPeter2503/Social_Pilot/blob/8124f3af9770b7e87fc81ed9539159aa603db0d4/docs/room-schema-contract.md).
  [Issue #14](https://github.com/nigPeter2503/Social_Pilot/issues/14) còn mở. Các khóa,
  index và thứ tự tích hợp analytics vẫn cần Đạt xác nhận.
- Không sửa `SocialConnector`, `AppDatabase`, backend, UI hoặc build trong bản nháp này.
  Các DTO và interface dưới đây là schema tài liệu, chưa phải Java API production.
- Fixture [analytics-examples.json](contracts/analytics-examples.json) hoàn toàn **MO_PHONG**.
  Không có response API thật, không chứng minh quyền hoặc nghiệm thu F6/F7.
  Kết quả kiểm tra nằm ở [hồ sơ SP-020](sp-020-verification.md).

## 2. Mapping yêu cầu → dữ liệu → nguồn → người dùng

Nguồn dưới đây là nhu cầu logic; endpoint, metric provider, version, scope và khả năng
thực tế **chưa kiểm chứng**. SP-018/#20 và SP-019/#21 phải bổ sung bằng chứng trước adapter.

| Yêu cầu | Dữ liệu cần | Nguồn dự kiến / giới hạn | Người dùng |
|---|---|---|---|
| F6.B1/B2 | Channel, post, comment, parent, cursor, sync time | Comment API của từng nền tảng; quyền đọc kiểm tra riêng | Nghị repository, UI inbox |
| F6.B4 | Comment key, reply capability, request/outcome, reply ID | Reply API trên đối tượng thử được phép; không suy từ quyền đọc | Nghị, Minh capability |
| F7.B1 | Reaction hoặc like, comment, share, impression; ER | Post metrics API; từng metric có định nghĩa và lý do thiếu | Nghị tính, Thiện hiển thị |
| F7.B2 | Hai snapshot thực, follower kênh, thời điểm và khoảng trống | Follower API + lưu cục bộ từ W2–3; connections không thay follower | Nghị repository, Thiện biểu đồ |
| F7.B3 | Follower mới nhất, delta tuần, top bài, coverage | Snapshot cùng định nghĩa và khoảng đo; kênh không tương thích hiển thị riêng | Thiện dashboard |
| F7.N1 | Topic ID, format, publishedAt, timezone, metric và coverage nhóm | Metadata bài của ứng dụng; metadata provider chỉ dùng khi đã xác minh | Thiện nhóm, Nghị truy vấn |
| F7.N3 | Goal, baseline, target, actual, progress và missing reason | Lưu mục tiêu cục bộ + snapshot đủ điều kiện | Nghị lưu/tính, Thiện mục tiêu |

## 3. Kiểu dùng chung và bất biến

Timestamp trao đổi dùng UTC ISO 8601 dạng `yyyy-MM-dd'T'HH:mm:ss'Z'` theo SP-009.
Đề xuất lưu UTC epoch millisecond trong Room theo SP-012. ID là chuỗi opaque không rỗng, không đổi chữ hoa/thường,
không cắt/gộp provider ID/URN. Không dùng tên hiển thị hoặc parse số để làm khóa.

`ChannelScope = {platform, channelId, connectionRevision}`: platform là FACEBOOK/LINKEDIN,
channelId là ID nội bộ, revision dương và bền vững theo SP-009. Không sao chép tokenReference
hay token vào comment/metric/fixture. Account type lấy từ ChannelInfo, không đoán từ ID.

Đối chiếu từng `SocialOperation` theo scope hiện hành: `READ_COMMENTS`,
`REPLY_COMMENT`, `READ_POST_METRICS` và `READ_FOLLOWER_COUNT`. `ChannelCapability`
gồm operation, availability, reasonCode, verifiedAt. `AVAILABLE` cần verifiedAt.
`PERMISSION_REQUIRED` và `UNSUPPORTED` cần cả reasonCode lẫn verifiedAt.
`UNVERIFIED` cần reasonCode và có thể chưa có verifiedAt. Khi có verifiedAt, dùng
định dạng UTC trên và ngày lịch hợp lệ. Từng lần gọi vẫn xử lý quyền bị thu hồi.
OAuth hoặc quyền đăng không xác nhận các operation này. `SocialConnector` cung cấp
tra cứu capability. Adapter và repository phụ trách thao tác comment/metrics.

`EvidenceSource = API_THAT | FIXTURE_TU_API_DA_CHE | MO_PHONG | PROTOTYPE`.
Chỉ `API_THAT` được dùng làm lịch sử production. Fixture đã che chỉ phục vụ test,
không nhập ngược vào kho lịch sử. Provenance theo từng sample, không chỉ nhãn toàn màn hình.

`MetricValue = {value: number|null, missingReason: MissingReason|null}`:
value hữu hạn, 0 là số đo hợp lệ; value có mặt thì missingReason null;
value null thì missingReason bắt buộc. Số đếm không âm; delta được phép âm.
Stale là thuộc tính freshness riêng, không ghi đè số đo đã có bằng null.
Mỗi response lỗi phải được adapter chuẩn hóa, không trả body thô hoặc URL có token.

### Ánh xạ dữ liệu thiếu và lỗi Social v1

`MissingReason` giải thích vì sao một giá trị analytics là null.
`ChannelCapability.availability` mô tả khả năng của từng thao tác trên kênh và
revision hiện hành. `SocialError` mô tả lỗi của lần gọi hoặc yêu cầu bị chặn.

| `MissingReason` | Nguồn và quy tắc ánh xạ |
|---|---|
| `PERMISSION_REQUIRED` | Capability `PERMISSION_REQUIRED`, hoặc lỗi `PERMISSION_REQUIRED / RESOLVE_PERMISSION` khi adapter xác định thiếu quyền đọc giá trị đó. |
| `UNSUPPORTED` | Capability `UNSUPPORTED` hoặc lỗi `UNSUPPORTED_OPERATION / NONE` cho thao tác đọc tương ứng. Nếu chỉ một metric đã được xác nhận không hỗ trợ, đặt `UNSUPPORTED` cho metric đó và giữ capability cấp thao tác, ví dụ `READ_POST_METRICS = AVAILABLE` khi thao tác vẫn đọc được metric khác. |
| `UNVERIFIED` | Capability hoặc metric cụ thể chưa được kiểm chứng. Trạng thái này tự nó không sinh `SocialError`. |
| `NOT_SYNCED` | Giá trị chưa được thu và chưa có kết quả cụ thể hơn. Đây là trạng thái cục bộ, không có mã SocialError tương ứng. |
| `PROVIDER_OMITTED` | Response thành công nhưng thiếu trường metric được yêu cầu. Giữ capability hiện có. Trường vắng mặt hoặc HTTP 403 chưa phân loại không đủ căn cứ để gán `UNSUPPORTED`. |
| `AUTH_REQUIRED` | Lỗi `AUTH_REQUIRED / RECONNECT` trên kết nối cần xác thực lại. |
| `NETWORK_ERROR` | Lỗi `NETWORK_ERROR` của lần đọc. Chỉ dùng `RETRY_WHEN_SAFE` cho lần đọc có thể thử lại an toàn. |
| `RATE_LIMITED` | Lỗi `RATE_LIMITED / WAIT`. Lưu `retryAfterAt` cạnh lỗi khi provider có thông tin. |

Khi thao tác đọc bị từ chối vì đã xác định không hỗ trợ, dùng
`SocialError.Code.UNSUPPORTED_OPERATION` với `NextAction.NONE` cho lần gọi.
Dùng `MissingReason.UNSUPPORTED` cho giá trị null và
`CapabilityAvailability.UNSUPPORTED` khi bằng chứng áp dụng cho cả thao tác.
Chỉ cập nhật capability khi có bằng chứng cho đúng thao tác, kênh và revision.

Khi lần đọc thất bại và còn mẫu hợp lệ đã lưu, giữ số đo và timestamp của mẫu.
Tính freshness từ timestamp đó và ghi lỗi ở trạng thái lần thử gần nhất. Nếu chưa có giá trị dùng được,
áp dụng bảng trên cho các metric bị ảnh hưởng. Với
`CHANNEL_DISCONNECTED / RECONNECT`, dùng trạng thái ngắt kênh và quy tắc dọn dữ liệu
ở mục 4. Trường hợp này không có `MissingReason` tương ứng.

## 4. Comment, phân trang và trạng thái đồng bộ

### CommentRecord (đề xuất DTO)

| Trường | Bắt buộc / nullable và quy tắc |
|---|---|
| scope | Bắt buộc; từ kết nối đang hoạt động |
| postId, commentId | Bắt buộc; native ID/URN của provider |
| parentCommentId | Nullable; kèm parentState = ROOT / KNOWN / UNKNOWN; KNOWN phải có ID, ROOT/UNKNOWN phải null |
| message | Nullable; contentState = AVAILABLE / OMITTED / DELETED; AVAILABLE phải là chuỗi (cho phép rỗng), hai trạng thái còn lại phải null |
| authorDisplayName | Nullable; chỉ tên cần hiển thị; không bắt buộc email/profile/avatar |
| createdAt, providerUpdatedAt | Nullable khi provider không trả; timeMissingReason bắt buộc khi createdAt thiếu, null khi có createdAt |
| syncedAt | Bắt buộc; thời điểm nhận thực tế, không thay createdAt |
| source, sourceRef | Bắt buộc; sourceRef là nhãn evidence đã che, không URL phân trang/token |
| replyCapability | Capability REPLY_COMMENT của scope hiện hành; UNVERIFIED nếu chưa thử, kèm reasonCode |

Khóa logic comment: `(platform, channelId, commentId)`. postId là quan hệ cần kiểm tra;
cùng khóa nhưng đổi postId là xung đột cần đối soát, không âm thầm chuyển comment sang bài khác.
Tất cả dữ liệu gắn kênh; ngắt kênh xóa dữ liệu cục bộ riêng kênh và chặn kết quả revision cũ.
Vô hiệu revision và chặn công việc mới trước khi hủy HTTP và dọn dữ liệu. Kiểm tra scope,
revision và ghi trong cùng transaction. Kết nối lại cùng channelId bắt đầu revision mới
với cache, checkpoint và lịch sử analytics riêng kênh đã được dọn khi ngắt.

### Request, page và checkpoint

`CommentPageRequest = {scope, postId, cursor:null|string, pageSize:int, historyFrom}`.
pageSize > 0 và <= giới hạn adapter đã xác minh; không chốt một giới hạn chung giả.
historyFrom là mốc lịch sử được cấu hình và hiển thị, không giả định đã tải toàn bộ lịch sử.

`CommentPage = {scope, postId, items, nextCursor:null|string, hasMore:boolean|null,
fetchedAt, source}`. hasMore=true yêu cầu nextCursor; false yêu cầu nextCursor=null;
null nghĩa provider chưa cho biết, không đánh đồng với trang cuối. Cursor là opaque,
adapter giữ ràng buộc post/filter/scope/revision và loại URL chứa token. Không log cursor.

`SyncCheckpoint = {scope, postId, queryFingerprint, resumeCursor, lastPageAt,
lastCompleteSyncAt, historyFrom}`. Resume chỉ cùng queryFingerprint và revision.
Lưu upsert trang + checkpoint nguyên tử sau trang thành công; lỗi trang sau không xóa trang trước.
lastCompleteSyncAt chỉ đổi sau khi xác nhận kết thúc chuỗi trang; cursor hết hạn thì
khởi động lại phạm vi đã cấu hình và dedup, không nhận là đã sync đầy đủ.

Các request cùng post chạy tuần tự. Trang chồng lặp upsert theo khóa comment:
ưu tiên providerUpdatedAt mới hơn khi cả hai có timestamp; nếu không so sánh được,
dùng lần nhận mới hơn trong cùng luồng, không cho response cũ ghi đè. Hai payload xung đột
cùng providerUpdatedAt giữ bản đã lưu và đánh dấu cần refresh. Không thấy comment trong
một trang không chứng minh comment đã xóa; chỉ dùng tombstone khi provider xác nhận.

Trang rỗng thành công khác với lỗi thiếu quyền. `Result = SUCCESS(page) | FAILURE(error)`;
lỗi không được trả success với items=[]. Dùng cặp `SocialError.code` và `nextAction`
theo SP-009 khi áp dụng: `PERMISSION_REQUIRED / RESOLVE_PERMISSION`,
`AUTH_REQUIRED / RECONNECT`, `CHANNEL_DISCONNECTED / RECONNECT`,
`UNSUPPORTED_OPERATION / NONE`, `RATE_LIMITED / WAIT`. Analytics bổ sung
`retryAfterAt` nullable bên cạnh `SocialError` khi provider có thông tin.
`nextAction` là gợi ý cho caller.

Đề xuất `CommentRepository.loadCached(query)` và `syncPage(request, callback) -> Cancelable`;
cache đi kèm sync/freshness/error state. Không chặn main thread; callback nền;
hủy HTTP và bỏ callback muộn theo SP-009. Reply chưa rõ kết quả giữ hồ sơ đối soát,
không tự retry. Chữ ký Java cụ thể chờ Minh/Thiện review.

## 5. Snapshot và định nghĩa metric

`MetricDefinition = {definitionId, metricKey, unit, entityType, measurementKind,
providerMetricName, aggregation, componentDefinitionIds}`.

- metricKey đề xuất: FOLLOWER_COUNT, REACTION_COUNT, COMMENT_COUNT, SHARE_COUNT,
  IMPRESSION_COUNT, ENGAGEMENT_COUNT, ENGAGEMENT_RATE. unit: COUNT / PERCENT.
- Dùng reaction **hoặc** like theo định nghĩa nguồn, không cộng cả hai nếu trùng.
- measurementKind = GAUGE (follower tại một thời điểm), CUMULATIVE (bộ đếm bài),
  PERIOD (provider trả số trong kỳ); không lấy hiệu hai PERIOD để giả làm tương tác tuần.
- definitionId thay khi phạm vi/định nghĩa thay; lưu definition registry cùng dữ liệu.
  providerMetricName có thể chưa chốt trong draft; sample API_THAT bắt buộc đã có mapping.
  API version khác chỉ so sánh nếu đã xác nhận cùng định nghĩa và phạm vi.

`MetricSample = {scope, entityType:CHANNEL|POST, entityId, definitionId,
value, missingReason, observedAt, observedAtBasis, collectedAt, providerPeriodStart, providerPeriodEnd,
providerApiVersion, source, sourceRef, collectionId}`.

observedAt là thời điểm đo provider hoặc thời điểm nhận nếu provider không cho biết;
kèm observedAtBasis = PROVIDER / COLLECTION. collectedAt là thời điểm thu thực tế.
PERIOD cần cả periodStart/end, khoảng nửa mở [start,end); CUMULATIVE/GAUGE không gán
thành kỳ tuần chỉ vì thu trong tuần đó. source/version/definition là provenance bắt buộc
với API_THAT. Attempt thất bại có thể lưu value=null/lý do, không ghi đè sample tốt trước đó.

`Snapshot = {snapshotId, collectionId, scope, entityType, entityId, samples,
collectionStartedAt, collectedAt, source}`. collectedAt của snapshot là lúc hoàn tất đợt thu;
collectedAt của từng sample giữ lúc nhận thật, trong [collectionStartedAt, snapshot.collectedAt].
Không gán cùng timestamp cho các response nhận khác nhau. Thành phần dùng chung collectionId và phạm vi thu;
không trộn reaction hôm nay với impression hôm qua. Không tuyên bố thu đa endpoint là
nguyên tử; nếu provider không cho phép bộ thành phần tương thích, ER không tính được.
Chung collectionId chưa đủ chứng minh cùng khoảng đo. Dữ liệu cùng một response/provider
measurement window có thể tương thích; khác endpoint/time window phải có quy tắc alignment
đã review và bằng chứng provider. Draft chưa tự đặt dung sai thời gian để ghép số.

Freshness ở thời điểm truy vấn: `isStale = now - collectedAt > freshnessThreshold`;
đề xuất threshold 48 giờ với lịch thu hằng ngày, cần kiểm lại theo lịch thu/API thật. Trả collectedAt,
lastAttemptAt/error riêng; offline vẫn có thể trả số cache với stale/offline indicator.
Capability AVAILABLE ở cấp READ_POST_METRICS không chứng minh mọi metric con đều có.

## 6. Công thức, khoảng đo và dữ liệu thiếu

`CalculatedValue = {value:null|number, reason:null|string, missingDetails, definitionId,
requestedStart, requestedEnd, actualStart, actualEnd, coverage, sourceSnapshotIds, freshness}`.
reason gồm MISSING_COMPONENT, ZERO_DENOMINATOR, MISSING_BASELINE, MISSING_CURRENT,
INCOMPATIBLE_SCOPE, INCOMPATIBLE_DEFINITION, INCOMPATIBLE_PERIOD, INCOMPATIBLE_SOURCE,
INVALID_TIME_ORDER, INVALID_TARGET, PARTIAL_COVERAGE khi phù hợp.
Giữ missing reason gốc của các thành phần trong kết quả chi tiết.
missingDetails là danh sách {componentDefinitionId, missingReason}; reason null khi có
kết quả đầy đủ, còn subtotal partial có thể có value và reason=PARTIAL_COVERAGE.
Tính trước khi làm tròn; UI chỉ format 2 số thập phân, không tính lại nghiệp vụ.

Trước delta/growth: cần hai giá trị không null, cùng platform/channel/entity/definition/unit,
measurementKind và source có thể so sánh; source API_THAT không ghép với MO_PHONG.
Thiếu current → null/MISSING_CURRENT, không để null bị chuyển thành số 0.
Hai mốc phải có effective observation time tăng nghiêm ngặt; bằng/đảo thời gian →
INVALID_TIME_ORDER. GAUGE so sánh mức tại hai mốc; CUMULATIVE phải cùng nguồn bộ đếm;
PERIOD khác kỳ không dùng phép trừ như hai mốc CUMULATIVE. Chọn hai mốc trong lịch sử
thuộc scope hiện hành. Ngắt kênh xóa lịch sử analytics riêng kênh theo mục 4.

| Phép tính | Điều kiện và ví dụ mong đợi |
|---|---|
| Engagement | E = reaction + comment + share; đủ ba thành phần tương thích |
| ER theo impression | E / impression × 100; 12+3+5 trên 400 = 5%; E=0 trên 400 = 0%; impression=0 → null/ZERO_DENOMINATOR; share thiếu → null/MISSING_COMPONENT |
| Delta | current - baseline; 100 → 95 = -5, không ép 0; cần hai snapshot thực khác thời điểm, cùng scope/entity/definition |
| Growth | delta / baseline × 100; 100 → 95 = -5%; baseline=0 → null/ZERO_DENOMINATOR; không có baseline → null/MISSING_BASELINE |
| Goal follower | (current-baseline)/(target-baseline) × 100; 100 → target 120, actual 110 = 50%; actual 95 = -25%; actual 125 = 125% |
| Goal tương tác | actual là tổng delta hợp lệ trong kỳ trên tập bài cố định; baseline của mức tăng là 0, target > 0; actual 30, target 60 = 50% |

Mục tiêu tăng trưởng đòi target > baseline; target=baseline hoặc target<baseline là INVALID_TARGET.
Không tự đổi thành mục tiêu giảm. Giữ rawProgress âm/>100; UI có thể giới hạn riêng độ dài thanh,
nhưng phần trăm và actual vẫn là giá trị thực. Thiếu dữ liệu không trả 0% hoặc nhận đạt mục tiêu.

### Tuần, chọn snapshot và coverage

Theo proposal: tuần thứ Hai đến Chủ nhật, múi giờ được cấu hình. Đề xuất mặc định
`Asia/Ho_Chi_Minh`, không ghi múi giờ máy vào dữ liệu. Lưu UTC, lưu zoneId trong query/goal;
thay timezone tạo query/kỳ mới, không sửa timestamp gốc.

Ví dụ tuần W1: [02/11/2026 00:00, 09/11/2026 00:00) UTC+07,
tương ứng `[2026-11-01T17:00:00Z, 2026-11-08T17:00:00Z)`.
Đúng 09/11 00:00 thuộc tuần kế. Kết quả phải giữ requestedStart/end và actualStart/end.

Đề xuất chọn hai thời điểm thu chung thực trong kỳ đóng tại hai ranh giới [start,end]:
snapshot đúng end được dùng làm mốc kết thúc (không coi là sự kiện nằm trong tuần).
Nếu không có mốc đúng ranh giới, chọn lần thu chung sớm nhất và muộn nhất bên trong kỳ;
actualStart/end là thời gian thật, coverage=PARTIAL. Không lấy snapshot sau end hoặc
nội suy mốc. Nếu chỉ có một mốc, delta=null/MISSING_BASELINE; không bịa điểm 0.
Việc chọn mốc dựa trên observedAt của số đo (và observedAtBasis), không dựa trên giờ
download lại dữ liệu provider cũ. Mốc chung phải tương thích theo measurement window,
không chỉ cùng ID đợt thu. Chọn mốc đầu/cuối hợp lệ độc lập: có start nhưng không có
end vẫn giữ start thật và mốc cuối bên trong kỳ, không bỏ start chỉ vì thiếu end.

`coverage = {temporal:FULL|PARTIAL|NONE, cohort:FULL|PARTIAL|NONE,
includedCount, totalCount, excludedReasons}`. Hai trục khác nhau: đủ mọi bài không
đồng nghĩa đủ cả tuần. Các ca coverage trong fixture dùng projection `coverage` chuỗi
chỉ cho trục temporal. Collection đúng một mốc không đủ delta; không có bài phù hợp
là trạng thái EMPTY, không coi là tổng tương tác 0 đã đo.

Tập bài phải được nêu rõ trong query (`cohortPostIds` hoặc fingerprint + danh sách đã lưu).
So sánh toàn kênh chỉ khi các bài đều có cặp snapshot tương thích tại hai mốc chung.
Bài mới giữa kỳ không có baseline được giữ trạng thái thiếu, không mặc định baseline=0.
Nếu chỉ tính được một phần tập bài, trả subtotal có nhãn PARTIAL_COVERAGE, số bài included/total
và excluded reasons; không gọi đó là tổng kênh hoặc dùng để tuyên bố goal đạt.
Chưa có hai lần thu chung thì không cộng các delta có actualStart/end khác nhau.
Snapshot thiếu giữ khoảng trống biểu đồ; chỉ vẽ điểm thực, không điền lịch sử giả.

## 7. Goal và đầu ra dashboard

`Goal = {goalId, scope, metricDefinitionId, kind:FOLLOWER_LEVEL|ENGAGEMENT_INCREASE,
unit, baselineValue, baselineSnapshotIds, targetValue, periodStart, periodEnd, zoneId,
cohortPostIds, revision, createdAt}`. revision dương; đề xuất một goal/một kênh; baseline/definition/cohort/kỳ bất biến,
chỉnh sửa tạo revision để không đổi ý nghĩa tiến độ đã lưu. Baseline follower phải là sample thật
được chọn rõ ràng; trễ đầu kỳ phải hiển thị khoảng thực và chưa coi là goal đầy đủ kỳ.

`GoalProgress = {goalId, goalRevision, actual, rawProgress, reason, actualStart, actualEnd,
coverage, sourceSnapshotIds, calculatedAt, isStale, completionStatus}`.
completionStatus = UNKNOWN / IN_PROGRESS / REACHED / NOT_REACHED.
Khi không đủ phạm vi đo,
chỉ trả progress tạm tính có nhãn partial; trạng thái hoàn thành còn UNKNOWN.
Đạt mục tiêu cần dữ liệu đủ coverage và không stale. Thiện xác nhận UI có thể hiển thị
`rawProgress` tạm tính và giữ `completionStatus = UNKNOWN` khi coverage thiếu hoặc dữ liệu stale.
actual chỉ dùng số đo trong kỳ goal, không lấy số mới nhất sau periodEnd để đánh giá kỳ cũ.
Goal follower có thể hiển thị mức hiện tại trong kỳ và progress tạm thời; không phải chờ
đến cuối kỳ mới hiển thị. completionStatus giữ UNKNOWN khi coverage/freshness chưa đủ.

`DashboardQuery = {channelIds, metricDefinitionId, requestedStart, requestedEnd,
zoneId, cohort, grouping, topLimit}`. grouping = TOPIC / FORMAT / PUBLISH_HOUR.
Giờ đăng lấy publishedAt của bài theo zoneId query; thiếu metadata vào nhóm UNKNOWN,
không đoán từ nội dung. Nếu bài có nhiều topic, chọn primaryTopicId cho nhóm cộng được;
nếu dùng multi-label thì ghi overlap và không cộng tổng các nhóm.

`DashboardData = {channelCards, postMetrics, growthPoints, weeklyEngagement,
topPosts, groupedMetrics, goals, coverage, lastSuccessAt, lastAttemptAt, syncState, errors}`.
Mỗi metric/card/point giữ timestamp, reason và freshness; không dùng một timestamp
toàn dashboard che dữ liệu cũ. `TopPost = {scope, postId, title?, publishedAt?,
selectedMetric, rank, actualStart, actualEnd, coverage}`. Title là metadata cache nullable.

Các trường con tối thiểu để Thiện review:

| DTO | Trường dữ liệu đề xuất |
|---|---|
| PostMetadata | scope, postId, title nullable, publishedAt nullable, primaryTopicId nullable, format nullable; UNKNOWN khi không có metadata |
| PostMetrics | PostMetadata + metrics: danh sách {definitionId, MetricValue/CalculatedValue, collectedAt nullable, freshness}; không chỉ số tổng mất thành phần |
| ChannelCard | scope, displayName, follower: MetricValue, followerCollectedAt nullable, weeklyEngagement: CalculatedValue, syncState, errors |
| GrowthPoint | scope, definitionId, snapshotId nullable, observedAt, value nullable, missingReason, isGap; điểm gap không phải sample đo |
| GroupedMetric | groupKind, groupKey (UNKNOWN nếu thiếu), label nullable, CalculatedValue, postCount, excludedCount |
| SyncState | IDLE / LOADING / ERROR; offline boolean riêng; danh sách rỗng là empty khi query thành công, lỗi không đổi thành empty |

freshness = {isStale:boolean|null, lastSuccessAt:null|UTC}; chưa từng đo thì isStale=null,
không khẳng định dữ liệu NOT_SYNCED là còn mới. Cache cũ + ERROR/OFFLINE giữ số cũ và
timestamp, không che lỗi refresh. GrowthPoint gap thuộc output biểu đồ, không ghi thành
MetricSample giả vào lịch sử.

Top bài: metric và kỳ do người dùng chọn; chỉ xếp các kết quả đủ dữ liệu tương thích
với query. Sort value giảm dần, publishedAt giảm dần (null sau cùng), platform,
channelId, postId tăng dần theo thứ tự chuỗi cố định khi hòa. Số âm vẫn được xếp.
Trả excludedCount/reasons; không xếp null thành 0. TopLimit > 0.
Nhóm ER dùng sum(E)/sum(impression) khi tất cả thành phần/định nghĩa/khoảng đo tương thích,
không lấy trung bình các phần trăm. Không gộp số của hai nền tảng khi chưa chứng minh
định nghĩa tương đương; mặc định hiển thị nhóm/kênh riêng.

Đề xuất `AnalyticsRepository.observeCachedDashboard(query)` / `refresh(query, callback)`
và `GoalRepository.saveRevision(goal)` / `observeProgress(goalId)`; nghiệp vụ do Nghị
tính ngoài main thread, Thiện chỉ nhận DTO và render loading/empty/error/permission/offline/stale.
Chữ ký LiveData/callback cụ thể chờ người dùng contract xác nhận.

## 8. Đề xuất lưu trữ cho Đạt

Chưa tạo entity/DAO/version/migration. Các khóa và index sau cần SP-012 xác nhận.

| Dữ liệu | Khóa / index đề xuất | Truy vấn / ownership |
|---|---|---|
| Comment | UNIQUE(platform,channelId,commentId); INDEX(channelId,postId,createdAt,commentId) | Phân trang cache ổn định; riêng kênh |
| SyncCheckpoint | UNIQUE(channelId,postId,queryFingerprint,connectionRevision) | Resume đúng revision, lưu cùng upsert trang |
| Snapshot | snapshotId; UNIQUE(channelId,entityType,entityId,collectionId); INDEX(channelId,entityType,entityId,collectedAt,snapshotId) | Một snapshot mỗi đối tượng mỗi đợt; xác minh đối tượng thuộc đúng kênh trong transaction |
| Sample | UNIQUE(snapshotId,definitionId); INDEX(definitionId,snapshotId) | Một sample mỗi định nghĩa trong một snapshot; chốt period key với Đạt nếu cần lưu nhiều kỳ trong cùng đợt |
| Collection | collectionId + scope + startedAt/completedAt | Xác định đợt thu chung, lỗi và coverage |
| Goal | goalId/revision; INDEX(channelId,periodStart,periodEnd) | Riêng kênh; không dùng bản nháp chung làm cascade root |
| Post grouping | INDEX(channelId,primaryTopicId,publishedAt,postId), index format khi cần | Lọc tập bài trước khi tải metric; không N+1 |

Retry cùng lần thu giữ collectionId/snapshotId để tránh nhân bản; lần đo mới tạo ID mới,
không UNIQUE theo ngày vì có refresh thủ công. Query lấy tập bài rồi batch load samples
cho hai mốc, không quét lịch sử theo từng card. Benchmark/query plan làm sau khi có DAO/schema thật.
Xóa kênh dọn comment/snapshot/goal/cache/cursor của kênh, giữ draft/topic chung và kênh khác.
Revision phải bền vững để callback cũ không khôi phục dữ liệu đã xóa. Goal trong W1
có scope kênh. Goal độc lập được nêu trong SP-012 nằm ngoài DTO này. Đề xuất N1 của
SP-009 về tombstone của hàng đợi đăng vẫn chờ duyệt.

## 9. Thu snapshot thật W2–3 (SP-045)

Owner Nghị; lịch dự kiến một lần/ngày khi thiết bị đủ điều kiện, kèm refresh thủ công
theo proposal. W2 gốc 09–15/11, W3 16–22/11/2026; ngày bắt đầu thực tế phụ thuộc quyền.
Đề xuất slot 00:15 theo zoneId được cấu hình, WorkManager có thể trì hoãn;
chỉ ghi collectedAt thực tế, không cam kết chạy đúng giờ hoặc bù snapshot giả.
Slot 00:15 không tự cung cấp mốc 00:00: delta thường chỉ là PARTIAL tuần và phải ghi
actualStart/end. Muốn FULL tuần cần provider cho số theo đúng kỳ hoặc hai số đo thật
đúng ranh giới; không tự dịch timestamp 15 phút. Chính sách goal/coverage cần được
Minh/Thiện chốt trước triển khai; chưa giả định mọi tuần sẽ có FULL coverage.

Trước lần thu đầu: Minh xác nhận kênh/account/product/scope và từng capability;
SP-018/019 chốt metric provider/version/definition. Thu follower kênh và bộ đếm từng bài
được phép đọc, cùng collectionId; metric thiếu ghi reason. Không có quyền không chạy
thay bằng connections hoặc scrape. Thất bại giữ lastSuccess, ghi attempt/gap và next action;
ngắt kênh dừng job. Hai snapshot thật khác thời điểm mới mở so sánh; W1 không yêu cầu sẵn lịch sử.

## 10. Xác nhận review và điểm cần chốt

| Người | Cần xác nhận / cung cấp | Ảnh hưởng nếu chưa có |
|---|---|---|
| Minh (`nigPeter2503`) — Social | Đã góp ý cập nhật trạng thái SP-009 và làm rõ mapping MissingReason với Social v1 | Draft-4 đã bổ sung mục 3. Cần Minh xác nhận bản sửa trên PR |
| Đạt (`dat532006`) — SP-012/#14 | Xác nhận khóa/ID, UTC/null, cascade, collection/index và thứ tự schema | Còn chờ xác nhận thiết kế Room |
| Thiện (`PhuocThien06`) — UI F7 | Ngày 10/11 xác nhận draft-2 đủ trường UI và chấp nhận tài liệu + fixture cho W1 | Xác nhận áp dụng cho draft-2 tại [PR #67](https://github.com/nigPeter2503/Social_Pilot/pull/67) |

Draft-4 cần review phần ánh xạ Social v1 và đề xuất lưu trữ SP-012. Các điểm còn mở gồm ngưỡng stale
theo lịch thu thực, chọn cặp mốc chung và coverage, alignment measurement windows,
query/grouping/tie-break và lịch thu W2–3. Chữ ký Java cụ thể chốt khi tích hợp.

SP-020 W1 bàn giao contract/fixture có nhãn: không đòi token hoặc snapshot thật sẵn có.
SP-018/019 kiểm chứng API, SP-045 thu lịch sử; chúng bổ sung mapping qua revision sau.
Thiện đã xác nhận tài liệu và fixture đủ cho UI W1. Minh còn cần xác nhận phần tiêu thụ
hợp đồng ở adapter và repository.
