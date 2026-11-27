# SP-045 (adapter follower) — Follower adapter Android

TV4 / Tô Lãng Nghị. Cập nhật **19/11/2026 12:59, Asia/Saigon (UTC+07)**.
Trạng thái: **sẵn sàng review local/fixture**, chưa commit, push hoặc tạo PR.
Thời gian thực hiện: —. Live Facebook PAGE / LinkedIn MEMBER / ORGANIZATION: **NOT_RUN**.

## Baseline và bảo toàn workspace

- Đã đọc master plan SP-045 (adapter follower), CONTRIBUTING, analytics-data-contract **draft-4**,
  social-contract **v1**, handoff SP-045 (bàn giao analytics) **0.1.0** và SP-046 (hợp đồng DTO) **draft 0.1.0**.
- Worktree gốc `D:/CNTT/mobile/Social_Pilot` giữ nhánh
  `feat/sp-019-linkedin-comments-metrics-spike`, HEAD
  `7931f4dc5325f0550241f7d5188521bccec63b59`. Diff tracked/staged sạch trước sửa.
  Bảy file untracked trong `docs/handoffs/` và `domain/contract/analytics/` main/test
  được giữ nguyên; không copy, stage hoặc đưa vào task này. Đối chiếu SHA-256
  trước/sau giai đoạn kiểm tra cuối: **7/7 PASS**, các blob khớp handoff đã đọc.
- Sandbox không chạy được truy vấn mạng ban đầu. Lệnh xác minh thành công ngoài sandbox:
  `git -c credential.helper= -c core.askPass= fetch origin main`.
  Main đã fetch và base/HEAD task đều là **230c2a6ebcd8785288e57764c4ebb5fb6bbd4afa**.
  Không có task commit SHA; định danh bản làm việc bằng blob bên dưới.
- `git worktree add -b feat/sp-045-follower-adapter D:/CNTT/mobile/Social_Pilot-SP045 origin/main`
  tạo worktree riêng từ main đã xác minh. Không sửa nhánh/main/worktree W1.
- Handoff SP-045 (bàn giao analytics) blob `a34ce21e3920485e1a656335c382fce0fc45601d`;
  SP-046 (hợp đồng DTO) blob `d23e93bfa3974837a4d061de71783027a901228a`, chỉ đọc làm đầu vào.

## File và hành vi để review

| File (từ gốc worktree task) | Đầu ra / Git blob |
|---|---|
| [FollowerRequest.java](../android/app/src/main/java/com/socialpilot/app/data/remote/social/FollowerRequest.java) | Request bất biến: FACEBOOK_PAGE, LINKEDIN_MEMBER, LINKEDIN_ORGANIZATION; platform/channelId/revision, external account, API version và collectionId. Blob `99c428bc7476088fa0db44e6de946a035db95e46`. |
| [FollowerResult.java](../android/app/src/main/java/com/socialpilot/app/data/remote/social/FollowerResult.java) | ChannelScope, MetricDefinition, MetricSample và kết quả/lỗi nội bộ adapter theo shape SP-020. Blob `30983f46b678f1f49384ff284ec313fbe4980b61`. |
| [FollowerMetricAdapter.java](../android/app/src/main/java/com/socialpilot/app/data/remote/social/FollowerMetricAdapter.java) | GET bất đồng bộ, admission, mapping, error normalization, cancellation. Blob `26bb12cc826057d2e87c5a891fb911498c075396`. |
| [FollowerMetricAdapterTest.java](../android/app/src/test/java/com/socialpilot/app/data/remote/social/FollowerMetricAdapterTest.java) | 29 test JUnit/MockWebServer **MO_PHONG**. Blob `f28c3ac7c82ad23891ff6ccd851102c327c24621`. |
| [facebook-sp018.json](../android/app/src/test/resources/followers/facebook-sp018.json) | Fixture SP-018 từ main/base; blob `3cf24f0530000fdd3bf2b0b289de02aff72256d2`. |
| [linkedin-sp019.json](../android/app/src/test/resources/followers/linkedin-sp019.json) | Fixture SP-019 có trong HEAD W1 `7931f4d`, chưa vào main; blob `2ba97703ee9f0abf600d26f8d3a34effab505c37`. |

Hai fixture được copy vào test resources để checkout task chạy độc lập, chuẩn hóa LF,
đối chiếu JSON bằng `assert.deepEqual` với bản gốc: **2/2 PASS**.
Không đưa prototype/document LinkedIn W1 hoặc draft analytics untracked sang nhánh task.
Blob tính bằng `git hash-object <file>`, không cần ghi index hoặc commit.

