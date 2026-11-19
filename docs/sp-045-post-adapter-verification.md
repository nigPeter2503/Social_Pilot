# SP-045.02 — Post metric adapter Android

TV4 / Tô Lãng Nghị. Cập nhật **19/11/2026 13:21, Asia/Saigon (UTC+07)**.
Trạng thái: **Chờ review local/fixture**, chưa commit, push, tạo PR, cập nhật tracker hoặc gửi tin.
Live Facebook PAGE / LinkedIn MEMBER / ORGANIZATION: **NOT_RUN**. Thời gian actual: —.

## Baseline và phạm vi

- Đã đọc master plan SP-045.02, CONTRIBUTING, analytics-data-contract **draft-4**,
  social-contract **v1**, handoff SP-045.05 **0.1.0**, SP-046.01 **draft 0.1.0**,
  hồ sơ/fixture/prototype SP-018 và SP-019, cùng code, fixture, 29 test và
  [evidence SP-045.01](sp-045-follower-adapter-verification.md).
- Tiếp tục worktree `D:/CNTT/mobile/Social_Pilot-SP045`, nhánh
  `feat/sp-045-follower-adapter` cho ticket SP-045/#47 theo chỉ định của người giao.
  HEAD/base/merge-base/origin-main cục bộ đều
  **230c2a6ebcd8785288e57764c4ebb5fb6bbd4afa**. Không có task commit SHA.
- Main remote đã xác minh bằng truy vấn chỉ đọc `ls-remote`, cũng là SHA trên;
  không merge/rebase/stash những đầu ra SP-045.01 đang untracked. Diff tracked/index
  sạch trước và sau triển khai. Dùng blob file để định danh bản review chưa commit.
- [Baseline SP-045.01](sp-045-02-baseline.json) ghi SHA-256 và Git blob **trước sửa**
  cho cả 7 file; đối chiếu cuối **7/7 giữ nguyên byte**. Code/test/evidence follower
  không sửa. [Git/plan baseline](sp-045-02-git-baseline.json) giữ hash master plan
  và nguyên văn hai dòng tiến độ trước cập nhật; dòng SP-045.01 được đối chiếu nguyên vẹn.
- Worktree gốc `Social_Pilot` giữ HEAD `7931f4dc5325f0550241f7d5188521bccec63b59`,
  nhánh `feat/sp-019-linkedin-comments-metrics-spike`. Bảy file untracked SP-045.05/
  SP-046.01 trong handoff và domain analytics chỉ đọc, không sao chép vào task.
  [Baseline riêng](sp-045-02-protected-baseline.json) và so hash cuối: **7/7 giữ nguyên**.
- Chỉ thêm adapter/DTO/registry nội bộ tầng data, unit test và evidence.
  Tái dùng validation account/scope từ FollowerRequest và enum nguồn FollowerResult,
  không refactor transport follower để bảo toàn baseline. Chưa đổi domain contract,
  AppDatabase/schema/version/migration, Room, Worker, UI, ER hoặc collector.

## API và admission

`read(PostRequest, callback) -> Cancelable` chạy nền, callback terminal tối đa một lần;
cancel hủy HTTP và chặn callback muộn. Mỗi request là **một query/endpoint**, không ghép
nhiều response thành một measurement window. Post ID/URN và external account giữ nguyên
chuỗi, kể cả số 0 đầu. LinkedIn entity union và List được encode một lần.

PostRequest mang account typed PAGE/MEMBER/ORGANIZATION, platform/channel/revision,
external account, post ID/URN, version, collectionId, query và optional period.
Binding phải lấy từ Channel hiện hành và chứa capabilityScope khớp **account, scope,
revision, version, post, query và period**, operation **READ_POST_METRICS**, CONNECTED,
availability AVAILABLE. Caller cấp binding sau khi xác minh ownership của bài trên kênh;
adapter không đoán ownership bằng cách cắt ID/URN. CredentialSource phải trả token đúng
subject, đặc biệt MEMBER analytics là của member đã xác thực.

`metricGrants` là map capability độc lập theo metric cho đúng capabilityScope. Thiếu
grant hoặc sai operation giữ UNVERIFIED, grant PERMISSION_REQUIRED/UNSUPPORTED giữ đúng
reason, không lấy AVAILABLE cấp operation làm quyền cho mọi field. Không có metric được
phép đọc thì không lấy token/gửi mạng. Kiểm lại binding sau credential và khi nhận body;
revision/account/tokenReference đổi thì loại kết quả. Thu hồi grant metric riêng chặn
giá trị metric đó, kể cả response còn chứa số. Không tự cập nhật capability.

