-- MO_PHONG. Seed only a fresh v1 test database before running its assigned migration.
INSERT INTO drafts(id, topic, createdAt) VALUES (7, 'MO_PHONG', 1795060800000);
INSERT INTO drafts(id, topic, createdAt) VALUES (11, 'MO_PHONG', 1795060800000);
INSERT INTO drafts(id, topic, createdAt) VALUES (42, NULL, 1795060800001);
INSERT INTO drafts(id, topic, createdAt) VALUES (50, 'MO_PHONG', 1795060800002);
DELETE FROM drafts WHERE id = 50;
