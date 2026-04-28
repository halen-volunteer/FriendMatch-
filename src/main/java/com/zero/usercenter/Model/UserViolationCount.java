package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户违规次数统计表
 * @TableName t_user_violation_count
 */
@TableName(value = "t_user_violation_count")
@Data
public class UserViolationCount {

    /** 统计主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联用户ID */
    private Long userId;

    /** 累计违规总次数 */
    private Integer totalViolationNum;

    /** 最近一次违规时间 */
    private LocalDateTime latestViolationTime;

    /** 违规次数重置时间 */
    private LocalDateTime resetTime;

    /**
     * 记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
