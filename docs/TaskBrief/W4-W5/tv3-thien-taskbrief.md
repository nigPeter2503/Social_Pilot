# Đỗ Phước Thiện (TV3) — Taskbrief 24/11–03/12/2026

Bản phân công đề xuất cho Đỗ Phước Thiện (`@PhuocThien06`). Thiện sở hữu form, editor, màn phân tích và đóng gói Android. Với F7, số liệu và công thức đến từ repository của Nghị. Thiện giữ đầu mối F9 và hỗ trợ Minh chạy Espresso trong SP-052.

Ước lượng 48 giờ thực hiện ticket. Các dòng “Đầu vào” dùng lại kết quả của bộ brief tuần 2–3 theo giả định lập kế hoạch. Ngày 30/11 khóa tính năng, 01–02/12 nghiệm thu và sửa lỗi, 03/12 bàn giao. [Lịch chung và phụ thuộc](week-4-5-taskbrief.md)

<a id="sp-037"></a>

## SP-037 — Nhập URL có trích nguồn và nhận Android Sharesheet

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#39](https://github.com/nigPeter2503/Social_Pilot/issues/39) | F3.N1, F3.N3 | Đầu vào | 0 | Minh, Nghị |

**Đầu vào** · SP-035.

### 1. Mục tiêu

Nghiệm thu đầu ra kế thừa, 0 giờ phát triển mới. Chứng minh một link công khai đi từ form hoặc Sharesheet đến đúng brief có trích nguồn.

### 2. Phạm vi

- Kiểm nhập URL, nguồn, vòng đời form và hủy. Gợi ý từ RSS/Atom thuộc SP-038.

### 3. Tệp và module liên quan

- [AndroidManifest.xml](../../../android/app/src/main/AndroidManifest.xml) · [Brief.java](../../../android/app/src/main/java/com/socialpilot/app/domain/editor/Brief.java) · [MainActivity.java](../../../android/app/src/main/java/com/socialpilot/app/ui/MainActivity.java) · [sp-016-brief-contract.md](../../../docs/sp-016-brief-contract.md)

### 4. Các bước thực hiện

1. Mở URL công khai trực tiếp và qua ACTION_SEND từ một ứng dụng khác. So URL hiển thị và nguồn lưu trong brief.
2. Thử URL sai, MIME không hỗ trợ, trang bị chặn và mất mạng. Kiểm đường nhập tay và số lệnh tự gửi.
3. Hủy luồng chia sẻ rồi mở lại, xoay màn hình khi đang nhập. Kiểm bản nháp và yêu cầu phát sinh.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F3.N1** — URL công khai tạo nội dung có nguồn truy ngược đúng bài. Với URL lỗi/bị chặn, có thông báo và nhập tay, tạo 0 trích dẫn hoặc nội dung nguồn giả.
- [ ] **F3.N3** — Với 1 link chia sẻ hợp lệ, mở đúng 1 form điền sẵn URL. Hủy tạo 0 bài, 0 lệnh đăng. MIME/URL sai được xử lý rõ và xoay màn hình không nhân đôi thao tác.
- [ ] **F3.N1, F3.N3** — Chuỗi trong nguồn yêu cầu tự duyệt hoặc bỏ qua ngân sách thực hiện được 0 hành động đặc quyền.

### 6. Bằng chứng nộp

- [ ] Video chia sẻ URL từ ứng dụng khác, nguồn trích dẫn và ca hủy. Thiện dùng lại ca thử trong SP-052.
- [ ] Gắn hiện vật hoặc liên kết vào issue #39, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Minh và Nghị đối chiếu bảng giao việc với bằng chứng của #39.
2. Chia sẻ từ ứng dụng khác, mở lại nguồn và thử hủy trước khi tạo brief. Yêu cầu bổ sung khi chỉ có ảnh chụp form không thể truy ra link.
3. Ưu tiên kết quả trên PR tích hợp. Nếu thiếu bằng chứng, yêu cầu bổ sung; nếu phát hiện lỗi, ghi ca tái hiện và phần phải sửa.

### 8. Rủi ro và lưu ý

