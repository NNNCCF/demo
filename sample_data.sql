-- ============================================================
-- 卓凯安伴 · 批量示例数据（扩充版）
-- 社区 4 个 + 医疗机构 4 个，监护人 20 名，被监护人 40 名，设备 40 台
--
-- 执行前提：
--   1. 已连接 health_iot 数据库
--   2. ID 段（101~108, 301~320, 10001~10040）未被占用
--   3. 密码哈希请替换为真实 BCrypt(cost=10) 值（见下方说明）
--
-- 密码说明：
--   示例哈希对应密码 Admin@123
--   替换方式：在线工具 https://bcrypt-generator.com (Cost=10)
--   或从已有账号 SELECT password_hash FROM sys_user WHERE username='admin';
-- ============================================================

USE health_iot;

-- ============================================================
-- 清理旧示例数据（幂等执行，每次运行前自动清理）
-- ============================================================
UPDATE device SET target_id = NULL WHERE device_id REGEXP '^DEV-(CF|YZ|XH|JY)-';
DELETE FROM ward            WHERE member_id  BETWEEN 10001 AND 10040;
DELETE FROM device          WHERE device_id  REGEXP '^DEV-(CF|YZ|XH|JY)-';
DELETE FROM family_guardian WHERE family_id  BETWEEN 301  AND 320;
DELETE FROM family          WHERE id         BETWEEN 301  AND 320;
DELETE FROM client_user     WHERE mobile     LIKE '13801110%';
DELETE FROM sys_user        WHERE username   IN ('wanbolinComm','yingzeComm','xinghuaComm','jinyuanComm',
                                                  'wanbolinHosp','yingzeHosp','xinghuaHosp','jinyuanHosp');
DELETE FROM organization    WHERE id         BETWEEN 101  AND 108;

-- ============================================================
-- 一、机构（4 社区 + 4 医疗机构）
-- ============================================================
INSERT INTO organization (id, name, type, region, contact_phone, status, created_at) VALUES
(101, '万柏林区长风街道社区养老服务中心', 'COMMUNITY',          '万柏林区', '0351-6011101', 'ENABLED', NOW()),
(102, '迎泽街道社区养老服务中心',         'COMMUNITY',          '迎泽区',   '0351-6011202', 'ENABLED', NOW()),
(103, '杏花岭区五一路社区养老服务中心',   'COMMUNITY',          '杏花岭区', '0351-6011303', 'ENABLED', NOW()),
(104, '晋源区义井街道社区养老服务中心',   'COMMUNITY',          '晋源区',   '0351-6011404', 'ENABLED', NOW()),
(105, '太原市第二人民医院老年科',         'MEDICAL_INSTITUTION', '万柏林区', '0351-6022101', 'ENABLED', NOW()),
(106, '山西省人民医院老年科',             'MEDICAL_INSTITUTION', '迎泽区',   '0351-6022202', 'ENABLED', NOW()),
(107, '杏花岭区人民医院',                 'MEDICAL_INSTITUTION', '杏花岭区', '0351-6022303', 'ENABLED', NOW()),
(108, '晋源区中医院',                     'MEDICAL_INSTITUTION', '晋源区',   '0351-6022404', 'ENABLED', NOW());

