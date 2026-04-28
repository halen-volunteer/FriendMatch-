# FriendMatch 数据库设计说明（基于 `SQL/friendmatch.sql`）

> 数据来源：`SQL/friendmatch.sql`
> 生成日期：2026-04-05
> 说明：本文档严格以当前 SQL 建表脚本为准，不再沿用历史数据库设计草稿。

---

## 一、数据库概览

当前数据库为 `friendmatch`，基于 `friendmatch.sql` 可确认：

- **表数量**：26 张
- **触发器数量**：1 个
- **数据库引擎**：InnoDB
- **字符集**：`utf8mb4`
- **排序规则**：`utf8mb4_0900_ai_ci`
- **主键类型**：`bigint` 自增为主
- **删除策略**：大量业务表采用 `is_delete` 软删除
- **外键策略**：SQL 中未建立物理外键，主要通过业务层维护关联一致性

---

## 二、表分组总览

### 2.1 账号与用户域

- `t_user`
- `t_user_login_log`
- `t_user_friend`
- `t_user_blacklist`
- `t_user_device`

### 2.2 团队域

- `t_team`
- `t_team_apply`
- `t_team_member`

### 2.3 聊天基础域

- `t_chat_message`
- `t_message_read_receipt`

### 2.4 聊天增强与消息治理域

- `t_message_collection`
- `t_message_pin`
- `t_message_report`

### 2.5 平台治理与安全域

- `t_admin_user`
- `t_system_notice`
- `t_user_punish_log`
- `t_user_violation_count`
- `t_punish_msg_relation`
- `t_user_report`
- `t_team_report`
- `t_appeal`
- `t_user_feedback`

### 2.6 搜索与推荐域

- `t_search_history`
- `t_search_hot_keyword`
- `t_user_recommendation`
- `t_team_recommendation`

---

## 三、核心表设计说明

## 3.1 `t_user` 用户基础信息表

**用途**：存储用户账号、邮箱、昵称、密码、隐私配置与全局处罚状态。

**关键字段**：

- `user_account`：公开唯一账号
- `user_email`：私密邮箱
- `user_nickname`：昵称
- `user_password`：BCrypt 密码
- `user_tags`：用户标签
- `privacy_setting`：JSON 隐私配置
- `global_punish_type`：全局处罚类型（0/1/2）
- `global_unpunish_time`：处罚解除时间
- `last_login_time` / `last_login_ip`：最后登录信息

**关键索引**：

- `uk_account`
- `uk_user_email`
- `idx_global_punish`

**说明**：

- 存在触发器 `trg_t_user_default_privacy`，在插入时为 `privacy_setting` 注入默认值。
- `privacy_setting` 当前为 JSON 字段，是用户隐私规则的核心来源。

---

## 3.2 `t_user_login_log` 用户登录日志表

**用途**：记录登录行为，用于审计、安全分析、异常登录追踪。

**关键字段**：

- `user_id`
- `login_ip`
- `login_type`
- `login_result`
- `create_time`

**关键索引**：

- `idx_user_id`

---

## 3.3 `t_user_friend` 用户好友关系表

**用途**：存储好友申请与好友关系。

**关键字段**：

- `user_id`
- `friend_id`
- `friend_remark`
- `friend_status`
- `agree_time`

**关键索引**：

- `uk_user_friend_status`
- `idx_friend_id`
- `idx_friend_status`

**说明**：

- 当前表结构支持“申请中 / 已同意 / 已拒绝 / 已拉黑”等状态。
- 没有物理外键，好友关系一致性由业务层维护。

---

## 3.4 `t_user_blacklist` 用户黑名单表

**用途**：存储拉黑关系。

**关键字段**：

- `user_id`
- `black_user_id`
- `black_reason`
- `is_delete`

**关键索引**：

- `uk_user_black`
- `idx_black_user_id`

---

## 3.5 `t_user_device` 用户设备管理表

**用途**：记录登录设备与信任状态。

**关键字段**：

- `device_id`
- `device_name`
- `device_type`
- `device_os`
- `device_browser`
- `device_ip`
- `device_location`
- `last_login_time`
- `is_trusted`
- `is_active`

**关键索引**：

- `uk_device_id`
- `idx_user_id`
- `idx_last_login_time`

---

## 3.6 `t_team` 团队基础信息表

**用途**：存储团队主体信息和加入规则。

**关键字段**：

- `team_name`
- `team_avatar`
- `team_intro`
- `team_tags`
- `creator_id`
- `max_member`
- `team_type`
- `join_rule`
- `join_password`
- `team_all_mute`

**关键索引**：

- `idx_creator_id`
- `idx_team_type`
- `idx_team_name`

---

## 3.7 `t_team_apply` 团队申请表

**用途**：记录加入团队申请与审批结果。

**关键字段**：

- `team_id`
- `apply_user_id`
- `audit_user_id`
- `apply_msg`
- `audit_status`
- `audit_msg`
- `audit_time`

**关键索引**：

- `idx_team_audit`
- `idx_apply_user`

