package com.zero.usercenter.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 项目通用常量。
 * 主要集中维护 Redis key、缓存时长、校验规则和处罚阈值，便于代码和文档保持一致。
 * 这类常量单独集中管理，可以减少业务代码里散落的“魔法值”，也能降低后续改 key 或改 TTL 的成本。
 */
public class Number {

    // ==================== Redis Key 规范（对齐设计文档）====================

    /** 图形验证码 Key，TTL: 1分钟 */
    public static final String REDIS_CAPTCHA_KEY = "captcha:";

    /** 邮箱验证码 Key，TTL: 5分钟 */
    public static final String REDIS_EMAIL_CODE_KEY = "verify_code:";

    /** 邮箱验证码发送冷却 Key，TTL: 60秒；解耦重发频率与验证码有效期 */
    public static final String REDIS_EMAIL_SEND_LIMIT_KEY = "send_limit:";

    /**
     * 用户登录 Token Key，TTL: 2小时
     * 格式：token:{token}
     * 用 Hash 存储用户基础信息，供拦截器反序列化为 UserFormat
     */
    public static final String TOKEN_KEY = "token:";

    /**
     * 用户当前唯一活跃 Token 索引 Key，TTL 与 token 保持一致
     * 格式：user_active_token:{userId}
     * value 为当前用户唯一允许生效的 token，用于限制同账号多端同时登录
     */
    public static final String USER_ACTIVE_TOKEN_KEY = "user_active_token:";

    /**
     * 登录串行化短锁 Key，TTL: 10 秒
     * 格式：user_login_lock:{userId}
     * 用于防止同一账号并发登录时同时通过校验，导致出现多个有效 token
     */
    public static final String USER_LOGIN_LOCK_KEY = "user_login_lock:";

    /** 忘记密码操作频率限制 Key，TTL: 1分钟 */
    public static final String REDIS_FORGET_PASSWORD_LIMIT_KEY = "forget_pwd_limit:";

    // ==================== Token 有效期（单位：分钟）====================

    /** 登录 Token 有效期：2小时 */
    public static final long TOKEN_TTL_MINUTES = 120L;

    /** 同一账号登录串行化短锁时长（秒） */
    public static final long USER_LOGIN_LOCK_TTL_SECONDS = 10L;

    /** WebSocket 握手票据 Key，格式：ws_ticket:{ticket} */
    public static final String REDIS_WS_TICKET_KEY = "ws_ticket:";

    /** WebSocket 握手票据有效期：60秒，一次性使用 */
    public static final long WS_TICKET_TTL_SECONDS = 60L;

    // ==================== 用户名 / 账号校验规则 ====================

    /** 系统保留用户名集合（注册和改名时校验） */
    public static final Set<String> RESERVED_NAMES = new HashSet<>(Arrays.asList(
            "admin", "root", "system", "guest", "test", "administrator"
    ));

    /** 用户昵称正则：中文+字母+数字+下划线，不能以下划线开头 */
    public static final String USERNAME_CN_REGEX = "^[\\u4e00-\\u9fa5a-zA-Z0-9][\\u4e00-\\u9fa5a-zA-Z0-9_]*$";

    // ==================== 邮箱校验规则 ====================

    /** 邮箱格式校验正则 */
    public static final String EMAIL_REGEX = "^[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*\\.[a-zA-Z]{2,6}$";

    /** 邮箱最大长度 */
    public static final int MAX_EMAIL_LENGTH = 128;

    // ==================== 好友/黑名单缓存 Key ====================

    /** 好友列表缓存 Key，TTL: 24小时，格式：friend_list:{userId} */
    public static final String FRIEND_LIST_CACHE_KEY = "friend_list:";

    /** 黑名单缓存 Key，TTL: 24小时，格式：blacklist:{userId} */
    public static final String BLACKLIST_CACHE_KEY = "blacklist:";

    /** 好友列表缓存有效期（小时） */
    public static final long FRIEND_LIST_CACHE_TTL_HOURS = 24L;

    /** 黑名单缓存有效期（小时） */
    public static final long BLACKLIST_CACHE_TTL_HOURS = 24L;

    // ==================== 团队缓存 Key ====================

    /** 团队全员禁言缓存 Key，TTL: 5分钟，格式：team_all_mute:{teamId} */
    public static final String TEAM_ALL_MUTE_KEY = "team_all_mute:";

    /** 团队成员禁言缓存 Key，TTL: 5分钟，格式：team_mute:{teamId}_{userId} */
    public static final String TEAM_MUTE_KEY = "team_mute:";

    /** 团队缓存有效期（分钟） */
    public static final long TEAM_MUTE_CACHE_TTL_MINUTES = 5L;

    // ==================== 聊天缓存 Key ====================

    /**
     * 会话未读消息数 Key（用户维度），格式：unread:{userId}:{conversationId}
     * 每个用户每个会话独立计数，发消息时对接收方自增，已读时只清零当前用户
     * 解决原 unread_count:{conversationId} 群聊共用 key 导致任意成员已读后全员未读数清零的问题
     */
    public static final String UNREAD_COUNT_KEY = "unread:";

    /** 群聊消息已读 Bitmap Key，TTL: 7天，格式：msg_read:{msgId} */
    public static final String MSG_READ_KEY = "msg_read:";

    /** 群聊消息已送达 Bitmap Key，TTL: 7天，格式：msg_deliver:{msgId} */
    public static final String MSG_DELIVER_KEY = "msg_deliver:";

    /** 待消费聊天消息缓存 Key，格式：chat_pending:message:{msgId} */
    public static final String CHAT_PENDING_MESSAGE_KEY = "chat_pending:message:";

