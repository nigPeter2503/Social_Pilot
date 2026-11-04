# Taskbrief Tuần 1 — Nguyễn Đức Đạt (TV2)

Đồng bộ ngày 04/11/2026 (Asia/Bangkok) từ GitHub Issues và [Project #5](https://github.com/users/nigPeter2503/projects/5). Các ticket dưới đây đã tạo, đã assign cho `dat532006`, hiện ở **Todo (Chưa làm)**. [Bản tổng hợp Tuần 1](week-1-taskbrief.md).

- **Trách nhiệm:** F2/F8, nền tảng AI, schema Room và migration chung.
- **Task chính:** [SP-012](https://github.com/nigPeter2503/Social_Pilot/issues/14), [SP-013](https://github.com/nigPeter2503/Social_Pilot/issues/15), [SP-014](https://github.com/nigPeter2503/Social_Pilot/issues/16).
- **Công sức tham khảo:** 20 giờ owner + 4,5 giờ review + 2 giờ phối hợp = **26,5 giờ**. Các phần tích hợp, kiểm thử và tài liệu nằm trong giờ owner.
- **Cách làm đã chốt:** giữ đầu ra và mốc bàn giao; mỗi thành viên tự phân bổ thời gian. Giờ ước lượng không phải giới hạn và không chờ xác nhận lịch bận/giờ tăng.
- **Lịch:** dùng ngày làm việc tương đối trong Tuần 1 theo issue, không tính lại từ ngày nhận task; xem [mốc và rủi ro](week-1-taskbrief.md#11-đánh-giá-và-rủi-ro-còn-lại). Nhận đầu vào sớm, hoàn tất review và tích hợp trước demo cuối tuần.

## Việc cần review

| Issue | Owner | Mốc bàn giao | Nội dung |
|---|---|---|---|
| [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11) | Minh (`nigPeter2503`) | Hết ngày làm việc 2 | Chốt hợp đồng OAuth, capability và kết quả đăng |
| [SP-010](https://github.com/nigPeter2503/Social_Pilot/issues/12) | Minh (`nigPeter2503`) | Hết ngày làm việc 3 | Kiểm chứng Facebook OAuth callback và một bài đăng thật |
| [SP-011](https://github.com/nigPeter2503/Social_Pilot/issues/13) | Minh (`nigPeter2503`) | Hết ngày làm việc 4 | Kiểm chứng LinkedIn OAuth, quyền đăng và refresh |
| [SP-016](https://github.com/nigPeter2503/Social_Pilot/issues/18) | Thiện (`PhuocThien06`) | Hết ngày làm việc 3 | Chốt wireframe và hợp đồng brief, phiên bản, duyệt |
| [SP-017](https://github.com/nigPeter2503/Social_Pilot/issues/19) | Thiện (`PhuocThien06`) | Trước demo cuối Tuần 1 | Nối bản mẫu UI, Room và WorkManager có kiểm thử vòng đời |
| [SP-019](https://github.com/nigPeter2503/Social_Pilot/issues/21) | Nghị (`bob35306`) | Hết ngày làm việc 4 | Kiểm chứng LinkedIn comment, reply, follower và số liệu |

Dành 0,75 giờ/người/task cho review ban đầu; chủ task gửi hợp đồng và ca thử sớm. Bố trí review theo mốc bàn giao, phản hồi các nhận xét bắt buộc trước merge.

<a id="sp-012"></a>

## SP-012 — Chốt quy ước Room, quyền sở hữu dữ liệu và thứ tự migration

- **Issue:** [SP-012](https://github.com/nigPeter2503/Social_Pilot/issues/14) — #14.
- **Issue cha:** [SP-007](https://github.com/nigPeter2503/Social_Pilot/issues/9).
- **Project:** [Social Pilot - Development Board 2024_31_MD_2](https://github.com/users/nigPeter2503/projects/5).
- **Trạng thái đã kiểm chứng:** Issue OPEN; Project **Todo (Chưa làm)**; đã assign cho `dat532006`.
- **Yêu cầu:** INFRA, F9.B2.
- **Owner:** TV2 — Nguyễn Đức Đạt (`dat532006`).
- **Hai reviewer khi có PR:** Ngô Gia Minh (`nigPeter2503`); Đỗ Phước Thiện (`PhuocThien06`).
- **Ưu tiên:** Cao, ghi trong issue; board hiện không có field Priority riêng.
- **Mốc bàn giao:** Hết ngày làm việc 2, tính từ đầu Tuần 1.
- **Ước lượng owner:** 5 giờ gồm thiết kế/triển khai, tích hợp, kiểm thử và tài liệu. Review thêm 0,75 giờ/người/PR, tính riêng.
- **Branch dự kiến:** `docs/sp-012-room-schema-contract` (chưa tạo), base `main`.
- **PR:** chưa tạo; liên kết issue ở trên và yêu cầu hai reviewer đã ghi.

### Mục tiêu, phạm vi và khu vực

Thiết kế ownership, ID, quan hệ, index/unique key, null, thời gian/múi giờ, tham chiếu ảnh/bí mật; kế hoạch migration từ database v1. Không viết hộ entity/DAO của mọi module và không nâng version database chỉ để thử thiết kế.

Khu vực dự kiến: docs/architecture.md, mô hình của data/local; AppDatabase.java chỉ đọc ở task này trừ thay đổi thật đã được thống nhất riêng.

Các đường dẫn package Android rút gọn tính từ `android/app/src/main/java/com/socialpilot/app/`; backend từ `backend/src/main/java/com/socialpilot/oauth/`. Vị trí ghi “dự kiến” chưa tồn tại, sẽ chốt khi triển khai. API AI gọi từ thiết bị; backend chỉ giữ client secret phục vụ OAuth.

### Đầu vào, phụ thuộc và điều kiện mở chặn

Owner gửi đề xuất dữ liệu sơ bộ ngày đầu Tuần 1. Chốt quy ước nền mà không chờ các DTO chi tiết [SP-016](https://github.com/nigPeter2503/Social_Pilot/issues/18)/[SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22); các DTO sau tuân theo quy ước này để tránh vòng phụ thuộc.

### Đầu ra và tiêu chí đạt

- [ ] Có sơ đồ dữ liệu tối thiểu brief/version/channel/job/usage/comment/snapshot và bảng owner; secret chỉ là tham chiếu, không plaintext.
- [ ] Có ví dụ xóa một kênh vẫn giữ draft dùng chung; unique key cho publish/comment/snapshot và cách biểu diễn thiếu số liệu.
- [ ] Có danh sách migration dự kiến từ v1, thứ tự tích hợp, ca kiểm tra dữ liệu cũ; một thời điểm một task sửa database/version/migration chung.

### Kiểm chứng và bằng chứng cần nộp

Walk-through dữ liệu cũ và các ca duplicate/null/delete với hai reviewer; đọc lại liên kết và diff tài liệu. Chưa có test migration chạy thì không ghi đã bảo toàn dữ liệu.

Nộp revision/PR khi có, lệnh/ca thử, môi trường, ngày thử, kết quả và phần chưa thử. Log/ảnh không chứa key/token. Prototype, mock và API thật phải có nhãn riêng. Bằng chứng dự kiến đặt trong hồ sơ ticket/PR hoặc tài liệu kiểm chứng được thống nhất; cập nhật bằng chứng thực thi vào issue/PR tương ứng; trạng thái Todo chưa chứng minh tiêu chí đã đạt.

### Bàn giao và xử lý blocker

Ngày làm việc 2: tất cả owner nhận quy ước; từng owner tự chuẩn bị entity/DAO và test sau này. Đạt xác nhận thứ tự tích hợp và số version khi có thay đổi thực tế.

Nếu nhiều người cùng muốn sửa AppDatabase, xếp hàng tích hợp; không giải quyết bằng xóa DB hoặc migration phá hủy.

Khi bàn giao ghi phần đã làm, kiểm thử đã chạy, phần còn thiếu, contract revision, người nhận và giờ còn lại. Nếu có nguy cơ trễ mốc, cập nhật việc còn lại và phụ thuộc trên issue; chỉ tạo task mới khi đầu ra độc lập, không đánh dấu tiêu chí chưa đạt thành đạt.

<a id="sp-013"></a>

## SP-013 — Chốt hợp đồng AI và đo tác vụ thật trên hai provider

- **Issue:** [SP-013](https://github.com/nigPeter2503/Social_Pilot/issues/15) — #15.
- **Issue cha:** [SP-003](https://github.com/nigPeter2503/Social_Pilot/issues/5).
- **Project:** [Social Pilot - Development Board 2024_31_MD_2](https://github.com/users/nigPeter2503/projects/5).
- **Trạng thái đã kiểm chứng:** Issue OPEN; Project **Todo (Chưa làm)**; đã assign cho `dat532006`.
- **Yêu cầu:** F8.B1, F8.B3, F8.B4, INFRA.
- **Owner:** TV2 — Nguyễn Đức Đạt (`dat532006`).
- **Hai reviewer khi có PR:** Ngô Gia Minh (`nigPeter2503`); Tô Lãng Nghị (`bob35306`).
- **Ưu tiên:** Cao, ghi trong issue; board hiện không có field Priority riêng.
- **Mốc bàn giao:** Hết ngày làm việc 3, tính từ đầu Tuần 1.
- **Ước lượng owner:** 7 giờ gồm thiết kế/triển khai, tích hợp, kiểm thử và tài liệu. Review thêm 0,75 giờ/người/PR, tính riêng.
- **Branch dự kiến:** `feat/sp-013-ai-provider-spike` (chưa tạo), base `main`.
- **PR:** chưa tạo; liên kết issue ở trên và yêu cầu hai reviewer đã ghi.

### Mục tiêu, phạm vi và khu vực

Một tác vụ văn bản thật trên OpenAI và Gemini; chốt event/cancel/error/usage/model capability, API ảnh hoặc template dự kiến và reserve/settle. Đo chi phí mẫu. Chưa xây UI BYOK, Keystore hay budget production.

Khu vực dự kiến: domain/contract/AiProvider.java, BudgetManager.java; adapter AI dự kiến dưới data/domain phù hợp; tài liệu cấu hình và mẫu phản hồi.

Các đường dẫn package Android rút gọn tính từ `android/app/src/main/java/com/socialpilot/app/`; backend từ `backend/src/main/java/com/socialpilot/oauth/`. Vị trí ghi “dự kiến” chưa tồn tại, sẽ chốt khi triển khai. API AI gọi từ thiết bị; backend chỉ giữ client secret phục vụ OAuth.

### Đầu vào, phụ thuộc và điều kiện mở chặn

Có key và hạn mức thử đã được nhóm chốt; nhận quy ước ID/chi phí [SP-012](https://github.com/nigPeter2503/Social_Pilot/issues/14). Chốt chữ ký event sớm ngày làm việc 2 cho [SP-014](https://github.com/nigPeter2503/Social_Pilot/issues/16); không đợi API thật của cả hai provider mới làm mock.

### Đầu ra và tiêu chí đạt

- [ ] Hai provider đều trả kết quả văn bản thật cho ca thử, lưu provider/model/usage hoặc nhãn thiếu usage, chi phí ước tính và thời điểm.
- [ ] Hợp đồng có session/channel/step/request ID, thứ tự event, lỗi, cancel và capability ảnh; người dùng hợp đồng có ví dụ success/error/cancel.
- [ ] Quy định reserve trước gọi, settle sau gọi, chi phí chung qua retry/fallback; chưa có implementation budget phải ghi rõ.
- [ ] Key thử không lưu vào source/Room/log/backup; chỉ gửi thẳng tới đúng provider, không qua backend nhóm.

### Kiểm chứng và bằng chứng cần nộp

Lượt gọi thật tối thiểu có chứng cứ đã che bí mật; unit/MockWebServer cho chuẩn hóa phản hồi. Chốt giới hạn chi trước thử; ngân sách dưới 1 triệu toàn dự án không phải quyền tự chi toàn bộ.

Nộp revision/PR khi có, lệnh/ca thử, môi trường, ngày thử, kết quả và phần chưa thử. Log/ảnh không chứa key/token. Prototype, mock và API thật phải có nhãn riêng. Bằng chứng dự kiến đặt trong hồ sơ ticket/PR hoặc tài liệu kiểm chứng được thống nhất; cập nhật bằng chứng thực thi vào issue/PR tương ứng; trạng thái Todo chưa chứng minh tiêu chí đã đạt.

### Bàn giao và xử lý blocker

Ngày làm việc 2 chữ ký event sơ bộ cho Thiện/Nghị; ngày làm việc 3 contract đã review, response mẫu và số liệu chi phí. Mọi module AI dùng cùng hợp đồng.

Không có key/quota: tiếp tục contract/mock nhưng F8.B1 chưa được kiểm chứng. Không chuyển sang một provider duy nhất rồi ghi đạt hai provider.

Khi bàn giao ghi phần đã làm, kiểm thử đã chạy, phần còn thiếu, contract revision, người nhận và giờ còn lại. Nếu có nguy cơ trễ mốc, cập nhật việc còn lại và phụ thuộc trên issue; chỉ tạo task mới khi đầu ra độc lập, không đánh dấu tiêu chí chưa đạt thành đạt.

<a id="sp-014"></a>

## SP-014 — Kiểm chứng hai nhánh stream và hủy độc lập trên Java

- **Issue:** [SP-014](https://github.com/nigPeter2503/Social_Pilot/issues/16) — #16.
- **Issue cha:** [SP-003](https://github.com/nigPeter2503/Social_Pilot/issues/5).
- **Project:** [Social Pilot - Development Board 2024_31_MD_2](https://github.com/users/nigPeter2503/projects/5).
- **Trạng thái đã kiểm chứng:** Issue OPEN; Project **Todo (Chưa làm)**; đã assign cho `dat532006`.
- **Yêu cầu:** F2.B1, F2.B4, F2.B5.
- **Owner:** TV2 — Nguyễn Đức Đạt (`dat532006`).
- **Hai reviewer khi có PR:** Đỗ Phước Thiện (`PhuocThien06`); Tô Lãng Nghị (`bob35306`).
- **Ưu tiên:** Cao, ghi trong issue; board hiện không có field Priority riêng.
- **Mốc bàn giao:** Hết ngày làm việc 4, tính từ đầu Tuần 1.
- **Ước lượng owner:** 8 giờ gồm thiết kế/triển khai, tích hợp, kiểm thử và tài liệu. Review thêm 0,75 giờ/người/PR, tính riêng.
- **Branch dự kiến:** `feat/sp-014-dual-stream-cancellation` (chưa tạo), base `main`.
- **PR:** chưa tạo; liên kết issue ở trên và yêu cầu hai reviewer đã ghi.

### Mục tiêu, phạm vi và khu vực

Hai nhánh qua ExecutorService với HTTP Call có thể hủy; event có định danh, lỗi riêng nhánh, tích lũy text đúng thứ tự. Không xây đủ sáu bước agent, sinh ảnh, nhật ký hoặc checkpoint production.

Khu vực dự kiến: domain/contract/AgentOrchestrator.java, Cancelable.java; implementation AI và android/app/src/test; không sửa Activity do [SP-017](https://github.com/nigPeter2503/Social_Pilot/issues/19) sở hữu.

Các đường dẫn package Android rút gọn tính từ `android/app/src/main/java/com/socialpilot/app/`; backend từ `backend/src/main/java/com/socialpilot/oauth/`. Vị trí ghi “dự kiến” chưa tồn tại, sẽ chốt khi triển khai. API AI gọi từ thiết bị; backend chỉ giữ client secret phục vụ OAuth.

### Đầu vào, phụ thuộc và điều kiện mở chặn

Chữ ký event [SP-013](https://github.com/nigPeter2503/Social_Pilot/issues/15) đã review; có thể dùng MockWebServer khi chờ provider thật. Kết quả API thật [SP-013](https://github.com/nigPeter2503/Social_Pilot/issues/15) là bằng chứng khác, không phải tiền đề của ca hủy mô phỏng.

### Đầu ra và tiêu chí đạt

- [ ] Hai stream có thời gian thực thi chồng lấp; nội dung không mất/đảo thứ tự khi nhiều chunk tới gần nhau.
- [ ] Hủy A dừng cả Future và HTTP Call; callback muộn của A không ghi lại kết quả, B vẫn hoàn tất.
- [ ] Lỗi A không mất B; chạy lại riêng A tạo request ID mới; không tự gọi lại sau process death.
- [ ] Bàn giao runtime đủ sớm cho [SP-017](https://github.com/nigPeter2503/Social_Pilot/issues/19), có cách inject fake và danh sách ca chưa có implementation.

### Kiểm chứng và bằng chứng cần nộp

JUnit + MockWebServer: chunk nhỏ, timeout, disconnect, hủy giữa stream và late callback. Đây là prototype dùng phản hồi mẫu; không đánh dấu toàn F2 đạt.

Nộp revision/PR khi có, lệnh/ca thử, môi trường, ngày thử, kết quả và phần chưa thử. Log/ảnh không chứa key/token. Prototype, mock và API thật phải có nhãn riêng. Bằng chứng dự kiến đặt trong hồ sơ ticket/PR hoặc tài liệu kiểm chứng được thống nhất; cập nhật bằng chứng thực thi vào issue/PR tương ứng; trạng thái Todo chưa chứng minh tiêu chí đã đạt.

### Bàn giao và xử lý blocker

Bản runtime sớm ngày làm việc 3; phiên bản đã review trước cuối ngày làm việc 4 cho Thiện nối UI. Đạt tự sửa lỗi parser/runtime phát hiện khi tích hợp.

Nếu chưa xong ngày làm việc 4, [SP-017](https://github.com/nigPeter2503/Social_Pilot/issues/19) vẫn làm Room/Worker; phần vòng đời stream còn thiếu được báo ngay, không ghép vội runtime chưa kiểm tra.

Khi bàn giao ghi phần đã làm, kiểm thử đã chạy, phần còn thiếu, contract revision, người nhận và giờ còn lại. Nếu có nguy cơ trễ mốc, cập nhật việc còn lại và phụ thuộc trên issue; chỉ tạo task mới khi đầu ra độc lập, không đánh dấu tiêu chí chưa đạt thành đạt.

## Theo dõi và bàn giao

- Issue và card đã tồn tại; dùng mã SP và URL ở từng task để nhận việc, cập nhật tiến độ, blocker và bằng chứng.
- Chuyển card từ Todo sang In Progress khi bắt đầu. Hai reviewer được ghi trong issue; gửi yêu cầu review trên GitHub khi có PR.
- Dùng tên branch cụ thể ở từng task; chỉ tạo khi nhận task, từ `main` mới nhất. Quy tắc branch/commit/PR và ánh xạ trạng thái tại [bản tổng hợp](week-1-taskbrief.md#10-quy-ước-triển-khai-tuần-1). Hai thành viên thật khác tác giả approve trước squash merge theo CONTRIBUTING.
- Mỗi owner làm VI/EN, loading/rỗng/lỗi/thiếu quyền/offline, tích hợp và tài liệu phần mình; phối hợp người dùng hợp đồng chung trước khi sửa.
- Android: tại android chạy `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Backend: tại backend chạy `.\gradlew.bat test bootJar`. Hợp đồng xuyên hai phía kiểm tra cả hai; UI/vòng đời/job nền thêm emulator/thiết bị, schema thêm test migration.
- Chỉ tài liệu: kiểm tra nội dung/liên kết và `git diff --check`; không cần build ứng dụng.
- Tham gia [SP-021](https://github.com/nigPeter2503/Social_Pilot/issues/23): hai họp ngắn, cung cấp bằng chứng/ước lượng còn lại và demo cuối tuần. Hai giờ phối hợp đã được tính trong tổng công sức.

## Kết quả đồng bộ

Đã đối chiếu mã SP, URL issue, assignee, hai reviewer, requirement, ước lượng và mốc bàn giao. Các issue trong brief đang OPEN/Todo; việc đã tạo và assign ticket không đồng nghĩa đã hoàn thành triển khai hoặc nghiệm thu.
