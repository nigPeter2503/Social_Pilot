# SP-041.07 · Mở inbox, lọc cache và chọn comment để reply

Nghị bàn giao API cho SP-042/043 và Thiện/SP-051. Các chữ ký và ca kiểm bên dưới áp dụng cho mã `a192d58003b2f7f6886587ac2adcfd7c133e24d1` của PR #87. JVM và thiết bị dùng store giao dịch **trong RAM** (`MO_PHONG`). Ứng dụng còn chờ Channel/DAO, Room và manifest provider của .06 để nghiệm thu dữ liệu thật. [Evidence Worker/cache](../sp-041-worker-cache-ig-fb.md)

## Chọn bài và tạo query

Lấy platform, loại account, channelId nội bộ, externalAccountId và connectionRevision dương từ Channel hiện hành. Tra `PublishedPostRepository.lookupByLocalId(scope, localPostId)` hoặc `lookupByExternalId(scope, externalPostId)` và dùng `LookupResult.post.externalPostId` khi `FOUND`. Adapter cần `CommentAdapter.Binding` xác minh ownership của bài và quan hệ với target trong cùng scope. Lấy pageSize trong giới hạn của binding, version từ cấu hình đã kiểm.

| Nguồn | Account và query | Post và target |
|---|---|---|
| Instagram Professional | `Account.INSTAGRAM_PROFESSIONAL`, `Query.IG_COMMENTS` | externalAccountId là IG user ID, postId là media ID. Với `Target.POST`, targetId bằng media ID. Với `Target.COMMENT`, giữ media ID ở postId và truyền ID comment cha vào targetId để đọc replies. |
| Facebook Page | `Account.FACEBOOK_PAGE`, `Query.FB_TOPLEVEL` hoặc `Query.FB_STREAM` theo filter provider | externalAccountId là Page ID, postId là ID bài Facebook. Target POST dùng lại ID bài, target COMMENT dùng ID comment đã bind. |

Facebook và Instagram truyền threadId = null, apiVersion dạng `vN.N`. Nhánh LinkedIn trong code dùng `LINKEDIN_MEMBER`/`LINKEDIN_ORGANIZATION`, `LI_COMMENTS`, post URN, thread URN đã xác minh, comment URN đầy đủ nếu đọc replies và version YYYYMM. `historyFrom` là mốc bao gồm, epoch ms hoặc null. Pull luôn truyền cursor null. Coordinator khôi phục cursor từ checkpoint của queryFingerprint, trong đó có cả target, filter provider, pageSize, historyFrom và revision. Bộ lọc handled dưới đây chỉ dùng khi đọc cache. [CommentPageRequest](../../android/app/src/main/java/com/socialpilot/app/data/remote/social/CommentPageRequest.java)

## Mở inbox → đọc cache → kéo làm mới → đọc lại

Ví dụ IG dùng `channel` kiểu `ChannelInfo`, `post` kiểu `PublishedPost` đã tra đúng scope và `repo` kiểu `CommentRepository` dùng chung với Worker. Ví dụ dùng version và pageSize của fixture. `showIfCurrent` là hàm của màn hình, chuyển sang main thread và kiểm phiên màn hình/query trước khi vẽ. Các lần đọc cache chạy trên IO.

```java
CommentPageRequest q = new CommentPageRequest(
        CommentPageRequest.Account.INSTAGRAM_PROFESSIONAL, channel.channelId,
        channel.connectionRevision, channel.externalAccountId, post.externalPostId,
        CommentPageRequest.Target.POST, post.externalPostId, null,
        CommentPageRequest.Query.IG_COMMENTS, 2, null, "v27.0", null);
CommentStore.Handled filter = CommentStore.Handled.ALL;
CommentRepository.View first = repo.loadCached(q, filter);
showIfCurrent(q, first);

// Khi người dùng kéo làm mới, giữ handle theo vòng đời của màn hình.
Cancelable pull = repo.refresh(q, (CommentSyncCoordinator.Result result) -> {
    CommentRepository.View updated = repo.loadCached(q, filter);
    showIfCurrent(q, updated);
});
// Khi đóng/đổi query, vô hiệu phiên hiển thị cũ rồi gọi:
pull.cancel();
```

