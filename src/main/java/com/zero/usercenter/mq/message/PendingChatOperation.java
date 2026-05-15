package com.zero.usercenter.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 待消费聊天消息上的挂起操作。
 * 当消息已经入队、但消费者还未真正处理时，编辑/撤回请求不会改 MQ 队列本身，
 * 而是先把“最新操作”暂存到 Redis，等待消费者消费时读取并应用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingChatOperation {

    /** 编辑操作标记。 */
    public static final String TYPE_EDIT = "EDIT";

    /** 撤回操作标记。 */
    public static final String TYPE_REVOKE = "REVOKE";

    /** 当前挂起操作类型：EDIT / REVOKE。 */
    private String operationType;

    /** 编辑后的最新内容。只有 EDIT 场景会使用。 */
    private String editedContent;

    /** 操作发生时间。 */
    private LocalDateTime operateTime;
}