-- ============================================================
-- 二、机构登录账号（sys_user，每机构一个，role=GUARDIAN）
-- ============================================================
INSERT INTO sys_user (username, password_hash, role, region, org_id, phone, status, created_at, updated_at) VALUES
('wanbolinComm', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', '万柏林区', 101, '13500001001', 'ENABLED', NOW(), NOW()),
('yingzeComm',   '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', '迎泽区',   102, '13500001002', 'ENABLED', NOW(), NOW()),
('xinghuaComm',  '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', '杏花岭区', 103, '13500001003', 'ENABLED', NOW(), NOW()),
('jinyuanComm',  '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', '晋源区',   104, '13500001004', 'ENABLED', NOW(), NOW()),
('wanbolinHosp', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', '万柏林区', 105, '13500001005', 'ENABLED', NOW(), NOW()),
('yingzeHosp',   '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', '迎泽区',   106, '13500001006', 'ENABLED', NOW(), NOW()),
('xinghuaHosp',  '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', '杏花岭区', 107, '13500001007', 'ENABLED', NOW(), NOW()),
('jinyuanHosp',  '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', '晋源区',   108, '13500001008', 'ENABLED', NOW(), NOW());

-- ============================================================
-- 三、监护人（client_user，role=GUARDIAN，共 20 名）
-- ============================================================
INSERT INTO client_user (name, mobile, password, role, org_id, created_at, updated_at) VALUES
-- 万柏林区 5 名
('张明', '13801110001', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 101, NOW(), NOW()),
('李华', '13801110002', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 101, NOW(), NOW()),
('王芳', '13801110003', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 101, NOW(), NOW()),
('赵强', '13801110004', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 101, NOW(), NOW()),
('陈静', '13801110005', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 101, NOW(), NOW()),
-- 迎泽区 5 名
('刘洋', '13801110006', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 102, NOW(), NOW()),
('杨磊', '13801110007', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 102, NOW(), NOW()),
('黄丽', '13801110008', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 102, NOW(), NOW()),
('吴勇', '13801110009', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 102, NOW(), NOW()),
('周娟', '13801110010', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 102, NOW(), NOW()),
-- 杏花岭区 5 名
('徐鹏', '13801110011', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 103, NOW(), NOW()),
('孙燕', '13801110012', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 103, NOW(), NOW()),
('马超', '13801110013', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 103, NOW(), NOW()),
('胡杰', '13801110014', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 103, NOW(), NOW()),
('林莉', '13801110015', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 103, NOW(), NOW()),
-- 晋源区 5 名
('何涛', '13801110016', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 104, NOW(), NOW()),
('郭敏', '13801110017', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 104, NOW(), NOW()),
('梁伟', '13801110018', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 104, NOW(), NOW()),
('曹雪', '13801110019', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 104, NOW(), NOW()),
('高峰', '13801110020', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'GUARDIAN', 104, NOW(), NOW());

-- ============================================================
-- 四、家庭（共 20 个，每社区 5 个）
-- ============================================================
INSERT INTO family (id, name, address, org_id, created_at) VALUES
-- 万柏林区长风街道（101）
(301, '张家', '万柏林区长风大街168号长风家园5栋2单元301室',   101, NOW()),
(302, '李家', '万柏林区长风大街182号阳光花园3栋1单元402室',   101, NOW()),
(303, '王家', '万柏林区迎新街56号碧桂园7栋3单元201室',        101, NOW()),
(304, '赵家', '万柏林区长风大街210号长风丽华苑2栋4单元101室', 101, NOW()),
(305, '陈家', '万柏林区友谊南路78号裕隆苑4栋2单元302室',      101, NOW()),
-- 迎泽街道（102）
(306, '刘家', '迎泽区迎泽大街388号迎泽苑1栋3单元201室',       102, NOW()),
(307, '杨家', '迎泽区桃园南路67号桃园小区3栋1单元402室',      102, NOW()),
(308, '黄家', '迎泽区迎泽大街256号迎泽世家6栋2单元301室',     102, NOW()),
(309, '吴家', '迎泽区并州南路145号并州苑2栋4单元102室',       102, NOW()),
(310, '周家', '迎泽区平阳路88号平阳小区5栋1单元203室',        102, NOW()),
-- 杏花岭五一路（103）
(311, '徐家', '杏花岭区五一路198号五一苑3栋2单元301室',       103, NOW()),
(312, '孙家', '杏花岭区解放路56号解放小区4栋1单元202室',      103, NOW()),
(313, '马家', '杏花岭区上北关街78号北关苑2栋3单元401室',      103, NOW()),
(314, '胡家', '杏花岭区杏花岭街135号杏花小区1栋4单元101室',   103, NOW()),
(315, '林家', '杏花岭区坝陵桥街98号坝陵苑5栋2单元302室',     103, NOW()),
-- 晋源义井街道（104）
(316, '何家', '晋源区义井南街168号义井家园3栋1单元301室',     104, NOW()),
(317, '郭家', '晋源区晋源街56号晋源苑2栋3单元202室',          104, NOW()),
(318, '梁家', '晋源区金胜西街88号金胜花园4栋2单元401室',      104, NOW()),
(319, '曹家', '晋源区罗城街78号罗城苑1栋4单元102室',          104, NOW()),
(320, '高家', '晋源区南中环街145号南中环小区5栋1单元203室',   104, NOW());

-- ============================================================
-- 五、家庭-监护人关联（子查询方式，不依赖自增 ID）
-- ============================================================
INSERT INTO family_guardian (family_id, client_user_id)
SELECT 301, id FROM client_user WHERE mobile='13801110001' UNION ALL
SELECT 302, id FROM client_user WHERE mobile='13801110002' UNION ALL
SELECT 303, id FROM client_user WHERE mobile='13801110003' UNION ALL
SELECT 304, id FROM client_user WHERE mobile='13801110004' UNION ALL
SELECT 305, id FROM client_user WHERE mobile='13801110005' UNION ALL
SELECT 306, id FROM client_user WHERE mobile='13801110006' UNION ALL
SELECT 307, id FROM client_user WHERE mobile='13801110007' UNION ALL
SELECT 308, id FROM client_user WHERE mobile='13801110008' UNION ALL
SELECT 309, id FROM client_user WHERE mobile='13801110009' UNION ALL
SELECT 310, id FROM client_user WHERE mobile='13801110010' UNION ALL
SELECT 311, id FROM client_user WHERE mobile='13801110011' UNION ALL
SELECT 312, id FROM client_user WHERE mobile='13801110012' UNION ALL
SELECT 313, id FROM client_user WHERE mobile='13801110013' UNION ALL
SELECT 314, id FROM client_user WHERE mobile='13801110014' UNION ALL
SELECT 315, id FROM client_user WHERE mobile='13801110015' UNION ALL
SELECT 316, id FROM client_user WHERE mobile='13801110016' UNION ALL
SELECT 317, id FROM client_user WHERE mobile='13801110017' UNION ALL
SELECT 318, id FROM client_user WHERE mobile='13801110018' UNION ALL
SELECT 319, id FROM client_user WHERE mobile='13801110019' UNION ALL
SELECT 320, id FROM client_user WHERE mobile='13801110020';

-- ============================================================
-- 六、预存监护人 ID 到变量（简化后续 INSERT）
-- ============================================================
SET @g01 = (SELECT id FROM client_user WHERE mobile='13801110001');
SET @g02 = (SELECT id FROM client_user WHERE mobile='13801110002');
SET @g03 = (SELECT id FROM client_user WHERE mobile='13801110003');
SET @g04 = (SELECT id FROM client_user WHERE mobile='13801110004');
SET @g05 = (SELECT id FROM client_user WHERE mobile='13801110005');
SET @g06 = (SELECT id FROM client_user WHERE mobile='13801110006');
SET @g07 = (SELECT id FROM client_user WHERE mobile='13801110007');
SET @g08 = (SELECT id FROM client_user WHERE mobile='13801110008');
SET @g09 = (SELECT id FROM client_user WHERE mobile='13801110009');
SET @g10 = (SELECT id FROM client_user WHERE mobile='13801110010');
SET @g11 = (SELECT id FROM client_user WHERE mobile='13801110011');
SET @g12 = (SELECT id FROM client_user WHERE mobile='13801110012');
SET @g13 = (SELECT id FROM client_user WHERE mobile='13801110013');
SET @g14 = (SELECT id FROM client_user WHERE mobile='13801110014');
SET @g15 = (SELECT id FROM client_user WHERE mobile='13801110015');
SET @g16 = (SELECT id FROM client_user WHERE mobile='13801110016');
SET @g17 = (SELECT id FROM client_user WHERE mobile='13801110017');
SET @g18 = (SELECT id FROM client_user WHERE mobile='13801110018');
SET @g19 = (SELECT id FROM client_user WHERE mobile='13801110019');
SET @g20 = (SELECT id FROM client_user WHERE mobile='13801110020');

-- ============================================================
-- 七、设备（共 40 台，target_id 暂 NULL）
--   medical_institution / property_management 存机构名称字符串
--   每家庭 2 台设备，分布在卧室和客厅
-- ============================================================

-- ── 万柏林区（社区101，医院105），设备 DEV-CF-001~010 ──
INSERT INTO device (device_id, device_type, target_id, home_location, address, room_number,
                    medical_institution, property_management, guardian_id, family_id, status, created_at) VALUES
('DEV-CF-001','FALL_DETECTOR',NULL,'万柏林区','长风大街168号长风家园5栋2单元','卧室', '太原市第二人民医院老年科','万柏林区长风街道社区养老服务中心',@g01,301,'OFFLINE',NOW()),
('DEV-CF-002','FALL_DETECTOR',NULL,'万柏林区','长风大街168号长风家园5栋2单元','客厅', '太原市第二人民医院老年科','万柏林区长风街道社区养老服务中心',@g01,301,'OFFLINE',NOW()),
('DEV-CF-003','FALL_DETECTOR',NULL,'万柏林区','长风大街182号阳光花园3栋1单元','卧室', '太原市第二人民医院老年科','万柏林区长风街道社区养老服务中心',@g02,302,'OFFLINE',NOW()),
('DEV-CF-004','FALL_DETECTOR',NULL,'万柏林区','长风大街182号阳光花园3栋1单元','客厅', '太原市第二人民医院老年科','万柏林区长风街道社区养老服务中心',@g02,302,'OFFLINE',NOW()),
('DEV-CF-005','FALL_DETECTOR',NULL,'万柏林区','迎新街56号碧桂园7栋3单元',    '卧室', '太原市第二人民医院老年科','万柏林区长风街道社区养老服务中心',@g03,303,'OFFLINE',NOW()),
('DEV-CF-006','FALL_DETECTOR',NULL,'万柏林区','迎新街56号碧桂园7栋3单元',    '客厅', '太原市第二人民医院老年科','万柏林区长风街道社区养老服务中心',@g03,303,'OFFLINE',NOW()),
('DEV-CF-007','FALL_DETECTOR',NULL,'万柏林区','长风大街210号长风丽华苑2栋',  '卧室', '太原市第二人民医院老年科','万柏林区长风街道社区养老服务中心',@g04,304,'OFFLINE',NOW()),
('DEV-CF-008','FALL_DETECTOR',NULL,'万柏林区','长风大街210号长风丽华苑2栋',  '客厅', '太原市第二人民医院老年科','万柏林区长风街道社区养老服务中心',@g04,304,'OFFLINE',NOW()),
('DEV-CF-009','FALL_DETECTOR',NULL,'万柏林区','友谊南路78号裕隆苑4栋2单元',  '卧室', '太原市第二人民医院老年科','万柏林区长风街道社区养老服务中心',@g05,305,'OFFLINE',NOW()),
('DEV-CF-010','FALL_DETECTOR',NULL,'万柏林区','友谊南路78号裕隆苑4栋2单元',  '客厅', '太原市第二人民医院老年科','万柏林区长风街道社区养老服务中心',@g05,305,'OFFLINE',NOW());

-- ── 迎泽区（社区102，医院106），设备 DEV-YZ-001~010 ──
INSERT INTO device (device_id, device_type, target_id, home_location, address, room_number,
                    medical_institution, property_management, guardian_id, family_id, status, created_at) VALUES
('DEV-YZ-001','FALL_DETECTOR',NULL,'迎泽区','迎泽大街388号迎泽苑1栋3单元',  '卧室','山西省人民医院老年科','迎泽街道社区养老服务中心',@g06,306,'OFFLINE',NOW()),
('DEV-YZ-002','FALL_DETECTOR',NULL,'迎泽区','迎泽大街388号迎泽苑1栋3单元',  '客厅','山西省人民医院老年科','迎泽街道社区养老服务中心',@g06,306,'OFFLINE',NOW()),
('DEV-YZ-003','FALL_DETECTOR',NULL,'迎泽区','桃园南路67号桃园小区3栋1单元', '卧室','山西省人民医院老年科','迎泽街道社区养老服务中心',@g07,307,'OFFLINE',NOW()),
('DEV-YZ-004','FALL_DETECTOR',NULL,'迎泽区','桃园南路67号桃园小区3栋1单元', '客厅','山西省人民医院老年科','迎泽街道社区养老服务中心',@g07,307,'OFFLINE',NOW()),
('DEV-YZ-005','FALL_DETECTOR',NULL,'迎泽区','迎泽大街256号迎泽世家6栋2单元','卧室','山西省人民医院老年科','迎泽街道社区养老服务中心',@g08,308,'OFFLINE',NOW()),
('DEV-YZ-006','FALL_DETECTOR',NULL,'迎泽区','迎泽大街256号迎泽世家6栋2单元','客厅','山西省人民医院老年科','迎泽街道社区养老服务中心',@g08,308,'OFFLINE',NOW()),
('DEV-YZ-007','FALL_DETECTOR',NULL,'迎泽区','并州南路145号并州苑2栋4单元',  '卧室','山西省人民医院老年科','迎泽街道社区养老服务中心',@g09,309,'OFFLINE',NOW()),
('DEV-YZ-008','FALL_DETECTOR',NULL,'迎泽区','并州南路145号并州苑2栋4单元',  '客厅','山西省人民医院老年科','迎泽街道社区养老服务中心',@g09,309,'OFFLINE',NOW()),
('DEV-YZ-009','FALL_DETECTOR',NULL,'迎泽区','平阳路88号平阳小区5栋1单元',   '卧室','山西省人民医院老年科','迎泽街道社区养老服务中心',@g10,310,'OFFLINE',NOW()),
('DEV-YZ-010','FALL_DETECTOR',NULL,'迎泽区','平阳路88号平阳小区5栋1单元',   '客厅','山西省人民医院老年科','迎泽街道社区养老服务中心',@g10,310,'OFFLINE',NOW());

-- ── 杏花岭区（社区103，医院107），设备 DEV-XH-001~010 ──
INSERT INTO device (device_id, device_type, target_id, home_location, address, room_number,
                    medical_institution, property_management, guardian_id, family_id, status, created_at) VALUES
('DEV-XH-001','FALL_DETECTOR',NULL,'杏花岭区','五一路198号五一苑3栋2单元',       '卧室','杏花岭区人民医院','杏花岭区五一路社区养老服务中心',@g11,311,'OFFLINE',NOW()),
('DEV-XH-002','FALL_DETECTOR',NULL,'杏花岭区','五一路198号五一苑3栋2单元',       '客厅','杏花岭区人民医院','杏花岭区五一路社区养老服务中心',@g11,311,'OFFLINE',NOW()),
('DEV-XH-003','FALL_DETECTOR',NULL,'杏花岭区','解放路56号解放小区4栋1单元',      '卧室','杏花岭区人民医院','杏花岭区五一路社区养老服务中心',@g12,312,'OFFLINE',NOW()),
('DEV-XH-004','FALL_DETECTOR',NULL,'杏花岭区','解放路56号解放小区4栋1单元',      '客厅','杏花岭区人民医院','杏花岭区五一路社区养老服务中心',@g12,312,'OFFLINE',NOW()),
('DEV-XH-005','FALL_DETECTOR',NULL,'杏花岭区','上北关街78号北关苑2栋3单元',      '卧室','杏花岭区人民医院','杏花岭区五一路社区养老服务中心',@g13,313,'OFFLINE',NOW()),
('DEV-XH-006','FALL_DETECTOR',NULL,'杏花岭区','上北关街78号北关苑2栋3单元',      '客厅','杏花岭区人民医院','杏花岭区五一路社区养老服务中心',@g13,313,'OFFLINE',NOW()),
('DEV-XH-007','FALL_DETECTOR',NULL,'杏花岭区','杏花岭街135号杏花小区1栋4单元',   '卧室','杏花岭区人民医院','杏花岭区五一路社区养老服务中心',@g14,314,'OFFLINE',NOW()),
('DEV-XH-008','FALL_DETECTOR',NULL,'杏花岭区','杏花岭街135号杏花小区1栋4单元',   '客厅','杏花岭区人民医院','杏花岭区五一路社区养老服务中心',@g14,314,'OFFLINE',NOW()),
('DEV-XH-009','FALL_DETECTOR',NULL,'杏花岭区','坝陵桥街98号坝陵苑5栋2单元',     '卧室','杏花岭区人民医院','杏花岭区五一路社区养老服务中心',@g15,315,'OFFLINE',NOW()),
('DEV-XH-010','FALL_DETECTOR',NULL,'杏花岭区','坝陵桥街98号坝陵苑5栋2单元',     '客厅','杏花岭区人民医院','杏花岭区五一路社区养老服务中心',@g15,315,'OFFLINE',NOW());

-- ── 晋源区（社区104，医院108），设备 DEV-JY-001~010 ──
INSERT INTO device (device_id, device_type, target_id, home_location, address, room_number,
                    medical_institution, property_management, guardian_id, family_id, status, created_at) VALUES
('DEV-JY-001','FALL_DETECTOR',NULL,'晋源区','义井南街168号义井家园3栋1单元', '卧室','晋源区中医院','晋源区义井街道社区养老服务中心',@g16,316,'OFFLINE',NOW()),
('DEV-JY-002','FALL_DETECTOR',NULL,'晋源区','义井南街168号义井家园3栋1单元', '客厅','晋源区中医院','晋源区义井街道社区养老服务中心',@g16,316,'OFFLINE',NOW()),
('DEV-JY-003','FALL_DETECTOR',NULL,'晋源区','晋源街56号晋源苑2栋3单元',      '卧室','晋源区中医院','晋源区义井街道社区养老服务中心',@g17,317,'OFFLINE',NOW()),
('DEV-JY-004','FALL_DETECTOR',NULL,'晋源区','晋源街56号晋源苑2栋3单元',      '客厅','晋源区中医院','晋源区义井街道社区养老服务中心',@g17,317,'OFFLINE',NOW()),
('DEV-JY-005','FALL_DETECTOR',NULL,'晋源区','金胜西街88号金胜花园4栋2单元',  '卧室','晋源区中医院','晋源区义井街道社区养老服务中心',@g18,318,'OFFLINE',NOW()),
('DEV-JY-006','FALL_DETECTOR',NULL,'晋源区','金胜西街88号金胜花园4栋2单元',  '客厅','晋源区中医院','晋源区义井街道社区养老服务中心',@g18,318,'OFFLINE',NOW()),
('DEV-JY-007','FALL_DETECTOR',NULL,'晋源区','罗城街78号罗城苑1栋4单元',      '卧室','晋源区中医院','晋源区义井街道社区养老服务中心',@g19,319,'OFFLINE',NOW()),
('DEV-JY-008','FALL_DETECTOR',NULL,'晋源区','罗城街78号罗城苑1栋4单元',      '客厅','晋源区中医院','晋源区义井街道社区养老服务中心',@g19,319,'OFFLINE',NOW()),
('DEV-JY-009','FALL_DETECTOR',NULL,'晋源区','南中环街145号南中环小区5栋1单元','卧室','晋源区中医院','晋源区义井街道社区养老服务中心',@g20,320,'OFFLINE',NOW()),
('DEV-JY-010','FALL_DETECTOR',NULL,'晋源区','南中环街145号南中环小区5栋1单元','客厅','晋源区中医院','晋源区义井街道社区养老服务中心',@g20,320,'OFFLINE',NOW());

-- ============================================================
-- 八、被监护人（ward，共 40 名，member_id 手动指定）
--   每台设备对应一名主被监护人
--   奇数 member_id = 女，偶数 = 男
-- ============================================================

-- 万柏林区
INSERT INTO ward (member_id, name, mobile, gender, emergency_phone, device_id) VALUES
(10001,'张桂英','13901110001','FEMALE','13801110001','DEV-CF-001'),
(10002,'张守仁','13901110002','MALE',  '13801110001','DEV-CF-002'),
(10003,'李秀兰','13901110003','FEMALE','13801110002','DEV-CF-003'),
(10004,'李德山','13901110004','MALE',  '13801110002','DEV-CF-004'),
(10005,'王素珍','13901110005','FEMALE','13801110003','DEV-CF-005'),
(10006,'王志远','13901110006','MALE',  '13801110003','DEV-CF-006'),
(10007,'赵淑华','13901110007','FEMALE','13801110004','DEV-CF-007'),
(10008,'赵文清','13901110008','MALE',  '13801110004','DEV-CF-008'),
(10009,'陈玉兰','13901110009','FEMALE','13801110005','DEV-CF-009'),
(10010,'陈宝生','13901110010','MALE',  '13801110005','DEV-CF-010');

-- 迎泽区
INSERT INTO ward (member_id, name, mobile, gender, emergency_phone, device_id) VALUES
(10011,'刘凤英','13901110011','FEMALE','13801110006','DEV-YZ-001'),
(10012,'刘建国','13901110012','MALE',  '13801110006','DEV-YZ-002'),
(10013,'杨淑贞','13901110013','FEMALE','13801110007','DEV-YZ-003'),
(10014,'杨明义','13901110014','MALE',  '13801110007','DEV-YZ-004'),
(10015,'黄宝珍','13901110015','FEMALE','13801110008','DEV-YZ-005'),
(10016,'黄文亮','13901110016','MALE',  '13801110008','DEV-YZ-006'),
(10017,'吴翠华','13901110017','FEMALE','13801110009','DEV-YZ-007'),
(10018,'吴德顺','13901110018','MALE',  '13801110009','DEV-YZ-008'),
(10019,'周淑英','13901110019','FEMALE','13801110010','DEV-YZ-009'),
(10020,'周金山','13901110020','MALE',  '13801110010','DEV-YZ-010');

-- 杏花岭区
INSERT INTO ward (member_id, name, mobile, gender, emergency_phone, device_id) VALUES
(10021,'徐桂芳','13901110021','FEMALE','13801110011','DEV-XH-001'),
(10022,'徐守礼','13901110022','MALE',  '13801110011','DEV-XH-002'),
(10023,'孙秀英','13901110023','FEMALE','13801110012','DEV-XH-003'),
(10024,'孙庆祥','13901110024','MALE',  '13801110012','DEV-XH-004'),
(10025,'马淑梅','13901110025','FEMALE','13801110013','DEV-XH-005'),
(10026,'马成林','13901110026','MALE',  '13801110013','DEV-XH-006'),
(10027,'胡金凤','13901110027','FEMALE','13801110014','DEV-XH-007'),
(10028,'胡德义','13901110028','MALE',  '13801110014','DEV-XH-008'),
(10029,'林宝珍','13901110029','FEMALE','13801110015','DEV-XH-009'),
(10030,'林正明','13901110030','MALE',  '13801110015','DEV-XH-010');

-- 晋源区
INSERT INTO ward (member_id, name, mobile, gender, emergency_phone, device_id) VALUES
(10031,'何秀兰','13901110031','FEMALE','13801110016','DEV-JY-001'),
(10032,'何守义','13901110032','MALE',  '13801110016','DEV-JY-002'),
(10033,'郭素贞','13901110033','FEMALE','13801110017','DEV-JY-003'),
(10034,'郭宝成','13901110034','MALE',  '13801110017','DEV-JY-004'),
(10035,'梁淑英','13901110035','FEMALE','13801110018','DEV-JY-005'),
(10036,'梁文博','13901110036','MALE',  '13801110018','DEV-JY-006'),
(10037,'曹桂芳','13901110037','FEMALE','13801110019','DEV-JY-007'),
(10038,'曹明义','13901110038','MALE',  '13801110019','DEV-JY-008'),
(10039,'高淑贞','13901110039','FEMALE','13801110020','DEV-JY-009'),
(10040,'高守礼','13901110040','MALE',  '13801110020','DEV-JY-010');

-- ============================================================
-- 九、回填设备的主被监护人（target_id）
-- ============================================================
UPDATE device SET target_id=10001 WHERE device_id='DEV-CF-001';
UPDATE device SET target_id=10002 WHERE device_id='DEV-CF-002';
UPDATE device SET target_id=10003 WHERE device_id='DEV-CF-003';
UPDATE device SET target_id=10004 WHERE device_id='DEV-CF-004';
UPDATE device SET target_id=10005 WHERE device_id='DEV-CF-005';
UPDATE device SET target_id=10006 WHERE device_id='DEV-CF-006';
UPDATE device SET target_id=10007 WHERE device_id='DEV-CF-007';
UPDATE device SET target_id=10008 WHERE device_id='DEV-CF-008';
UPDATE device SET target_id=10009 WHERE device_id='DEV-CF-009';
UPDATE device SET target_id=10010 WHERE device_id='DEV-CF-010';

UPDATE device SET target_id=10011 WHERE device_id='DEV-YZ-001';
UPDATE device SET target_id=10012 WHERE device_id='DEV-YZ-002';
UPDATE device SET target_id=10013 WHERE device_id='DEV-YZ-003';
UPDATE device SET target_id=10014 WHERE device_id='DEV-YZ-004';
UPDATE device SET target_id=10015 WHERE device_id='DEV-YZ-005';
UPDATE device SET target_id=10016 WHERE device_id='DEV-YZ-006';
UPDATE device SET target_id=10017 WHERE device_id='DEV-YZ-007';
UPDATE device SET target_id=10018 WHERE device_id='DEV-YZ-008';
UPDATE device SET target_id=10019 WHERE device_id='DEV-YZ-009';
UPDATE device SET target_id=10020 WHERE device_id='DEV-YZ-010';

UPDATE device SET target_id=10021 WHERE device_id='DEV-XH-001';
UPDATE device SET target_id=10022 WHERE device_id='DEV-XH-002';
UPDATE device SET target_id=10023 WHERE device_id='DEV-XH-003';
UPDATE device SET target_id=10024 WHERE device_id='DEV-XH-004';
UPDATE device SET target_id=10025 WHERE device_id='DEV-XH-005';
UPDATE device SET target_id=10026 WHERE device_id='DEV-XH-006';
UPDATE device SET target_id=10027 WHERE device_id='DEV-XH-007';
UPDATE device SET target_id=10028 WHERE device_id='DEV-XH-008';
UPDATE device SET target_id=10029 WHERE device_id='DEV-XH-009';
UPDATE device SET target_id=10030 WHERE device_id='DEV-XH-010';

UPDATE device SET target_id=10031 WHERE device_id='DEV-JY-001';
UPDATE device SET target_id=10032 WHERE device_id='DEV-JY-002';
UPDATE device SET target_id=10033 WHERE device_id='DEV-JY-003';
UPDATE device SET target_id=10034 WHERE device_id='DEV-JY-004';
UPDATE device SET target_id=10035 WHERE device_id='DEV-JY-005';
UPDATE device SET target_id=10036 WHERE device_id='DEV-JY-006';
UPDATE device SET target_id=10037 WHERE device_id='DEV-JY-007';
UPDATE device SET target_id=10038 WHERE device_id='DEV-JY-008';
UPDATE device SET target_id=10039 WHERE device_id='DEV-JY-009';
UPDATE device SET target_id=10040 WHERE device_id='DEV-JY-010';

-- ============================================================
-- 验证
-- ============================================================
SELECT
  d.device_id                        AS 设备号,
  d.home_location                    AS 区划,
  d.room_number                      AS 位置,
  d.medical_institution              AS 医疗机构,
  d.property_management              AS 所属社区,
  cu.name                            AS 监护人,
  w.name                             AS 主被监护人,
  f.name                             AS 家庭
FROM device d
LEFT JOIN client_user cu ON cu.id       = d.guardian_id
LEFT JOIN ward         w  ON w.device_id = d.device_id AND w.member_id = d.target_id
LEFT JOIN family       f  ON f.id       = d.family_id
ORDER BY d.device_id;
