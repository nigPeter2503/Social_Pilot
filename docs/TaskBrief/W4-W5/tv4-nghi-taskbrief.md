# Tô Lãng Nghị (TV4) — Taskbrief 24/11–03/12/2026

Bản phân công đề xuất cho Tô Lãng Nghị (`@bob35306`). Nghị sở hữu API comment/số liệu, dữ liệu và nghiệp vụ F7, gồm cả màn báo cáo AI. Nghị giao DTO có số liệu tính tay và hai mốc đo thật để Thiện dựng các màn phân tích, sau đó tổng hợp bằng chứng cuối kỳ.

Ước lượng 38 giờ thực hiện ticket. Các dòng “Đầu vào” dùng lại kết quả của bộ brief tuần 2–3 theo giả định lập kế hoạch. Ngày 30/11 khóa tính năng, 01–02/12 nghiệm thu và sửa lỗi, 03/12 bàn giao. [Lịch chung và phụ thuộc](week-4-5-taskbrief.md)

<a id="sp-042"></a>

## SP-042 — Inbox hợp nhất với bộ lọc và dữ liệu offline

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#44](https://github.com/nigPeter2503/Social_Pilot/issues/44) | F6.B1, F9.B2 | Đầu vào | 0 | Minh, Thiện |

**Đầu vào** · SP-041.

### 1. Mục tiêu

Nghiệm thu đầu ra kế thừa, 0 giờ phát triển mới. Chứng minh một inbox hiển thị đúng comment thật của cả hai kênh và đọc được cache khi mất mạng.

### 2. Phạm vi

- Kiểm phần hiển thị và bộ lọc trên SP-041. Phân loại/gửi reply thuộc SP-043.

### 3. Tệp và module liên quan

- [analytics-data-contract.md](../../../docs/analytics-data-contract.md) · [social-contract.md](../../../docs/social-contract.md) · [facebook-comments-metrics-verification.md](../../../docs/facebook-comments-metrics-verification.md) · [Module giao diện Android](../../../android/app/src/main/java/com/socialpilot/app/ui/)

### 4. Các bước thực hiện

1. Lấy 1 thread Facebook và 1 thread Instagram kèm post ID, comment ID và thời điểm sync riêng của từng thread.
2. Mở inbox, lọc lần lượt theo kênh, bài và trạng thái. Refresh hai lần trên cùng dữ liệu.
3. Bật chế độ máy bay rồi restart. Mở lại hai thread, kiểm thời điểm sync và các trạng thái lỗi trong VI/EN.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F6.B1** — Hai thread thật xuất hiện trong cùng inbox, mỗi comment gắn đúng bài/kênh. Cả 3 bộ lọc trả đúng tập ID mong đợi; đồng bộ lặp tạo 0 comment trùng.
- [ ] **F9.B2** — Sau restart offline, cả 2 thread đã cache còn đọc được cùng thời điểm sync gần nhất. Dữ liệu thiếu quyền không hiển thị như inbox rỗng đã đồng bộ.
- [ ] **F6.B1, F9.B2** — Có kết quả cho loading, rỗng, lỗi, thiếu quyền ở 2 ngôn ngữ. Mẫu phục vụ kiểm UI được gắn nhãn tách với comment thật.

### 6. Bằng chứng nộp

- [ ] Hai thread thật, các bộ lọc và mốc sync sau restart offline. Nghị dùng làm đầu vào kiểm tra gửi đúng thread.
- [ ] Gắn hiện vật hoặc liên kết vào issue #44, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Minh và Thiện đối chiếu bảng giao việc với bằng chứng của #44.
2. Dò một comment mỗi kênh về đúng post, lặp bộ lọc rồi đối chiếu tập ID sau restart offline. Từ chối khi fixture được dùng để kết luận đã đọc được comment thật.
3. Ưu tiên kết quả trên PR tích hợp. Nếu thiếu bằng chứng, yêu cầu bổ sung; nếu phát hiện lỗi, ghi ca tái hiện và phần phải sửa.

### 8. Rủi ro và lưu ý

- Hai nền tảng có thể dùng định danh cục bộ giống nhau. Đối chiếu kèm scope kênh, không chỉ chuỗi ID.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | PR/SHA, cặp thread thật và quyền đọc tương ứng của bản nghiệm thu ở đâu? | Nghị | Khi nhận dữ liệu từ SP-041 | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-043"></a>

## SP-043 — Phân loại comment và gửi trả lời đã chỉnh sửa

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#45](https://github.com/nigPeter2503/Social_Pilot/issues/45) | F6.B3, F6.B4 | Đầu vào | 0 | Đạt, Thiện |

