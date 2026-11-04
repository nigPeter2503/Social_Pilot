# Taskbrief chung — SocialPilot Tuần 1

Đồng bộ ngày **04/11/2026**, múi giờ Asia/Bangkok, từ GitHub Issues và Project. Bản này dùng mã SP và URL ticket thật cho toàn bộ công việc Tuần 1.

- Repository: [nigPeter2503/Social_Pilot](https://github.com/nigPeter2503/Social_Pilot).
- Project: [Social Pilot - Development Board 2024_31_MD_2 — #5](https://github.com/users/nigPeter2503/projects/5).
- **Đã tạo và assign 13 issue Tuần 1: SP-009–SP-021.** Tất cả đang **OPEN / Todo (Chưa làm)** tại thời điểm đồng bộ.
- Board có 58/58 item đã đọc đủ; 13 card Tuần 1 khớp URL và assignee. Các nhóm kết quả cha và công việc sau Tuần 1 được theo dõi trong backlog chung.
- Board hiện có Todo/In Progress/Done. Reviewer dự kiến, ưu tiên, requirement, ước lượng và mốc tuần được ghi trong nội dung issue; yêu cầu review được gửi khi có PR.
- Tuần 1 là **02–08/11/2026** theo CONTRIBUTING. Giữ mốc ngày làm việc tương đối trong issue, không tính lại từ ngày nhận task. Giữ toàn bộ đầu ra và mốc bàn giao; theo quyết định đã ghi trong issue, các thành viên tự phân bổ thời gian, không áp trần 24 giờ/người/tuần. Mức 24 giờ vẫn là công sức danh nghĩa để đối chiếu tải, không phải bằng chứng đủ nguồn lực.

## 1. Thành viên và taskbrief chi tiết

| Thành viên | GitHub đã assign | Task chính | Taskbrief |
|---|---|---|---|
| TV1 — Ngô Gia Minh | [nigPeter2503](https://github.com/nigPeter2503) | [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11), [SP-010](https://github.com/nigPeter2503/Social_Pilot/issues/12), [SP-011](https://github.com/nigPeter2503/Social_Pilot/issues/13) | [Chi tiết](tv1-minh-taskbrief.md) |
| TV2 — Nguyễn Đức Đạt | [dat532006](https://github.com/dat532006) | [SP-012](https://github.com/nigPeter2503/Social_Pilot/issues/14), [SP-013](https://github.com/nigPeter2503/Social_Pilot/issues/15), [SP-014](https://github.com/nigPeter2503/Social_Pilot/issues/16) | [Chi tiết](tv2-dat-taskbrief.md) |
| TV3 — Đỗ Phước Thiện | [PhuocThien06](https://github.com/PhuocThien06) | [SP-015](https://github.com/nigPeter2503/Social_Pilot/issues/17), [SP-016](https://github.com/nigPeter2503/Social_Pilot/issues/18), [SP-017](https://github.com/nigPeter2503/Social_Pilot/issues/19) | [Chi tiết](tv3-thien-taskbrief.md) |
| TV4 — Tô Lãng Nghị | [bob35306](https://github.com/bob35306) | [SP-018](https://github.com/nigPeter2503/Social_Pilot/issues/20), [SP-019](https://github.com/nigPeter2503/Social_Pilot/issues/21), [SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22) | [Chi tiết](tv4-nghi-taskbrief.md) |

Minh là owner [SP-021](https://github.com/nigPeter2503/Social_Pilot/issues/23); cả bốn thành viên tham gia phối hợp/demo. Hai reviewer ghi trong issue là người review dự kiến khi có PR, chưa phải approval đã có.

## 2. Nguồn và cách dùng

- [CONTRIBUTING](../../../CONTRIBUTING.md): ticket/branch/PR, hai human approvals, điều kiện hoàn tất và phối hợp schema.
- [README](../../../README.md), [proposal](../../SocialPilot_Proposal_Java.md), [ma trận yêu cầu](../../requirements.md), [kiến trúc](../../architecture.md) và [phát triển](../../development.md).
- Issue là nguồn đối chiếu mã SP, requirement, owner, reviewer, phạm vi, ước lượng và mốc bàn giao. Card Project là nguồn trạng thái; cập nhật tiến độ và bằng chứng trực tiếp trên ticket.
- Bốn brief cá nhân giữ chỉ dẫn triển khai chi tiết; mọi tham chiếu task dùng cùng mã SP với ticket. Mã SP độc lập với số issue GitHub.

Baseline đã có topic → ViewModel → Repository → Room, resource VI/EN và backend health. OAuth start trả 501 OAUTH_NOT_CONFIGURED; các contract AI/queue/budget/sync là điểm mở rộng. Đây là đầu vào để triển khai, chưa phải bằng chứng task mới đã hoàn thành. Kết quả build cũ ở [build-verification](../../build-verification.md); lượt đồng bộ này chỉ kiểm tra tài liệu và tracker.

## 3. Danh mục task đã tạo và assign

Ước lượng owner gồm thiết kế/triển khai, tích hợp, kiểm thử và tài liệu. Mốc ngày 2/3/4 tính từ đầu Tuần 1, không quy đổi sang ngày lịch.

| Mã và issue thật | Owner | Hai reviewer khi có PR | Đầu ra | Giờ owner | Mốc bàn giao | Brief chi tiết |
|---|---|---|---|---:|---|---|
| [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11) — #11 | Minh (`nigPeter2503`) | Đạt, Nghị | Chốt hợp đồng OAuth, capability và kết quả đăng | 6 | Hết ngày làm việc 2 | [Chi tiết](tv1-minh-taskbrief.md#sp-009) |
| [SP-010](https://github.com/nigPeter2503/Social_Pilot/issues/12) — #12 | Minh (`nigPeter2503`) | Đạt, Thiện | Kiểm chứng Facebook OAuth callback và một bài đăng thật | 8 | Hết ngày làm việc 3 | [Chi tiết](tv1-minh-taskbrief.md#sp-010) |
| [SP-011](https://github.com/nigPeter2503/Social_Pilot/issues/13) — #13 | Minh (`nigPeter2503`) | Đạt, Nghị | Kiểm chứng LinkedIn OAuth, quyền đăng và refresh | 8 | Hết ngày làm việc 4 | [Chi tiết](tv1-minh-taskbrief.md#sp-011) |
| [SP-012](https://github.com/nigPeter2503/Social_Pilot/issues/14) — #14 | Đạt (`dat532006`) | Minh, Thiện | Chốt quy ước Room, quyền sở hữu dữ liệu và thứ tự migration | 5 | Hết ngày làm việc 2 | [Chi tiết](tv2-dat-taskbrief.md#sp-012) |
| [SP-013](https://github.com/nigPeter2503/Social_Pilot/issues/15) — #15 | Đạt (`dat532006`) | Minh, Nghị | Chốt hợp đồng AI và đo tác vụ thật trên hai provider | 7 | Hết ngày làm việc 3 | [Chi tiết](tv2-dat-taskbrief.md#sp-013) |
| [SP-014](https://github.com/nigPeter2503/Social_Pilot/issues/16) — #16 | Đạt (`dat532006`) | Thiện, Nghị | Kiểm chứng hai nhánh stream và hủy độc lập trên Java | 8 | Hết ngày làm việc 4 | [Chi tiết](tv2-dat-taskbrief.md#sp-014) |
| [SP-015](https://github.com/nigPeter2503/Social_Pilot/issues/17) — #17 | Thiện (`PhuocThien06`) | Minh, Nghị | Xác nhận baseline build, CI và thiết bị demo | 4 | Hết ngày làm việc 2 | [Chi tiết](tv3-thien-taskbrief.md#sp-015) |
| [SP-016](https://github.com/nigPeter2503/Social_Pilot/issues/18) — #18 | Thiện (`PhuocThien06`) | Minh, Đạt | Chốt wireframe và hợp đồng brief, phiên bản, duyệt | 6 | Hết ngày làm việc 3 | [Chi tiết](tv3-thien-taskbrief.md#sp-016) |
| [SP-017](https://github.com/nigPeter2503/Social_Pilot/issues/19) — #19 | Thiện (`PhuocThien06`) | Đạt, Nghị | Nối bản mẫu UI, Room và WorkManager có kiểm thử vòng đời | 8 | Trước demo cuối Tuần 1 | [Chi tiết](tv3-thien-taskbrief.md#sp-017) |
| [SP-018](https://github.com/nigPeter2503/Social_Pilot/issues/20) — #20 | Nghị (`bob35306`) | Minh, Thiện | Kiểm chứng Facebook comment, reply và số liệu thật | 6 | Hết ngày làm việc 3 | [Chi tiết](tv4-nghi-taskbrief.md#sp-018) |
| [SP-019](https://github.com/nigPeter2503/Social_Pilot/issues/21) — #21 | Nghị (`bob35306`) | Đạt, Thiện | Kiểm chứng LinkedIn comment, reply, follower và số liệu | 6 | Hết ngày làm việc 4 | [Chi tiết](tv4-nghi-taskbrief.md#sp-019) |
| [SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22) — #22 | Nghị (`bob35306`) | Minh, Thiện | Chốt hợp đồng comment, snapshot, công thức và dashboard | 6 | Hết ngày làm việc 3 | [Chi tiết](tv4-nghi-taskbrief.md#sp-020) |
| [SP-021](https://github.com/nigPeter2503/Social_Pilot/issues/23) — #23 | Minh (`nigPeter2503`) | Đạt, Nghị | Demo Tuần 1, xử lý blocker và chốt ước lượng còn lại | 2 | Cuối Tuần 1 | [Chi tiết](#sp-021) |

Riêng [SP-021](https://github.com/nigPeter2503/Social_Pilot/issues/23): 2 giờ cho mỗi thành viên, tổng nhóm 8 giờ. Hai reviewer là Đạt/Nghị nếu có PR tài liệu; họp/demo không cần PR rỗng.

## 4. Đầu ra cuối Tuần 1 và kiểm chứng

| Đầu ra | Ticket | Bằng chứng phải có |
|---|---|---|
| Baseline Java/XML + Spring Boot, CI và thiết bị | [SP-015](https://github.com/nigPeter2503/Social_Pilot/issues/17) | Wrapper build/test/lint đúng revision, APK cài và thao tác được trên emulator/thiết bị, phiên bản khóa |
| Hợp đồng và wireframe | [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11), [SP-012](https://github.com/nigPeter2503/Social_Pilot/issues/14), [SP-013](https://github.com/nigPeter2503/Social_Pilot/issues/15), [SP-016](https://github.com/nigPeter2503/Social_Pilot/issues/18), [SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22) | DTO, lỗi/hủy, ID/ownership/schema/migration, brief/version/approval và mẫu dữ liệu; bên cung cấp/tiêu thụ xác nhận |
| OAuth và đăng hai nền tảng | [SP-010](https://github.com/nigPeter2503/Social_Pilot/issues/12), [SP-011](https://github.com/nigPeter2503/Social_Pilot/issues/13) | App/account/Page/scope/API version/callback/refresh và post ID/URL thật; quyền thiếu ghi blocker riêng |
| Comment/reply, follower và metrics | [SP-018](https://github.com/nigPeter2503/Social_Pilot/issues/20), [SP-019](https://github.com/nigPeter2503/Social_Pilot/issues/21) | Endpoint/quyền, phản hồi thật đã che bí mật và đối chiếu nền tảng; follower không thay bằng connections |
| Hai provider AI và chi phí mẫu | [SP-013](https://github.com/nigPeter2503/Social_Pilot/issues/15) | Tác vụ thật OpenAI/Gemini, usage/ước tính có nhãn, hạn mức thử |
| Stream, hủy, Room, lifecycle và Worker | [SP-014](https://github.com/nigPeter2503/Social_Pilot/issues/16), [SP-017](https://github.com/nigPeter2503/Social_Pilot/issues/19) | Hai nhánh đồng thời, hủy Future+HTTP Call, chặn callback muộn, xoay không tạo lệnh mới, restart giữ dữ liệu, job theo ràng buộc mạng |
| Demo và kế hoạch tiếp | [SP-021](https://github.com/nigPeter2503/Social_Pilot/issues/23) | Revision/video, kết quả từng phép thử, blocker có người xử lý và ước lượng còn lại |

Giữ 44 yêu cầu cam kết, gồm 33 B và 11 N đã chọn; chỉ F3.N2 và F6.N1 là Optional. Có issue/assignee hoặc chạy mock không đồng nghĩa yêu cầu đã nghiệm thu. Mục tiêu API thật thiếu bằng chứng phải ghi chưa đạt/bị chặn.

## 5. Phụ thuộc và điều kiện mở chặn

| Bên nhận | Ticket cung cấp / đầu vào | Điều kiện mở chặn |
|---|---|---|
| [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11) | Nhu cầu social/version sơ bộ từ Nghị/Thiện | Chốt DTO/callback/capability với bên dùng; không cần token/API thật để thiết kế |
| [SP-010](https://github.com/nigPeter2503/Social_Pilot/issues/12) | SP-009; Meta app/Page/HTTPS/quyền thử | Callback contract đã review để triển khai; token và quyền hợp lệ cho phép thử OAuth/đăng thật |
| [SP-011](https://github.com/nigPeter2503/Social_Pilot/issues/13) | SP-009; callback chung SP-010; LinkedIn app/product/quyền | Tái dùng callback đã kiểm tra; kiểm kê LinkedIn độc lập, không chờ Facebook được duyệt quyền |
| [SP-012](https://github.com/nigPeter2503/Social_Pilot/issues/14) | Nhu cầu dữ liệu sơ bộ từ module owner | Chốt ID/ownership/quy ước nền trước DTO chi tiết; không chờ SP-016/SP-020 hoàn tất |
| [SP-013](https://github.com/nigPeter2503/Social_Pilot/issues/15) | SP-012; key/quota và hạn mức thử | Contract event dùng được để mock; tác vụ thật mỗi provider cần key/quota tương ứng |
| [SP-014](https://github.com/nigPeter2503/Social_Pilot/issues/16) | Contract event/cancel SP-013 | Chữ ký đã review và fake/MockWebServer đủ cho ca stream/hủy; không chờ hai provider thật |
| [SP-015](https://github.com/nigPeter2503/Social_Pilot/issues/17) | Repo, JDK/SDK, emulator/thiết bị | Có môi trường chạy hai Wrapper và thiết bị; không phụ thuộc quyền social/AI |
| [SP-016](https://github.com/nigPeter2503/Social_Pilot/issues/18) | SP-009, SP-012 | ID/version đã chốt để đóng contract; wireframe và payload nháp làm trước |
| [SP-017](https://github.com/nigPeter2503/Social_Pilot/issues/19) | SP-015 baseline; SP-012 schema; SP-014 runtime; mẫu SP-016 | Room/Worker làm độc lập; nối stream sau runtime đã kiểm tra, dùng schema/mẫu đã thống nhất |
| [SP-018](https://github.com/nigPeter2503/Social_Pilot/issues/20) | SP-009; token/Page/post từ SP-010 hoặc tài khoản thử sẵn có | Token hợp lệ và quyền từng endpoint để thử thật; parser/fixture làm trước |
| [SP-019](https://github.com/nigPeter2503/Social_Pilot/issues/21) | SP-009; token/author/post từ SP-011 hoặc tài khoản thử sẵn có; ID SP-012 | Kiểm kê riêng quyền LinkedIn comment/metrics/follower; không suy từ quyền đăng |
| [SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22) | SP-009, SP-012; nhu cầu UI của Thiện | Contract ID/capability nền đã chốt; fixture có nhãn, không chờ SP-018/SP-019 thử thật |

Code dùng trong bản tích hợp phải có checks và PR merge theo CONTRIBUTING. Các đầu vào sơ bộ có thể trao đổi sớm để thiết kế/test riêng; API thật chỉ là điều kiện của phép thử cần API.

- [SP-018](https://github.com/nigPeter2503/Social_Pilot/issues/20) nhận token thử hợp lệ có sẵn hoặc đầu ra [SP-010](https://github.com/nigPeter2503/Social_Pilot/issues/12); [SP-019](https://github.com/nigPeter2503/Social_Pilot/issues/21) nhận token thử có sẵn hoặc [SP-011](https://github.com/nigPeter2503/Social_Pilot/issues/13).
- [SP-011](https://github.com/nigPeter2503/Social_Pilot/issues/13) dùng khung callback từ [SP-010](https://github.com/nigPeter2503/Social_Pilot/issues/12) khi sẵn, không chờ Facebook xét quyền. [SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22) không chờ API thật để chốt contract dữ liệu.
- Fixture/mock có nhãn. Có thể thiết kế/test phần độc lập trong khi chờ token/adapter; chỉ bước tích hợp thật bị chặn. Hết ước lượng giờ không tự làm API đạt.

## 6. Chạy song song và tích hợp

| Mốc | Minh | Đạt | Thiện | Nghị |
|---|---|---|---|---|
| Đầu Tuần 1 → ngày làm việc 2 | [SP-009](https://github.com/nigPeter2503/Social_Pilot/issues/11), chuẩn bị quyền/app | [SP-012](https://github.com/nigPeter2503/Social_Pilot/issues/14), event AI sơ bộ | [SP-015](https://github.com/nigPeter2503/Social_Pilot/issues/17), nhu cầu wireframe | Chuẩn bị quyền/API, nhu cầu [SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22) |
| Ngày làm việc 2–3 | [SP-010](https://github.com/nigPeter2503/Social_Pilot/issues/12), callback dùng chung | [SP-013](https://github.com/nigPeter2503/Social_Pilot/issues/15) | [SP-016](https://github.com/nigPeter2503/Social_Pilot/issues/18), Room/Worker độc lập | [SP-020](https://github.com/nigPeter2503/Social_Pilot/issues/22), [SP-018](https://github.com/nigPeter2503/Social_Pilot/issues/20) theo token sẵn có |
| Ngày làm việc 3–4 | [SP-011](https://github.com/nigPeter2503/Social_Pilot/issues/13) | [SP-014](https://github.com/nigPeter2503/Social_Pilot/issues/16) | [SP-017](https://github.com/nigPeter2503/Social_Pilot/issues/19), nối runtime khi sẵn | [SP-019](https://github.com/nigPeter2503/Social_Pilot/issues/21), đối chiếu khả năng API |
| Trước demo cuối Tuần 1 | Điều phối [SP-021](https://github.com/nigPeter2503/Social_Pilot/issues/23) | Hỗ trợ runtime/schema, review | APK và thử vòng đời | Tổng hợp quyền/dữ liệu/bằng chứng |

Thứ tự tích hợp: contract/ID → DTO/fixture và adapter spike → runtime/API spike → wiring UI/Room/Worker → build lại bản tích hợp và demo. Chỉ chờ tiền đề thực sự cần; mỗi người ưu tiên một task chính và chuẩn bị đầu vào sớm cho bên nhận.

| Phần dùng chung | Điều phối | Quy tắc |
|---|---|---|
| SocialConnector/DTO OAuth/capability/lỗi | Minh | Nghị cung cấp nhu cầu comment/metrics; chốt chữ ký trước khi adapter sửa |
| AiProvider/AgentOrchestrator/BudgetManager/Cancelable | Đạt | Chốt event/cancel/usage; mọi AI module dùng dịch vụ và ngân sách chung |
| AppDatabase/version/schema/migration | Đạt | Một task sửa chuỗi chung mỗi thời điểm; module owner tự làm entity/DAO/test dữ liệu |
| AppContainer/wiring, manifest/resources/build/CI | Thiện phối hợp owner liên quan | Bàn giao factory/contract và chuỗi VI/EN; checkout/worktree riêng khi có xung đột |
| Dữ liệu/công thức comment, snapshot, dashboard | Nghị | Thiện dùng DTO/repository, không tính lại chỉ số trong View |

## 7. Công sức tham khảo

| Thành viên | Task chính, gồm tích hợp/test/docs | Review 6 lượt × 0,75h | Phối hợp/demo | Tổng |
|---|---:|---:|---:|---:|
| Minh | 22h | 4,5h | 2h | 28,5h |
| Đạt | 20h | 4,5h | 2h | 26,5h |
| Thiện | 18h | 4,5h | 2h | 24,5h |
| Nghị | 18h | 4,5h | 2h | 24,5h |
| Nhóm | **78h** | **18h** | **8h** | **104h** |

Đây là ước lượng công sức, không phải giờ đã làm hoặc giới hạn thời gian. **104h cao hơn mức danh nghĩa 96h là 8h (~8,3%)**, chưa tính SP-001 và công việc soạn bộ taskbrief này. 0,75h/lượt là review ban đầu; vòng sửa/review lại chưa có dự phòng riêng. Nếu SP-021 có PR tài liệu, tải review bổ sung của Đạt/Nghị cũng cần cộng vào ước lượng còn lại.

Giữ đầu ra và mốc bàn giao theo quyết định đã ghi trong issue; từng thành viên tự sắp xếp thời gian, kể cả review và sửa nhận xét. Mỗi owner tự tích hợp/sửa phần mình; Minh điều phối mốc chung, Đạt schema/runtime, Thiện wiring/CI, Nghị dữ liệu/API. Quyết định tự phân bổ giờ không chứng minh chắc chắn đạt lịch; rà việc còn lại tại hai buổi họp và báo blocker ngay khi xuất hiện.

<a id="sp-021"></a>

## SP-021 — Demo Tuần 1, xử lý blocker và chốt ước lượng còn lại

- **Issue:** [SP-021](https://github.com/nigPeter2503/Social_Pilot/issues/23) — #23.
- **Issue cha:** [SP-008](https://github.com/nigPeter2503/Social_Pilot/issues/10).
- **Trạng thái đã kiểm chứng:** Issue OPEN; Project Todo (Chưa làm); đã assign cho `nigPeter2503`.
- **Yêu cầu:** DOCS, INFRA.
- **Owner:** Ngô Gia Minh (`nigPeter2503`).
- **Hai reviewer khi có PR tài liệu:** Nguyễn Đức Đạt (`dat532006`), Tô Lãng Nghị (`bob35306`).
- **Mốc bàn giao:** Cuối Tuần 1.
- **Ước lượng:** mỗi người 2 giờ, tổng nhóm 8 giờ; đã tính trong bảng công sức.
- **Branch dự kiến nếu có thay đổi repo:** `docs/sp-021-week-1-demo-report` (chưa tạo), base `main`; họp/demo không tạo branch/PR rỗng.

### Đầu ra và tiêu chí đạt

- [ ] Mỗi đầu ra có revision/ngày thử/người xác nhận/bằng chứng, phân biệt thật với mock
- [ ] Giữ đủ 44 yêu cầu; phần chưa kiểm chứng/bị chặn không được cộng đạt; cập nhật ước lượng công sức và khả năng đạt mốc; không dùng 24h/người/tuần làm giới hạn
- [ ] Người dùng xác nhận giữ toàn bộ đầu ra và mốc bàn giao; không giới hạn giờ làm việc, mỗi thành viên tự phân bổ thời gian. Chuẩn bị Current tasks/Next plan/Blockers, không gửi Slack trong lượt lập backlog này

- Hai họp ngắn và demo cuối tuần; owner từng module cung cấp evidence và việc còn lại. Chuẩn bị Current tasks / Next plan / Blockers; người trực báo cáo theo vòng TV1 → TV2 → TV3 → TV4 trong CONTRIBUTING.
- Mỗi người 2 giờ: hai họp 0,25 giờ/lần + 0,75 giờ chuẩn bị + 0,75 giờ demo/quyết định. Minh tổng hợp demo; Đạt/Nghị review nếu phát sinh PR tài liệu.
- Tiến độ và bằng chứng cập nhật tại issue #23. Họp/demo không cần PR rỗng; tài liệu được commit đi qua PR với hai review.

## 8. Đầu vào cần chuẩn bị

| Đầu vào/rủi ro | Owner | Mốc xử lý | Phần có thể tiếp tục |
|---|---|---|---|
| Meta app/Page, LinkedIn account/product/admin/scope/callback HTTPS | Minh + Nghị | Đầu tuần; quyết định phần bị chặn trước demo | Contract, fixture, parser, test callback |
| Key/quota/hạn mức thử AI | Đạt | Trước tác vụ thật SP-013 | Hợp đồng event và MockWebServer |
| ID/schema/migration dùng chung | Đạt và module owner | Ngày làm việc 2 | Gửi nhu cầu sơ bộ; tránh sửa database/version cạnh tranh |
| Emulator/điện thoại và trình duyệt callback | Thiện phối hợp Minh | SP-015, ngày làm việc 2 | Môi trường ổn định tương thích JDK 21/SDK 36/minSdk 23 đã khóa |
| Runtime hoặc token đến muộn | Owner cung cấp và bên nhận | Báo ngay khi ảnh hưởng mốc | Làm phần độc lập, ghi phép thử thật còn thiếu |

GitHub đã đủ mapping và đã assign. Tài khoản/quyền cần kiểm chứng ở đây là Facebook/LinkedIn và provider AI; thiết bị là emulator/điện thoại kiểm thử. Không chờ xác nhận lịch bận để bắt đầu task đã giao.

## 9. Theo dõi tiến độ và điều kiện hoàn tất

- Dùng issue đã có để nhận việc/cập nhật blocker; chuyển card Todo → In Progress khi bắt đầu. Ưu tiên, tuần, ước lượng và reviewer giữ trong nội dung issue do board hiện không có các field riêng tương ứng.
- Khi có PR, gắn ticket và mã SP; yêu cầu hai reviewer thật khác tác giả approve. Checks đạt, xử lý nhận xét bắt buộc, squash merge và cập nhật evidence/Project theo CONTRIBUTING.
- Chỉ tài liệu: kiểm tra nội dung/liên kết và `git diff --check`. Android/backend dùng Wrapper theo [development](../../development.md); UI/lifecycle/job nền thử emulator/thiết bị, schema có test migration.
- Các task đang OPEN/Todo tại thời điểm đồng bộ. Chưa ghi nhận PR Tuần 1 trong danh sách PR đã kiểm tra; SP-001/PR #3 là công việc tài liệu trước đó, PR #3 đã MERGED.
- Đồng bộ TaskBrief cập nhật hồ sơ giao việc, không nâng trạng thái triển khai hoặc nghiệm thu F1–F9.

## 10. Quy ước triển khai Tuần 1

Theo [CONTRIBUTING mục 2–4](../../../CONTRIBUTING.md#2-nhánh-git): branch `<type>/sp-xxx-<slug>` viết thường, không dấu, nối từ bằng `-`; commit và tiêu đề PR `<type>(<scope>): <mô tả> [SP-xxx]`. Mỗi ticket có branch/PR riêng về `main`, tạo từ `main` mới nhất khi nhận việc. Không dùng lại branch SP-001 đã merge cho bộ taskbrief.

| Task / issue | Branch dự kiến, chưa tạo |
|---|---|
| SP-009 / #11 | `feat/sp-009-social-contract` |
| SP-010 / #12 | `feat/sp-010-facebook-oauth-spike` |
| SP-011 / #13 | `feat/sp-011-linkedin-oauth-spike` |
| SP-012 / #14 | `docs/sp-012-room-schema-contract` |
| SP-013 / #15 | `feat/sp-013-ai-provider-spike` |
| SP-014 / #16 | `feat/sp-014-dual-stream-cancellation` |
| SP-015 / #17 | `chore/sp-015-baseline-verification` |
| SP-016 / #18 | `feat/sp-016-brief-approval-contract` |
| SP-017 / #19 | `feat/sp-017-ui-room-worker-prototype` |
| SP-018 / #20 | `feat/sp-018-facebook-comments-metrics-spike` |
| SP-019 / #21 | `feat/sp-019-linkedin-comments-metrics-spike` |
| SP-020 / #22 | `feat/sp-020-analytics-data-contract` |
| SP-021 / #23 | `docs/sp-021-week-1-demo-report`, chỉ khi có thay đổi repo |

Ngày 04/11/2026 đã bổ sung tên trên vào từng issue và đọc lại để kiểm chứng. Đây là kế hoạch đặt tên, chưa phải remote branch hoặc liên kết Development đã được tạo. Owner tạo branch khi nhận task, cập nhật branch thực tế vào issue; khi có PR thêm `Closes #<số issue>`, head là branch của ticket, base `main`, chọn đúng hai reviewer. Nếu phạm vi thực tế cần đổi type/slug, cập nhật issue và brief cùng lúc.

Ví dụ cho SP-009: branch `feat/sp-009-social-contract`, commit/PR `feat(social): chốt hợp đồng OAuth và capability [SP-009]`, nội dung PR có `Closes #11`, requirement, thay đổi, kiểm tra thực tế và bàn giao. Việc chỉ ghi tên reviewer trong issue không thay thao tác request review trên PR.

Board hiện thiếu cột Review/Kiểm thử so với CONTRIBUTING; đây là chênh lệch cấu hình còn tồn tại. Trong lúc chưa đồng bộ board, giữ Todo cho Chưa làm, In Progress cho Đang làm/Review/Kiểm thử và Done chỉ khi đủ điều kiện hoàn tất. Owner ghi rõ bước Review/Kiểm thử và link PR/checks trong issue; không dùng ba cột để bỏ hai bước này. Lượt này không đổi field/cột Project.

## 11. Đánh giá và rủi ro còn lại

Đánh giá ngày 04/11/2026 từ năm brief, CONTRIBUTING, README, requirements, architecture, development, proposal, mã baseline và tracker trực tiếp: đã đọc đủ 58/58 item Project, đối chiếu 13 issue SP-009–SP-021 cùng danh sách branch/PR. Chủ việc và hai reviewer rõ; 24 lượt review cho 12 task chia đều 6 lượt/người; các dependency contract được tách khỏi quyền API và không có vòng chờ bắt buộc khi gửi đầu vào sơ bộ sớm.

| Điểm cần theo dõi | Tác động và cách xử lý | Người theo dõi / mốc |
|---|---|---|
| Ngày làm việc 2/3/4 chưa có lịch ngày cụ thể trong issue | Không tự dịch lại mốc từ ngày nhận việc. Hôm nay đã là 04/11; mỗi owner ghi ngay tiến độ thực tế, mốc đã lỡ nếu có và giờ còn lại. Lịch hiện chỉ đủ làm mốc thứ tự, chưa chứng minh kế hoạch ngày khả thi. | Minh tổng hợp, từng owner cập nhật khi nhận task và trước demo 08/11 |
| 104h so với 96h danh nghĩa; việc tài liệu và review lại chưa cộng đủ | Giữ phạm vi theo quyết định trong issue; công bố giờ còn lại và cửa sổ review, không coi tự tăng giờ là hết rủi ro. Minh/Đạt đang có tải cao nhất. | Mỗi owner tại hai buổi họp; Minh theo dõi đường tích hợp |
| SP-010 → SP-018, SP-011 → SP-019 có cùng mốc kết thúc | Bàn giao token/ID hợp lệ sớm hoặc dùng tài khoản thử sẵn có. Nếu chờ đến cuối mốc mới giao thì bên nhận không còn đủ cửa sổ kiểm chứng. | Minh + Nghị kiểm kê quyền ngay; cập nhật blocker trước mốc bên nhận |
| SP-013 → SP-014 → SP-017 sát cuối tuần | Gửi chữ ký event ngày 2 và runtime sớm ngày 3; Room/Worker làm độc lập. Bản demo tích hợp vẫn cần code đã merge và đủ review/checks. | Đạt + Thiện, rà trước cuối ngày làm việc 4 |
| Quyền API, refresh, key/quota và thiết bị chưa có evidence mới | Brief giao phép thử, không xác nhận nền tảng đã cấp quyền. Bị chặn phải ghi endpoint/quyền thiếu, người xử lý và thử nghiệm còn thiếu. | Minh/Nghị social; Đạt AI; Thiện thiết bị, trước phép thử tương ứng |
| Nhiều task sửa docs/architecture.md hoặc wiring chung | Các owner chuẩn bị phần riêng, chốt thứ tự merge với người điều phối ở mục 6; đồng bộ main rồi kiểm tra lại bản tích hợp. | Minh/Đạt/Thiện/Nghị theo phần sở hữu, trước khi sửa/merge |

Các tiêu chí trong brief bổ sung cách thực hiện cho issue, không thay thế hoặc hạ tiêu chí gốc. Có hồ sơ lỗi/blocker không đủ để đóng SP-015 nếu checks còn lỗi, hoặc đóng phép thử API thật của SP-010/SP-011/SP-018/SP-019 khi còn thiếu quyền. SP-020 thiết kế quy tắc snapshot; lịch sử thật được thu từ W2–3 theo SP-045.

**Kết luận:** nội dung đủ để dùng làm hồ sơ giao việc; chưa có cơ sở cam kết chắc chắn tất cả đầu ra W1 đúng hạn. Tiến độ, blocker và bằng chứng triển khai tiếp tục được cập nhật trên từng issue và Project.

Kiểm tra tài liệu và đồng bộ tracker không thay bằng chứng triển khai API/app. Kết quả kiểm tra của từng thay đổi được ghi trong issue/PR tương ứng theo CONTRIBUTING.
