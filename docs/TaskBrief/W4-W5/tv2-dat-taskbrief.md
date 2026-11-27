# Nguyễn Đức Đạt (TV2) — Taskbrief 24/11–03/12/2026

Bản phân công đề xuất cho Nguyễn Đức Đạt (`@dat532006`). Đạt sở hữu nền tảng AI và ledger chung. Các prompt cho editor, inbox và báo cáo thuộc người làm module. Đạt chốt số version và thứ tự migration cho từng PR, từng lần chỉ một người sửa chuỗi migration chung.

Ước lượng 32 giờ thực hiện ticket. Các dòng “Đầu vào” dùng lại kết quả của bộ brief tuần 2–3 theo giả định lập kế hoạch. Ngày 30/11 khóa tính năng, 01–02/12 nghiệm thu và sửa lỗi, 03/12 bàn giao. [Lịch chung và phụ thuộc](week-4-5-taskbrief.md)

<a id="sp-032"></a>

## SP-032 — Sinh ảnh riêng từng kênh và template dự phòng

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#34](https://github.com/nigPeter2503/Social_Pilot/issues/34) | F2.B3, F8.B3, F8.B4 | Đầu vào | 0 | Thiện, Nghị |

**Đầu vào** · SP-028, SP-029, SP-016.

### 1. Mục tiêu

SP-032 trong đợt này là **kiểm tra lại đầu ra tuần 2–3**, với **0 giờ phát triển mới**. Đạt đối chiếu ảnh, template và chi phí trên bản kế thừa. Nếu thiếu hồ sơ, bổ sung bằng chứng. Chỉ khi phép thử xác nhận thiếu chức năng hoặc có lỗi mới ghi phần mã cần sửa, ca tái hiện và ước lượng vào #34.

### 2. Phạm vi

- **Kiểm tra** — Ảnh của hai kênh, lựa chọn model, template cục bộ, tham chiếu ảnh giao editor và sổ chi phí theo từng lệnh.
- **Ngoài phạm vi nghiệm thu này** — Viết adapter ảnh mới, đóng gói cả nhánh (SP-031), sinh lại ảnh/alt text trong editor (SP-039) và đăng bài (SP-025).

**Bảng đối chiếu trước khi giao.** Cột cuối ghi phần cần chốt cho SP-032. Đạt điền thông số đã duyệt và dẫn nguồn từ PR hoặc cấu hình theo câu hỏi Q1. Nếu một kênh thiếu chuẩn, ghi F2.B3 của kênh đó là *chờ xác nhận*.

