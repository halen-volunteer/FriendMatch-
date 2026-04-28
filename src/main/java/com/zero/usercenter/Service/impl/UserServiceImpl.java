package com.zero.usercenter.Service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zero.usercenter.DTO.LoginDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.DTO.UserFormat;
import com.zero.usercenter.Enums.EmailTemplateEnum;
import com.zero.usercenter.Mapper.AdminUserMapper;
import com.zero.usercenter.Mapper.UserLoginLogMapper;
import com.zero.usercenter.Mapper.UserMapper;
import com.zero.usercenter.Model.AdminUser;
import com.zero.usercenter.Model.User;
import com.zero.usercenter.Model.UserLoginLog;
import com.zero.usercenter.Service.UserService;
import com.zero.usercenter.utils.EmailApi;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.zero.usercenter.utils.Number.*;

/**
 * 用户认证 Service 实现类
 * 负责用户注册、登录、登出、验证码发送、找回密码等认证相关业务逻辑
 * 重写 save/updateById 以自动进行 BCrypt 密码加密
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private EmailApi emailApi;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserLoginLogMapper userLoginLogMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Resource
    private UserValidationSupport userValidationSupport;
    @Resource
    private UserAccountGeneratorSupport userAccountGeneratorSupport;

    // ==================== 密码加密 ====================

    /**
     * 重写 save，入库前自动 BCrypt 加密密码
     */
    @Override
    public boolean save(User entity) {
        encodePasswordIfNecessary(entity);
        return super.save(entity);
    }

    /**
     * 重写 updateById，更新密码时自动加密
     */
    @Override
    public boolean updateById(User entity) {
        encodePasswordIfNecessary(entity);
        return super.updateById(entity);
    }

    /**
     * 将明文密码转换为 BCrypt 密文（避免重复加密）
     */
    private void encodePasswordIfNecessary(User user) {
        String rawPassword = user.getUserPassword();
        if (rawPassword == null || rawPassword.isBlank()) return;
        if (rawPassword.startsWith("$2a$") || rawPassword.startsWith("$2b$") || rawPassword.startsWith("$2y$")) return;
        user.setUserPassword(PASSWORD_ENCODER.encode(rawPassword));
    }

    // ==================== 登录 ====================

    /**
     * 用户登录
     * 1. 参数校验
     * 2. 图形验证码校验
     * 3. 查询用户（支持 user_account 或 user_email 登录）
     * 4. 永久封号检测（global_punish_type == 2）
     * 5. BCrypt 密码比对
     * 6. 异步更新 last_login_time / last_login_ip
     * 7. 异步写入登录日志
     * 8. 生成 Token 存入 Redis，返回 token
     */
    @Override
    public Result userLogin(LoginDTO loginDTO, HttpServletRequest request) {
        String userAccount = loginDTO.getUserAccount();
        String userPassword = loginDTO.getUserPassword();
        String captchaId = loginDTO.getCaptchaID();
        String checkNumber = loginDTO.getCheckNumber();

        // 1. 判空
        if (StrUtil.isBlank(userAccount)) return Result.fail("账户为空");
        if (StrUtil.isBlank(userPassword)) return Result.fail("密码为空");
        if (StrUtil.isBlank(checkNumber)) return Result.fail("验证码为空");
        if (StrUtil.isBlank(captchaId)) return Result.fail("验证码未生成");

        userAccount = userAccount.trim();
        userPassword = userPassword.trim();
        checkNumber = checkNumber.trim();

        // 2. 账号/邮箱格式校验（满足其中一种即可）
        boolean isAccountFormat = checkUserAccount(userAccount).getSuccess();
        boolean isEmailFormat = checkEmail(userAccount).getSuccess();
        if (!isAccountFormat && !isEmailFormat) {
            return Result.fail("账户/邮箱格式错误");
        }

        // 3. 密码格式校验
        Result passwordCheck = checkUserPassword(userPassword);
        if (Boolean.FALSE.equals(passwordCheck.getSuccess())) return passwordCheck;

        // 4. 图形验证码校验
        String captchaKey = REDIS_CAPTCHA_KEY + captchaId;
        String captchaCode = stringRedisTemplate.opsForValue().get(captchaKey);
        if (StrUtil.isBlank(captchaCode)) return Result.fail("验证码已过期");
        if (!captchaCode.equalsIgnoreCase(checkNumber)) return Result.fail("验证码错误");
        // 校验通过后立即删除，防止重放攻击
        stringRedisTemplate.delete(captchaKey);

        // 5. 查询用户（支持 user_account 或 user_email 登录）
        final String loginInput = userAccount;
        User user = this.lambdaQuery()
                .eq(User::getIsDelete, 0)
                .and(w -> w.eq(User::getUserAccount, loginInput)
                           .or()
                           .eq(User::getUserEmail, loginInput))
                .one();
        if (user == null) return Result.fail("账户不存在");

        // 6. 永久封号检测（global_punish_type == 2 才拒绝登录；禁言=1 允许登录）
        if (Integer.valueOf(2).equals(user.getGlobalPunishType())) {
            return Result.fail("账号已被永久封禁，如有疑问请联系管理员");
        }

        // 7. BCrypt 密码比对
        if (!PASSWORD_ENCODER.matches(userPassword, user.getUserPassword())) {
            // 异步记录登录失败日志
            asyncWriteLoginLog(user.getId(), getClientIp(request), 0);
            return Result.fail("账户或密码错误");
        }

        // 8. 登录成功——异步更新登录信息和日志，不阻塞主流程
        final Long userId = user.getId();
        final String clientIp = getClientIp(request);
        asyncUpdateLoginInfo(userId, clientIp);
        asyncWriteLoginLog(userId, clientIp, 1);

        // 9. 生成 Token，写入 Redis Hash（Key: token:{token}，TTL: 2小时）
        String token = cn.hutool.core.lang.UUID.randomUUID().toString(true);
        UserFormat userFormat = new UserFormat();
        BeanUtils.copyProperties(user, userFormat);
        AdminUser adminUser = findActiveAdmin(user.getId());
        userFormat.setIsAdmin(adminUser != null);
        if (adminUser != null) {
            userFormat.setAdminId(adminUser.getId());
            userFormat.setAdminName(adminUser.getAdminName());
        }

        java.util.Map<Object, Object> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(userFormat.getId()));
        userMap.put("userAccount", userFormat.getUserAccount() == null ? "" : userFormat.getUserAccount());
        userMap.put("userNickname", userFormat.getUserNickname() == null ? "" : userFormat.getUserNickname());
        userMap.put("userAvatar", userFormat.getUserAvatar() == null ? "" : userFormat.getUserAvatar());
        userMap.put("userEmail", userFormat.getUserEmail() == null ? "" : userFormat.getUserEmail());
        userMap.put("userTags", userFormat.getUserTags() == null ? "" : userFormat.getUserTags());
        userMap.put("userIntro", userFormat.getUserIntro() == null ? "" : userFormat.getUserIntro());
        userMap.put("isAdmin", String.valueOf(Boolean.TRUE.equals(userFormat.getIsAdmin())));
        userMap.put("adminId", userFormat.getAdminId() == null ? "" : String.valueOf(userFormat.getAdminId()));
        userMap.put("adminName", userFormat.getAdminName() == null ? "" : userFormat.getAdminName());

        String redisKey = TOKEN_KEY + token;
        stringRedisTemplate.opsForHash().putAll(redisKey, userMap);
        // TTL 加随机抖动（±10分钟），防止大量 Token 同时过期引发雪崩
        long tokenTtl = TOKEN_TTL_MINUTES + ThreadLocalRandom.current().nextLong(-10, 11);
        stringRedisTemplate.expire(redisKey, tokenTtl, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    private AdminUser findActiveAdmin(Long userId) {
        LambdaQueryWrapper<AdminUser> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminUser::getUserId, userId)
          .eq(AdminUser::getAdminStatus, 1)
          .eq(AdminUser::getIsDelete, 0)
          .last("limit 1");
        return adminUserMapper.selectOne(qw);
    }

    // ==================== 注册 ====================

    /**
     * 用户注册
     * 1. 参数校验（用户名/邮箱/密码/验证码）
     * 2. Redis 验证码校验
     * 3. 分布式锁防并发重复注册
     * 4. 邮箱唯一性检查
     * 5. 雪花ID生成 user_account
     * 6. 写入 t_user
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result userRegister(String username, String email, String emailCode, String password) {
        // 判空
        if (StrUtil.isBlank(username)) return Result.fail("用户名为空");
        if (StrUtil.isBlank(email)) return Result.fail("邮箱为空");
        if (StrUtil.isBlank(emailCode)) return Result.fail("验证码为空");
        if (StrUtil.isBlank(password)) return Result.fail("密码为空");

        username = username.trim();
        email = email.trim();
        emailCode = emailCode.trim();
        password = password.trim();

        // 参数格式校验
        Result resultUsername = checkUsername(username);
        if (Boolean.FALSE.equals(resultUsername.getSuccess())) return resultUsername;

        Result checkPwd = checkUserPassword(password);
        if (Boolean.FALSE.equals(checkPwd.getSuccess())) return checkPwd;

        Result checkEmailResult = checkEmail(email);
        if (Boolean.FALSE.equals(checkEmailResult.getSuccess())) return checkEmailResult;

        // 验证码校验
        String checkCode = stringRedisTemplate.opsForValue().get(REDIS_EMAIL_CODE_KEY + email);
        if (StrUtil.isBlank(checkCode)) return Result.fail("验证码已过期");
        if (!checkCode.equals(emailCode)) return Result.fail("验证码错误");

        // 分布式锁防并发重复注册
        String lockKey = "lock:user:email:" + email;
        String lockValue = cn.hutool.core.lang.UUID.randomUUID().toString();
        try {
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(locked)) return Result.fail("操作太频繁，请稍后重试");

            // 邮箱唯一性检查（加锁后再次确认）
            if (this.exists(new LambdaQueryWrapper<User>().eq(User::getUserEmail, email))) {
                return Result.fail("当前邮箱已注册");
            }

            // 生成账号
            String account = generateSnowflakeAccount();

            User user = new User();
            user.setUserAccount(account);
            user.setUserNickname(username);
            user.setUserEmail(email);
            user.setUserPassword(password);
            user.setGlobalPunishType(0);
            user.setIsDelete(0);

            boolean saveSuccess = this.save(user);
            if (!saveSuccess) return Result.fail("注册失败，请稍后重试");

            // 注册成功，删除 Redis 验证码
            stringRedisTemplate.delete(REDIS_EMAIL_CODE_KEY + email);

            UserFormat userFormat = new UserFormat();
            BeanUtils.copyProperties(user, userFormat);
            return Result.ok(userFormat);

        } catch (Exception e) {
            log.error("用户注册失败，邮箱：{}", email, e);
            return Result.fail("系统异常，请联系管理员");
        } finally {
            releaseRedisLock(lockKey, lockValue);
        }
    }

    // ==================== 发送验证码 ====================

    /**
     * 发送邮箱验证码
     * 引入独立发送冷却 Key（send_limit:{email}，TTL=60s），解耦重发频率与验证码有效期：
     * - 60s 内不能重复发送
     * - 但 5 分钟内可重新发送（旧码会被覆盖），避免因未收到邮件而等待 5 分钟
     */
    @Override
    public Result sendCode(String email) {
        if (StrUtil.isBlank(email)) return Result.fail("邮箱为空");
        email = email.trim();

        Result checkEmailResult = checkEmail(email);
        if (!checkEmailResult.getSuccess()) return checkEmailResult;

        // 发送冷却检查（60秒内不能重复发送）
        String sendLimitKey = REDIS_EMAIL_SEND_LIMIT_KEY + email;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(sendLimitKey))) {
            return Result.fail("发送过于频繁，请 60 秒后重试");
        }

        // 生成验证码
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(900000) + 100000);
        String emailContent = EmailTemplateEnum.VERIFICATION_CODE_EMAIL_HTML.set(code);
        String emailSubject = EmailTemplateEnum.VERIFICATION_CODE_EMAIL_HTML.getSubject();

        try {
            emailApi.sendHtmlEmail(emailSubject, emailContent, email);
            // 验证码写入 Redis，有效期 5 分钟（新码覆盖旧码）
            stringRedisTemplate.opsForValue().set(REDIS_EMAIL_CODE_KEY + email, code, 5, TimeUnit.MINUTES);
            // 写入发送冷却 Key，60 秒内不能重发
            stringRedisTemplate.opsForValue().set(sendLimitKey, "1", 60, TimeUnit.SECONDS);
            return Result.ok("验证码发送成功");
        } catch (Exception e) {
            log.error("发送验证码失败，邮箱：{}", email, e);
            return Result.fail("验证码发送失败，请稍后重试");
        }
    }

    // ==================== 找回密码 ====================

    /**
     * 忘记密码 —— 通过邮箱验证码重置密码
     */
    @Override
    public Result forgetPassword(String email, String emailCode, String newPassword) {
        if (StrUtil.isBlank(email)) return Result.fail("邮箱不能为空");
        if (StrUtil.isBlank(emailCode)) return Result.fail("验证码为空");
        if (StrUtil.isBlank(newPassword)) return Result.fail("密码为空");

        email = email.trim();
        newPassword = newPassword.trim();

        Result checkPwd = checkUserPassword(newPassword);
        if (!checkPwd.getSuccess()) return checkPwd;

        Result checkedEmail = checkEmail(email);
        if (!checkedEmail.getSuccess()) return checkedEmail;

        // 验证码校验
        String checkCode = stringRedisTemplate.opsForValue().get(REDIS_EMAIL_CODE_KEY + email);
        if (StrUtil.isBlank(checkCode)) return Result.fail("验证码已过期");
        if (!checkCode.equals(emailCode)) return Result.fail("验证码错误");

        // 修改频率限制
        String limitKey = REDIS_FORGET_PASSWORD_LIMIT_KEY + email;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(limitKey))) {
            return Result.fail("修改过于频繁，请稍后再试");
        }

        // 更新密码
        String encoded = PASSWORD_ENCODER.encode(newPassword);
        boolean updated = this.update(new LambdaUpdateWrapper<User>()
                .eq(User::getUserEmail, email)
                .eq(User::getIsDelete, 0)
                .set(User::getUserPassword, encoded));
        if (!updated) return Result.fail("邮箱不存在或账号异常");

        // 删除验证码，写入频率限制（1分钟内不能再次修改）
        stringRedisTemplate.delete(REDIS_EMAIL_CODE_KEY + email);
        stringRedisTemplate.opsForValue().set(limitKey, "1", 1, TimeUnit.MINUTES);

        return Result.ok("密码修改成功");
    }

    // ==================== 私有工具方法 ====================

    /**
     * 获取客户端真实 IP
     * 优先读取 X-Forwarded-For（反向代理场景），兜底使用 getRemoteAddr()
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能含多个 IP（逗号分隔），取第一个
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 异步更新用户最后登录时间和 IP
     */
    private void asyncUpdateLoginInfo(Long userId, String ip) {
        Thread.ofVirtual().start(() -> {
            try {
                this.update(new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getLastLoginTime, LocalDateTime.now())
                        .set(User::getLastLoginIp, ip));
            } catch (Exception e) {
                log.warn("异步更新登录信息失败，userId={}", userId, e);
            }
        });
    }

    /**
     * 异步写入登录日志（t_user_login_log）
     *
     * @param userId  用户ID
     * @param ip      登录IP
     * @param result  0-失败，1-成功
     */
    private void asyncWriteLoginLog(Long userId, String ip, int result) {
        Thread.ofVirtual().start(() -> {
            try {
                UserLoginLog logEntry = new UserLoginLog();
                logEntry.setUserId(userId);
                logEntry.setLoginIp(ip);
                logEntry.setLoginType(1);
                logEntry.setLoginResult(result);
                userLoginLogMapper.insert(logEntry);
            } catch (Exception e) {
                log.warn("异步写入登录日志失败，userId={}", userId, e);
            }
        });
    }

    /**
     * 账号格式校验：6-20位，字母/数字/下划线
     */
    private Result checkUserAccount(String userAccount) {
        return userValidationSupport.checkUserAccount(userAccount);
    }

    /**
     * 密码规则校验：至少8位，同时包含大小写字母
     */
    private Result checkUserPassword(String userPassword) {
        return userValidationSupport.checkUserPassword(userPassword);
    }

    /**
     * 用户昵称校验
     */
    public Result checkUsername(String trimUsername) {
        return userValidationSupport.checkUsername(trimUsername);
    }

    /**
     * 邮箱格式校验
     */
    public Result checkEmail(String trimEmail) {
        return userValidationSupport.checkEmail(trimEmail);
    }

    /**
     * 原子释放 Redis 分布式锁（Lua 脚本保证查询+删除的原子性）
     */
    private void releaseRedisLock(String lockKey, String lockValue) {
        String luaScript = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
                """;
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(luaScript);
        redisScript.setResultType(Long.class);
        stringRedisTemplate.execute(redisScript, Collections.singletonList(lockKey), lockValue);
    }

    private String generateSnowflakeAccount() {
        return userAccountGeneratorSupport.generateSnowflakeAccount();
    }
}

    