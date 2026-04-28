package com.zero.usercenter.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统通知 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemNoticeDTO {
    
    /**
     * 通知ID
     */
    private Long id;
    
    /**
     * 通知类型：1-好友申请，2-好友拒绝，3-入群审批通过，4-入群审批拒绝，5-被移出团队，6-账号异常，7-处罚通知，8-反馈回复
     */
    private Integer noticeType;
    
    /**
     * 通知内容
     */
    private String noticeContent;
    
    /**
     * 关联ID（申请人ID、团队ID、处罚ID等）
     */
    private Long relatedId;
    
    /**
     * 是否已读：0-否，1-是
     */
    private Integer isRead;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