**Đầu vào** · SP-041, SP-042, SP-029.

### 1. Mục tiêu

Nghiệm thu đầu ra kế thừa, 0 giờ phát triển mới. Kiểm kết quả phân loại và xác nhận câu do người dùng sửa được gửi đúng thread, đúng một lần.

### 2. Phạm vi

- Kiểm nhãn, gợi ý, sửa và gửi thủ công qua budget chung. Chính sách tự gửi thuộc SP-044.

### 3. Tệp và module liên quan

- [analytics-data-contract.md](../../../docs/analytics-data-contract.md) · [facebook-comments-metrics-verification.md](../../../docs/facebook-comments-metrics-verification.md) · [ai-provider-contract.md](../../../docs/ai-provider-contract.md) · [social-contract.md](../../../docs/social-contract.md)

### 4. Các bước thực hiện

1. Đối chiếu bộ 60 comment với nhãn chuẩn và dự đoán. Tính số sai cùng ma trận nhầm lẫn cho năm nhóm.
2. Dùng thread có bài gốc và giọng văn, sửa câu AI gợi ý, so nội dung cuối với reply đọc lại trên nền tảng.
3. Tái hiện timeout sau gửi, làm mới rồi thử lại. Kiểm thêm ca mất quyền và hủy trước gửi.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F6.B3** — Có kết quả từng mẫu cho 60/60 comment, đủ 5 nhóm hỏi/khen/góp ý/tiêu cực/spam. Tổng ô của ma trận nhầm lẫn bằng 60, tổng ngoài đường chéo bằng số sai đã báo.
- [ ] **F6.B4** — Có hồ sơ reply thủ công đã chỉnh sửa trên cả 2 kênh. ID cha và nội dung đọc lại khớp từng thread; timeout/refresh/retry không làm số reply của ca thành công vượt 1.
- [ ] **F6.B4** — Hủy hoặc mất quyền trước gửi tạo 0 reply và giữ bản đang soạn. Nội dung comment tạo 0 quyết định tự gửi; việc gọi AI dùng dịch vụ và ngân sách chung.

### 6. Bằng chứng nộp

- [ ] Bộ 60 comment có nhãn, ma trận nhầm lẫn, ID comment/reply và kết quả đọc lại trên hai kênh. Nghị dùng cho SP-044/056.
- [ ] Gắn hiện vật hoặc liên kết vào issue #45, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Đạt và Thiện đối chiếu bảng giao việc với bằng chứng của #45.
2. Tính lại số sai từ dữ liệu gốc, đối chiếu nội dung đã sửa với reply thật và số lượt gửi sau timeout. Yêu cầu bổ sung nếu chỉ có tỷ lệ tổng hoặc ảnh gợi ý chưa gửi.
3. Ưu tiên kết quả trên PR tích hợp. Nếu thiếu bằng chứng, yêu cầu bổ sung; nếu phát hiện lỗi, ghi ca tái hiện và phần phải sửa.

### 8. Rủi ro và lưu ý

- Ẩn thông tin cá nhân trong dataset/log. Thiếu quyền trả lời là blocker theo kênh, không suy ra quyền ghi từ quyền đọc.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Bộ nhãn chuẩn và hồ sơ reply thật nào truy được tới thread và revision app? | Nghị | Trước khi giao SP-044/056 | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-048"></a>

## SP-048 — Mục tiêu lưu bền vững và tính tiến độ từ dữ liệu thật

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#50](https://github.com/nigPeter2503/Social_Pilot/issues/50) | F7.N3 | 24–25/11 | 8 | Đạt, Thiện |

**Đầu vào** · SP-046.

### 1. Mục tiêu

**Triển khai mới.** Bổ sung phần lưu mục tiêu và tính tiến độ từ snapshot thật. Cùng một goal và một tập số đo phải cho kết quả có thể đối chiếu độc lập với UI.

### 2. Phạm vi

- Repository lưu chỉ số, baseline, đích, hạn, kỳ và múi giờ. Form nhập và hiển thị do Thiện phụ trách ở SP-049.

### 3. Tệp và module liên quan

- [analytics-data-contract.md](../../../docs/analytics-data-contract.md) · [analytics-examples.json](../../../docs/contracts/analytics-examples.json) · [verify-analytics-examples.cjs](../../../docs/contracts/verify-analytics-examples.cjs) · [room-schema-contract.md](../../../docs/room-schema-contract.md)

### 4. Các bước thực hiện