`loadCached(CommentPageRequest, CommentStore.Handled)` trả một `View` đồng bộ. Đổi giữa `ALL`, `HANDLED`, `UNHANDLED` bằng cách đọc lại. `refresh(CommentPageRequest, CommentSyncCoordinator.Callback)` trả `Cancelable`. Callback nền `complete(Result)` trả kết quả lượt pull với `state()/reason()/error()/retryAt()`. Dữ liệu hiển thị nằm trong `View.rows()`. Hủy handle gỡ listener đó, listener khác cùng query vẫn có thể nhận kết quả. Khi cần vẽ spinner, đọc lại trong lúc pull đang chạy. Consumer tự kích hoạt đọc lại sau pull, đổi filter hoặc khi biết Worker đã chạy. Repository hiện chưa cung cấp observer cache, API đánh dấu handled hay cờ handled cho từng row của danh sách ALL. [Repository](../../android/app/src/main/java/com/socialpilot/app/data/repository/comment/CommentRepository.java) · [Coordinator](../../android/app/src/main/java/com/socialpilot/app/data/repository/comment/CommentSyncCoordinator.java) · [Cache](../../android/app/src/main/java/com/socialpilot/app/data/local/comment/CommentCache.java)

| SP-042/Thiện-SP-051 đọc từ View | Cách hiển thị |
|---|---|
| `LOADING` | Hiện tiến trình cùng các rows đang có. |
| `EMPTY` | Đã hoàn tất sync, online và bộ lọc hiện tại rỗng. `NO_CACHE` là chưa có checkpoint. `PARTIAL` là có checkpoint chưa hoàn tất, xem thêm paging/paginationReason. |
| `OFFLINE_CACHED` / `OFFLINE_NO_CACHE` | Báo offline. Trường hợp đầu giữ danh sách hoặc kết quả rỗng đã biết. Cache vẫn có thể tồn tại khi bộ lọc hiện tại trả 0 hàng. |
| Lỗi trang sau | Khi online, `ERROR` có thể đi cùng rows trang trước và `paging() == MORE`. Khi offline trạng thái ưu tiên offline. Giữ rows, đọc `failure()/error()/retryAt()` để hiện lỗi và thời điểm thử lại. `lastAttemptAt()` ghi lần thử, `lastCompleteSyncAt()` và `freshness()` theo lần hoàn tất. |
| Cần kết nối lại | Kiểm `connection() == REAUTH_REQUIRED` hoặc `error() != null` và `error().nextAction == RECONNECT` kể cả lúc offline. Truy cập revision đã nghỉ có thể trả `ERROR` với rows rỗng. Thiếu quyền có action `RESOLVE_PERMISSION` riêng. |

**SP-042** hiển thị `StoredComment.authorDisplayName`, `message/contentState` (AVAILABLE, OMITTED, DELETED), `createdAt/timeMissingReason`, `parentState/parentCommentId` và `needsRefresh`. Các field trên row giữ metadata đã biết khi provider trả sparse. Cache sắp theo createdAt giảm dần, ngày thiếu xuống cuối, rồi commentId. **SP-043** lấy FK `row.localPostId`, ID bài/media và comment phía provider từ `row.observation.postId/commentId`, parent từ `row.parentCommentId` khi `parentState == KNOWN`. UNKNOWN giữ nguyên quan hệ chưa rõ. Lưu selection cùng `row.observation.scope` (platform/channelId/connectionRevision). Quyền tại lúc đọc nằm ở `row.observation.replyCapability` (operation, availability, reasonCode, verifiedAt). Trước gửi, SP-043 phải đối chiếu lại Channel đang kết nối, revision và capability `REPLY_COMMENT/AVAILABLE` từ binding hiện hành, cùng nội dung đã duyệt. API inbox cung cấp selection và quyền đã quan sát. API gửi reply và đối soát sau mất phản hồi thuộc SP-043.

**Ca đối chiếu tại mã bàn giao.** Trong `CommentSyncTest`, `instagramWorkerResumesEachQueryAndDeduplicatesOverlappingPages` dựng lại coordinator, resume bốn query và dedup. `instagramPageTwoNetworkLossKeepsFirstPageThroughWorkerRetry` và `nextPage429PreservesCacheAndHonorsDeadlineAcrossRestart` kiểm giữ dữ liệu, chờ hạn và resume đúng cursor. `lateInstagramPageCannotAdvanceCheckpointAndFacebookJobSurvivesCleanup` kiểm response IG đến muộn và job Facebook vẫn chạy. 239/239 JVM tại `a192d58`. Instrumentation 3/3 trên Pixel 9 API 37 x86_64 dùng mã ứng dụng và harness của `c1f197d`. [Test JVM](../../android/app/src/test/java/com/socialpilot/app/data/remote/social/CommentSyncTest.java) · [Evidence thiết bị](../sp-041-worker-cache-ig-fb.md)

## SP-041.01 · Hồ sơ PublishedPost v0.1.0

Owner: Tô Lãng Nghị / TV4. **MO_PHONG harness; chưa tích hợp Room/Channel production.**
Không thay Social v1, Analytics draft-4 hoặc Room contract đã có. Chữ ký dưới đây
là API module đề xuất để Minh/Đạt và consumer review, chưa có ACK.

