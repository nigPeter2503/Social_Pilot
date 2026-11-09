# SP-020 — Hồ sơ bản nháp và kiểm tra local

Ngày: 09/11/2026 (Asia/Saigon). Người thực hiện: Tô Lãng Nghị — TV4 (`bob35306`).
Revision contract hiện tại: **draft-2**. Trạng thái: **xong bản nháp local, chờ review**;
chưa hoàn tất ticket, chưa nghiệm thu F6/F7.

## 1. Revision và kiểm kê tiến độ

- Base/main sau `git fetch origin`: `0348982c8f295d946815da91eae491bd9645ac94`.
- Nhánh local: `feat/sp-020-analytics-data-contract`, tạo từ `origin/main` sạch.
  Bộ đầu ra draft-2 được đóng gói bằng commit local theo yêu cầu ngày 09/11;
  xem revision bằng `git log -1` trên nhánh này. Chưa push/PR.
  Remote không có nhánh SP-018/019/020 tại lần kiểm tra ban đầu.
- GitHub REST đọc ngày 09/11: [#20](https://github.com/nigPeter2503/Social_Pilot/issues/20),
  [#21](https://github.com/nigPeter2503/Social_Pilot/issues/21),
  [#22](https://github.com/nigPeter2503/Social_Pilot/issues/22) đều OPEN, assignee `bob35306`;
  cả ba có updated_at ngày 04/11. Danh sách PR mọi trạng thái không có PR ba task này.
  Không suy trạng thái board hiện tại từ nội dung issue cũ: **Project live chưa xác minh**.
- [SP-009 PR #66](https://github.com/nigPeter2503/Social_Pilot/pull/66) OPEN, chưa merge,
  head `289e4d137b5d006c16d257fae413e0d5687fc472`. Đã đọc social-contract,
  SocialConnector, ChannelInfo/ChannelCapability/SocialOperation ở revision này để so khớp.
  Đây chưa phải một lượt review đầy đủ hoặc human approval.
- [SP-012 #14](https://github.com/nigPeter2503/Social_Pilot/issues/14) OPEN, assignee `dat532006`;
  chưa thấy nhánh/PR liên quan trong danh sách remote/PR đã đọc. Chưa có schema chốt để tích hợp.
- Lần fetch/REST đầu trong sandbox thất bại DNS (`getaddrinfo() thread failed to start`,
  `No such host is known`). Chạy lại bằng quyền mạng được duyệt đã thành công.
  Giữ kết quả thất bại này để phân biệt lỗi môi trường với trạng thái GitHub thực tế.

## 2. Đầu vào và giới hạn

| Đầu vào | Kết quả kiểm kê | Người / bước mở chặn |
|---|---|---|
| Capability/error SP-009 | Có draft ở PR #66; chưa merge/xác nhận tích hợp | Minh chốt revision với Nghị |
| Schema SP-012 | Main vẫn AppDatabase v1 chỉ DraftEntity; chưa thấy contract schema chốt | Đạt review mục 8 contract |
| Token/đối tượng Facebook | Không phát hiện cấu hình trong file cấu hình local dự kiến và tên biến môi trường đã kiểm tra; chưa được cung cấp vị trí hoặc xác nhận đối tượng thử | Minh, SP-010/#12; không kết luận token không tồn tại ở nơi khác |
| LinkedIn account/product/scope/token/IDs | Chưa có thông tin đã xác nhận trong nguồn kiểm tra | Minh, SP-011/#13 |
| Trường UI | Có proposal/brief, chưa có xác nhận Thiện cho draft-2 | Thiện review mục 7 và fixture |
| Review/handoff | Nội dung bàn giao đã chuẩn bị tại mục 10 contract; chưa gửi | Minh/Thiện review; Đạt schema |

Không gọi Facebook/LinkedIn hoặc gửi reply. Chưa có đối tượng thử được xác nhận,
chưa kiểm tra hiệu lực/quyền token. Không đọc hoặc in giá trị biến môi trường nhạy cảm.
Không lưu token/client secret/Authorization trong đầu ra. Bản nháp chưa pin endpoint,
API version/scope; mapping provider chỉ là nhu cầu chưa kiểm chứng, dành cho SP-018/019.

## 3. Môi trường kiểm tra

- Windows, PowerShell; Node v24.19.0 được gọi bằng `node` trên PATH, không cài dependency mới.
- Wrapper Android khai báo Gradle 8.13, JAR và script có trong repo.
- `java.exe -version` trên PATH thoát **-1073740791**, không in phiên bản;
  chưa xác minh JDK 21 dùng được. Junction `C:\Program Files\Java\latest\jdk-25`
  trỏ `D:\game\javajdk\`; không coi tên thư mục là kết quả version.
- SDK local có `build-tools/36.0.0`, license file `android-sdk-license`,
  nhưng thư mục platforms chỉ có `android-37.0`, chưa có `android-36`.
  Chưa kiểm chứng SDK/license đủ cho build dự án.
- Cache wrapper mặc định nhìn thấy Gradle 9.3/9.6; chưa thấy cache 8.13 ở vị trí đã kiểm tra.
- Không sửa Java/XML/build/schema/backend; không chạy Android/backend build trong phiên tài liệu này.
  Khi thêm code sau review: chuẩn bị JDK 21/SDK 36 rồi chạy bộ kiểm tra trong CONTRIBUTING.

## 4. Kiểm tra thực hiện

Bảng dưới là kết quả **draft-1** ở lượt tạo đầu; kết quả mới draft-2 ở mục 6.

Tại repo root:

```powershell
node docs/contracts/verify-analytics-examples.cjs
git diff --check
```

| Kiểm tra | Expected | Actual |
|---|---|---|
| Đọc JSON, nhãn và liên kết DTO sample | MO_PHONG; comment/snapshot/goal cùng scope, definition/baseline liên kết được | PASS |
| Metric 0, permission, unsupported, not-synced, stale | 0 giữ nguyên; thiếu có reason; stale giữ value/time | PASS, 5 ca |
| ER đủ/0 numerator/0 denominator/thiếu share hoặc impression | 5%; 0%; null với reason phù hợp | PASS, 5 ca |
| Snapshot giảm/thiếu baseline/baseline 0/khác definition | -5 và -5%; null; delta 5/growth null; incompatible | PASS, 4 ca |
| Goal thường/âm/vượt/interaction/target sai/thiếu actual | 50%; -25%; 125%; 50%; null; null | PASS, 6 ca |
| Trang chồng lặp có comment chỉnh sửa | 3 comment duy nhất, giữ nội dung mới của mock-c1 | PASS |
| Empty success, permission error, rate limit | Rỗng khác lỗi; lỗi không có page success | PASS, 3 ca |
| Ranh giới tuần UTC+07 | Hai mốc là thứ Hai 00:00; start inclusive, end exclusive | PASS, 5 ca |
| Hai mốc đầy đủ/partial/một mốc/không có lịch sử | FULL/PARTIAL/NONE/NONE; không bịa mốc | PASS, 4 ca |
| Top tie, số âm, giá trị thiếu | p1 trước p2, giữ p3 âm, loại p4 null | PASS |
| ER nhóm có trọng số | (20+10)/(400+100) × 100 = 6%, không phải trung bình 7,5% | PASS |
| Tổng validator fixture | Exit 0, 36 ca | **36 PASS**, exit 0 |
| Whitespace tracked diff | Không lỗi | PASS, exit 0 |
| Liên kết local và LF/newline của các file mới/sửa | Tất cả đích tồn tại; UTF-8/LF, newline cuối | PASS khi chốt phiên |

Validator chỉ đối chiếu số học, cấu trúc mẫu và expected result trong draft.
Không kiểm tra implementation repository/parser/Room, HTTP, race/cancellation hay quyền provider.
Không gọi đây là unit test Android, test API thật hoặc test schema đầy đủ.
Ca API_THAT = **0**, fixture từ API thật đã che = **0**; toàn bộ 36 ca là MO_PHONG.

Lượt QA line ending đầu phát hiện CRLF ở architecture.md (checkout cũ có CRLF),
nên chưa chạy hết phần kiểm tra link. Đã chuẩn hóa các file mới/sửa và plan sang LF
theo .editorconfig, rồi chạy lại QA; giữ ghi nhận lỗi đầu, không tính lượt đó là PASS.

## 5. Bước tiếp theo và điều kiện hoàn tất

1. Minh/Thiện review draft-2 và expected results; Đạt xác nhận ID/null/ownership/index/thời gian.
   Xác minh Project live trước khi cập nhật trạng thái khi có quyền/yêu cầu cập nhật tracker.
2. Chốt các quyết định còn mở trong mục 10 contract. Sau đó mới thêm Java DTO/repository
   interface/công thức nếu cần tích hợp; kiểm tra Android tương ứng, không đổi schema tự phát.
3. Đầu vào token/đối tượng thử thuộc SP-018/019, không chặn hoàn tất contract W1 SP-020.
   Khi làm các task API, Minh cung cấp đầu vào SP-010/#12, SP-011/#13 và owner tra docs chính thức.
4. SP-020 chỉ cập nhật revision khi SP-018/019 phát hiện giới hạn sau đó; không bắt buộc
   có response hoặc lịch sử API thật để nghiệm thu contract W1. PR mỗi ticket riêng,
   hai human approvals, checks áp dụng đạt, merge và cập nhật tracker mới hoàn tất quy trình.

Chưa tạo PR/push hoặc gửi tin nhắn bên ngoài trong phiên này. Không tick xác nhận người nhận.

## 6. Rà soát kỹ theo yêu cầu người dùng — draft-2

Đối chiếu trực tiếp mục SP-020 trong brief TV4 và proposal F6/F7, đọc cả năm file repo
đầu ra, kiểm tra logic validator. Không tái xác minh tracker từ mạng trong lượt rà soát này;
thông tin GitHub mục 1 là lần đọc trước trong cùng ngày, không phải snapshot live mới.

### Vấn đề phát hiện và sửa local

| Vấn đề draft-1 | Sửa trong draft-2 | Bằng chứng / giới hạn |
|---|---|---|
| Thiếu current có thể bị JavaScript ép null thành 0, tạo delta âm giả | Trả MISSING_CURRENT trước số học; thêm ca thiếu current | Snapshot missing-current |
| Chỉ so definition, chưa kiểm tra scope/unit/kind/source/period/thứ tự thời gian | Thêm điều kiện tương thích và expected rejection, phân biệt MISSING_BASELINE/MISSING_CURRENT | 8 ca snapshot mới; 3 ca ER khác scope/window/source |
| DTO dùng observedAtBasis/goal revision/status/coverage nhưng chưa liệt kê đầy đủ | Khai báo field; tách temporal/cohort coverage; NOT_SYNCED có freshness null; thêm DTO con dashboard và state | Contract mục 3–7; kiểm tra liên kết DTO mẫu, chưa phải JSON Schema hoặc Java DTO đầy đủ |
| Giả định sample.collectedAt luôn bằng snapshot.collectedAt | Thêm collectionStartedAt, snapshot là giờ hoàn tất; sample giữ giờ nhận thật | DTO mẫu dùng hai thời điểm khác nhau; validator kiểm tra sample nằm trong đợt thu |
| Chung collectionId dễ bị hiểu là cùng phạm vi đo | Ghi rõ cần alignment/provider measurement window, không tự ghép đa endpoint | Cần Minh review chính sách alignment trước adapter |
| FULL tuần 00:00 có thể bị nhầm là đạt nhờ lịch 00:15; goal có thể lấy số ngoài kỳ | Ghi rõ PARTIAL và actualStart/end; chặn mẫu sau periodEnd | 3 ca coverage mới; goal cutoff; không tự coi PARTIAL là goal đạt |
| Chưa đối chiếu các nhánh dedup/cursor trong tài liệu | Thêm trang unknown, cặp hasMore/cursor sai, response cũ, cùng giờ xung đột, thiếu update time, postId xung đột; thêm top có publishedAt null | 6 pagination shape + 4 dedup; top giữ tie-break ổn định |
| Các bước tiếp theo lẫn bằng chứng API với DoD SP-020 | Tách API/token và lịch sử thật sang SP-018/019/SP-045; SP-020 không chờ các API thành công | Sửa mục 5 hồ sơ và mục 10 contract |

Kết quả lệnh `node docs/contracts/verify-analytics-examples.cjs`: **61/61 PASS, exit 0**.
Thêm 5 probe chạy validator qua Node VM với bản fixture sửa trong bộ nhớ:
expected ER sai (5 thành 6), null thiếu reason, revision goal bằng 0,
sample nhận sau khi collection kết thúc, và missing-current bị gán delta=-100.
Cả **5/5 bị từ chối** bằng assertion; fixture gốc không bị ghi đè.
QA cuối: 6 file UTF-8/LF/newline/whitespace và 21 đích link local đạt;
`node --check docs/contracts/verify-analytics-examples.cjs` và `git diff --check` exit 0.
Metadata scope/window/source trong fixture là projection đã chuẩn hóa, không phải response
provider hoặc xác minh rằng adapter đã normalize đúng. Các ca negative nêu expected bị từ chối,
không phải 61 ca HTTP hay 61 unit test production. Toàn bộ vẫn MO_PHONG.

### Mức đáp ứng tiêu chí SP-020

| Tiêu chí brief | Đánh giá hiện tại | Còn thiếu để chốt |
|---|---|---|
| DTO có ID/nguồn/time/null; pagination/dedup độc lập UI | Có schema tài liệu, mẫu comment/snapshot/goal và quy tắc; Java DTO/interface chưa có | Minh/Thiện xác nhận cách bàn giao document-only đủ dùng, hoặc thống nhất thêm Java contract; Đạt xác nhận ID/ownership/null/time |
| Công thức có ca 0/thiếu/số giảm và cùng định nghĩa/khoảng đo | Có ví dụ + 61 fixture checks; chưa có runtime nghiệp vụ | Review định nghĩa/alignment với Minh/Thiện; chỉ cần unit test Java khi có code tính thật |
| Chốt tuần/timezone/hai snapshot/goal/top, không bịa lịch sử | Đã soạn rõ nhưng vẫn là đề xuất; không có bằng chứng đã chốt | Quyết định slot/coverage, goal partial/stale, index/query với Đạt; ghi người xác nhận và revision |
| Thiện xác nhận đủ trường; Nghị owner/lịch W2–3 | Có field list và lịch dự kiến; chưa có xác nhận Thiện, lịch chưa chốt | Walk-through fixture/UI; ghi xác nhận Thiện và lịch thu được đồng ý |
| Quy trình hoàn tất | Đóng gói commit local; chưa push/PR, hai approval, merge hoặc cập nhật tracker | Tạo hồ sơ PR khi được yêu cầu; human review/checks/merge và tracker theo CONTRIBUTING |

**Kết luận:** phù hợp phạm vi và đủ làm bản nháp review; **chưa đủ điều kiện đóng SP-020**.
Không cần xây cả dashboard, Room migration, worker production hoặc báo cáo AI trong task này.
Các bổ sung kỹ thuật local đã sửa; phần còn lại cần quyết định người dùng contract và quy trình nhóm.
