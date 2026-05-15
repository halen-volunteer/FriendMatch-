package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 好友操作 DTO。
 */
@Data
public class FriendOperationDTO {

    /** 好友 ID。 */
    private Long friendId;

    /** 申请备注，可选。 */
    private String applyMsg;

    /** 好友备注名，可选。 */
    private String friendRemark;
}
