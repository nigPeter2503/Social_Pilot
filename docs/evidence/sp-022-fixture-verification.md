# SP-022 — Vòng đời kết nối bằng fixture

Ngày 21/11/2026 (UTC+07:00), TV1 Ngô Gia Minh nhận [issue #24](https://github.com/nigPeter2503/Social_Pilot/issues/24).
Đã đọc lại trên [Project](https://github.com/users/nigPeter2503/projects/5) và xác minh assignee `nigPeter2503`, trạng thái `In Progress`.
Mốc Minh xác nhận là **20:00 ngày 21/11/2026**. Số giờ còn lại sau khi đổi kênh đang chờ xác nhận. Reviewer dự kiến là Đạt (`dat532006`) và Nghị (`bob35306`).

## Đầu vào và phạm vi

- Nhánh `feat/sp-022-oauth-token-lifecycle` tách từ `main` tại `ac38888c7038eaa8135933acac72d565ab29f2e5`. SP-009 (`4f69a39`) và SP-010 (`c7b0b9c`) đã tích hợp. [Hợp đồng Social](../social-contract.md) · [Bằng chứng SP-010](../drafts/sp-010-implementation-status.md)
- SP-065 đang review tại head `922982f7540b10218b8ad4d48ca05169263633ce`. Fixture dùng danh tính `INSTAGRAM / PROFESSIONAL` và Page liên kết theo đề xuất đó. Bản spike cung cấp bằng chứng thiết bị và đổi token dài hạn cho tài khoản thử. Cần đối chiếu lại enum, wire metadata và ràng buộc của phiên sau khi PR tích hợp. [PR #94](https://github.com/nigPeter2503/Social_Pilot/pull/94)
- Bước 7 còn chờ Đạt bàn giao API kho bí mật SP-027 và chốt lượt/version Room. Đường OAuth Facebook đang dùng kho spike. Kiểm thử dưới đây chạy chính sách vòng đời độc lập trong JVM; fixture repository chạy hoàn toàn trong bộ test. [SP-027](https://github.com/nigPeter2503/Social_Pilot/issues/29) · [Hợp đồng Room](../room-schema-contract.md)

## Hợp đồng bàn giao mô phỏng

[Chính sách kết nối](../../android/app/src/main/java/com/socialpilot/app/domain/contract/social/ConnectionLifecycle.java)
nhận DTO OAuth Facebook hiện hữu và metadata đã xác thực theo đề xuất SP-065. Khóa lookup là
`(platform, accountType, externalAccountId)` hoặc `channelId`. Cùng danh tính giữ `channelId`
và tăng revision. ID Instagram và Page liên kết được kiểm riêng. Thời điểm đến hạn là mốc sớm
nhất trong hạn token và hạn quyền truy cập dữ liệu nếu nhận được cả hai. Thiếu cả hai nghĩa
là chưa biết hạn. Refresh vẫn cần cơ chế được Meta cấp và ca thử thực tế.

[Fixture MO_PHONG](../../android/app/src/test/resources/social/sp-022-consumer-fixtures.json)
cho Nghị (SP-041/SP-045) và Thiện (SP-023/SP-025) gồm ba trạng thái và một ca ghi muộn:

| Scope | Kết quả và cách dùng |
| --- | --- |
| Instagram, `ig-demo`, revision 7 | `CONNECTED`, `READ_COMMENTS = AVAILABLE`; consumer dùng scope này khi lưu kết quả comment. |
| Instagram, `ig-demo`, revision 7 | `REAUTH_REQUIRED`, `AUTH_REQUIRED / RECONNECT`; dừng yêu cầu cần xác thực, giữ draft và cache. |
| Instagram, `ig-demo`, revision 8 | `CONNECTED`, `READ_FOLLOWER_COUNT = PERMISSION_REQUIRED`, `PERMISSION_REQUIRED / RESOLVE_PERMISSION`; capability của từng operation có kết quả riêng. |
| Response revision 7 sau khi đã có revision 8 | Trong transaction ghi, so `platform`, `channelId`, `connectionRevision` với kênh hiện hành và bỏ response cũ. |

Consumer comment và metrics dùng scope, operation và bằng chứng xác minh UTC. Scope OAuth chỉ
là đầu vào kiểm quyền. Nghị dùng `(channelId, externalPostId)` khi tạo root bài đã có; follower
cấp kênh chờ Channel production và quyền follower. `ChannelInfo` và reference thuộc ranh giới
kết nối. Fixture Android kiểm DTO Facebook với reference giả; DTO Instagram chờ enum của
SP-065. Lookup có bản ghi thiếu reference trả hướng khôi phục trong fixture; semantics thực
của lỗi đọc kho chờ chốt với Đạt. Bàn giao production và xác nhận của bên nhận còn mở.
[Hợp đồng analytics](../analytics-data-contract.md) · [Brief TV4](../TaskBrief/W2-W3/tv4-nghi-taskbrief.md)

## Kết quả ngày 21/11/2026

Windows, JDK 21.0.7, Android SDK Platform 36 cho build Android. Các ca giả lập mang nhãn `MO_PHONG`.

| Kiểm tra | Kết quả |
| --- | --- |
| `git diff --check` | Đạt. |
| `android` · `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | Đạt, 66 unit test, lint 0 lỗi và 14 cảnh báo. |
| `backend` · `.\gradlew.bat test bootJar` | Đạt, 41 test. Hồi quy SP-010 kiểm state, phiên, verifier, TTL, replay và hai lượt đổi mã đồng thời với một lượt thắng. MockMvc kiểm provider/redirect sai và callback bị từ chối chỉ trả session cùng mã lỗi chuẩn hóa. |
| Thiết bị, restart, migration và Meta thật cho SP-022 | Chưa chạy. ADB chưa có thiết bị và SDK chưa có AVD khả dụng. Kiểm thử production còn chờ SP-065, SP-027, lượt schema và môi trường HTTPS thử. |

## Đồng bộ ngày 25/11/2026

Đã ghép main tại `ddab17f` cùng SP-065 và SP-027. Android dùng wire DTO của cả hai
đích trong callback. Pending đã tách theo provider. Ca hồi quy kiểm callback
của phiên bị thay thế, phiên cũ khi nâng cấp, tiêu thụ một lần sau restart và lỗi
ghi xóa verifier. Handoff giữ riêng hai nguồn hạn khi Meta cung cấp, đồng thời
giữ `expiresAt` cho app cũ.

| Kiểm tra trên worktree tích hợp | Kết quả |
| --- | --- |
| Android `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` | Đạt. 153 unit test, lint 0 lỗi và 11 cảnh báo. |
| Backend `test bootJar` | Đạt. 43 test, gồm fixture JSON dùng chung Android/backend và hai hạn khác nhau trong handoff. |
| AndroidJUnitRunner trên AVD API 36 | Đạt 30/30. Gồm SP-065, Keystore, prototype và BYOK. Dùng dữ liệu thử. |

## Repository production và migration Channel

Dùng migration 1 → 2 theo [hợp đồng Room](../room-schema-contract.md#6-thứ-tự-migration-từ-v1-và-kiểm-thử).
Sau callback, `AppContainer.channels` ghi ref mới vào danh sách cleanup, ghi
token trong `SOCIAL_TOKEN`, rồi đổi ref và revision trong transaction Room.
Lỗi ghi giữ kết nối cũ. Ref chưa được gắn và ref đã thay được dọn theo journal
khi tiến trình mở lại. Bản ghi OAuth v2 từ SP-010/065 được nhập theo provider,
chỉ bỏ bản cũ sau khi Room và credential mới đã ghi thành công. Dữ liệu đăng
Instagram đang chờ đối soát dùng kho attempt hiện hành.

- SP-023/035 dùng `ChannelRepository.list()` và `find(channelId)` trên worker.
  `Snapshot` trả danh tính, kết nối, hạn và capability theo thao tác. Tình trạng
  `MISSING` và `UNREADABLE` dẫn tới reconnect, tách với quyền của từng thao tác.
  Khi nối #97, phân biệt repository này với field `AppContainer.channels`
  cùng tên đang dùng cho data source UI cũ trên nhánh SP-023.
- SP-025/041/045 đọc credential ở tầng adapter qua `credential(scope)`. Khi cập
  nhật capability, dùng `recordCapability(scope, result)`. Các module ghi Room
  phải kiểm lại platform, ID và revision **trong transaction ghi tương ứng**.
  `isCurrent(scope)` chỉ kiểm tại thời điểm gọi.
- Thử đăng Instagram khi chưa biết hạn token dùng GET kiểm danh tính trước
  khi tạo container. Kết quả OAuth hay GET danh tính chỉ xác nhận đúng kết nối.
  Quyền comment, follower và publish cần bằng chứng theo operation.

OAuth Meta thật và refresh với quyền được cấp vẫn cần môi trường HTTPS và tài khoản
thử. Lượt AVD sử dụng credential giả và phản hồi provider có kiểm soát.

Trên base main `c5b8b17`, Android build, lint và 178 unit test đạt. AndroidJUnitRunner
API 36 đạt 35/35, gồm bốn ca Room/Keystore và ca kiểm danh tính trước publish
khi thiếu hạn. Các ca Room nâng từ v1 qua builder thật, giữ ID/topic nullable/thời
gian draft, mở lại hai kết nối đã nhập, chặn revision cũ, kiểm lỗi ghi secret,
rollback khi transaction Room lỗi và dọn journal sau khi mở lại. Backend
`test bootJar` đạt với kết quả 43 test đã lưu từ cùng mã backend.

### Kiểm tra trên Samsung API 36

- Bản kiểm thử từ `61c667b` dùng application ID riêng `com.socialpilot.app.sp022qa`
  và base URL HTTPS của môi trường thử. AndroidJUnitRunner trên Samsung SM-G990E
  đạt **35/35** với fixture, gồm migration SQLite thật, Android Keystore, kiểm
  callback từ chối cấp quyền và hồi quy Instagram.
- Dùng `aapt` đọc manifest của APK cài thử. `allowBackup=false`,
  `usesCleartextTraffic=false` và hai tài nguyên loại dữ liệu backup đều có
  trong APK. Kiểm tệp trong APK cho kết quả 0 tệp `.env`, cấu hình Spring hoặc
  keystore đóng gói. Kiểm theo cấu trúc APK và fixture, kiểm URL/log với phiên
  Meta thật còn mở.
- SHA-256 của APK thử là
  `b18991fb6cab602cde4155c478a140e23c9769937ac7c45a4d5b9d3b7ff63196`.

## Nghiệm thu còn mở

- [ ] Hai kênh qua luồng production đúng danh tính, reference và revision sau restart.
- [ ] Hết hạn, thu hồi, kết nối lại trên thiết bị và draft còn nguyên. Ghi cơ chế refresh nếu được cấp.
- [ ] Hồi quy callback/handoff trên bản tích hợp SP-065 và đường ghi kết nối production.
- [ ] Kiểm tra URL, log, Room, backup và APK của bản tích hợp.
- [ ] Bên nhận xác nhận bàn giao production, hai reviewer approve, PR đã merge và Project đã cập nhật hoàn tất.

F1.B1 và F1.B3 vẫn mở theo [ma trận yêu cầu](../requirements.md).
