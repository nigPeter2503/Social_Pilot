# SP-066 · Nội dung bàn giao cho PR

Task: [SP-066 / #91](https://github.com/nigPeter2503/Social_Pilot/issues/91) · F6.B1/B2/B4, F7.B1/B2/B3

Reviewers: `@dat532006`, `@PhuocThien06`

### Thay đổi và phạm vi review

- Bổ sung [manifest](verification-manifest.md), [index](evidence/index.md), 19 JSON response đã làm sạch và 27 ảnh (ba ảnh phân trang chỉ có bản đã che), cùng parser/fixture offline.
- Đối chiếu IG-01–13: comment/reply đúng parent, terminal page, follower 1, reach 0/lifetime, media fields 1/6/0; `impressions` code 100 được ghi `UNSUPPORTED` riêng cho metric.
- IG-12 token A/B→từng request và IG-13 mốc T1/T2 được owner trực tiếp xác nhận ngày 22/11/2026; ảnh/JSON đã publish không tự chứa binding hoặc giờ request. Review giúp ranh giới này và cách phân loại `PERMISSION_REQUIRED`/`INSIGHT_EMPTY`/`UNSUPPORTED`.

### Kiểm tra

- `node docs/api-verification/instagram/verify-fixtures.cjs` — PASS (`MO_PHONG`).
- `node docs/api-verification/instagram/verify-api-fixtures.cjs` — PASS (`FIXTURE_TU_API_DA_CHE`).
- `node docs/contracts/verify-analytics-examples.cjs` — PASS 75 checks (`MO_PHONG`).
- `git diff --check` — PASS.

### Bàn giao và SP phụ thuộc

- [SP-065 / #90](https://github.com/nigPeter2503/Social_Pilot/issues/90), Minh: pin role/Page task/target, Channel ID, `connectionRevision` và grant production.
- [SP-041 / #43](https://github.com/nigPeter2503/Social_Pilot/issues/43), [SP-042 / #44](https://github.com/nigPeter2503/Social_Pilot/issues/44), [SP-043 / #45](https://github.com/nigPeter2503/Social_Pilot/issues/45): dùng ID/parent và ca phân trang cho inbox/reply; app/Room chưa được chứng minh bởi PR này.
- [SP-045 / #47](https://github.com/nigPeter2503/Social_Pilot/issues/47), [SP-046 / #48](https://github.com/nigPeter2503/Social_Pilot/issues/48), [SP-047 / #49](https://github.com/nigPeter2503/Social_Pilot/issues/49): nhận mapping follower/reach, provenance `MEDIA_FIELD`, 0/null và giới hạn ER; cần snapshot/readback production, ACK DTO/metric semantics từ Thiện. Đạt nhận thay đổi enum/schema `INSIGHT_EMPTY` nếu đưa vào Room.

### Điều kiện merge

- [ ] Hai reviewer approve; nhận xét bắt buộc và CI trên head PR đạt.
- [ ] Các owner xác nhận contract và phần bàn giao production tương ứng.