1. Ngày 24/11 chốt DTO sơ bộ với Thiện. Tạo goal từ snapshot có ID nguồn, kiểm điều kiện đích và kỳ, lưu zone cùng revision.
2. Dùng mẫu hợp đồng follower từ 100 tới đích 120: giá trị 110 cho tiến độ 50%, 95 cho −25%, 125 cho 125%. Tính tay ca đạt/chưa đạt/hết hạn và ca thiếu baseline. Thêm một điểm sau hạn để kiểm biên kỳ.
3. Chạy lại sau sync, restart và migration. Giao DTO cùng giá trị mong đợi cho Thiện.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F7.N3** — Lưu và đọc lại đủ chỉ số, đích, hạn và baseline có ID nguồn. Target tăng trưởng bằng hoặc thấp hơn baseline hợp lệ tạo 0 goal tăng trưởng mới.
- [ ] **F7.N3** — Với các ca đạt, chưa đạt, hết hạn và thiếu baseline, kết quả khớp bảng tính tay theo zone. Ca thiếu coverage có trạng thái chưa xác định; có 0 mẫu sau hạn được tính vào kỳ cũ.
- [ ] **F7.N3** — Sau sync, tiến độ thay đổi theo dữ liệu thật. Sau migration/restart, goal giữ số lượng, ID, revision và định nghĩa metric như trước.

### 6. Bằng chứng nộp

- [ ] Ngày 25/11 giao SP-049 ba bộ dữ liệu đạt/chưa đạt/chưa xác định, kết quả tính tay, một ca hết hạn và việc đổi sau sync. Khôi phục goal qua migration/restart.
- [ ] Gắn hiện vật hoặc liên kết vào issue #50, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Đạt và Thiện đối chiếu bảng giao việc với bằng chứng của #50.
2. Tính lại từ source snapshot đúng kỳ, thử đích sai và so goal trước/sau migration. Từ chối khi UI có thể tự đổi định nghĩa hoặc dữ liệu thiếu bị coi là đã đạt.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Metric khác định nghĩa hoặc stale ảnh hưởng kết luận hoàn thành. Giữ trạng thái thiếu riêng với giá trị 0.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Những snapshot ID, định nghĩa metric và zone nào dùng để hai bên đối chiếu bộ ca goal? | Nghị và Thiện | Trước khi bàn giao cho SP-049 | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-044"></a>

## SP-044 — Tự trả lời mặc định tắt với FAQ và hạn mức

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#46](https://github.com/nigPeter2503/Social_Pilot/issues/46) | F6.B5 | 26–28/11 | 12 | Minh, Đạt |

**Đầu vào** · SP-043, SP-029.

### 1. Mục tiêu

**Triển khai mới.** Bổ sung tự trả lời giới hạn theo kênh. Chỉ lời khen hoặc FAQ đã xác nhận mới đủ điều kiện được gửi sau khi kiểm tra chính sách lần cuối.

### 2. Phạm vi

- Gồm opt-in, FAQ, hạn mức, khóa chống trùng và audit. Những trường hợp chưa đủ điều kiện chuyển cho người dùng duyệt.

### 3. Tệp và module liên quan

- [SocialPilot_Proposal_Java.md](../../../docs/SocialPilot_Proposal_Java.md) · [analytics-data-contract.md](../../../docs/analytics-data-contract.md) · [ai-provider-contract.md](../../../docs/ai-provider-contract.md) · [social-contract.md](../../../docs/social-contract.md)

### 4. Các bước thực hiện

1. Cài mặc định tắt, lưu cấu hình theo kênh với hạn mức mặc định 10 câu tự trả lời/kênh/ngày. Cho phép người dùng giảm hạn mức hoặc tắt. Chốt danh sách FAQ, hạn mức ngày và cách đối chiếu độ tin cậy trên bộ dữ liệu gán nhãn.
2. Ngay trước gửi, kiểm lại opt-in, quyền, quota, nhãn và trạng thái đã trả lời. Ghi quyết định theo comment ID.
3. Chạy bộ ca cấm, hai job tranh cùng comment, tắt giữa tác vụ và callback về sau ngắt kênh. Đếm lượt gửi.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F6.B5** — Mặc định tắt tạo 0 lượt gửi. Sau opt-in, chỉ ca khen hoặc FAQ khớp danh sách xác nhận mới qua bước duyệt chính sách.
- [ ] **F6.B5** — 100% ca cấm trong bộ thử (tiêu cực, PII, không chắc, FAQ ngoài danh sách, quá hạn mức) có 0 lượt tự gửi. Với mức mặc định, lần tự gửi thứ 11 trong ngày trên cùng kênh bị chặn. Khi người dùng giảm hạn mức xuống N, lượt N+1 bị chặn.
- [ ] **F6.B5** — Hai job đồng thời trên cùng comment tạo tối đa 1 reply. Tắt hoặc ngắt kênh trước dispatch tạo 0 lệnh mới; callback cũ không làm khởi động gửi lại. Chỉ bật thử gửi sau khi toàn bộ ca cấm đạt.

