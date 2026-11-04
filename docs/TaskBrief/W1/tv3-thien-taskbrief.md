# Taskbrief Tuần 1 — Đỗ Phước Thiện (TV3)

Đồng bộ ngày 04/11/2026 (Asia/Bangkok) từ GitHub Issues và [Project #5](https://github.com/users/nigPeter2503/projects/5). Các ticket dưới đây đã tạo, đã assign cho `PhuocThien06`, hiện ở **Todo (Chưa làm)**. [Bản tổng hợp Tuần 1](week-1-taskbrief.md).

- **Trách nhiệm:** F3/F4/F9, UI F7, CI và đóng gói Android.
- **Task chính:** [SP-015](https://github.com/nigPeter2503/Social_Pilot/issues/17), [SP-016](https://github.com/nigPeter2503/Social_Pilot/issues/18), [SP-017](https://github.com/nigPeter2503/Social_Pilot/issues/19).
- **Công sức tham khảo:** 18 giờ owner + 4,5 giờ review + 2 giờ phối hợp = **24,5 giờ**. Các phần tích hợp, kiểm thử và tài liệu nằm trong giờ owner.
- **Cách làm đã chốt:** giữ đầu ra và mốc bàn giao; mỗi thành viên tự phân bổ thời gian. Giờ ước lượng không phải giới hạn và không chờ xác nhận lịch bận/giờ tăng.
- **Lịch:** dùng ngày làm việc tương đối trong Tuần 1 theo issue, không tính lại từ ngày nhận task; xem [mốc và rủi ro](week-1-taskbrief.md#11-đánh-giá-và-rủi-ro-còn-lại). Nhận đầu vào sớm, hoàn tất review và tích hợp trước demo cuối tuần.

## Việc cần review

| Issue | Owner | Mốc bàn giao | Nội dung |
|---|---|---|---|
| [SP-010](https://github.com/nigPeter2503/Social_Pilot/issues/12) | Minh (`nigPeter2503`) | Hết ngày làm việc 3 | Kiểm chứng Facebook OAuth callback và một bài đăng thật |
| [SP-012](https://github.com/nigPeter2503/Social_Pilot/issues/14) | Đạt (`dat532006`) | Hết ngày làm việc 2 | Chốt quy ước Room, quyền sở hữu dữ liệu và thứ tự migration |
| [SP-014](https://github.com/nigPeter2503/Social_Pilot/issues/16) | Đạt (`dat532006`) | Hết ngày làm việc 4 | Kiểm chứng hai nhánh stream và hủy độc lập trên Java |
| [SP-018](https://github.com/nigPeter2503/Social_Pilot/issues/20) | Nghị (`bob35306`) | Hết ngày làm việc 3 | Kiểm chứng Facebook comment, reply và số liệu thật |
| [SP-019](https://github.com/nigPeter2503/Social_Pilot/issues/21) | Nghị (`bob35306`) | Hết ngày làm việc 4 | Kiểm chứng LinkedIn comment, reply, follower và số liệu |
| [SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22) | Nghị (`bob35306`) | Hết ngày làm việc 3 | Chốt hợp đồng comment, snapshot, công thức và dashboard |

Dành 0,75 giờ/người/task cho review ban đầu; chủ task gửi hợp đồng và ca thử sớm. Bố trí review theo mốc bàn giao, phản hồi các nhận xét bắt buộc trước merge.

<a id="sp-015"></a>

## SP-015 — Xác nhận baseline build, CI và thiết bị demo

- **Issue:** [SP-015](https://github.com/nigPeter2503/Social_Pilot/issues/17) — #17.
- **Issue cha:** [SP-007](https://github.com/nigPeter2503/Social_Pilot/issues/9).
- **Project:** [Social Pilot - Development Board 2024_31_MD_2](https://github.com/users/nigPeter2503/projects/5).
- **Trạng thái đã kiểm chứng:** Issue OPEN; Project **Todo (Chưa làm)**; đã assign cho `PhuocThien06`.
- **Yêu cầu:** INFRA.
- **Owner:** TV3 — Đỗ Phước Thiện (`PhuocThien06`).
- **Hai reviewer khi có PR:** Ngô Gia Minh (`nigPeter2503`); Tô Lãng Nghị (`bob35306`).
- **Ưu tiên:** Cao, ghi trong issue; board hiện không có field Priority riêng.
- **Mốc bàn giao:** Hết ngày làm việc 2, tính từ đầu Tuần 1.
- **Ước lượng owner:** 4 giờ gồm thiết kế/triển khai, tích hợp, kiểm thử và tài liệu. Review thêm 0,75 giờ/người/PR, tính riêng.
- **Branch dự kiến:** `chore/sp-015-baseline-verification` (chưa tạo), base `main`.
- **PR:** chưa tạo; liên kết issue ở trên và yêu cầu hai reviewer đã ghi.

### Mục tiêu, phạm vi và khu vực

Kiểm kê stack/phiên bản hiện có, mã cũ nếu có, chạy wrapper và cài bản nền; lưu/đọc topic sau khởi động lại. Đối chiếu kết quả CI hiện tại. Không tạo lại project hoặc tự nâng stack.

Khu vực dự kiến: .github/workflows/ci.yml, build Gradle chỉ sửa nếu có lỗi thuộc task được chốt; docs/build-verification.md, docs/development.md; UI/Room hiện có để kiểm tra.

Các đường dẫn package Android rút gọn tính từ `android/app/src/main/java/com/socialpilot/app/`; backend từ `backend/src/main/java/com/socialpilot/oauth/`. Vị trí ghi “dự kiến” chưa tồn tại, sẽ chốt khi triển khai. API AI gọi từ thiết bị; backend chỉ giữ client secret phục vụ OAuth.

### Đầu vào, phụ thuộc và điều kiện mở chặn

JDK 21, SDK 36, emulator/thiết bị. Chọn emulator/điện thoại phù hợp với stack đã khóa và ghi môi trường thực tế. Dùng emulator API 36 theo baseline và API 23 cho mức tối thiểu; thử OS mới hơn nếu sẵn có, không đổi target vì việc đó.

### Đầu ra và tiêu chí đạt

- [ ] Hai wrapper build/test đạt, Android lint đạt trên revision bàn giao; phiên bản khóa được đối chiếu mã build. Nếu có lỗi, ghi cách tái hiện và owner xử lý; task chưa đạt cho tới khi các kiểm tra áp dụng đạt.
- [ ] Cài APK, lưu topic, đóng/mở app vẫn còn dữ liệu; ghi thiết bị/API và video/ảnh; nếu chưa thử máy thật phải nói rõ.
- [ ] CI theo đúng commit được dẫn link; không lấy kết quả build cũ làm kết quả thử mới.
- [ ] Ghi có/không mã cũ cần chuyển đổi theo phạm vi đã kiểm kê; chưa biết mã ngoài repo thì ghi chưa xác nhận.

### Kiểm chứng và bằng chứng cần nộp

Android assembleDebug/testDebugUnitTest/lintDebug; backend test/bootJar; kiểm tra Room trên emulator/thiết bị. Không cần thêm test chỉ để lặp lại mã validator.

Nộp revision/PR khi có, lệnh/ca thử, môi trường, ngày thử, kết quả và phần chưa thử. Log/ảnh không chứa key/token. Prototype, mock và API thật phải có nhãn riêng. Bằng chứng dự kiến đặt trong hồ sơ ticket/PR hoặc tài liệu kiểm chứng được thống nhất; cập nhật bằng chứng thực thi vào issue/PR tương ứng; trạng thái Todo chưa chứng minh tiêu chí đã đạt.

### Bàn giao và xử lý blocker

Ngày làm việc 2: cả nhóm nhận hướng dẫn tái lập, revision, artifact và lỗi môi trường. Minh nhận thông tin backend; [SP-017](https://github.com/nigPeter2503/Social_Pilot/issues/19) nhận thiết bị baseline.

Nếu máy cá nhân không chạy build, dùng máy khác/CI để tách lỗi môi trường; không ghi test thiết bị đạt chỉ vì CI xanh.

Khi bàn giao ghi phần đã làm, kiểm thử đã chạy, phần còn thiếu, contract revision, người nhận và giờ còn lại. Nếu có nguy cơ trễ mốc, cập nhật việc còn lại và phụ thuộc trên issue; chỉ tạo task mới khi đầu ra độc lập, không đánh dấu tiêu chí chưa đạt thành đạt.

<a id="sp-016"></a>

## SP-016 — Chốt wireframe và hợp đồng brief, phiên bản, duyệt

- **Issue:** [SP-016](https://github.com/nigPeter2503/Social_Pilot/issues/18) — #18.
- **Issue cha:** [SP-004](https://github.com/nigPeter2503/Social_Pilot/issues/6).
- **Project:** [Social Pilot - Development Board 2024_31_MD_2](https://github.com/users/nigPeter2503/projects/5).
- **Trạng thái đã kiểm chứng:** Issue OPEN; Project **Todo (Chưa làm)**; đã assign cho `PhuocThien06`.
- **Yêu cầu:** F3.B1, F4.B1, F4.B2, F4.N1, INFRA.
- **Owner:** TV3 — Đỗ Phước Thiện (`PhuocThien06`).
- **Hai reviewer khi có PR:** Ngô Gia Minh (`nigPeter2503`); Nguyễn Đức Đạt (`dat532006`).
- **Ưu tiên:** Cao, ghi trong issue; board hiện không có field Priority riêng.
- **Mốc bàn giao:** Hết ngày làm việc 3, tính từ đầu Tuần 1.
- **Ước lượng owner:** 6 giờ gồm thiết kế/triển khai, tích hợp, kiểm thử và tài liệu. Review thêm 0,75 giờ/người/PR, tính riêng.
- **Branch dự kiến:** `feat/sp-016-brief-approval-contract` (chưa tạo), base `main`.
- **PR:** chưa tạo; liên kết issue ở trên và yêu cầu hai reviewer đã ghi.

### Mục tiêu, phạm vi và khu vực

Wireframe luồng brief → hai kết quả → sửa/duyệt → queue cùng điểm vào kênh/inbox/dashboard/settings; brief đủ trường; hợp đồng version/approval và ca test domain tối thiểu. Chưa xây editor đầy đủ, lịch sử UI hoặc AI rewrite.

Khu vực dự kiến: Tài liệu wireframe dự kiến docs/w1-wireframes.md; domain/editor và contract brief/version; resources VI/EN nếu thêm UI mẫu.

Các đường dẫn package Android rút gọn tính từ `android/app/src/main/java/com/socialpilot/app/`; backend từ `backend/src/main/java/com/socialpilot/oauth/`. Vị trí ghi “dự kiến” chưa tồn tại, sẽ chốt khi triển khai. API AI gọi từ thiết bị; backend chỉ giữ client secret phục vụ OAuth.

### Đầu vào, phụ thuộc và điều kiện mở chặn

Quy ước ID [SP-012](https://github.com/nigPeter2503/Social_Pilot/issues/14); [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11) đã chốt kiểu approvedVersionId. Bản nháp ID có thể gửi từ đầu Tuần 1 để không chặn [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11); hoàn thiện mẫu theo contract đã chốt.

### Đầu ra và tiêu chí đạt

- [ ] Brief gồm topic, mục tiêu, đối tượng, giọng văn, VI/EN, kênh đích; payload mẫu dùng được bởi AI.
- [ ] Phiên bản duyệt bất biến; sửa text/ảnh hoặc khôi phục bản cũ không kế thừa approval sai; bài chưa duyệt không đủ điều kiện enqueue.
- [ ] Wireframe có loading/rỗng/lỗi/offline/thiếu quyền cho các điểm chính; ghi rõ thao tác cần mạng, tránh hứa mock là thật.
- [ ] Có ví dụ/test cho sửa sau duyệt, draft dùng chung hai kênh và version không tồn tại; giới hạn ký tự phải lấy từ capability được kiểm chứng sau.

### Kiểm chứng và bằng chứng cần nộp

Walk-through wireframe với hai reviewer; unit test quy tắc version nếu có Java model; ví dụ payload không thay acceptance app end-to-end.

Nộp revision/PR khi có, lệnh/ca thử, môi trường, ngày thử, kết quả và phần chưa thử. Log/ảnh không chứa key/token. Prototype, mock và API thật phải có nhãn riêng. Bằng chứng dự kiến đặt trong hồ sơ ticket/PR hoặc tài liệu kiểm chứng được thống nhất; cập nhật bằng chứng thực thi vào issue/PR tương ứng; trạng thái Todo chưa chứng minh tiêu chí đã đạt.

### Bàn giao và xử lý blocker

Ngày làm việc 3: Đạt nhận Brief DTO; Minh nhận version/approval contract; Nghị nhận nhu cầu dữ liệu của wireframe inbox/dashboard.

Nếu UI polish làm tăng giờ, giữ wireframe đơn giản; không cắt quy tắc duyệt hoặc dời hợp đồng sang W2.

Khi bàn giao ghi phần đã làm, kiểm thử đã chạy, phần còn thiếu, contract revision, người nhận và giờ còn lại. Nếu có nguy cơ trễ mốc, cập nhật việc còn lại và phụ thuộc trên issue; chỉ tạo task mới khi đầu ra độc lập, không đánh dấu tiêu chí chưa đạt thành đạt.

<a id="sp-017"></a>

## SP-017 — Nối bản mẫu UI, Room và WorkManager có kiểm thử vòng đời

- **Issue:** [SP-017](https://github.com/nigPeter2503/Social_Pilot/issues/19) — #19.
- **Issue cha:** [SP-007](https://github.com/nigPeter2503/Social_Pilot/issues/9).
- **Project:** [Social Pilot - Development Board 2024_31_MD_2](https://github.com/users/nigPeter2503/projects/5).
- **Trạng thái đã kiểm chứng:** Issue OPEN; Project **Todo (Chưa làm)**; đã assign cho `PhuocThien06`.
- **Yêu cầu:** INFRA, F2.B4, F2.B5, F9.B2.
- **Owner:** TV3 — Đỗ Phước Thiện (`PhuocThien06`).
- **Hai reviewer khi có PR:** Nguyễn Đức Đạt (`dat532006`); Tô Lãng Nghị (`bob35306`).
- **Ưu tiên:** Cao, ghi trong issue; board hiện không có field Priority riêng.
- **Mốc bàn giao:** Trước demo cuối Tuần 1, tính từ đầu Tuần 1.
- **Ước lượng owner:** 8 giờ gồm thiết kế/triển khai, tích hợp, kiểm thử và tài liệu. Review thêm 0,75 giờ/người/PR, tính riêng.
- **Branch dự kiến:** `feat/sp-017-ui-room-worker-prototype` (chưa tạo), base `main`.
- **PR:** chưa tạo; liên kết issue ở trên và yêu cầu hai reviewer đã ghi.

### Mục tiêu, phạm vi và khu vực

Một màn hình mẫu thể hiện hai stream, hủy và trạng thái; lưu kết quả mẫu qua Room; một Worker mẫu có ràng buộc mạng/checkpoint. Không xây queue đăng thật, auto-reply hoặc tác vụ AI nền production.

Khu vực dự kiến: ui/, core/AppContainer.java, data/local nếu cần migration, Worker dự kiến; androidTest, resources VI/EN. Thiện là người sửa wiring chung cuối W1.

Các đường dẫn package Android rút gọn tính từ `android/app/src/main/java/com/socialpilot/app/`; backend từ `backend/src/main/java/com/socialpilot/oauth/`. Vị trí ghi “dự kiến” chưa tồn tại, sẽ chốt khi triển khai. API AI gọi từ thiết bị; backend chỉ giữ client secret phục vụ OAuth.

### Đầu vào, phụ thuộc và điều kiện mở chặn

[SP-015](https://github.com/nigPeter2503/Social_Pilot/issues/17) baseline; [SP-014](https://github.com/nigPeter2503/Social_Pilot/issues/16) runtime đã review; [SP-012](https://github.com/nigPeter2503/Social_Pilot/issues/14) cho mọi schema thay đổi; [SP-016](https://github.com/nigPeter2503/Social_Pilot/issues/18) cho dữ liệu mẫu. Làm phần Room/Worker độc lập ngày làm việc 3 đến ngày làm việc 4, chỉ nối stream khi runtime sẵn sàng.

### Đầu ra và tiêu chí đạt

- [ ] Xoay màn hình không khởi tạo thêm lệnh AI; hủy A vẫn thấy B hoàn tất; UI không bị callback muộn ghi đè.
- [ ] Lưu dữ liệu mẫu, khởi động lại còn dữ liệu; nếu đổi v1 phải có migration bảo toàn topic cũ, không xóa DB.
- [ ] Job mẫu khi offline được giữ, khi đủ điều kiện mạng thì tiếp tục; process death hiển thị gián đoạn, không tự gọi lại AI tính phí.
- [ ] Chuỗi UI mẫu có VI/EN và trạng thái rỗng/lỗi/offline; bản demo gắn nhãn dữ liệu mẫu rõ.

### Kiểm chứng và bằng chứng cần nộp

Espresso/thiết bị cho xoay màn hình và khởi động lại; WorkManager test helpers cho ràng buộc mạng; Room test khi đổi schema. Không cam kết Worker chạy ngay khi app force-stop.

Nộp revision/PR khi có, lệnh/ca thử, môi trường, ngày thử, kết quả và phần chưa thử. Log/ảnh không chứa key/token. Prototype, mock và API thật phải có nhãn riêng. Bằng chứng dự kiến đặt trong hồ sơ ticket/PR hoặc tài liệu kiểm chứng được thống nhất; cập nhật bằng chứng thực thi vào issue/PR tương ứng; trạng thái Todo chưa chứng minh tiêu chí đã đạt.

### Bàn giao và xử lý blocker

Cuối Tuần 1: artifact/commit tích hợp, video và kịch bản chạy cho Minh demo; lỗi runtime trả Đạt, lỗi dữ liệu thuộc owner dữ liệu, không dồn tất cả về Thiện.

Phải dành cửa sổ review trong cuối Tuần 1 trước demo. Nếu runtime tới muộn, demo Room/Worker độc lập và đánh dấu thiếu thử vòng đời stream; W1 chưa đủ điều kiện đạt.

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
