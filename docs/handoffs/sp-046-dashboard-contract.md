# SP-046 · Query và DTO dashboard cho Thiện/SP-047

**Bản consumer 23/11/2026:** mã/test `aa5d592f27436b833edc5b3b562083cab1b64bd2`, kế thừa fix PARTIAL `0573ca9126a9bb22ad333996d75afc4ffdfa2bb1`. Chạy mới **159/159 JVM**, 17 suite, 0 failure/error/skip; assemble đạt, lint 0 lỗi/14 cảnh báo, analytics fixture 75/75. Java query/DTO vẫn draft **0.1.0**, analytics draft-4; không đổi schema. [Evidence và ma trận consumer](../sp-046-consumer-verification-2026-11-23.md) · [Manifest production NOT_RUN](../sp-046-reconciliation-manifest.json).

ViewModel nhận `AnalyticsRepository`. Implementation là `DefaultAnalyticsRepository`, đọc và refresh projection qua `AnalyticsSource`. Fixture `SimulatedAnalyticsSource` nằm trong src/test và dùng bộ nhớ. Chưa có nguồn DAO SP-045 hoặc binding AppContainer cho dashboard thật. [PR #89](https://github.com/nigPeter2503/Social_Pilot/pull/89) · [Source port](sp-046-source-query-proposal.md) · [Implementation](../../android/app/src/main/java/com/socialpilot/app/data/repository/analytics/DefaultAnalyticsRepository.java)

**ACK đã xác minh:** [Thiện xác nhận consumer](https://github.com/nigPeter2503/Social_Pilot/pull/89#issuecomment-7094996411) cho handoff `2012752` và Java `c2da648`: observe/refresh với hai Cancelable, dispatch main thread, generation query/revision, 0/null, giữ cache khi lỗi và nhãn MO_PHONG. Không suy ACK đó thành xác nhận fix `0573ca9`, bộ test `aa5d592`, source transaction hay dashboard production. Bản PARTIAL này chờ Thiện đối chiếu lại; review CHANGES_REQUESTED của Đạt trên `1f96962` chưa có re-review trong lần đọc GitHub 23/11.

## PARTIAL: dữ liệu tốt cũ cùng dữ liệu mới

Source commit sample thành công và attempt/errors nguyên tử trước khi trả. Batch của `refreshProjection` có thể thiếu lịch sử; repository chỉ project kết quả **readCached đầy đủ**, có attempt khớp. Readback không available hoặc attempt khác → FAILED/CACHE_READ_FAILED, giữ projection tốt đã có; không publish batch riêng của attempt như cache đầy đủ. Source chịu trách nhiệm tính đầy đủ của readback: so attempt không tự chứng minh DAO đã giữ mọi mẫu.

Ca consumer qua repository thật và hàng đợi UI: cache follower 10, tương tác tuần `(12−0)+(3−0)+(5−0)=20`; refresh chỉ có follower 15 và NETWORK_ERROR/READ_POST_METRICS. Observer render follower 15, tuần 20, CONTENT/ERROR rồi callback PARTIAL. `lastAttemptAt` theo lần mới; 20 giữ snapshot/collection IDs, observedAt/basis, collectedAt, bounds và coverage cũ. Freshness lấy từ input cũ nhất của từng giá trị; timestamp mới của follower không làm 20 thành fresh. Đọc errors của dashboard/card và freshness của **từng metric**, không dùng `lastSuccessAt` toàn dashboard để tô mới mọi số.

Reattach chỉ đọc cache, không gọi refresh; generation được kiểm lại khi UI thực thi công việc đã queued. Cancel refresh chặn outcome muộn nhưng không hoàn tác dữ liệu đã commit. Đổi query/revision hủy hai handle cũ và vô hiệu generation trước attach. Tạo repository mới đọc được history/attempt từ cùng source RAM chỉ là **MO_PHONG**, không phải restart/process death trên Room. [Bốn ca consumer](../../android/app/src/test/java/com/socialpilot/app/data/repository/analytics/AnalyticsDashboardConsumerTest.java).

**Mapping IG:** follower GAUGE/COUNT/LATEST; reach CUMULATIVE/COUNT/**DISTINCT_TOTAL/LIFETIME** theo từng media. Không cộng reach giữa bài thành unique reach, không dùng reach/views làm impressions. Definition ID pin aggregation/window dù source port không có enum aggregation riêng; adapter production cần registry đã review. IG-11 chỉ chứng minh UNSUPPORTED cho đúng media/query/API version; thiếu binding giữ UNVERIFIED. Không suy ER khi thiếu impression hoặc component tương thích. [Đầu vào SP-045 tại b6a59ee](https://github.com/nigPeter2503/Social_Pilot/blob/b6a59ee7767e6cf6d86bb09d0c7b3327bfca40b7/docs/handoffs/sp-045-analytics-input.md).

## Dùng query trong ViewModel

Ví dụ tái lập scope, kỳ, cohort và definition của ca IG trong `AnalyticsProjectorRegressionTest`. `post` là ID PublishedPost **nội bộ**. Trên ứng dụng lấy channelId/revision từ Channel hiện hành, lấy definitionId từ registry và cố định cohort theo bộ lọc người dùng. Khoảng sự kiện là [start, end) theo zone đã chọn. Mẫu tại end vẫn có thể làm mốc cuối tính delta. Mỗi channel có một scope, revision > 0, start < end, zone hợp lệ và topLimit > 0. Cohort rỗng chọn 0 bài.

```java
DashboardQuery.ChannelScope ig =
        new DashboardQuery.ChannelScope(Platform.INSTAGRAM, "channel-ig", 2);
DashboardQuery q = new DashboardQuery(List.of(ig), "rate-v1",
        Instant.parse("2026-11-01T17:00:00Z").toEpochMilli(),
        Instant.parse("2026-11-08T17:00:00Z").toEpochMilli(), "UTC",
        new DashboardQuery.Cohort(List.of("post")), DashboardQuery.Grouping.TOPIC, 1);

Cancelable observation = repo.observeCachedDashboard(q, (DashboardData data) -> {
    boolean fixture = data.source() == AnalyticsTypes.EvidenceSource.MO_PHONG;
    for (DashboardData.ChannelCard card : data.channelCards()) {
        AnalyticsTypes.MetricReading follower = card.follower();
        String label = follower.value() == null
                ? follower.missingReason().name() : follower.value().toString();
        showCardIfCurrent(q, card.scope(), label, fixture, follower.freshness());
    }
});
// Khi kéo làm mới
Cancelable refresh = repo.refresh(q, (AnalyticsRepository.RefreshResult r) ->
        showOutcomeIfCurrent(q, r.outcome(), r.errors()));
// Khi bỏ query hoặc đóng ViewModel
observation.cancel();
refresh.cancel();
```

Các hàm `show…IfCurrent` do UI triển khai, chuyển việc vẽ sang main thread và kiểm generation của query tại thời điểm vẽ. `observeCachedDashboard(DashboardQuery, DashboardObserver)` trả `Cancelable`, phát `DashboardData` qua `onChanged` trên callback worker. Đăng ký lại chỉ đọc cache. `refresh(DashboardQuery, RefreshCallback)` trả handle riêng, `onComplete(RefreshResult)` nhận tối đa một terminal (SUCCESS, PARTIAL hoặc FAILED). Giữ handle khi refresh còn thuộc ViewModel, hủy observer khi tháo view và vô hiệu generation trước khi hủy/đổi query. Cache và dữ liệu hiển thị đến từ observer. [Interface](../../android/app/src/main/java/com/socialpilot/app/domain/contract/analytics/AnalyticsRepository.java) · [DTO](../../android/app/src/main/java/com/socialpilot/app/domain/contract/analytics/DashboardData.java)

| Ca fixture | SP-047 đọc và hiển thị |
|---|---|
| Follower IG 0, reach lifetime của media 0 | Hiện cả hai số 0. Chọn reading trong `postMetrics().readings()` của từng bài theo definitionId `ig-media-reach-lifetime-v27`. Provenance thuộc scope IG. Mẫu impression Facebook 400 không đi vào scope IG. ER của bài null, danh sách top rỗng. Query theo reach cũng cho top rỗng và giá trị nhóm null. |
| Thiếu quyền và thiếu mẫu số | Reading null có `missingReason() == PERMISSION_REQUIRED` ở fixture thiếu quyền, lỗi kèm action RESOLVE_PERMISSION. Ở calculator, nếu cả share và impression bị omitted, ER null với `reason() == MISSING_COMPONENT` và `missingDetails()` ghi PROVIDER_OMITTED cho cả hai. Mẫu số quan sát được bằng 0 cho ZERO_DENOMINATOR khi các thành phần còn lại hợp lệ. |
| Cache cũ khi refresh lỗi | Ca stale/offline giữ số 100 cùng timestamp thành công cũ, lần thử sau 73 giờ ghi NETWORK_ERROR. Đọc `offline()/syncState()/errors()` cùng `freshness().isStale()/lastSuccessAt()` của từng metric và `lastAttemptAt()` của dashboard. 48h là ngưỡng fixture. |
| Coverage thiếu | Subtotal tuần giữ giá trị 20 của bài đủ mẫu và PARTIAL_COVERAGE, `coverage().excludedReasons()` chứa bài bị loại. Đọc `includedCount()/totalCount()`, `temporal()/cohort()`, `bounds().actualStart()/actualEnd()` và `sourceSnapshotIds()` của giá trị. Hai trục cho phép đủ số bài nhưng chỉ có mốc bên trong kỳ. |

Bảng dùng dữ liệu MO_PHONG. Với từng metric, `provenance()` xác định source, scope, snapshot và thời điểm. Nghị sẽ nối DAO và đối soát T1/T2 khi có cặp snapshot thật tương thích. [Regression IG và coverage](../../android/app/src/test/java/com/socialpilot/app/data/repository/analytics/AnalyticsProjectorRegressionTest.java) · [Regression mẫu số](../../android/app/src/test/java/com/socialpilot/app/domain/analytics/AnalyticsCalculatorRegressionTest.java) · [Consumer fixture](../../android/app/src/test/java/com/socialpilot/app/domain/contract/analytics/AnalyticsConsumerContractTest.java) · [Regression vòng đời và cache](../../android/app/src/test/java/com/socialpilot/app/data/repository/analytics/AnalyticsRepositoryRegressionTest.java)

**HO-DTO hiện hành:** đã có ACK consumer đúng `2012752`/`c2da648` ở đầu tài liệu; bản PARTIAL mới và production vẫn chờ xác nhận. ACK #67 chỉ áp dụng SP-020 W1. [ACK draft-2](https://github.com/nigPeter2503/Social_Pilot/pull/67#pullrequestreview-5847811439) · [Approval tiếp theo trên #67](https://github.com/nigPeter2503/Social_Pilot/pull/67#pullrequestreview-5862180313)

## Hồ sơ thiết kế và kiểm chứng draft 0.1.0

Các mốc base, blob, kết quả chạy và trạng thái local bên dưới thuộc hồ sơ ngày 19/11. Phần trên ghi revision hiện hành cho SP-047.

**Draft 0.1.0 · 19/11/2026 · Người giao Tô Lãng Nghị · Người xác nhận Đỗ Phước Thiện qua HO-DTO**

| Mốc | Revision |
|---|---|
| Base đối chiếu | `origin/main` cục bộ và merge-base `230c2a6ebcd8785288e57764c4ebb5fb6bbd4afa` |
| HEAD trước khi sửa | `7931f4dc5325f0550241f7d5188521bccec63b59`, nhánh `feat/sp-019-linkedin-comments-metrics-spike` |
| Gói đầu vào | [SP-045 (bàn giao analytics) v0.1.0](sp-045-analytics-input.md), Git blob `a34ce21e3920485e1a656335c382fce0fc45601d` |
| Hợp đồng và fixture | [SP-020 draft-4 §§3, 6–7, 10](../analytics-data-contract.md), tích hợp tại `e51dfc3375e389ed39b7ae18d44a8c7525d88cc1`. [analytics-examples.json](../contracts/analytics-examples.json), Git blob `559b2f380045c293e69ffb162ddcadbea86fedf1` |
| Đối chiếu | [SP-012 §§3, 5](../room-schema-contract.md), [brief Thiện SP-047](../TaskBrief/W2-W3/tv3-thien-taskbrief.md#sp-047), [CONTRIBUTING.md](../../CONTRIBUTING.md). Các tài liệu và fixture đối chiếu giống nhau giữa base và HEAD. |

Changelog **0.1.0** gồm chữ ký Java, DTO và consumer stub cho SP-047/#49. Draft nằm trong working tree. Lúc kiểm tra ban đầu, chỉ gói SP-045 (bàn giao analytics) còn untracked. Query/DAO, công thức và đối chiếu live thuộc SP-046 (repository/query và đối soát live).

Git blob IDs để đối chiếu bản draft khi ACK (`git hash-object <file>`):

| Java | Blob |
|---|---|
| `AnalyticsRepository.java` | `1c59d6c3f7b118111f45f3dfec5dfbc8483c7288` |
| `AnalyticsTypes.java` | `7df905ca32aa8c9888287daf26e5d09012e66e8f` |
| `DashboardData.java` | `7f9b45ec5f7c05fb8161968842573ac5b7f79288` |
| `DashboardQuery.java` | `ec977976d4e8010b3258923854543eba6a6927f2` |
| `AnalyticsConsumerContractTest.java` | `91840980707de52fe88350db3cb60092f550622a` |

## API và vòng đời

[AnalyticsRepository.java](../../android/app/src/main/java/com/socialpilot/app/domain/contract/analytics/AnalyticsRepository.java) dùng lại `Cancelable` từ Android. Callback lồng trong interface theo convention `SocialConnector.Result`. Hợp đồng đề xuất cho implementation SP-046 (repository/query):

~~~java
Cancelable observeCachedDashboard(DashboardQuery query, DashboardObserver observer);
Cancelable refresh(DashboardQuery query, RefreshCallback callback);
~~~

1. Hai lời gọi trả handle ngay. Cache observe đọc projection cục bộ của query chính xác, phát trạng thái ban đầu rồi phát thay đổi tuần tự trên worker. Khi chưa có projection, observer nhận `UNAVAILABLE`. ViewModel chuyển callback sang main thread trước khi cập nhật state UI.
2. Refresh là thao tác tường minh do ViewModel sở hữu. Một lần refresh cập nhật trạng thái `LOADING`, `lastAttemptAt` và kết quả vào projection. Callback terminal phát tối đa một lần sau khi kết quả được công bố cho cache observe. `RefreshResult` chứa query, `SUCCESS/PARTIAL/FAILED` và danh sách lỗi. `DashboardData` đi qua observer. UI dùng callback terminal cho trạng thái của thao tác, đọc dữ liệu hiển thị từ observer.
3. `cancel()` idempotent. Hủy observe gỡ đăng ký, hủy refresh dừng công việc riêng có thể dừng và chặn callback đến sau. Callback đã bắt đầu có thể đã xếp việc lên main thread. ViewModel đối chiếu query và generation/attempt **khi nhận việc trên main thread**, tăng generation trước khi cancel hoặc thay query. Repository kiểm tra cancel, attempt hiện hành, ownership và connectionRevision trước publish/commit. Kết quả đã commit vẫn được đọc qua cache theo chính sách scope hiện hành.
4. ViewModel giữ query/filter và handle refresh theo query qua xoay màn hình. Gắn lại observer chỉ đọc cache. Trigger refresh đến từ hành động người dùng hoặc chính sách tải lần đầu được lưu riêng trong ViewModel. Nếu refresh trùng query đang chạy, ViewModel giữ một attempt. Khi đổi filter, scope hoặc revision, hủy handle của query cũ và thay token trước khi đăng ký query mới. Sau restart, dựng lại query từ filter đã lưu và revision hiện hành; đọc cache trước khi xét chính sách tải.

## Query, DTO và cách hiển thị

| Chữ ký | Cách dùng trong draft 0.1.0 |
|---|---|
| [DashboardQuery](../../android/app/src/main/java/com/socialpilot/app/domain/contract/analytics/DashboardQuery.java) | Scope tường minh gồm `Platform`, channel ID nội bộ và connection revision dương lấy từ `ChannelInfo`. IDs opaque, phân biệt hoa/thường. Scope và cohort là danh sách bất biến, có thứ tự trong cache key. Giữ cùng thứ tự khi tái lập cùng filter. Cohort chứa ID PublishedPost nội bộ cố định, danh sách rỗng nghĩa là không có bài. Repository cần kiểm tra các bài thuộc scope. Cache key dùng mọi trường của query và tách namespace nguồn. |
| Definition và kỳ | `metricDefinitionId` pin nghĩa và unit trong registry SP-020/SP-045 (bàn giao analytics). Đổi nghĩa hoặc measurement window thì dùng đúng definition tương ứng. Java dùng UTC epoch millisecond `long/Long`, fixture wire dùng ISO UTC hậu tố Z. Query truyền `zoneId` IANA. Đề xuất mặc định `Asia/Ho_Chi_Minh`; thứ Hai đến Chủ nhật theo zone, lọc sự kiện nửa mở [start, end). Snapshot đúng end có thể là mốc delta. Đổi zone tạo kỳ/query mới, giữ timestamp mẫu. |
| Grouping và top | `TOPIC` theo primaryTopicId, `FORMAT` theo format, `PUBLISH_HOUR` theo zone query. Metadata thiếu vào `UNKNOWN`. `topLimit > 0`. Số hợp lệ giảm dần, rồi publishedAt giảm dần với null cuối, rồi platform/channelId/postId tăng dần theo chuỗi. Giữ số âm, loại null có lý do qua `topPostExclusions`. So sánh và nhóm theo scope khi chưa có bằng chứng tương thích giữa nền tảng/definition. |
| [DashboardData](../../android/app/src/main/java/com/socialpilot/app/domain/contract/analytics/DashboardData.java) | Echo query, nhãn nguồn, card kênh, post metrics, chart, tương tác tuần theo scope, top, nhóm và goal read-side. Lists luôn có giá trị, có thể rỗng. `UNAVAILABLE` chưa có projection usable, `EMPTY` là lần đọc thành công không có dữ liệu và `CONTENT` có kết quả render được, kể cả card thiếu quyền. Kết hợp content state với `IDLE/LOADING/ERROR`, offline và errors. Nếu lỗi mà không có kết quả dùng được, ưu tiên hiển thị lỗi; cache có dữ liệu vẫn render cùng lỗi và timestamp cũ. |
| [AnalyticsTypes](../../android/app/src/main/java/com/socialpilot/app/domain/contract/analytics/AnalyticsTypes.java) | `MetricReading` và `CalculatedValue` có definition riêng. Các số đếm/delta dùng `Long`, phần trăm dùng `Double` hữu hạn. Metric null luôn có `MissingReason`; số 0 có reason null. Calculation null có `CalculationReason` và các missing details theo component. Kết quả có số cho phép reason null hoặc `PARTIAL_COVERAGE` đối với subtotal thiếu bài. Thiện chỉ format số/thời gian và render theo reason. |
| Bounds và coverage | `CalculatedValue.bounds()` giữ requested và actual UTC. Thiếu cặp thì actual bounds đều null. Cặp có thật nằm trong [start, end], và dùng observedAt/basis tương thích. `temporal` và `cohort` độc lập, mỗi trục có FULL/PARTIAL/NONE. Trả includedCount/totalCount và excludedReasons theo scope/post. Với partial tuần của fixture, reason null là kết quả chọn hai mốc bên trong kỳ, còn temporal là PARTIAL. `PARTIAL_COVERAGE` gắn với subtotal cohort thiếu bài. |
| Freshness và lỗi | `Freshness.isStale` và `lastSuccessAt` đều nullable khi chưa có mẫu thành công dùng được. Fixture pin stale nghiêm ngặt `age > 48h`. Giữ freshness/collectedAt theo từng số đo, dùng lastAttemptAt để ghi lần thử gần nhất. Lỗi đọc giữ `SocialError.code/nextAction`, `SocialOperation`, scope và `retryAfterAt` nếu biết. Lỗi cache có mã cục bộ, scope/operation có thể null cho lỗi toàn query. Offline là tín hiệu riêng. |
| Nguồn và gap | `provenance()` là danh sách sample đã che cho từng giá trị, gồm snapshot/collection/scope/entity/definition, nguồn, sourceRef, observedAt/basis, collectedAt, provider bounds/version nếu có. `sourceSnapshotIds()` trích ID duy nhất theo thứ tự từ chính danh sách. Điểm chart có `observedAt` và reading; gap dùng mốc lưới kỳ vọng, reading null-value/`NOT_SYNCED`, không có snapshot. Dán nhãn `MO_PHONG` trên fixture UI/test. Production history dùng `API_THAT`. |
| Chỗ cho goal | `GoalView` dùng count cho baseline/target trong hai goal kind hiện có. `GoalProgress` dùng calculated values cho actual/rawProgress, giữ bounds/reason/coverage/provenance theo từng số. Coverage thiếu hoặc stale cho completion `UNKNOWN`. SP-049 xác nhận phần UI goal đầy đủ. |

Trường nullable ngoài các trường trên gồm title/publishedAt/primaryTopicId/format trong metadata, label nhóm, timestamp chưa biết, providerPeriodStart/End và providerApiVersion. Với các phép tính trên mẫu, source IDs là ID snapshot thực có trong input. Case ID của dòng fixture dùng để chọn đầu vào test.

## Fixture cho consumer và evidence

Consumer stub và fake repository nằm trong [AnalyticsConsumerContractTest.java](../../android/app/src/test/java/com/socialpilot/app/domain/contract/analytics/AnalyticsConsumerContractTest.java). Stub đọc trực tiếp JSON SP-020 và payload markdown của SP-045 (bàn giao analytics), nên hai handoff cần đi cùng checkout để chạy lại. Query fixture dùng FACEBOOK/`mock-page-a`/revision 1, definition `mock-followers-v1`, cohort rỗng, TOPIC, topLimit 5 và kỳ từ 01/11 17:00Z đến 08/11 17:00Z. Dùng class test trong Android Studio hoặc chạy task dưới đây.

| Ca pin của SP-045 (bàn giao analytics) | Expected mà consumer đọc |
|---|---|
| `no-history` + `dashboard-empty`; `available-zero` | EMPTY/IDLE, danh sách rỗng, hai trục NONE. Mẫu 0 đọc thành `Long(0)` với reason null. |
| `permission`, `permission-revoked-after-verification`, `permission-error` | Null/`PERMISSION_REQUIRED`, action `RESOLVE_PERMISSION`. |
| `one-snapshot`, `growth-gap`, `follower-delta-missing-baseline` | Gap tại query start, không snapshot/value; delta null/`MISSING_BASELINE` với actual bounds null. |
| `partial-week`, `full-boundaries`, `decrease` | Partial dùng 02/11 17:15Z và 07/11 17:20Z, cohort độc lập. Projection full dùng `followers-v1`, delta −5 và growth −5%; fixture follower DTO dùng `mock-followers-v1`. |
| `dashboard-stale-refresh`, `network-error`, `dtoExamples.snapshot` | Giữ 100 và sample time 08/11 17:00Z từ `mock-snapshot-1`/`mock-collection-1`, provenance `MO_PHONG`/`analytics-draft-4-snapshot`. Lần thử 11/11 18:00Z cách 73 giờ, báo stale/offline/`NETWORK_ERROR` với `RETRY_WHEN_SAFE`. |
| Vòng đời trên `dashboard-empty` | Hai hàng đợi điều khiển bằng tay kiểm reattach giữ một lượt refresh đã yêu cầu, cancel chặn callback, đổi query/revision bỏ cả callback cache và refresh đã xếp lên main. |

### Lệnh và kết quả thực tế

- Gốc repo, `node docs/contracts/verify-analytics-examples.cjs` với Node v24.19.0. Đạt **75/75** fixture SP-020. Chạy thêm khối PowerShell §3 của SP-045 (bàn giao analytics) từ gốc repo, đạt **4/4** view `dashboard-empty`, `dashboard-stale-refresh`, `growth-gap` và `follower-delta-missing-baseline`.
- Gốc repo, `javac --release 17 -encoding UTF-8 -cp $classPath -d $classes @sources` rồi `java -cp "$classes;$classPath" org.junit.runner.JUnitCore com.socialpilot.app.domain.contract.analytics.AnalyticsConsumerContractTest`. Temurin 21.0.13, Gson 2.11.0, JUnit 4.13.2 và Hamcrest 1.3 từ cache Gradle. Nguồn gồm bốn tệp analytics mới, `Cancelable`, `Platform`, `SocialError`, `SocialOperation` và stub. Compile đạt, JUnit **7/7**.
- Tại `android/`, `.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` với Temurin 21.0.13 và Android SDK 36. **BUILD SUCCESSFUL**. APK debug đã tạo, Android UNIT **67/67** gồm stub **7/7**, lint **0 lỗi, 6 cảnh báo** ngoài tệp analytics. Lượt đầu bị chặn khóa cache Gradle trong sandbox, lượt dùng JDK 25 lỗi major version 69. Lượt đầu trên JDK 21 phát hiện `Files.readString` trong stub không có trong classpath Android. Đổi sang `Files.readAllBytes` và UTF-8 rồi chạy đạt.
- Gốc repo, `git diff --check` và `git diff --cached --check` đạt. Kiểm thêm từng tệp draft untracked bằng `git diff --no-index --check -- /dev/null <file>`, đạt.

## HO-DTO — lịch sử yêu cầu xác nhận ngày 19/11

Thiện đã xác nhận draft-2 cho W1. ACK cho SP-046 (hợp đồng DTO) draft 0.1.0 vẫn chờ. GitHub issue #48/#49 chưa truy cập được trong lượt kiểm tra. [SP-020 §10](../analytics-data-contract.md#10-xác-nhận-review-và-điểm-cần-chốt) · [#48](https://github.com/nigPeter2503/Social_Pilot/issues/48) · [#49](https://github.com/nigPeter2503/Social_Pilot/issues/49)

1. **Chữ ký và vòng đời.** Chốt callback nền + hai handle với ViewModel giữ refresh qua reattach, hay đổi observe sang LiveData. Chốt nguồn trigger tải lần đầu, thread dispatch và guard khi đổi query/revision.
2. **Query và kỳ.** Chốt scope/revision truyền tường minh, cohort ID cố định, thứ tự cache key, kỳ tuần và zone mặc định. Xác nhận grouping, topLimit và tie-break/filters mà UI cần bảo toàn.
3. **Nullable, chart và goal.** Chốt Java epoch milliseconds, `Long/Double`, content state, cách render null/permission/0, gap theo lưới và progress với `UNKNOWN`.
4. **Freshness và lỗi.** Chốt 48h cho fixture, ngưỡng production theo lịch thu sau này, retryAfterAt, action, lỗi theo scope/operation và cách hiển thị stale + lỗi + offline cùng cache.
5. **Coverage và provenance.** Chốt actual bounds, hai trục coverage, excluded reasons/subtotal, evidence inline và cách trình bày metric khác scope/definition. Xác nhận nhãn `MO_PHONG`.

Thiện có thể dựng SP-047 từ draft và fixture. Khi đưa bản draft vào PR, dùng nhánh `feat/sp-046-analytics-repository` từ main theo CONTRIBUTING và đưa gói đầu vào SP-045 (bàn giao analytics) đi cùng stub. Lưu ACK kèm version và commit SHA hoặc blob IDs của bản được review trong HO-DTO. SP-046 (hợp đồng DTO) và HO-DTO giữ trạng thái **Chờ Thiện xác nhận** tới lúc có bằng chứng đó.
