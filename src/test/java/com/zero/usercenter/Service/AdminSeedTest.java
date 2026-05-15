package com.zero.usercenter.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zero.usercenter.Mapper.AdminUserMapper;
import com.zero.usercenter.Mapper.UserMapper;
import com.zero.usercenter.Model.AdminUser;
import com.zero.usercenter.Model.User;
import com.zero.usercenter.Service.impl.UserServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 本地初始化管理员测试。
 * 运行后会创建普通用户并补齐管理员身份，适合开发环境一次性造数。
 */
@SpringBootTest
public class AdminSeedTest {

    @Resource
    private UserServiceImpl userService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private AdminUserMapper adminUserMapper;

    @Test
    void createAdmins() {
        createAdminUser("111111", "user01@test.com", "用户01", "User123451");
        createAdminUser("222222", "user02@test.com", "用户02", "User123452");
        createAdminUser("333333", "user03@test.com", "用户03", "User123453");
    }

    private void createAdminUser(String account, String email, String nickname, String rawPassword) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, account)
                .eq(User::getIsDelete, 0)
                .last("LIMIT 1"));

        if (user == null) {
            user = new User();
            user.setUserAccount(account);
            user.setUserEmail(email);
            user.setUserNickname(nickname);
            user.setUserAvatar("");
            user.setUserIntro("");
            user.setUserPassword(rawPassword);
            user.setUserTags("");
            user.setPrivacySetting("{\"view_info\":1,\"send_msg\":1,\"search_by_email\":1}");
            user.setGlobalPunishType(0);
            user.setIsDelete(0);
            userService.save(user);

            user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUserAccount, account)
                    .eq(User::getIsDelete, 0)
                    .last("LIMIT 1"));
        }

        if (user == null) {
            throw new IllegalStateException("创建用户失败：" + account);
        }

        AdminUser adminUser = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getUserId, user.getId())
                .eq(AdminUser::getIsDelete, 0)
                .last("LIMIT 1"));

        if (adminUser != null) {
            return;
        }

        adminUser = new AdminUser();
        adminUser.setUserId(user.getId());
        adminUser.setAdminName(nickname + "_admin");
        adminUser.setAdminStatus(1);
        adminUser.setIsDelete(0);
        adminUserMapper.insert(adminUser);
    }
}
