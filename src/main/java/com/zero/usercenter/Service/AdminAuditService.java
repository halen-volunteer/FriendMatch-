package com.zero.usercenter.Service;

/**
 * 管理员审计日志服务接口。
 */
public interface AdminAuditService {

    /**
     * 记录一次管理员操作审计日志。
     *
     * @param actionType 操作类型，如审核、封禁、解封等
     * @param actionTarget 操作目标标识，如用户、团队或举报单 ID
     * @param actionDetail 操作详情或补充说明
     */
    void log(String actionType, String actionTarget, String actionDetail);
}
