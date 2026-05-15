package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户好友关系实体。
 * 对应表 `t_user_friend`，用于记录申请态、好友态和拉黑态的关系快照。
 */
@TableName(value = "t_user_friend")
@Data
public class UserFriend {

    /**
     * 关系主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 发起好友申请的用户ID
     */
    private Long userId;

    /**
     * 被添加的好友ID
     */
    private Long friendId;

    /**
     * 对好友的备注名。
     */
    private String friendRemark;

    /**
     * 关系状态：0-待验证，1-已成为好友，2-已拒绝，3-已拉黑
     */
    private Integer friendStatus;

    /**
     * 申请时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 同意好友时间
     */
    private LocalDateTime agreeTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
