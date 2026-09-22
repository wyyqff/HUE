-- 河北工程大学单校部署。仅调整学校启用状态，不删除原始学校数据。
USE nowl;
UPDATE school_info SET status = 0 WHERE school_code <> 'HEBEU';
INSERT INTO school_info (school_code, school_name, campus_code, campus_name, status)
VALUES ('HEBEU', '河北工程大学', 'HEBEU_MAIN', '河北工程大学', 1)
ON DUPLICATE KEY UPDATE school_name = VALUES(school_name), campus_name = VALUES(campus_name), status = 1;
