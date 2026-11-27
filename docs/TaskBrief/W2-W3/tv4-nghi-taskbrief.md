# Taskbrief cá nhân tuần 2–3 — Tô Lãng Nghị (TV4)

> Brief lịch sử của giai đoạn này. Mốc hoàn thành nội bộ hiện hành là **03/12/2026**. Xem [lịch tích hợp, nghiệm thu và bàn giao](../W4-W5/week-4-5-taskbrief.md).

**Owner:** Tô Lãng Nghị — GitHub **@bob35306**. Soạn ngày **15/11/2026**, Asia/Bangkok, theo [taskbrief chung đã được người dùng duyệt](week-2-3-taskbrief.md), baseline lịch sử `7134bde`, đã đối soát feedback ngày 15/11/2026. Cùng gói tài liệu [SP-062 / #78](https://github.com/nigPeter2503/Social_Pilot/issues/78), [PR #79](https://github.com/nigPeter2503/Social_Pilot/pull/79). [Project #5](https://github.com/users/nigPeter2503/projects/5).

## 1. Trách nhiệm và đầu vào đã hoàn tất

Nghị sở hữu F6, API comment/reply/metrics/follower của cả hai nền tảng và tầng dữ liệu F7: snapshot, công thức, repository, goal/progress và báo cáo AI F7.N2 cùng UI báo cáo. Thiện làm XML/ViewModel dashboard/biểu đồ/phân nhóm/mục tiêu, không tính lại số liệu. Minh cung cấp OAuth/token/capability; Đạt cung cấp AI service/budget và điều phối schema, không làm thay adapter/dữ liệu của Nghị.

Ước lượng ngày 15/11 giả định tuần 1 hoàn tất. Từ 20/11, dùng Facebook Page và Instagram Professional; Minh và Nghị cần đối chiếu lại phần công tích hợp Instagram. Dùng bằng chứng Page từ SP-018, kết quả Instagram ngày 20/11, contract SP-020 và root/ID SP-012. Kiểm tra các quyền còn thiếu trên đúng tài khoản. **SP-021 đã hoàn tất**, 2h tham chiếu trong baseline không phải task mới. Mức quyền vẫn áp dụng đúng account/product/scope/revision đã kiểm chứng, không suy mọi API từ OAuth.

Các issue triển khai đọc ngày 15/11 còn chưa có assignee. Brief giao Nghị theo phân công đã duyệt, không tự thay assignment/status trên GitHub; nhãn In Progress trong report là kịch bản báo cáo, không phải evidence nhận việc. Phê duyệt tài liệu không tự biến fixture thành nghiệm thu API/app.

**Mốc:** phần tuần 2 hạn **thứ Hai, 16/11/2026**; phần tuần 3 **21/11**; tuần 4 **23–29/11**, hạn dự kiến **29/11**. Hạn 16/11 áp dụng phần 6h/10h tuần 2, không phải đóng toàn bộ issue sync/snapshot nhiều giai đoạn. Giờ của cả tuần không phải capacity còn mới tính từ hôm nay.

**Đối soát hiện tại:** giả định W1 hoàn tất chỉ dùng tính effort. PR #69 đã merge; #70 và #73–#77 còn mở tại lần kiểm tra 15/11. Dùng [brief chung §4–5](week-2-3-taskbrief.md) làm nguồn thống nhất cho tải review đã sửa, thứ tự handoff và bằng chứng live từng operation; không lấy giả định thay nghiệm thu.

## 2. Công sức và thứ tự ưu tiên

| Tuần | Công việc owner | SP-055 | Review | Tổng còn giao | SP-021 đã xong, đối chiếu | Baseline chung |
|---|---|---:|---:|---:|---:|---:|
| 2 | SP-045 10h + SP-041 6h = **16h** | 2h | 4h | **22h** | 2h | 24h |
| 3 | SP-045 2h + SP-041 6h + SP-046 12h = **20h** | 2h | **2.25h** | **24.25h** | 0h | 24h |
| 4 — dự kiến | SP-042 8h + SP-043 12h = **20h** | 2h | **3.75h** | **25.75h** | 0h | 24h |

Giờ owner đã gồm API/domain/UI của module, lưu offline, VI/EN, test/migration và docs. Review/phối hợp tính riêng, không cộng 8h team SP-055 vào riêng Nghị. Nếu API/quota/dữ liệu thiếu hoặc review vượt allowance, báo Minh phần còn lại và deadline bị ảnh hưởng; không dùng mock để đóng khoảng thiếu.

1. Nhận connection/capability/token-reference boundary từ [Minh SP-022](tv1-minh-taskbrief.md#sp-022), xin lượt schema từ [Đạt](tv2-dat-taskbrief.md). Bàn giao chữ ký/fixture có nhãn cho Thiện sớm.
2. Ưu tiên SP-045 để lịch sử thật bắt đầu ngay khi quyền cho phép; song song chuẩn bị SP-041 theo contract đã chốt, tránh tranh AppDatabase/version.
3. Tuần 3 kiểm chứng SP-045 đủ đầu vào rồi thực hiện SP-046. Hoàn thiện sync SP-041 trước inbox tuần 4. Thiện có thể dựng SP-047 fixture theo SP-020 trước, nhưng final UI phải chờ repository/dữ liệu thật.
4. Tuần 4 hoàn thiện inbox SP-042 trước classification/manual reply SP-043; mọi AI đi qua ledger [Đạt SP-029](tv2-dat-taskbrief.md#sp-029). Tự trả lời/notification không nằm trong phần giao này.

## 3. Task triển khai chi tiết

<a id="sp-045"></a>

### SP-045 — Thu snapshot thật từ tuần 2–3

- **Issue:** [#47](https://github.com/nigPeter2503/Social_Pilot/issues/47). **Yêu cầu:** F7.B1/B2/B3. **Ưu tiên:** High.
- **Công sức/mốc:** 10h collection/storage và bắt đầu lịch sử hạn 16/11; 2h đối chiếu nhiều mốc/recovery hạn 21/11; tổng 12h.
- **Hai reviewer dự kiến:** Nguyễn Đức Đạt (@dat532006), Đỗ Phước Thiện (@PhuocThien06), theo CONTRIBUTING §6 vì PR có migration và Thiện dùng dữ liệu dashboard. Cặp này thay Minh–Thiện của issue #47 trong kế hoạch routing; tracker chưa được đổi bởi brief. **Nhánh đề xuất, chưa tạo:** `feat/sp-045-real-metric-snapshots`.
- **Nguồn/phụ thuộc:** [Proposal F7/§5.1](../../SocialPilot_Proposal_Java.md), [analytics contract §§3/5/8/9](../../analytics-data-contract.md), [Room contract §§3/5/6](../../room-schema-contract.md); SP-022 cung cấp kết nối. Dùng SP-018 cho Page, kết quả Explorer ngày 20/11 cho Instagram và SP-020 cho hợp đồng.

**Handoff có thứ tự:** trước lần thu 16/11, Đạt/SP-027 giao secret boundary → Minh/SP-022 upsert Channel và giao lookup/revision → Nghị/SP-041 upsert PublishedPost của bài có sẵn, độc lập PublishJob/SP-025 → Nghị/SP-045 dùng chung root để lưu metric bài. Đạt cấp slot schema nối tiếp, owner từng module viết migration/test. Follower được thu sau Channel/quyền hợp lệ, không cần PublishedPost; post-level/comment phải chờ root bài. Thiếu root/quyền thì tiếp tục parser/fixture có nhãn và ghi mốc thật bị lỡ.

**Đầu vào phải nhận:** channel/account type/revision và quyền từng operation; post/native IDs, provider API version, metric name/definition/unit/measurement kind, timestamp basis và lý do trường thiếu. Nhận token qua secret boundary đã bàn giao, không sao chép credential vào fixture hoặc report.

**Thực hiện:**

1. Chốt root/foreign keys, collection/snapshot/sample key và lượt migration cùng Đạt. Giữ provider ID/URN nguyên dạng; lưu value nullable cùng reason, nguồn, version/definition, observedAt/collectedAt và scope.
2. Thu follower thật của kênh và các metric bài được phép; không dùng connections thay follower, không tự thay views bằng impression hoặc gộp metric khác định nghĩa.
3. Lưu từng lần đo thật, dedup retry cùng collection ID; lần refresh mới là collection mới. Worker hữu hạn theo điều kiện thiết bị, hỗ trợ refresh thủ công; lịch một lần/ngày không cam kết đúng phút.
4. Ghi attempt/error/gap và giữ last success nếu lượt đọc lỗi. Chặn write theo revision tại transaction, hook disconnect dừng job/dọn dữ liệu riêng kênh theo policy.

**Đầu ra/tiêu chí đạt:**

- [ ] **16/11:** collection/storage và lịch thu đầu tiên có evidence đúng thời điểm thực; metric thiếu giữ null/reason; phần chưa có quyền được ghi rõ riêng.
- [ ] **17/11:** giao schema/query/metric-definition/DTO và fixture có nhãn cho SP-046/Thiện; không đợi nghiệm thu SP-045 mới cho repository phát triển.
- [ ] **19/11 mục tiêu:** có mẫu thật thứ hai tương thích, đối chiếu cuối trước 21/11. Trễ mốc này báo Minh/Thiện ngay cùng forecast SP-046 và SP-047; không cam kết còn 12h sau cutoff.
- [ ] **21/11:** có ít nhất hai thời điểm đo thật đủ điều kiện so sánh theo contract, kèm source/definition/scope; nếu chưa đủ không tự thêm mốc lịch sử.
- [ ] 0 hợp lệ khác provider omitted/permission/unsupported/not synced; một operation có quyền không chứng minh mọi metric con có dữ liệu.
- [ ] Không ghép reaction hôm nay với impression hôm qua chỉ vì cùng collection label; measurement window/alignment chưa chốt thì không tính ER.
- [ ] Missed schedule giữ gap/partial; observed time là thời điểm provider đo hoặc collection có basis rõ, không đổi thành mốc 00:00 để đủ tuần.
- [ ] Retry/restart không nhân bản snapshot, không ghi đè mẫu tốt bằng lỗi mới; scope A không lưu dưới kênh B.
- [ ] Xóa/ngắt kênh giữ kênh khác và dữ liệu độc lập; callback revision cũ không khôi phục mẫu sau cleanup.

**Kiểm thử/handoff:** unit mapping null/0/provenance; Room unique/FK/transaction/migration và restart; Worker constraints/retry/cancel; provider response thật đối chiếu từng metric. Test lỗi/429/permission bằng fixture có nhãn khi không tái hiện thật. Thiện nhận sample/DTO với timestamps/coverage; SP-046 nhận lịch sử đủ định nghĩa, không nhận ảnh biểu đồ làm nguồn dữ liệu. Nếu hôm nay mới bắt đầu, chỉ ghi các mốc thực đã thu từ hôm nay, không dựng lại ngày trước.

<a id="sp-041"></a>

### SP-041 — Adapter comment và đồng bộ tăng dần

- **Issue:** [#43](https://github.com/nigPeter2503/Social_Pilot/issues/43). **Yêu cầu:** F6.B2. **Ưu tiên:** High.
- **Công sức/mốc:** 6h adapter/persistence hạn 16/11; 6h background/error/disconnect integration hạn 21/11; tổng 12h.
- **Hai reviewer dự kiến:** Ngô Gia Minh (@nigPeter2503), Nguyễn Đức Đạt (@dat532006). **Nhánh đề xuất:** `feat/sp-041-incremental-comment-sync`.
- **Nguồn/phụ thuộc:** [Proposal F6](../../SocialPilot_Proposal_Java.md), [analytics contract §§3–4/8](../../analytics-data-contract.md), [Room contract](../../room-schema-contract.md); SP-022 cung cấp kết nối. Dùng SP-018 cho Page, kết quả Explorer ngày 20/11 cho Instagram và SP-020 cho hợp đồng.

**Thực hiện theo phần:**

1. Trước lưu comment/snapshot cấp bài 16/11, Nghị/SP-041 upsert root PublishedPost cho bài provider có sẵn dưới Channel do Minh/SP-022 bàn giao; dùng `(channelId, externalPostId)` để không nhân bản khi SP-025 đăng/đối soát sau này. Đạt cấp slot migration nối tiếp, không chờ PublishJob. Tuần 2 dựng adapter production theo endpoint/quyền đã xác minh, không làm lại spike; normalize comment/post/parent/scope/revision và lỗi. Comment key `(platform, channelId, commentId)`; cùng key khác post là conflict cần đối soát, không âm thầm chuyển thread.
2. Lưu upsert trang + cursor/query fingerprint/history range nguyên tử. Cursor opaque gắn scope/post/filter/revision; không lưu/log URL phân trang chứa token. Parent chưa sync khác comment root; message omitted/deleted khác chuỗi rỗng.
3. Tuần 3 nối kéo refresh và Worker hữu hạn, backoff/rate limit, lastAttempt/lastCompleteSync và hủy theo kết nối. Chỉ kết thúc chuỗi trang được xác nhận mới cập nhật lastCompleteSync.

- [ ] Trang chồng lặp/sync lại không nhân bản; lỗi trang sau giữ các trang đã lưu và cursor hợp lệ, không báo đã tải đủ.
- [ ] hasMore/nextCursor/unknown tuân contract; cursor hết hạn chỉ khởi động lại phạm vi có dedup, không xóa sạch cache hoặc coi là hết dữ liệu.
- [ ] Response cũ/khác revision không ghi đè mới; payload cùng timestamp nhưng xung đột được giữ/phản ánh nhu cầu refresh theo contract.
- [ ] Empty success khác thiếu quyền hoặc lỗi mạng; không trả danh sách rỗng thành công để che lỗi.
- [ ] User thấy history range và lần sync thành công; offline giữ cache/freshness, không hứa toàn bộ comment lịch sử hoặc cập nhật tức thời.
- [ ] Hai nền tảng dùng production API và đúng capability, hoặc ghi blocker cụ thể cho operation chưa được quyền; không lấy Facebook thành công thay Instagram.
- [ ] Hủy HTTP/Worker và revision guard ngăn late callback ghi lại comment sau ngắt kênh; không ảnh hưởng kênh khác.

**Kiểm thử/handoff:** fixture parser cho parent unknown, deleted/omitted content, native ID conflict; MockWebServer pagination/overlap/out-of-order, HTTP lỗi/429/timeout/cancel; Room migration và transaction rollback; Worker offline/restart. Đối chiếu comment thật đúng post/thread khi có quyền. SP-042 nhận cache query/filter/state; Minh nhận capability/error mapping cập nhật đúng revision, Đạt nhận đề xuất schema trước khi đổi version.

<a id="sp-046"></a>

### SP-046 — Công thức và repository analytics

- **Issue:** [#48](https://github.com/nigPeter2503/Social_Pilot/issues/48). **Yêu cầu:** F7.B1/B2/B3/N1. **Ưu tiên:** High. **Công sức/mốc:** 12h tuần 3, hạn mục tiêu 21/11; phát triển từ schema/DTO/fixture sớm 17/11, đối chiếu cuối dùng mẫu thật SP-045, không chờ SP-045 hoàn tất mới bắt đầu.
- **Hai reviewer dự kiến:** Nguyễn Đức Đạt (@dat532006), Đỗ Phước Thiện (@PhuocThien06). **Nhánh đề xuất:** `feat/sp-046-analytics-repository`.
- **Nguồn/phụ thuộc:** [analytics contract §§5–8](../../analytics-data-contract.md), [Proposal F7](../../SocialPilot_Proposal_Java.md); SP-045/SP-020. [Fixture](../../contracts/analytics-examples.json) là MO_PHONG, không phải lịch sử production.

**Nếu mẫu thật trễ:** không hạ tiêu chí hai mốc thật. Tiếp tục unit/query/DTO bằng fixture có nhãn; ghi phần reconciliation chưa đạt ở 21/11 và tác động final binding SP-047 tuần 4. Giờ phát triển và đối chiếu phải nằm trong 12h hoặc báo phần vượt, không ép lịch bằng cách gọi fixture là live.

**Phạm vi/thực hiện:** triển khai công thức ngoài main thread và typed repository output cho post metrics, follower points, weekly engagement, top posts, grouped queries theo chủ đề/định dạng/giờ đăng và truy vấn bài hiệu quả cho F3.N4 về sau. Chốt DTO/query/cancel/error/freshness với Thiện; Java signature cụ thể là handoff phải xác nhận, không khẳng định schema tài liệu đã là API code.

| Ca bắt buộc | Kết quả mong đợi theo contract |
|---|---|
| Reaction 12 + comment 3 + share 5, impression 400, cùng scope/window | E = 20, ER = 5% |
| E = 0, impression 400 | ER = 0% hợp lệ |
| Impression = 0 hoặc thiếu share | null với ZERO_DENOMINATOR hoặc MISSING_COMPONENT, không 0% |
| Counter 100 → 95 tại hai mốc tương thích | Delta = -5, không ép thành 0 |
| Một snapshot hoặc hai mốc đảo thời gian | Không tính delta; missing baseline hoặc invalid time order |
| Số đo/định nghĩa/source/window khác nhau | Không gộp như tương thích; giữ lý do loại trừ |

- [ ] Tuần thứ Hai–Chủ nhật theo zone cấu hình, khoảng nửa mở và actualStart/end giữ nguyên thời điểm thật. Thay zone không sửa timestamp cũ.
- [ ] Delta chỉ giữa hai giá trị đủ dữ liệu và cùng scope/entity/definition/unit/kind/source/window; không trừ hai số PERIOD khác kỳ như cumulative.
- [ ] Tổng tuần dùng cohort bài cố định và cặp mốc chung hợp lệ; thiếu baseline bài mới không tự lấy 0. Subtotal partial có included/total/excluded reason, không gọi tổng kênh đầy đủ.
- [ ] Top theo metric/kỳ do query chọn, sort/tie-break ổn định; null bị loại có lý do, không xếp như 0. Group ER dùng tổng E/tổng impression tương thích, không trung bình phần trăm.
- [ ] Query nhóm dùng metadata đã biết; topic/format/publishedAt thiếu vào UNKNOWN, không suy từ nội dung. Không cộng chồng multi-label mà không ghi overlap.
- [ ] Repository trả số + definition/source snapshot IDs + requested/actual period + coverage + freshness/error; Thiện chỉ format/render, không tính lại.
- [ ] Đối chiếu cả fixture tính tay và dữ liệu thật; cache vẫn đọc offline và tách lastSuccess với lỗi lần refresh gần nhất.

**Kiểm thử/handoff:** JUnit công thức với ca bảng trên, timezone/boundary/partial/cohort/tie; DAO/query/freshness tests và kiểm migration khi thêm bảng/index. Bàn giao [Thiện SP-047](tv3-thien-taskbrief.md#sp-047) input query/DTO thật/fixture rỗng/permission/gap, expected numbers và source IDs. Goal persistence/progress SP-048 và báo cáo AI SP-050 chưa được tính hoàn tất ở đây.

<a id="sp-042"></a>

### SP-042 — Inbox hợp nhất, bộ lọc và offline

- **Issue:** [#44](https://github.com/nigPeter2503/Social_Pilot/issues/44). **Yêu cầu:** F6.B1, F9.B2. **Ưu tiên:** High. **Công sức/mốc:** 8h tuần 4, hạn dự kiến 29/11, trước SP-043.
- **Hai reviewer dự kiến:** Ngô Gia Minh (@nigPeter2503), Đỗ Phước Thiện (@PhuocThien06). **Nhánh đề xuất:** `feat/sp-042-unified-comment-inbox`.
- **Nguồn/phụ thuộc:** [Proposal F6/F9](../../SocialPilot_Proposal_Java.md), [analytics contract §4](../../analytics-data-contract.md); SP-041, [Thiện SP-051](tv3-thien-taskbrief.md#sp-051) cung cấp language/offline convention.

**Thực hiện:** XML/ViewModel của inbox quan sát repository cache, filter theo channel/post/handled state; refresh đi qua sync coordinator. Nghị sở hữu cả UI này, không giao mặc định cho Thiện. Hiển thị source/channel/thread và thời điểm sync; xử lý comment deleted/omitted và kênh ngắt rõ ràng.

- [ ] Comment thật của hai kênh hiển thị đúng identity/thread; filter không trộn scope hoặc chuyển reply sang post khác.
- [ ] Offline/restart đọc cache và giữ filter/state theo hợp đồng đã chốt; dữ liệu cũ có freshness, không biến lỗi thành empty.
- [ ] Kéo refresh không nhân bản/sync vô hạn; xoay/reattach không tạo call lặp; cancel không làm mất cache.
- [ ] Loading/rỗng/lỗi/permission/offline có VI/EN; không hiện nút reply như có quyền khi capability chưa được xác minh.
- [ ] Fixture có nhãn riêng và không ghi ngược vào production; screenshot bằng fixture không thay bằng chứng inbox thật.

**Kiểm thử/handoff:** ViewModel/filter/cache tests, instrumentation rotation/restart/airplane mode, đối chiếu post/comment IDs thật. Thiện review trải nghiệm/ngôn ngữ, Minh review auth/capability. SP-043 nhận selected comment scope, context bài và draft-reply state; không triển khai thông báo F6.N1 Optional.

<a id="sp-043"></a>

### SP-043 — Phân loại, gợi ý và gửi câu trả lời đã sửa

- **Issue:** [#45](https://github.com/nigPeter2503/Social_Pilot/issues/45). **Yêu cầu:** F6.B3/B4. **Ưu tiên:** High. **Công sức/mốc:** 12h tuần 4, hạn dự kiến 29/11.
- **Hai reviewer dự kiến:** Nguyễn Đức Đạt (@dat532006), Đỗ Phước Thiện (@PhuocThien06). **Nhánh đề xuất:** `feat/sp-043-reviewed-ai-replies`.
- **Nguồn/phụ thuộc:** [Proposal F6/§5.1](../../SocialPilot_Proposal_Java.md), [AI contract](../../ai-provider-contract.md), [analytics contract §4](../../analytics-data-contract.md); SP-041/SP-042 và [Đạt SP-029](tv2-dat-taskbrief.md#sp-029).

**Thực hiện:** Nghị viết prompt, validate output và nghiệp vụ cho năm nhóm câu hỏi/khen/góp ý/tiêu cực/spam; sử dụng context bài/giọng văn để gợi ý reply. Người dùng sửa/xác nhận gửi đúng comment. AI input là dữ liệu không được thay policy hoặc tự cấp quyền gửi. Mọi AI call qua provider/model/error/budget service của Đạt.

- [ ] Bộ **60 comment gán nhãn trước** theo proposal bao phủ đủ năm nhóm cùng trường hợp FAQ/PII/không chắc; lưu expected/actual từng mẫu, số sai và ma trận nhầm lẫn, không chỉ chọn ca đúng.
- [ ] Output thiếu/sai kiểu/không chắc hiển thị rõ và giữ đường sửa tay; không dùng confidence model tự khai làm lý do tự gửi.
- [ ] Reply draft giữ qua lỗi mạng/cancel/thiếu quyền theo policy đã chốt; thay selected comment hoặc revision không nhận callback cũ.
- [ ] Trước gửi kiểm lại kênh/revision, quyền reply, parent/post/comment identity và nội dung người dùng xác nhận; lỗi quyền không thành success.
- [ ] Response thật có reply ID đúng thread được đối chiếu trên provider; double tap/timeout/retry không gửi lặp. Kết quả có thể đã gửi nhưng chưa rõ giữ cần đối soát, không tự POST lại.
- [ ] Token/key/PII nhạy cảm không vào log/evidence không cần thiết; tác vụ AI bị budget từ chối không chuyển sang call ngoài gate.
- [ ] Không bật auto-reply hoặc thêm notification: SP-044 là task sau, mặc định tự trả lời vẫn tắt; F6.N1 Optional không nằm trong lịch này.

**Kiểm thử/handoff:** JUnit prompt/output/routing/correlation; fake AI + MockWebServer budget-denied/cancel/timeout/protocol/error; persistence/duplicate reply guard tests; bộ 60 mẫu và một phép reply thật được chủ kênh duyệt cụ thể trước gửi. Bằng chứng có đúng channel/post/comment/reply identity, nội dung đã che phù hợp, revision/ngày/người thử và mục chưa thử. Không claim F6.B5 đã xong nhờ manual reply.

## 4. Hợp đồng bàn giao và phần dùng chung

| Bên nhận/cung cấp | Input/output Nghị phải chốt | Mốc và điều kiện |
|---|---|---|
| [Minh](tv1-minh-taskbrief.md#sp-022) → Nghị | Connection identity/revision, token-reference access, từng capability và lỗi chuẩn hóa | SP-027 → SP-022 trước lần thu/sync 16/11; Nghị/SP-041 cấp root bài có sẵn; không đưa secret vào issue |
| [Đạt](tv2-dat-taskbrief.md#sp-029) → Nghị | AiRequest/event/model/usage/cancel, budget admission/settle và schema slot | Schema trước sửa database; budget trước production classification/reply |
| Nghị → [Thiện](tv3-thien-taskbrief.md#sp-047) | DTO/query/error/freshness, fixture rỗng/null/partial có nhãn và source IDs thật | Schema/DTO/fixture 17/11; mục tiêu mẫu thật thứ hai 19/11, đối chiếu 21/11; SP-046 live gate trước final UI tuần 4 |
| Nghị → Minh/Đạt | Disconnect hooks cho comment/cursor/snapshot/goal theo scope, late-write guard | Theo [SP-024](tv1-minh-taskbrief.md#sp-024); chốt policy trước persistence giữ dữ liệu sau ngắt |

Nghị tự thiết kế entity/DAO/index/migration/test module, Đạt cấp version/thứ tự tích hợp; một thời điểm chỉ một task sửa AppDatabase/version/chuỗi chung. Theo [Room contract](../../room-schema-contract.md), giữ ID/URN opaque, FK/scope, sample null/reason, nguồn thật/mô phỏng và UTC epoch ms; không destructive fallback hoặc ép thiếu số thành 0. Tên Java signature mới phải được consumer xác nhận, không tự sửa SocialConnector khác contract của Minh.

## 5. Review, bằng chứng và giới hạn

[SP-055 / #57](https://github.com/nigPeter2503/Social_Pilot/issues/57): 2h/tuần cho checkpoint, evidence và demo; Minh tổng hợp. Review provision đã sửa **4/2.25/3.75h**, theo roster ở brief chung: tuần 3 SP-023/029/051 = 2.25h; tuần 4 SP-032/031/026/047/037 = 3.75h. Tổng tải mới 24.25h và 25.75h, vượt 24h lần lượt 0.25h và 1.75h; SP-037/SP-047 có nguy cơ trễ review cuối, cần Minh chốt năng lực/routing trước 16/11. Không lấy phần thiếu từ giờ module hoặc giảm hai approval. Nếu số PR/re-review vượt giờ, ghi gap để điều phối; AI review không thay hai human approvals. Không tự gửi Slack/nhắn người khác trong lượt soạn tài liệu.

Khi triển khai Android, tại `android/` chạy `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`; UI/Room/Worker thêm emulator/thiết bị phù hợp API 23/36, test migration từ các version hỗ trợ, fresh install và transaction/FK/unique. Nếu đổi backend contract/cấu hình thì tại `backend/` thêm `.\gradlew.bat test bootJar`. Chỉ docs: link/nội dung và `git diff --check`. Đây là gate tương lai, không phải test mới đã chạy khi viết brief.

Mỗi handoff có issue/branch/commit/PR, schema/contract revision, input/query/source IDs, ngày/thiết bị/môi trường, lệnh và expected/actual, giới hạn/mock/live rõ ràng, người nhận và giờ còn lại. Không commit credential hoặc log nhạy cảm. Mỗi task nhánh/PR riêng từ main mới nhất; nếu phần cần merge độc lập thì tạo child ticket/ID đã kiểm tra. Done sau đủ hai thành viên thật khác tác giả approve, checks đạt, feedback bắt buộc xử lý, squash merge và cập nhật evidence/Project theo [CONTRIBUTING](../../../CONTRIBUTING.md).

**Rủi ro:** scope API/capability thay đổi, snapshot bắt đầu muộn làm thiếu lịch sử, quota/rate limit, schema tranh lượt, budget gate chưa có, số liệu hai kênh không so sánh được. Báo operation/metric bị ảnh hưởng, owner đang chờ, phần độc lập tiếp tục và mốc cần quyết định. Thiếu dữ liệu giữ gap/partial; không hạ yêu cầu bắt buộc hoặc lấy fixture thay nghiệm thu.

**Giữ ngoài bảng tuần 2–4:** SP-044 auto-reply 12h, SP-048 goal persistence/progress 8h, SP-050 báo cáo AI và UI báo cáo 10h; công việc tổng hợp ma trận/video cuối kỳ. Thiện giữ UI mục tiêu/phân nhóm SP-049; Nghị vẫn là owner nghiệp vụ/data và F7.N2. Không tự nhận thêm ba task vào tuần 4 đã kín hoặc dùng Week 6 mặc định bù tính năng. Chỉ F3.N2/F6.N1 là Optional.

**Điểm phối hợp:** [Minh — auth/publish](tv1-minh-taskbrief.md), [Đạt — AI/budget/schema](tv2-dat-taskbrief.md), [Thiện — dashboard/language](tv3-thien-taskbrief.md). Nguồn: [requirements](../../requirements.md), [architecture](../../architecture.md), [Proposal §4.1](../../SocialPilot_Proposal_Java.md).