| Account | Request / field | Định nghĩa |
|---|---|---|
| Facebook PAGE | `GET /{version}/{page-id}?fields=id,followers_count` → `followers_count`; kiểm ID Page trong response | CHANNEL / FOLLOWER_COUNT / GAUGE / COUNT / LATEST |
| LinkedIn MEMBER (ChannelInfo PERSON) | `GET /rest/memberFollowersCount?q=me` → một `elements[].memberFollowersCount`, không dateRange | CHANNEL / FOLLOWER_COUNT / GAUGE / COUNT / LATEST |
| LinkedIn ORGANIZATION | `GET /rest/networkSizes/{organization-urn}?edgeType=COMPANY_FOLLOWED_BY_MEMBER` → `firstDegreeSize` | CHANNEL / FOLLOWER_COUNT / GAUGE / COUNT / LATEST, riêng nghĩa edge này |

Definition ID tách account và API version; không khẳng định tương đương giữa version.
Không dùng fan_count, connections, kích thước danh sách hoặc metric bài làm follower.
Count dùng Long nguyên không âm, giữ 0; field vắng/null trả null/PROVIDER_OMITTED.
Payload sai kiểu, số âm, tràn Long, nhiều MEMBER totals hoặc dateRange trả FAILURE,
null/UNVERIFIED. Không dùng nhãn POINT_IN_TIME/CURRENT_TOTAL của prototype làm kind production.

Mỗi sample giữ scope, entityId là channel nội bộ, definitionId, collectionId,
source/sourceRef đã che, providerApiVersion. Các endpoint này chưa có mốc đo provider
được xác minh: observedAt = collectedAt = clock lúc đọc xong response,
observedAtBasis = COLLECTION, providerPeriodStart/End = null. Java time dùng UTC epoch ms
theo handoff; không biến follower thành số theo tuần hoặc gán giờ ảnh W1 làm giờ thu.

Admission yêu cầu Binding chứa ChannelInfo hiện hành và capability có **capabilityScope riêng**
khớp account/platform/channel/external ID/revision/version, operation READ_FOLLOWER_COUNT,
availability AVAILABLE, kết nối CONNECTED. Capability UNVERIFIED không mở mạng.
Kiểm lại sau lấy credential và sau nhận response; revision/account/token reference đổi
thì loại sample. Capability bị thu hồi trong lúc nhận thì không nhận số cũ.
Consumer phải lấy credential đúng subject (đặc biệt `q=me` không echo MEMBER) và kiểm
scope/revision nguyên tử khi ghi ở SP-045 (lưu snapshot và collector/Worker); adapter không thay transaction đó.

HTTP 401 và Facebook code 190 → AUTH_REQUIRED/RECONNECT; 429 và các code throttle
trong fixture Facebook → RATE_LIMITED/WAIT. Retry-After hỗ trợ giây hoặc HTTP date;
header thiếu/sai/tràn giữ retryAfterAt null. IOException → NETWORK_ERROR/RETRY_WHEN_SAFE.
403 chưa phân loại, code #100 đơn lẻ và lỗi provider chưa đủ bằng chứng → null/UNVERIFIED,
không bịa SocialError chung mới hoặc chuyển thành UNSUPPORTED. Lỗi kho credential là
LOCAL_ERROR, không coi là lỗi provider. Không cập nhật capability tự động.

PermissionEvidence chỉ phân loại PERMISSION_REQUIRED khi provider code và đúng target
account/scope/revision/version khớp. Test #100 dùng evidence **MO_PHONG** theo cơ chế này.
[SP-018 FB-10](facebook-comments-metrics-verification.md) là đối chứng lịch sử có
ảnh Page token thiếu pages_read_engagement và OAuthException #100; HTTP status/subcode
và app connectionRevision chưa được quan sát. Vì vậy không tự gắn bằng chứng W1 vào
revision app mới; HTTP 400 trong test là mô phỏng, không tuyên bố đó là status live FB-10.

Callback chạy nền, kết thúc tối đa một lần; cancel hủy HTTP và chặn callback muộn.
Token chỉ nằm ở Authorization header; không giữ body/message/exception/credential trong
kết quả. Không theo redirect mang credential. sourceRef/evidenceRef chỉ nhận nhãn đã che.
Các lớp DTO ở tầng data để review chữ ký, chưa sửa domain contract chung đang chờ chốt.

