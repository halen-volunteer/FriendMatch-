package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户推荐记录实体，对应表 `t_user_recommendation`。
 */
@Data
@TableName("t_user_recommendation")
public class UserRecommendation {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被推荐用户ID */
    private Long userId;

    /** 接收推荐的用户ID */
    private Long recommendToUserId;

    /** 推荐原因（1-标签相似，2-好友关系，3-聊天频繁，4-团队相同） */
    private Integer recommendReason;

    /** 推荐分数 */
    private Integer recommendScore;

    /** 是否已点击（0-否，1-是） */
    private Integer isClicked;

    /** 是否已添加好友（0-否，1-是） */
    private Integer isAdded;

    /** 软删除标记（0-未删，1-已删） */
    private Integer isDelete;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
