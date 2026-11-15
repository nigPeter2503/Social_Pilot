# SP-012 — Hợp đồng dữ liệu Room

Issue [#14](https://github.com/nigPeter2503/Social_Pilot/issues/14) · INFRA, F9.B2 · Điều phối Nguyễn Đức Đạt (TV2).

Hợp đồng này mô tả mô hình logic và kế hoạch migration để module owner triển khai trong PR riêng. Tên bảng/cột hiện hữu được giữ theo schema v1. Tên các thực thể còn lại trong tài liệu là tên logic.

## 1. Baseline phải bảo toàn

Đối chiếu `main` tại `0348982`. [Schema v1](../android/app/schemas/com.socialpilot.app.data.local.AppDatabase/1.json) và [AppDatabase](../android/app/src/main/java/com/socialpilot/app/data/local/AppDatabase.java) khai báo đúng một bảng trong `socialpilot.db`.

| Cột | Khai báo v1 | Quy tắc tương thích |
|---|---|---|
| `id` | `INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL` | Giữ giá trị và khả năng sinh ID mới. Hai bản nháp cùng topic vẫn là hai dòng độc lập |
| `topic` | `TEXT`, nullable | Bảo toàn dữ liệu đã lưu, kể cả `NULL`. Luồng tạo mới qua repository vẫn validate và trim |
| `createdAt` | `INTEGER NOT NULL` | Giữ epoch millisecond. Khi bằng nhau, dùng `id` để sắp xếp |

[DraftDao](../android/app/src/main/java/com/socialpilot/app/data/local/DraftDao.java) đọc 20 dòng gần nhất theo `createdAt DESC, id DESC`. [DraftRepository](../android/app/src/main/java/com/socialpilot/app/data/repository/DraftRepository.java) ghi epoch ms trên executor. [AppContainer](../android/app/src/main/java/com/socialpilot/app/core/AppContainer.java) tạo Room builder chưa có migration hoặc destructive fallback.

## 2. Quan hệ và trách nhiệm dữ liệu

```text
Brief 1 ── * BriefSource
Brief 1 ── * BriefChannel * ── 1 Channel
BriefChannel 1 ── * ContentVersion ── 0..1 parent cùng link
ContentVersion 1 ── 0..1 PublishJob ── 0..1 PublishedPost
Channel 1 ── * PublishedPost 1 ── * Comment
Channel hoặc PublishedPost 1 ── * Snapshot 1 ── * MetricSample
AiSession 1 ── * AiAttempt 1 ── 0..1 UsageSettlement
ContentVersion * ── 0..1 ImageRef
```

`PublishedPost` có thể được đồng bộ độc lập với `PublishJob`. `AiAttempt` mang phạm vi kênh và revision khi làm việc cho một kênh, hoặc scope chung tường minh. Bản nháp v1 có thể được ánh xạ sang `Brief` trong một PR sau khi Thiện thống nhất luồng đọc/ghi. Thiết kế v1 chưa có kênh, version hay approval để suy ra các quan hệ đó.

| Dữ liệu | Owner cung cấp → bên dùng | Ownership và vòng đời |
|---|---|---|
| Draft v1, brief, source | Thiện → Đạt, Minh | Độc lập với kết nối kênh. Xóa một kênh chỉ gỡ liên kết tương ứng |
| Link kênh, version, approval, ảnh của version | Thiện → Đạt, Minh | Version thuộc đúng một link. Parent và con trỏ current/approved cùng link. Enqueue chỉ nhận version vừa là current vừa có approval còn hiệu lực. Khi lưu version current mới, đánh dấu cần duyệt lại |
| Channel, credential ref OAuth, publish job/post | Minh → Thiện, Đạt, Nghị | Scope theo kênh. Minh sở hữu vòng đời kết nối, invalidation, đối soát publish và xóa token |
| AI session, attempt, checkpoint, usage | Đạt → Thiện, Nghị | Scope chung hoặc scope kênh tường minh. Checkpoint, partial text và log riêng của kênh được dọn khi ngắt. Quyết định giữ tổng chi phí sau ngắt được theo dõi ở mục 7 |
| Comment, sync cursor, snapshot, metric, goal | Nghị → Thiện, Minh | Comment/cursor/snapshot thuộc kênh. Goal mang scope tường minh. Goal cấp kênh được dọn theo kênh, goal độc lập giữ lại |
| ImageRef và nội dung ảnh | Owner tạo ảnh → Thiện, Minh | Ảnh có thể được nhiều version tham chiếu. Dọn file chỉ sau khi xác định không còn reference hoạt động |

Các hợp đồng đang review dùng làm đầu vào đối chiếu: [SP-009 social](https://github.com/nigPeter2503/Social_Pilot/blob/d09819594e5b939d88c810a83cf8165c0abcc12c/docs/social-contract.md), [SP-016 brief/version](https://github.com/nigPeter2503/Social_Pilot/blob/49197859aebef82167d9b4f3dce5c8f460166ad0/docs/sp-016-brief-contract.md), [SP-020 analytics](https://github.com/nigPeter2503/Social_Pilot/blob/bf89da1598ca13fd02e2bd116f4f47a5a07c2555/docs/analytics-data-contract.md). Khi những hợp đồng này đổi, owner đối chiếu field và phạm vi khóa trước khi xin lượt migration.

## 3. Định danh, khóa và index

- Tên bảng mới dùng danh từ số nhiều, chữ thường và dấu gạch dưới khi có nhiều từ. Cột mới dùng `lowerCamelCase` theo schema v1. Ghi tên lưu trữ rõ trong entity khi tên Java có thể đổi độc lập.
- ID local mới là chuỗi opaque, ưu tiên UUID sinh trên thiết bị trước khi enqueue hoặc gọi mạng. Giữ nguyên kiểu `long` và giá trị PK của `drafts.id`. Nếu nhập draft cũ, lưu mapping theo `legacyDraftId` với ràng buộc duy nhất, để mỗi dòng v1 chỉ nhập một lần.
- Giữ external ID và URN đúng dạng provider trả về, kể cả hoa/thường và số 0 đầu. Tránh ép sang số, ghép ID bằng delimiter hoặc dùng display name làm khóa. Khi đã xác nhận semantics tài khoản, Minh dùng tuple `(platform, accountType, externalAccountId)` cho kết nối cùng danh tính. `connectionRevision` đánh dấu đời kết nối và phải đổi khi ngắt/thay kết nối.
- Khai báo FK cho quan hệ bắt buộc có cùng vòng đời và index các cột FK bên con dùng để join/xóa. Dùng composite FK hoặc kiểm tra trong transaction khi cần ràng buộc parent/version/subject thuộc cùng scope. Thành phần khóa nghiệp vụ bắt buộc phải `NOT NULL`. Ghi rõ phạm vi nullable cho cột dùng trong unique index vì SQLite cho phép nhiều giá trị `NULL`.

| Đối tượng | Khóa nghiệp vụ và xử lý trùng | Index truy vấn cần xác nhận cùng owner |
|---|---|---|
| BriefChannel | Unique `(briefId, channelId)` | Tra theo `channelId` khi ngắt kết nối |
| ContentVersion | `versionId` unique. Parent, current và approved đều phải thuộc đúng link; cùng lúc chỉ một current/approved pointer cho link | Lịch sử theo `(briefId, channelId, createdAt)` |
| PublishJob | Unique `(channelId, briefId, approvedVersionId)`. `briefId` là ID draft canonical cho editor mới. Retry/đối soát cập nhật cùng job. Transaction xác minh version đang được duyệt thuộc đúng brief/kênh trước khi enqueue | Theo channel/status và thời điểm retry. `UNKNOWN` phải đối soát trước khi gửi lại |
| PublishedPost | Unique `(channelId, externalPostId)` khi đã có external ID; trạng thái chưa có ID thuộc job đang đối soát | Theo channel và thời điểm đăng |
| Comment | Unique `(platform, channelId, externalCommentId)`. Cùng khóa nhưng khác post là xung đột. Parent chưa sync được biểu diễn riêng với comment gốc | Theo channel/post/time; cursor theo channel, post và query fingerprint |
| Snapshot và sample | Unique `(channelId, subjectType, subjectId, collectionId)`. Retry cùng đợt tái dùng collection ID. Sample unique `(snapshotId, definitionId)` nếu mỗi definition có một sample mỗi đợt. Nếu cần nhiều kỳ, Nghị chốt thêm period key trước khi tạo constraint | Theo scope, subject và observed time; theo definition cho biểu đồ |
| AI attempt và settlement | Unique `requestId` cho mỗi lần dispatch. Retry/fallback tạo ID mới. Reserve và settle theo ID đó, idempotent khi nhận terminal lặp | Theo provider và kỳ ngân sách đã gắn lúc reserve |

Với `Snapshot`, `subjectId` phải là ID của chính channel hoặc post thuộc channel đó, tùy `subjectType`. Khi hiện thực hóa, owner dùng hai quan hệ có FK hoặc tách quan hệ theo kiểu subject. Nếu dùng chung cột đa hình, transaction ghi và xóa phải kiểm soát việc tham chiếu chéo kênh và orphan.

## 4. Ngắt kênh, secret và file

1. Trong transaction, vô hiệu revision cũ và chặn admission mới cho kênh. Mọi callback ghi Room so sánh revision và thực hiện ghi trong cùng transaction. Check revision trước HTTP chưa đủ.
2. Yêu cầu các module hủy công việc của revision cũ. Đạt hủy Future và HTTP Call. Minh và Nghị hủy job/đồng bộ tương ứng. Công việc đã gửi nhưng chưa rõ kết quả đi theo quy tắc đối soát của Minh.
3. Dọn dữ liệu sở hữu riêng theo channel. Dùng cascade với child độc quyền có FK rõ và xóa tường minh với quan hệ nhiều owner, state đối soát hoặc tài nguyên ngoài DB. Giữ root độc lập và link của kênh khác.
4. Dọn token, secret ref và ảnh hết tham chiếu. Với cleanup ngoài DB, module owner định nghĩa trạng thái cần dọn bền vững hoặc cách quét lại an toàn nếu process chết sau commit. Phải kiểm lại quyền sở hữu và reference trước khi retry dọn file.

Ví dụ brief `b1` có link `cA` và `cB`, mỗi kênh có một version. Khi ngắt `cA`, version riêng của A, cursor, comment và snapshot A được dọn, link A được gỡ. `b1`, nguồn chung, version B và ảnh mà B còn tham chiếu vẫn đọc được. Callback A đến từ revision cũ sau khi commit bị bỏ ở điểm ghi. Ngắt kênh xử lý dữ liệu cục bộ và kết nối. Bài đã đăng trên nền tảng vẫn tồn tại.

Room chỉ lưu opaque credential reference khi cần. Key/token nằm trong kho riêng được mã hóa bằng khóa do Android Keystore bảo vệ và được loại khỏi log, backup và export. Provider secret được cấp đúng ranh giới mạng. ImageRef chứa ID hoặc đường dẫn tương đối được quản lý trong vùng riêng của app cùng metadata cần thiết. Tránh lưu absolute path, URL tạm có chữ ký hoặc URL chứa credential.

## 5. Null, provenance và thời gian

- Metric `0` là số đo hợp lệ. Khi thiếu, dùng `value = NULL` và lý do, ví dụ `PERMISSION_REQUIRED`, `UNSUPPORTED`, `NOT_SYNCED` hoặc `PROVIDER_OMITTED`. Response lỗi cập nhật trạng thái lần thử và giữ số đo tốt gần nhất kèm freshness. Tỷ lệ với mẫu số 0 là không tính được. Fixture chỉ vào kho test, có provenance riêng với dữ liệu API thật.
- Lưu thời điểm trong Room dạng UTC epoch millisecond. Ghi riêng thời điểm provider đo và thời điểm thu. Nếu dùng thời điểm thu để thay observed time thiếu, lưu `observedAtBasis = COLLECTION`. Trên wire, giữ định dạng UTC mà contract tương ứng yêu cầu.
- Nhóm ngày/tháng dùng zone IANA đã chọn và khoảng `[start, end)`. Kỳ ngân sách được gắn lúc reserve để settle cùng kỳ khi request đi qua nửa đêm hoặc cấu hình zone đổi. Ví dụ `2026-11-11T17:30:00Z` thuộc ngày 12/11 ở `Asia/Bangkok`.

## 6. Thứ tự migration từ v1 và kiểm thử

| Thứ tự | Owner và đầu vào | Thay đổi dự kiến |
|---|---|---|
| Hiện tại | Đạt, SP-012 | Hợp đồng và walkthrough trên schema `1.json`. Database đang ở v1 |
| Bước schema đầu tiên | Đạt cấp lượt, Minh/Thiện cung cấp root cần thiết | Thêm schema tương thích cho Channel và Brief khi module sẵn sàng. Giữ bảng `drafts`, ID, topic và timestamp v1. Giữ entry point đọc draft cũ đến khi Thiện bàn giao đường chuyển đổi |
| Version và approval | Thiện, sau khi có root và ID thống nhất | Thêm link, version và con trỏ duyệt. Nếu nhập draft cũ, map mỗi ID v1 sang một brief và bảo toàn null/topic/thời điểm theo quy tắc đã review. Giữ draft không có liên kết kênh cho tới khi người dùng chọn kênh |
| Publish, AI, analytics | Minh/Đạt/Nghị theo thứ tự FK thực tế | Thêm job/post sau version, usage/checkpoint sau scope tương ứng, comment/snapshot khi đã có các root cần thiết. Chốt version từng bước theo `main` và policy đang áp dụng |

Mỗi lượt schema có một owner sửa khai báo database, version và chuỗi migration chung. Chủ module đưa entity/DAO, migration, schema export và kiểm thử vào cùng PR. Đạt đối chiếu các đường từ mọi version được hỗ trợ, thứ tự merge và schema mới trên fresh install. Migration cần backfill, đổi ràng buộc hay chuyển ownership dùng migration tường minh. Cấm destructive fallback và xóa database để vượt qua kiểm thử.

| Ca cần chạy trong PR migration | Dữ liệu và điều kiện đạt |
|---|---|
| Fixture v1 | Tạo DB theo `1.json` bằng Room `MigrationTestHelper`. Seed raw SQL với ID 7 và 11 cùng topic/cùng timestamp, ID 42 có topic `NULL`, từng tạo rồi xóa ID 50. Chạy `runMigrationsAndValidate` với migration đích. Kiểm chính xác ID, topic, null, timestamp, thứ tự `42/11/7` khi thời điểm dòng 42 mới hơn, và draft thêm sau upgrade nhận ID lớn hơn 50 |
| Đường nâng cấp | Chạy từng bước và từ mọi version được hỗ trợ đến head mới. So schema upgrade với fresh install. Mở lại DB qua Room builder thật để kiểm tra đăng ký migration và hành vi DAO |
| Integrity và dedupe | Kiểm FK/orphan, insert trùng publish/comment/snapshot, upsert theo đúng scope và rollback khi lỗi giữa transaction. Kiểm parent/version/post thuộc cùng scope |
| Ngắt A khi A/B cùng hoạt động | Seed brief và ảnh chung. Kiểm riêng callback A trước và sau commit invalidation, cleanup ngoài DB retry, giữ version B, chặn attempt A cũ và áp dụng đúng chính sách reconnect đã duyệt |
| Dữ liệu thiếu và ngày | Seed 0, NULL có lý do, stale và fixture. Kiểm group theo timezone, qua nửa đêm, DST, đổi zone và settle kỳ cũ |

Đóng gói schema exports vào assets của instrumentation test khi thêm migration test đầu tiên. Chạy trên API 23 và 36 với revision cố định. PR SP-012 chỉ kiểm nội dung, liên kết, diff và walkthrough thiết kế. Các ca thực thi trong bảng mang trạng thái `NOT_RUN` tại thời điểm bàn giao hợp đồng.

## 7. Bàn giao và quyết định còn mở

| Người nhận | Phần cần xác nhận trước khi triển khai |
|---|---|
| Minh | Mapping danh tính external, publish tuple và revision guard. Chốt N1 cùng Đạt/Nghị tại [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11) hoặc ticket kế nhiệm trước khi giữ tombstone sau disconnect. TTL 30 ngày trong PR #66 là đề xuất |
| Thiện | Bảo toàn draft v1, brief/link/version/approval, kế hoạch đọc hoặc nhập dữ liệu cũ và thời điểm chọn PR migration đầu tiên |
| Nghị | Khóa comment, scope post/snapshot, period key khi có nhiều kỳ, null/provenance và dữ liệu cần dọn theo kênh |
| Đạt | Giữ lịch cấp version và review đường nâng cấp. Chốt việc giữ aggregate chi phí tối thiểu, không nhận dạng kênh, cùng owner trước khi đưa vào persistence. Bàn giao convention `requestId`, scope và thời gian cho [SP-013](https://github.com/nigPeter2503/Social_Pilot/issues/15) |

Khi mở PR migration, ghi schema revision, owner giữ lượt, nguồn và đích version, FK/index thay đổi, rollback hoặc cách xử lý lỗi, kết quả kiểm thử nâng cấp và người tiêu thụ cần review. [Quy trình phối hợp](../CONTRIBUTING.md#6-phối-hợp-phần-dùng-chung)