### 6. Bằng chứng nộp

- [ ] Ngày 28/11 giao Minh bộ ca mặc định tắt, bật, tắt giữa tác vụ, đụng hạn mức và chạy song song. Chỉ cho phép khen/FAQ đã xác nhận. Mọi ca cấm gồm thông tin cá nhân, bình luận tiêu cực, không chắc và FAQ ngoài danh sách phải bị chặn.
- [ ] Gắn hiện vật hoặc liên kết vào issue #46, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Minh và Đạt đối chiếu bảng giao việc với bằng chứng của #46.
2. Chạy ca âm trước ca dương, đặc biệt FAQ ngoài danh sách và giành suất cuối ngày. Đối chiếu audit với số reply. Từ chối khi có dù chỉ 1 ca cấm được gửi.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Nhận định tin cậy do model tự khai chưa đủ để cho gửi. Sau timeout cần đối soát, kể cả khi trạng thái gửi trên máy chưa ghi xong.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Bộ FAQ đã duyệt và kết quả hiệu chỉnh ngưỡng tin cậy cho bộ thử được lưu ở đâu? | Nghị | Trước khi bật thử tự trả lời | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-050"></a>

## SP-050 — Báo cáo tuần AI dựa trên snapshot và giao diện báo cáo

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#52](https://github.com/nigPeter2503/Social_Pilot/issues/52) | F7.N2 | 29–30/11 | 10 | Đạt, Thiện |

**Đầu vào** · SP-046, SP-029.

### 1. Mục tiêu

**Triển khai mới.** Bổ sung nghiệp vụ và màn báo cáo tuần AI. Người dùng đối chiếu được từng con số trong báo cáo với snapshot và mở lại bản đã lưu khi offline.

### 2. Phạm vi

- Nghị sở hữu truy vấn, prompt, kiểm tra đầu ra và màn báo cáo. Dùng SP-046 cho số liệu, SP-029 cho mọi lệnh AI.

### 3. Tệp và module liên quan

- [analytics-data-contract.md](../../../docs/analytics-data-contract.md) · [analytics-examples.json](../../../docs/contracts/analytics-examples.json) · [ai-provider-contract.md](../../../docs/ai-provider-contract.md) · [BudgetManager.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/BudgetManager.java)

### 4. Các bước thực hiện

1. Chốt kỳ từ thứ Hai đến Chủ nhật theo múi giờ cấu hình, tạo bảng nguồn gồm metric, giá trị và snapshot ID; thử một kỳ đủ dữ liệu và một kỳ thưa.
2. Chuyển bảng nguồn qua dịch vụ AI, kiểm các số và tách nhận xét có chứng cứ khỏi giả thuyết trước khi lưu.
3. Mở lại offline, đổi VI/EN, hủy lúc đang sinh và xóa dữ liệu một kênh. So các báo cáo còn đọc được.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F7.N2** — Mỗi số liệu trình bày ở kỳ đủ dữ liệu truy ra ít nhất 1 nguồn và khớp giá trị/kỳ/zone. Báo cáo có biến động, diễn giải và đề xuất tuần tiếp theo. Mọi nhận xét nhân quả chưa chứng minh đều có nhãn giả thuyết.
- [ ] **F7.N2** — Ca thiếu snapshot tạo 0 giá trị thay thế tưởng tượng, báo rõ thiếu dữ liệu. Số lệnh AI gửi mới khi hết budget bằng 0.
- [ ] **F7.N2** — Bản đã lưu đọc được offline ở luồng VI/EN. Hủy tạo 0 bản hoàn tất muộn. Xóa kênh A giữ được các báo cáo riêng của B.

### 6. Bằng chứng nộp

