# Ngô Gia Minh (TV1) — Taskbrief 24/11–03/12/2026

Bản phân công đề xuất cho Ngô Gia Minh (`@nigPeter2503`). Minh chịu trách nhiệm kết nối, hàng đợi và đối soát với nền tảng. Dành cửa sổ cuối kỳ cho kiểm thử liên thông và demo sau khi luồng đăng đã bàn giao.

Ước lượng 34 giờ thực hiện ticket. Các dòng “Đầu vào” dùng lại kết quả của bộ brief tuần 2–3 theo giả định lập kế hoạch. Ngày 30/11 khóa tính năng, 01–02/12 nghiệm thu và sửa lỗi, 03/12 bàn giao. [Lịch chung và phụ thuộc](week-4-5-taskbrief.md)

<a id="sp-054"></a>

## SP-054 — Rà quy trình hai approval và nhịp báo cáo nhóm

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#56](https://github.com/nigPeter2503/Social_Pilot/issues/56) | INFRA, DOCS | 24–25/11 | 4 | Đạt, Nghị |

**Đầu vào** · SP-021.

### 1. Mục tiêu

**Rà soát quy trình.** Làm mới biên bản kiểm tra quy trình để nhóm biết PR nào đủ hai approval và ai xử lý sai khác trước lần merge tiếp theo.

### 2. Phạm vi

- Đối chiếu cấu hình repo, PR mẫu và lịch báo cáo. Thay đổi quyền bảo vệ nhánh thuộc chủ repo.

### 3. Tệp và module liên quan

- [CONTRIBUTING.md](../../../CONTRIBUTING.md) · [ci.yml](../../../.github/workflows/ci.yml)

### 4. Các bước thực hiện

1. Ghi cấu hình bảo vệ main, checks bắt buộc, quyền người thao tác và giới hạn gói thực tế.
2. Chọn một PR đã merge, đối chiếu tác giả, hai bản review, SHA và thời điểm merge.
3. Đối chiếu tên Slack channel và lịch trực. Giao từng sai khác cho một người có quyền xử lý.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **INFRA** — Có hồ sơ cấu hình main và ít nhất 1 PR mẫu. Mỗi approval được tính có người thật khác tác giả, revision và thời điểm trước merge. PR thiếu một trong hai approval được ghi thiếu.
- [ ] **DOCS** — Lịch báo cáo ghi mã nhóm, đúng tiền tố 2024_31_md_, tên người trực cho cả hai tuần và hạn theo CONTRIBUTING. Mỗi sai khác có 1 đầu mối và mốc kiểm lại.

### 6. Bằng chứng nộp

- [ ] Minh bàn giao biên bản cho Đạt, Nghị và chủ repo ngày 25/11. Ghi riêng ai có quyền xử lý từng sai khác và ngày kiểm lại.
- [ ] Gắn hiện vật hoặc liên kết vào issue #56, kèm PR khi có thay đổi repo. Ghi ngày, người lập và phiên bản nguồn, kiểm tra người nhận mở được các liên kết.

### 7. Điều kiện reviewer approve

1. Đạt và Nghị đối chiếu bảng giao việc với bằng chứng của #56.
2. Mở cấu hình và PR mẫu, đếm lại hai người duyệt. Yêu cầu chỉnh biên bản nếu suy approval từ trạng thái merged hoặc ghi đã bật quy tắc khi mới đề xuất.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Quyền xem, quyền sửa và khả năng của gói GitHub có thể khác nhau. Chỉ ghi thao tác đã quan sát.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Mã nhóm, link channel và ai có quyền điều chỉnh bảo vệ main? | Minh | Trước khi chốt biên bản | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-024"></a>

## SP-024 — Ngắt kênh, hủy tác vụ và xóa dữ liệu có chọn lọc

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#26](https://github.com/nigPeter2503/Social_Pilot/issues/26) | F1.B4 | 24–26/11 | 10 | Đạt, Nghị |

**Đầu vào** · SP-022, SP-012, SP-020.

### 1. Mục tiêu

