package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zero.usercenter.Mapper.AdminUserMapper;
import com.zero.usercenter.Model.AdminUser;
import com.zero.usercenter.Service.AdminAuthService;
import com.zero.usercenter.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 管理员鉴权服务实现。
 * 基于管理员表判断某个普通用户当前是否拥有启用中的后台管理身份。
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    @Resource
    private AdminUserMapper adminUserMapper;

    /**
     * 判断用户是否为管理员。
     *
     * @param userId 用户 ID
     * @return true 表示当前拥有启用中的管理员身份
     */
    @Override
    public boolean isAdmin(Long userId) {
        // 1. 管理员身份判断的唯一依据是 t_admin_user 中存在“启用且未删除”的记录。
        if (userId == null) return false;
        LambdaQueryWrapper<AdminUser> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminUser::getUserId, userId)
          .eq(AdminUser::getAdminStatus, 1)
          .eq(AdminUser::getIsDelete, 0);
        return adminUserMapper.selectCount(qw) > 0;
    }

    /**
     * 断言用户必须具备管理员身份。
     *
     * @param userId 用户 ID
     */
    @Override
    public void assertAdmin(Long userId) {
        // 1. 统一抛出业务异常，方便控制层和 AOP 直接复用这一条鉴权规则。
        if (!isAdmin(userId)) throw new BusinessException("仅管理员可操作");
    }
}