- Trang đăng nhập/paywall hoặc nội dung đã đổi cần có kết quả lỗi và nhập tay. Không vượt quyền truy cập.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | PR/SHA và video Sharesheet của bản kế thừa nào dùng cho SP-052? | Thiện | Khi nhận đầu vào | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-047"></a>

## SP-047 — Màn hình số liệu, tăng trưởng và dashboard

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#49](https://github.com/nigPeter2503/Social_Pilot/issues/49) | F7.B1, F7.B2, F7.B3 | Đầu vào | 0 | Đạt, Nghị |

**Đầu vào** · SP-020, SP-046.

### 1. Mục tiêu

Nghiệm thu đầu ra kế thừa, 0 giờ phát triển mới. Chứng minh cả màn bài, biểu đồ và dashboard dùng số liệu thật do repository cung cấp.

### 2. Phạm vi

- Kiểm XML/ViewModel, định dạng và trạng thái giao diện. Nghị sở hữu công thức ở SP-046.

### 3. Tệp và module liên quan

- [analytics-data-contract.md](../../../docs/analytics-data-contract.md) · [analytics-examples.json](../../../docs/contracts/analytics-examples.json) · [verify-analytics-examples.cjs](../../../docs/contracts/verify-analytics-examples.cjs) · [Module giao diện Android](../../../android/app/src/main/java/com/socialpilot/app/ui/)

### 4. Các bước thực hiện

1. Chọn bài và kênh có ít nhất hai lần đo thật. Ghi source ID, định nghĩa metric, thời điểm và giá trị mong đợi.
2. Đối chiếu từng màn với DTO, thử mẫu số 0, thiếu quyền và kỳ bị bỏ lỡ.
3. Đổi VI/EN, xoay màn hình và mở offline. Chụp bộ lọc, nhãn thời gian và giá trị sau mỗi ca.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F7.B1** — Số liệu từng bài khớp các metric khả dụng của DTO. Với mẫu hợp đồng 12 reaction, 3 comment, 5 share và 400 impression, UI hiển thị tỷ lệ 5%. Cả ca mẫu số 0 và ca thiếu thành phần đều hiển thị không khả dụng, có 0 giá trị 0% suy diễn.
- [ ] **F7.B2** — Có ít nhất 2 thời điểm đo thật ở từng kênh trong hồ sơ. Các điểm khớp đúng thời điểm/giá trị và khoảng trống không bị lấp bằng điểm đo tự tạo.
- [ ] **F7.B3** — Dashboard có đủ 4 phần: follower, tương tác tuần, top bài và so sánh kênh cùng định nghĩa. 2 ngôn ngữ hiển thị được trạng thái tải/offline/thiếu quyền. Sau xoay còn đúng bộ lọc, có 0 công thức nghiệp vụ tính lại trong View.

### 6. Bằng chứng nộp

- [ ] Hai thời điểm snapshot thật ở mỗi kênh cùng ca thiếu metric hoặc mẫu số bằng 0. Thiện và Nghị dùng lại số liệu để đối chiếu UI trong SP-049/052.
- [ ] Gắn hiện vật hoặc liên kết vào issue #49, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Đạt và Nghị đối chiếu bảng giao việc với bằng chứng của #49.
2. Nghị đối chiếu một bài đến snapshot nguồn, Đạt cùng kiểm ca thiếu dữ liệu và cache sau restart. Yêu cầu bổ sung nếu chỉ có fixture hoặc dùng reach thay impression.
3. Ưu tiên kết quả trên PR tích hợp. Nếu thiếu bằng chứng, yêu cầu bổ sung; nếu phát hiện lỗi, ghi ca tái hiện và phần phải sửa.

### 8. Rủi ro và lưu ý

- Các kênh có thể cấp quyền khác nhau. Đối chiếu định nghĩa và khoảng đo trước khi cộng hoặc so sánh.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Revision repository và các snapshot ID thật nào minh chứng cho hai kênh? | Nghị và Thiện | Trước khi đối chiếu dashboard | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-040"></a>

## SP-040 — Lịch sử và khôi phục bản nháp không kế thừa duyệt

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#42](https://github.com/nigPeter2503/Social_Pilot/issues/42) | F4.N1 | 24–25/11 | 8 | Minh, Đạt |