Response phải khớp subject khi provider echo: Facebook shares `id`, optional edge
post/account/from, optional insight id; MEMBER targetEntity union + metricType + optional
author; ORGANIZATION organizationalEntity và đúng share/ugcPost. Sai subject → REJECTED,
samples rỗng. Edge Facebook thường không echo post: ràng buộc bằng URL request đã dựng,
Call và binding scope; không dựng ID từ các reaction/comment con. Trước ghi production,
SP-045.03/.04 vẫn phải kiểm revision/ownership và ghi cùng transaction.

## Mapping table

Mọi unit là COUNT, entityType POST. Definition ID tách account/query/metric/API version,
kind và múi giờ kỳ nếu có; không tuyên bố tương đương giữa version hoặc provider.
Các kind/window bổ sung chỉ là đề xuất **tầng data**, chưa mở rộng enum domain chung.

| Account / query | Request và field/filter | Định nghĩa/khoảng đo và thiếu dữ liệu |
|---|---|---|
| Facebook PAGE / FB_REACTIONS | `/{version}/{post}/reactions?summary=true&limit=1` → `summary.total_count` | REACTION_COUNT, CUMULATIVE/TOTAL; tổng edge, không lấy độ dài data/viewer_reaction |
| Facebook / FB_LIKES | Cùng edge, thêm `type=LIKE` | LIKE_COUNT, CUMULATIVE/TOTAL; definition và filter riêng, không cộng với tổng reaction |
| Facebook / FB_COMMENTS_TOPLEVEL | `/{post}/comments?summary=true&filter=toplevel&limit=1` | COMMENT_COUNT, CUMULATIVE/TOTAL, chỉ comment cấp cao nhất |
| Facebook / FB_COMMENTS_STREAM | Cùng edge, `filter=stream` | COMMENT_COUNT, CUMULATIVE/TOTAL, gồm reply; definition khác toplevel |
| Facebook / FB_SHARES | `/{post}?fields=id,shares` → `shares.count` | SHARE_COUNT, CUMULATIVE/TOTAL; shares vắng/null → null/PROVIDER_OMITTED |
| Facebook / FB_MEDIA_VIEWS | `/{post}/insights?metric=post_media_view&period=lifetime` → một `values[].value` đúng name/period | MEDIA_VIEW_COUNT, CUMULATIVE/TOTAL, window LIFETIME; không thay impression |
| Facebook / FB_UNIQUE_VIEWERS | Insights `post_total_media_view_unique`, lifetime | UNIQUE_MEDIA_VIEWER_COUNT, CUMULATIVE/DISTINCT_TOTAL, tách views và impression |
| Facebook / FB_IMPRESSIONS | Không gửi API chưa xác minh | IMPRESSION_COUNT null/UNVERIFIED; kind/aggregation/window UNVERIFIED, không tự gán nghĩa hoặc fallback views |
| LinkedIn MEMBER / từng IMPRESSION, REACTION, COMMENT, RESHARE, MEMBERS_REACHED | `/rest/memberCreatorPostAnalytics?q=entity&entity=(share:{encodedUrn})` hoặc `(ugc:{encodedUrn})`, từng queryType, `aggregation=TOTAL` → `elements[].count` | Metric riêng, CUMULATIVE/LIFETIME khi không dateRange; MEMBERS_REACHED dùng DISTINCT_TOTAL, các metric khác TOTAL. `likeCount` không được dùng |
| LinkedIn MEMBER / TOTAL với dateRange tường minh | Cùng entity finder; thêm dateRange start/end | PERIOD/TOTAL hoặc DISTINCT_TOTAL; bắt buộc response khớp hai ngày và evidence zone từ request. Không trộn với TOTAL lifetime; không hỗ trợ DAILY trong task này |
| LinkedIn ORGANIZATION / ORGANIZATION_ORGANIC | `/rest/organizationalEntityShareStatistics?q=organizationalEntity&organizationalEntity={org}&shares=List({share})` hoặc `ugcPosts=List({ugcPost})`, không timeIntervals | `totalShareStatistics.impressionCount/commentCount/shareCount` → IMPRESSION_COUNT/COMMENT_COUNT/SHARE_COUNT; PROVIDER_ROLLING_12_MONTHS_ORGANIC/TOTAL. Bounds null, không gọi lifetime CUMULATIVE |
| LinkedIn ORGANIZATION / like riêng | `totalShareStatistics.likeCount` | PROVIDER_LIKE_COUNT, cùng rolling organic, `permitsNegative=true`: giữ số âm provider-specific; REACTION_COUNT riêng null/UNVERIFIED, không lấy like làm all-reaction |
| LinkedIn ORGANIZATION / time-bound aggregate | Không tạo request post cho endpoint aggregate tổ chức | Reject period trong PostRequest ORGANIZATION; response timeRange hoặc không có đúng post không thành single-post sample. Không gắn số aggregate cho một bài |

