# Taskbrief Tuần 1 — Ngô Gia Minh (TV1)

Đồng bộ ngày 04/11/2026 (Asia/Bangkok) từ GitHub Issues và [Project #5](https://github.com/users/nigPeter2503/projects/5). Các ticket dưới đây đã tạo, đã assign cho `nigPeter2503`, ở **Todo (Chưa làm)** tại thời điểm 04/11; SP-009 có cập nhật ngày 09/11 bên dưới. [Bản tổng hợp Tuần 1](week-1-taskbrief.md).

- **Trách nhiệm:** F1/F5, OAuth/backend, hợp đồng social và điều phối tích hợp.
- **Task chính:** [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11), [SP-010](https://github.com/nigPeter2503/Social_Pilot/issues/12), [SP-011](https://github.com/nigPeter2503/Social_Pilot/issues/13).
- **Công sức tham khảo:** 22 giờ owner + 4,5 giờ review + 2 giờ phối hợp = **28,5 giờ**. Các phần tích hợp, kiểm thử và tài liệu nằm trong giờ owner.
- **Cách làm đã chốt:** giữ đầu ra và mốc bàn giao; mỗi thành viên tự phân bổ thời gian. Giờ ước lượng không phải giới hạn và không chờ xác nhận lịch bận/giờ tăng.
- **Lịch:** dùng ngày làm việc tương đối trong Tuần 1 theo issue, không tính lại từ ngày nhận task; xem [mốc và rủi ro](week-1-taskbrief.md#11-đánh-giá-và-rủi-ro-còn-lại). Nhận đầu vào sớm, hoàn tất review và tích hợp trước demo cuối tuần.

## Việc cần review

| Issue | Owner | Mốc bàn giao | Nội dung |
|---|---|---|---|
| [SP-012](https://github.com/nigPeter2503/Social_Pilot/issues/14) | Đạt (`dat532006`) | Hết ngày làm việc 2 | Chốt quy ước Room, quyền sở hữu dữ liệu và thứ tự migration |
| [SP-013](https://github.com/nigPeter2503/Social_Pilot/issues/15) | Đạt (`dat532006`) | Hết ngày làm việc 3 | Chốt hợp đồng AI và đo tác vụ thật trên hai provider |
| [SP-015](https://github.com/nigPeter2503/Social_Pilot/issues/17) | Thiện (`PhuocThien06`) | Hết ngày làm việc 2 | Xác nhận baseline build, CI và thiết bị demo |
| [SP-016](https://github.com/nigPeter2503/Social_Pilot/issues/18) | Thiện (`PhuocThien06`) | Hết ngày làm việc 3 | Chốt wireframe và hợp đồng brief, phiên bản, duyệt |
| [SP-018](https://github.com/nigPeter2503/Social_Pilot/issues/20) | Nghị (`bob35306`) | Hết ngày làm việc 3 | Kiểm chứng Facebook comment, reply và số liệu thật |
| [SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22) | Nghị (`bob35306`) | Hết ngày làm việc 3 | Chốt hợp đồng comment, snapshot, công thức và dashboard |

Dành 0,75 giờ/người/task cho review ban đầu; chủ task gửi hợp đồng và ca thử sớm. Bố trí review theo mốc bàn giao, phản hồi các nhận xét bắt buộc trước merge.

<a id="sp-009"></a>

## SP-009 — Chốt hợp đồng OAuth, capability và kết quả đăng

- **Issue:** [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11) — #11.
- **Issue cha:** [SP-002](https://github.com/nigPeter2503/Social_Pilot/issues/4).
- **Project:** [Social Pilot - Development Board 2024_31_MD_2](https://github.com/users/nigPeter2503/projects/5).
- **Cập nhật 10/11:** PR #66 đã push tại head `ed7ed291e50bffb9de344823dfa91dabe2dd9018`; CI backend và Android của head này đều success theo kết quả reviewer. Chưa ghi nhận approval chính thức tại lần review lại; cần hai approval theo CONTRIBUTING. Issue/Project chưa xác minh; trạng thái OPEN/Todo ngày 04/11 là lịch sử.
- **Yêu cầu:** INFRA, F1.B1, F1.B3, F5.B1.
- **Owner:** TV1 — Ngô Gia Minh (`nigPeter2503`).
- **Hai reviewer khi có PR:** Nguyễn Đức Đạt (`dat532006`); Tô Lãng Nghị (`bob35306`).
- **Ưu tiên:** Cao, ghi trong issue; board hiện không có field Priority riêng.
- **Mốc bàn giao:** Hết ngày làm việc 2, tính từ đầu Tuần 1.
- **Ước lượng owner:** 6 giờ gồm thiết kế/triển khai, tích hợp, kiểm thử và tài liệu. Review thêm 0,75 giờ/người/PR, tính riêng.
- **Branch hiện tại:** `feat/sp-009-social-contract`; head đã push `ed7ed291e50bffb9de344823dfa91dabe2dd9018`.
- **PR:** [#66](https://github.com/nigPeter2503/Social_Pilot/pull/66). CI: [backend](https://github.com/nigPeter2503/Social_Pilot/actions/runs/42582986127/job/129045943045), [Android](https://github.com/nigPeter2503/Social_Pilot/actions/runs/42582986127/job/129045943029), đều success theo reviewer. Xem [hồ sơ kiểm chứng](../../sp-009-verification.md). N1 còn mở; nếu merge đóng issue #11 trước khi chốt chính sách, chuyển N1 sang ticket còn mở, giữ owner và liên kết tại §7 social contract. Chưa đánh dấu Hoàn tất.
- **Đề xuất N1 chờ duyệt:** xóa dữ liệu kênh và job chưa gửi khi disconnect; chỉ giữ tombstone cục bộ tối thiểu cho request SUCCESS/PUBLISHED_URL_PENDING/đang gửi/UNKNOWN tối đa 30 ngày. Giữ SUCCESS đến hết TTL; chỉ xóa sớm khi xác nhận request chưa tạo bài hoặc người dùng yêu cầu sau khi được cảnh báo mất dedupe. Reconnect tạo revision mới, không tự khôi phục/retry; Đạt/Nghị cần xác nhận ngoại lệ này có phù hợp F1.B4 không. Chi tiết tại [§7 social contract](../../social-contract.md).

### Mục tiêu, phạm vi và khu vực

Định nghĩa DTO kênh/capability, token reference, lỗi chuẩn hóa, approvedVersionId, kết quả thành công/lỗi/chưa rõ; vòng đời hủy/xóa theo kênh; callback OAuth bàn giao một lần có TTL. Chỉ chốt hợp đồng và ví dụ, chưa xây adapter production.

Khu vực dự kiến: Android domain/contract/SocialConnector.java, PublishQueue.java; DTO backend; docs/architecture.md. Minh điều phối file social chung; các module không tự mở rộng khác nhau.

Các đường dẫn package Android rút gọn tính từ `android/app/src/main/java/com/socialpilot/app/`; backend từ `backend/src/main/java/com/socialpilot/oauth/`. Vị trí ghi “dự kiến” chưa tồn tại, sẽ chốt khi triển khai. API AI gọi từ thiết bị; backend chỉ giữ client secret phục vụ OAuth.

### Đầu vào, phụ thuộc và điều kiện mở chặn

Không cần quyền API để bắt đầu. Nhận nhu cầu comment/số liệu sơ bộ từ Nghị và ID phiên bản từ Thiện từ đầu Tuần 1; không chờ toàn bộ [SP-016](https://github.com/nigPeter2503/Social_Pilot/issues/18)/[SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22). Đóng bản hợp đồng sau review ngày làm việc 2.

### Đầu ra và tiêu chí đạt

- [x] Có mẫu đầu vào/đầu ra cho đăng thành công với postId/URL, thiếu quyền và timeout chưa rõ kết quả; trường chưa rõ không được biến thành thành công.
- [ ] Chốt state/ràng buộc phiên/redirect, PKCE khi hỗ trợ, mã bàn giao dùng một lần có TTL và quy tắc không đưa token vào URL.
- [ ] Ngắt kênh phải hủy job, bỏ callback muộn, giữ dữ liệu kênh khác và bản nháp dùng chung; sơ đồ ownership được hai reviewer kiểm tra.

### Kiểm chứng và bằng chứng cần nộp

Review DTO theo ca giả lập; nếu sửa Java, build cả phía Android/backend bị ảnh hưởng. Ví dụ hợp đồng chỉ là dữ liệu mẫu, không chứng minh API thật.

Nộp revision/PR khi có, lệnh/ca thử, môi trường, ngày thử, kết quả và phần chưa thử. Log/ảnh không chứa key/token. Prototype, mock và API thật phải có nhãn riêng. Bằng chứng dự kiến đặt trong hồ sơ ticket/PR hoặc tài liệu kiểm chứng được thống nhất; cập nhật bằng chứng thực thi vào issue/PR tương ứng; trạng thái Todo chưa chứng minh tiêu chí đã đạt.

### Bàn giao và xử lý blocker

Ngày làm việc 2: Đạt nhận định danh phiên và bí mật; Thiện nhận hợp đồng approved version; Nghị nhận auth/capability/lỗi. Bàn giao schema DTO, ca lỗi và revision đã review.

Nếu chưa thống nhất một trường, ghi owner quyết định và hạn trong ngày làm việc 2; không cho hai adapter tự tạo định nghĩa trái nhau.

Khi bàn giao ghi phần đã làm, kiểm thử đã chạy, phần còn thiếu, contract revision, người nhận và giờ còn lại. Nếu có nguy cơ trễ mốc, cập nhật việc còn lại và phụ thuộc trên issue; chỉ tạo task mới khi đầu ra độc lập, không đánh dấu tiêu chí chưa đạt thành đạt.

<a id="sp-010"></a>

## SP-010 — Kiểm chứng Facebook OAuth callback và một bài đăng thật

- **Issue:** [SP-010](https://github.com/nigPeter2503/Social_Pilot/issues/12) — #12.
- **Issue cha:** [SP-002](https://github.com/nigPeter2503/Social_Pilot/issues/4).
- **Project:** [Social Pilot - Development Board 2024_31_MD_2](https://github.com/users/nigPeter2503/projects/5).
- **Trạng thái đã kiểm chứng trong lượt sửa 11–12/11:** Issue OPEN; Project **In Progress (Đang làm)**; đã assign cho `nigPeter2503`. Chưa Hoàn tất; mốc W1 ban đầu đã trễ.
- **Yêu cầu:** F1.B1, F1.B3, F5.B1, F5.B5.
- **Owner:** TV1 — Ngô Gia Minh (`nigPeter2503`).
- **Hai reviewer khi có PR:** Nguyễn Đức Đạt (`dat532006`); Đỗ Phước Thiện (`PhuocThien06`).
- **Ưu tiên:** Cao, ghi trong issue; board hiện không có field Priority riêng.
- **Mốc bàn giao:** Hết ngày làm việc 3, tính từ đầu Tuần 1.
- **Ước lượng owner:** 8 giờ gồm thiết kế/triển khai, tích hợp, kiểm thử và tài liệu. Review thêm 0,75 giờ/người/PR, tính riêng.
- **Branch:** `feat/sp-010-facebook-oauth-spike`; đã tích hợp `origin/main` sau khi SP-009 / PR #66 merge và giải conflict trong checkout sửa.
- **PR:** [#74](https://github.com/nigPeter2503/Social_Pilot/pull/74). Bản sửa bốn feedback có 39 backend test và 27 Android unit test đạt; build/lint và APK test đạt. Ngày 12/11, 2 test Keystore đạt trên Samsung SM-G990E / Android 16 / API 36 tại source `a6c8a9d`; đã khôi phục APK ban đầu bằng install -r. API 23 và callback cold-start chưa thử. Hai reviewer cần xác nhận lại head mới; xem [hồ sơ kiểm chứng](../../drafts/sp-010-implementation-status.md).

### Mục tiêu, phạm vi và khu vực

Chuẩn bị Meta app/Page và redirect thử, bản mẫu callback xuyên backend/Android, thử đăng một bài trong kênh thử được xác nhận. Ghi quyền, refresh, ảnh/alt text và giới hạn quan sát được. Chưa có My Channels, queue hay token lifecycle production đầy đủ.

Khu vực dự kiến: backend/.../oauth/api và lớp OAuth mới; Android domain/social (dự kiến), manifest callback do Thiện phối hợp; tài liệu cấu hình/bằng chứng.

Các đường dẫn package Android rút gọn tính từ `android/app/src/main/java/com/socialpilot/app/`; backend từ `backend/src/main/java/com/socialpilot/oauth/`. Vị trí ghi “dự kiến” chưa tồn tại, sẽ chốt khi triển khai. API AI gọi từ thiết bị; backend chỉ giữ client secret phục vụ OAuth.

### Đầu vào, phụ thuộc và điều kiện mở chặn

[SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11) đã chốt phần callback; Meta app, Page và quyền quản trị, token/secret, URL HTTPS. Chuẩn bị cấu hình có thể song song với review [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11). Token thử hợp lệ có sẵn được dùng để mở chặn Nghị, không cần chờ app UI hoàn chỉnh.

### Đầu ra và tiêu chí đạt

- [x] Trình duyệt hệ thống → backend → app trả đúng định danh Page qua mã bàn giao; owner xác nhận kết nối trên điện thoại thật ngày 11/11/2026.
- [ ] Sai state, sai phiên, mã hết hạn và đổi mã lần hai bị từ chối; bí mật không có trong URL/log hoặc repo.
- [x] Bài Page thật có ID/URL đã được đọc lại qua Graph API Explorer và owner xác nhận permalink mở đúng bài; metadata Page token/mở rộng thủ công đã có bằng chứng riêng ngày 12/11, chưa đồng nghĩa app tự refresh hoặc đã thử token hết hạn/thu hồi.
- Ghi nhận bổ sung 12/11: owner xác nhận đã xem/duyệt nội dung, kiểm tra Page rồi chủ động gửi POST. [Ảnh bài và xác nhận lịch sử](../../evidence/sp-010-publish-approval-2026-11-12.md) được lưu riêng; phần duyệt trước dựa trên xác nhận của người thực hiện. Bằng chứng quyền/token được ghi trong mục tiếp theo; Issue #12, owner Minh vẫn theo dõi các điều kiện hoàn tất còn mở.
- Cập nhật tiếp 12/11: [lỗi quyền thật, metadata và mở rộng Page token thủ công](../../evidence/sp-010-permission-token-lifecycle-2026-11-12.md) đã có ảnh/kết quả riêng. Debugger sau thao tác ghi Page token hợp lệ, hạn token “Không bao giờ”, hạn truy cập dữ liệu vẫn riêng; GET nhận đúng Page. Chưa chứng minh app tự refresh hoặc xử lý token hết hạn/thu hồi. Các ghi chú NOT TESTED cũ về metadata/provider extension được thay bằng bằng chứng này; restart/cold-start và ảnh/alt text vẫn cần chốt theo phạm vi spike với reviewer.
- [ ] Có ghi nhận HTTPS, restart/cold start của môi trường thử và khả năng ảnh/alt text; không tuyên bố production-ready.

### Kiểm chứng và bằng chứng cần nộp

State/TTL/replay/race đã có test service với provider giả trong bản sửa review; không dùng kết quả này thay bằng chứng callback âm tính/lifecycle trên thiết bị. Callback OAuth cơ bản đã được owner xác nhận trên thiết bị thật trước bản sửa. Bài Page thật được tạo và đọc lại qua Graph API Explorer; không đồng nhất bằng chứng này với publish qua hàng đợi ứng dụng. Lỗi mô phỏng ghi tách khỏi thành công API thật.

Nộp revision/PR khi có, lệnh/ca thử, môi trường, ngày thử, kết quả và phần chưa thử. Log/ảnh không chứa key/token. Prototype, mock và API thật phải có nhãn riêng. Bằng chứng dự kiến đặt trong hồ sơ ticket/PR hoặc tài liệu kiểm chứng được thống nhất; cập nhật bằng chứng thực thi vào issue/PR tương ứng; trạng thái Todo chưa chứng minh tiêu chí đã đạt.

### Bàn giao và xử lý blocker

Chậm nhất ngày làm việc 3: Nghị nhận Page ID, post ID và cơ chế lấy token qua kênh riêng; Thiện nhận callback mẫu. Chỉ bàn giao log đã che bí mật trong repo.

Không có app/quyền sau bước kiểm kê: ghi blocker ngay, tiếp tục test callback bằng provider giả; yêu cầu đăng thật vẫn chưa đạt. Cập nhật 11/11/2026: callback trên điện thoại và một bài Page thật đã có bằng chứng; token lifecycle, restart/cold-start và ảnh/alt text còn mở.

Khi bàn giao ghi phần đã làm, kiểm thử đã chạy, phần còn thiếu, contract revision, người nhận và giờ còn lại. Nếu có nguy cơ trễ mốc, cập nhật việc còn lại và phụ thuộc trên issue; chỉ tạo task mới khi đầu ra độc lập, không đánh dấu tiêu chí chưa đạt thành đạt.

<a id="sp-011"></a>

## SP-011 — Kiểm chứng LinkedIn OAuth, quyền đăng và refresh

- **Issue:** [SP-011](https://github.com/nigPeter2503/Social_Pilot/issues/13) — #13.
- **Issue cha:** [SP-002](https://github.com/nigPeter2503/Social_Pilot/issues/4).
- **Project:** [Social Pilot - Development Board 2024_31_MD_2](https://github.com/users/nigPeter2503/projects/5).
- **Trạng thái đã kiểm chứng:** Issue OPEN; Project **Todo (Chưa làm)**; đã assign cho `nigPeter2503`.
- **Yêu cầu:** F1.B1, F1.B3, F5.B1, F5.B5.
- **Owner:** TV1 — Ngô Gia Minh (`nigPeter2503`).
- **Hai reviewer khi có PR:** Nguyễn Đức Đạt (`dat532006`); Tô Lãng Nghị (`bob35306`).
- **Ưu tiên:** Cao, ghi trong issue; board hiện không có field Priority riêng.
- **Mốc bàn giao:** Hết ngày làm việc 4, tính từ đầu Tuần 1.
- **Ước lượng owner:** 8 giờ gồm thiết kế/triển khai, tích hợp, kiểm thử và tài liệu. Review thêm 0,75 giờ/người/PR, tính riêng.
- **Branch dự kiến:** `feat/sp-011-linkedin-oauth-spike` (chưa tạo), base `main`.
- **PR:** chưa tạo; liên kết issue ở trên và yêu cầu hai reviewer đã ghi.

### Mục tiêu, phạm vi và khu vực

Chốt cá nhân/tổ chức theo tài khoản thực, kiểm kê app/product/quyền; dùng khung callback chung để đăng một bài thử. Xác minh refresh có được cấp và API version đang hỗ trợ. Chưa triển khai mọi API LinkedIn.

Khu vực dự kiến: Backend OAuth adapter, Android domain/social (dự kiến), phần cấu hình và bằng chứng LinkedIn.

Các đường dẫn package Android rút gọn tính từ `android/app/src/main/java/com/socialpilot/app/`; backend từ `backend/src/main/java/com/socialpilot/oauth/`. Vị trí ghi “dự kiến” chưa tồn tại, sẽ chốt khi triển khai. API AI gọi từ thiết bị; backend chỉ giữ client secret phục vụ OAuth.

### Đầu vào, phụ thuộc và điều kiện mở chặn

[SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11); phần callback chung đã kiểm thử của [SP-010](https://github.com/nigPeter2503/Social_Pilot/issues/12), không phụ thuộc Facebook được duyệt quyền. Cần LinkedIn app/tài khoản/quyền; token thử sẵn có có thể mở chặn [SP-019](https://github.com/nigPeter2503/Social_Pilot/issues/21) sớm.

### Đầu ra và tiêu chí đạt

- [ ] Bảng quyền ghi loại tài khoản, product, scope thực tế, API version, thời điểm thử và endpoint; không suy ra quyền comment/follower từ quyền đăng.
- [ ] OAuth nhận đúng tài khoản, bài thử có ID/URL thật; callback dùng cơ chế bảo vệ đã chốt.
- [ ] Refresh thử thành công khi được cấp; nếu không, ghi bị chặn/đạt một phần ở bằng chứng liên quan, không coi kết nối lại là refresh đạt.

### Kiểm chứng và bằng chứng cần nộp

Bộ test callback chung và ca lỗi adapter; đối chiếu tài khoản/bài trên nền tảng thật. Tách thời gian kỹ thuật khỏi thời gian chờ xét duyệt.

Nộp revision/PR khi có, lệnh/ca thử, môi trường, ngày thử, kết quả và phần chưa thử. Log/ảnh không chứa key/token. Prototype, mock và API thật phải có nhãn riêng. Bằng chứng dự kiến đặt trong hồ sơ ticket/PR hoặc tài liệu kiểm chứng được thống nhất; cập nhật bằng chứng thực thi vào issue/PR tương ứng; trạng thái Todo chưa chứng minh tiêu chí đã đạt.

### Bàn giao và xử lý blocker

Ngày làm việc 3 gửi sớm thông tin khả dụng cho Nghị; ngày làm việc 4 bàn giao token reference, author/post ID, lỗi/quyền và kết quả đã che bí mật.

Nếu callback chung [SP-010](https://github.com/nigPeter2503/Social_Pilot/issues/12) trễ, chốt hợp đồng và xác minh quyền bằng công cụ thử trước; phần callback end-to-end còn thiếu phải ghi rõ. Không đăng lại mù quáng khi không biết yêu cầu trước đã thành công.

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
