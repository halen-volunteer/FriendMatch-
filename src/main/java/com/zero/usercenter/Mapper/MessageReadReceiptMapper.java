package com.zero.usercenter.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zero.usercenter.Model.MessageReadReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息回执 Mapper。
 * 负责消息送达/已读回执的基础 CRUD 和批量幂等写入。
 */
@Mapper
public interface MessageReadReceiptMapper extends BaseMapper<MessageReadReceipt> {

    /**
     * 批量插入消息回执，利用 INSERT IGNORE 跳过唯一索引冲突。
     * 唯一索引：uk_msg_user_type (msg_id, user_id, receipt_type)
     *
     * @param msgIds      消息 ID 列表
     * @param userId      用户 ID
     * @param receiptType 回执类型（1-已送达，2-已读）
     * @param receiptTime 回执时间
     */
    void batchInsertIgnore(@Param("msgIds") List<Long> msgIds,
                           @Param("userId") Long userId,
                           @Param("receiptType") int receiptType,
                           @Param("receiptTime") LocalDateTime receiptTime);
}