---

## 3.8 `t_team_member` 团队成员表

**用途**：存储团队成员关系、角色、禁言状态、加入来源。

> 校准说明：`friendmatch.sql` 在字段注释中预留了 `role_type=4`、`join_source=4/5/6` 等扩展值，但当前 Java 业务代码实际使用的角色范围为 `1/2/3`，实际写入的加入来源主要为 `1/2/3`。文档阅读时应区分“库表预留”与“当前实现已启用”。

**关键字段**：

- `team_id`
- `user_id`
- `role_type`
- `join_source`
- `invite_user_id`
- `team_mute_type`
- `team_mute_unpunish_time`
- `join_time`
- `last_active_time`
- `quit_time`
- `is_quit`

**关键索引**：

- `uk_team_user`
- `idx_user_id`
- `idx_role_type`
- `idx_team_mute`
- `idx_team_role_type`
- `idx_team_join_time`

---

## 3.9 `t_chat_message` 聊天消息主表

**用途**：存储私聊与群聊消息。

**关键字段**：

- `sender_id`
- `recv_type`
- `recv_id`
- `conversation_id`
- `msg_type`
- `msg_content`
- `is_edited`
- `edit_time`
- `edit_count`
- `is_delete`

**关键索引**：

- `idx_conversation_create_time`
- `idx_sender_id`
- `idx_recv_type_id`

**说明**：

- `conversation_id` 是消息查询主维度。
- `msg_type` 已支持：文本、图片、文件、表情包、@消息。

---

## 3.10 `t_message_read_receipt` 消息回执表

**用途**：记录送达与已读状态。

**关键字段**：

- `msg_id`
- `user_id`
- `receipt_type`
- `receipt_time`

**关键索引**：

- `uk_msg_user_type`
- `idx_msg_id`
- `idx_user_id`

---

## 3.11 `t_message_collection` 消息收藏表

**用途**：记录用户收藏消息。

**关键字段**：

- `user_id`
- `message_id`
- `collection_note`

**关键索引**：

- `uk_user_msg`
- `idx_user_id`

---

## 3.12 `t_message_pin` 消息置顶表

**用途**：记录会话内置顶消息。

**关键字段**：

- `conversation_id`
- `message_id`
- `pin_user_id`
- `pin_order`

**关键索引**：

- `uk_conv_msg`
- `idx_conversation_id`

---

## 3.13 `t_message_report` 消息举报表

**用途**：记录消息举报、AI 检测结果和管理员处理状态。

**关键字段**：

- `message_id`
- `reporter_id`
- `report_reason`
- `report_content`
- `ai_check_result`
- `ai_check_time`
- `ai_confidence`
- `admin_status`
- `admin_action`
- `admin_note`
- `appeal_count`

**关键索引**：

- `idx_message_id`
- `idx_reporter_id`
- `idx_ai_check_result`
- `idx_admin_status`

---

## 3.14 `t_admin_user` 管理员表

**用途**：平台管理员身份与状态管理。

**关键字段**：

- `user_id`
- `admin_name`
- `admin_status`

**关键索引**：

- `uk_user_id`
- `idx_admin_status`

---

## 3.15 `t_system_notice` 系统通知表

**用途**：存储面向用户的系统通知。

**关键字段**：

- `user_id`
- `notice_type`
- `notice_content`
- `related_id`
- `is_read`
- `read_time`

**关键索引**：

- `idx_user_read`
- `idx_notice_type`

---

## 3.16 `t_user_punish_log` 用户处罚主记录表

**用途**：记录禁言、封号等处罚信息。

**关键字段**：

- `punish_user_id`
- `punish_type`
- `team_id`
- `punish_reason`
- `punish_duration`
- `punish_start_time`
- `punish_end_time`
- `operate_type`
- `operate_user_id`
- `is_cancel`
- `cancel_time`
- `cancel_user_id`

**关键索引**：

- `idx_punish_user`
- `idx_punish_time`
- `idx_operate_type`

---

## 3.17 `t_user_violation_count` 用户违规统计表

**用途**：用于记录累计违规次数，支撑梯度处罚。

**关键字段**：

- `user_id`
- `total_violation_num`
- `latest_violation_time`
- `reset_time`

**关键索引**：

- `uk_user_violation`
- `idx_latest_violation`

---

## 3.18 `t_punish_msg_relation` 处罚-消息关联表

**用途**：将处罚记录与具体违规消息关联。

**关键字段**：

- `punish_log_id`
- `msg_id`
- `ai_audit_result`

**关键索引**：

- `uk_punish_msg`
- `idx_msg_id`

---

## 3.19 `t_user_report` 用户举报表

**用途**：记录举报用户行为。

**关键字段**：

- `reporter_id`
- `reported_user_id`
- `report_reason`
- `report_content`
- `report_evidence`
- `ai_check_result`
- `report_status`
- `admin_action`
- `admin_note`
- `admin_id`
- `process_time`
- `appeal_count`

**关键索引**：