**Đầu vào** · SP-036.

### 1. Mục tiêu

**Triển khai mới.** Bổ sung lịch sử và khôi phục phiên bản theo kênh. Người dùng mở lại được bản cũ khi offline và bản khôi phục phải được duyệt lại.

### 2. Phạm vi

- Giao diện và nghiệp vụ lịch sử dựa trên SP-036. Hàng đợi chỉ tiêu thụ version đã duyệt còn hiệu lực.

### 3. Tệp và module liên quan

- [ContentVersion.java](../../../android/app/src/main/java/com/socialpilot/app/domain/editor/ContentVersion.java) · [VersionApprovalState.java](../../../android/app/src/main/java/com/socialpilot/app/domain/editor/VersionApprovalState.java) · [VersionApprovalStateTest.java](../../../android/app/src/test/java/com/socialpilot/app/domain/editor/VersionApprovalStateTest.java) · [room-schema-contract.md](../../../docs/room-schema-contract.md)

### 4. Các bước thực hiện

1. Tạo hai phiên bản có nội dung và ảnh nhận diện được. Duyệt bản hiện hành rồi chọn khôi phục bản trước.
2. Lưu bản khôi phục theo expectedCurrentVersionId, so ID cha, ID mới và approval.
3. Thử phục hồi offline, mở lại app, chạy migration và kiểm file dùng chung khi xóa liên kết.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F4.N1** — Khôi phục một lần tạo đúng 1 versionId mới, giữ nguyên toàn bộ phiên bản trước đó, nội dung và ảnh khớp bản được chọn. Bản mới có 0 approval được kế thừa.
- [ ] **F4.N1** — Sau restart offline và sau migration, số version và nội dung mẫu đều khớp trước/sau. Ảnh có ít nhất 1 tham chiếu còn mở được.
- [ ] **F4.N1** — Nếu current đổi trước khi yêu cầu restore được ghi, thao tác cũ tạo 0 version mới và không đổi approval hiện hành.

### 6. Bằng chứng nộp

- [ ] Ngày 25/11 giao Minh hai version trước/sau restore, bằng chứng bản mới chưa duyệt và lịch sử vẫn đủ sau restart offline. Chạy migration với bản nháp cũ có ảnh.
- [ ] Gắn hiện vật hoặc liên kết vào issue #42, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Minh và Đạt đối chiếu bảng giao việc với bằng chứng của #42.
2. Theo version ID trước/sau restore, thử xếp hàng bản chưa duyệt và so lịch sử sau nâng cấp. Từ chối khi ghi đè bản cũ hay mất ảnh còn dùng.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Ảnh có thể dùng chung giữa các version hoặc thuộc một job. Kiểm tham chiếu trước khi dọn tệp.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Bộ dữ liệu nâng cấp có bản nháp cũ và ảnh dùng chung nằm ở revision nào? | Thiện và Đạt | Trước khi chạy migration test | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-039"></a>

## SP-039 — Viết lại đoạn, sinh lại ảnh, ba hook và alt text

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#41](https://github.com/nigPeter2503/Social_Pilot/issues/41) | F4.B3, F4.N2, F4.N3 | 26–27/11 | 12 | Đạt, Nghị |

**Đầu vào** · SP-036, SP-032, SP-029.

### 1. Mục tiêu

**Triển khai mới.** Bổ sung sửa từng phần bằng AI, sinh lại ảnh, chọn hook và quản lý alt text trên đúng phiên bản đang biên tập.

### 2. Phạm vi

- Thiện sở hữu prompt và thay đổi version. SP-032 cung cấp ảnh, SP-029 kiểm ngân sách và SP-025 gửi payload theo quyền của kênh.

### 3. Tệp và module liên quan

- [sp-016-brief-contract.md](../../../docs/sp-016-brief-contract.md) · [ContentVersion.java](../../../android/app/src/main/java/com/socialpilot/app/domain/editor/ContentVersion.java) · [ai-provider-contract.md](../../../docs/ai-provider-contract.md) · [social-contract.md](../../../docs/social-contract.md)

### 4. Các bước thực hiện

