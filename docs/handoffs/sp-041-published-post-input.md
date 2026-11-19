# SP-041.01 — PublishedPost handoff v0.1.0 (local draft)

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
