# SP-010 — real photo and custom alt-text write/read test

## Result and provenance

**REAL PASS — manual Page-photo publishing and exact custom alt-text read-back.** On 12/11/2026 Minh supplied the successful creation response, then the GET response and Explorer screenshot, and confirmed “đã kiểm thử link và xác nhận”. The custom text read back exactly matches the submitted value. The owner opened the returned Facebook link and confirmed the real photo; the agent did not independently open the Facebook post or send the provider requests.

The owner approved the minimal image/alt-text capability test before preparation. The exact sample image, caption, alt text, intended Page and `published=true` request were presented before the owner manually sent the request. No exact content-review timestamp is inferred.

## Environment and input

- Owner/tester: Ngô Gia Minh — TV1; date recorded: 12/11/2026, Asia/Bangkok.
- Meta Graph API Explorer, version `v27.0`; app Social Management MD (`1650819950076669`).
- Intended Page: Demo Page MD 24-31 (`1427548503779474`).
- Repository context when recording: `21259c30e68eda7f65b8f4d21f103e5ed19633c3`. The requests ran directly in Explorer, not through Android publishing code or the backend.
- Explorer shows five selected permissions: `pages_show_list`, `business_management`, `pages_read_engagement`, `pages_read_user_content`, `pages_manage_posts`. The selected list is not an independent token-scope inspection; no token is recorded.
- Test image URL: `https://placehold.co/1200x630/png?text=SocialPilot%20SP-010`. The agent inspected the prepared 1200x630 PNG: light gray background and centered text SocialPilot SP-010. This is the prepared asset, not an independently captured copy of the published image.

## Creation request and owner-supplied response

`POST /v27.0/1427548503779474/photos`

```json
{
  "url": "https://placehold.co/1200x630/png?text=SocialPilot%20SP-010",
  "message": "Kiểm thử đăng ảnh và alt text SocialPilot - SP-010 - 12/11/2026.",
  "alt_text_custom": "Ảnh nền xám nhạt, ở giữa có dòng chữ SocialPilot SP-010.",
  "published": true
}
```

The owner's pasted Explorer links preserved this method, path, version and parameter set. The response supplied in chat was:

```json
{
  "id": "122109617541532657",
  "post_id": "1427548503779474_122109617565532657"
}
```

## GET and confirmation

`GET /v27.0/122109617541532657?fields=id,name,alt_text_custom,alt_text,link,created_time`

The [safe read-back JSON](sp-010-photo-alt-text-readback-2026-11-12.json) is a transcription of the owner's pasted response, with Markdown link wrappers removed from string values. The screenshot independently shows the matching photo ID, caption, both alt-text fields, timestamp, GET version/path and intended app/Page. Explorer displays 372 ms for this GET; HTTP status is not shown.

- Photo ID: `122109617541532657`.
- Post ID from creation: `1427548503779474_122109617565532657`.
- `alt_text_custom` and `alt_text`: `Ảnh nền xám nhạt, ở giữa có dòng chữ SocialPilot SP-010.`
- `created_time`: `2026-11-12T07:15:30+0000`, equivalent to **12/11/2026 14:15:30 UTC+07:00**. This is the provider's creation time, not a fabricated approval or capture time.
- [Returned photo link, checked and confirmed by the owner](https://www.facebook.com/photo.php?fbid=122109617541532657&set=a.122109617559532657&type=3).

The earlier `publish_actions` error was superseded when the owner corrected the attempt and supplied this success. The exact earlier request context was not established, so that error is not evidence of unsupported Page-photo or alt-text capability.

## Secret handling and acceptance boundaries

- The screenshot rendered in chat has its token covered, but the inspected local source PNG still shows a credential fragment. The raw PNG is **not committed**. Its SHA-256 is `36D83D51ECDB821C6D76F1AB97DC5D24AA2FBCAB4792A41EA332266DAD3A01B9`; only the safe response transcription and observations above are retained in Git. The agent did not edit or recreate the screenshot.
- This verifies one PNG via a public URL and explicit custom alt text on this Page/app with v27.0. It does not establish every format, size, multi-photo flow or platform capability.
- The SP-010 capability finding is now **image upload/publish: observed success; custom alt-text write/read: observed success**. No scope waiver or unsupported-API claim is needed for this finding.
- Full editor generation/editing/persistence of alt text remains [SP-039 / #41](https://github.com/nigPeter2503/Social_Pilot/issues/41); app-side publishing remains [SP-025 / #27](https://github.com/nigPeter2503/Social_Pilot/issues/27). These links describe existing task boundaries, not a new assignment or proof those tasks are complete. Full F4.N3, F5 and production F1.B3 are not accepted by a manual Explorer test.
- The remaining SP-010 close-out is final evidence/scope review, checks on the final head, two human approvals and merge. No issue/Project completion is inferred from this record alone.