1. Ghi lại bản đã duyệt, chọn một đoạn để viết lại, sau đó tạo ba hook và áp dụng một phương án. So diff từng lần.
2. Sinh lại ảnh của một kênh. Lưu/sửa alt text rồi mở lại bản hiện hành và kiểm payload qua adapter.
3. Cho callback cũ về sau khi sửa tay hoặc hủy. So version, approval và số lần gọi qua budget.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F4.B3** — Viết lại 1 vùng giữ nguyên 100% văn bản ngoài vùng. Sinh lại ảnh chỉ đổi ảnh ở kênh đã chọn. Mỗi thay đổi hợp lệ tạo phiên bản mới chưa duyệt; callback cũ/hủy ghi thêm 0 version.
- [ ] **F4.N2** — Sinh đúng 3 hook. Chọn 1 hook chỉ đổi phần mở đầu, phần thân khớp nguyên bản.
- [ ] **F4.N3** — Có 1 ca tạo alt text tự động rồi sửa tay cho ảnh đã chọn. Alt text đã sửa đọc lại đúng sau restart. Trên kênh hỗ trợ, payload ảnh chứa đúng alt text; trên kênh thiếu capability, có thông báo hạn chế. Các ca hết ngân sách gửi 0 yêu cầu AI mới.

### 6. Bằng chứng nộp

- [ ] Ngày 27/11 giao Minh payload ảnh/alt text và ảnh đối chiếu API khi hỗ trợ. Thử phản hồi AI đến sau một sửa tay, kiểm phần thân bài giữ nguyên khi chọn hook, mọi sửa đổi cần duyệt lại.
- [ ] Gắn hiện vật hoặc liên kết vào issue #41, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Đạt và Nghị đối chiếu bảng giao việc với bằng chứng của #41.
2. So text trước/sau theo vùng, xem cả ba hook và đọc alt text ở payload cuối. Yêu cầu sửa khi callback ghi đè sửa tay hoặc báo đã truyền alt text sai capability.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Thay ảnh sau duyệt làm mất hiệu lực của duyệt cũ. Phân biệt alt text lưu cục bộ và alt text đã được kênh nhận.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Capability alt text nào đã được kiểm chứng cho mỗi kênh trên revision ứng viên? | Thiện và Minh | Trước khi kiểm payload bàn giao Minh | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-038"></a>

## SP-038 — Gợi ý topic từ bài hiệu quả và nguồn RSS/Atom

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#40](https://github.com/nigPeter2503/Social_Pilot/issues/40) | F3.N4 | 28–29/11 | 10 | Đạt, Nghị |

**Đầu vào** · SP-035, SP-046, SP-029.

### 1. Mục tiêu

**Triển khai mới.** Bổ sung gợi ý topic từ bài có hiệu quả và nguồn RSS/Atom đã chọn. Mỗi gợi ý mở được nguồn và chuyển thành brief có thể sửa.

### 2. Phạm vi

- Thiện tổng hợp gợi ý. Nghị cung cấp truy vấn bài hiệu quả, dịch vụ AI dùng ngân sách chung nếu được gọi.

### 3. Tệp và module liên quan

- [analytics-data-contract.md](../../../docs/analytics-data-contract.md) · [Brief.java](../../../android/app/src/main/java/com/socialpilot/app/domain/editor/Brief.java) · [DraftRepository.java](../../../android/app/src/main/java/com/socialpilot/app/data/repository/DraftRepository.java) · [ai-provider-contract.md](../../../docs/ai-provider-contract.md)

### 4. Các bước thực hiện

1. Lấy một bài hiệu quả kèm post ID, kỳ và tiêu chí từ SP-046; đọc một mục từ feed đã chọn.
2. Gắn nguồn, lý do và thời điểm vào gợi ý. Mở từng nguồn và chuyển gợi ý thành brief.
3. Làm mới lặp, thử feed hỏng, lịch sử rỗng và mở lại cache offline.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F3.N4** — Có ít nhất 1 gợi ý từ bài hiệu quả và 1 từ RSS/Atom trong ca đủ dữ liệu. Cả 2 truy về được nguồn, có lý do và điền được form brief.
- [ ] **F3.N4** — Đọc cùng mục feed hai lần tạo 0 bản trùng. Hiển thị thời điểm theo múi giờ cấu hình. Khi offline còn mở được gợi ý đã lưu.
- [ ] **F3.N4** — Với feed hỏng hoặc lịch sử trống, UI chỉ rõ nguồn thiếu, sinh 0 kết luận xu hướng toàn mạng. Mọi lượt AI phát sinh để gợi ý đều đi qua budget chung.

