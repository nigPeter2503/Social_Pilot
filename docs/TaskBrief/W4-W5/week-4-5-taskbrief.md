# Taskbrief hoàn tất SocialPilot · 24/11–03/12/2026

**Chiều thứ Năm 03/12/2026, nhóm bàn giao toàn bộ đồ án.** Hoàn thành 100% nghĩa là 44/44 yêu cầu cam kết có bằng chứng nghiệm thu trên bản giao và đủ tám nhóm sản phẩm, gồm APK cài được, backend OAuth HTTPS, tài liệu, kết quả kiểm thử và bộ demo. Ưu tiên trước hết là xử lý các phụ thuộc có thể chặn mốc này. Hạn nộp và bảo vệ chính thức theo thông báo giảng viên. [README](../../../README.md) · [Proposal](../../SocialPilot_Proposal_Java.md) · [Quy trình nhóm](../../../CONTRIBUTING.md)

| Brief cá nhân | Chịu trách nhiệm trong đợt | Đầu ra người khác đang chờ |
|---|---|---|
| [Ngô Gia Minh · TV1](tv1-minh-taskbrief.md) | Facebook Page, Instagram Professional, queue, điều phối tích hợp | Hợp đồng ngắt kênh, hai bài thật, kết quả E2E và kịch bản demo |
| [Nguyễn Đức Đạt · TV2](tv2-dat-taskbrief.md) | AI, ngân sách, Room chung, báo cáo đóng góp | Ảnh và gói hai nhánh, checkpoint, fallback, ledger và báo cáo |
| [Đỗ Phước Thiện · TV3](tv3-thien-taskbrief.md) | Topic/editor, F9, UI phân tích, CI và APK | Bản đã duyệt, màn dùng số liệu thật, slide và bộ cài |
| [Tô Lãng Nghị · TV4](tv4-nghi-taskbrief.md) | Comment, snapshot, mục tiêu, báo cáo AI, nghiệm thu | Thread/reply thật, repository mục tiêu, ma trận và video |

## Đầu vào từ tuần 2–3

Kế hoạch dùng giả định toàn bộ phần việc đã phân bổ trong [bộ brief tuần 2–3](../W2-W3/week-2-3-taskbrief.md) đã bàn giao, kể cả phần dự báo tuần 4 ghi trong bộ đó. Ngày 24/11 chủ module gắn SHA và bằng chứng đầu vào để dùng lại khi kiểm thử liên thông.