**Triển khai tiếp.** Hoàn thiện phần còn lại của luồng ngắt kênh. Khi người dùng xác nhận, ứng dụng dừng công việc và xóa dữ liệu riêng của kênh, đồng thời giữ bản nháp và ảnh còn được kênh khác dùng.

### 2. Phạm vi

- Phần chung là thứ tự vô hiệu hóa kết nối và các điểm dọn dữ liệu. Chủ module chịu trách nhiệm dọn dữ liệu phần mình.

### 3. Tệp và module liên quan

- [social-contract.md](../../../docs/social-contract.md) · [room-schema-contract.md](../../../docs/room-schema-contract.md) · [EncryptedOAuthStore.java](../../../android/app/src/main/java/com/socialpilot/app/data/security/EncryptedOAuthStore.java)

### 4. Các bước thực hiện

1. Liệt kê dữ liệu sẽ xóa, dữ liệu dùng chung và quyết định giữ dấu vết chống trùng. Hiển thị danh sách trước khi xác nhận.
2. Vô hiệu hóa revision rồi hủy queue, sync, AI và auto-reply của A. Dọn credential, dữ liệu và ảnh hết tham chiếu.
3. Ngắt A khi B còn hoạt động, cho callback cũ về, restart và kết nối lại. Đếm bản ghi và mở lại ảnh/draft của B.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F1.B4** — Hủy ở bước xác nhận làm thay đổi 0 kết nối. Sau xác nhận và restart, A có 0 token sử dụng được, 0 job được phép tiếp tục và 0 bản ghi riêng ngoài chính sách giữ đã duyệt.
- [ ] **F1.B4** — Với 2 kênh cùng tham chiếu dữ liệu, B vẫn mở được draft/ảnh trước đó. Callback cũ tạo 0 dữ liệu mới cho A, kể cả sau reconnect.
- [ ] **F1.B4** — Có kết quả trước/sau cho queue, sync, AI và auto-reply. Ca liên thông được chạy lại trong SP-052 khi đủ các module.

### 6. Bằng chứng nộp

- [ ] Đối chiếu quy tắc xóa với hành vi queue SP-026 ngày 24/11. Trước khi giao ngày 26/11, ngắt Page giữa lúc có job/sync, cho callback cũ về, khởi động lại và kết nối lại. Chụp số bản ghi trước/sau cho hai kênh và bản nháp chung. SP-052 chạy lại sau khi có auto-reply.
- [ ] Gắn hiện vật hoặc liên kết vào issue #26, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Đạt và Nghị đối chiếu bảng giao việc với bằng chứng của #26.
2. Đối chiếu số dòng theo quyền sở hữu, mở ảnh còn tham chiếu và kiểm lại callback sau reconnect. Từ chối khi token còn dùng được hoặc dữ liệu kênh B bị xóa.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Cleanup tệp có thể gián đoạn sau commit Room. Lần dọn lại phải kiểm tra tham chiếu còn hiệu lực.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Nhóm đã chọn chính sách nào cho dấu vết chống đăng trùng khi ngắt và kết nối lại? | Minh, Đạt và Nghị | Trước khi tích hợp phần xóa dữ liệu | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-025"></a>

## SP-025 — Đăng bản đã duyệt lên hai kênh và lưu link thật

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#27](https://github.com/nigPeter2503/Social_Pilot/issues/27) | F5.B1, F5.B5 | Đầu vào | 0 | Đạt, Thiện |

**Đầu vào** · SP-022, SP-036. Cần thêm ảnh SP-032 trước kiểm chứng đích thứ hai.

### 1. Mục tiêu

Nghiệm thu đầu ra kế thừa, 0 giờ phát triển mới. Đối chiếu một phiên bản đã duyệt đến đúng bài thật trên Facebook Page và Instagram Professional.

### 2. Phạm vi

- Kiểm đầu vào, trạng thái và link sau đăng. Retry của hàng đợi được kiểm riêng ở SP-026.

### 3. Tệp và module liên quan

- [PublishRequest.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/social/PublishRequest.java) · [PublishResult.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/social/PublishResult.java) · [social-contract.md](../../../docs/social-contract.md)

### 4. Các bước thực hiện