- `idx_reported_user_id`
- `idx_report_status`
- `idx_create_time`

---

## 3.20 `t_team_report` 团队举报表

**用途**：记录举报团队行为。

**关键字段**：

- `reporter_id`
- `reported_team_id`
- `report_reason`
- `report_content`
- `report_evidence`
- `report_status`
- `admin_action`
- `admin_note`
- `admin_id`
- `process_time`

**关键索引**：

- `idx_reported_team_id`
- `idx_report_status`

---

## 3.21 `t_appeal` 申诉表

**用途**：记录围绕举报/处罚发起的申诉。

**关键字段**：

- `appellant_id`
- `appellant_type`
- `related_report_id`
- `related_report_type`
- `related_punish_id`
- `appeal_round`
- `appeal_reason`
- `appeal_evidence`
- `appeal_status`
- `admin_id`
- `admin_reply`
- `process_time`

**关键索引**：

- `idx_appellant_id`
- `idx_related_report`
- `idx_appeal_status`
- `idx_admin_id`

---

## 3.22 `t_user_feedback` 用户反馈表

**用途**：记录功能问题、违规举报、建议反馈等。

**关键字段**：

- `user_id`
- `feedback_type`
- `feedback_title`
- `feedback_content`
- `feedback_attachment`
- `feedback_img`
- `handle_user_id`
- `handle_status`
- `handle_content`
- `handle_time`

**关键索引**：

- `idx_user_handle`
- `idx_handle_time`

---

## 3.23 `t_search_history` 搜索历史表

**用途**：记录用户搜索行为。

**关键字段**：

- `user_id`
- `search_type`
- `search_keyword`
- `search_count`
- `last_search_time`
- `is_delete`

**关键索引**：

- `uk_user_keyword`
- `idx_user_id`
- `idx_last_search_time`

---

## 3.24 `t_search_hot_keyword` 热门搜索词表

**用途**：记录热搜关键词与热度。

**关键字段**：

- `keyword`
- `search_type`
- `search_count`
- `rank`
- `is_delete`

**关键索引**：

- `uk_keyword_type`
- `idx_rank`

---

## 3.25 `t_user_recommendation` 用户推荐记录表

**用途**：记录推荐给用户的“可能认识的人”。

**关键字段**：

- `user_id`
- `recommend_to_user_id`
- `recommend_reason`
- `recommend_score`
- `is_clicked`
- `is_added`
- `is_delete`

**关键索引**：

- `idx_recommend_to_user`
- `idx_recommend_score`

---

## 3.26 `t_team_recommendation` 团队推荐记录表

**用途**：记录推荐给用户的团队。

**关键字段**：

- `team_id`
- `recommend_to_user_id`
- `recommend_reason`
- `recommend_score`
- `is_clicked`
- `is_joined`
- `is_delete`

**关键索引**：

- `idx_recommend_to_user`
- `idx_recommend_score`

---

## 四、触发器说明

### 4.1 `trg_t_user_default_privacy`

**作用**：在插入 `t_user` 记录前，若 `privacy_setting` 为空，则自动设置默认值：

```json
{"view_info":1,"send_msg":1,"search_by_email":0}
```

**说明**：

- 该触发器保证新增用户即便未显式写隐私配置，也具备可用默认值。
- 这是当前 SQL 中唯一的触发器。

---

## 五、设计特征总结

从 `friendmatch.sql` 可以看出当前数据库设计具有以下特征：

1. **按业务域拆分清晰**：用户、团队、聊天、治理、搜索推荐分组明确。
2. **消息治理闭环完整**：消息举报、用户举报、团队举报、处罚、申诉形成链路。
3. **推荐系统已落表**：搜索历史、热词、用户推荐、团队推荐均已具备实体表。
4. **大量采用软删除**：方便保留历史记录与审计信息。
5. **无物理外键**：灵活性更高，但对业务层一致性要求更高。
6. **索引设计偏实用**：围绕查询主链路建立组合索引，如消息会话、举报状态、推荐分数等。

---

## 六、与文档体系的对应关系

- `Part1`：`t_user`、`t_user_login_log`
- `Part2`：`t_user_friend`、`t_user_blacklist`
- `Part3`：`t_team`、`t_team_apply`、`t_team_member`
- `Part4`：`t_chat_message`、`t_message_read_receipt`
- `Part5`：`t_admin_user`、`t_system_notice`、`t_user_punish_log`、`t_user_violation_count`、`t_punish_msg_relation`、`t_user_report`、`t_team_report`、`t_appeal`、`t_user_feedback`、`t_user_device`
- `Part6`：`t_message_collection`、`t_message_pin`、`t_message_report`
- `Part7`：`t_search_history`、`t_search_hot_keyword`、`t_user_recommendation`、`t_team_recommendation`

---

## 七、结论

当前数据库已经不是早期草稿阶段，而是一套**能够支撑 FriendMatch 1~7 模块主线的完整业务库结构**。后续若再更新表结构，应以 `SQL/friendmatch.sql` 为唯一事实来源，并同步更新本文档。