## API đã có code độc lập

Package `com.socialpilot.app.data.local.publishedpost`:

```java
PublishedPostRepository.forProduction(PublishedPostStore store); // chỉ nhận API_THAT
PublishedPostRepository.forSimulation(PublishedPostStore store); // chỉ nhận MO_PHONG
WriteResult upsert(PublishedPostInput input);
WriteResult upsertAll(List<PublishedPostInput> inputs);
LookupResult lookupByExternalId(PublishedPostScope scope, String externalPostId);
LookupResult lookupByLocalId(PublishedPostScope scope, String localPostId);
```

`PublishedPostScope` chứa platform, accountType (PAGE/PERSON/ORGANIZATION), channelId,
connectionRevision dương và externalAccountId. Không chứa displayName/credential.
ID/URN giữ nguyên chuỗi; không parse số/cắt ID/suy ownership từ prefix.

`PublishedPostInput.Ownership` là kết quả binding **tin cậy từ caller**, chứa đúng
scope, externalPostId và evidenceRef đã che. Constructor không chứng minh ownership
ngoài provider. Minh phải cung cấp lookup/mapping đã xác minh; không được tạo binding
bằng cách sao chép request không tin cậy. Input còn có metadata nullable, receivedAt
epoch ms, providerUpdatedAt nullable, source và sourceRef nhãn evidence. Không nhận
token/tokenReference/raw response. Caller normalize trước khi gọi, chạy trên IO executor.

`WriteResult.status=COMMITTED` chỉ sau transaction commit; entries có action
INSERTED/UPDATED/UNCHANGED/STALE và row ID local ổn định. STALE là no-op có chủ đích,
không cập nhật watermark hoặc provenance. Batch có lỗi trả entries rỗng và rollback
tất cả insert/update, kể cả batch nhiều kênh. Không có PublishJob hoặc approval FK.

Lookup trả FOUND với post; MISSING chỉ khi Channel/scope hợp lệ và không có row.
Các status CHANNEL_MISSING, SCOPE_MISMATCH, DISCONNECTED, AUTH_REQUIRED,
OWNERSHIP_CONFLICT, METADATA_CONFLICT, PROVENANCE_REJECTED và STORAGE_ERROR giữ
ý nghĩa riêng; failure không chứa post hoặc exception/message/SQL thô. Local ID
của kênh khác hoặc revision cũ không được trả ra ngoài scope. API synchronous,
không tự gửi HTTP, không tự retry, không ghi log.

## Transaction port cần Minh/Đạt bàn giao

`PublishedPostStore.transaction(Work<T>)` phải gọi `RoomDatabase.runInTransaction`
trên **cùng AppDatabase** với Channel. `Transaction.channel(channelId)` đọc projection
ChannelState `{scope,status}` ngay trong transaction. Invalidation/disconnect,
reconnect/account replacement và mọi post writes dùng cùng database transaction
boundary. Không nối bằng lookup OAuth ngoài Room rồi ghi post ở transaction khác.

`byExternalId(channelId, externalPostId)` và `byLocalId(id)` là DAO nội bộ; không
phơi ra UI/consumer để bypass guard. `insert` dùng ABORT; `update` giữ PK và business
key, bắt buộc affectedRows=1. Adapter store phải ném lỗi constraint/IO/commit và
rollback; không nuốt lỗi trả null. Repository kiểm Channel trước xử lý và trước
kết thúc transaction. Port mới bảo đảm serialization, FK và durability thực tế.

Cùng khóa `(channelId, externalPostId)` giữ ID UUID ban đầu. Khác channel tạo row
riêng. Row đã lưu khác identity/platform/account/revision/source bị từ chối, không
chuyển subject hoặc tự nhận lại sau reconnect. SP-024/Minh cần chốt cleanup root
riêng kênh, tách khỏi publish tombstone N1 chưa duyệt. Harness mô phỏng xóa post
độc quyền khi disconnect; đây chưa phải hook cleanup production đã ký nhận.

## Merge metadata đề xuất

- Null/vắng cùng nghĩa omitted/unknown: giữ content/permalink/publishedAt đã biết.
  Chuỗi content rỗng là giá trị thật. Không có lệnh xóa metadata ngầm.
- Khi cả hai có providerUpdatedAt, so timestamp provider; nếu không, so receivedAt.
  Response nhỏ hơn watermark là STALE, kể cả receipt mới hơn nhưng provider cũ hơn.
- Cùng version và field đã biết khác nhau: METADATA_CONFLICT, rollback batch,
  caller cần refresh/đối soát; cùng version được bổ sung field trước đó null.