Căn cứ Facebook: [SP-018 FB-05–09 và bảng bàn giao](facebook-comments-metrics-verification.md),
base `230c2a6`. LinkedIn: SP-019 ở HEAD gốc `7931f4d` (kế thừa `306a1da`),
[hồ sơ SP-019](../../Social_Pilot/docs/linkedin-comments-metrics-verification.md),
[handoff SP-045.05](../../Social_Pilot/docs/handoffs/sp-045-analytics-input.md).
Đã đọc bổ sung tài liệu chính thức cho parser MEMBER vốn chưa có trong prototype:
[Member Post Statistics](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/members/post-statistics?view=li-lms-2026-10)
và [Organization Share Statistics](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/organizations/share-statistics?view=li-lms-2026-10).
Version 202610 là ứng viên fixture; chưa chứng minh grant/mapping live cho app.

## Sample, thời gian và trạng thái

Sample giữ scope, externalAccountId, entityId nguyên dạng post, definitionId, value/reason,
providerApiVersion, source/sourceRef đã che và collectionId. `observedAt = collectedAt`
là clock lúc body nhận xong, basis COLLECTION; không dùng created_time/end_time hoặc giờ
chụp ảnh làm observedAt khi chưa xác minh nghĩa. Mỗi read có timestamp riêng; cùng
collectionId không chứng minh các metric đa endpoint đủ alignment để tính ER.

Không tự tạo period bounds. CUMULATIVE và rolling organic không có bounds giả. MEMBER
Period chỉ nhận hai ngày hợp lệ tăng dần, explicit verifiedZoneId và evidenceRef; không
mặc định zone từ máy/UTC hoặc end=now. Tài liệu hiện đọc chưa xác nhận timezone date-only
cho app nên **chưa có period evidence live**. UTC trong test period là MO_PHONG tường minh.
Chỉ sau khi response echo đủ start/end khớp request mới chuẩn hóa theo zone đã cấp thành
UTC epoch ms `[start,end)` và giữ periodEvidenceRef. Thiếu/null/sai bounds → FAILURE,
null/UNVERIFIED, bounds null; không giữ số PERIOD không có bounds, không đổi thành lifetime.

- SUCCESS: response hợp lệ, mọi metric trả số; **0 hợp lệ**.
- PARTIAL: request thành công nhưng một số field thiếu/null, grant bị chặn, định nghĩa
  chưa xác minh hoặc field riêng sai kiểu; giữ các metric tốt. All-omitted hợp lệ vẫn
  là PARTIAL, không báo request thất bại hay suy 0 từ empty elements.
- UNAVAILABLE: không gửi request vì không có submetric grant/mapping dùng được.
- FAILURE: admission bị chặn, auth/provider/network failure hoặc response không có
  số dùng được do schema/type/period sai. Không biến lỗi thành SUCCESS/empty/0.
- REJECTED: scope/subject/revision không khớp, disconnect hoặc lỗi dependency local;
  samples rỗng. Failure cho biết nguyên nhân cục bộ; không lưu vào history.

Count phải là JSON number nguyên trong Long, không chấp nhận string/boolean/object/
fraction/overflow. Chỉ org PROVIDER_LIKE_COUNT nhận âm theo evidence SP-019; count khác
âm là INVALID_RESPONSE/UNVERIFIED. Breakdown views dạng object, nhiều values/rows mơ hồ,
sai metric/period cũng không được cộng hay ép thành số. Invalid field trong org response
giữ metric trước/sau hợp lệ, gắn failure INVALID_RESPONSE cùng PARTIAL.

HTTP 401/FB 190 → AUTH_REQUIRED/RECONNECT; 429/FB throttle codes 4/17/32/613 →
RATE_LIMITED/WAIT với Retry-After giây hoặc HTTP date. Header sai/tràn/thiếu giữ null.
IOException → NETWORK_ERROR/RETRY_WHEN_SAFE. HTTP 403 hoặc FB #100 đơn lẻ →
UNVERIFIED, SocialError null. PermissionEvidence chỉ áp dụng provider code **đúng kiểu**
và exact target/query/account/scope/revision/version; không suy từ message. Đối chứng
FB-10 của follower không tự cấp evidence cho metric bài hoặc revision mới.
Token ở Authorization header, không trả body/message/URL/exception nhạy cảm; không follow redirect.

