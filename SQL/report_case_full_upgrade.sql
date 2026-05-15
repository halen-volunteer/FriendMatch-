SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE `t_appeal`
ADD COLUMN `related_case_id` bigint NULL DEFAULT NULL COMMENT '关联举报主单ID' AFTER `related_report_id`,
ADD COLUMN `assign_time` datetime NULL DEFAULT NULL COMMENT '分配时间' AFTER `admin_id`,
ADD COLUMN `accept_time` datetime NULL DEFAULT NULL COMMENT '接单时间' AFTER `assign_time`,
ADD COLUMN `last_dispatch_time` datetime NULL DEFAULT NULL COMMENT '最近一次派单时间' AFTER `accept_time`,
ADD COLUMN `dispatch_count` tinyint NULL DEFAULT 0 COMMENT '派单次数' AFTER `last_dispatch_time`;

CREATE TABLE IF NOT EXISTS `t_report_case`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '举报主单ID',
  `report_type` tinyint NOT NULL COMMENT '举报类型：1-用户举报，2-消息举报，3-团队举报',
  `target_id` bigint NOT NULL COMMENT '被举报目标ID：用户ID/消息ID/团队ID',
  `case_status` tinyint NOT NULL DEFAULT 0 COMMENT '案件状态：0-进行中，1-已结案',
  `report_count` int NOT NULL DEFAULT 1 COMMENT '聚合举报次数',
  `latest_report_time` datetime NULL DEFAULT NULL COMMENT '最近一次举报时间',
  `priority_level` tinyint NOT NULL DEFAULT 0 COMMENT '优先级：0-普通，1-高优先级',
  `ai_check_result` tinyint NOT NULL DEFAULT 0 COMMENT 'AI结果：0-待检查或不适用，1-违规，2-正常',
  `ai_confidence` tinyint NOT NULL DEFAULT 0 COMMENT 'AI置信度（0-100）',
  `admin_status` tinyint NOT NULL DEFAULT 0 COMMENT '管理员处理状态：0-待审核，1-已成立，2-已驳回，3-已忽略',
  `admin_action` tinyint NULL DEFAULT NULL COMMENT '管理员处理动作编码',
  `admin_note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '管理员备注',
  `admin_id` bigint NULL DEFAULT NULL COMMENT '当前处理管理员ID',
  `process_time` datetime NULL DEFAULT NULL COMMENT '处理时间',
  `appeal_count` tinyint NOT NULL DEFAULT 0 COMMENT '累计申诉次数',
  `is_delete` tinyint NOT NULL DEFAULT 0 COMMENT '软删除：0-否，1-是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_report_case_active`(`report_type` ASC, `target_id` ASC, `case_status` ASC, `is_delete` ASC) USING BTREE,
  INDEX `idx_report_case_status`(`admin_status` ASC, `latest_report_time` ASC) USING BTREE,
  INDEX `idx_report_case_priority`(`priority_level` ASC, `latest_report_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '举报主单表' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `t_report_detail`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '举报明细ID',
  `case_id` bigint NOT NULL COMMENT '关联举报主单ID',
  `report_type` tinyint NOT NULL COMMENT '举报类型：1-用户举报，2-消息举报，3-团队举报',
  `target_id` bigint NOT NULL COMMENT '被举报目标ID：用户ID/消息ID/团队ID',
  `reporter_id` bigint NOT NULL COMMENT '举报人ID',
  `report_reason` tinyint NOT NULL COMMENT '举报原因',
  `report_content` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '举报补充说明',
  `report_evidence` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '举报证据',
  `is_delete` tinyint NOT NULL DEFAULT 0 COMMENT '软删除：0-否，1-是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_report_detail_reporter`(`case_id` ASC, `reporter_id` ASC, `is_delete` ASC) USING BTREE,
  INDEX `idx_report_detail_case`(`case_id` ASC, `create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '举报明细表' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `t_appeal_admin_history`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `appeal_id` bigint NOT NULL COMMENT '申诉ID',
  `related_report_id` bigint NOT NULL COMMENT '关联举报ID',
  `related_report_type` tinyint NOT NULL COMMENT '关联举报类型',
  `appeal_round` tinyint NOT NULL DEFAULT 1 COMMENT '申诉轮次',
  `admin_id` bigint NOT NULL COMMENT '管理员ID',
  `action_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '流转动作：assign/approve/reject/reassign',
  `action_note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '动作备注',
  `is_delete` tinyint NOT NULL DEFAULT 0 COMMENT '软删除：0-否，1-是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_appeal_admin_history_appeal`(`appeal_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_appeal_admin_history_report`(`related_report_type` ASC, `related_report_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '申诉管理员流转历史表' ROW_FORMAT = Dynamic;

UPDATE `t_appeal` a
JOIN `t_message_report` mr ON a.related_report_type = 2
    AND a.related_report_id = mr.id
    AND mr.is_delete = 0
JOIN `t_report_case` rc ON rc.report_type = 2
    AND rc.target_id = mr.message_id
    AND rc.is_delete = 0
SET a.related_case_id = rc.id,
    a.related_report_id = rc.id
WHERE a.related_report_type = 2
  AND (a.related_case_id IS NULL OR a.related_case_id <> rc.id);

UPDATE `t_appeal_admin_history` h
JOIN `t_message_report` mr ON h.related_report_type = 2
    AND h.related_report_id = mr.id
    AND mr.is_delete = 0
JOIN `t_report_case` rc ON rc.report_type = 2
    AND rc.target_id = mr.message_id
    AND rc.is_delete = 0
SET h.related_report_id = rc.id
WHERE h.related_report_type = 2
  AND h.related_report_id <> rc.id;

SET FOREIGN_KEY_CHECKS = 1;