    /** 待消费聊天消息操作缓存 Key，格式：chat_pending:op:{msgId} */
    public static final String CHAT_PENDING_OPERATION_KEY = "chat_pending:op:";

    /** 待消费聊天消息缓存有效期（分钟） */
    public static final long CHAT_PENDING_MESSAGE_TTL_MINUTES = 30L;

    /** 待消费聊天消息操作缓存有效期（分钟） */
    public static final long CHAT_PENDING_OPERATION_TTL_MINUTES = 30L;

    /** 消息撤回/编辑允许窗口（分钟） */
    public static final long CHAT_MESSAGE_EDIT_WINDOW_MINUTES = 5L;

    /** 用户全局禁言状态缓存 Key，TTL: 5分钟，格式：user_punish:{userId} */
    public static final String USER_PUNISH_KEY = "user_punish:";

    /** 消息 Bitmap 缓存有效期（天） */
    public static final long MSG_BITMAP_TTL_DAYS = 7L;

    /** 用户处罚缓存有效期（分钟） */
    public static final long USER_PUNISH_CACHE_TTL_MINUTES = 5L;

    /** 大群判定阈值：成员数超过该值后走降级推送和读优化链路。 */
    public static final int LARGE_TEAM_MEMBER_THRESHOLD = 500;

    /** 大群标记缓存 Key，格式：chat_large_team:{teamId}。 */
    public static final String LARGE_TEAM_FLAG_KEY = "chat_large_team:";

    /** 大群标记缓存有效期（分钟）。 */
    public static final long LARGE_TEAM_FLAG_TTL_MINUTES = 5L;

    /** 历史消息读取限流 Key，格式：chat_history_limit:{userId}:{conversationId}。 */
    public static final String CHAT_HISTORY_RATE_LIMIT_KEY = "chat_history_limit:";

    /** 历史消息读取限流窗口（秒）。 */
    public static final long CHAT_HISTORY_RATE_LIMIT_WINDOW_SECONDS = 5L;

    /** 单用户单会话在一个限流窗口内允许的历史消息读取次数。 */
    public static final long CHAT_HISTORY_RATE_LIMIT_MAX_REQUESTS = 12L;

    /** 会话最新一页历史缓存 Key，格式：chat_latest_history:{conversationId}。 */
    public static final String CHAT_LATEST_HISTORY_CACHE_KEY = "chat_latest_history:";

    /** 会话最新一页历史缓存有效期（秒）。 */
    public static final long CHAT_LATEST_HISTORY_CACHE_TTL_SECONDS = 30L;

    /** 会话最新一页历史缓存固定按 30 条构建，兼容 20~30 条的前端分页请求。 */
    public static final int CHAT_HISTORY_CACHE_PAGE_SIZE = 30;

    /** 大群平滑推送时每批最多处理的在线用户数。 */
    public static final int LARGE_TEAM_PUSH_BATCH_SIZE = 100;

    /** 大群平滑推送时相邻批次之间的间隔（毫秒）。 */
    public static final long LARGE_TEAM_PUSH_BATCH_DELAY_MILLIS = 50L;

    /** 群公告缓存 Key，格式：group_notice:{conversationId} */
    public static final String GROUP_NOTICE_KEY = "group_notice:";

    /** 群公告缓存有效期（天） */
    public static final long GROUP_NOTICE_TTL_DAYS = 30L;

    /**
     * 会话最后一条消息缓存 Key，格式：last_msg:{conversationId}
     * Hash 结构，存储 msgId/senderId/msgType/msgContent/createTime
     * 写入时机：sendPrivateMsg / sendTeamMsg 发消息成功后
     * TTL：30天（与群公告对齐，活跃会话会持续刷新）
     */
    public static final String LAST_MSG_CACHE_KEY = "last_msg:";

    /** 会话最后一条消息缓存有效期（天） */
    public static final long LAST_MSG_CACHE_TTL_DAYS = 30L;

    /**
     * 用户隐藏会话集合 Key，格式：hidden_conversations:{userId}
     * Set 结构，member=conversationId
     * 用于支持“从会话列表移除但不删除好友/团队关系”的轻量能力
     */
    public static final String HIDDEN_CONVERSATIONS_KEY = "hidden_conversations:";

    // ==================== 用户信息缓存 Key ====================

    /**
     * 用户基础信息缓存 Key，TTL: 5分钟，格式：user_info:{userId}
     * Hash 结构，存储 id/userAccount/userNickname/userAvatar/userIntro/userTags/privacySetting
     * 写入时机：searchUser 命中结果、getUserProfile 查库后
     * 清除时机：updateUserProfile 修改资料后
     */
    public static final String USER_INFO_CACHE_KEY = "user_info:";

    /** 用户信息缓存有效期（分钟） */
    public static final long USER_INFO_CACHE_TTL_MINUTES = 5L;

    // ==================== 用户在线状态 Key ====================

    /** 用户在线状态 Hash Key，field=userId，value=状态(1-在线,2-离开,3-忙碌,4-隐身) */
    public static final String USER_ONLINE_KEY = "user_online";

    /** 用户在线状态过期时间 ZSet Key，member=userId，score=过期时间戳 */
    public static final String USER_ONLINE_TTL_KEY = "user_online_ttl";

    /** 用户在线状态超时时间（分钟），超过此时间未心跳则视为离线 */
    public static final long USER_ONLINE_TIMEOUT_MINUTES = 5L;

    // ==================== 处罚梯度阈值 ====================

    /** 违规次数达到此值时永久封号 */
    public static final int VIOLATION_BAN_THRESHOLD = 4;

    /** 梯度禁言时长（分钟）：第1次60分钟，第2次1天，第3次7天 */
    public static final int[] GRADIENT_MUTE_DURATIONS = {60, 1440, 10080};
}