## File/blob của bản review

| File (từ gốc task) | Git blob (`git hash-object`) |
|---|---|
| [android/app/src/main/java/com/socialpilot/app/data/remote/social/PostRequest.java](../android/app/src/main/java/com/socialpilot/app/data/remote/social/PostRequest.java) | `0808cdc151868d9ff584b82820142b919e2494b6` |
| [android/app/src/main/java/com/socialpilot/app/data/remote/social/PostResult.java](../android/app/src/main/java/com/socialpilot/app/data/remote/social/PostResult.java) | `5f7dbcc7e73cf6f483fd2c063c7bbcb8f46e228b` |
| [android/app/src/main/java/com/socialpilot/app/data/remote/social/PostMetricMapping.java](../android/app/src/main/java/com/socialpilot/app/data/remote/social/PostMetricMapping.java) | `ddeae53421207e2343cea857275a6b8df64d3829` |
| [android/app/src/main/java/com/socialpilot/app/data/remote/social/PostMetricAdapter.java](../android/app/src/main/java/com/socialpilot/app/data/remote/social/PostMetricAdapter.java) | `a0380c48c8095e9a8db1dd353dd4a493c4bc0112` |
| [android/app/src/test/java/com/socialpilot/app/data/remote/social/PostMetricAdapterTest.java](../android/app/src/test/java/com/socialpilot/app/data/remote/social/PostMetricAdapterTest.java) | `2e5e25d91ee7d76e09270079e53c5827fd7ac9f1` |
| [android/app/src/test/resources/posts/sp045-02.json](../android/app/src/test/resources/posts/sp045-02.json) | `f2e697a9c55735a3f6c03238f3dd3d7871833866` |
| [docs/sp-045-02-baseline.json](sp-045-02-baseline.json) | `f65cf3ce1bde098fb3485af4ebd6f46fa622d009` |
| [docs/sp-045-02-protected-baseline.json](sp-045-02-protected-baseline.json) | `9c543719604029e221ae2b48b3a7510da436a817` |
| [docs/sp-045-02-git-baseline.json](sp-045-02-git-baseline.json) | `7cbac7cfb2a3ead5afb5d1ad3eabc7e6758a3d5d` |

Tất cả file trong bảng là **file mới SP-045.02**, chưa tracked. File sửa ngoài worktree:
`D:/CNTT/mobile/TV4-W2-W3-MASTER-PLAN.md`, chỉ dòng tiến độ SP-045.02. Evidence này mới;
không sửa evidence SP-045.01. Bản review được định danh bằng file blob ở bảng và base SHA,
không có commit mới.

## Kiểm chứng thực tế

JDK đã xác minh `D:/CNTT/mobile/.reviews/pr70-tools/jdk-21.0.13+9/bin/java.exe -version`:
Temurin **21.0.13+9**. SDK platform jar đã xác minh tại
`C:/Users/ASUS/AppData/Local/Android/Sdk/platforms/android-36/android.jar` (27.768.026 bytes).
Node **24.19.0**, Gradle Wrapper **8.13**, compile/target SDK 36, minSdk 23. Không đổi build config.