| Kênh | Số liệu và mẫu ảnh đã ghi trong dự án | Chuẩn nghiệm thu SP-032 |
|---|---|---|
| Facebook Page | PNG **1200 × 630 px**, nền xám nhạt, chữ “SocialPilot SP-010” căn giữa. Đây là tệp đầu vào của phép thử đăng ảnh. [Hồ sơ SP-010](../../evidence/sp-010-photo-alt-text-2026-11-12.md) | Đã có kích thước và mô tả mẫu để đối chiếu. Chờ Q1 xác nhận mẫu này có áp dụng cho SP-032 hay cung cấp cấu hình thay thế. |
| Instagram Professional | SP-065 ghi ảnh **JPEG**, media nguồn `18028517477875672`, có ảnh chụp bài đăng để đối chiếu. Hồ sơ không ghi kích thước pixel của tệp gốc hoặc preset phong cách. [Hồ sơ SP-065 trên revision đã kiểm tra](https://github.com/nigPeter2503/Social_Pilot/blob/5ad8050ef6e619b59f98ceaf3a21951b5e2bb7b1/docs/sp-065-instagram-oauth-verification.md) | Chờ Q1 cung cấp chiều rộng × chiều cao và preset hoặc mẫu được duyệt. **1080 × 1080 px** là ví dụ khi soạn brief. |

### 3. Tệp và module liên quan

- Hợp đồng Android về khả năng model và giữ trước chi phí: [ModelCapabilities.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/ai/ModelCapabilities.java), [BudgetManager.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/BudgetManager.java). Các [adapter hiện có](../../../android/app/src/main/java/com/socialpilot/app/data/ai/) được mô tả trong [hợp đồng AI](../../ai-provider-contract.md) cho tác vụ văn bản.
- Bàn giao ảnh theo phiên bản: [ContentVersion.java](../../../android/app/src/main/java/com/socialpilot/app/domain/editor/ContentVersion.java), [hợp đồng editor](../../sp-016-brief-contract.md) và [quy tắc lưu, dọn ImageRef](../../room-schema-contract.md).
- Mẫu kiểm thử hợp đồng: [AiProvidersTest.java](../../../android/app/src/test/java/com/socialpilot/app/data/ai/AiProvidersTest.java) và [BudgetContractTest.java](../../../android/app/src/test/java/com/socialpilot/app/domain/contract/ai/BudgetContractTest.java). Phạm vi gốc của ảnh/template nằm trong [brief SP-032 tuần 2–3](../W2-W3/tv2-dat-taskbrief.md#sp-032).

### 4. Các bước thực hiện

1. Lấy PR, SHA và hồ sơ của #34. Đối chiếu model SP-028, ngân sách SP-029 và phiên bản editor SP-016. Điền bảng chuẩn từ nguồn đã duyệt trước khi đánh giá ảnh.
2. Mở tệp ảnh của từng kênh từ cùng brief. Ghi chiều rộng × chiều cao đọc từ tệp, preset hoặc mẫu đối chiếu và nguồn tạo (API/template). Đối chiếu riêng một ca API thật và một ca thiếu API dùng template.
3. Dùng `requestId` nối log ảnh với khoản giữ trước và chi phí. Đối chiếu kết quả các ca model thiếu khả năng sinh ảnh, hết ngân sách, lỗi provider và hủy sau khi gửi.
4. Kiểm tra bàn giao qua editor theo ba ca độc lập.

   - **4a · Thay ảnh hợp lệ.** Mở ảnh qua tham chiếu, thay ảnh ở kênh đang duyệt. **Kỳ vọng:** editor mở đúng tệp, tạo một phiên bản mới chưa duyệt. Trạng thái duyệt của kênh kia giữ nguyên.
   - **4b · Callback đến muộn.** Cho lệnh bắt đầu ở v1, chuyển sang v2 và duyệt v2 trước khi trả ảnh của v1. **Kỳ vọng:** cả `currentVersionId` và `approvedVersionId` vẫn là v2, số phiên bản phát sinh do callback cũ bằng 0.
   - **4c · Ngắt kênh dùng chung ảnh.** Cho A và B cùng tham chiếu một tệp, ngắt A rồi trả callback cũ của A. **Kỳ vọng:** callback tạo 0 tham chiếu mới cho A, B vẫn mở được tệp. Ghi số tham chiếu trước/sau và kết quả mở ảnh của B.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F2.B3** — Có chuẩn được xác nhận cho 2/2 kênh trong bảng mục 2. Hai tệp mở được, chiều rộng và chiều cao đọc từ tệp khớp từng giá trị đã duyệt. Mỗi ảnh có preset khớp cấu hình hoặc ảnh đối chiếu kèm xác nhận của reviewer theo mẫu đã duyệt. Có bằng chứng riêng cho ít nhất 1 ca API ảnh thật và 1 ca template cục bộ có nhãn.
- [ ] **F8.B3** — Đọc lại đúng model đã lưu cho tác vụ ảnh. Với từng trạng thái không hỗ trợ hoặc chưa rõ khả năng sinh ảnh, số lệnh gửi tới model đó bằng 0. Khi không có API ảnh, template tạo được ảnh với 0 lần gọi provider.
- [ ] **F8.B4** — Mỗi lệnh ảnh đã gửi có khoản giữ trước theo `requestId`. Khi hết trần, số lệnh mới bằng 0. Chi phí ảnh được ghi riêng, tổng theo provider/ngày/tháng khớp các khoản của kỳ. Token thiếu giữ trạng thái thiếu, số tiền ước tính có nhãn.
- [ ] **F2.B3, F8.B4** — Ca API lỗi hoặc bị hủy giữ nguyên văn bản đã tạo. Khi có thể đã gửi mà chi phí chưa rõ, khoản giữ trước ở trạng thái chờ đối soát. Ca hủy dọn tệp tạm và giữ nguyên mọi tệp còn tham chiếu.
- [ ] **F2.B3** — Editor nhận đúng tham chiếu và metadata. Thay ảnh hợp lệ tạo phiên bản chưa duyệt. Callback của phiên bản cũ hoặc kênh đã ngắt tạo 0 phiên bản mới. Trong lịch sử và hồ sơ nộp có 0 key, token hoặc URL ký tạm.

### 6. Bằng chứng nộp

- [ ] Đính kèm hoặc dẫn link từ **issue #34 và PR tương ứng** đến ảnh của hai kênh, ảnh chụp editor, log API/template đã che bí mật và bản ghi chi phí. Ghi SHA, môi trường, thời điểm, provider/model, kích thước và kết quả mong đợi/thực tế. Dùng lại tệp trong [thư mục bằng chứng](../../evidence/) nếu đã có.
- [ ] Trong PR, dẫn kết quả CI trên SHA được review và kết quả các ca lỗi/hủy/dọn tệp. Với thay đổi Android, ghi kết quả chạy từ `android/`: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Phân biệt ca dùng dữ liệu giả với lần gọi API thật.

### 7. Điều kiện reviewer approve

1. Thiện và Nghị mở bằng chứng từ #34, đối chiếu SHA và nguồn xác nhận bảng chuẩn. Khi Q1 chưa đủ cho cả hai kênh, giữ F2.B3 ở trạng thái chờ.
2. Kiểm từng dòng DoD ở mục 5, đối chiếu pixel từ tệp gốc, kết quả 4a–4c và sổ chi phí cùng `requestId`.
3. Yêu cầu bổ sung nếu thiếu bằng chứng API thật hoặc CI của revision. Yêu cầu sửa nếu ảnh sai chuẩn đã duyệt, chi phí ghi sai, dữ liệu bị mất hoặc lộ bí mật. Hai reviewer xác nhận khi đủ bằng chứng và mọi điều kiện đều đạt. Nếu có PR sửa lỗi, cả hai approve revision cuối sau khi checks đạt.

### 8. Rủi ro và lưu ý

- Kích thước ảnh chụp màn hình và kích thước tệp ảnh gốc có thể khác nhau. Khi thiếu tệp hoặc cấu hình nguồn, giữ nguyên trạng thái chờ.
- Ưu tiên hồ sơ đã có. Nếu cần gọi lại API thật, kiểm quyền thử, số lượt và trần chi trước khi gửi. Hủy ở thiết bị vẫn có thể phát sinh phí tại provider.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Với từng kênh, kích thước pixel và preset hoặc mẫu phong cách nào đã được duyệt cho SP-032? | Đạt cung cấp cấu hình, Thiện đối chiếu mẫu trong editor | Trước khi giao bảng chuẩn cho reviewer | Chờ điền thông số của hai kênh và link cấu hình/mẫu đã duyệt. |
| Q2 | Provider/model ảnh, quota, số lượt, trần chi và bảng giá nào áp dụng cho lần thử? | Đạt | Trước khi đối soát chi phí hoặc gọi bổ sung | Chờ điền model, giới hạn và nguồn giá áp dụng. |
| Q3 | PR/SHA nào chứa adapter ảnh và hồ sơ thử API thật, template, hủy? | Đạt | Khi nhận đầu vào, trước bước 1 | Chờ điền PR/SHA, đường dẫn adapter và hồ sơ từng ca. |

<a id="sp-031"></a>

## SP-031 — Orchestrator hai nhánh và chuỗi tạo nội dung theo kênh

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#33](https://github.com/nigPeter2503/Social_Pilot/issues/33) | F2.B1, F2.B2 | Đầu vào | 0 | Thiện, Nghị |

**Đầu vào** · SP-014, SP-028, SP-029, SP-035.

### 1. Mục tiêu

SP-031 là **nghiệm thu đầu ra kế thừa, 0 giờ phát triển mới**. Đạt chứng minh một brief khởi chạy đúng các kênh đã chọn và mỗi nhánh thành công giao cho editor một gói kết quả riêng, đủ sáu bước.

### 2. Phạm vi

- **Kiểm tra** số nhánh, thời gian chạy chồng lấp, thứ tự các bước, lỗi theo bước và gói văn bản/ảnh theo kênh. SP-035 cung cấp brief, SP-014 cung cấp cơ chế điều phối, SP-028/029 kiểm soát các lệnh AI.
- **Ranh giới bàn giao** — SP-032 cung cấp ảnh hoặc template. SP-031 đóng gói đầu ra có kiểu dữ liệu xác định cho SP-036 tạo phiên bản chưa duyệt. Phục hồi checkpoint thuộc SP-033, đăng bài thuộc SP-025. Chỉ ghi việc sửa mã khi ca kiểm tra tái hiện được lỗi.

### 3. Tệp và module liên quan

- [AgentOrchestrator.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/AgentOrchestrator.java) khai báo điểm vào điều phối. [AiRequest.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/ai/AiRequest.java) và [hợp đồng AI](../../ai-provider-contract.md) mô tả cách truy từ lệnh về phiên, kênh và bước.
- [ContentVersion.java](../../../android/app/src/main/java/com/socialpilot/app/domain/editor/ContentVersion.java) và [hợp đồng editor](../../sp-016-brief-contract.md) dùng để đối chiếu phiên bản mà editor nhận. Chuỗi sáu bước và ca một/hai kênh nằm trong [brief SP-031 tuần 2–3](../W2-W3/tv2-dat-taskbrief.md#sp-031).

### 4. Các bước thực hiện

1. Lấy PR/SHA bàn giao của #33 và bộ 20 brief. Đánh dấu kênh đích, ngôn ngữ, kết quả hoặc lỗi của từng mẫu.
2. Đối chiếu hai ca một kênh: một brief chỉ chọn Facebook Page, một brief chỉ chọn Instagram Professional. Đếm nhánh và lệnh gửi theo kênh.
3. Với một brief chọn cả hai, đọc thời điểm bắt đầu/kết thúc của từng nhánh trong cùng phiên. Đối chiếu từng bước với gói kết quả mà editor nhận.
4. Dùng ca lỗi có kiểm soát ở một bước của một nhánh. So sánh tên bước lỗi, kết quả một phần và gói đã hoàn tất của nhánh kia.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F2.B1** — Cả 2/2 ca một kênh đều tạo đúng 1 nhánh và gửi 0 lệnh cho kênh ngoài brief.
- [ ] **F2.B1** — Trong ca hai kênh, cùng một brief tạo đúng 2 nhánh. Trên cùng trục thời gian, thời điểm bắt đầu muộn hơn **nhỏ hơn** thời điểm kết thúc sớm hơn. Khoảng chạy chồng lấp phải lớn hơn 0.
- [ ] **F2.B2** — Mỗi nhánh thành công có đủ 6/6 bước theo thứ tự: dàn ý → bản nháp → rà quy tắc kênh → mô tả ảnh → ảnh/template từ SP-032 → đóng gói. Với ca hai kênh thành công, editor đọc được 2/2 gói qua cấu trúc dữ liệu đã bàn giao. Văn bản, tham chiếu ảnh, kênh và phiên bản khớp nguồn của từng nhánh. Cả hai phiên bản mới đều chưa duyệt.
- [ ] **F2.B1, F2.B2** — Trong ca gây lỗi, log chỉ đúng kênh và bước đã gây lỗi. Nhánh lỗi có 0 gói mang trạng thái hoàn tất, nhánh kia giữ đủ 1 gói kết quả của chính nó.

### 6. Bằng chứng nộp

- [ ] Gắn vào **issue #33 và PR bàn giao** đường dẫn tới SHA, kết quả CI và log có thời điểm bắt đầu/kết thúc, mã phiên, kênh và bước. Kèm hai gói đầu ra đã che dữ liệu nhạy cảm, ảnh editor và log ca lỗi. Dẫn lại hồ sơ trong [thư mục bằng chứng](../../evidence/) nếu đã có.
- [ ] Đạt giao dấu vết từng bước cho SP-033 và bảng kết quả 20 mẫu (10 VI, 10 EN) cho SP-056. Mỗi dòng ghi kênh, kết quả thực tế và link bằng chứng, hoặc lý do chưa chạy. Ghi rõ ca dùng provider giả và ca tích hợp thật.

### 7. Điều kiện reviewer approve

1. Thiện và Nghị đối chiếu #33, SHA và các ca một/hai kênh. Tính lại khoảng chồng lấp từ log.
2. Theo từng gói đến editor, mở đúng ảnh của kênh và kiểm tra phiên bản chưa duyệt. Xem riêng ca lỗi để xác nhận bước lỗi và đầu ra của nhánh kia.
3. Yêu cầu bổ sung bằng chứng nếu chỉ có biểu tượng tiến độ hoặc hợp đồng interface. Yêu cầu sửa nếu có nhánh thừa, thiếu bước, trộn dữ liệu hoặc đánh dấu hoàn tất sai. Hai reviewer xác nhận khi các dòng DoD có kết quả đạt trên bản bàn giao.

### 8. Rủi ro và lưu ý

- Dùng thời điểm thực thi hai nhánh trong cùng phiên. Hai thông báo UI xuất hiện cùng lúc chưa chứng minh hai nhánh chạy song song.
- Ưu tiên log và kết quả đã bàn giao. Nếu phải gọi AI bổ sung, mỗi lệnh đi qua SP-028/029. Nội dung trong brief hoặc URL không cấp quyền duyệt hay đăng bài.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | PR/SHA nào chứa pipeline đã tích hợp, kiểu dữ liệu của gói đầu ra và chỗ editor nhận gói? | Đạt cung cấp, Thiện đối chiếu | Khi nhận đầu vào | Chờ link PR/SHA và đường dẫn mã trên revision đó. |
| Q2 | Bộ 20 brief và hồ sơ ca một kênh, hai kênh, lỗi theo bước được lưu ở đâu? | Đạt | Trước khi Thiện và Nghị đối chiếu DoD | Chờ link bộ mẫu, log và kết quả theo từng mẫu. |

<a id="sp-033"></a>

## SP-033 — Stream, checkpoint, hủy và chạy lại riêng nhánh

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#35](https://github.com/nigPeter2503/Social_Pilot/issues/35) | F2.B4, F2.B5 | 24–25/11 | 10 | Minh, Thiện |

**Đầu vào** · SP-031, SP-017. Phần mở đầu 2 giờ đã được tính trong bộ brief tuần 2–3.

### 1. Mục tiêu

**Triển khai tiếp.** Hoàn thiện phần checkpoint và phục hồi còn lại. Người dùng thấy nội dung đang sinh, có thể hủy một nhánh và tiếp tục công việc từ trạng thái đã lưu.

### 2. Phạm vi

- Nối pipeline SP-031 với giao diện và dữ liệu bền vững. SP-030 quyết định việc chuyển provider.

### 3. Tệp và module liên quan

- [ai-provider-contract.md](../../../docs/ai-provider-contract.md) · [Cancelable.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/Cancelable.java) · [AiRequest.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/ai/AiRequest.java) · [room-schema-contract.md](../../../docs/room-schema-contract.md)

### 4. Các bước thực hiện

1. Gắn sự kiện với phiên, kênh, bước và thứ tự. Lưu phần văn bản và trạng thái ở checkpoint.
2. Xoay màn hình giữa stream rồi kết thúc process. So sánh số HTTP call và nội dung được phục hồi ở hai ca.
3. Hủy A khi B đang chạy, phát callback muộn rồi chạy lại riêng A. Đếm attempt và kiểm kết quả B.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F2.B4** — Nhìn thấy ít nhất 1 đoạn trước terminal. Kết quả cuối khớp chuỗi chunk theo thứ tự, số chunk mất/lặp bằng 0. Xoay màn hình tạo 0 HTTP call mới.
- [ ] **F2.B4** — Sau process death, checkpoint còn đọc được, UI báo gián đoạn và có 0 lệnh tính phí tự chạy lại.
- [ ] **F2.B5** — Hủy kích hoạt cả Future và HTTP Call của nhánh chọn. Callback cũ ghi thêm 0 kết quả. Retry A tạo 0 attempt mới cho B, kết quả và ledger của B giữ nguyên. Số tác vụ chạy đồng thời không vượt giới hạn executor đã cấu hình.

### 6. Bằng chứng nộp

- [ ] Ngày 25/11 giao Minh và Thiện log thứ tự chunk, số HTTP call qua rotate/kill và ảnh UI trước/sau. Hủy một nhánh đang stream, cho callback cũ về rồi retry riêng nhánh đó. Đầu ra và ledger nhánh kia giữ nguyên.
- [ ] Gắn hiện vật hoặc liên kết vào issue #35, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Minh và Thiện đối chiếu bảng giao việc với bằng chứng của #35.
2. Đếm request khi xoay và khi khởi động lại, đối chiếu log hủy cả hai handle rồi mở lại kết quả B. Từ chối khi callback cũ có thể ghi đè checkpoint.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- LiveData có thể gộp cập nhật. Tích lũy nội dung theo thứ tự sự kiện trước khi phát trạng thái tới View.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Revision pipeline/checkpoint và giới hạn executor nào được dùng cho phép thử? | Đạt | Trước khi đối chiếu log trên thiết bị | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-030"></a>

## SP-030 — Chuyển provider dự phòng có kiểm soát và không nhân đôi kết quả

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#32](https://github.com/nigPeter2503/Social_Pilot/issues/32) | F8.N2 | 26–27/11 | 8 | Minh, Nghị |

**Đầu vào** · SP-028, SP-029, SP-033.

### 1. Mục tiêu

**Triển khai mới.** Bổ sung chuyển provider dự phòng khi provider chính gặp lỗi khả dụng. Một tác vụ chỉ có một kết quả cuối và mọi lượt gọi đi qua cùng ngân sách.

### 2. Phạm vi

- Áp dụng trên cơ chế attempt/cancel của SP-033. Việc chọn model và cấp quyền gọi dùng đầu vào SP-028/029.

### 3. Tệp và module liên quan

- [ai-provider-contract.md](../../../docs/ai-provider-contract.md) · [BudgetManager.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/BudgetManager.java) · [ModelCapabilities.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/ai/ModelCapabilities.java) · [AiUsage.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/ai/AiUsage.java)

### 4. Các bước thực hiện

1. Phân loại lỗi trước và sau dispatch. Kiểm key, khả năng model và phần ngân sách còn lại trước khi chọn provider phụ.
2. Cấp requestId mới cho attempt phụ, reserve rồi mới gửi. Lưu nguyên khoản chưa rõ của lượt chính để đối soát.
3. Thử thành công, provider phụ lỗi, bị hủy, thiếu key/capability, hết quota và hết trần. So số request, output và ledger.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F8.N2** — Với lỗi khả dụng đủ điều kiện, đúng 1 attempt phụ ở ca chuyển thành công và có đúng 1 kết quả cuối. Callback lặp tạo 0 bản sao văn bản/ảnh.
- [ ] **F8.N2** — Khi bị hủy, vượt trần, model không phù hợp hoặc chưa rõ kết quả cần đối soát, số lần tự chuyển để gửi tiếp bằng 0. Lỗi/thiếu quota ở provider phụ có lý do dừng.
- [ ] **F8.N2** — Mỗi attempt đã dispatch có requestId và reservation riêng. Tổng usage/chi phí của ca thử khớp các lượt thực tế, mỗi lượt được ghi một lần. Khoản uncertain còn theo dõi sau khi hủy.

### 6. Bằng chứng nộp

- [ ] Giao SP-052 trace có một kết quả cuối và hai lượt tính phí theo thực tế. Chạy thêm các ca provider phụ thiếu key, hết trần, không hỗ trợ task hoặc người dùng đã hủy.
- [ ] Gắn hiện vật hoặc liên kết vào issue #32, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Minh và Nghị đối chiếu bảng giao việc với bằng chứng của #32.
2. Theo cùng một tác vụ qua hai provider, so quyết định chuyển với loại lỗi rồi đếm bản ghi kết quả và chi phí. Từ chối khi đổi provider cho phép vượt trần hoặc gửi lại mù.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Provider có thể tính phí dù máy khách đã hủy. Không release một khoản đã có khả năng dispatch như khoản chưa gửi.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Cặp provider/model đủ quyền và giới hạn số lượt, trần chi nào dùng cho lần kiểm chứng thật? | Đạt | Trước khi thử chuyển provider thật | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-034"></a>

## SP-034 — Nhật ký agent theo phiên/kênh và xóa log an toàn

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#36](https://github.com/nigPeter2503/Social_Pilot/issues/36) | F2.B6 | 28–29/11 | 8 | Thiện, Nghị |

**Đầu vào** · SP-031, SP-028.

### 1. Mục tiêu

**Triển khai mới.** Bổ sung nhật ký xem được theo phiên, kênh và bước. Người dùng kiểm tra nội dung, usage, chi phí và xóa đúng phần mình chọn.

### 2. Phạm vi

- Lấy sự kiện từ SP-031/028 và cung cấp điểm xóa cho SP-024. Chỉ ghi prompt, kết quả và metadata cần hiển thị.

### 3. Tệp và module liên quan

- [ai-provider-contract.md](../../../docs/ai-provider-contract.md) · [AiRequest.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/ai/AiRequest.java) · [AiUsage.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/ai/AiUsage.java) · [AiCostEstimate.java](../../../android/app/src/main/java/com/socialpilot/app/domain/contract/ai/AiCostEstimate.java) · [room-schema-contract.md](../../../docs/room-schema-contract.md)

### 4. Các bước thực hiện

1. Ghi một phiên hai kênh, gồm bước thành công và lỗi/hủy. So từng dòng với request và terminal.
2. Mở log/checkpoint khi offline, đối chiếu usage thật, thiếu usage và chi phí ước tính.
3. Xóa một phiên hoặc kênh rồi mở lại app. Kiểm log còn lại và callback tới muộn.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **F2.B6** — Với phiên thử có 2 kênh, mỗi bước có đúng phiên/kênh, prompt, kết quả hoặc lý do lỗi, usage và chi phí hoặc nhãn thiếu. Phân biệt được 3 trường hợp: số 0 được báo, usage thiếu, chi phí ước tính.
- [ ] **F2.B6** — Log, màn hình và hồ sơ xuất thử có 0 key/token thử và 0 trường yêu cầu chuỗi suy luận nội bộ của model.
- [ ] **F2.B6** — Mở offline vẫn đọc được log/checkpoint đã lưu. Sau xóa và restart còn 0 log trong phạm vi xóa, số log ngoài phạm vi giữ nguyên.

### 6. Bằng chứng nộp

- [ ] Ngày 29/11 giao Nghị một trace hai kênh, một bước lỗi/hủy và kết quả xóa một phiên. Minh nhận delete hook theo channel revision để chạy lại SP-024/052.
- [ ] Gắn hiện vật hoặc liên kết vào issue #36, kèm PR khi có thay đổi repo. Ghi SHA, thiết bị hoặc môi trường, đầu vào, kỳ vọng và thực tế. Dẫn kết quả test/CI và ảnh hoặc log đã che bí mật.

### 7. Điều kiện reviewer approve

1. Thiện và Nghị đối chiếu bảng giao việc với bằng chứng của #36.
2. Chọn hai dòng ở hai kênh, truy request tương ứng, kiểm nhãn tiền/token rồi xóa riêng một phiên. Từ chối khi có secret hoặc dòng ngoài phạm vi bị xóa.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Thông báo lỗi thô từ provider có thể chứa dữ liệu nhạy cảm. Kiểm cả ca lỗi và log thu thập làm minh chứng.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Phiên thử nào có đồng thời terminal thiếu usage và chi phí ước tính để reviewer đối chiếu? | Đạt | Trước khi bàn giao trace | Chờ ghi câu trả lời và nguồn đối chiếu. |

<a id="sp-057"></a>

## SP-057 — Báo cáo dự án và bằng chứng đóng góp từng thành viên

| Issue | Yêu cầu | Cửa sổ | Giờ còn lại | Hai reviewer dự kiến |
|---|---|---|---:|---|
| [#59](https://github.com/nigPeter2503/Social_Pilot/issues/59) | DOCS | 30/11–02/12 | 6 | Minh, Nghị |

**Đầu vào** · SP-056.

### 1. Mục tiêu

**Soạn tài liệu mới.** Soạn báo cáo cuối kỳ ghi được sản phẩm và đóng góp thực tế của cả bốn thành viên, khớp kết quả nghiệm thu của SP-056.

### 2. Phạm vi

- Tổng hợp từ owner từng module. Nghị chốt kết luận nghiệm thu, Thiện nhận báo cáo để đóng gói.

### 3. Tệp và module liên quan

- [CONTRIBUTING.md](../../../CONTRIBUTING.md) · [SocialPilot_Proposal_Java.md](../../../docs/SocialPilot_Proposal_Java.md) · [architecture.md](../../../docs/architecture.md) · [development.md](../../../docs/development.md)

### 4. Các bước thực hiện

1. Thu từ mỗi người danh sách ticket, PR, review, kết quả test và tài liệu module; đánh dấu phần thiếu nguồn.
2. Đối chiếu từng ghi nhận với hiện vật hoặc lịch sử review. Dùng giờ thực tế khi có số liệu và ghi riêng các hạn chế đã xác minh. Thu hướng dẫn cài đặt, OAuth, BYOK và cấu hình mẫu.
3. Sau SP-056, so số mã đạt và hạn chế với ma trận đã khóa, rồi giao báo cáo cho SP-058.

### 5. Điều kiện đạt (Definition of Done)

- [ ] **DOCS** — Báo cáo có 4/4 thành viên, mỗi đóng góp được ghi có ít nhất 1 nguồn truy vết. Số approval tự ghi không có bằng chứng bằng 0.
- [ ] **DOCS** — Tổng trạng thái của 44 mã và danh sách hạn chế khớp SP-056. Mỗi số giờ thực tế nêu trong báo cáo có nguồn hoặc được ghi là chưa có số liệu.
- [ ] **DOCS** — Các phần kiến trúc, setup, OAuth/HTTPS và BYOK có nguồn từ chủ module; cấu hình mẫu chứa 0 credential thật.

### 6. Bằng chứng nộp

- [ ] Cuối ngày 02/12, sau ma trận SP-056, khóa số mã đạt và hạn chế trong báo cáo. Giao Thiện bản có nguồn, phiên bản, giờ thực tế nếu có và hướng dẫn cấu hình bằng giá trị mẫu.
- [ ] Gắn hiện vật hoặc liên kết vào issue #59, kèm PR khi có thay đổi repo. Ghi ngày, người lập và phiên bản nguồn, kiểm tra người nhận mở được các liên kết.

### 7. Điều kiện reviewer approve

1. Minh và Nghị đối chiếu bảng giao việc với bằng chứng của #59.
2. Mở nguồn của từng thành viên, đối chiếu toàn bộ số tổng với SP-056 và so bản báo cáo giao Thiện. Yêu cầu sửa nếu quy đóng góp theo số commit đơn thuần.
3. Nếu ticket có thay đổi repo, hai reviewer xác nhận revision cuối sau khi checks áp dụng đạt và các nhận xét bắt buộc đã được xử lý.

### 8. Rủi ro và lưu ý

- Một người có thể review hoặc kiểm thử trên ticket người khác. Ghi nhận đúng vai trò và revision.

### Câu hỏi mở

| Mã | Cần xác nhận | Người trả lời | Hạn trả lời | Trả lời / nguồn xác nhận |
|---|---|---|---|---|
| Q1 | Bản báo cáo cuối và sổ đóng góp có nguồn từ bốn thành viên sẽ được lưu ở đâu? | Đạt | Trước khi khóa báo cáo sau SP-056 | Chờ ghi câu trả lời và nguồn đối chiếu. |

Khi giao từng ticket, gắn SHA/PR, lệnh và kết quả thực tế, ảnh/log đã che bí mật, người nhận và việc còn chặn. Quy trình kiểm tra và điều kiện đóng ticket nằm trong [brief chung](week-4-5-taskbrief.md#kiem-chung).