## Kiểm tra thực tế

Môi trường: Windows / PowerShell, Node 24.19.0, Temurin **21.0.13+9**, Gradle Wrapper **8.13**,
Android SDK `C:/Users/ASUS/AppData/Local/Android/Sdk`, compile/target 36, minSdk 23.
Tất cả request trong test dùng MockWebServer localhost với token giả và source **MO_PHONG**.

| Lệnh / phép thử | Expected → actual |
|---|---|
| Gốc task: `node docs/contracts/verify-analytics-examples.cjs` | PASS → **75/75**, MO_PHONG |
| Gốc task: `node docs/api-verification/facebook/verify-fixtures.cjs` | PASS → **55/55**, MO_PHONG |
| Gốc task: `node D:/CNTT/mobile/Social_Pilot/docs/api-verification/linkedin/verify-fixtures.cjs` | Chạy validator W1 trên ref có file → **128/128**, MO_PHONG, API_THAT=0 |
| Android: `.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | **BUILD SUCCESSFUL**, lượt cuối đầy đủ 31s; **89/89** unit, gồm **29/29** follower; lint **0 errors, 6 warnings** |
| Android: `.\gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest` sau chuẩn hóa LF fixture | **BUILD SUCCESSFUL**, **89/89** unit |
| Gốc task: `git diff --check`, `git diff --cached --check`, kiểm `--no-index --check` từng file mới | Không lỗi whitespace → PASS; index không thay đổi |

Gradle chạy với biến môi trường (không sửa build/config):

```powershell
$env:JAVA_HOME='D:\CNTT\mobile\.reviews\pr70-tools\jdk-21.0.13+9'
$env:ANDROID_HOME='C:\Users\ASUS\AppData\Local\Android\Sdk'
.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Lần đầu **cùng lệnh Gradle trên** bị sandbox chặn trước build:
`FileNotFoundException: C:\Users\ASUS\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13-bin.zip.lck (Access is denied)`.
Chạy lại được phê duyệt dùng cache ngoài workspace rồi đạt; không còn blocker build.
Lượt build đầu đầy đủ 1m53s có 24 test follower; sau bổ sung guard/race/lỗi, lượt cuối
ở bảng mới là bản kiểm chứng hiện hành. Cảnh báo lint thuộc EncryptedOAuthStore.java
và app/build.gradle; không có cảnh báo trong file follower.

Reports local (generated, không commit):
[JUnit](../android/app/build/reports/tests/testDebugUnitTest/index.html),
[follower XML](../android/app/build/test-results/testDebugUnitTest/TEST-com.socialpilot.app.data.remote.social.FollowerMetricAdapterTest.xml),
[lint](../android/app/build/reports/lint-results-debug.html),
[APK debug](../android/app/build/outputs/apk/debug/app-debug.apk).

29 test bao phủ request path/header, 0 cả ba loại account, missing/null field,
không fallback connections/fan_count, type/range validation, total vs period MEMBER,
sai account/channel/platform/revision/response Page ID, capability sai operation và
scope/version, disconnect/reauth, revision đổi trong credential lookup/response,
permission bị thu hồi, lỗi credential/transport, 401/403/#100 và evidence sai scope,
429/Retry-After, malformed/error envelope HTTP 200, redirect, cancel callback và
definition khác account/version.

## Còn thiếu và bước tiếp theo

Người nhận đầu ra: Nghị cho SP-045 (collector/Worker và lượt follower T1); Thiện tham chiếu nghĩa metric qua SP-045 (bàn giao analytics).
Chưa gửi tin nhắn/ACK, tạo PR hoặc cập nhật tracker. Bước tiếp: Nghị tự review bản local,
đối chiếu chữ ký với Minh khi nối Channel/credential/capability, rồi tích hợp collector
trong task tương ứng. Room, Worker, metric bài và UI nằm ngoài SP-045 (adapter follower).

Live **NOT_RUN** cho cả ba account: chưa có Channel/grant/capability gắn revision
được bàn giao và xác minh cho worktree này. LinkedIn 202610 vẫn là version khảo sát
fixture; cần Minh xác minh version/product/grant/role và mapping live riêng MEMBER/ORGANIZATION.
Mẫu app thực, persistence và lịch sử hai mốc thuộc các bước tiếp; không coi test fixture
là nghiệm thu API hoặc hoàn tất issue SP-045/#47 theo quy trình repo.
