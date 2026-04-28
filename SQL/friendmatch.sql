/*
 Navicat Premium Dump SQL

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80036 (8.0.36)
 Source Host           : localhost:3306
 Source Schema         : friendmatch

 Target Server Type    : MySQL
 Target Server Version : 80036 (8.0.36)
 File Encoding         : 65001

 Date: 12/04/2026 19:11:45
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for t_admin_user
-- ----------------------------
DROP TABLE IF EXISTS `t_admin_user`;
CREATE TABLE `t_admin_user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '管理员主键ID',
  `user_id` bigint NOT NULL COMMENT '关联用户ID（t_user.id）',
  `admin_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '管理员显示名',
  `admin_status` tinyint NULL DEFAULT 1 COMMENT '管理员状态：0-禁用，1-启用',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_admin_status`(`admin_status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '管理员表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_appeal
-- ----------------------------
DROP TABLE IF EXISTS `t_appeal`;
CREATE TABLE `t_appeal`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申诉主键ID',
  `appellant_id` bigint NOT NULL COMMENT '申诉人ID',
  `appellant_type` tinyint NOT NULL COMMENT '申诉人类型：1-被处罚用户，2-举报人',
  `related_report_id` bigint NOT NULL COMMENT '关联举报ID',
  `related_report_type` tinyint NOT NULL COMMENT '举报来源：1-用户举报，2-消息举报',
  `related_punish_id` bigint NULL DEFAULT NULL COMMENT '关联处罚记录ID（t_user_punish_log）',
  `appeal_round` tinyint NOT NULL DEFAULT 1 COMMENT '申诉轮次（1/2/3）',
  `appeal_reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '申诉理由',
  `appeal_evidence` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '申诉证据（图片URL，逗号分隔）',
  `appeal_status` tinyint NULL DEFAULT 0 COMMENT '申诉状态：0-待审核，1-申诉成立，2-驳回',
  `admin_id` bigint NULL DEFAULT NULL COMMENT '本轮负责管理员ID',
  `admin_reply` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '管理员回复',
  `process_time` datetime NULL DEFAULT NULL COMMENT '处理时间',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_appellant_id`(`appellant_id` ASC) USING BTREE,
  INDEX `idx_related_report`(`related_report_id` ASC, `related_report_type` ASC) USING BTREE,
  INDEX `idx_appeal_status`(`appeal_status` ASC) USING BTREE,
  INDEX `idx_admin_id`(`admin_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '申诉表（三轮换管理员）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_chat_message
-- ----------------------------
DROP TABLE IF EXISTS `t_chat_message`;
CREATE TABLE `t_chat_message`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息主键ID',
  `sender_id` bigint NOT NULL COMMENT '消息发送人ID',
  `recv_type` tinyint NOT NULL COMMENT '接收类型：1-私聊，2-群聊（团队）',
  `recv_id` bigint NOT NULL COMMENT '接收ID：私聊=对方用户ID，群聊=团队ID',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话ID（私聊：min(uid1,uid2)_max(uid1,uid2)；群聊：team_群ID）',
  `msg_type` tinyint NOT NULL DEFAULT 1 COMMENT '消息类型：1-文本，2-图片，3-文件，4-表情包，5-@消息',
  `msg_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消息内容：文本=内容，图片/文件=URL，表情包=标识',
  `is_edited` tinyint NULL DEFAULT 0 COMMENT '是否已编辑：0-否，1-是',
  `edit_time` datetime NULL DEFAULT NULL COMMENT '编辑时间',
  `edit_count` int NULL DEFAULT 0 COMMENT '编辑次数',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_conversation_create_time`(`conversation_id` ASC, `create_time` ASC) USING BTREE COMMENT '核心索引：按会话ID+时间查消息',
  INDEX `idx_sender_id`(`sender_id` ASC) USING BTREE,
  INDEX `idx_recv_type_id`(`recv_type` ASC, `recv_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 29 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '聊天消息主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_message_collection
-- ----------------------------
DROP TABLE IF EXISTS `t_message_collection`;
CREATE TABLE `t_message_collection`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏主键ID',
  `user_id` bigint NOT NULL COMMENT '收藏用户ID',
  `message_id` bigint NOT NULL COMMENT '被收藏消息ID',
  `collection_note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '收藏备注（可选）',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_msg`(`user_id` ASC, `message_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '消息收藏表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_message_pin
-- ----------------------------
DROP TABLE IF EXISTS `t_message_pin`;
CREATE TABLE `t_message_pin`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '置顶主键ID',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话ID',
  `message_id` bigint NOT NULL COMMENT '被置顶消息ID',
  `pin_user_id` bigint NOT NULL COMMENT '置顶操作人ID',
  `pin_order` int NULL DEFAULT 1 COMMENT '置顶顺序（越小越靠前）',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '置顶时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_conv_msg`(`conversation_id` ASC, `message_id` ASC) USING BTREE,
  INDEX `idx_conversation_id`(`conversation_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '消息置顶表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_message_read_receipt
-- ----------------------------
DROP TABLE IF EXISTS `t_message_read_receipt`;
CREATE TABLE `t_message_read_receipt`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '回执主键ID',
  `msg_id` bigint NOT NULL COMMENT '关联消息主表ID',
  `user_id` bigint NOT NULL COMMENT '已读/已送达的用户ID',
  `receipt_type` tinyint NOT NULL COMMENT '回执类型：1-已送达，2-已读',
  `receipt_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '回执时间',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_msg_user_type`(`msg_id` ASC, `user_id` ASC, `receipt_type` ASC) USING BTREE,
  INDEX `idx_msg_id`(`msg_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 20765 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '消息回执表（已读/已送达）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_message_report
-- ----------------------------
DROP TABLE IF EXISTS `t_message_report`;
CREATE TABLE `t_message_report`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '举报主键ID',
  `message_id` bigint NOT NULL COMMENT '被举报消息ID',
  `reporter_id` bigint NOT NULL COMMENT '举报人ID',
  `report_reason` tinyint NOT NULL COMMENT '举报原因：1-色情，2-暴力，3-骚扰，4-广告，5-诈骗，6-其他',
  `report_content` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '举报详细描述',
  `ai_check_result` tinyint NULL DEFAULT 0 COMMENT 'AI检查结果：0-待检查，1-违规，2-正常',
  `ai_check_time` datetime NULL DEFAULT NULL COMMENT 'AI检查时间',
  `ai_confidence` tinyint NULL DEFAULT 0 COMMENT 'AI置信度（0-100）',
  `admin_status` tinyint NULL DEFAULT 0 COMMENT '管理员处理状态：0-待处理，1-已处理，2-驳回',
  `admin_action` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '管理员处理动作描述',
  `admin_note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '管理员备注',
  `appeal_count` tinyint NULL DEFAULT 0 COMMENT '已申诉次数（0-3，超过3次不可再申诉）',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '举报时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_message_id`(`message_id` ASC) USING BTREE,
  INDEX `idx_reporter_id`(`reporter_id` ASC) USING BTREE,
  INDEX `idx_ai_check_result`(`ai_check_result` ASC) USING BTREE,
  INDEX `idx_admin_status`(`admin_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '消息举报表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_punish_msg_relation
-- ----------------------------
DROP TABLE IF EXISTS `t_punish_msg_relation`;
CREATE TABLE `t_punish_msg_relation`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联主键ID',
  `punish_log_id` bigint NOT NULL COMMENT '关联处罚主表ID',
  `msg_id` bigint NOT NULL COMMENT '关联违规消息ID',
  `ai_audit_result` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '大模型审核结果',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_punish_msg`(`punish_log_id` ASC, `msg_id` ASC) USING BTREE,
  INDEX `idx_msg_id`(`msg_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '处罚-违规消息关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_search_history
-- ----------------------------
DROP TABLE IF EXISTS `t_search_history`;
CREATE TABLE `t_search_history`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '历史主键ID',
  `user_id` bigint NOT NULL COMMENT '搜索用户ID',
  `search_type` tinyint NOT NULL COMMENT '搜索类型：1-用户搜索，2-团队搜索',
  `search_keyword` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '搜索关键词',
  `search_count` int NULL DEFAULT 1 COMMENT '搜索次数',
  `last_search_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后搜索时间',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_keyword`(`user_id` ASC, `search_type` ASC, `search_keyword` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_last_search_time`(`last_search_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '搜索历史表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_search_hot_keyword
-- ----------------------------
DROP TABLE IF EXISTS `t_search_hot_keyword`;
CREATE TABLE `t_search_hot_keyword`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '热词主键ID',
  `keyword` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '热门关键词',
  `search_type` tinyint NOT NULL COMMENT '搜索类型：1-用户，2-团队',
  `search_count` int NULL DEFAULT 0 COMMENT '搜索次数',
  `rank` int NULL DEFAULT 0 COMMENT '排名',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_keyword_type`(`keyword` ASC, `search_type` ASC) USING BTREE,
  INDEX `idx_rank`(`rank` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '热门搜索词表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_system_notice
-- ----------------------------
DROP TABLE IF EXISTS `t_system_notice`;
CREATE TABLE `t_system_notice`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '通知主键ID',
  `user_id` bigint NOT NULL COMMENT '接收通知的用户ID',
  `notice_type` tinyint NOT NULL COMMENT '通知类型：1-入群审批通过，2-入群审批拒绝，3-被移出团队，4-账号异常，5-反馈回复，6-处罚通知',
  `notice_content` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '通知内容',
  `related_id` bigint NULL DEFAULT NULL COMMENT '关联ID（申请人ID、团队ID、处罚ID等）',
  `is_read` tinyint NULL DEFAULT 0 COMMENT '是否已读：0-否，1-是',
  `read_time` datetime NULL DEFAULT NULL COMMENT '已读时间',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '通知创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_read`(`user_id` ASC, `is_read` ASC) USING BTREE,
  INDEX `idx_notice_type`(`notice_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统通知表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_team
-- ----------------------------
DROP TABLE IF EXISTS `t_team`;
CREATE TABLE `t_team`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '团队主键ID',
  `team_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '团队名称',
  `team_avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '团队头像URL',
  `team_intro` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '团队简介',
  `team_tags` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '团队标签（逗号分隔）',
  `creator_id` bigint NOT NULL COMMENT '团队创建人ID',
  `max_member` int NULL DEFAULT 200 COMMENT '团队最大成员数',
  `team_type` tinyint NOT NULL COMMENT '团队类型：1-公开，2-私有',
  `join_rule` tinyint NOT NULL COMMENT '加入规则：1-申请审批，2-仅邀请，3-密码加入',
  `join_password` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '加入密码（仅join_rule=3有效，BCrypt加密）',
  `team_all_mute` tinyint NULL DEFAULT 0 COMMENT '团队全员禁言：0-正常，1-禁言（队长/管理员除外）',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_creator_id`(`creator_id` ASC) USING BTREE,
  INDEX `idx_team_type`(`team_type` ASC) USING BTREE,
  INDEX `idx_team_name`(`team_name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '团队基础信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_team_apply
-- ----------------------------
DROP TABLE IF EXISTS `t_team_apply`;
CREATE TABLE `t_team_apply`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请主键ID',
  `team_id` bigint NOT NULL COMMENT '关联团队ID',
  `apply_user_id` bigint NOT NULL COMMENT '申请人ID',
  `audit_user_id` bigint NULL DEFAULT NULL COMMENT '审核人ID（队长/管理员）',
  `apply_msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '申请备注',
  `audit_status` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态：0-待审核，1-通过，2-拒绝',
  `audit_msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '审核备注',
  `audit_time` datetime NULL DEFAULT NULL COMMENT '审核时间',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_team_audit`(`team_id` ASC, `audit_status` ASC) USING BTREE,
  INDEX `idx_apply_user`(`apply_user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '团队加入申请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_team_member
-- ----------------------------
DROP TABLE IF EXISTS `t_team_member`;
CREATE TABLE `t_team_member`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '成员关联主键ID',
  `team_id` bigint NOT NULL COMMENT '关联团队ID',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `role_type` tinyint NOT NULL DEFAULT 3 COMMENT '角色类型：1-队长，2-管理员，3-普通成员，4-嘉宾',
  `join_source` tinyint NULL DEFAULT 1 COMMENT '加入来源：1-直接加入，2-邀请加入，3-申请审批，4-二维码加入，5-链接加入，6-密码加入',
  `invite_user_id` bigint NULL DEFAULT NULL COMMENT '邀请人ID（邀请加入时有值）',
  `team_mute_type` tinyint NULL DEFAULT 0 COMMENT '团队内禁言状态：0-正常，1-禁言',
  `team_mute_unpunish_time` datetime NULL DEFAULT NULL COMMENT '团队内禁言解除时间',
  `join_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入团队时间',
  `last_active_time` datetime NULL DEFAULT NULL COMMENT '最后活跃时间',
  `quit_time` datetime NULL DEFAULT NULL COMMENT '退出团队时间',
  `is_quit` tinyint NULL DEFAULT 0 COMMENT '是否退出团队：0-否，1-是',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_team_user`(`team_id` ASC, `user_id` ASC, `is_quit` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_role_type`(`role_type` ASC) USING BTREE,
  INDEX `idx_team_mute`(`team_id` ASC, `team_mute_type` ASC, `team_mute_unpunish_time` ASC) USING BTREE,
  INDEX `idx_team_role_type`(`team_id` ASC, `role_type` ASC) USING BTREE,
  INDEX `idx_team_join_time`(`team_id` ASC, `join_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '团队成员关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_team_recommendation
-- ----------------------------
DROP TABLE IF EXISTS `t_team_recommendation`;
CREATE TABLE `t_team_recommendation`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '推荐主键ID',
  `team_id` bigint NOT NULL COMMENT '被推荐团队ID',
  `recommend_to_user_id` bigint NOT NULL COMMENT '推荐给谁',
  `recommend_reason` tinyint NOT NULL COMMENT '推荐原因：1-标签相似，2-成员重叠，3-热门团队，4-新建团队',
  `recommend_score` tinyint NULL DEFAULT 0 COMMENT '推荐分数（0-100）',
  `is_clicked` tinyint NULL DEFAULT 0 COMMENT '是否被点击：0-否，1-是',
  `is_joined` tinyint NULL DEFAULT 0 COMMENT '是否加入：0-否，1-是',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_recommend_to_user`(`recommend_to_user_id` ASC) USING BTREE,
  INDEX `idx_recommend_score`(`recommend_score` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '团队推荐记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_team_report
-- ----------------------------
DROP TABLE IF EXISTS `t_team_report`;
CREATE TABLE `t_team_report`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '举报主键ID',
  `reporter_id` bigint NOT NULL COMMENT '举报人ID',
  `reported_team_id` bigint NOT NULL COMMENT '被举报团队ID',
  `report_reason` tinyint NOT NULL COMMENT '举报原因：1-违规内容，2-诈骗，3-骚扰，4-广告，5-其他',
  `report_content` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '举报详细描述',
  `report_evidence` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '举报证据',
  `report_status` tinyint NULL DEFAULT 0 COMMENT '处理状态：0-待审核，1-已处理，2-驳回，3-已关闭',
  `admin_action` tinyint NULL DEFAULT NULL COMMENT '管理员处理动作：1-警告，2-限制功能，3-解散团队，4-无处理',
  `admin_note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '管理员备注',
  `admin_id` bigint NULL DEFAULT NULL COMMENT '处理管理员ID',
  `process_time` datetime NULL DEFAULT NULL COMMENT '处理时间',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_reported_team_id`(`reported_team_id` ASC) USING BTREE,
  INDEX `idx_report_status`(`report_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '团队举报表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_user
-- ----------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户主键ID',
  `user_account` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '公开唯一账号（6-20位，字母/数字/下划线，用于搜索加好友、登录）',
  `user_email` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '私密邮箱（用于注册验证、找回密码、登录）',
  `user_nickname` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户昵称（可重复，用于展示）',
  `user_avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '用户头像URL',
  `user_intro` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '用户个人简介',
  `user_password` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录密码（BCrypt加密密文）',
  `user_tags` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '用户标签（逗号分隔）',
  `privacy_setting` json NULL COMMENT '隐私设置：view_info(1-所有人，2-仅团队成员)；send_msg(1-所有人，2-仅团队成员，3-需验证)；search_by_email(0-不允许通过邮箱搜索，1-允许)',
  `global_punish_type` tinyint NULL DEFAULT 0 COMMENT '全局处罚类型：0-无处罚，1-全局禁言，2-永久封号',
  `global_unpunish_time` datetime NULL DEFAULT NULL COMMENT '全局处罚解除时间（禁言有效，封号为NULL）',
  `last_login_time` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '最后登录IP',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_account`(`user_account` ASC) USING BTREE,
  UNIQUE INDEX `uk_user_email`(`user_email` ASC) USING BTREE,
  INDEX `idx_global_punish`(`global_punish_type` ASC, `global_unpunish_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户基础信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_user_blacklist
-- ----------------------------
DROP TABLE IF EXISTS `t_user_blacklist`;
CREATE TABLE `t_user_blacklist`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '黑名单主键ID',
  `user_id` bigint NOT NULL COMMENT '拉黑方用户ID',
  `black_user_id` bigint NOT NULL COMMENT '被拉黑方用户ID',
  `black_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '拉黑原因',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否解除拉黑：0-未解除，1-已解除',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '拉黑时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_black`(`user_id` ASC, `black_user_id` ASC, `is_delete` ASC) USING BTREE,
  INDEX `idx_black_user_id`(`black_user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户黑名单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_user_device
-- ----------------------------
DROP TABLE IF EXISTS `t_user_device`;
CREATE TABLE `t_user_device`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '设备主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `device_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '设备唯一标识（UUID）',
  `device_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '设备名称',
  `device_type` tinyint NOT NULL COMMENT '设备类型：1-Web，2-iOS，3-Android，4-Windows，5-Mac',
  `device_os` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '操作系统版本',
  `device_browser` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '浏览器（Web设备）',
  `device_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '设备IP',
  `device_location` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '设备位置',
  `last_login_time` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
  `is_trusted` tinyint NULL DEFAULT 0 COMMENT '是否信任：0-否，1-是',
  `is_active` tinyint NULL DEFAULT 1 COMMENT '是否在线：0-已下线，1-在线',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_device_id`(`device_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_last_login_time`(`user_id` ASC, `last_login_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户设备管理表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_user_feedback
-- ----------------------------
DROP TABLE IF EXISTS `t_user_feedback`;
CREATE TABLE `t_user_feedback`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '反馈主键ID',
  `user_id` bigint NOT NULL COMMENT '反馈用户ID',
  `feedback_type` tinyint NULL DEFAULT 1 COMMENT '反馈类型：1-功能问题，2-违规举报，3-处罚申诉，4-其他建议',
  `feedback_title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '反馈标题',
  `feedback_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '反馈详细内容',
  `feedback_attachment` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '附件URL（逗号分隔）',
  `feedback_img` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '反馈图片URL（逗号分隔）',
  `handle_user_id` bigint NULL DEFAULT NULL COMMENT '处理人ID（管理员）',
  `handle_status` tinyint NULL DEFAULT 0 COMMENT '处理状态：0-待处理，1-处理中，2-已解决，3-已驳回',
  `handle_content` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '处理结果/回复内容',
  `handle_time` datetime NULL DEFAULT NULL COMMENT '处理完成时间',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '反馈时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_handle`(`user_id` ASC, `handle_status` ASC) USING BTREE,
  INDEX `idx_handle_time`(`handle_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户反馈表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_user_friend
-- ----------------------------
DROP TABLE IF EXISTS `t_user_friend`;
CREATE TABLE `t_user_friend`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关系主键ID',
  `user_id` bigint NOT NULL COMMENT '发起好友申请的用户ID',
  `friend_id` bigint NOT NULL COMMENT '被添加的好友ID',
  `friend_remark` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '对好友的备注名',
  `friend_status` tinyint NOT NULL DEFAULT 0 COMMENT '关系状态：0-待验证，1-已成为好友，2-已拒绝，3-已拉黑',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `agree_time` datetime NULL DEFAULT NULL COMMENT '同意好友时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_friend_status`(`user_id` ASC, `friend_id` ASC, `friend_status` ASC) USING BTREE,
  INDEX `idx_friend_id`(`friend_id` ASC) USING BTREE,
  INDEX `idx_friend_status`(`friend_status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户好友关系表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_user_login_log
-- ----------------------------
DROP TABLE IF EXISTS `t_user_login_log`;
CREATE TABLE `t_user_login_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志主键ID',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `login_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录IP',
  `login_type` tinyint NOT NULL COMMENT '登录类型：1-账号密码，2-验证码',
  `login_result` tinyint NOT NULL COMMENT '登录结果：0-失败，1-成功',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户登录日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_user_punish_log
-- ----------------------------
DROP TABLE IF EXISTS `t_user_punish_log`;
CREATE TABLE `t_user_punish_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '处罚记录主键ID',
  `punish_user_id` bigint NOT NULL COMMENT '被处罚用户ID',
  `punish_type` tinyint NOT NULL COMMENT '处罚类型：1-单团队禁言，2-全局禁言，3-永久封号',
  `team_id` bigint NULL DEFAULT NULL COMMENT '关联团队ID（仅单团队禁言有值）',
  `punish_reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '处罚原因',
  `punish_duration` int NOT NULL COMMENT '禁言时长（单位：分钟，封号填-1）',
  `punish_start_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '处罚开始时间',
  `punish_end_time` datetime NULL DEFAULT NULL COMMENT '处罚结束时间（封号填NULL）',
  `operate_type` tinyint NOT NULL COMMENT '操作类型：1-系统自动，2-管理员手动',
  `operate_user_id` bigint NULL DEFAULT NULL COMMENT '操作人ID（系统自动填NULL）',
  `is_cancel` tinyint NULL DEFAULT 0 COMMENT '是否撤销处罚：0-未撤销，1-已撤销',
  `cancel_time` datetime NULL DEFAULT NULL COMMENT '处罚撤销时间',
  `cancel_user_id` bigint NULL DEFAULT NULL COMMENT '撤销人ID（管理员）',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_punish_user`(`punish_user_id` ASC, `punish_type` ASC) USING BTREE,
  INDEX `idx_punish_time`(`punish_start_time` ASC, `punish_end_time` ASC) USING BTREE,
  INDEX `idx_operate_type`(`operate_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户处罚主记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_user_recommendation
-- ----------------------------
DROP TABLE IF EXISTS `t_user_recommendation`;
CREATE TABLE `t_user_recommendation`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '推荐主键ID',
  `user_id` bigint NOT NULL COMMENT '被推荐用户ID',
  `recommend_to_user_id` bigint NOT NULL COMMENT '推荐给谁',
  `recommend_reason` tinyint NOT NULL COMMENT '推荐原因：1-标签相似，2-好友关系，3-聊天频繁，4-团队相同',
  `recommend_score` tinyint NULL DEFAULT 0 COMMENT '推荐分数（0-100）',
  `is_clicked` tinyint NULL DEFAULT 0 COMMENT '是否被点击：0-否，1-是',
  `is_added` tinyint NULL DEFAULT 0 COMMENT '是否被添加为好友：0-否，1-是',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_recommend_to_user`(`recommend_to_user_id` ASC) USING BTREE,
  INDEX `idx_recommend_score`(`recommend_score` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户推荐记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_user_report
-- ----------------------------
DROP TABLE IF EXISTS `t_user_report`;
CREATE TABLE `t_user_report`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '举报主键ID',
  `reporter_id` bigint NOT NULL COMMENT '举报人ID',
  `reported_user_id` bigint NOT NULL COMMENT '被举报用户ID',
  `report_reason` tinyint NOT NULL COMMENT '举报原因：1-骚扰，2-色情，3-暴力，4-广告，5-诈骗，6-其他',
  `report_content` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '举报详细描述',
  `report_evidence` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '举报证据（图片URL或消息ID，逗号分隔）',
  `ai_check_result` tinyint NULL DEFAULT 0 COMMENT 'AI检查结果：0-待检查，1-违规，2-正常',
  `ai_check_time` datetime NULL DEFAULT NULL COMMENT 'AI检查时间',
  `ai_confidence` tinyint NULL DEFAULT 0 COMMENT 'AI置信度（0-100）',
  `report_status` tinyint NULL DEFAULT 0 COMMENT '处理状态：0-待审核，1-已处理，2-驳回，3-已关闭',
  `admin_action` tinyint NULL DEFAULT NULL COMMENT '管理员处理动作：1-警告，2-禁言，3-封号，4-无处理',
  `admin_note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '管理员备注',
  `admin_id` bigint NULL DEFAULT NULL COMMENT '处理管理员ID',
  `process_time` datetime NULL DEFAULT NULL COMMENT '处理时间',
  `appeal_count` tinyint NULL DEFAULT 0 COMMENT '已申诉次数（0-3）',
  `is_delete` tinyint NULL DEFAULT 0 COMMENT '是否软删除：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_reported_user_id`(`reported_user_id` ASC) USING BTREE,
  INDEX `idx_report_status`(`report_status` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户举报表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for t_user_violation_count
-- ----------------------------
DROP TABLE IF EXISTS `t_user_violation_count`;
CREATE TABLE `t_user_violation_count`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '统计主键ID',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `total_violation_num` int NULL DEFAULT 0 COMMENT '累计违规总次数',
  `latest_violation_time` datetime NULL DEFAULT NULL COMMENT '最近一次违规时间',
  `reset_time` datetime NULL DEFAULT NULL COMMENT '违规次数重置时间',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_violation`(`user_id` ASC) USING BTREE,
  INDEX `idx_latest_violation`(`latest_violation_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户违规次数统计表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Triggers structure for table t_user
-- ----------------------------
DROP TRIGGER IF EXISTS `trg_t_user_default_privacy`;
delimiter ;;
CREATE TRIGGER `trg_t_user_default_privacy` BEFORE INSERT ON `t_user` FOR EACH ROW BEGIN
  IF NEW.`privacy_setting` IS NULL THEN
    SET NEW.`privacy_setting` = '{"view_info":1,"send_msg":1,"search_by_email":0}';
  END IF;
END
;;
delimiter ;

SET FOREIGN_KEY_CHECKS = 1;
