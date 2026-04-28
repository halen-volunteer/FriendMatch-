package com.zero.usercenter.Service;

public interface AdminAuditService {
    void log(String actionType, String actionTarget, String actionDetail);
}