- [ ] Ngày 30/11 giao SP-052 một báo cáo có số đối chiếu được, một kỳ dữ liệu thưa và ca offline mở lại. Đánh dấu giả thuyết, thiếu quyền hoặc thiếu điểm đo. Kiểm thay ngôn ngữ, hủy lệnh và xóa theo kênh.
- [ ] Gắn hiện vật hoặc liên kết vào issue #52, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Đạt và Thiện đối chiếu bảng giao việc với bằng chứng của #52.
2. Chọn một số tăng/giảm để tính lại từ nguồn, đọc riêng kỳ thưa và thử mở offline. Yêu cầu sửa khi kết luận thiếu số liệu đối chiếu hoặc giả thuyết được viết thành sự thật.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Số liệu trước/sau khác metric hoặc kỳ không so được. Ghi kỳ, độ phủ và độ cũ cùng báo cáo.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Kỳ, zone và tập snapshot thật nào đã đủ quyền để chạy báo cáo và đối chiếu tuần thưa? | Nghị | Trước khi sinh báo cáo nghiệm thu | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-056"></a>

## SP-056 — Tổng hợp ma trận 44 yêu cầu và video dự phòng

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#58](https://github.com/nigPeter2503/Social_Pilot/issues/58) | DOCS | 28/11–02/12 | 8 | Minh, Thiện |

**Đầu vào** · SP-052.

### 1. Mục tiêu

**Tổng hợp hồ sơ nghiệm thu.** Lập ma trận nghiệm thu và video cuối kỳ từ bằng chứng bốn owner. Mỗi yêu cầu có kết luận kiểm được trên đúng revision bàn giao.

### 2. Phạm vi

- Tổng hợp kết quả từ SP-052 và các module. Đạt dùng tổng số để viết báo cáo, Thiện nhận video và ma trận để đóng gói.

### 3. Tệp và module liên quan

- [requirements.md](../../../docs/requirements.md) · [SocialPilot_Proposal_Java.md](../../../docs/SocialPilot_Proposal_Java.md) · [week-4-5-taskbrief.md](../../../docs/TaskBrief/W4-W5/week-4-5-taskbrief.md) · [build-verification.md](../../../docs/build-verification.md)

### 4. Các bước thực hiện

1. Liệt kê đủ mã cam kết và hai Optional, gắn owner, ca thử, thiết bị và nơi lưu bằng chứng.
2. Đối chiếu từng mã với kết quả thật, kiểm bộ 20 brief và 60 comment, ghi rõ mẫu thiếu và việc bị chặn.
3. Sau SP-052 khóa kết quả và quay hoặc kiểm lại video trên RC. Mở thử video và chuyển link cho SP-057/058.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **DOCS** — Có đúng 44 mã cam kết riêng biệt (33 B, 11 N), mỗi dòng có ca thử, SHA, ngày, người xác nhận, kết quả và link hoặc lý do thiếu. Hai Optional tách riêng. Số mã bị chặn/một phần được cộng đạt bằng 0.
- [ ] **DOCS** — Đối chiếu bộ 20 brief (10 VI, 10 EN), bộ 60 comment và hồ sơ API thật từng phép thử theo kênh. Mọi thiếu sót được ghi đúng mã.
- [ ] **DOCS** — Ít nhất 1 video dự phòng mở được trên revision bàn giao, có chỉ dẫn phần chưa đạt. Số mã đạt dùng trong SP-057 bằng tổng các dòng đạt trong ma trận.

### 6. Bằng chứng nộp

- [ ] Chiều 02/12 sau SP-052, khóa ma trận có người xác nhận từng dòng. Giao Đạt số đạt/thử một phần/bị chặn và Thiện video đã mở lại được. Thay revision thì đối chiếu hoặc quay lại phần bị ảnh hưởng.
- [ ] Gắn hiện vật hoặc liên kết vào issue #58, kèm PR khi có thay đổi repo. Ghi ngày, người lập và phiên bản nguồn, kiểm tra người nhận mở được các liên kết.

### 7. Điều kiện reviewer approve

1. Minh và Thiện đối chiếu bảng giao việc với bằng chứng của #58.
2. Đếm lại mã từ requirements, mở bằng chứng API theo kênh rồi phát video. Từ chối nếu dùng bằng chứng khác revision chưa đối chiếu hoặc tính một mã thiếu là đạt.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Một test của module có thể chỉ chứng minh một phần của một mã. Kết quả chạy bằng fixture cần được ghi rõ.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Revision cuối và vị trí video/ma trận đã khóa do SP-052 bàn giao là gì? | Nghị và Minh | Trước khi chốt ma trận chiều 02/12 | Chờ ghi câu trả lời và nguồn đối chiếu. |

Khi giao từng ticket, gắn SHA/PR, lệnh và kết quả thực tế, ảnh/log đã che bí mật, người nhận và việc còn chặn. Quy trình kiểm tra và điều kiện đóng ticket nằm trong [brief chung](week-4-5-taskbrief.md#kiem-chung).
