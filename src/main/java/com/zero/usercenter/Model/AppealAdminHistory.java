package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 申诉管理员流转历史实体，对应表 `t_appeal_admin_history`。
 */
@Data
@TableName("t_appeal_admin_history")
public class AppealAdminHistory {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申诉ID */
    private Long appealId;

    /** 关联举报ID */
    private Long relatedReportId;

    /** 关联举报类型 */
    private Integer relatedReportType;

    /** 申诉轮次 */
    private Integer appealRound;

    /** 管理员ID */
    private Long adminId;

    /** 流转动作：assign/approve/reject/reassign */
    private String actionType;

    /** 处理结果备注 */
    private String actionNote;

    /** 是否软删除：0-否，1-是 */
    private Integer isDelete;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
