# SP-035 — Form brief lưu trên thiết bị

## Hợp đồng dữ liệu

`AppDatabase` nâng v1→v2 bằng migration tường minh. Bảng `drafts` v1 giữ nguyên ID `long`, `topic` nullable, `createdAt` và thứ tự đọc. Brief mới có UUID `briefId`, topic, goal, audience, tone, `contentLanguage` (`vi`/`en`), thời điểm UTC epoch ms. `legacyDraftId` nullable và unique dành cho đường nhập draft v1 về sau; phiên bản này **không tự chuyển** draft cũ sang brief và không tự gán kênh hoặc approval. Màn hình vẫn hiển thị danh sách topic v1 riêng.

`brief_sources` lưu từng nguồn TEXT/URL theo thứ tự; `brief_targets` lưu `channelId` nội bộ và platform, unique theo `(briefId, channelId)` và `(briefId, platform)`. Hai bảng con có FK `ON DELETE CASCADE` tới brief. Repository ghi brief, sources và targets trong một transaction trên executor, đọc lại thành `Brief` đã validate trước khi chuyển cho consumer AI. Form tạo snapshot bất biến tại lúc bấm lưu, không đọc lại View từ callback.

Form cho lưu brief offline khi chưa có kênh. Mỗi field bắt buộc có lỗi tại đúng ô bằng VI/EN; ngôn ngữ nội dung độc lập với locale của UI. Nguồn text có thể để trống hoặc nhập một đoạn; bật loại URL để nhập nhiều URL, mỗi dòng một URL http/https không có userinfo. Danh sách gần đây đọc lại topic, goal, audience, tone, ngôn ngữ, sources và nền tảng đã chọn qua một truy vấn Room có transaction. Không có nút tạo nội dung/đăng bài từ màn hình này.

## Bàn giao và giới hạn

Hợp đồng cho Đạt: `Brief` trong `domain/editor` nhận `briefId`, topic, goal, audience, tone, language, danh sách tối đa một target Facebook và một target LinkedIn, cùng sources. Chỉ `BriefRepository.loadValidated(briefId)` cho payload hợp lệ; draft chưa có target không thể thành payload. ID kênh phải đến từ registry do Minh sở hữu theo SP-009/SP-023; bản `main` hiện chưa có registry đó. Màn hình hiển thị rõ chưa có kênh khả dụng và không tự dùng Page ID/tên Page làm `channelId`. Việc chọn target thật qua UI chờ API/registry này; đây là phần chưa đạt của SP-035, cần review cùng Minh/Đạt trước khi đóng issue.

## Kiểm chứng

- JUnit kiểm field rỗng, target trùng platform và snapshot brief hợp lệ.
- Instrumentation dùng `MigrationTestHelper` seed v1: ID 7/11 cùng topic và thời gian, ID 42 topic null, ID 50 từng tạo rồi xóa. Kiểm thứ tự, giá trị, ID mới >50 và DAO sau mở lại.
- Instrumentation lưu brief hai target và hai source, đóng/mở database, đọc đúng field/ID/thứ tự. Test UI lưu rồi xoay không nhân bản brief.
- Test trên AVD API 23 và API 36; kết quả chạy được ghi trong PR theo commit cụ thể.

Ngày 19/11/2026, Windows/JDK 21/SDK 36: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest` đạt trên AVD API 23; 63/63 JVM test, 5/5 instrumentation, lint 0 lỗi/16 cảnh báo. Cùng bộ instrumentation đạt 5/5 trên AVD API 36. Sau khi thêm transaction đọc snapshot, hai test migration/reopen đạt trên API 36; sau khi thêm danh sách chi tiết, ca UI lưu/xoay/hiển thị field đạt lại trên API 23. [Ảnh form tiếng Anh trên API 36](evidence/sp-035-api36-form-en.png). Chưa thử trên điện thoại thật; thao tác offline qua UI dump trên emulator không lấy được root node nên chỉ xác nhận đường lưu local bằng instrumentation, chưa ghi nhận kiểm tra trực quan offline.