- SP-022/023/027/028/029/035/036/041/045/046/051/053 cung cấp OAuth, quyền theo kênh, BYOK, ngân sách, editor, comment/snapshot, dữ liệu dashboard, VI/EN và HTTPS.
- SP-025/026/031/032/037/042/043/047 cung cấp tám đầu ra kế thừa. SP-024 còn 10 giờ triển khai ngắt kênh, SP-033 còn 10 giờ hoàn thiện checkpoint và phục hồi. Nếu thiếu bằng chứng, owner bổ sung phép thử trong SP-052 và chuyển kết quả cho SP-056.
- SP-021 và các nền tuần 1 là đầu vào. Danh mục issue đọc ngày 24/11 còn ghi mở SP-013 (kiểm chứng AI), SP-066 (Instagram comment/Insights) và nhiều ticket tuần 2–3. Theo giả định, Đạt và Nghị lấy link kiểm chứng thật từ bàn giao đầu kỳ, đối chiếu từng phép thử còn thiếu trong SP-052/056 và ghi giờ bổ sung nếu phải làm lại. SP-065 đã đóng. Hồ sơ Explorer hỗ trợ đối chiếu API, việc xác nhận trên app vẫn cần bản tích hợp. [SP-013](https://github.com/nigPeter2503/Social_Pilot/issues/15) · [SP-065](https://github.com/nigPeter2503/Social_Pilot/issues/90) · [SP-066](https://github.com/nigPeter2503/Social_Pilot/issues/91) · [Trạng thái spike Facebook](../../drafts/sp-010-implementation-status.md)
- Dùng lại 26 issue trong bảng dưới. SP-002 đến SP-008 là các issue tổng hợp, đối chiếu con và phạm vi trước khi đóng. Owner, reviewer và việc chuyển SP-052 là phân công đề xuất. Hạn bàn giao nội bộ là 03/12/2026. Bản nháp này chỉ đối chiếu danh mục và nội dung issue. [Danh mục issue](https://github.com/nigPeter2503/Social_Pilot/issues) · [GitHub Project 5](https://github.com/users/nigPeter2503/projects/5)

## Lịch giao và thứ tự tích hợp

Tất cả ngày theo Asia/Bangkok. Chủ module giao đầu ra cho người kế tiếp ngay khi PR đã merge và các kiểm tra áp dụng đạt. Từng owner đặt lịch với hai reviewer trước mốc tích hợp, kể cả cuối tuần 28–29/11.

| Mốc | Bàn giao cần thực hiện | Điều kiện để đi tiếp |
|---|---|---|
| 24–25/11 · Nhận đầu vào | Minh rà quyền và hợp đồng ngắt kênh. Đạt hoàn thiện checkpoint. Thiện giao lịch sử phiên bản. Nghị giao dữ liệu mục tiêu cùng kết quả tính tay. | Có SHA, quyền API thật theo từng thao tác, dữ liệu hai kênh và bộ ca hồi quy để các module chạy song song. |
| 26–28/11 · Tích hợp và demo | Minh chứng minh xóa chọn lọc, Đạt giao fallback, Thiện giao editor nâng cao, Nghị kiểm chứng auto-reply. Demo phần tích hợp ngày 28/11. | Ghi ca hủy, kết quả thử chống gửi trùng và toàn bộ ca cấm tự trả lời. Mỗi lỗi có owner tiếp nhận trong ngày. |
| 29–30/11 · Khóa tính năng | Hoàn thiện log, RSS, UI phân nhóm/mục tiêu và báo cáo AI. Minh tập hợp các PR đã merge, Nghị thu kết quả 20 brief VI/EN và 60 comment. | Cuối 30/11 chốt SHA bản ứng viên Android/backend, bộ dữ liệu và danh mục checks. |
| 01/12 · Nghiệm thu trên bản ứng viên | Minh điều phối SP-052. Thiện chạy UI, VI/EN và offline. Đạt kiểm hủy, ngân sách và migration. Nghị đối chiếu bài, reply và snapshot thật. | Mỗi mã có kết quả ban đầu. Lỗi có ca tái hiện, chủ module, PR sửa và người xác nhận. |
| 02/12 · Sửa lỗi, chạy lại và khóa hồ sơ | Buổi sáng sửa lỗi, chạy lại trên SHA cuối rồi Minh giao SP-052. Buổi chiều Nghị khóa ma trận/video SP-056. Đạt chốt báo cáo SP-057 sau khi nhận ma trận. | Thiện nhận bằng chứng, video và báo cáo cùng revision để khóa manifest. Nếu bản giao thay đổi, chủ module chạy lại ca bị ảnh hưởng, Nghị cập nhật ma trận và video. |
| 03/12 · Bàn giao | Buổi sáng Thiện cài sạch, thử nâng cấp APK, kiểm hash và giao SP-058. Buổi chiều Minh cùng nhóm demo đúng bản và lập biên bản SP-059. | Xác nhận 100% khi đủ 44/44 mã đạt, đủ tám nhóm sản phẩm và có người xác nhận. Ghi đúng kết quả từng mã nếu phép thử còn thiếu. |

**Đường phụ thuộc cần giữ** · Ảnh SP-032 → gói AI SP-031 → editor SP-036 → người dùng duyệt → publish SP-025 → queue SP-026. Checkpoint SP-033 → fallback SP-030. Inbox SP-042 → reply SP-043 → auto-reply SP-044. Dữ liệu SP-046 → mục tiêu SP-048 → UI SP-049. Các luồng này và báo cáo SP-050 hội tụ tại SP-052 → SP-056 → SP-057 → SP-058 → SP-059. Công việc chuẩn bị báo cáo, slide, video và manifest có thể bắt đầu khi từng module giao mẫu.

## Danh mục giao việc

Bấm vào mã để xem đầu vào, hai reviewer, cách thực hiện, ca lỗi và bằng chứng bàn giao. Tám dòng “Đầu vào” có 0 giờ triển khai mới. SP-055 tính số giờ phối hợp của cả nhóm.

| Task | Đầu ra | Owner | Cửa sổ thực hiện | Giờ ước lượng |
|---|---|---|---|---:|
| [SP-054](tv1-minh-taskbrief.md#sp-054) | Rà quy trình hai approval và nhịp báo cáo nhóm | Minh | 24–25/11 | 4 |
| [SP-024](tv1-minh-taskbrief.md#sp-024) | Ngắt kênh, hủy tác vụ và xóa dữ liệu có chọn lọc | Minh | 24–26/11 | 10 |
| [SP-025](tv1-minh-taskbrief.md#sp-025) | Đăng bản đã duyệt lên hai kênh và lưu link thật | Minh | Đầu vào | 0 |
| [SP-026](tv1-minh-taskbrief.md#sp-026) | Hàng đợi bền vững và đối soát chống đăng trùng | Minh | Đầu vào | 0 |
| [SP-055](tv1-minh-taskbrief.md#sp-055) | Điều phối demo và theo dõi blocker Tuần 2–5 | Minh | 24/11–03/12 | 16 nhóm |
| [SP-052](tv1-minh-taskbrief.md#sp-052) | Nghiệm thu liên thông, phục hồi, migration và bảo mật | Minh | 28/11–02/12 | 16 |
| [SP-059](tv1-minh-taskbrief.md#sp-059) | Kịch bản demo và xác nhận bàn giao cuối kỳ | Minh | 01–03/12 | 4 |
| [SP-032](tv2-dat-taskbrief.md#sp-032) | Sinh ảnh riêng từng kênh và template dự phòng | Đạt | Đầu vào | 0 |
| [SP-031](tv2-dat-taskbrief.md#sp-031) | Orchestrator hai nhánh và chuỗi tạo nội dung theo kênh | Đạt | Đầu vào | 0 |
| [SP-033](tv2-dat-taskbrief.md#sp-033) | Stream, checkpoint, hủy và chạy lại riêng nhánh | Đạt | 24–25/11 | 10 |
| [SP-030](tv2-dat-taskbrief.md#sp-030) | Chuyển provider dự phòng có kiểm soát và không nhân đôi kết quả | Đạt | 26–27/11 | 8 |
| [SP-034](tv2-dat-taskbrief.md#sp-034) | Nhật ký agent theo phiên/kênh và xóa log an toàn | Đạt | 28–29/11 | 8 |
| [SP-057](tv2-dat-taskbrief.md#sp-057) | Báo cáo dự án và bằng chứng đóng góp từng thành viên | Đạt | 30/11–02/12 | 6 |
| [SP-037](tv3-thien-taskbrief.md#sp-037) | Nhập URL có trích nguồn và nhận Android Sharesheet | Thiện | Đầu vào | 0 |
| [SP-047](tv3-thien-taskbrief.md#sp-047) | Màn hình số liệu, tăng trưởng và dashboard | Thiện | Đầu vào | 0 |
| [SP-040](tv3-thien-taskbrief.md#sp-040) | Lịch sử và khôi phục bản nháp không kế thừa duyệt | Thiện | 24–25/11 | 8 |
| [SP-039](tv3-thien-taskbrief.md#sp-039) | Viết lại đoạn, sinh lại ảnh, ba hook và alt text | Thiện | 26–27/11 | 12 |
| [SP-038](tv3-thien-taskbrief.md#sp-038) | Gợi ý topic từ bài hiệu quả và nguồn RSS/Atom | Thiện | 28–29/11 | 10 |
| [SP-049](tv3-thien-taskbrief.md#sp-049) | Giao diện phân tích theo nhóm và mục tiêu | Thiện | 29–30/11 | 10 |
| [SP-058](tv3-thien-taskbrief.md#sp-058) | Đóng gói APK, slide và bộ bàn giao có thể tái lập | Thiện | 30/11–03/12 | 8 |
| [SP-042](tv4-nghi-taskbrief.md#sp-042) | Inbox hợp nhất với bộ lọc và dữ liệu offline | Nghị | Đầu vào | 0 |
| [SP-043](tv4-nghi-taskbrief.md#sp-043) | Phân loại comment và gửi trả lời đã chỉnh sửa | Nghị | Đầu vào | 0 |
| [SP-048](tv4-nghi-taskbrief.md#sp-048) | Mục tiêu lưu bền vững và tính tiến độ từ dữ liệu thật | Nghị | 24–25/11 | 8 |
| [SP-044](tv4-nghi-taskbrief.md#sp-044) | Tự trả lời mặc định tắt với FAQ và hạn mức | Nghị | 26–28/11 | 12 |
| [SP-050](tv4-nghi-taskbrief.md#sp-050) | Báo cáo tuần AI dựa trên snapshot và giao diện báo cáo | Nghị | 29–30/11 | 10 |
| [SP-056](tv4-nghi-taskbrief.md#sp-056) | Tổng hợp ma trận 44 yêu cầu và video dự phòng | Nghị | 28/11–02/12 | 8 |

## Ước lượng và thứ tự ưu tiên

Các ticket còn 152 giờ thực hiện. Nhóm ước lượng thêm 25,5 giờ review, 16 giờ họp/demo, 4 giờ điều phối Room và 16 giờ dự phòng sửa lỗi. Tổng 213,5 giờ dùng để chia việc và bố trí người review. Hạn bàn giao là 03/12.

| Người | Thực hiện | Review | Họp, schema và dự phòng | Tổng giờ ước lượng |
|---|---:|---:|---:|---:|
| Minh | 34 | 6 | 4 + 0 + 4 | 48 |
| Đạt | 32 | 6,75 | 4 + 4 + 4 | 50,75 |
| Thiện | 48 | 4,5 | 4 + 0 + 4 | 60,5 |
| Nghị | 38 | 8,25 | 4 + 0 + 4 | 54,25 |
| **Cả nhóm** | **152** | **25,5** | **16 + 4 + 16** | **213,5** |

Ưu tiên đầu tiên là công việc chặn người kế tiếp hoặc một mã nghiệm thu, đặc biệt là quyền API thật, phục hồi sau hủy, duyệt theo phiên bản, chống đăng trùng và bảo toàn dữ liệu. Khi phát hiện lỗi, chủ module nhận xử lý, hai reviewer xem PR sửa và người chạy ca ban đầu xác nhận lại trên bản ứng viên.

Minh điều phối kiểm thử liên thông SP-052 theo phân công đề xuất. Thiện giữ F9, CI và APK. Nghị nhận kết quả từ cả bốn thành viên để khóa ma trận, Đạt dùng ma trận đã khóa để chốt báo cáo. Việc chuẩn bị slide, manifest, video và báo cáo diễn ra song song từ khi có đầu ra đầu tiên.

<a id="kiem-chung"></a>

## Kiểm chứng và đóng ticket

- Trước khi sửa, nhận issue trên Project, chốt owner và hai reviewer. Mỗi ticket triển khai dùng một nhánh ngắn `<type>/sp-xxx-<slug>` từ `main`, một PR, hai approval thật khác tác giả và squash merge sau khi checks đạt. Đổi schema hoặc contract cần Đạt/người dùng hợp đồng review theo quy tắc nhóm. SP-055 ghi biên bản họp/demo. Với PR có thay đổi đáng kể, chừa lượt xác nhận lại. [CONTRIBUTING](../../../CONTRIBUTING.md)
- Từ `android/` chạy `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Với UI, vòng đời, worker hoặc migration, chạy ca tương ứng trên emulator/thiết bị và `:app:connectedDebugAndroidTest` khi có test instrumented. Từ `backend/` chạy `.\gradlew.bat test bootJar`. Đổi hợp đồng xuyên hai phía thì chạy cả hai. Ghi revision, phiên bản JDK/SDK, ngày, kết quả và link CI. [Hướng dẫn phát triển](../../development.md) · [Baseline build và thiết bị](../../build-verification.md)
- Trong từng module, kiểm VI/EN, loading/rỗng/lỗi/thiếu quyền, cache và offline sau restart. Chụp hoặc quay đầu vào, kết quả mong đợi và thực tế, thời điểm, kênh, phiên bản app, người xác nhận. Gắn nhãn dữ liệu mẫu. Bằng chứng API thật phải đủ đối chiếu trên kênh và đã che credential. [Kiến trúc](../../architecture.md) · [Social](../../social-contract.md) · [Room](../../room-schema-contract.md) · [AI](../../ai-provider-contract.md) · [Editor](../../sp-016-brief-contract.md) · [Analytics](../../analytics-data-contract.md)
- Ở mốc nghiệm thu, Nghị ghi cho mỗi mã trạng thái đạt, một phần, chưa đạt hoặc bị chặn. Minh ghi ai xử lý và thời điểm quyết định nếu thiếu entitlement, quota, dữ liệu lịch sử hay thời gian chạy lại. Đồng bộ issue cha theo bằng chứng ticket con và trạng thái trên Project sau khi merge.

## Bản đồ kiểm chứng 44 yêu cầu

Đây là phân công phép thử cho SP-052 và SP-056. Cột ticket chỉ đến phần triển khai hoặc đầu vào tuần trước. Chủ module cung cấp kết quả trên bản bàn giao, Nghị tổng hợp kết luận theo mục 5.1. F3.N2 (ảnh thành topic) và F6.N1 (thông báo comment) là hai Optional ngoài lịch. [Ma trận yêu cầu](../../requirements.md) · [Proposal và kịch bản nghiệm thu](../../SocialPilot_Proposal_Java.md)

| Mã | Ticket cung cấp đầu ra | Phép thử và kết quả cần thấy |
|---|---|---|
| F1.B1 | [SP-022](https://github.com/nigPeter2503/Social_Pilot/issues/24) | OAuth thật với Facebook Page và Instagram Professional. Nhận đúng Page ID và Instagram user ID liên kết, hiển thị hai đích đăng riêng. Hai tài khoản cùng nền tảng không thay hai nền tảng đã chọn. |
| F1.B2 | [SP-023](https://github.com/nigPeter2503/Social_Pilot/issues/25) | Hiển thị trạng thái, tài khoản/Page và khả năng đăng, đọc/trả lời comment, mention, số liệu. Đối chiếu quyền thực tế cho từng kênh. |
| F1.B3 | [SP-022](https://github.com/nigPeter2503/Social_Pilot/issues/24) | Mở trình duyệt hệ thống. Thử refresh, hết hạn và thu hồi token. Refresh thành công khi được cấp quyền. Thiếu cơ chế refresh phải ghi bị chặn/đạt một phần. |
| F1.B4 | [SP-024](tv1-minh-taskbrief.md#sp-024) | Ngắt một kênh rồi mở lại app. Token, dữ liệu và job liên quan không còn. Kênh khác và bản nháp dùng chung vẫn đúng. Kết quả về muộn không khôi phục dữ liệu. |
| F2.B1 | [SP-031](tv2-dat-taskbrief.md#sp-031) | Một topic tạo brief chung và khởi chạy song song hai nhánh riêng. Log chứng minh thời gian thực thi chồng lấp. |
| F2.B2 | [SP-031](tv2-dat-taskbrief.md#sp-031) | Mỗi kênh có dàn ý, bản nháp, kết quả tự rà, mô tả ảnh, ảnh và gói kết quả. Lỗi ghi đúng bước. |
| F2.B3 | [SP-032](tv2-dat-taskbrief.md#sp-032) | Mỗi kênh có cấu hình kích thước/phong cách. Thử cả API sinh ảnh và trường hợp không có API để xác nhận template hoạt động. |
| F2.B4 | [SP-033](tv2-dat-taskbrief.md#sp-033) | Stream xuất hiện trước khi hoàn tất. Tích lũy đủ nội dung đúng thứ tự. Xoay màn hình/đổi Fragment không tạo lệnh mới. Process bị kết thúc khôi phục checkpoint và báo gián đoạn. |
| F2.B5 | [SP-033](tv2-dat-taskbrief.md#sp-033) | Hủy giữa chừng phải hủy Future và HTTP Call, bỏ qua callback muộn. Chạy lại riêng nhánh lỗi. Nhánh còn lại và kết quả đã lưu không mất. Không ghi đè sau hủy. |
| F2.B6 | [SP-034](tv2-dat-taskbrief.md#sp-034) | Xem prompt, kết quả, token và chi phí theo từng bước/kênh. Phân biệt số liệu thiếu với 0. Không xuất hiện key hoặc token bí mật. |
| F3.B1 | [SP-035](https://github.com/nigPeter2503/Social_Pilot/issues/37) | Nhập và lưu topic, mục tiêu, đối tượng, giọng văn, VI/EN và kênh đích. Dữ liệu đúng được chuyển vào brief chung. |
| F3.N1 | [SP-037](tv3-thien-taskbrief.md#sp-037) | URL công khai tạo bài chia sẻ kèm nguồn. URL lỗi/bị chặn có thông báo và nhập tay, không bịa nội dung nguồn. |
| F3.N3 | [SP-037](tv3-thien-taskbrief.md#sp-037) | Chia sẻ link từ app khác qua ACTION_SEND mở đúng form với URL. Luồng hủy không tạo bài hoặc tự đăng. |
| F3.N4 | [SP-038](tv3-thien-taskbrief.md#sp-038) | Có gợi ý từ bài hiệu quả và từ nguồn xu hướng RSS/Atom đã chọn, kèm nguồn/lý do. Thiếu dữ liệu có trạng thái rõ. |
| F4.B1 | [SP-036](https://github.com/nigPeter2503/Social_Pilot/issues/38), [SP-026](tv1-minh-taskbrief.md#sp-026) | Bài chưa duyệt không vào hàng đợi. Sửa nội dung/ảnh sau duyệt làm mất hiệu lực duyệt. Kiểm tra cả luồng online và offline. |
| F4.B2 | [SP-036](https://github.com/nigPeter2503/Social_Pilot/issues/38) | Preview riêng hai kênh, sửa tay, đếm ký tự. Thử dưới/đúng/vượt giới hạn đã kiểm chứng. Vượt giới hạn không được gửi. |
| F4.B3 | [SP-039](tv3-thien-taskbrief.md#sp-039) | Chọn một đoạn để viết lại, giữ phần còn lại. Sinh lại ảnh theo đúng kênh và yêu cầu duyệt lại bản thay đổi. |
| F4.N1 | [SP-040](tv3-thien-taskbrief.md#sp-040) | Xem và khôi phục phiên bản. Không mất phiên bản cũ và không kế thừa nhầm trạng thái duyệt. |
| F4.N2 | [SP-039](tv3-thien-taskbrief.md#sp-039) | Sinh đủ 3 hook. Chọn một hook chỉ thay phần mở đầu và giữ phần thân. |
| F4.N3 | [SP-039](tv3-thien-taskbrief.md#sp-039), [SP-025](tv1-minh-taskbrief.md#sp-025) | Tạo, sửa và lưu alt text. Gửi cùng ảnh nếu API hỗ trợ, báo rõ nếu không thể truyền lên kênh. |
| F5.B1 | [SP-025](tv1-minh-taskbrief.md#sp-025) | Đăng lên hai kênh với trạng thái độc lập: nháp, chờ, đang đăng, đã đăng/lỗi/đã bàn giao. Một kênh lỗi không đổi kết quả kênh kia. |
| F5.B2 | [SP-026](tv1-minh-taskbrief.md#sp-026) | Mất mạng vẫn xếp hàng bài đã duyệt. Đóng/mở app không mất job. Có mạng và đủ điều kiện hệ điều hành thì tiếp tục. |
| F5.B3 | [SP-026](tv1-minh-taskbrief.md#sp-026) | Thử bấm hai lần, retry, khởi động lại và mất phản hồi sau khi server nhận. Kiểm tra bài thực tế. Kết quả chưa rõ phải dừng đối soát, không đăng lại mù quáng. |
| F5.B4 | [SP-026](tv1-minh-taskbrief.md#sp-026) | Lỗi mạng/429 có backoff và giới hạn thử. Lỗi quyền/nội dung hiện lý do và bước xử lý. Không retry tự động khi chưa rõ kết quả đăng. |
| F5.B5 | [SP-025](tv1-minh-taskbrief.md#sp-025) | Sau API thành công lưu mã bài và URL đúng từng kênh. Mở đúng bài gốc. Đã bàn giao không được tự gán link/thành công giả. |
| F6.B1 | [SP-042](tv4-nghi-taskbrief.md#sp-042) | Comment thật của hai kênh hiển thị cùng hộp thư. Lọc đúng theo kênh, bài và trạng thái xử lý. |
| F6.B2 | [SP-041](https://github.com/nigPeter2503/Social_Pilot/issues/43) | Kéo làm mới và job nền cập nhật comment, xử lý phân trang. Đồng bộ lặp không nhân bản. Hiển thị lần cập nhật gần nhất. |
| F6.B3 | [SP-043](tv4-nghi-taskbrief.md#sp-043) | Bộ kiểm thử có đủ câu hỏi, khen, góp ý, tiêu cực và spam. Lưu nhãn từng mẫu, số sai và ma trận nhầm lẫn để đánh giá. |
| F6.B4 | [SP-043](tv4-nghi-taskbrief.md#sp-043) | AI dựa trên bài và giọng văn. Người dùng sửa rồi gửi. Câu gửi xuất hiện đúng comment trên nền tảng. Thử lại không gửi lặp. |
| F6.B5 | [SP-044](tv4-nghi-taskbrief.md#sp-044) | Mặc định tắt. Chỉ khen/FAQ đã xác nhận được gửi. Toàn bộ ca tiêu cực, thông tin cá nhân, không chắc chắn và quá hạn mức trong bộ thử đều phải bị chặn. |
| F7.B1 | [SP-045](https://github.com/nigPeter2503/Social_Pilot/issues/47), [SP-046](https://github.com/nigPeter2503/Social_Pilot/issues/48), [SP-047](tv3-thien-taskbrief.md#sp-047) | Đối chiếu thích/reaction, comment, chia sẻ, impression nếu có và công thức tương tác. Thiếu quyền/dữ liệu hoặc mẫu số 0 không hiển thị thành 0%. |
| F7.B2 | [SP-045](https://github.com/nigPeter2503/Social_Pilot/issues/47), [SP-047](tv3-thien-taskbrief.md#sp-047) | Lưu ít nhất hai thời điểm thực tế cho bài/kênh. Biểu đồ đúng thời điểm và giá trị, giữ khoảng trống khi bỏ lỡ lịch. |
| F7.B3 | [SP-046](https://github.com/nigPeter2503/Social_Pilot/issues/48), [SP-047](tv3-thien-taskbrief.md#sp-047) | Có follower theo thời gian, tương tác theo tuần, top bài và so sánh kênh. Đối chiếu bằng dữ liệu có cùng định nghĩa. Đối chiếu tổng follower với nguồn của từng kênh. |
| F7.N1 | [SP-046](https://github.com/nigPeter2503/Social_Pilot/issues/48), [SP-049](tv3-thien-taskbrief.md#sp-049) | Phân nhóm theo chủ đề, định dạng và giờ đăng. Số liệu tổng hợp khớp dữ liệu gốc và múi giờ cấu hình. |
| F7.N2 | [SP-050](tv4-nghi-taskbrief.md#sp-050) | Nêu biến động và đề xuất dựa trên snapshot. Số liệu khớp nguồn, nguyên nhân chưa chứng minh được ghi là giả thuyết. Thiếu dữ liệu không bịa kết luận. |
| F7.N3 | [SP-048](tv4-nghi-taskbrief.md#sp-048), [SP-049](tv3-thien-taskbrief.md#sp-049) | Lưu chỉ số, mức đích và hạn đạt. Tiến độ tính từ dữ liệu thực, cập nhật sau đồng bộ và xử lý trường hợp chưa có số liệu. |
| F8.B1 | [SP-028](https://github.com/nigPeter2503/Social_Pilot/issues/30) | Key hợp lệ của OpenAI và Gemini đều thực hiện được một tác vụ văn bản. Đổi provider không mất bản nháp hoặc usage đã ghi. |
| F8.B2 | [SP-027](https://github.com/nigPeter2503/Social_Pilot/issues/29) | Thử key đúng/sai, bị thu hồi và lỗi mạng. Key được che, mã hóa, xóa được và không nằm trong backup/log. Kiểm tra network: chỉ gửi tới provider tương ứng, không qua backend nhóm. |
| F8.B3 | [SP-028](https://github.com/nigPeter2503/Social_Pilot/issues/30), [SP-032](tv2-dat-taskbrief.md#sp-032) | Chọn và lưu model cho viết bài, phân loại comment, sinh ảnh. Model không có khả năng phù hợp bị chặn. Thiếu API ảnh dùng template. |
| F8.B4 | [SP-028](https://github.com/nigPeter2503/Social_Pilot/issues/30), [SP-029](https://github.com/nigPeter2503/Social_Pilot/issues/31), [SP-032](tv2-dat-taskbrief.md#sp-032) | Usage từng lệnh tổng hợp đúng theo ngày/tháng và provider. Ảnh ghi chi phí riêng nếu không có token. Số ước tính có nhãn và không biến usage thiếu thành 0. |
| F8.N1 | [SP-029](https://github.com/nigPeter2503/Social_Pilot/issues/31), [SP-033](tv2-dat-taskbrief.md#sp-033) | Thử trần ngày/tháng với hai nhánh song song và retry: giữ trước ngân sách, không mở lệnh mới khi hết. Dừng nhánh, lưu kết quả một phần và thử hủy lệnh đang chạy nếu hỗ trợ. |
| F8.N2 | [SP-030](tv2-dat-taskbrief.md#sp-030) | Gây lỗi khả dụng ở provider chính: dùng provider phụ có key/model phù hợp và còn ngân sách. Ghi log, cộng chi phí chung, không tạo kết quả/ảnh trùng. |
| F9.B1 | [SP-051](https://github.com/nigPeter2503/Social_Pilot/issues/53), [SP-052](tv1-minh-taskbrief.md#sp-052) | Đổi ngôn ngữ trên toàn bộ luồng chính, trạng thái lỗi và thông báo. Kiểm tra chuỗi thiếu và bố cục. |
| F9.B2 | [SP-051](https://github.com/nigPeter2503/Social_Pilot/issues/53), [SP-026](tv1-minh-taskbrief.md#sp-026), [SP-052](tv1-minh-taskbrief.md#sp-052) | Sau đồng bộ, bật chế độ máy bay và khởi động lại: vẫn xem bản nháp, comment, số liệu đã lưu. Nhận biết dữ liệu cũ và chức năng cần mạng. |

## Danh sách bàn giao ngày 03/12

| Sản phẩm theo proposal §5.4 | Người tổng hợp | Dấu hiệu bàn giao được |
|---|---|---|
| APK Android | Thiện · SP-058 | Hash khớp manifest, cài và smoke trên thiết bị demo. |
| Mã nguồn Android và backend | Thiện · SP-058, Minh cung cấp backend | Revision có hai Gradle Wrapper và phiên bản khóa, dựng lại được. |
| Proposal, yêu cầu và truy vết | Nghị · SP-056 | 44 dòng có kết quả, người xác nhận, chứng cứ và hai Optional theo dõi riêng. |
| Kiến trúc, mô hình dữ liệu, luồng nghiệp vụ | Đạt · SP-057, chủ module cung cấp | Kiến trúc khớp bản giao, có schema/migration và luồng AI, OAuth, duyệt/đăng, đồng bộ. |
| Hướng dẫn môi trường và vận hành | Minh, Đạt cung cấp · Thiện đóng gói SP-058 | JDK/SDK/Gradle, HTTPS callback, OAuth, BYOK và cấu hình mẫu đã thử trên máy nhận. |
| Bộ test, kết quả và hạn chế | Minh · SP-052, Nghị tổng hợp SP-056 | CI, thiết bị, API thật, ca lỗi, phiên bản và giới hạn đều truy được. |
| Báo cáo và đóng góp bốn thành viên | Đạt · SP-057 | Có issue, PR, review, test hoặc hiện vật cho từng đóng góp. |
| Slide, kịch bản demo, video dự phòng | Thiện · SP-058, Minh · SP-059, Nghị · SP-056 | Ba hiện vật dùng cùng bản tích hợp, mở thử được và có người trình bày từng bước. |
