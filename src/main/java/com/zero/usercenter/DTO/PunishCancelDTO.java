package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 撤销处罚请求 DTO。
 */
@Data
public class PunishCancelDTO {

    /** 要撤销的处罚记录ID（对应 t_user_punish_log.id）。 */
    private Long punishLogId;

    /** 撤销原因（可选，便于审计留痕）。 */
    private String cancelReason;
}