1. Lấy hai hồ sơ app đã đăng được phép, kiểm kênh, version và ảnh SP-032.
2. Đối chiếu ID và thời điểm trả về, mở permalink từng đích. Với Instagram, phân biệt container chuẩn bị ảnh với media đã publish.
3. Đọc kết quả ca bản chưa duyệt, payload sai và một kênh lỗi. Đếm số lệnh gửi và trạng thái kênh còn lại.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F5.B1** — Phiên bản chưa duyệt, đã bị thay thế hoặc payload không hợp lệ tạo 0 lệnh đăng. Trong ca một kênh lỗi, kết quả đã lưu của kênh kia giữ nguyên.
- [ ] **F5.B5** — Cả 2/2 đích thành công có ID bài đã xuất bản, URL và timestamp. Từng URL mở đúng nội dung và tài khoản. Ca bàn giao thủ công có 0 trạng thái API thành công và 0 permalink tự gán.

### 6. Bằng chứng nộp

- [ ] Hai bản ghi đăng thật, versionId và permalink từng đích. Minh dùng các bản ghi khi đối soát số bài trong SP-052.
- [ ] Gắn hiện vật hoặc liên kết vào issue #27, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Đạt và Thiện đối chiếu bảng giao việc với bằng chứng của #27.
2. Mở từng permalink, dò ngược về đúng version đã duyệt và kiểm trạng thái ca lỗi một kênh. Yêu cầu bổ sung nếu chỉ có ảnh chụp Explorer hoặc chỉ có container ID.
3. Ưu tiên kết quả trên PR tích hợp. Nếu thiếu bằng chứng, yêu cầu bổ sung; nếu phát hiện lỗi, ghi ca tái hiện và phần phải sửa.

### 8. Rủi ro và lưu ý

- Không đăng lại bài chỉ để lấy ảnh minh chứng. Chỉ chạy bổ sung khi đã duyệt đúng nội dung và đúng tài khoản.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | PR/SHA và hai hồ sơ đăng từ ứng dụng cho đúng phiên bản nằm ở đâu? | Minh | Khi nhận đầu vào SP-052 | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-026"></a>

## SP-026 — Hàng đợi bền vững và đối soát chống đăng trùng

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#28](https://github.com/nigPeter2503/Social_Pilot/issues/28) | F5.B2, F5.B3, F5.B4 | Đầu vào | 0 | Đạt, Nghị |

**Đầu vào** · SP-025, SP-012. Cần quyết định giữ/xóa dữ liệu của SP-024 trước khi khóa schema.

### 1. Mục tiêu

Nghiệm thu đầu ra kế thừa, 0 giờ phát triển mới. Kiểm rằng một thao tác đăng hợp lệ sống qua mất mạng và restart, và một lần gửi có kết quả chưa rõ được đối soát trước khi gửi tiếp.

### 2. Phạm vi

- Kiểm queue, transaction, Worker, retry và trạng thái chờ đối soát. SP-025 cung cấp bằng chứng bài đã đăng.

### 3. Tệp và module liên quan

- [PublishQueue.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/PublishQueue.java) · [social-contract.md](../../../docs/social-contract.md) · [room-schema-contract.md](../../../docs/room-schema-contract.md)

### 4. Các bước thực hiện

1. Bật chế độ máy bay, xếp hàng bản đã duyệt, bấm hai lần rồi dừng/mở lại app. Đọc khóa job và version được lưu.
2. Khôi phục mạng, thử mất phản hồi sau khi nền tảng nhận. Đối chiếu số bài thật, trạng thái và số lần gửi tiếp.
3. Thử 429, lỗi mạng, quyền và payload sai. So lịch thử với cấu hình backoff, giới hạn lượt và kiểm lại job sau migration.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F5.B2** — Bấm đôi cho cùng kênh/draft/version tạo đúng 1 job. Job còn sau khi restart offline và tiếp tục khi có mạng cùng điều kiện Worker phù hợp.
- [ ] **F5.B3** — Sau timeout chưa rõ kết quả, có 0 lượt tự gửi lại. Mỗi version/kênh chỉ có 1 bài trong ca đã thành công, kể cả sau restart. Lượt đăng mới cần quyết định tường minh của người dùng sau đối soát.
- [ ] **F5.B4** — Lỗi mạng/429 dùng backoff, tuân thời gian chờ API trả về nếu có và số lần thử không vượt cấu hình. Lỗi quyền/nội dung có 0 retry tự động và hiển thị bước xử lý. Sau migration còn đủ khóa job đã gieo.

