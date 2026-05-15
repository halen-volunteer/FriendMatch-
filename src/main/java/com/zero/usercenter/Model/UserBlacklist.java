package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户黑名单实体。
 * 对应表 `t_user_blacklist`，用于记录拉黑关系及其恢复状态。
 */
@TableName(value = "t_user_blacklist")
@Data
public class UserBlacklist {

    /**
     * 黑名单主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 拉黑方用户ID
     */
    private Long userId;

    /**
     * 被拉黑方用户ID
     */
    private Long blackUserId;

    /**
     * 拉黑原因
     */
    private String blackReason;

    /**
     * 是否解除拉黑：0-未解除，1-已解除
     */
    private Integer isDelete;

    /**
     * 拉黑时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
