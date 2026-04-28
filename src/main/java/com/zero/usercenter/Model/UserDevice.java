package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户设备实体，对应表 `t_user_device`。
 */
@Data
@TableName("t_user_device")
public class UserDevice {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 设备唯一标识 */
    private String deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 设备类型 */
    private Integer deviceType;

    /** 操作系统 */
    private String deviceOs;

    /** 浏览器信息 */
    private String deviceBrowser;

    /** 登录IP */
    private String deviceIp;

    /** 登录地点 */
    private String deviceLocation;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;

    /** 是否信任设备（0-否，1-是） */
    private Integer isTrusted;

    /** 设备在线状态（0-离线，1-在线） */
    private Integer isActive;

    /** 软删除标记（0-未删，1-已删） */
    private Integer isDelete;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