### 6. Bằng chứng nộp

- [ ] Ca enqueue offline, bấm đôi, kill/restart và timeout sau gửi. Thiện và Minh chạy lại trên bản ứng viên trong SP-052.
- [ ] Gắn hiện vật hoặc liên kết vào issue #28, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Đạt và Nghị đối chiếu bảng giao việc với bằng chứng của #28.
2. Đếm khóa queue trước/sau restart, so số lần gọi với số bài thật và lịch backoff. Từ chối khi timeout làm phát sinh lần đăng thứ hai chưa đối soát.
3. Ưu tiên kết quả trên PR tích hợp. Nếu thiếu bằng chứng, yêu cầu bổ sung; nếu phát hiện lỗi, ghi ca tái hiện và phần phải sửa.

### 8. Rủi ro và lưu ý

- Mock có thể tái hiện timeout, còn số bài phải đối chiếu trên nền tảng. Nếu đầu vào thiếu và phần sửa vượt hai ngày tập trung, Minh ghi phần việc cần tách.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Revision queue, chính sách retry và bộ evidence timeout/đối soát nào đi cùng bản giao? | Minh | Trước khi kiểm retry trong SP-052 | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-055"></a>

## SP-055 — Điều phối demo và theo dõi blocker Tuần 2–5

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến nếu sửa tài liệu |
|---|---|---|---:|---|
| [#57](https://github.com/nigPeter2503/Social_Pilot/issues/57) | DOCS, INFRA | 24/11–03/12 | 16 giờ nhóm, chia đều | Đạt, Nghị |

**Đầu vào** · SP-021.

### 1. Mục tiêu

**Điều phối và lập hồ sơ mới.** Thực hiện phần điều phối còn lại của SP-055. Mỗi buổi phải cho biết bản tích hợp nào chạy, phần nào đang chặn và ai nhận việc tiếp theo.

### 2. Phạm vi

- Chuẩn bị họp, demo và đầu vào báo cáo. Người trực thực hiện việc gửi theo ủy quyền của nhóm.
- Hai lượt họp ngắn mỗi tuần vào 24 và 27/11, 30/11 và 02/12. Demo bản tích hợp 28/11 và 03/12. Ghi revision, đầu ra từng người, giờ còn lại và blocker cần quyết định. TV4 trực tuần 4, TV1 trực tuần 5.

### 3. Tệp và module liên quan

- [CONTRIBUTING.md](../../../CONTRIBUTING.md) · [week-4-5-taskbrief.md](../../../docs/TaskBrief/W4-W5/week-4-5-taskbrief.md)

### 4. Các bước thực hiện

1. Đối chiếu biên bản tuần 2 một kênh và tuần 3 đa kênh. Lập danh sách các đầu ra kế thừa hoặc còn thiếu.
2. Tại mỗi cuộc họp, ghi tiến độ so với hạn, người phụ trách, phụ thuộc và quyết định xử lý blocker.
3. Demo bản đã tích hợp, ghi SHA, kết quả thực tế. Bàn giao báo cáo tuần cho người trực.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **INFRA** — Có biên bản 4 cuộc họp (24, 27, 30/11 và 02/12) và 2 demo (28/11, 03/12), mỗi demo có revision và kết quả. Mỗi blocker còn mở có 1 owner và mốc xem lại.
- [ ] **DOCS** — Đủ 2 báo cáo có Current tasks, Next plan, Blockers và link truy vết. Biên bản cuối đối chiếu 44 mã với ma trận, ghi rõ mã thiếu.
- [ ] **DOCS** — Bảng phối hợp ghi 2h/người/tuần theo kế hoạch gốc, 32h nhóm cho tuần 2–5, trong đó 16h thuộc đợt hiện tại. Số giờ thực tế được ghi riêng và không cộng trùng theo ticket.

### 6. Bằng chứng nộp

- [ ] Ngày 28/11 Nghị chốt báo cáo tuần 4. Ngày 03/12 Minh chuẩn bị báo cáo tuần 5 với Current tasks, Next plan, Blockers và link ticket/PR. Báo cáo tuần theo lịch nhóm, việc gửi do người trực thực hiện theo ủy quyền.
- [ ] Gắn hiện vật hoặc liên kết vào issue #57 và biên bản tương ứng. Ghi ngày, người lập và phiên bản nguồn, kiểm tra người nhận mở được các liên kết.

### 7. Điều kiện reviewer approve

1. Đạt và Nghị đối chiếu bảng giao việc với bằng chứng của #57.
2. Kiểm ngày họp, mở revision demo và chọn một blocker để truy đến owner và quyết định xử lý. Yêu cầu bổ sung nếu báo cáo ghi hoàn tất mà thiếu kết quả trên bản tích hợp.
3. Hai reviewer kiểm hồ sơ nếu có PR tài liệu. Buổi họp/demo được xác nhận bằng biên bản.

### 8. Rủi ro và lưu ý

- Thiếu quyền API và chờ reviewer có thể ảnh hưởng ngay tới demo. Nêu tác động tại lần phát hiện.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Biên bản, link demo và bản báo cáo tuần được nhóm lưu ở đâu? | Minh, người trực tuần | Trước khi tổng hợp từng tuần | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-052"></a>

## SP-052 — Nghiệm thu liên thông, phục hồi, migration và bảo mật

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#54](https://github.com/nigPeter2503/Social_Pilot/issues/54) | INFRA, F1.B4, F2.B5, F5.B3, F8.N1, F9.B1, F9.B2 | 28/11–02/12 | 16 | Thiện, Nghị |

**Đầu vào** · SP-024, SP-026, SP-033, SP-044, SP-049, SP-050, SP-030, SP-051.

### 1. Mục tiêu

**Nghiệm thu liên thông.** Kiểm tra các luồng xuyên module trên bản ứng viên. Mỗi lỗi có ca tái hiện, người sửa và kết quả chạy lại để Nghị chốt SP-056 trên cùng revision.

### 2. Phạm vi

- Minh điều phối kiểm tra. Chủ module sửa lỗi phần mình. Ma trận và video cuối thuộc SP-056.
- Đề xuất Minh nhận điều phối ticket liên thông từ Thiện để Thiện giữ editor, dashboard và APK. Thiện bàn giao bộ Espresso/CI, Đạt cung cấp ledger và chuỗi migration, Nghị cấp bộ snapshot/comment đã ẩn dữ liệu nhạy cảm. Chuẩn bị ca thử trên các module đã nhập main từ 28/11. Chốt bản ứng viên sau các PR tính năng ngày 30/11. Chạy bộ ca liên thông ngày 01/12, sửa lỗi và kiểm chứng lại sáng 02/12.

### 3. Tệp và module liên quan

- [architecture.md](../../../docs/architecture.md) · [room-schema-contract.md](../../../docs/room-schema-contract.md) · [build-verification.md](../../../docs/build-verification.md) · [development.md](../../../docs/development.md)

### 4. Các bước thực hiện

1. Ghi SHA Android/backend, thiết bị và các version Room hỗ trợ. Chuẩn bị tập dữ liệu đối chiếu trước/sau.
2. Chạy các ca trong bảng dưới đây. Ghi riêng phép thử API thật và phép gây lỗi có kiểm soát, kỳ vọng và kết quả từng ca.
3. Chạy Android assembleDebug, testDebugUnitTest, lintDebug qua wrapper, chạy backend test và bootJar qua wrapper backend. Giao lỗi cho owner, xác nhận lại ca lỗi và luồng bị ảnh hưởng trên SHA mới rồi chuyển kết quả cho SP-056.

| Ca liên thông trên bản ứng viên | Kết quả phải đối chiếu |
|---|---|
| Brief VI/EN → hai nhánh AI → sửa ảnh → duyệt → hai bài thật | Chồng lấp thời gian, ảnh và version khớp từng đích. Bấm đôi và timeout sau gửi chỉ có một bài mỗi phiên bản/kênh. Mở đúng permalink. |
| Hai nhánh cùng chạm trần, hủy, xoay, kill rồi mở lại | Dừng dispatch mới, giữ usage/chi phí chưa xác định, chỉ chạy lại nhánh chọn và khôi phục checkpoint. |
| Comment thật → gợi ý sửa → reply → làm mới hai lần | Một reply đúng thread, ca cấm auto-reply có số lượt gửi bằng 0. |
| Mất mạng rồi restart, đổi VI/EN và phục hồi mạng | Đọc draft, comment, snapshot và goal đã cache, thấy độ cũ. Queue chỉ nhận bản đã duyệt và tiếp tục khi đủ điều kiện. |
| Nâng cấp Room từ từng phiên bản hỗ trợ, ngắt một kênh lúc job đang chạy | Đối chiếu số dòng/khóa trước–sau cho draft, queue, usage, comment, snapshot, ảnh. Dữ liệu kênh kia còn đúng, callback muộn bị bỏ. |
| Thu hồi quyền, hết hạn token, 429, provider lỗi và backup/log | Hiện đúng bước phục hồi, giới hạn retry. Kiểm bản export, backup, log và APK bằng credential thử. LLM key chỉ tới đúng provider, client secret chỉ ở backend. |

Ghi kết quả trên API 23, API 36 và thiết bị demo, CI của SHA đang thử và bằng chứng app/API thật. Lỗi mất dữ liệu, đăng/reply trùng, lộ secret, vượt ngân sách hoặc ghi sai phiên bản chặn kết luận đạt. Chủ module nhận lỗi ngay trong ngày, chạy lại ca lỗi và luồng kề sau sửa.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F1.B4** — Ngắt A và chạy callback cũ tạo 0 dữ liệu trở lại. Kênh B còn đọc đủ draft và ảnh dùng chung.
- [ ] **F2.B5, F8.N1** — Hủy nhánh A không làm mất kết quả nhánh B. Có 0 lệnh mới sau khi hết trần, khoản đã gửi chưa rõ chi phí được giữ để đối soát.
- [ ] **F5.B3** — Bấm đôi, timeout sau gửi và restart để lại đúng 1 bài thật trên từng đích cho cùng version. Ca reply lặp chỉ có 1 reply đúng thread.
- [ ] **F9.B1, F9.B2** — Chạy luồng chính bằng cả 2 ngôn ngữ. Sau restart ở chế độ máy bay, đọc được draft, comment và snapshot đã cache cùng trạng thái dữ liệu cũ.
- [ ] **INFRA** — Mỗi version Room còn hỗ trợ có kết quả migration đối chiếu draft, queue, usage, comment và snapshot. Có 0 credential thử lộ qua APK/log/backup/export. Mỗi lỗi nghiêm trọng có kết quả chạy lại đạt trước khi xác nhận hoàn tất.

### 6. Bằng chứng nộp

- [ ] Cuối buổi sáng 02/12, giao Nghị bảng kỳ vọng/thực tế, lỗi, bản sửa và kết quả chạy lại trên cùng SHA. Mỗi owner sửa lỗi module mình. Nếu RC đổi sau đó, đánh dấu các bằng chứng bị ảnh hưởng và chạy lại trước SP-056/058.
- [ ] Gắn hiện vật hoặc liên kết vào issue #54, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Thiện và Nghị đối chiếu bảng giao việc với bằng chứng của #54.
2. Lấy một ca thật và một ca phục hồi, dò từ revision tới dữ liệu đầu vào và kết quả sau sửa. Từ chối kết luận đạt nếu chỉ có fixture cho luồng API hoặc lỗi nghiêm trọng còn mở.
3. Đối chiếu kết quả với bảng ca liên thông ở mục 4 và lưu link lần chạy lại.

### 8. Rủi ro và lưu ý

- Khi SHA đổi, kết quả trước đó cần đánh dấu phạm vi bị ảnh hưởng. Không đưa credential thật vào dữ liệu tái hiện.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | SHA ứng viên, danh sách migration hỗ trợ và thiết bị thử nào dùng cho lượt chạy cuối? | Minh, Đạt và Thiện | Trước khi chạy bộ liên thông | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-059"></a>

## SP-059 — Kịch bản demo và xác nhận bàn giao cuối kỳ

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#61](https://github.com/nigPeter2503/Social_Pilot/issues/61) | DOCS | 01–03/12 | 4 | Đạt, Nghị |

**Đầu vào** · SP-056, SP-057, SP-058.

### 1. Mục tiêu

**Soạn tài liệu và bàn giao.** Soạn kịch bản mới và thực hiện bàn giao cuối kỳ. Người nhận có thể mở đúng bản cài, lần theo demo và xác định phần phạm vi đã đạt.

### 2. Phạm vi

- Dùng manifest đã khóa ở SP-058 và kết luận SP-056/057. Chủ module cung cấp nội dung cho phần trình bày.

### 3. Tệp và module liên quan

- [SocialPilot_Proposal_Java.md](../../../docs/SocialPilot_Proposal_Java.md) · [week-4-5-taskbrief.md](../../../docs/TaskBrief/W4-W5/week-4-5-taskbrief.md) · [requirements.md](../../../docs/requirements.md)

### 4. Các bước thực hiện

1. Chia kịch bản theo luồng: Minh kết nối/đăng, Đạt tạo/hủy và ngân sách, Thiện sửa/duyệt, Nghị đối chiếu reply/snapshot. Ghi dữ liệu đầu vào, kết quả mong đợi và cách chuyển sang video hoặc ca offline khi lỗi.
2. Đối chiếu APK/backend/video theo manifest, thử mở video, chuẩn bị tài khoản demo và duyệt trước nội dung được phép đăng.
3. Chạy kịch bản chiều 03/12, lập biên bản quyết định theo từng mã còn thiếu và ghi người nhận.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **DOCS** — Mỗi bước kịch bản có 1 người thực hiện, kết quả mong đợi và phương án khi lỗi. Revision APK/backend và video khớp manifest ở cả 3 hiện vật.
- [ ] **DOCS** — Biên bản kiểm đủ 8 nhóm sản phẩm và đối chiếu 44/44 dòng theo ma trận. Chỉ ghi hoàn thành 100% khi cả 44 dòng đạt. Mỗi dòng chưa đạt có owner và quyết định xử lý.
- [ ] **DOCS** — Có 1 biên bản demo ghi thời điểm và người nhận. Nếu sửa tài liệu repo, PR có 2 approval người thật.

### 6. Bằng chứng nộp

- [ ] Chiều 03/12 chạy theo manifest của SP-058, đối chiếu APK, backend, slide, video và tám nhóm bàn giao. Ghi người nhận, giờ, revision, hạn chính thức nếu có thông báo và kết luận theo số mã thực đạt.
- [ ] Gắn hiện vật hoặc liên kết vào issue #61, kèm PR khi có thay đổi repo. Ghi ngày, người lập và phiên bản nguồn, kiểm tra người nhận mở được các liên kết.

### 7. Điều kiện reviewer approve

1. Đạt và Nghị đối chiếu bảng giao việc với bằng chứng của #61.
2. So kịch bản với manifest, thử mở hiện vật và đối chiếu kết luận cuối với ma trận. Yêu cầu sửa khi có bước dùng revision khác hoặc một mã chưa đạt được tính vào tổng đạt.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Quyền API có thể thay đổi khi trình diễn. Ghi tình trạng thực tế và dùng video đã kiểm đúng revision để minh họa.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Ai nhận bộ bàn giao, và nhóm đã nhận thông báo chính thức nào về hạn nộp/bảo vệ? | Minh | Trước khi lập biên bản bàn giao | Chờ ghi câu trả lời và nguồn đối chiếu. |

Khi giao từng ticket, gắn SHA/PR, lệnh và kết quả thực tế, ảnh/log đã che bí mật, người nhận và việc còn chặn. Quy trình kiểm tra và điều kiện đóng ticket nằm trong [brief chung](week-4-5-taskbrief.md#kiem-chung).