### 6. Bằng chứng nộp

- [ ] Ngày 29/11 giao hai gợi ý truy ngược được đến hai loại nguồn. Thử feed lặp, feed hỏng, lịch sử trống và mở cache khi offline. Ghi nguồn nào thiếu dữ liệu.
- [ ] Gắn hiện vật hoặc liên kết vào issue #40, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Đạt và Nghị đối chiếu bảng giao việc với bằng chứng của #40.
2. Theo hai gợi ý về đúng post/feed, đối chiếu kỳ đánh giá và lặp lại refresh. Từ chối khi không truy được nguồn hoặc hiện một mục thành hai gợi ý.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Giờ đăng và giờ quan sát có thể khác múi giờ. Giữ provenance khi cache. Nguồn công khai không tự cấp quyền gọi AI.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Feed RSS/Atom nào đã được chọn và truy vấn bài hiệu quả nào được SP-046 bàn giao? | Thiện và Nghị | Trước khi kiểm hai loại nguồn | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-049"></a>

## SP-049 — Giao diện phân tích theo nhóm và mục tiêu

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#51](https://github.com/nigPeter2503/Social_Pilot/issues/51) | F7.N1, F7.N3 | 29–30/11 | 10 | Minh, Nghị |

**Đầu vào** · SP-046, SP-048, SP-047.

### 1. Mục tiêu

**Triển khai mới.** Bổ sung UI phân tích nhóm và mục tiêu. Người dùng lọc được số liệu và thấy tiến độ đổi sau lần đồng bộ mới.

### 2. Phạm vi

- Thiện làm XML/ViewModel. SP-046 cung cấp số liệu nhóm, SP-048 lưu và tính mục tiêu.

### 3. Tệp và module liên quan

- [analytics-data-contract.md](../../../docs/analytics-data-contract.md) · [analytics-examples.json](../../../docs/contracts/analytics-examples.json) · [Module giao diện Android](../../../android/app/src/main/java/com/socialpilot/app/ui/) · [architecture.md](../../../docs/architecture.md)

### 4. Các bước thực hiện

1. Nhận DTO nhóm/goal, một bộ tính tay và snapshot thật, ghi múi giờ và kỳ.
2. Nối lần lượt bộ lọc chủ đề, định dạng và giờ đăng; nhập metric, đích và hạn, thử giá trị sai.
3. Đồng bộ mẫu mới, mở offline, đổi VI/EN và restart. So số hiển thị với DTO từng ca.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F7.N1** — Cả 3 chiều chủ đề/định dạng/giờ đăng khớp kết quả tính tay theo cùng múi giờ. UI dùng kết quả repository, có 0 phép tổng hợp nghiệp vụ tính lại tại View.
- [ ] **F7.N3** — Form hợp lệ lưu đúng chỉ số, đích và hạn. Sau xoay màn hình, bộ lọc đang chọn giữ nguyên. Nhập đích sai tạo 0 goal; thiếu baseline hiển thị chưa xác định. Sau sync, tiến độ hiển thị bằng DTO mới.
- [ ] **F7.N3** — Sau restart offline, goal đã lưu giữ đủ 3 trường và hiện trạng thái cache ở cả VI/EN. Có ca dữ liệu thật cùng fixture gắn nhãn.

### 6. Bằng chứng nộp

