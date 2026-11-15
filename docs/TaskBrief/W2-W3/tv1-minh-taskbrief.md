# Taskbrief cá nhân tuần 2–3 — Ngô Gia Minh (TV1)

**Owner:** Ngô Gia Minh — GitHub **@nigPeter2503**. Soạn ngày **15/11/2026**, Asia/Bangkok, theo [taskbrief chung đã được người dùng duyệt](week-2-3-taskbrief.md), revision `7134bde`. Gói tài liệu: [SP-063 / #80](https://github.com/nigPeter2503/Social_Pilot/issues/80). [Project #5](https://github.com/users/nigPeter2503/projects/5).

## 1. Phạm vi và cách sử dụng

Minh sở hữu F1/F5, backend OAuth, hợp đồng social và điều phối tích hợp. Mục tiêu cá nhân: chuyển các spike tuần 1 thành kết nối production, đầu vào đăng đã duyệt và hàng đợi bền vững; cung cấp auth/capability cho Nghị, hợp đồng kết quả đăng cho Thiện và ranh giới lưu bí mật cho Đạt.

Tuần 1 và bảy PR #69/#70/#73/#74/#75/#76/#77 được coi là đã xong theo giả định đã thống nhất; không giao lại spike hoặc coi chờ approve là blocker. **SP-021 là đầu vào đã hoàn tất.** Hai giờ SP-021/người trong bảng chung chỉ được giữ để đối chiếu baseline, không phải task mới. Phê duyệt nội dung trong chat không phải hai approval GitHub hoặc bằng chứng nghiệm thu tính năng.

Các issue bên dưới có thật; người chịu trách nhiệm theo brief đã duyệt là Minh. Khi đọc tracker ngày 15/11, các issue triển khai này chưa có assignee; tài liệu không tự cập nhật assignment/status. Nhãn In Progress trong bản report là kịch bản báo cáo, không thay việc nhận task trên Project trước khi sửa code.

**Mốc:** phần bàn giao tuần 2 hạn **thứ Hai, 16/11/2026**; phần tuần 3 hạn **21/11/2026**; tuần 4 **23–29/11** là kế hoạch phía trước, hạn dự kiến **29/11**. Mốc 16/11 chỉ áp dụng phần việc tuần 2 đã phân bổ, không kéo toàn bộ task nhiều giai đoạn về ngày này. Giờ là công sức cho cả khoảng tuần, không phải giờ mới còn trống từ hôm nay.

## 2. Phân bổ công sức

| Tuần | Công việc owner | Phối hợp SP-055 | Review | Tổng còn giao theo kế hoạch | SP-021 đã xong, chỉ đối chiếu | Baseline chung |
|---|---:|---:|---:|---:|---:|---:|
| 2 | SP-022 8h + SP-053 6h + SP-024 2h = **16h** | 2h | 4h | **22h** | 2h | 24h |
| 3 | SP-053 2h + SP-023 6h + SP-025 8h = **16h** | 2h | 4h | **22h** | 0h | 22h |
| 4 — dự kiến | SP-025 4h + SP-026 16h = **20h** | 2h | 2h | **24h** | 0h | 24h |

Ước lượng SP-022 8h và SP-023 6h là phần còn lại đã giảm từ backlog vì tái sử dụng spike; cần cập nhật giờ thực tế trước khi cam kết lịch chi tiết. Review/phối hợp không cộng lần nữa vào giờ task. Không tự tăng giờ hoặc giảm kiểm thử để ép vừa lịch; rủi ro mốc trong brief chung vẫn được giữ.

## 3. Thứ tự thực hiện và bàn giao

1. Nhận contract kho bí mật từ [Đạt — SP-027](tv2-dat-taskbrief.md#sp-027); kiểm tra đầu ra OAuth tuần 1, sau đó hoàn thiện SP-022. Dùng adapter đã có, không viết lại state/handoff engine.
2. Sau kết nối production dùng được, bàn giao scope/revision/capability cho [Nghị — SP-041/SP-045](tv4-nghi-taskbrief.md#sp-041). Thiếu quyền của endpoint nào được ghi riêng; không chặn phần độc lập bằng một trạng thái chung.
3. Trong tuần 2, chốt policy SP-024 với Đạt/Nghị, triển khai phần thiết lập HTTPS SP-053. Không giữ tombstone theo giả định chưa được duyệt.
4. Tuần 3 hoàn thiện SP-053/SP-023; nhận đầu ra approval từ [Thiện — SP-036](tv3-thien-taskbrief.md#sp-036), rồi nối kênh production đầu tiên ở SP-025.
5. Tuần 4 nhận ảnh/metadata của [Đạt — SP-032](tv2-dat-taskbrief.md#sp-032), hoàn thiện SP-025 trước SP-026. Nếu handoff trễ, báo ảnh hưởng cửa sổ 16h queue, không đưa task phụ thuộc lên trước.

## 4. Task triển khai chi tiết

<a id="sp-022"></a>

### SP-022 — OAuth production và vòng đời token

- **Issue:** [#24](https://github.com/nigPeter2503/Social_Pilot/issues/24). **Yêu cầu:** F1.B1, F1.B3. **Ưu tiên:** High. **Công sức/mốc:** 8h tuần 2, bàn giao 16/11.
- **Hai reviewer dự kiến:** Nguyễn Đức Đạt (@dat532006), Tô Lãng Nghị (@bob35306).
- **Nhánh đề xuất, chưa tạo:** `feat/sp-022-oauth-token-lifecycle`. **Nguồn:** [Proposal F1, §3.3 và §5.1/F1](../../SocialPilot_Proposal_Java.md), [social contract §§3/6](../../social-contract.md), [Room contract §4](../../room-schema-contract.md).

**Đầu vào/phụ thuộc:** SP-009/SP-010/SP-011 đã xong theo giả định; SP-027 bàn giao API kho bí mật, namespace và lỗi đọc/ghi. Nhận danh tính, loại tài khoản, token reference, thời hạn thực được provider trả và capability gắn đúng connection revision; không yêu cầu token thật trong repo/chat.

**Thực hiện:**

1. Đối chiếu kết nối Facebook/LinkedIn đã có với kho secret production; thống nhất cách truy xuất reference cùng Đạt. Giữ dữ liệu provider độc lập và dữ liệu cũ khi ghi mới thất bại theo hợp đồng đã bàn giao.
2. Nối trạng thái kết nối, hết hạn/thu hồi và yêu cầu kết nối lại vào repository. Refresh chỉ dùng khi provider/app có cơ chế đã được cấp; giữ bản nháp khi xác thực lỗi.
3. Giữ state, phiên, redirect allow-list, mã bàn giao một lần/TTL và PKCE khi hỗ trợ; bổ sung regression ở chỗ wiring mới ảnh hưởng, không tính lại spike thành task mới.
4. Ghi tài liệu cấu hình/thử nghiệm và bàn giao auth/capability cho consumer. Backend giữ client secret; không chuyển AI key qua backend hoặc lưu social token dài hạn phía server.

**Đầu ra/tiêu chí đạt:**

- [ ] Hai kết nối có đúng identity/account type, secret reference và revision sau restart; không ghép metadata kênh này với credential kênh kia.
- [ ] Hết hạn/thu hồi dẫn tới trạng thái có thể xử lý; bản nháp giữ nguyên. Thiếu entitlement refresh ghi Đạt một phần/Bị chặn ở F1.B3, không gọi kết nối lại là refresh thành công.
- [ ] Sai state/phiên/redirect, handoff hết hạn/dùng lại không tạo kết nối; regression không làm mất các kiểm tra bảo mật tuần 1.
- [ ] Token/key/client secret không xuất hiện trong URL điều hướng, log, Room thường hoặc backup; lỗi không lộ response thô.
- [ ] Consumer nhận ví dụ connected/reauth/permission denied và cách kiểm revision tại điểm ghi dữ liệu.

**Kiểm thử/bằng chứng:** unit/MockMvc cho wiring và state transitions; thiết bị cho Keystore, cold-start callback và reload. Thử provider thật đúng tài khoản với quyền đã xác nhận; tách fixture khỏi API thật. Handoff cho Nghị trước lần thu snapshot/comment đầu, cho Thiện trước SP-023/SP-025.

<a id="sp-053"></a>

### SP-053 — Backend HTTPS và runbook OAuth

- **Issue:** [#55](https://github.com/nigPeter2503/Social_Pilot/issues/55). **Yêu cầu:** INFRA, DOCS. **Ưu tiên:** High theo brief chung.
- **Công sức/mốc:** 6h thiết lập bàn giao 16/11; 2h kiểm chứng/runbook bàn giao 21/11; tổng 8h. Phần đầu chưa đóng toàn issue.
- **Hai reviewer dự kiến:** Nguyễn Đức Đạt (@dat532006), Đỗ Phước Thiện (@PhuocThien06). **Nhánh đề xuất:** `chore/sp-053-https-oauth-deployment`.
- **Nguồn/phụ thuộc:** [Proposal §3.3, §4.4, §5.4](../../SocialPilot_Proposal_Java.md), [development](../../development.md); đầu ra SP-010/SP-011 đã xong.

**Thực hiện và đầu ra:** xác nhận môi trường JVM/HTTPS, biến cấu hình với giá trị mẫu, redirect đã đăng ký, health check và trình tự triển khai/khởi động lại. Đo RAM, startup, sleep/wake từ môi trường thật rồi ghi nguồn thời gian; không bịa gói hosting hay chi phí. Chốt dịch vụ/trần chi với owner trước khi phát sinh phí. Một instance có phiên tạm TTL; nếu cần nhiều instance, ghi yêu cầu kho phiên chung để quyết định trước triển khai, không tự mở rộng kiến trúc.

- [ ] **16/11:** có cấu hình mẫu không secret, địa chỉ HTTPS phục vụ kiểm thử và hồ sơ đo ban đầu; ghi mục chưa xác minh.
- [ ] **21/11:** tái lập được từ runbook; callback mới hoạt động sau restart/wake, phiên cũ hết hiệu lực được báo đúng.
- [ ] Không lưu token dài hạn phía server; restart mất phiên tạm yêu cầu đăng nhập lại, không hồi sinh phiên.
- [ ] Hướng dẫn có khởi động, health, cấu hình redirect, đọc lỗi an toàn và rollback về revision đã biết; không yêu cầu chép secret vào issue.

**Kiểm thử/handoff:** backend `test bootJar` nếu đổi Java/config/build; kiểm tra health + OAuth callback thật trong môi trường triển khai, ghi revision và ngày thử. Thiện nhận cấu hình build app; cả nhóm nhận runbook. Tài liệu thuần túy không dùng kết quả build cũ để khẳng định revision code mới đạt.

<a id="sp-024"></a>

### SP-024 — Hợp đồng ngắt kênh và xóa dữ liệu

- **Issue:** [#26](https://github.com/nigPeter2503/Social_Pilot/issues/26). **Yêu cầu:** F1.B4. **Ưu tiên:** High. **Công sức/mốc:** chỉ 2h chốt contract tuần 2, hạn 16/11; 10h triển khai/tích hợp còn lại ngoài phạm vi tuần 2–4.
- **Hai reviewer dự kiến:** Nguyễn Đức Đạt (@dat532006), Tô Lãng Nghị (@bob35306). **Nhánh đề xuất:** `feat/sp-024-channel-disconnect`.
- **Nguồn/phụ thuộc:** [social contract §7](../../social-contract.md), [Room contract §§2/4/7](../../room-schema-contract.md), SP-022/SP-012/SP-020.

**Phần 2h phải giao:** ma trận dữ liệu/job/ảnh/log theo owner; thứ tự vô hiệu revision → chặn tác vụ mới → hủy → dọn → bỏ callback cũ; điều kiện xóa secret/file ngoài transaction; ví dụ ngắt A giữ kênh B và brief/ảnh dùng chung. Chốt với Đạt/Nghị việc giữ hay không giữ tombstone và aggregate chi phí. Mốc 30 ngày trong social contract là đề xuất, không phải lựa chọn đã được duyệt.

- [ ] Contract chỉ rõ owner từng hook, đầu vào channel/revision, kết quả, lỗi và cách retry cleanup sau process death.
- [ ] Xác nhận người dùng thấy rõ dữ liệu bị ảnh hưởng; xóa liên kết cục bộ không xóa bài trên provider.
- [ ] Quyết định retention có người xác nhận/nguồn; chưa có quyết định thì ghi blocker cụ thể trước SP-026, không tự chọn policy.
- [ ] Nêu rõ cần kiểm revision và ghi/xóa trong cùng transaction; kiểm tra trước HTTP đơn lẻ không đủ chặn race.
- [ ] Không báo F1.B4 đạt hoặc đóng task chỉ nhờ bản contract; integration toàn luồng còn SP-024/SP-052.

**Kiểm thử và handoff:** [Đạt](tv2-dat-taskbrief.md#sp-029) nhận quy tắc hủy/usage; [Thiện](tv3-thien-taskbrief.md#sp-036) nhận ownership brief/version/ảnh; [Nghị](tv4-nghi-taskbrief.md#sp-041) nhận cursor/comment/snapshot cleanup. Walk-through ma trận callback đến trước/sau invalidation, reconnect revision mới và xóa một kênh có dữ liệu dùng chung; ghi expected result và owner chạy mỗi ca khi tích hợp. Phần 2h chỉ review contract/ca thử; các phép thực thi chưa chạy phải ghi chưa chạy.

<a id="sp-023"></a>

### SP-023 — Màn hình Kênh của tôi

- **Issue:** [#25](https://github.com/nigPeter2503/Social_Pilot/issues/25). **Yêu cầu:** F1.B2. **Ưu tiên:** High. **Công sức/mốc:** 6h tuần 3, hạn 21/11.
- **Hai reviewer dự kiến:** Đỗ Phước Thiện (@PhuocThien06), Tô Lãng Nghị (@bob35306). **Nhánh đề xuất:** `feat/sp-023-my-channels`.
- **Nguồn/phụ thuộc:** [Proposal F1/§5.1](../../SocialPilot_Proposal_Java.md), [social contract §3](../../social-contract.md); SP-022.

**Thực hiện:** nối XML/ViewModel tới repository; hiển thị identity, Page/person/organization, trạng thái kết nối và từng operation/capability từ dữ liệu đã xác minh. Giữ phân biệt AVAILABLE/PERMISSION_REQUIRED/UNSUPPORTED/UNVERIFIED; không suy quyền comment/metrics từ OAuth hoặc đăng thành công. Không tính năng ngoài contract như mention trở thành AVAILABLE khi chưa có bằng chứng; phần chưa có operation được ghi thiếu/chờ xác nhận.

- [ ] Restart/offline vẫn xem được metadata đã lưu và thời điểm xác minh; không hiển thị credential.
- [ ] Trạng thái hết hạn/thiếu quyền có thông báo VI/EN và thao tác xử lý phù hợp.
- [ ] Capability áp dụng đúng account/kênh/revision; thu hồi quyền ở request sau được xử lý thay vì tin snapshot vĩnh viễn.
- [ ] Loading/rỗng/lỗi/không có mạng có ca UI; xoay màn hình không tự mở OAuth hoặc gọi trùng.

**Kiểm thử/handoff:** test mapping trạng thái; UI thiết bị/emulator, screenshot VI/EN và offline; Nghị đối chiếu capability, Thiện đối chiếu navigation. Không đưa toàn bộ disconnect implementation còn thiếu vào issue này.

<a id="sp-025"></a>

### SP-025 — Đăng bản đã duyệt lên hai kênh

- **Issue:** [#27](https://github.com/nigPeter2503/Social_Pilot/issues/27). **Yêu cầu:** F5.B1, F5.B5. **Ưu tiên:** High.
- **Công sức/mốc:** 8h kênh production đầu tiên hạn 21/11; 4h kênh thứ hai/ảnh hạn dự kiến 29/11; tổng 12h. Tuần 3 chưa nghiệm thu toàn bộ đa kênh.
- **Hai reviewer dự kiến:** Nguyễn Đức Đạt (@dat532006), Đỗ Phước Thiện (@PhuocThien06). **Nhánh đề xuất:** `feat/sp-025-approved-publishing`.
- **Nguồn:** [Proposal F5/§5.1](../../SocialPilot_Proposal_Java.md), [social contract §§4–5](../../social-contract.md). **Phụ thuộc:** SP-022; [Thiện SP-036](tv3-thien-taskbrief.md#sp-036); [Đạt SP-032](tv2-dat-taskbrief.md#sp-032) cho ảnh.

**Thực hiện:** lấy payload từ đúng version bất biến với approval còn hiệu lực, không lấy bản mới nhất theo thời điểm gửi; giữ requestId/channelId/revision/approvedVersionId xuyên suốt; ánh xạ kết quả và lưu ID/URL/thời điểm. Bảo toàn kết quả kênh thành công khi kênh kia lỗi.

- [ ] Version chưa duyệt, đã bị sửa hoặc khác scope không được gửi; kiểm tra trước admission và ngay trước dispatch.
- [ ] SUCCESS chỉ khi có ID và URL thật đã đối chiếu; có ID chưa có URL giữ PUBLISHED_URL_PENDING và không đăng lại.
- [ ] Timeout sau khi có thể đã gửi giữ UNKNOWN/RECONCILE, không báo lỗi an toàn để retry mù.
- [ ] Bàn giao sang app mạng xã hội là trạng thái riêng, không tính là API thành công.
- [ ] Tuần 4 tích hợp đúng ảnh/alt-text capability do provider hỗ trợ, lấy từ version đã duyệt; không gán thành công alt text khi API không truyền được.
- [ ] Phiên bản đã thay đổi trong lúc tác vụ chạy không bị callback cũ ghi đè; dữ liệu của kết nối mới không nhận kết quả revision cũ.

**Kiểm thử/handoff:** unit/MockWebServer cho outcome, hủy/race và lỗi; bài thật được chủ kênh duyệt, kiểm đúng destination, ID và permalink. Số bài/ảnh và nội dung thử phải được xác nhận trước dispatch. Thiện nhận kết quả cho UI; SP-026 nhận API callback/outcome và persistence boundary; Nghị nhận published-post identity cho sync. Không coi harness text tuần 1 là editor/queue production.

<a id="sp-026"></a>

### SP-026 — Hàng đợi offline, retry và đối soát

- **Issue:** [#28](https://github.com/nigPeter2503/Social_Pilot/issues/28). **Yêu cầu:** F5.B2/B3/B4. **Ưu tiên:** High. **Công sức/mốc:** 16h tuần 4, hạn dự kiến 29/11, sau SP-025.
- **Hai reviewer dự kiến:** Nguyễn Đức Đạt (@dat532006), Tô Lãng Nghị (@bob35306). **Nhánh đề xuất:** `feat/sp-026-durable-publish-queue`.
- **Nguồn/phụ thuộc:** [Proposal F5](../../SocialPilot_Proposal_Java.md), [social contract §§4–7](../../social-contract.md), [Room contract §§3/6](../../room-schema-contract.md); SP-025, lượt schema từ Đạt và policy SP-024 đã chốt.

**Thực hiện:** tạo admission/claim/update nguyên tử và khóa nghiệp vụ theo scope brief/channel/approvedVersion; đăng ký Worker hữu hạn với ràng buộc mạng và checkpoint; retry cùng logical job, không tạo request mới để lách khóa. Unique Work không thay ràng buộc database. Chốt migration/version với Đạt trước sửa AppDatabase.

- [ ] Double tap, hai Worker tranh job, retry và restart không tạo bài trùng; khóa bền vững qua process death.
- [ ] Offline vẫn lưu được bài đã duyệt; Worker chỉ trả hoàn tất sau khi lưu kết quả/checkpoint, không sau khi vừa khởi động callback.
- [ ] Lỗi mạng/429 đủ điều kiện dùng backoff, Retry-After nếu có và số lần thử hữu hạn; lỗi quyền/nội dung dừng có next action.
- [ ] Sau dispatch chưa rõ kết quả: dừng đối soát; chỉ tạo lượt gửi mới khi người dùng xác nhận chủ động. Hủy cục bộ không chứng minh provider đã rollback.
- [ ] Migration giữ draft/version/queue và ràng buộc FK/unique; ngắt kênh không khôi phục job cũ sau callback/reconnect.
- [ ] Kiểm số bài thật trên destination ở ca chống trùng, không chỉ đếm request cục bộ; không hứa Worker chạy tức thời sau force-stop.

**Kiểm thử/handoff:** Room transaction/concurrency/migration; WorkManager test helpers; MockWebServer timeout sau gửi, 429, disconnect và cancellation; emulator/thiết bị cho offline/restart. Giao bảng job/outcome, video ca mất mạng, bằng chứng đối soát và giới hạn chưa chạy cho cả nhóm. Nếu >16h hoặc >2 ngày tập trung, báo phần còn lại và đề nghị child ticket trước khi mở rộng phạm vi.

## 5. Điều phối SP-055 và review

[SP-055 / #57](https://github.com/nigPeter2503/Social_Pilot/issues/57): Minh chịu trách nhiệm tổng hợp, mỗi người tự cung cấp kết quả module. Mỗi tuần chỉ tính **2h/người**: hai checkpoint ngắn, chuẩn bị và demo/quyết định; không cộng 8h team vào riêng tải Minh. Nguồn: [CONTRIBUTING §§8–9](../../../CONTRIBUTING.md), [brief chung §4](week-2-3-taskbrief.md).

- [ ] Bàn giao tuần 2 hạn 16/11: revision, output/partial, kiểm tra đã chạy, dependency và giờ còn lại của từng người.
- [ ] Cuối cutoff 21/11 và tuần 4: demo đúng revision; ghi rõ phần chưa đạt thay vì đổi mục tiêu âm thầm.
- [ ] Chuẩn bị Current tasks/Next plan/Blockers có link ticket, tên thật/handle; không tự gửi Slack hoặc nhắn thành viên khi chưa có ủy quyền gửi riêng.
- [ ] Ưu tiên review bảo mật của Đạt, hợp đồng approval của Thiện và capability của Nghị theo reviewer pairs trên issue; giờ review 4/4/2h là allowance, không phải approval đã có. Nếu thiếu giờ, điều phối roster trước mốc, không tự thay reviewer chỉ trong tài liệu.

## 6. Kiểm chứng, bàn giao và giới hạn

Android Java/XML/resource/config: tại `android/` chạy `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`; UI/lifecycle/Worker thêm emulator/thiết bị phù hợp API 23/36. Backend Java/config/build: tại `backend/` chạy `.\gradlew.bat test bootJar`; hợp đồng xuyên hai phía chạy cả hai. Chỉ tài liệu: kiểm tra nội dung/link và `git diff --check`. Đây là các kiểm tra **phải chạy khi triển khai**, chưa có kết quả mới do việc soạn brief.

Mỗi handoff gồm issue/branch/commit/PR, contract/schema revision, dữ liệu mẫu có nhãn, lệnh test + môi trường/ngày/kết quả, phần chưa chạy, người nhận và giờ còn lại. Không gửi secret qua issue/report. Mỗi issue triển khai có nhánh/PR riêng từ main mới nhất; nếu muốn merge độc lập một phần thì lập child ticket với ID đã tra cứu. Hai thành viên thật khác tác giả approve, checks đạt, nhận xét bắt buộc đã xử lý rồi mới squash merge/Done theo [CONTRIBUTING](../../../CONTRIBUTING.md).

**Rủi ro cần theo dõi:** entitlement refresh; policy retention; reviewer/timebox quá sát; SP-025 → SP-026 cùng tuần; hosting/callback đến muộn. Nêu bên đang chờ, tác động, phần độc lập tiếp tục được và mốc xử lý ngay trong ticket. Không giảm 44 yêu cầu hoặc dùng Week 6 mặc định để bù tính năng. SP-024 còn 10h, SP-054 còn 4h và các việc cuối kỳ của Minh vẫn nằm ngoài bảng giao tuần 2–4; không tự chèn thêm vào giờ đã kín.

**Điểm phối hợp:** [Đạt — AI/secret/schema](tv2-dat-taskbrief.md), [Thiện — brief/approval/UI](tv3-thien-taskbrief.md), [Nghị — sync/analytics](tv4-nghi-taskbrief.md). Nguồn phạm vi: [requirements](../../requirements.md), [architecture](../../architecture.md), [Proposal §4.1](../../SocialPilot_Proposal_Java.md).
