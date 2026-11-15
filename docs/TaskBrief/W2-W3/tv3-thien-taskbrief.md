# Taskbrief cá nhân tuần 2–3 — Đỗ Phước Thiện (TV3)

**Owner:** Đỗ Phước Thiện — GitHub **@PhuocThien06**. Soạn ngày **15/11/2026**, Asia/Bangkok, theo [taskbrief chung đã được người dùng duyệt](week-2-3-taskbrief.md), baseline lịch sử `7134bde`, đã đối soát feedback ngày 15/11/2026. Cùng gói tài liệu [SP-062 / #78](https://github.com/nigPeter2503/Social_Pilot/issues/78), [PR #79](https://github.com/nigPeter2503/Social_Pilot/pull/79). [Project #5](https://github.com/users/nigPeter2503/projects/5).

## 1. Trách nhiệm và quy tắc đầu vào

Thiện sở hữu F3/F4, điều phối F9 và UI/ViewModel F7.B1/B2/B3/N1/N3; vận hành CI/đóng gói theo proposal. Trong cửa sổ này, tập trung brief form, editor/approval bền vững, khung/dashboard dữ liệu thật, lựa chọn ngôn ngữ/offline và URL/Sharesheet. Nghị sở hữu API, repository, công thức, snapshot/goal và cả UI báo cáo AI F7.N2. Không tính lại analytics trong View hoặc nhận thay mọi màn hình/offline của các thành viên.

Tuần 1 và bảy PR #69/#70/#73/#74/#75/#76/#77 được coi là hoàn tất. Tái sử dụng contract brief/version/approval SP-016 và prototype UI/Room/Worker SP-017; không triển khai lại chúng. **SP-021 đã xong**, 2h tham chiếu trong baseline chung không phải việc mới. Bản report In Progress là kịch bản; các issue triển khai được đọc ngày 15/11 chưa có assignee, tài liệu không tự assign/cập nhật Project. Phê duyệt brief trong chat không phải GitHub human approval hay evidence đã nghiệm thu app.

**Mốc:** phần tuần 2 bàn giao **thứ Hai, 16/11/2026**; phần tuần 3 **21/11**; tuần 4 **23–29/11**, hạn dự kiến **29/11**. Task nhiều giai đoạn chỉ giao phần giờ đã phân bổ ở từng mốc. Không biến mốc 16/11 thành hạn hoàn tất mọi chức năng của tuần 3–4 hoặc coi thời gian từ đầu tuần đã qua là capacity còn mới.

**Đối soát hiện tại:** giả định W1 hoàn tất chỉ dùng tính effort. PR #69 đã merge; #70 và #73–#77 còn mở tại lần kiểm tra 15/11. Dùng [brief chung §4–5](week-2-3-taskbrief.md) làm nguồn thống nhất cho tải review đã sửa, thứ tự handoff và bằng chứng live từng operation; không lấy giả định thay nghiệm thu.

## 2. Phân bổ công sức và ưu tiên

| Tuần | Công việc owner | SP-055 | Review | Tổng còn giao | SP-021 đã xong, đối chiếu | Baseline chung |
|---|---|---:|---:|---:|---:|---:|
| 2 | SP-035 8h + SP-036 8h = **16h** | 2h | 4h | **22h** | 2h | 24h |
| 3 | SP-036 4h + SP-047 6h + SP-051 8h = **18h** | 2h | 4h | **24h** | 0h | 24h |
| 4 — dự kiến | SP-047 6h + SP-037 10h = **16h** | 2h | 6h | **24h** | 0h | 24h |

Giờ owner đã có design/code/test/integration/docs/VI-EN và cache của module. Review/coordination cộng riêng đúng bảng, không tính lần hai. Thời gian CI phát sinh do lỗi module nào thuộc owner đó; Thiện điều phối wiring/build chung và báo gap nếu quá allowance, không dùng toàn giờ review để sửa thay cả nhóm.

1. Ưu tiên SP-035 và phần persistence SP-036 để [Đạt SP-031](tv2-dat-taskbrief.md#sp-031) có brief thật, [Minh SP-025](tv1-minh-taskbrief.md#sp-025) có approved-version contract.
2. Chuẩn bị UI với payload/fixture có nhãn mà không đợi toàn F2. Nối text production qua service/gate của Đạt khi đủ đầu vào; không mở đường gọi provider trực tiếp trong Activity.
3. Tuần 3 kết thúc SP-036 trước tích hợp publish; dựng SP-047 bằng contract/fixture SP-020 và làm SP-051. Khung dashboard chưa phải dashboard dữ liệu thật.
4. Tuần 4 nhận [Nghị SP-046](tv4-nghi-taskbrief.md#sp-046) rồi nối SP-047, kiểm số với Nghị; SP-037 dùng lại form/nguồn/approval đã có. Không tự lấy mock làm history production.

## 3. Task triển khai chi tiết

<a id="sp-035"></a>

### SP-035 — Form topic và brief lưu bền vững

- **Issue:** [#37](https://github.com/nigPeter2503/Social_Pilot/issues/37). **Yêu cầu:** F3.B1, F9.B2. **Ưu tiên:** High. **Công sức/mốc:** 8h tuần 2, hạn 16/11.
- **Hai reviewer dự kiến:** Nguyễn Đức Đạt (@dat532006), Tô Lãng Nghị (@bob35306). **Nhánh đề xuất, chưa tạo:** `feat/sp-035-persistent-brief-form`.
- **Nguồn/phụ thuộc:** [Proposal F3/§5.1](../../SocialPilot_Proposal_Java.md), [Room contract §§1–3/6](../../room-schema-contract.md); SP-012/SP-016 đã có. Nhận channel identity từ Minh, không tự tạo ID theo tên hiển thị.

**Thực hiện:** mở rộng form topic sẵn có với mục tiêu, đối tượng, giọng văn, ngôn ngữ nội dung VI/EN và target channels theo contract. ViewModel quản lý validation/state; repository/Room lưu dữ liệu trên executor. Brief giữ sources và selected targets; ngôn ngữ nội dung không bị nhầm với ngôn ngữ UI.

- [ ] Rỗng/sai được validate trước tạo tác vụ; lỗi nằm đúng field và có VI/EN. Chỉ nhận tổ hợp target hợp lệ theo SP-016, tối đa một target mỗi Facebook/LinkedIn.
- [ ] Lưu/đọc/restart/offline giữ chính xác field/ID/thời gian và lựa chọn kênh; xoay không tạo thêm brief hoặc lệnh AI.
- [ ] Migration bảo toàn draft v1: ID `long`, topic nullable, timestamp và thứ tự ổn định; hai draft cùng topic vẫn là hai dòng.
- [ ] Nếu chuyển dữ liệu v1 sang brief mới, mapping legacy ID unique, nhập lại không nhân bản; không tự gán approval/kênh cho draft cũ chưa có thông tin đó.
- [ ] Chưa có kênh/thiếu quyền/offline có trạng thái rõ; form vẫn cho lưu draft phù hợp, thao tác cần mạng không giả thành thành công.
- [ ] Payload gửi Đạt là snapshot brief đã validate, không lấy từng field đang thay đổi từ View khi callback về.

**Kiểm thử/đầu ra:** JUnit validation/mapping; Room migration từ v1, raw seed theo Room contract (ID 7/11 cùng topic/time, ID 42 topic null, từng xóa ID 50), kiểm ID mới >50 và đường đọc cũ. Instrumentation restart/rotation, VI/EN/offline. Đạt cấp lượt schema trước AppDatabase/version; Thiện tự viết migration/test. Bàn giao brief sample hợp lệ/lỗi và source/target contract cho Đạt, root/version ownership cho Minh.

<a id="sp-036"></a>

### SP-036 — Preview, sửa tay, giới hạn và duyệt theo phiên bản

- **Issue:** [#38](https://github.com/nigPeter2503/Social_Pilot/issues/38). **Yêu cầu:** F4.B1/B2. **Ưu tiên:** High.
- **Công sức/mốc:** 8h editor/persistence hạn 16/11; 4h approval/lifecycle/integration hạn 21/11; tổng 12h.
- **Hai reviewer dự kiến:** Ngô Gia Minh (@nigPeter2503), Nguyễn Đức Đạt (@dat532006). **Nhánh đề xuất:** `feat/sp-036-versioned-editor-approval`.
- **Nguồn/phụ thuộc:** [Proposal F4/§5.1](../../SocialPilot_Proposal_Java.md), [social contract §4](../../social-contract.md), [Room contract §§2–3](../../room-schema-contract.md); SP-035, SP-016 đã có. Text integration qua [Đạt SP-028/SP-029](tv2-dat-taskbrief.md#sp-028), ảnh tuần 4 qua SP-032.

**Thực hiện theo phần:**

1. Tuần 2 tạo preview Facebook/LinkedIn, sửa text và hiển thị giới hạn từ capability/quy tắc đã xác minh; lưu version/current/approval vào repository production, tái sử dụng rule domain SP-016.
2. Duyệt gắn đúng version bất biến của một brief-channel link; sửa text/ảnh tạo version mới chưa duyệt. Không approve toàn brief rồi cho cả hai kênh dùng tùy ý.
3. Thiện sở hữu consumer text-output SP-031 → SP-036: nhận DTO/correlation sớm tuần 3 bằng fixture, áp dụng output thành version mới chưa duyệt qua expected-current guard; tích hợp thật khi Đạt giao packaging tuần 4, không tự lấy text từ View hoặc gọi provider. Giờ wiring/test phải đối soát cùng Minh/Đạt, chưa hứa đủ luồng AI ở 21/11.
4. Tuần 3 hoàn thiện transaction/expected-current guard, boundary và restart; bàn giao resolver approved payload cho Minh. AI callback cũ chỉ được áp dụng nếu `expectedCurrentVersionId` còn khớp; stale callback không ghi đè current/approval mới.

- [ ] Tuần 3 kiểm resolver từ chối bài chưa duyệt/approval cũ bằng unit/fixture; ca admission online/offline thật tích hợp cùng SP-026 tuần 4. Bài chưa duyệt hoặc approval cũ bị vô hiệu không enqueue được cả online/offline; backend/provider input không tự vượt gate duyệt.
- [ ] Preview tách kênh; test dưới/đúng/vượt giới hạn thực, vượt giới hạn chặn gửi. Quy tắc chưa xác minh ghi thiếu đầu vào, không tự bịa số ký tự.
- [ ] Current/approved/parent cùng link; version kênh A không dùng làm approval kênh B. Lưu version mới + mất hiệu lực approval nguyên tử.
- [ ] Sau restart/offline, trạng thái text/ảnh/version/approval đúng; restore là tạo bản mới chưa duyệt theo contract, nhưng lịch sử/restore UI đầy đủ vẫn thuộc SP-040 về sau.
- [ ] Xoay/đổi Fragment không gọi lại AI; hủy/race không ghi đè bản mới; late callback của revision đã ngắt bị bỏ tại điểm ghi.
- [ ] Loading/rỗng/lỗi/thiếu quyền/offline có VI/EN; không bật lại nút đăng khi chỉ có dữ liệu mẫu.
- [ ] Tuần 4 nhận ảnh mới phải mất duyệt, không đòi mở cả UI regenerate/hook/alt-text của SP-039 trong task này.

**Kiểm thử/handoff:** JUnit rule/transaction/race; Room migration và restart; UI boundary/rotation/disconnect; test request từ version cũ trong lúc người dùng sửa. Minh nhận approvedVersionId, immutable text/image refs/metadata, validation errors và cách kiểm current/approval khi enqueue/dispatch. Không claim full F4.B3/N1/N2/N3 chỉ nhờ editor cơ bản.

<a id="sp-047"></a>

### SP-047 — Số liệu bài, tăng trưởng và dashboard

- **Issue:** [#49](https://github.com/nigPeter2503/Social_Pilot/issues/49). **Yêu cầu:** UI/ViewModel F7.B1/B2/B3. **Ưu tiên:** Normal cho khung tuần 3, High cho tích hợp thật tuần 4.
- **Công sức/mốc:** 6h framework/fixture hạn 21/11; 6h bind dữ liệu thật hạn dự kiến 29/11; tổng 12h.
- **Hai reviewer dự kiến:** Nguyễn Đức Đạt (@dat532006), Tô Lãng Nghị (@bob35306). **Nhánh đề xuất:** `feat/sp-047-growth-dashboard-ui`.
- **Nguồn/phụ thuộc:** [Proposal §4.1/§5.1/F7](../../SocialPilot_Proposal_Java.md), [analytics contract §§3/5–7](../../analytics-data-contract.md). SP-020 đủ dựng khung bằng fixture; [Nghị SP-045/SP-046](tv4-nghi-taskbrief.md#sp-046) bắt buộc cho final binding/acceptance.

**Tuần 3 — đầu ra khung:** XML/ViewModel hiển thị card kênh, số liệu từng bài, follower timeline, tương tác tuần, top bài và so sánh kênh theo DTO. Fixture rỗng/thiếu quyền/stale/error/partial có nhãn trên màn hình và test; không ghi fixture vào production history. Giữ query/filter trong state, không gọi sync lại chỉ vì reattach observer.

**Gate dữ liệu:** Nghị bàn giao schema/DTO/fixture sớm 17/11, mục tiêu mẫu thật thứ hai 19/11 và đối chiếu cuối 21/11. SP-046 được phát triển song song trước khi SP-045 đóng. Mẫu thật trễ/không tương thích thì giữ UI fixture có nhãn, ghi SP-047 real-data acceptance bị đe dọa ở 29/11; không tự đổi nguồn hoặc vẽ thêm lịch sử.

**Tuần 4 — đầu ra tích hợp:** bind repository của Nghị, dùng định nghĩa/kỳ/timezone/coverage do repository trả. UI chỉ format số và thời gian, không tính lại ER/delta/tổng tuần hoặc tự ghép metric hai nền tảng. Khả năng N1/N3 có hợp đồng để tiếp tục nhưng UI phân nhóm/mục tiêu đầy đủ thuộc SP-049 về sau.

- [ ] Với ít nhất hai snapshot thật, vị trí/giá trị trục thời gian khớp dữ liệu; thiếu mốc giữ gap, không thêm điểm 0 hay nội suy lịch sử để làm đẹp.
- [ ] Follower không thay bằng connections; trường null/permission/unsupported khác 0, mẫu số 0 không hiển thị 0%.
- [ ] Freshness/time/source/coverage hiển thị đúng từng chỉ số; offline vẫn xem cache kèm lỗi/stale, không che refresh failure bằng trạng thái empty.
- [ ] Tuần không đủ lịch sử ghi khoảng thực tế/partial; không gọi subtotal partial là tổng kênh đầy đủ.
- [ ] Top/so sánh giữ query metric/kỳ/định nghĩa từ Nghị; dữ liệu không tương thích được trình bày riêng.
- [ ] Bộ lọc/lựa chọn giữ qua xoay và restart theo persistence đã thống nhất; VI/EN/error/empty/permission/offline được thử.
- [ ] Nghị đối chiếu số và nguồn trước bàn giao thật; thiếu API/history ghi đúng phần chưa đạt, không dùng ảnh fixture làm evidence final.

**Kiểm thử/handoff:** ViewModel test với DTO rỗng/null/gap/stale/negative delta; UI chart/timezone/filter/lifecycle; so màn hình với snapshot thật và expected của repository. Lỗi công thức trả Nghị cùng query/source IDs; Thiện tự sửa render/ViewModel. Giao screenshot/video VI/EN, input query và source snapshot IDs, không chỉ ảnh dashboard thành công.

<a id="sp-051"></a>

### SP-051 — Ngôn ngữ ứng dụng và trạng thái offline thống nhất

- **Issue:** [#53](https://github.com/nigPeter2503/Social_Pilot/issues/53). **Yêu cầu:** F9.B1/B2. **Ưu tiên:** Normal. **Công sức/mốc:** 8h tuần 3, hạn 21/11.
- **Hai reviewer dự kiến:** Nguyễn Đức Đạt (@dat532006), Tô Lãng Nghị (@bob35306). **Nhánh đề xuất:** `feat/sp-051-language-offline-settings`.
- **Nguồn/phụ thuộc:** [Proposal F9/§4.1](../../SocialPilot_Proposal_Java.md), [architecture](../../architecture.md); SP-035, SP-016 đã có. Nhận trạng thái queue từ Minh, AI từ Đạt, sync/cache từ Nghị.

**Thực hiện:** lưu lựa chọn UI VI/EN bền vững theo stack đã khóa và vùng settings phù hợp; dùng resources `values`/`values-en`, không hardcode strings. Thống nhất cách UI biểu diễn network/offline, lần sync thành công, stale và job chờ; framework không tự làm toàn bộ cache của module khác.

- [ ] Người dùng đổi VI/EN trong app, restart vẫn giữ; không chỉ đổi theo locale thiết bị và không đổi nhầm ngôn ngữ nội dung của brief.
- [ ] Đổi ngôn ngữ/khởi tạo View không làm mất draft/filter hoặc tạo yêu cầu AI/publish mới.
- [ ] **21/11:** offline đọc/sửa/lưu draft và đọc cache đã sync; thao tác cần mạng được báo rõ. Queue state dùng fixture có nhãn nếu SP-026 chưa có, không báo đã thử enqueue thật.
- [ ] **Tuần 4 qua SP-026:** tích hợp queue nhận bài đã duyệt khi offline và khôi phục sau restart; Minh cung cấp admission/job outcome, Thiện nối UI. Giữ ca này trong nghiệm thu liên thông SP-052; SP-051 framework xong không đóng thay ca queue.
- [ ] Không coi toàn bộ offline là cấm mọi thao tác; không tự gửi lại AI/reply/publish khi vừa có mạng mà chưa qua policy tương ứng.
- [ ] Main screens và lỗi của phần đã tích hợp có strings VI/EN; layout/placeholder không vỡ; module owner nhận checklist thiếu chuỗi/cache phần mình.
- [ ] Framework xong không tự chứng minh toàn F9 đã nghiệm thu: các module tuần 4–5 tự nối cache/strings, SP-052 nghiệm thu xuyên luồng về sau.

**Kiểm thử/handoff:** UI đổi ngôn ngữ khi đang có draft/filter rồi restart/rotation; airplane mode/reload, cache cũ/chưa có cache và network trở lại; dùng API 23/36 khi phù hợp. Giao checklist state/resource keys và các scenario cho từng owner; lỗi module có ticket/owner riêng, không dồn mọi fix về Thiện.

<a id="sp-037"></a>

### SP-037 — Nhập URL có nguồn và nhận Android Sharesheet

- **Issue:** [#39](https://github.com/nigPeter2503/Social_Pilot/issues/39). **Yêu cầu:** F3.N1/N3. **Ưu tiên:** Normal. **Công sức/mốc:** 10h tuần 4, hạn dự kiến 29/11.
- **Hai reviewer dự kiến:** Ngô Gia Minh (@nigPeter2503), Tô Lãng Nghị (@bob35306). **Nhánh đề xuất:** `feat/sp-037-url-sharesheet-import`.
- **Nguồn/phụ thuộc:** [Proposal F3/§5.1](../../SocialPilot_Proposal_Java.md), [Room contract brief/source ownership](../../room-schema-contract.md); SP-035. AI nếu dùng qua service/budget của Đạt, không thêm endpoint AI vào backend OAuth.

**Thực hiện:** nhận URL từ form hoặc ACTION_SEND, validate kiểu/MIME/scheme được hỗ trợ; lấy bài công khai có thể truy cập, lưu source/provenance và cho người dùng kiểm/sửa brief. Trang lỗi/bị chặn/login/paywall có lý do và đường nhập tay; không vượt quyền, scrape thay quyền API hoặc bịa nội dung nguồn. Gợi ý RSS/Atom và bài hiệu quả thuộc SP-038, không mở rộng vào đây.

- [ ] ACTION_SEND mở đúng form với URL, intent lặp/rotation không tạo bài hoặc dispatch hai lần; cancel không tạo post/job và không tự publish.
- [ ] URL hợp lệ có source gắn brief/output bài chia sẻ; URL không đọc được không tạo bản tóm tắt tưởng tượng.
- [ ] Nguồn URL là dữ liệu, không đổi rule duyệt/budget hoặc tự cấp quyền đăng; mọi AI call mới qua gate đã bàn giao.
- [ ] Hủy network hoặc đổi source không cho callback cũ ghi đè brief mới; offline vẫn xem nguồn/nội dung đã lưu cùng freshness.
- [ ] Lỗi mạng/HTTP/unsupported content có UI VI/EN; dữ liệu nguồn dùng chung không bị xóa theo một kênh đã ngắt.

**Kiểm thử/handoff:** fixture HTTP/MockWebServer cho article, redirect/error/timeout/content không hỗ trợ; instrumentation ACTION_SEND từ app khác, cancel/rotation/process restart; một URL công khai thật đối chiếu nguồn khi có quyền thử. Giao brief+sources contract cho Đạt và citation metadata cho Minh; không yêu cầu người dùng đăng bài thật chỉ để chứng minh Sharesheet.

## 4. Review, phối hợp và khu vực sửa

- [SP-055 / #57](https://github.com/nigPeter2503/Social_Pilot/issues/57): 2h/tuần cho checkpoint, evidence và demo; Minh tổng hợp. Bàn giao tuần 2 ngày 16/11, cutoff 21/11 và tuần 4 dự kiến 29/11. Chuẩn bị report có tên thật/handle/ticket; không tự gửi Slack/nhắn thành viên khi chưa có ủy quyền gửi.
- Review allowance **4/4/6h** theo brief chung. Ưu tiên consumer contracts của Đạt/Nghị và approval/publish của Minh; các reviewer đã đề xuất trên issue vẫn là nguồn roster. Tăng số PR/re-review cần điều phối tải, không tự báo đủ giờ hoặc thay hai approval bằng AI.
- Khu vực hiện có tính từ Android package `com.socialpilot.app`: `ui/`, `domain/`, `data/local/`, `data/repository/`, `core/AppContainer.java`; XML/resources trong `android/app/src/main/res/`. Tên lớp/màn hình mới là thiết kế task, không khẳng định đã có trên main.
- Thiện phối hợp AppContainer/navigation/manifest/resources/build với owner liên quan. Chốt lượt schema cùng Đạt; mỗi thời điểm một task sửa AppDatabase/version/migration, không giữ toàn bộ file build chung bằng nhánh TV3 dài hạn.

## 5. Kiểm chứng và gói bàn giao

Khi sửa Android, tại `android/` chạy `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`; UI/lifecycle/Room/Worker thêm emulator/thiết bị phù hợp API 23/36. Room migration cần đường nâng từ mọi version đang hỗ trợ, fresh install, FK/unique và dữ liệu v1; không destructive fallback. Nếu đổi contract backend, chạy thêm tại `backend/`: `.\gradlew.bat test bootJar`. Chỉ docs: kiểm link/nội dung và `git diff --check`. Các gate này dành cho triển khai, không phải test đã chạy khi soạn brief.

Handoff phải có issue/branch/commit/PR, schema/contract revision, payload/fixture có nhãn, ảnh/video VI/EN, lệnh test + ngày/môi trường/kết quả, mục chưa thử, người nhận và giờ còn lại. Không ghi key/token hoặc dữ liệu nhạy cảm. Mỗi task một nhánh/PR từ main mới nhất; phần muốn merge độc lập cần child ticket/ID được tra cứu trước. Chỉ Done khi đủ hai human approvals khác tác giả, checks, sửa feedback bắt buộc, squash merge và cập nhật evidence/Project theo [CONTRIBUTING](../../../CONTRIBUTING.md).

**Rủi ro:** approval handoff trễ chặn Minh; schema tranh lượt; DTO của Nghị chưa có dữ liệu thật; pipeline/ảnh của Đạt tới muộn; VI/EN và review bị dồn cuối. Có thể làm fixture có nhãn khi chờ, nhưng final acceptance vẫn cần đường thật. Không tự nhận công thức, API sync hoặc lỗi module khác để lấp khoảng chờ.

**Giữ ngoài bảng tuần 2–4:** SP-038 gợi ý topic 10h; SP-039 rewrite/regen/ba hook/alt text 12h; SP-040 lịch sử/restore 8h; SP-049 UI phân nhóm/mục tiêu 10h; các việc nghiệm thu/đóng gói tuần 6. Đây vẫn là phạm vi cam kết, không tự thêm vào giờ đã kín hoặc hạ thành Optional. Chỉ F3.N2 và F6.N1 là Optional.

**Điểm phối hợp:** [Minh — connection/publish](tv1-minh-taskbrief.md), [Đạt — AI/secret/budget/schema](tv2-dat-taskbrief.md), [Nghị — data/analytics](tv4-nghi-taskbrief.md). Nguồn: [requirements](../../requirements.md), [Proposal §4.1](../../SocialPilot_Proposal_Java.md), [development](../../development.md).
