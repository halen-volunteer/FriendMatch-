package com.zero.usercenter.Service.impl;

import com.zero.usercenter.Mapper.AdminAuditLogMapper;
import com.zero.usercenter.Model.AdminAuditLog;
import com.zero.usercenter.Service.AdminAuditService;
import com.zero.usercenter.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditServiceImpl implements AdminAuditService {

    @Resource
    private AdminAuditLogMapper adminAuditLogMapper;

    @Override
    public void log(String actionType, String actionTarget, String actionDetail) {
        Long userId = UserHolder.getUserId();
        if (userId == null) return;
        AdminAuditLog log = new AdminAuditLog();
        log.setAdminUserId(userId);
        log.setActionType(actionType);
        log.setActionTarget(actionTarget);
        log.setActionDetail(actionDetail);
        adminAuditLogMapper.insert(log);
    }
}
