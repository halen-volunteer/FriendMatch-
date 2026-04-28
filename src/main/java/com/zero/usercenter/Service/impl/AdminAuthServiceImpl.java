package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zero.usercenter.Mapper.AdminUserMapper;
import com.zero.usercenter.Model.AdminUser;
import com.zero.usercenter.Service.AdminAuthService;
import com.zero.usercenter.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    /** 管理员鉴权实现，基于 t_admin_user 表查询。 */

    @Resource
    private AdminUserMapper adminUserMapper;

    @Override
    public boolean isAdmin(Long userId) {
        if (userId == null) return false;
        LambdaQueryWrapper<AdminUser> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminUser::getUserId, userId)
          .eq(AdminUser::getAdminStatus, 1)
          .eq(AdminUser::getIsDelete, 0);
        return adminUserMapper.selectCount(qw) > 0;
    }

    @Override
    public void assertAdmin(Long userId) {
        if (!isAdmin(userId)) throw new BusinessException("仅管理员可操作");
    }
}