- [ ] Ngày 30/11 cùng Nghị so một phép nhóm có số tính tay và goal thay đổi sau sync. Giao SP-052 ảnh VI/EN cho đủ dữ liệu, thiếu baseline, đích sai và offline.
- [ ] Gắn hiện vật hoặc liên kết vào issue #51, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Minh và Nghị đối chiếu bảng giao việc với bằng chứng của #51.
2. Đổi từng bộ lọc, đối chiếu với dữ liệu gốc, thử goal sai rồi đồng bộ goal hợp lệ. Từ chối nếu UI tự tính khác repository hoặc thay phần thiếu bằng 0%.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Sắp xếp theo giờ địa phương có thể đẩy bài sang nhóm khác. Dùng cùng zone ở fixture, repository và UI.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | DTO, bộ số tính tay và snapshot thật nào Nghị bàn giao cho lần bind cuối? | Nghị và Thiện | Trước khi đối chiếu UI với SP-048 | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-058"></a>

## SP-058 — Đóng gói APK, slide và bộ bàn giao có thể tái lập

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#60](https://github.com/nigPeter2503/Social_Pilot/issues/60) | INFRA, DOCS | 30/11–03/12 | 8 | Minh, Nghị |

**Đầu vào** · SP-052, SP-056, SP-057.

### 1. Mục tiêu

**Đóng gói và kiểm chứng.** Tạo gói bàn giao mới có APK cài được, mã nguồn dựng lại được và các hiện vật cùng revision để Minh demo.

### 2. Phạm vi

- Thiện đóng gói từ sản phẩm do các chủ module giao. Minh cung cấp backend, Nghị cung cấp ma trận/video, Đạt cung cấp báo cáo.

### 3. Tệp và module liên quan

- [ci.yml](../../../.github/workflows/ci.yml) · [build-verification.md](../../../docs/build-verification.md) · [development.md](../../../docs/development.md) · [SocialPilot_Proposal_Java.md](../../../docs/SocialPilot_Proposal_Java.md)

### 4. Các bước thực hiện

1. Build RC từ checkout sạch với hai Gradle Wrapper, ghi SHA, lệnh, kết quả và checksum APK.
2. Cài sạch rồi thử nâng cấp trên thiết bị demo. Trên máy thứ hai làm theo hướng dẫn, kiểm health HTTPS và smoke luồng chính.
3. Đối chiếu đủ tám nhóm sản phẩm mục 5.4, mở từng liên kết, kiểm bí mật rồi khóa manifest sau SP-052/056/057.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **INFRA** — APK cài trên thiết bị có checksum khớp manifest. Cả ca cài sạch và ca nâng cấp đều có kết quả smoke, có kết quả dựng lại từ hai wrapper trên revision được ghi.
- [ ] **DOCS** — Manifest liệt kê đủ 8/8 nhóm sản phẩm, mỗi nhóm có người giao và vị trí mở được. Slide, APK, video và hồ sơ cùng bản tích hợp.
- [ ] **INFRA, DOCS** — Cấu hình mẫu và gói nộp có 0 client secret, API key hoặc keystore cá nhân. Máy thứ hai dùng được hướng dẫn setup và kiểm được health HTTPS.

### 6. Bằng chứng nộp

- [ ] Sáng 03/12 sau SP-052/056/057, giao Minh APK, source hai wrapper, slide, manifest và đường dẫn tài liệu/bằng chứng/video. Máy thứ hai làm theo hướng dẫn, health HTTPS và smoke qua luồng chính trên đúng bản.
- [ ] Gắn hiện vật hoặc liên kết vào issue #60, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Minh và Nghị đối chiếu bảng giao việc với bằng chứng của #60.
2. Kiểm checksum trước cài, mở 8 mục theo manifest, so SHA với SP-052 và phát thử video. Yêu cầu đóng gói lại nếu checksum lệch, link hỏng hay còn secret.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Bản CI mới có thể trùng tên tệp APK cũ. Khóa manifest theo hash và SHA sau lần sửa cuối.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Thiết bị demo, máy kiểm dựng lại và vị trí lưu gói cuối nào sẽ ghi vào manifest? | Thiện và Minh | Trước khi khóa gói SP-058 | Chờ ghi câu trả lời và nguồn đối chiếu. |

Khi giao từng ticket, gắn SHA/PR, lệnh và kết quả thực tế, ảnh/log đã che bí mật, người nhận và việc còn chặn. Quy trình kiểm tra và điều kiện đóng ticket nằm trong [brief chung](week-4-5-taskbrief.md#kiem-chung).
