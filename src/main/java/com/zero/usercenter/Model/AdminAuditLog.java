package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员审计日志实体。
 * 用于记录处罚、撤销处罚、工单处理等后台敏感操作，方便追溯谁在什么时间做了什么事。
 */
@Data
@TableName("t_admin_audit_log")
public class AdminAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long adminUserId;
    private String actionType;
    private String actionTarget;
    private String actionDetail;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
