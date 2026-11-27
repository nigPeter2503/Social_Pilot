# SP-035 — Form brief lưu trên thiết bị

## Hợp đồng dữ liệu

`AppDatabase` nâng v1→v2 cho Channel (SP-022), rồi v2→v3 cho Brief bằng migration tường minh. Bảng `drafts` v1 giữ nguyên ID `long`, `topic` nullable, `createdAt` và thứ tự đọc. Brief mới có UUID `briefId`, topic, goal, audience, tone, `contentLanguage` (`vi`/`en`), thời điểm UTC epoch ms. `legacyDraftId` nullable và unique dành cho đường nhập draft v1 về sau; phiên bản này **không tự chuyển** draft cũ sang brief và không tự gán kênh hoặc approval. Màn hình vẫn hiển thị danh sách topic v1 riêng.

`brief_sources` lưu từng nguồn TEXT/URL theo thứ tự; `brief_targets` lưu `channelId` nội bộ và platform, unique theo `(briefId, channelId)` và `(briefId, platform)`. Hai bảng con có FK `ON DELETE CASCADE` tới brief. Repository ghi brief, sources và targets trong một transaction trên executor, đọc lại thành `Brief` đã validate trước khi chuyển cho consumer AI. Form tạo snapshot bất biến tại lúc bấm lưu, không đọc lại View từ callback.

Form cho lưu brief offline khi chưa có kênh. Mỗi field bắt buộc có lỗi tại đúng ô bằng VI/EN; ngôn ngữ nội dung độc lập với locale của UI. Nguồn text có thể để trống hoặc nhập một đoạn; bật loại URL để nhập nhiều URL, mỗi dòng một URL http/https không có userinfo. Danh sách gần đây đọc lại topic, goal, audience, tone, ngôn ngữ, sources và nền tảng đã chọn qua một truy vấn Room có transaction. Không có nút tạo nội dung/đăng bài từ màn hình này.

## Bàn giao và giới hạn

Hợp đồng cho Đạt: `Brief` trong `domain/editor` nhận `briefId`, topic, goal, audience, tone, language, danh sách tối đa một target cho mỗi nền tảng có trong registry, cùng sources. Chỉ `BriefRepository.loadValidated(briefId)` cho payload hợp lệ; draft chưa có target không thể thành payload. ID kênh lấy từ `ChannelRepository` của SP-022, không dùng Page ID/tên Page làm `channelId`. Form cho chọn tối đa một kênh mỗi nền tảng, kiểm lại revision và credential khi lưu. Brief vẫn lưu offline khi chưa chọn kênh. SP-022 đã có trong `main`; SP-035 nối migration Channel v1→v2 với Brief v2→v3.

## Kiểm chứng

- JUnit kiểm field rỗng, target trùng platform và snapshot brief hợp lệ.
- Instrumentation dùng `MigrationTestHelper` seed v1: ID 7/11 cùng topic và thời gian, ID 42 topic null, ID 50 từng tạo rồi xóa. Kiểm thứ tự, giá trị, ID mới >50 và DAO sau mở lại.
- Instrumentation lưu brief hai target và hai source, đóng/mở database, đọc đúng field/ID/thứ tự. Test UI lưu rồi xoay không nhân bản brief.
- Test trên AVD API 23 và API 36; kết quả chạy được ghi trong PR theo commit cụ thể.

Ngày 19/11/2026, Windows/JDK 21/SDK 36: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest` đạt trên AVD API 23; 63/63 JVM test, 5/5 instrumentation, lint 0 lỗi/16 cảnh báo. Cùng bộ instrumentation đạt 5/5 trên AVD API 36. Sau khi thêm transaction đọc snapshot, hai test migration/reopen đạt trên API 36; sau khi thêm danh sách chi tiết, ca UI lưu/xoay/hiển thị field đạt lại trên API 23. [Ảnh form tiếng Anh trên API 36](evidence/sp-035-api36-form-en.png). Chưa thử trên điện thoại thật; thao tác offline qua UI dump trên emulator không lấy được root node nên chỉ xác nhận đường lưu local bằng instrumentation, chưa ghi nhận kiểm tra trực quan offline.

Ngày 26/11/2026: tích hợp ChannelRepository SP-022 theo schema Channel v2 → Brief v3, hiển thị kênh đã kết nối từ registry và lưu channelId nội bộ. Form từ chối lựa chọn đã đổi revision hoặc mất credential tại lúc lưu. Android assemble/unit/lint đạt; 4/4 ca migration và form trên AVD API 36 đạt, gồm kiểm dữ liệu Channel v2 còn nguyên sau migration.

Ngày 26/11/2026, bổ sung guard revision trong cùng transaction ghi brief/source/target: dao kiểm channelId, platform, connectionRevision, trạng thái và hạn hiệu lực trước insert. Ca thiết bị dùng revision cũ xác nhận 0 brief/source/target; revision hiện hành lưu đủ. 4/4 ca BriefMigrationInstrumentedTest trên AVD API 36 đạt.

Ngày 27/11/2026: đồng bộ `main` sau khi SP-022 và pipeline AI được merge. Theo review của Nghị, form lưu `channelId` và revision khi xoay màn hình, chỉ khôi phục lựa chọn còn hợp lệ trong registry; khóa các ô nhập và kênh trong lúc ghi bất đồng bộ; fixture Activity của SP-022 nhận `BriefRepository` từ cùng Room database. Android assemble, 285/285 unit test và lint đạt. Trên AVD API 36, 3/3 ca `BriefFormInstrumentedTest` đạt, gồm xoay trước khi lưu và ghi chậm; 4/4 ca `BriefMigrationInstrumentedTest` đạt trong lượt trước. Package QA `.sp022review` chạy 2/2 ca `MainActivityConnectionDeviceTest` bằng Gradle init script tạm thời.
