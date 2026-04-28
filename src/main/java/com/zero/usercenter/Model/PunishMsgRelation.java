package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 处罚-违规消息关联表
 * @TableName t_punish_msg_relation
 */
@TableName(value = "t_punish_msg_relation")
@Data
public class PunishMsgRelation {

    /** 关联主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联处罚主表ID */
    private Long punishLogId;

    /** 关联违规消息ID */
    private Long msgId;

    /** 大模型审核结果 */
    private String aiAuditResult;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
