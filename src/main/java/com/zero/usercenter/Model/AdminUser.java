package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台管理员实体。
 * 这里不是独立账号体系，而是“普通用户 + 管理员扩展角色”的建模方式。
 */
@Data
@TableName("t_admin_user")
public class AdminUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String adminName;
    private Integer adminStatus;
    private Integer isDelete;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
