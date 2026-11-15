# Taskbrief cá nhân tuần 2–3 — Nguyễn Đức Đạt (TV2)

**Owner:** Nguyễn Đức Đạt — GitHub **@dat532006**. Soạn ngày **15/11/2026**, Asia/Bangkok, theo [taskbrief chung đã được người dùng duyệt](week-2-3-taskbrief.md), revision `7134bde`. Gói tài liệu: [SP-063 / #80](https://github.com/nigPeter2503/Social_Pilot/issues/80). [Project #5](https://github.com/users/nigPeter2503/projects/5).

## 1. Trách nhiệm và đầu vào đã có

Đạt sở hữu F2/F8: BYOK, provider/model/usage, ngân sách dùng chung, pipeline hai nhánh, ảnh/template và production checkpoint. Đạt điều phối database/version/migration; mỗi module owner vẫn tự làm entity/DAO/migration/test của mình. Nghiệp vụ prompt của F3/F4 thuộc Thiện, phân loại/reply/báo cáo F6/F7 thuộc Nghị; Đạt cung cấp nền tảng, không nhận thay toàn bộ các nghiệp vụ đó.

Tuần 1 và bảy PR #69/#70/#73/#74/#75/#76/#77 được coi là xong. Tái sử dụng SP-013 typed request/event, OpenAI/Gemini text adapters, SP-014 coordinator/HTTP cancellation và SP-012 schema contract; không dựng lại hoặc chờ approval của các PR này. **SP-021 đã hoàn tất**, không nhận thêm task 2h; số đó chỉ đối chiếu baseline chung. Phê duyệt nội dung không phải nghiệm thu đủ F2/F8 hoặc GitHub approval.

Các issue triển khai có thật nhưng khi đọc tracker ngày 15/11 chưa có assignee. Brief xác định Đạt theo phân công đã duyệt; không tự thay assignment/status hay coi nhãn In Progress trong draft report là evidence đã nhận việc.

**Mốc:** bàn giao phần tuần 2 **thứ Hai, 16/11/2026**; cutoff tuần 3 **21/11**; tuần 4 **23–29/11**, hạn dự kiến **29/11**. Không dồn toàn bộ issue nhiều giai đoạn về 16/11; không coi giờ của cả tuần là giờ mới có sẵn từ hôm nay.

## 2. Công sức và trình tự

| Tuần | Công việc owner | SP-055 | Điều phối schema | Review | Tổng còn giao | SP-021 đã xong, đối chiếu | Baseline chung |
|---|---|---:|---:|---:|---:|---:|---:|
| 2 | SP-027 10h + SP-028 6h = **16h** | 2h | 2h | 2h | **22h** | 2h | 24h |
| 3 | SP-029 12h + SP-031 6h = **18h** | 2h | 2h | 2h | **24h** | 0h | 24h |
| 4 — dự kiến | SP-032 10h + SP-031 6h + SP-033 2h = **18h** | 2h | 2h | 2h | **24h** | 0h | 24h |

SP-028 6h là ước lượng phần còn lại, giảm từ backlog 12h do adapter đã bàn giao; xác minh trước khi chốt lịch. Không cộng giờ migration coordinator vào từng task lần nữa. Nếu review/schema vượt allowance, báo Minh điều chỉnh, không tự mở nhiều task chính hoặc bỏ migration test.

1. Bàn giao kho bí mật SP-027 trước để [Minh SP-022](tv1-minh-taskbrief.md#sp-022) nối social token và để SP-028 nối model/provider.
2. SP-028 bàn giao typed call/model/usage interface cho [Thiện](tv3-thien-taskbrief.md#sp-036) và [Nghị](tv4-nghi-taskbrief.md#sp-043). Không cho UI production dispatch không kiểm soát khi ledger SP-029 chưa sẵn sàng.
3. Tuần 3 hoàn tất ledger SP-029 trước khi mở production orchestrator SP-031. Chuẩn bị test/fixture của consumer có nhãn trong lúc chờ; không coi fake ledger là trần production.
4. Tuần 4 ưu tiên ảnh/template SP-032 theo image contract đã thống nhất, sau đó hoàn thiện packaging SP-031. Chỉ bắt đầu phần 2h SP-033 khi pipeline bàn giao; không báo toàn bộ checkpoint/recovery đã xong.

## 3. Task triển khai chi tiết

<a id="sp-027"></a>

### SP-027 — Kho bí mật Keystore và kiểm tra BYOK

- **Issue:** [#29](https://github.com/nigPeter2503/Social_Pilot/issues/29). **Yêu cầu:** F8.B2. **Ưu tiên:** High. **Công sức/mốc:** 10h tuần 2, hạn 16/11.
- **Hai reviewer dự kiến:** Ngô Gia Minh (@nigPeter2503), Đỗ Phước Thiện (@PhuocThien06). **Nhánh đề xuất, chưa tạo:** `feat/sp-027-secure-byok-store`.
- **Nguồn/phụ thuộc:** [Proposal F8/§3.5](../../SocialPilot_Proposal_Java.md), [Room contract §4](../../room-schema-contract.md), [AI contract](../../ai-provider-contract.md); SP-012/SP-013 đã có.

**Phạm vi/thực hiện:** tạo UI nhập/che/kiểm tra/xóa key và repository truy xuất bí mật; tái sử dụng cơ chế mã hóa phù hợp đã bàn giao thay vì refactor toàn kho OAuth. Keystore bảo vệ khóa mã hóa; ciphertext nằm trong vùng riêng, không plaintext Room. Social token có namespace riêng, DB chỉ giữ opaque reference. Chốt API lưu/đọc/xóa, loại lỗi và cách cấp credential cho provider với Minh trước khi nối.

- [ ] Key đúng/sai/thu hồi, thiếu quyền, mạng/timeout được phân loại; lỗi mạng không bị báo thành key sai.
- [ ] UI mặc định che key, restart còn cấu hình hợp lệ; xóa key xóa reference/ciphertext đúng scope, không xóa token/provider khác.
- [ ] Lỗi mã hóa/ghi/đọc hoặc Keystore key không dùng được không crash, không ghi đè hồ sơ hợp lệ bằng dữ liệu dở; có hướng nhập lại.
- [ ] Kiểm storage, backup/export/log: không thấy plaintext. LLM key chỉ gửi đúng provider, không qua backend OAuth, URL query hoặc redirect.
- [ ] Thread I/O/network chạy nền; hủy/đổi màn hình không để kết quả kiểm tra cũ ghi đè key mới. Chuỗi VI/EN và offline state đầy đủ.

**Kiểm thử/đầu ra:** unit với kho giả cho read/write/delete/error; instrumentation Keystore trên API 23/36 khi có thiết bị, trường hợp chưa chạy ghi rõ; network test kiểm host/header và lỗi chuẩn hóa. Bàn giao cho Minh credential-reference contract với success/missing/unreadable, cho Thiện UI state và cho adapter credential source. Không yêu cầu gửi key thật qua PR/chat.

<a id="sp-028"></a>

### SP-028 — Hai provider, lựa chọn model và usage production

- **Issue:** [#30](https://github.com/nigPeter2503/Social_Pilot/issues/30). **Yêu cầu:** F8.B1/B3/B4. **Ưu tiên:** High. **Công sức/mốc:** 6h tuần 2, hạn 16/11.
- **Hai reviewer dự kiến:** Ngô Gia Minh (@nigPeter2503), Tô Lãng Nghị (@bob35306). **Nhánh đề xuất:** `feat/sp-028-ai-model-usage-integration`.
- **Nguồn/phụ thuộc:** [AI contract §§1–5](../../ai-provider-contract.md), [Proposal F8](../../SocialPilot_Proposal_Java.md); SP-027 và adapter SP-013.

**Thực hiện:** đăng ký adapter hiện có vào service/repository dùng chung; lưu provider/model cho từng loại tác vụ theo capability registry đã xác nhận; nối usage/ước tính và trạng thái lỗi vào UI. Không viết lại parser SSE, đổi stack hoặc tự thêm provider thứ ba. Model/version/giá chưa chốt phải ghi thiếu và nguồn cần xác minh, không tự điền theo trí nhớ.

- [ ] Caller dùng typed request với session/channel/revision/step/requestId và output cap; model unsupported/unknown không được dispatch như supported.
- [ ] Chuyển provider không mất draft và usage đã ghi; gọi đúng host, không tự redirect/retry của transport để lách admission.
- [ ] Usage REPORTED/PARTIAL/MISSING giữ khác nhau; 0 chỉ khi provider thực trả 0, không cộng các snapshot lặp.
- [ ] Chi phí theo provider/ngày/tháng gắn timezone và price revision, phân biệt ước tính với hóa đơn; model ảnh/template là capability riêng cho SP-032.
- [ ] Bàn giao service/error/usage contract đủ cho Thiện và Nghị; UI production đi qua BudgetManager. Nếu ledger SP-029 chưa có, chưa mở dispatch production, giữ integration test có nhãn và ghi bước live acceptance còn phụ thuộc.
- [ ] Khi được cấp key/model/quota/số lượt/trần chi cụ thể, đường Android → từng provider có kết quả thật và usage hoặc nhãn thiếu. Phép thử này xác minh wiring production, không giao lại scope xây adapter tuần 1.

**Kiểm thử/đầu ra:** unit model selection/persistence và tổng usage; MockWebServer cho success/error/missing usage/cancel; live evidence tách từng provider với revision/output cap/request count và nguồn giá, không chứa credential. Nếu live gate chưa đủ, báo chưa kiểm chứng F8 tương ứng, không dùng fixture làm bằng chứng thật. Đầu ra tuần 2 là service integration/contract; production budget và nghiệm thu xuyên module còn phụ thuộc SP-029.

<a id="sp-029"></a>

### SP-029 — Ngân sách nguyên tử cho mọi tác vụ AI

- **Issue:** [#31](https://github.com/nigPeter2503/Social_Pilot/issues/31). **Yêu cầu:** F8.N1, F8.B4. **Ưu tiên:** High. **Công sức/mốc:** 12h tuần 3, hạn 21/11, trước production SP-031.
- **Hai reviewer dự kiến:** Ngô Gia Minh (@nigPeter2503), Tô Lãng Nghị (@bob35306). **Nhánh đề xuất:** `feat/sp-029-atomic-ai-budget`.
- **Nguồn/phụ thuộc:** [AI contract §3](../../ai-provider-contract.md), [Room contract §§3/5–7](../../room-schema-contract.md), [Proposal F8](../../SocialPilot_Proposal_Java.md); SP-028/SP-012 và retention/cost policy liên quan [Minh SP-024](tv1-minh-taskbrief.md#sp-024).

**Thực hiện:** dùng một ledger Room cho mọi provider/tác vụ/kênh. Reserve trước submit/network theo requestId một lần, pin kỳ ngày/tháng và price revision; settle idempotent; chỉ release khi chắc chưa dispatch; mark uncertain khi đã có thể phát sinh phí nhưng thiếu usage. Hủy máy không xác nhận chi phí provider bằng 0. Quyết định giữ aggregate chi phí sau ngắt kênh phải được chốt với owner trước persistence.

- [ ] Hai nhánh cùng tranh phần ngân sách cuối không đều được cấp; reserve và cập nhật tổng trong transaction.
- [ ] Chạm một trong trần ngày/tháng không mở lệnh mới, lưu partial/lý do và yêu cầu hủy Future/HTTP đang chạy khi có thể.
- [ ] Retry/fallback có requestId mới và reserve mới, không reset ngày hoặc đổi provider để vượt trần.
- [ ] Duplicate terminal không quyết toán hai lần; hủy trước dispatch release một lần; sau dispatch thiếu usage giữ uncertain; restart không làm mất reservation.
- [ ] Qua nửa đêm/thay timezone vẫn settle đúng kỳ đã pin, không dồn sang kỳ mới; không biến missing usage thành chi phí 0.
- [ ] Mọi AI consumer của Thiện/Nghị dùng cùng gate; không có đường riêng cho rewrite, image, classification hoặc report.
- [ ] Cơ chế xóa dữ liệu kênh không tự xóa dấu vết ledger cần đối soát hoặc giữ dữ liệu trái policy đã duyệt.

**Kiểm thử/handoff:** Room transaction/concurrency/migration + JUnit cho trần ngày/tháng, race, duplicate settle, release/uncertain, restart và timezone; MockWebServer/cancel ở boundary. Có thể dùng ví dụ giả 100 micros, A/B cùng reserve 60, chỉ một lượt được cấp theo AI contract. Giao reserve/settle/error/cancel semantics và ca consumer-test cho [Thiện](tv3-thien-taskbrief.md) và [Nghị](tv4-nghi-taskbrief.md); fixtures không phải hóa đơn provider thật.

<a id="sp-031"></a>

### SP-031 — Pipeline hai nhánh và đóng gói kết quả

- **Issue:** [#33](https://github.com/nigPeter2503/Social_Pilot/issues/33). **Yêu cầu:** F2.B1/B2. **Ưu tiên:** High.
- **Công sức/mốc:** 6h text stages hạn 21/11; 6h final packaging hạn dự kiến 29/11; tổng 12h.
- **Hai reviewer dự kiến:** Đỗ Phước Thiện (@PhuocThien06), Tô Lãng Nghị (@bob35306). **Nhánh đề xuất:** `feat/sp-031-channel-agent-pipeline`.
- **Nguồn/phụ thuộc:** [Proposal F2/§3.4](../../SocialPilot_Proposal_Java.md), [AI contract §§1/4](../../ai-provider-contract.md); SP-014 đã xong, SP-028/SP-029, [Thiện SP-035](tv3-thien-taskbrief.md#sp-035). SP-032 cung cấp ảnh cho final packaging.

**Thực hiện theo phần:**

1. **Tuần 3:** nhận brief đã validate và selected targets; tạo scope/session và các step typed riêng từng kênh; chạy dàn ý → nháp → rà quy tắc, báo lỗi đúng step, tái sử dụng executor/coordinator tuần 1. Prompt không cho URL/comment tự cấp quyền đăng.
2. **Tuần 4:** mô tả ảnh → nhận ảnh/template SP-032 → đóng gói text/ảnh/metadata cho editor. Chuỗi mỗi nhánh phải đủ sáu bước của proposal; phần thiếu không được đánh dấu complete.
3. Giữ nhánh độc lập, requestId/attempt riêng; không tự retry có phí sau process death, không tự enqueue hoặc approve nội dung.

- [ ] Target tuân contract brief đã bàn giao, tối đa một Facebook và một LinkedIn; không tự thêm kênh không được chọn.
- [ ] Timestamp chứng minh hai nhánh chạy chồng lấp; lỗi A không làm mất kết quả B, kiểm bằng typed snapshot chứ không chỉ icon UI.
- [ ] Stage/output/correlation riêng từng nhánh; text và ảnh không bị lẫn kênh/revision; lỗi ghi đúng bước.
- [ ] Tuần 3 chỉ nhận text-stage output; tuần 4 nhận đủ chain và gói kết quả bất biến cho editor. Không claim đủ F2 trước final image/package.
- [ ] Mọi call reserve trước dispatch; output bị hủy/lỗi giữ partial và usage theo contract, không tự mở lượt mới.

**Kiểm thử/handoff:** fake provider cho step/order/failure/cancel, MockWebServer integration dùng coordinator đã có; fixture brief VI/EN để kiểm các target. Dùng bộ 20 brief (10 VI/10 EN) theo [Proposal §5.1](../../SocialPilot_Proposal_Java.md) khi đánh giá nội dung; ghi sample chưa chạy thay vì chọn riêng ca đẹp. Thiện nhận package/version update contract; Nghị nhận shared service semantics, không đợi Đạt viết prompt F6/F7.

<a id="sp-032"></a>

### SP-032 — Ảnh theo kênh và template dự phòng

- **Issue:** [#34](https://github.com/nigPeter2503/Social_Pilot/issues/34). **Yêu cầu:** F2.B3, F8.B3/B4. **Ưu tiên:** High. **Công sức/mốc:** 10h tuần 4, hạn dự kiến 29/11; giao sớm trong tuần trước final SP-031/SP-025.
- **Hai reviewer dự kiến:** Đỗ Phước Thiện (@PhuocThien06), Tô Lãng Nghị (@bob35306). **Nhánh đề xuất:** `feat/sp-032-channel-images-templates`.
- **Nguồn/phụ thuộc:** [Proposal F2/F8](../../SocialPilot_Proposal_Java.md), [Room contract §§2/4](../../room-schema-contract.md); SP-028/SP-029 và SP-016 đã có. Image contract cho phép chuẩn bị độc lập với final pipeline.

**Phạm vi:** cấu hình kích thước/phong cách theo kênh từ thông số đã chốt; adapter ảnh khả dụng và template cục bộ có nhãn khi không có API. Không nâng thư viện hoặc tạo thêm hệ thống sinh ảnh ngoài proposal. Lưu image reference trong vùng riêng, thống nhất file ownership/refcount với Thiện; không lưu URL ký tạm hoặc credential vào lịch sử.

- [ ] Hai kênh có ảnh/cấu hình riêng; API ảnh thật và đường template đều có bằng chứng kiểm chứng riêng.
- [ ] Capability model bị chặn/không rõ không bị coi là hỗ trợ ảnh; lỗi provider ảnh không làm mất text đã có.
- [ ] Mọi request ảnh qua budget, chi phí riêng khi không có token; cancellation giữ kết quả/chi phí đúng và dọn file tạm an toàn.
- [ ] Gói output chỉ gồm reference/metadata cần thiết, không URL bí mật; version nhận ảnh phải mất hiệu lực approval cũ theo contract của Thiện.
- [ ] Ảnh nhiều version/kênh tham chiếu không bị xóa nhầm; callback revision cũ không thêm ảnh lại sau disconnect.

**Kiểm thử/handoff:** unit template/layout/metadata, fixture adapter success/failure/cancel, file lifecycle/reference; live image API với giới hạn đã được cấp. [Minh SP-025](tv1-minh-taskbrief.md#sp-025) nhận ảnh/ref/metadata để publish; Thiện nhận image-change semantics. Alt-text nghiệp vụ nâng cao thuộc SP-039 của Thiện, không tự coi hoàn tất trong task ảnh.

<a id="sp-033"></a>

### SP-033 — Bắt đầu checkpoint/recovery production

- **Issue:** [#35](https://github.com/nigPeter2503/Social_Pilot/issues/35). **Yêu cầu:** F2.B4/B5. **Ưu tiên:** High. **Công sức/mốc:** chỉ 2h tuần 4, hạn dự kiến 29/11; tổng ticket 12h, **10h còn lại ngoài bảng tuần 2–4**.
- **Hai reviewer dự kiến:** Ngô Gia Minh (@nigPeter2503), Đỗ Phước Thiện (@PhuocThien06). **Nhánh đề xuất:** `feat/sp-033-production-agent-checkpoints`.
- **Nguồn/phụ thuộc:** [Proposal §3.8/§5.1/F2](../../SocialPilot_Proposal_Java.md), [AI contract §4](../../ai-provider-contract.md), [Room contract](../../room-schema-contract.md); SP-031 và SP-017 đã bàn giao.

**Đầu ra 2h:** xác định và bắt đầu wiring checkpoint/snapshot của runtime đã có vào repository/Room production, chốt schema/correlation/session/channel/step/attempt boundary cùng Thiện. Giao mapping trạng thái khi process chết và danh sách test recovery. Không dựng lại coordinator SP-014 hoặc demo Room SP-017.

- [ ] Có handoff field/state mapping và điểm kiểm revision trước ghi; late callback không được trở thành current output.
- [ ] Recovery từ checkpoint có trạng thái gián đoạn và yêu cầu người dùng chạy lại, không tự dispatch call có phí.
- [ ] Phần mới đã sửa có regression test phù hợp, nhưng các ca chưa thực thi vẫn ghi NOT_RUN.
- [ ] Ticket và F2.B4/B5 vẫn partial sau 2h; không báo đã hoàn tất xoay màn hình/process death/independent retry production.

**Kiểm thử trong phần 2h:** walk-through mapping runtime → checkpoint với Thiện; kiểm correlation/revision bằng fake provider và regression tại điểm ghi đã thay đổi. Chưa nối đủ đường Room/UI thì ghi rõ chưa chạy ca restart/process death production, không lấy evidence prototype SP-017 thay thế.

**Điều kiện hoàn tất toàn ticket về sau:** xoay không gọi mới, giữ đúng thứ tự text, hủy Future+HTTP, bỏ callback muộn, retry riêng nhánh giữ kết quả nhánh kia, restart giữ partial/ledger và không tính phí lặp. Lưu rõ 10h chưa phân bổ để Minh theo dõi, không lấp bằng giờ review hoặc Week 6 mặc định.

## 4. Điều phối schema và hợp đồng xuyên module

Nguồn: [CONTRIBUTING §6](../../../CONTRIBUTING.md), [Room contract §§3–7](../../room-schema-contract.md). **Allowance 2h/tuần** dùng cho cấp lượt/schema revision và review tích hợp, không phải 2h để tự viết mọi migration của nhóm.

| Bên bàn giao | Nội dung Đạt cần chốt | Điều kiện trước khi merge |
|---|---|---|
| [Minh](tv1-minh-taskbrief.md) | Channel/credential refs, publish unique key, revision và retention | Root/FK/index, policy disconnect, thứ tự schema có owner |
| [Thiện](tv3-thien-taskbrief.md) | Draft v1 → brief/link/version/approval, ảnh dùng chung | Giữ ID/topic/null/timestamp và đường đọc draft cũ; approval cùng scope |
| [Nghị](tv4-nghi-taskbrief.md) | Comment/cursor/snapshot/sample, period key | Unique key giữ provider ID/URN, null/provenance và ownership theo kênh |

- [ ] Một task giữ AppDatabase/version/migration chung tại một thời điểm; module khác chuẩn bị entity/DAO/test theo contract, không cùng tự tăng version.
- [ ] Mỗi PR có schema export, test đường nâng từ mọi version đang hỗ trợ, fresh install và FK/unique/rollback; cấm destructive fallback.
- [ ] Dữ liệu thiếu/timezone/requestId pin theo contract; aggregate cost sau disconnect chưa được chốt thì chưa đưa vào persistence như policy đã duyệt.
- [ ] Caller mới không tự đổi AiProvider/DTO/lỗi/cancel semantics; ghi bên cung cấp/tiêu thụ và cập nhật contract/test cùng thay đổi.

## 5. Review, kiểm chứng và handoff

[SP-055 / #57](https://github.com/nigPeter2503/Social_Pilot/issues/57): Đạt tham gia 2h/tuần gồm checkpoint, chuẩn bị evidence và demo; Minh tổng hợp. Bàn giao phần tuần 2 ngày 16/11, phần tuần 3 ngày 21/11, tuần 4 dự kiến 29/11. Review allowance **2/2/2h**, ưu tiên security/queue của Minh và schema consumer; reviewer pairs lấy từ issue, thay đổi roster cần ghi lại, không tự hạ hai approval. Không tự gửi Slack hoặc thông báo thành viên trong công việc soạn tài liệu.

Khi triển khai Android, chạy tại `android/`: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`; Keystore/Room/Worker/UI thêm instrumentation hoặc thiết bị/emulator API 23/36 phù hợp. Nếu đổi contract backend, tại `backend/` thêm `.\gradlew.bat test bootJar`. Chỉ docs: link/diff và `git diff --check`. Các lệnh là gate tương lai; soạn brief không tạo evidence app mới.

Mỗi output gửi issue/branch/commit/PR, chữ ký contract/schema revision, dữ liệu thật/mô phỏng có nhãn, test command/môi trường/ngày/kết quả, mục chưa thử và giờ còn lại. Live call chỉ sau khi có quyền thử cụ thể; báo lỗi trước khi xin lượt thêm, không retry/fallback ngoài trần. Không commit key/token. Nhánh task riêng từ main mới nhất, mỗi issue một PR; phần muốn merge độc lập phải có child ticket với ID đã tra cứu. Done sau hai approval thật khác tác giả, checks, xử lý feedback, squash merge và cập nhật evidence/Project.

**Rủi ro còn giữ:** SP-029 chặn production consumers, ledger/schema nhiều owner dùng chung, giá/model/quota và entitlement chưa đủ cho live checks, SP-032 đến muộn làm trễ publish, giờ review ít. Ghi bên đang chờ và phần fixture độc lập có thể tiếp tục. SP-030 fallback 8h, SP-034 log 8h và 10h còn lại SP-033 chưa nằm trong bảng tuần 2–4; không tự coi log/template/fake ledger là đã đáp ứng toàn bộ F2/F8.

**Điểm phối hợp:** [Minh — auth/queue](tv1-minh-taskbrief.md), [Thiện — editor/UI](tv3-thien-taskbrief.md), [Nghị — sync/analytics/AI consumer](tv4-nghi-taskbrief.md). Nguồn: [requirements](../../requirements.md), [architecture](../../architecture.md), [Proposal §4.1](../../SocialPilot_Proposal_Java.md).
