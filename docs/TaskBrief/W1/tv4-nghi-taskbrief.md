# Taskbrief Tuần 1 — Tô Lãng Nghị (TV4)

Đồng bộ ngày 04/11/2026 (Asia/Bangkok) từ GitHub Issues và [Project #5](https://github.com/users/nigPeter2503/projects/5). Các ticket dưới đây đã tạo, đã assign cho `bob35306`, hiện ở **Todo (Chưa làm)**. [Bản tổng hợp Tuần 1](week-1-taskbrief.md).

- **Trách nhiệm:** F6, API comment/số liệu và dữ liệu/công thức F7.
- **Task chính:** [SP-018](https://github.com/nigPeter2503/Social_Pilot/issues/20), [SP-019](https://github.com/nigPeter2503/Social_Pilot/issues/21), [SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22).
- **Công sức tham khảo:** 18 giờ owner + 4,5 giờ review + 2 giờ phối hợp = **24,5 giờ**. Các phần tích hợp, kiểm thử và tài liệu nằm trong giờ owner.
- **Cách làm đã chốt:** giữ đầu ra và mốc bàn giao; mỗi thành viên tự phân bổ thời gian. Giờ ước lượng không phải giới hạn và không chờ xác nhận lịch bận/giờ tăng.
- **Lịch:** dùng ngày làm việc tương đối trong Tuần 1 theo issue, không tính lại từ ngày nhận task; xem [mốc và rủi ro](week-1-taskbrief.md#11-đánh-giá-và-rủi-ro-còn-lại). Nhận đầu vào sớm, hoàn tất review và tích hợp trước demo cuối tuần.

## Việc cần review

| Issue | Owner | Mốc bàn giao | Nội dung |
|---|---|---|---|
| [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11) | Minh (`nigPeter2503`) | Hết ngày làm việc 2 | Chốt hợp đồng OAuth, capability và kết quả đăng |
| [SP-011](https://github.com/nigPeter2503/Social_Pilot/issues/13) | Minh (`nigPeter2503`) | Hết ngày làm việc 4 | Kiểm chứng LinkedIn OAuth, quyền đăng và refresh |
| [SP-013](https://github.com/nigPeter2503/Social_Pilot/issues/15) | Đạt (`dat532006`) | Hết ngày làm việc 3 | Chốt hợp đồng AI và đo tác vụ thật trên hai provider |
| [SP-014](https://github.com/nigPeter2503/Social_Pilot/issues/16) | Đạt (`dat532006`) | Hết ngày làm việc 4 | Kiểm chứng hai nhánh stream và hủy độc lập trên Java |
| [SP-015](https://github.com/nigPeter2503/Social_Pilot/issues/17) | Thiện (`PhuocThien06`) | Hết ngày làm việc 2 | Xác nhận baseline build, CI và thiết bị demo |
| [SP-017](https://github.com/nigPeter2503/Social_Pilot/issues/19) | Thiện (`PhuocThien06`) | Trước demo cuối Tuần 1 | Nối bản mẫu UI, Room và WorkManager có kiểm thử vòng đời |

Dành 0,75 giờ/người/task cho review ban đầu; chủ task gửi hợp đồng và ca thử sớm. Bố trí review theo mốc bàn giao, phản hồi các nhận xét bắt buộc trước merge.

<a id="sp-018"></a>

## SP-018 — Kiểm chứng Facebook comment, reply và số liệu thật

- **Issue:** [SP-018](https://github.com/nigPeter2503/Social_Pilot/issues/20) — #20.
- **Issue cha:** [SP-005](https://github.com/nigPeter2503/Social_Pilot/issues/7).
- **Project:** [Social Pilot - Development Board 2024_31_MD_2](https://github.com/users/nigPeter2503/projects/5).
- **Trạng thái đã kiểm chứng:** Issue OPEN; Project **Todo (Chưa làm)**; đã assign cho `bob35306`.
- **Yêu cầu:** F6.B1, F6.B2, F6.B4, F7.B1, F7.B2, F7.B3.
- **Owner:** TV4 — Tô Lãng Nghị (`bob35306`).
- **Hai reviewer khi có PR:** Ngô Gia Minh (`nigPeter2503`); Đỗ Phước Thiện (`PhuocThien06`).
- **Ưu tiên:** Cao, ghi trong issue; board hiện không có field Priority riêng.
- **Mốc bàn giao:** Hết ngày làm việc 3, tính từ đầu Tuần 1.
- **Ước lượng owner:** 6 giờ gồm thiết kế/triển khai, tích hợp, kiểm thử và tài liệu. Review thêm 0,75 giờ/người/PR, tính riêng.
- **Branch dự kiến:** `feat/sp-018-facebook-comments-metrics-spike` (chưa tạo), base `main`.
- **PR:** chưa tạo; liên kết issue ở trên và yêu cầu hai reviewer đã ghi.

### Mục tiêu, phạm vi và khu vực

Chuẩn bị và thử đọc/phân trang comment, trả lời một comment thử, follower và số liệu bài; ghi dữ liệu mẫu đã ẩn thông tin nhạy cảm. Chưa làm inbox/sync production.

Khu vực dự kiến: Adapter comment/metrics dự kiến dưới data/repository hoặc social; tài liệu bằng chứng API; không tự sửa SocialConnector ngoài [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11).

Các đường dẫn package Android rút gọn tính từ `android/app/src/main/java/com/socialpilot/app/`; backend từ `backend/src/main/java/com/socialpilot/oauth/`. Vị trí ghi “dự kiến” chưa tồn tại, sẽ chốt khi triển khai. API AI gọi từ thiết bị; backend chỉ giữ client secret phục vụ OAuth.

### Đầu vào, phụ thuộc và điều kiện mở chặn

Page/post/token hợp lệ từ [SP-010](https://github.com/nigPeter2503/Social_Pilot/issues/12) hoặc fixture thật có sẵn; quyền đọc/trả lời/metrics phải thử riêng. Khi chưa có token chuyển sang [SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22), không ngồi chờ.

### Đầu ra và tiêu chí đạt

- [ ] Bảng endpoint/quyền/kết quả phân biệt đọc, phân trang, trả lời, follower, các chỉ số bài và khả năng ảnh/định danh liên quan.
- [ ] Câu trả lời xuất hiện đúng comment thử; đối chiếu ID với nền tảng; không tự thử lại gửi khi kết quả chưa rõ.
- [ ] Số liệu thiếu và thiếu quyền được giữ khác 0; mẫu có thời điểm đo, định nghĩa chỉ số và dữ liệu đủ cho [SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22).
- [ ] Có ca lỗi thiếu quyền/rate limit trong fixture, ghi rõ ca nào mô phỏng và ca nào API thật.

### Kiểm chứng và bằng chứng cần nộp

Gọi API thật với đối tượng thử được nhóm xác nhận; đối chiếu comment/bài trên Page; test parser fixture nếu thêm Java.

Nộp revision/PR khi có, lệnh/ca thử, môi trường, ngày thử, kết quả và phần chưa thử. Log/ảnh không chứa key/token. Prototype, mock và API thật phải có nhãn riêng. Bằng chứng dự kiến đặt trong hồ sơ ticket/PR hoặc tài liệu kiểm chứng được thống nhất; cập nhật bằng chứng thực thi vào issue/PR tương ứng; trạng thái Todo chưa chứng minh tiêu chí đã đạt.

### Bàn giao và xử lý blocker

Ngày làm việc 3: Minh nhận capability đã kiểm chứng; Thiện/Đạt nhận DTO mẫu không có token. Bộ dữ liệu cho UI phải ghi nguồn và thời điểm.

Không có quyền thì bàn giao hồ sơ blocker trong timebox; phép thử API chưa đạt vẫn nằm trong tiêu chí W1, không dùng fixture để đóng thay.

Khi bàn giao ghi phần đã làm, kiểm thử đã chạy, phần còn thiếu, contract revision, người nhận và giờ còn lại. Nếu có nguy cơ trễ mốc, cập nhật việc còn lại và phụ thuộc trên issue; chỉ tạo task mới khi đầu ra độc lập, không đánh dấu tiêu chí chưa đạt thành đạt.

<a id="sp-019"></a>

## SP-019 — Kiểm chứng LinkedIn comment, reply, follower và số liệu

- **Issue:** [SP-019](https://github.com/nigPeter2503/Social_Pilot/issues/21) — #21.
- **Issue cha:** [SP-006](https://github.com/nigPeter2503/Social_Pilot/issues/8).
- **Project:** [Social Pilot - Development Board 2024_31_MD_2](https://github.com/users/nigPeter2503/projects/5).
- **Trạng thái đã kiểm chứng:** Issue OPEN; Project **Todo (Chưa làm)**; đã assign cho `bob35306`.
- **Yêu cầu:** F6.B1, F6.B2, F6.B4, F7.B1, F7.B2, F7.B3.
- **Owner:** TV4 — Tô Lãng Nghị (`bob35306`).
- **Hai reviewer khi có PR:** Nguyễn Đức Đạt (`dat532006`); Đỗ Phước Thiện (`PhuocThien06`).
- **Ưu tiên:** Cao, ghi trong issue; board hiện không có field Priority riêng.
- **Mốc bàn giao:** Hết ngày làm việc 4, tính từ đầu Tuần 1.
- **Ước lượng owner:** 6 giờ gồm thiết kế/triển khai, tích hợp, kiểm thử và tài liệu. Review thêm 0,75 giờ/người/PR, tính riêng.
- **Branch dự kiến:** `feat/sp-019-linkedin-comments-metrics-spike` (chưa tạo), base `main`.
- **PR:** chưa tạo; liên kết issue ở trên và yêu cầu hai reviewer đã ghi.

### Mục tiêu, phạm vi và khu vực

Kiểm chứng từng khả năng theo loại tài khoản [SP-011](https://github.com/nigPeter2503/Social_Pilot/issues/13) đã chọn; mẫu comment/phân trang/trả lời và metrics. Không thay số follower bằng connections, không suy ra quyền từ OAuth thành công.

Khu vực dự kiến: Adapter comment/metrics LinkedIn dự kiến, test parser, bảng quyền/bằng chứng; thống nhất lỗi với Minh.

Các đường dẫn package Android rút gọn tính từ `android/app/src/main/java/com/socialpilot/app/`; backend từ `backend/src/main/java/com/socialpilot/oauth/`. Vị trí ghi “dự kiến” chưa tồn tại, sẽ chốt khi triển khai. API AI gọi từ thiết bị; backend chỉ giữ client secret phục vụ OAuth.

### Đầu vào, phụ thuộc và điều kiện mở chặn

Token/author/post ID [SP-011](https://github.com/nigPeter2503/Social_Pilot/issues/13) hoặc fixture thật có sẵn; không cần đợi callback UI hoàn chỉnh nếu đã có token hợp lệ. Nhận schema cơ bản [SP-012](https://github.com/nigPeter2503/Social_Pilot/issues/14).

### Đầu ra và tiêu chí đạt

- [ ] Bảng khả năng ghi account type, API version, endpoint, scope/product, ngày thử và quyền còn thiếu.
- [ ] Đọc/trả lời đúng comment nếu được quyền; lưu ID và phản hồi thật đã che bí mật; trường hợp không được cấp ghi blocker cụ thể.
- [ ] Follower, impression, reaction/comment/share được ghi theo khả năng thật; dữ liệu thiếu có lý do và timestamp.
- [ ] Có mẫu dữ liệu phục vụ UI/test, ghi rõ thật hay mô phỏng; không trình bày bộ mẫu là dashboard đã tích hợp.

### Kiểm chứng và bằng chứng cần nộp

API thật và đối chiếu nền tảng; test parser/error từ fixture khi cần. Các lỗi không tái hiện được thật có nhãn mô phỏng.

Nộp revision/PR khi có, lệnh/ca thử, môi trường, ngày thử, kết quả và phần chưa thử. Log/ảnh không chứa key/token. Prototype, mock và API thật phải có nhãn riêng. Bằng chứng dự kiến đặt trong hồ sơ ticket/PR hoặc tài liệu kiểm chứng được thống nhất; cập nhật bằng chứng thực thi vào issue/PR tương ứng; trạng thái Todo chưa chứng minh tiêu chí đã đạt.

### Bàn giao và xử lý blocker

Ngày làm việc 4: Minh nhận capability/lỗi; Thiện nhận DTO metrics/comment; Đạt nhận vấn đề schema/usage khi liên quan.

Nếu [SP-011](https://github.com/nigPeter2503/Social_Pilot/issues/13) chưa có token cuối ngày làm việc 3, chuẩn bị test/parser và nêu rủi ro W1 ngay. Không lấy thành công Facebook thay LinkedIn.

Khi bàn giao ghi phần đã làm, kiểm thử đã chạy, phần còn thiếu, contract revision, người nhận và giờ còn lại. Nếu có nguy cơ trễ mốc, cập nhật việc còn lại và phụ thuộc trên issue; chỉ tạo task mới khi đầu ra độc lập, không đánh dấu tiêu chí chưa đạt thành đạt.

<a id="sp-020"></a>

## SP-020 — Chốt hợp đồng comment, snapshot, công thức và dashboard

- **Issue:** [SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22) — #22.
- **Issue cha:** [SP-006](https://github.com/nigPeter2503/Social_Pilot/issues/8).
- **Project:** [Social Pilot - Development Board 2024_31_MD_2](https://github.com/users/nigPeter2503/projects/5).
- **Trạng thái đã kiểm chứng:** Issue OPEN; Project **Todo (Chưa làm)**; đã assign cho `bob35306`.
- **Yêu cầu:** INFRA, F6.B2, F7.B1, F7.B2, F7.B3, F7.N1, F7.N3.
- **Owner:** TV4 — Tô Lãng Nghị (`bob35306`).
- **Hai reviewer khi có PR:** Ngô Gia Minh (`nigPeter2503`); Đỗ Phước Thiện (`PhuocThien06`).
- **Ưu tiên:** Cao, ghi trong issue; board hiện không có field Priority riêng.
- **Mốc bàn giao:** Hết ngày làm việc 3, tính từ đầu Tuần 1.
- **Ước lượng owner:** 6 giờ gồm thiết kế/triển khai, tích hợp, kiểm thử và tài liệu. Review thêm 0,75 giờ/người/PR, tính riêng.
- **Branch dự kiến:** `feat/sp-020-analytics-data-contract` (chưa tạo), base `main`.
- **PR:** chưa tạo; liên kết issue ở trên và yêu cầu hai reviewer đã ghi.

### Mục tiêu, phạm vi và khu vực

DTO comment/snapshot/goal, pagination và dedup; công thức engagement, khoảng đo tuần/múi giờ, top bài và truy vấn hiệu quả; mẫu rỗng/thiếu quyền/cũ. Không làm toàn F7, báo cáo AI hay dashboard UI.

Khu vực dự kiến: domain/contract và data/repository dự kiến; docs/architecture.md, test công thức nếu có implementation thuần Java. Đạt vẫn điều phối schema/version.

Các đường dẫn package Android rút gọn tính từ `android/app/src/main/java/com/socialpilot/app/`; backend từ `backend/src/main/java/com/socialpilot/oauth/`. Vị trí ghi “dự kiến” chưa tồn tại, sẽ chốt khi triển khai. API AI gọi từ thiết bị; backend chỉ giữ client secret phục vụ OAuth.

### Đầu vào, phụ thuộc và điều kiện mở chặn

Có thể soạn bản nháp độc lập từ requirements; chốt ID theo [SP-012](https://github.com/nigPeter2503/Social_Pilot/issues/14) và capability theo [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11). Không đợi [SP-018](https://github.com/nigPeter2503/Social_Pilot/issues/20)/[SP-019](https://github.com/nigPeter2503/Social_Pilot/issues/21) thành công: mẫu có nhãn, ghi rõ giả định cần thay bằng API thật.

### Đầu ra và tiêu chí đạt

- [ ] DTO có ID, nguồn, timestamp, giá trị nullable/lý do thiếu; contract phân trang và khóa chống trùng không phụ thuộc UI.
- [ ] Ví dụ công thức với mẫu số 0/thiếu thành phần không trả 0%; số giảm được giữ; so sánh cùng định nghĩa/khoảng đo.
- [ ] Chốt quy ước tuần/múi giờ và thiết kế so sánh snapshot từ tối thiểu hai thời điểm thực; quy tắc mục tiêu/tiến độ khi thiếu dữ liệu và top bài không bịa lịch sử. W1 bàn giao contract/fixture có nhãn; thu snapshot thật thuộc SP-045 từ W2–3, không thêm điều kiện phải có lịch sử thật ngay trong task contract W1.
- [ ] Thiện xác nhận đủ trường dựng dashboard/mục tiêu; lịch thu snapshot thật từ W2 được ghi với Nghị là owner.

### Kiểm chứng và bằng chứng cần nộp

Review ví dụ dữ liệu/công thức với Minh/Thiện; unit test công thức nếu có code. Không dùng dữ liệu mẫu để ghi F7 đạt.

Nộp revision/PR khi có, lệnh/ca thử, môi trường, ngày thử, kết quả và phần chưa thử. Log/ảnh không chứa key/token. Prototype, mock và API thật phải có nhãn riêng. Bằng chứng dự kiến đặt trong hồ sơ ticket/PR hoặc tài liệu kiểm chứng được thống nhất; cập nhật bằng chứng thực thi vào issue/PR tương ứng; trạng thái Todo chưa chứng minh tiêu chí đã đạt.

### Bàn giao và xử lý blocker

Ngày làm việc 3: Thiện nhận contract UI và fixture; Đạt nhận entity/index đề xuất; Minh nhận nhu cầu capability. Sau [SP-018](https://github.com/nigPeter2503/Social_Pilot/issues/20)/[SP-019](https://github.com/nigPeter2503/Social_Pilot/issues/21) bổ sung giới hạn thực tế qua revision được review.

Nếu API thiếu trường, sửa capability/nhãn thiếu; không tự đổi định nghĩa follower hoặc tương tác cho đẹp biểu đồ.

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