| Working directory / lệnh | Kết quả thực tế |
|---|---|
| Task root: `git status --short`, `git branch --show-current`, `git worktree list`, `git diff`, `git diff --cached`, `git merge-base HEAD origin/main` | Nhánh/base đúng, tracked/index sạch, đầu ra follower untracked được giữ |
| Task root: `git ls-remote origin refs/heads/main` | Sandbox lỗi `getaddrinfo() thread failed to start`; đã xử lý bằng lệnh chỉ đọc có quyền mạng bên dưới |
| Task root: `git -c credential.helper= -c core.askPass= ls-remote origin refs/heads/main` | Exit 0, remote main `230c2a6ebcd8785288e57764c4ebb5fb6bbd4afa`; không thay ref/worktree |
| Task root: `node docs/contracts/verify-analytics-examples.cjs` | Exit 0, **75/75**, MO_PHONG |
| Task root: `node docs/api-verification/facebook/verify-fixtures.cjs` | Exit 0, **55/55**, MO_PHONG |
| Gốc `Social_Pilot` (HEAD `7931f4d`, nơi có file): `node docs/api-verification/linkedin/verify-fixtures.cjs` | Exit 0, **128/128**, MO_PHONG, API_THAT=0 |
| Task `android/`: Gradle command bên dưới | Lượt cuối **BUILD SUCCESSFUL in 1m10s**, assembleDebug, **136/136** UNIT (post **47/47**, follower **29/29**, test khác **60/60**), lintDebug **0 errors/6 warnings** có sẵn ngoài adapter |
| Task root: `git diff --check`, `git diff --cached --check` | Exit 0, PASS |
| Từng file untracked: `git -c core.autocrlf=false diff --no-index --check -- /dev/null <file>` + đọc UTF-8 fatal/LF/newline/trailing whitespace | PASS, gồm file mới ngoài diff tracked; `--no-index` exit 1 và không diagnostics là file khác /dev/null, không phải whitespace error |
| SHA-256/hash-object baseline đối chiếu cuối | Follower **7/7** và worktree gốc **7/7** giữ nguyên; dòng SP-045.01 nguyên vẹn |

```powershell
$env:JAVA_HOME='D:\CNTT\mobile\.reviews\pr70-tools\jdk-21.0.13+9'
$env:ANDROID_HOME='C:\Users\ASUS\AppData\Local\Android\Sdk'
.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Lần đầu cùng lệnh Gradle bị sandbox chặn trước build:
`FileNotFoundException: C:\Users\ASUS\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13-bin.zip.lck (Access is denied)`.
Chạy lại với quyền dùng cache ngoài workspace đã đạt. Lượt đầu đầy đủ đạt 128 unit
(39 post + 29 follower); bổ sung guard thu hồi submetric và edge tests rồi lượt cuối
ở bảng đạt 136. Không có test failure bị bỏ qua. Lúc chuẩn bị code, alias `python`
trỏ WindowsApps không chạy được; chuyển sang Node sẵn có, không cài/thay môi trường.
Harness whitespace ban đầu coi exit 1 của no-index là lỗi; chạy lại xử lý đúng semantics
và đối chiếu trực tiếp UTF-8/LF/newline/trailing spaces, đạt.

Test đọc fixture Facebook/LinkedIn SP-018/SP-019 **đã có sẵn từ SP-045.01**; không sửa
hai fixture đó. Fixture bổ sung `posts/sp045-02.json` ghi source **MO_PHONG**; raw JSON,
HTTP/error/permission/zone trong Java test cũng ghi nhãn MO_PHONG, không phải live proof.
47 test bao phủ mapping account/endpoint, 0/missing/null/type/overflow/negative, partial,
period/total, views/viewers/impression, LIKE/reaction, hai filter comment, sai subject/
scope/revision/capability, thu hồi quyền, 401/403/#100/evidence, 429/Retry-After, body I/O,
cancel, redirect và lỗi dependency. Không chạy instrumented/device test vì không thêm
storage/UI/Worker; live cả ba account NOT_RUN.

Reports được sinh cục bộ, không commit:
[JUnit](../android/app/build/reports/tests/testDebugUnitTest/index.html),
[post XML](../android/app/build/test-results/testDebugUnitTest/TEST-com.socialpilot.app.data.remote.social.PostMetricAdapterTest.xml),
[follower XML](../android/app/build/test-results/testDebugUnitTest/TEST-com.socialpilot.app.data.remote.social.FollowerMetricAdapterTest.xml),
[lint](../android/app/build/reports/lint-results-debug.html),
[APK](../android/app/build/outputs/apk/debug/app-debug.apk).

## Giới hạn và bước tiếp

Nghị review bản local trước khi dùng cho SP-045.04/.07; Thiện tham chiếu definition qua
SP-045.05. Chưa gửi handoff hoặc yêu cầu ACK cho thành viên khác. Cần xác nhận chữ ký
binding/credential và từng submetric grant với Minh, quyền/product/role/version LinkedIn,
zone/period evidence thực và ownership PublishedPost trước tích hợp production.
Storage/transaction và collector ở task riêng, contract rolling/signed provider metric
cần review trước đưa sang domain. Chưa có bằng chứng alignment đa endpoint hoặc hai mốc
app thật; không tính ER, không gộp rolling org với lifetime member hay organic với paid.
Live NOT_RUN không cản hoàn thành đầu ra fixture để review, cũng không phải nghiệm thu
toàn ticket SP-045 hoặc phê duyệt hợp đồng chung.
