package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 用户隐私设置 DTO
 */
@Data
public class PrivacySettingDTO {

    /**
     * 资料可见性：1-所有人，2-仅团队成员，3-不可见
     */
    private Integer viewInfo;

    /**
     * 消息接收权限：1-所有人，2-仅团队成员，3-需验证
     */
    private Integer sendMsg;

    /**
     * 是否允许通过邮箱搜索：0-不允许，1-允许
     */
    private Integer searchByEmail;
}
