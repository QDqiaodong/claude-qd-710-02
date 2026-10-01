-- 植保无人机飞行队演示数据（ddl-auto: none，全部表结构与数据都靠这份脚本）
SET NAMES utf8mb4;

DROP TABLE IF EXISTS abnormal_action;
DROP TABLE IF EXISTS abnormal_handling;
DROP TABLE IF EXISTS flight_log;
DROP TABLE IF EXISTS mixing_review;
DROP TABLE IF EXISTS mixing_bucket;
DROP TABLE IF EXISTS mixing_batch;
DROP TABLE IF EXISTS job_order;
DROP TABLE IF EXISTS pilot;
DROP TABLE IF EXISTS drone;
DROP TABLE IF EXISTS plot;

-- 模块一：地块
CREATE TABLE plot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plot_no VARCHAR(16) NOT NULL COMMENT '地块编号',
    farmer_name VARCHAR(64) NOT NULL COMMENT '承包农户 / 合作社',
    crop_type VARCHAR(16) NOT NULL COMMENT '作物类型 RICE/WHEAT/CORN/COTTON/FRUIT/OILSEED',
    area_mu DECIMAL(10, 2) NOT NULL COMMENT '面积（亩）',
    village_group VARCHAR(64) NOT NULL COMMENT '所属村组',
    terrain VARCHAR(16) NOT NULL COMMENT '地形 PLAIN/HILL/MOUNTAIN',
    irrigation VARCHAR(16) NOT NULL COMMENT '灌溉条件 DITCH/SPRINKLER/DRIP/RAINFED',
    status VARCHAR(16) NOT NULL COMMENT '状态 PENDING/WORKING/DONE/HOLD',
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plot_no (plot_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='地块台账';

-- 模块二：植保无人机
CREATE TABLE drone (
    id BIGINT NOT NULL AUTO_INCREMENT,
    body_no VARCHAR(16) NOT NULL COMMENT '机身编号',
    model VARCHAR(32) NOT NULL COMMENT '型号',
    drone_type VARCHAR(16) NOT NULL COMMENT '类型 MULTI/SINGLE/SPRAY',
    max_payload DECIMAL(6, 2) NOT NULL COMMENT '最大载荷（L）',
    spray_width DECIMAL(5, 2) NOT NULL COMMENT '喷幅（m）',
    total_flight_hours DECIMAL(10, 2) NOT NULL COMMENT '累计飞行小时',
    total_area_mu DECIMAL(12, 2) NOT NULL COMMENT '累计作业亩数',
    battery_packs INT NOT NULL COMMENT '电池组数',
    status VARCHAR(16) NOT NULL COMMENT '状态 IDLE/WORKING/MAINT/DISABLED',
    purchase_date DATE NULL COMMENT '购入日期',
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_body_no (body_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='植保无人机台账';

-- 模块三：飞手
CREATE TABLE pilot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    pilot_no VARCHAR(16) NOT NULL COMMENT '飞手编号',
    name VARCHAR(32) NOT NULL COMMENT '姓名',
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    license_level VARCHAR(16) NOT NULL COMMENT '执照等级 LEVEL1/LEVEL2/LEVEL3/INSTRUCTOR',
    total_sorts INT NOT NULL COMMENT '累计架次',
    total_area_mu DECIMAL(12, 2) NOT NULL COMMENT '累计作业亩数',
    status VARCHAR(16) NOT NULL COMMENT '状态 ACTIVE/LEAVE/RESIGNED',
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pilot_no (pilot_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='飞手台账';

-- 模块四：作业单
CREATE TABLE job_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(24) NOT NULL COMMENT '作业单号',
    plot_id BIGINT NOT NULL COMMENT '地块',
    drone_id BIGINT NOT NULL COMMENT '无人机',
    pilot_id BIGINT NOT NULL COMMENT '飞手',
    formula VARCHAR(128) NOT NULL COMMENT '药剂配方',
    dose_per_mu DECIMAL(5, 2) NOT NULL COMMENT '亩用量（L/亩）',
    planned_mu DECIMAL(10, 2) NOT NULL COMMENT '计划作业亩数',
    job_date DATE NOT NULL COMMENT '作业日期',
    weather VARCHAR(16) NULL COMMENT '天气',
    status VARCHAR(16) NOT NULL COMMENT '状态 PENDING/WORKING/DONE/CANCELED',
    actual_mu DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '实际作业亩数',
    finished_at DATETIME NULL COMMENT '完成时间',
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_job_date (job_date),
    KEY idx_plot_id (plot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='植保作业单';

-- 模块五：飞行日志
CREATE TABLE flight_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_order_id BIGINT NOT NULL COMMENT '所属作业单',
    seq_no INT NOT NULL COMMENT '架次序号',
    takeoff_at DATETIME NOT NULL COMMENT '起飞时间',
    landing_at DATETIME NULL COMMENT '降落时间',
    duration_min INT NOT NULL COMMENT '飞行时长（分钟）',
    area_mu DECIMAL(10, 2) NOT NULL COMMENT '本架次作业亩数',
    chemical_l DECIMAL(10, 2) NOT NULL COMMENT '消耗药量（L）',
    wind_speed DECIMAL(4, 1) NULL COMMENT '风速（m/s）',
    temperature DECIMAL(4, 1) NULL COMMENT '气温',
    abnormal TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否异常',
    abnormal_desc VARCHAR(255) NULL COMMENT '异常描述',
    created_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_job_order_id (job_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='飞行日志';

-- 模块五-延：异常架次停飞处置（登记即立案，一条异常架次最多一条处置）
CREATE TABLE abnormal_handling (
    id BIGINT NOT NULL AUTO_INCREMENT,
    handling_no VARCHAR(24) NOT NULL COMMENT '处置单号',
    flight_log_id BIGINT NOT NULL COMMENT '异常架次（飞行日志 id）',
    job_order_id BIGINT NOT NULL COMMENT '所属作业单（立案时快照）',
    drone_id BIGINT NOT NULL COMMENT '关联无人机（立案时快照）',
    status VARCHAR(16) NOT NULL COMMENT '状态 PENDING/OBSERVING/GROUNDED/CLOSED',
    -- PENDING 待处置：登记后自动进入；OBSERVING 继续观察；GROUNDED 停飞机身（机身转维修）；CLOSED 已解除（终态）
    register_pilot_id BIGINT NULL COMMENT '立案飞手（系统自动立案时为空）',
    register_name VARCHAR(32) NULL COMMENT '立案人姓名快照',
    supervisor_id BIGINT NULL COMMENT '最近一次处置值班主管',
    supervisor_name VARCHAR(32) NULL COMMENT '最近处置人姓名快照',
    conclusion VARCHAR(255) NULL COMMENT '处置结论（解除前必填）',
    observe_comment VARCHAR(255) NULL COMMENT '继续观察意见',
    ground_comment VARCHAR(255) NULL COMMENT '停飞意见',
    registered_at DATETIME NOT NULL COMMENT '立案时间',
    observed_at DATETIME NULL COMMENT '继续观察时间',
    grounded_at DATETIME NULL COMMENT '停飞时间',
    closed_at DATETIME NULL COMMENT '解除时间',
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_handling_no (handling_no),
    -- 并发兜底：同一异常架次同时只能存在一条处置（1:1）
    UNIQUE KEY uk_handling_flight_log (flight_log_id),
    KEY idx_handling_drone (drone_id),
    KEY idx_handling_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异常架次停飞处置';

-- 模块五-延：处置留痕（自动立案 / 继续观察 / 停飞 / 解除全部留一条）
CREATE TABLE abnormal_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    handling_id BIGINT NOT NULL COMMENT '所属处置单',
    action VARCHAR(16) NOT NULL COMMENT '动作 REGISTER/OBSERVE/GROUND/RELEASE',
    actor_pilot_id BIGINT NULL COMMENT '操作飞手（系统自动立案时为空）',
    actor_name VARCHAR(32) NULL COMMENT '操作人姓名快照',
    comment VARCHAR(255) NULL COMMENT '处置意见 / 结论',
    created_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_action_handling (handling_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异常处置留痕';

-- 模块六：作业前配药批次（复核台）
CREATE TABLE mixing_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_no VARCHAR(28) NOT NULL COMMENT '配药批次号',
    job_order_id BIGINT NOT NULL COMMENT '作业单',
    status VARCHAR(16) NOT NULL COMMENT '状态 DRAFT/PENDING_REVIEW/LOCKED/REJECTED/EXPIRED',
    prepared_pilot_id BIGINT NOT NULL COMMENT '配药人（在队飞手）',
    reviewer_id BIGINT NULL COMMENT '锁定 / 驳回复核人',
    reject_reason VARCHAR(255) NULL COMMENT '最近一次驳回原因',
    -- 发起配药时的快照：复核依据与起飞一致性校验都以快照为准
    order_no_snapshot VARCHAR(24) NOT NULL COMMENT '作业单号快照',
    formula_snapshot VARCHAR(128) NOT NULL COMMENT '药剂配方快照',
    planned_mu_snapshot DECIMAL(10, 2) NOT NULL COMMENT '计划亩数快照',
    dose_per_mu_snapshot DECIMAL(5, 2) NOT NULL COMMENT '亩用量快照',
    total_l DECIMAL(10, 2) NOT NULL COMMENT '计划总药量（L）= 计划亩数 × 亩用量',
    drone_id_snapshot BIGINT NOT NULL COMMENT '无人机快照',
    drone_body_no_snapshot VARCHAR(16) NOT NULL COMMENT '机身编号快照',
    drone_model_snapshot VARCHAR(32) NOT NULL COMMENT '机型快照',
    max_payload_snapshot DECIMAL(6, 2) NOT NULL COMMENT '最大载荷快照（L）',
    bucket_count INT NOT NULL COMMENT '桶数',
    expire_reason VARCHAR(255) NULL COMMENT '失效原因',
    locked_at DATETIME NULL COMMENT '锁定时间',
    submitted_at DATETIME NULL COMMENT '最近一次提交复核时间',
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    -- 生成列：仅 LOCKED 状态取值为 1，配合唯一索引保证“一单一锁定”（EXPIRED 历史锁定不占坑）
    status_lock_key TINYINT
        GENERATED ALWAYS AS (CASE WHEN status = 'LOCKED' THEN 1 ELSE NULL END) STORED,
    -- 生成列：未失效批次取常量 1，失效后置 NULL，配合 (job_order_id, 1) 唯一索引保证“一单一有效批次”
    active_batch_key TINYINT
        GENERATED ALWAYS AS (CASE WHEN status <> 'EXPIRED' THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_no (batch_no),
    KEY idx_mixing_order (job_order_id),
    KEY idx_mixing_status (status),
    -- 并发兜底：同一作业单在数据库层面最多只有一个仍有效的已锁定批次
    UNIQUE KEY uk_one_locked_per_order (job_order_id, status_lock_key),
    -- 并发兜底：同一作业单同时只能存在一个未失效批次（草稿/待复核/已锁定/已驳回）
    UNIQUE KEY uk_one_active_per_order (job_order_id, active_batch_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业前配药批次';

-- 模块六：分桶明细
CREATE TABLE mixing_bucket (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL COMMENT '所属配药批次',
    seq_no INT NOT NULL COMMENT '桶序号（从 1 开始）',
    amount_l DECIMAL(8, 2) NOT NULL COMMENT '本桶药量（L）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_seq (batch_id, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配药分桶明细';

-- 模块六：复核留痕（提交 / 驳回 / 锁定 / 失效全部留一条）
CREATE TABLE mixing_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL COMMENT '所属配药批次',
    action VARCHAR(16) NOT NULL COMMENT '动作 SUBMIT/REJECT/LOCK/EXPIRE',
    actor_pilot_id BIGINT NULL COMMENT '操作飞手（系统失效时为空）',
    actor_name VARCHAR(32) NULL COMMENT '操作人姓名快照',
    comment VARCHAR(255) NULL COMMENT '说明（驳回原因 / 失效原因）',
    created_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_review_batch (batch_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配药复核记录';

-- ---------------- 地块（12 块） ----------------
INSERT INTO plot (plot_no, farmer_name, crop_type, area_mu, village_group, terrain, irrigation, status, updated_at) VALUES
('P-01', '张建国', 'RICE', 128.50, '李集一组', 'PLAIN', 'DITCH', 'DONE', '2026-09-18 09:10:00'),
('P-02', '兴旺种植合作社', 'WHEAT', 96.00, '李集二组', 'PLAIN', 'SPRINKLER', 'DONE', '2026-09-16 08:20:00'),
('P-03', '王富裕', 'CORN', 74.50, '王庄三组', 'HILL', 'DRIP', 'DONE', '2026-09-14 10:05:00'),
('P-04', '赵春河', 'COTTON', 152.00, '赵楼一组', 'PLAIN', 'DITCH', 'DONE', '2026-09-11 11:30:00'),
('P-05', '绿源果品合作社', 'FRUIT', 63.50, '西山二组', 'MOUNTAIN', 'DRIP', 'DONE', '2026-09-08 12:40:00'),
('P-06', '孙德海', 'OILSEED', 88.00, '孙洼四组', 'PLAIN', 'SPRINKLER', 'WORKING', '2026-09-23 07:50:00'),
('P-07', '李集聚丰合作社', 'RICE', 210.00, '李集一组', 'PLAIN', 'DITCH', 'WORKING', '2026-09-23 07:30:00'),
('P-08', '周文田', 'WHEAT', 135.50, '周庄五组', 'HILL', 'RAINFED', 'PENDING', '2026-09-20 15:20:00'),
('P-09', '陈永根', 'CORN', 79.00, '陈洼二组', 'PLAIN', 'DRIP', 'PENDING', '2026-09-20 15:22:00'),
('P-10', '丰收农机合作社', 'RICE', 186.00, '李集三组', 'PLAIN', 'DITCH', 'PENDING', '2026-09-20 15:25:00'),
('P-11', '吴有福', 'COTTON', 58.50, '吴台一组', 'MOUNTAIN', 'RAINFED', 'HOLD', '2026-09-12 09:00:00'),
('P-12', '郑岗果林合作社', 'FRUIT', 112.00, '郑岗三组', 'HILL', 'DRIP', 'HOLD', '2026-09-12 09:05:00');

-- ---------------- 无人机（12 台） ----------------
INSERT INTO drone (body_no, model, drone_type, max_payload, spray_width, total_flight_hours, total_area_mu, battery_packs, status, purchase_date, updated_at) VALUES
('DP-01', '极飞 P100', 'SPRAY', 40.00, 7.00, 268.40, 4126.50, 8, 'IDLE', '2025-04-12', '2026-09-18 09:15:00'),
('DP-02', '大疆 T60', 'MULTI', 50.00, 8.50, 231.70, 3584.00, 6, 'MAINT', '2025-05-08', '2026-09-16 08:30:00'),
('DP-03', '大疆 T50', 'MULTI', 40.00, 7.50, 198.20, 2915.50, 6, 'IDLE', '2024-11-20', '2026-09-14 10:10:00'),
('DP-04', '极飞 P60', 'SPRAY', 45.00, 8.00, 302.60, 4877.00, 10, 'IDLE', '2025-03-02', '2026-09-11 11:35:00'),
('DP-05', '丰疆 XF-30', 'SINGLE', 30.00, 9.50, 145.30, 1926.00, 5, 'IDLE', '2024-08-16', '2026-09-10 10:20:00'),
('DP-06', '汉和 CD-15', 'SINGLE', 32.00, 6.50, 88.90, 1104.50, 4, 'WORKING', '2025-06-11', '2026-09-23 07:55:00'),
('DP-07', '大疆 T70', 'MULTI', 55.00, 9.00, 176.50, 2633.00, 8, 'WORKING', '2026-02-25', '2026-09-23 07:35:00'),
('DP-08', '极飞 V50', 'SPRAY', 35.00, 6.80, 64.20, 812.00, 4, 'IDLE', '2025-09-30', '2026-09-20 15:30:00'),
('DP-09', '天途 M8A', 'MULTI', 30.00, 6.00, 52.80, 668.50, 4, 'IDLE', '2025-10-18', '2026-09-20 15:32:00'),
('DP-10', '韦加 X9', 'SPRAY', 42.00, 7.20, 37.40, 452.00, 6, 'IDLE', '2026-01-15', '2026-09-20 15:35:00'),
('DP-11', '全丰领航者', 'SINGLE', 25.00, 8.20, 121.60, 1478.00, 5, 'IDLE', '2024-07-09', '2026-09-20 15:38:00'),
('DP-12', '拓攻 TG-26', 'MULTI', 22.00, 5.50, 18.30, 196.00, 3, 'DISABLED', '2023-05-22', '2026-08-30 10:00:00');

-- ---------------- 飞手（10 名） ----------------
INSERT INTO pilot (pilot_no, name, phone, license_level, total_sorts, total_area_mu, status, updated_at) VALUES
('PL-01', '刘志远', '13862041527', 'INSTRUCTOR', 1268, 8420.50, 'ACTIVE', '2026-09-18 09:20:00'),
('PL-02', '马晓峰', '13955473381', 'LEVEL3', 942, 6315.00, 'ACTIVE', '2026-09-23 07:40:00'),
('PL-03', '田鹏', '13770129866', 'LEVEL2', 736, 3182.50, 'ACTIVE', '2026-09-14 10:15:00'),
('PL-04', '胡俊成', '15062197704', 'LEVEL3', 815, 4520.00, 'ACTIVE', '2026-09-11 11:40:00'),
('PL-05', '周立波', '13382716045', 'LEVEL3', 604, 2977.00, 'ACTIVE', '2026-09-08 12:45:00'),
('PL-06', '黄子谦', '18862370915', 'LEVEL2', 519, 2148.50, 'ACTIVE', '2026-09-23 08:00:00'),
('PL-07', '贾路遥', '13645128730', 'LEVEL2', 688, 3306.00, 'ACTIVE', '2026-09-23 07:38:00'),
('PL-08', '宋雅琴', '13905286672', 'LEVEL1', 212, 806.00, 'LEAVE', '2026-09-18 14:00:00'),
('PL-09', '崔行舟', '15262149038', 'LEVEL2', 401, 1624.50, 'ACTIVE', '2026-09-20 15:40:00'),
('PL-10', '何向东', '18961724350', 'LEVEL1', 96, 372.00, 'RESIGNED', '2026-08-25 09:30:00');

-- ---------------- 作业单（12 张） ----------------
INSERT INTO job_order (order_no, plot_id, drone_id, pilot_id, formula, dose_per_mu, planned_mu, job_date, weather, status, actual_mu, finished_at, updated_at) VALUES
('JO-24091801', 1, 1, 1, '井冈霉素 5% + 磷酸二氢钾 叶面肥', 1.20, 128.50, '2026-09-18', '晴', 'DONE', 128.50, '2026-09-18 07:50:00', '2026-09-18 07:50:00'),
('JO-24091601', 2, 2, 3, '吡虫啉 10% + 三唑酮', 1.50, 96.00, '2026-09-16', '多云', 'DONE', 96.00, '2026-09-16 07:20:00', '2026-09-16 07:20:00'),
('JO-24091401', 3, 3, 3, '氯虫苯甲酰胺 5% + 芸苔素', 1.10, 74.50, '2026-09-14', '晴', 'DONE', 74.50, '2026-09-14 08:05:00', '2026-09-14 08:05:00'),
('JO-24091101', 4, 4, 4, '甲维盐 3% + 缩节胺', 1.30, 152.00, '2026-09-11', '晴', 'DONE', 152.00, '2026-09-11 07:42:00', '2026-09-11 07:42:00'),
('JO-24090801', 5, 5, 5, '代森锰锌 80% + 螺虫乙酯', 1.40, 63.50, '2026-09-08', '阴', 'DONE', 63.50, '2026-09-08 09:15:00', '2026-09-08 09:15:00'),
('JO-24090501', 1, 1, 1, '稻瘟灵 40% + 芸苔素内酯', 1.00, 128.50, '2026-09-05', '晴', 'DONE', 128.50, '2026-09-05 17:40:00', '2026-09-05 17:40:00'),
('JO-24092301', 6, 6, 6, '菌核净 40% + 硼肥', 1.20, 88.00, '2026-09-23', '晴', 'WORKING', 52.00, NULL, '2026-09-23 06:50:00'),
('JO-24092302', 7, 7, 7, '稻曲病防治：苯甲·丙环唑 + 磷酸二氢钾', 1.25, 210.00, '2026-09-23', '晴转多云', 'WORKING', 96.50, NULL, '2026-09-23 07:12:00'),
('JO-24092501', 8, 8, 9, '戊唑醇 43% + 叶面钾肥', 1.10, 135.50, '2026-09-25', '多云', 'PENDING', 0.00, NULL, '2026-09-20 15:20:00'),
('JO-24092601', 9, 9, 3, '氯虫苯甲酰胺 5% + 氨基寡糖素', 1.00, 79.00, '2026-09-26', '晴', 'PENDING', 0.00, NULL, '2026-09-20 15:25:00'),
('JO-24092701', 10, 10, 4, '稻飞虱防控：吡蚜酮 25% + 烯啶虫胺', 1.20, 186.00, '2026-09-27', '待定', 'PENDING', 0.00, NULL, '2026-09-20 15:28:00'),
('JO-24092001', 12, 11, 2, '石硫合剂清园 + 矿物油', 1.60, 112.00, '2026-09-20', '小雨', 'CANCELED', 0.00, NULL, '2026-09-19 17:00:00'),
-- DP-02 停飞前已派出的待飞单：机身停飞后这张单不得起飞，必须改派
('JO-24092401', 9, 2, 1, '氯虫苯甲酰胺 5% + 芸苔素', 1.10, 79.00, '2026-09-24', '晴', 'PENDING', 0.00, NULL, '2026-09-23 12:10:00');

-- ---------------- 飞行日志（30 架次，分布在 8 张作业单上） ----------------
INSERT INTO flight_log (job_order_id, seq_no, takeoff_at, landing_at, duration_min, area_mu, chemical_l, wind_speed, temperature, abnormal, abnormal_desc, created_at) VALUES
-- JO-24091801 地块 P-01（5 架次）
(1, 1, '2026-09-18 06:12:00', '2026-09-18 06:24:00', 12, 26.00, 31.20, 2.1, 21.5, 0, NULL, '2026-09-18 06:25:00'),
(1, 2, '2026-09-18 06:31:00', '2026-09-18 06:43:00', 12, 25.50, 30.60, 2.3, 22.0, 0, NULL, '2026-09-18 06:44:00'),
(1, 3, '2026-09-18 06:50:00', '2026-09-18 07:02:00', 12, 26.00, 31.20, 2.0, 22.4, 0, NULL, '2026-09-18 07:03:00'),
(1, 4, '2026-09-18 07:10:00', '2026-09-18 07:23:00', 13, 25.50, 30.60, 2.6, 23.1, 0, NULL, '2026-09-18 07:24:00'),
(1, 5, '2026-09-18 07:30:00', '2026-09-18 07:42:00', 12, 25.50, 30.60, 3.1, 23.8, 0, NULL, '2026-09-18 07:43:00'),
-- JO-24091601 地块 P-02（4 架次，含 1 次异常）
(2, 1, '2026-09-16 05:58:00', '2026-09-16 06:12:00', 14, 24.00, 36.00, 1.8, 20.3, 0, NULL, '2026-09-16 06:13:00'),
(2, 2, '2026-09-16 06:19:00', '2026-09-16 06:33:00', 14, 24.00, 36.00, 2.0, 20.9, 0, NULL, '2026-09-16 06:34:00'),
(2, 3, '2026-09-16 06:40:00', '2026-09-16 06:53:00', 13, 24.00, 36.00, 2.4, 21.6, 1, '第 6 分钟风速突增，短暂悬停，喷头流量下降约 12%', '2026-09-16 06:54:00'),
(2, 4, '2026-09-16 07:00:00', '2026-09-16 07:14:00', 14, 24.00, 36.00, 3.4, 22.2, 0, NULL, '2026-09-16 07:15:00'),
-- JO-24091401 地块 P-03（3 架次，丘陵地 1 次返航点重设）
(3, 1, '2026-09-14 07:05:00', '2026-09-14 07:19:00', 14, 25.00, 27.50, 3.6, 24.1, 0, NULL, '2026-09-14 07:20:00'),
(3, 2, '2026-09-14 07:25:00', '2026-09-14 07:38:00', 13, 25.00, 27.50, 4.2, 25.0, 1, '丘陵地形起伏，返航点重设一次，航线高度修正至 3.2m', '2026-09-14 07:39:00'),
(3, 3, '2026-09-14 07:45:00', '2026-09-14 07:58:00', 13, 24.50, 27.00, 3.9, 25.6, 0, NULL, '2026-09-14 07:59:00'),
-- JO-24091101 地块 P-04（5 架次）
(4, 1, '2026-09-11 06:02:00', '2026-09-11 06:16:00', 14, 30.50, 39.70, 1.5, 22.4, 0, NULL, '2026-09-11 06:17:00'),
(4, 2, '2026-09-11 06:22:00', '2026-09-11 06:35:00', 13, 30.50, 39.70, 1.7, 23.0, 0, NULL, '2026-09-11 06:36:00'),
(4, 3, '2026-09-11 06:42:00', '2026-09-11 06:56:00', 14, 30.50, 39.70, 2.2, 23.7, 0, NULL, '2026-09-11 06:57:00'),
(4, 4, '2026-09-11 07:03:00', '2026-09-11 07:16:00', 13, 30.50, 39.70, 2.6, 24.5, 0, NULL, '2026-09-11 07:17:00'),
(4, 5, '2026-09-11 07:22:00', '2026-09-11 07:35:00', 13, 30.00, 39.00, 3.0, 25.2, 0, NULL, '2026-09-11 07:36:00'),
-- JO-24090801 地块 P-05（3 架次，果树区绕障）
(5, 1, '2026-09-08 08:10:00', '2026-09-08 08:25:00', 15, 21.50, 30.10, 4.5, 26.3, 1, '果树区边缘绕障，避让高压线并补飞一小段', '2026-09-08 08:26:00'),
(5, 2, '2026-09-08 08:32:00', '2026-09-08 08:46:00', 14, 21.00, 29.40, 4.1, 26.8, 0, NULL, '2026-09-08 08:47:00'),
(5, 3, '2026-09-08 08:53:00', '2026-09-08 09:07:00', 14, 21.00, 29.40, 3.8, 27.4, 0, NULL, '2026-09-08 09:08:00'),
-- JO-24090501 地块 P-01 二次作业（4 架次）
(6, 1, '2026-09-05 16:20:00', '2026-09-05 16:33:00', 13, 32.00, 32.00, 2.8, 28.6, 0, NULL, '2026-09-05 16:34:00'),
(6, 2, '2026-09-05 16:40:00', '2026-09-05 16:53:00', 13, 32.50, 32.50, 2.5, 28.1, 0, NULL, '2026-09-05 16:54:00'),
(6, 3, '2026-09-05 17:00:00', '2026-09-05 17:12:00', 12, 32.00, 32.00, 2.2, 27.5, 0, NULL, '2026-09-05 17:13:00'),
(6, 4, '2026-09-05 17:18:00', '2026-09-05 17:31:00', 13, 32.00, 32.00, 1.9, 26.9, 0, NULL, '2026-09-05 17:32:00'),
-- JO-24092301 地块 P-06 作业中（2 架次）
(7, 1, '2026-09-23 06:15:00', '2026-09-23 06:28:00', 13, 26.00, 31.20, 2.4, 21.8, 0, NULL, '2026-09-23 06:29:00'),
(7, 2, '2026-09-23 06:35:00', '2026-09-23 06:47:00', 12, 26.00, 31.20, 2.7, 22.5, 0, NULL, '2026-09-23 06:48:00'),
-- JO-24092302 地块 P-07 作业中（4 架次，含 1 次超阈值风速）
(8, 1, '2026-09-23 05:50:00', '2026-09-23 06:05:00', 15, 24.00, 30.00, 3.2, 20.4, 0, NULL, '2026-09-23 06:06:00'),
(8, 2, '2026-09-23 06:12:00', '2026-09-23 06:27:00', 15, 24.50, 30.60, 3.5, 21.0, 0, NULL, '2026-09-23 06:28:00'),
(8, 3, '2026-09-23 06:34:00', '2026-09-23 06:48:00', 14, 24.00, 30.00, 3.9, 21.6, 0, NULL, '2026-09-23 06:49:00'),
(8, 4, '2026-09-23 06:55:00', '2026-09-23 07:09:00', 14, 24.00, 30.00, 4.3, 22.1, 1, '风速逼近 5m/s 阈值，暂停 4 分钟待风小后再复飞', '2026-09-23 07:10:00');

-- ---------------- 配药批次（3 张：已锁定 / 待复核 / 草稿） ----------------
-- 批次1：JO-24092501 总药量 135.50×1.10=149.05L，DP-08 载荷 35L → 5 桶，已锁定可起飞
INSERT INTO mixing_batch (id, batch_no, job_order_id, status, prepared_pilot_id, reviewer_id, reject_reason,
    order_no_snapshot, formula_snapshot, planned_mu_snapshot, dose_per_mu_snapshot, total_l,
    drone_id_snapshot, drone_body_no_snapshot, drone_model_snapshot, max_payload_snapshot, bucket_count,
    expire_reason, locked_at, submitted_at, created_at, updated_at) VALUES
(1, 'PB-24092201', 9, 'LOCKED', 9, 2, NULL,
 'JO-24092501', '戊唑醇 43% + 叶面钾肥', 135.50, 1.10, 149.05,
 8, 'DP-08', '极飞 V50', 35.00, 5,
 NULL, '2026-09-22 16:20:00', '2026-09-22 16:05:00', '2026-09-22 15:50:00', '2026-09-22 16:20:00'),
-- 批次2：JO-24092701 总药量 186.00×1.20=223.20L，DP-09 载荷 30L → 8 桶，待复核
(2, 'PB-24092202', 11, 'PENDING_REVIEW', 3, NULL, NULL,
 'JO-24092701', '稻飞虱防控：吡蚜酮 25% + 烯啶虫胺', 186.00, 1.20, 223.20,
 9, 'DP-09', '天途 M8A', 30.00, 8,
 NULL, NULL, '2026-09-22 17:02:00', '2026-09-22 16:40:00', '2026-09-22 17:02:00'),
-- 批次3：JO-24092601 总药量 79.00×1.00=79.00L，DP-10 载荷 42L → 2 桶，草稿中
(3, 'PB-24092203', 10, 'DRAFT', 6, NULL, NULL,
 'JO-24092601', '氯虫苯甲酰胺 5% + 氨基寡糖素', 79.00, 1.00, 79.00,
 10, 'DP-10', '韦加 X9', 42.00, 2,
 NULL, NULL, NULL, '2026-09-22 17:20:00', '2026-09-22 17:20:00');

-- 批次1 分桶：29.81×5 = 149.05（恰好整除到分，桶数最少且不超载）
INSERT INTO mixing_bucket (id, batch_id, seq_no, amount_l) VALUES
(1, 1, 1, 29.81), (2, 1, 2, 29.81), (3, 1, 3, 29.81),
(4, 1, 4, 29.81), (5, 1, 5, 29.81),
-- 批次2 分桶：27.90×8 = 223.20
(6, 2, 1, 27.90), (7, 2, 2, 27.90), (8, 2, 3, 27.90), (9, 2, 4, 27.90),
(10, 2, 5, 27.90), (11, 2, 6, 27.90), (12, 2, 7, 27.90), (13, 2, 8, 27.90),
-- 批次3 分桶：39.50×2 = 79.00
(14, 3, 1, 39.50), (15, 3, 2, 39.50);

INSERT INTO mixing_review (id, batch_id, action, actor_pilot_id, actor_name, comment, created_at) VALUES
(1, 1, 'CREATE', 9, '崔行舟', '发起配药批次，系统生成默认分桶', '2026-09-22 15:50:00'),
(2, 1, 'SUBMIT', 9, '崔行舟', NULL, '2026-09-22 16:05:00'),
(3, 1, 'LOCK', 2, '马晓峰', '分桶与载荷复核一致，准予锁定', '2026-09-22 16:20:00'),
(4, 2, 'CREATE', 3, '田鹏', '发起配药批次，系统生成默认分桶', '2026-09-22 16:40:00'),
(5, 2, 'SUBMIT', 3, '田鹏', NULL, '2026-09-22 17:02:00'),
(6, 3, 'CREATE', 6, '黄子谦', '发起配药批次，系统生成默认分桶', '2026-09-22 17:20:00');

-- ---------------- 异常架次停飞处置（4 条：停飞中 / 待处置 / 观察后解除 / 已解除） ----------------
-- 异常架次：#8=JO-24091601·DP-02(9-16)，#11=JO-24091401·DP-03(9-14)，#18=JO-24090801·DP-05(9-08)，#30=JO-24092302·DP-07(9-23)
INSERT INTO abnormal_handling (id, handling_no, flight_log_id, job_order_id, drone_id, status,
    register_pilot_id, register_name, supervisor_id, supervisor_name, conclusion, observe_comment, ground_comment,
    registered_at, observed_at, grounded_at, closed_at, updated_at) VALUES
-- ① DP-02：停飞处置中，机身已转维修；待飞单 JO-24092401 需改派
(1, 'AH-260916-01', 8, 2, 2, 'GROUNDED',
 3, '田鹏', 2, '马晓峰', NULL, NULL, '喷头流量下降 12%，疑似喷洒管路堵塞，停飞检查并更换喷头',
 '2026-09-16 07:00:00', NULL, '2026-09-16 07:20:00', NULL, '2026-09-16 07:20:00'),
-- ② DP-03：继续观察中（丘陵地返航点重设，属环境因素，先观察）
(2, 'AH-260914-01', 11, 3, 3, 'OBSERVING',
 3, '田鹏', 1, '刘志远', NULL, '丘陵地形导致返航点重设，未见机身异常，后续三个架次重点观察悬停稳定性', NULL,
 '2026-09-14 07:45:00', '2026-09-14 08:00:00', NULL, NULL, '2026-09-14 08:00:00'),
-- ③ DP-05：9-08 停飞检修，9-10 排除故障后解除，机身已恢复在库
(3, 'AH-260908-01', 18, 5, 5, 'CLOSED',
 5, '周立波', 1, '刘志远', '绕障保护触发正常，检查电调与桨叶无损伤，校准指南针后试飞正常，解除停飞恢复作业',
 NULL, '果树区绕障伴随悬停掉高，停飞检查动力与避障系统',
 '2026-09-08 09:15:00', NULL, '2026-09-08 09:40:00', '2026-09-10 10:20:00', '2026-09-10 10:20:00'),
-- ④ DP-07：今早最新异常，登记后自动立案，等待值班主管处置
(4, 'AH-260923-01', 30, 8, 7, 'PENDING',
 7, '贾路遥', NULL, NULL, NULL, NULL, NULL,
 '2026-09-23 07:12:00', NULL, NULL, NULL, '2026-09-23 07:12:00');

INSERT INTO abnormal_action (id, handling_id, action, actor_pilot_id, actor_name, comment, created_at) VALUES
(1, 1, 'REGISTER', 3, '田鹏', '登记第 3 架次异常：风速突增、喷头流量下降，自动立案待处置', '2026-09-16 07:00:00'),
(2, 1, 'GROUND', 2, '马晓峰', '喷头流量下降 12%，疑似喷洒管路堵塞，停飞检查并更换喷头', '2026-09-16 07:20:00'),
(3, 2, 'REGISTER', 3, '田鹏', '登记第 2 架次异常：丘陵地形返航点重设，自动立案待处置', '2026-09-14 07:45:00'),
(4, 2, 'OBSERVE', 1, '刘志远', '丘陵地形导致返航点重设，未见机身异常，后续三个架次重点观察悬停稳定性', '2026-09-14 08:00:00'),
(5, 3, 'REGISTER', 5, '周立波', '登记第 1 架次异常：果树区绕障并补飞，自动立案待处置', '2026-09-08 09:15:00'),
(6, 3, 'GROUND', 1, '刘志远', '果树区绕障伴随悬停掉高，停飞检查动力与避障系统', '2026-09-08 09:40:00'),
(7, 3, 'RELEASE', 1, '刘志远', '绕障保护触发正常，检查电调与桨叶无损伤，校准指南针后试飞正常，解除停飞恢复作业', '2026-09-10 10:20:00'),
(8, 4, 'REGISTER', 7, '贾路遥', '登记第 4 架次异常：风速逼近 5m/s 暂停复飞，自动立案待处置', '2026-09-23 07:12:00');