- Khi đã biết providerUpdatedAt, response không có provider timestamp chỉ được bổ
  sung field thiếu, không thay field đã biết. Field khác trả METADATA_CONFLICT.
  Response mới có provider timestamp được ưu tiên theo timestamp provider.
- receivedAt lưu max của các response đã accept; không thay publishedAt. Giữ
  providerUpdatedAt đã biết khi response omitted. Quy tắc này bảo thủ, chưa có ACK
  mapping/version provider. Không khẳng định giải quyết thứ tự chỉnh sửa khi provider
  không cấp version đáng tin cậy; khi cả hai thiếu, receipt order là fallback tường minh.
- sourceRef/ownershipEvidenceRef là evidence response/binding cuối được accept.
  contentSourceRef/permalinkSourceRef/publishedAtSourceRef giữ evidence từng field
  khi omitted, không gán response sparse thành nguồn của metadata cũ.
- Canonical permalink chỉ nhận HTTPS, có host, không userinfo/query/fragment/port.
  URL có credential/signed query bị từ chối; caller phải cung cấp canonical public
  URL đã xác minh hoặc omitted. Không tự strip URL hay bịa permalink. Đây là giới
  hạn draft: link Facebook dạng query cần mapping canonical được Minh xác nhận.
- SourceRef/evidenceRef chỉ nhận nhãn allowlist; không nhận URL/Bearer. Không có
  trường credential hoặc response thô. Caller phải giữ secret ngoài normalized
  content/ID/evidence, không nhét credential vào trường văn bản. toString redacted;
  lỗi storage trả mã cố định. API_THAT do trusted caller xác nhận, không do enum
  tự chứng minh; harness test policy không phải thu API thật.

## Room draft và gate

[Entity](../drafts/sp-041-published-post/PublishedPostEntity.java.template),
[DAO](../drafts/sp-041-published-post/PublishedPostDao.java.template),
[SQL](../drafts/sp-041-published-post/migration.sql.template) là template **không compile,
không execute, không có version**. FK Channel không bị bỏ; placeholder chưa biết
tên class/table/PK. Dự kiến FK channelId → Channel PK, CASCADE cho root sync độc quyền,
unique `(channelId,externalPostId)` BINARY, index `(channelId,publishedAt,id)` hỗ trợ
lookup/channel cleanup/thời điểm. Cần Đạt/Minh xác nhận cascade và coexist với SP-025.
Không backfill post từ Draft, fixture hoặc PublishJob không có ID provider.

Minh cần bàn giao commit/schema Channel, tên/kiểu PK, scoped DAO lookup, identity
tuple, revision bền vững không tái dùng, trạng thái kết nối, lifecycle cleanup và
ownership evidence của bài. Đạt cần pin base/schema SHA, owner giữ slot, source/target
version, thứ tự Channel → PublishedPost với slot Brief/version liên quan và mọi
migration path hỗ trợ. Chưa có các artifact này trên checkout/refs đã đọc.

Sau khi nhận: resolve template, viết RoomStore mapper, đăng ký entity/DAO/migration
đúng slot trong AppDatabase/builder, export schema bằng annotationProcessor hiện hữu,
kiểm fresh install và mọi upgrade path. Migration test seed v1 ID 7/11/42, topic
NULL, deleted sequence 50; kiểm thứ tự và next ID >50; thêm FK/unique/index/rollback,
restart/reconnect và device API 23/36. Không destructive fallback.

## Consumer

- **SP-041.03:** lookup native post ID bằng scope; dùng post.id làm FK, giữ native
  ID cho CommentPageRequest.postId/CommentRecord.postId. Comment DTO hiện hữu chỉ có
  platform/channel/revision; bổ sung account/identity từ binding Channel tin cậy,
  không đoán từ ID. Không sửa SP-041.02; post↔thread LinkedIn vẫn cần evidence riêng.
  Khi lưu page/checkpoint, lookup và write phải trong cùng transaction hoặc store
  adapter dùng chung Transaction, không gọi lookup public rồi ghi transaction khác.
- **SP-045.07:** resolve row cùng scope trước gắn snapshot POST. Không lấy external
  ID từ kênh khác làm local FK. Fixture không được nhập lịch sử production.
- **Minh/SP-025:** dùng cùng `(channelId,externalPostId)` để upsert/reconcile bài
  đã đăng; giữ local ID. PublishJob association nếu cần thuộc slot/task khác.

Facebook SP-018 có refs Page/bài ở hồ sơ FB-01/02/09, chưa bind revision app hiện
hành. LinkedIn SP-019 LI-01–09 live NOT_RUN; post/thread fixture chỉ MO_PHONG. Task
này không seed bất kỳ ref spike nào vào database và không dựng ID quan hệ suy đoán.
