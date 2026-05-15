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
import com.zero.usercenter.Mapper.UserMapper;
import com.zero.usercenter.Model.AdminUser;
import com.zero.usercenter.Model.User;
import com.zero.usercenter.Service.UserService;
import com.zero.usercenter.mq.AsyncMessageService;
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
 * 用户认证服务实现。
 * 负责登录、注册、邮箱验证码发送、找回密码，以及登录态相关的异步日志与缓存写入。
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private AsyncMessageService asyncMessageService;
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Resource
    private UserValidationSupport userValidationSupport;
    @Resource
    private UserAccountGeneratorSupport userAccountGeneratorSupport;

    /**
     * 重写 save，确保用户密码入库前自动进行 BCrypt 加密。
     */
    @Override
    public boolean save(User entity) {
        encodePasswordIfNecessary(entity);
        return super.save(entity);
    }

    /**
     * 重写 updateById，避免更新密码时遗漏加密。
     */
    @Override
    public boolean updateById(User entity) {
        encodePasswordIfNecessary(entity);
        return super.updateById(entity);
    }

    /**
     * 将明文密码转换为 BCrypt 密文，并跳过已加密密码。
     */
    private void encodePasswordIfNecessary(User user) {
        String rawPassword = user.getUserPassword();
        if (rawPassword == null || rawPassword.isBlank()) return;
        if (rawPassword.startsWith("$2a$") || rawPassword.startsWith("$2b$") || rawPassword.startsWith("$2y$")) return;
        user.setUserPassword(PASSWORD_ENCODER.encode(rawPassword));
    }

    /**
     * 用户登录。
     * 支持账号或邮箱登录，校验通过后会异步记录登录日志并把登录态写入 Redis。
     */
    @Override
    public Result userLogin(LoginDTO loginDTO, HttpServletRequest request) {
        // 1. 先做基础空值校验和输入清洗，避免脏数据进入认证流程。
        String userAccount = loginDTO.getUserAccount();
        String userPassword = loginDTO.getUserPassword();
        String captchaId = loginDTO.getCaptchaID();
        String checkNumber = loginDTO.getCheckNumber();

        if (StrUtil.isBlank(userAccount)) return Result.fail("账户为空");
        if (StrUtil.isBlank(userPassword)) return Result.fail("密码为空");
        if (StrUtil.isBlank(checkNumber)) return Result.fail("验证码为空");
        if (StrUtil.isBlank(captchaId)) return Result.fail("验证码未生成");

        userAccount = userAccount.trim();
        userPassword = userPassword.trim();
        checkNumber = checkNumber.trim();

        // 2. 分别校验账号/邮箱格式、密码规则和验证码有效性。
        boolean isAccountFormat = checkUserAccount(userAccount).getSuccess();
        boolean isEmailFormat = checkEmail(userAccount).getSuccess();
        if (!isAccountFormat && !isEmailFormat) {
            return Result.fail("账户/邮箱格式错误");
        }

        Result passwordCheck = checkUserPassword(userPassword);
        if (Boolean.FALSE.equals(passwordCheck.getSuccess())) return passwordCheck;

        String captchaKey = REDIS_CAPTCHA_KEY + captchaId;
        String captchaCode = stringRedisTemplate.opsForValue().get(captchaKey);
        if (StrUtil.isBlank(captchaCode)) return Result.fail("验证码已过期");
        if (!captchaCode.equalsIgnoreCase(checkNumber)) return Result.fail("验证码错误");
        // 校验通过后立即删除，防止重放攻击
        stringRedisTemplate.delete(captchaKey);

        // 3. 根据账号或邮箱查出用户，并做封禁状态和密码比对。
        final String loginInput = userAccount;
        User user = this.lambdaQuery()
                .eq(User::getIsDelete, 0)
                .and(w -> w.eq(User::getUserAccount, loginInput)
                           .or()
                           .eq(User::getUserEmail, loginInput))
                .one();
        if (user == null) return Result.fail("账户不存在");

        if (Integer.valueOf(2).equals(user.getGlobalPunishType())) {
            return Result.fail("账号已被永久封禁，如有疑问请联系管理员");
        }

        if (!PASSWORD_ENCODER.matches(userPassword, user.getUserPassword())) {
            asyncWriteLoginLog(user.getId(), getClientIp(request), 0);
            return Result.fail("账户或密码错误");
        }

        final Long userId = user.getId();
        String loginLockKey = USER_LOGIN_LOCK_KEY + userId;
        String loginLockValue = cn.hutool.core.lang.UUID.randomUUID().toString();

        try {
            // 4. 对同一账号的登录流程加短锁，防止并发请求同时签发多个有效 token。
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                    loginLockKey,
                    loginLockValue,
                    USER_LOGIN_LOCK_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            if (Boolean.FALSE.equals(locked)) {
                return Result.fail("该账号正在处理登录，请稍后再试");
            }

            // 5. 单账号仅允许一个活跃登录态。
            // 如果旧 token 还有效，直接拒绝本次登录；如果只是历史脏索引，则清理后继续。
            Result activeSessionCheck = ensureSingleActiveSession(userId);
            if (Boolean.FALSE.equals(activeSessionCheck.getSuccess())) {
                return activeSessionCheck;
            }

            // 6. 登录成功后异步更新登录信息和登录日志，避免主流程等待 I/O。
            final String clientIp = getClientIp(request);
            asyncUpdateLoginInfo(userId, clientIp);
            asyncWriteLoginLog(userId, clientIp, 1);

            // 7. 组装轻量登录态快照并写入 Redis，后续鉴权直接从缓存读取。
            // Redis 中仅保存登录态所需的轻量用户快照，避免每次鉴权都回库查用户和管理员身份。
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

            // 8. 维护“用户 -> 当前唯一活跃 token”索引，后续新登录请求据此直接判断是否拒绝。
            stringRedisTemplate.opsForValue().set(USER_ACTIVE_TOKEN_KEY + userId, token, tokenTtl, TimeUnit.MINUTES);
            return Result.ok(token);
        } finally {
            // 9. 无论成功还是失败，都要释放用户级登录锁，避免后续登录被阻塞。
            releaseRedisLock(loginLockKey, loginLockValue);
        }
    }

    /**
     * 用户主动退出登录。
     * 会删除当前 token 登录态，并在索引仍指向该 token 时同步清理唯一活跃 token 映射。
     */
    @Override
    public Result logout(HttpServletRequest request) {
        // 1. 没有 token 说明当前已经处于未登录或本地态已清空，直接按幂等成功返回。
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return Result.ok("已退出登录");
        }

        String redisKey = TOKEN_KEY + token;
        Object userIdValue = stringRedisTemplate.opsForHash().get(redisKey, "id");
        if (userIdValue != null && StrUtil.isNotBlank(userIdValue.toString())) {
            String activeTokenKey = USER_ACTIVE_TOKEN_KEY + userIdValue;
            String activeToken = stringRedisTemplate.opsForValue().get(activeTokenKey);
            // 2. 只有当索引仍指向当前 token 时才删除索引，避免误删其它新登录设备生成的 token 索引。
            if (token.equals(activeToken)) {
                stringRedisTemplate.delete(activeTokenKey);
            }
        }

        // 3. 最后删除 token 本体，让当前请求携带的登录态立即失效。
        stringRedisTemplate.delete(redisKey);
        return Result.ok("已退出登录");
    }

    /**
     * 校验当前账号是否已存在其它设备的有效登录态。
     * 若索引残留但旧 token 已失效，则自动清理脏索引，避免用户被异常退出历史状态锁死。
     */
    private Result ensureSingleActiveSession(Long userId) {
        // 1. 先读取“用户 -> 当前活跃 token”索引，没有索引时说明当前账号不存在有效登录。
        String activeTokenKey = USER_ACTIVE_TOKEN_KEY + userId;
        String activeToken = stringRedisTemplate.opsForValue().get(activeTokenKey);
        if (StrUtil.isBlank(activeToken)) {
            return Result.ok();
        }

        // 2. 索引存在时继续判断 token 对应 Hash 是否仍然存在，避免把过期脏索引当成真实在线态。
        String activeRedisKey = TOKEN_KEY + activeToken;
        Boolean activeTokenAlive = stringRedisTemplate.hasKey(activeRedisKey);
        if (Boolean.TRUE.equals(activeTokenAlive)) {
            return Result.fail("该账号已在其他设备登录，请先退出原设备后再试");
        }

        // 3. 旧 token 实际已失效时，删除脏索引并放行本次登录。
        stringRedisTemplate.delete(activeTokenKey);
        return Result.ok();
    }

    /**
     * 查询用户当前是否拥有启用中的管理员身份。
     *
     * @param userId 用户 ID
     * @return 启用中的管理员记录；不存在时返回 null
     */
    private AdminUser findActiveAdmin(Long userId) {
        // 1. 只认启用且未删除的管理员记录，避免把历史失效身份带进登录态。
        LambdaQueryWrapper<AdminUser> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminUser::getUserId, userId)
          .eq(AdminUser::getAdminStatus, 1)
          .eq(AdminUser::getIsDelete, 0)
          .last("limit 1");
        return adminUserMapper.selectOne(qw);
    }

    /**
     * 用户注册。
     * 通过邮箱验证码完成注册，并借助 Redis 分布式锁避免同邮箱并发重复注册。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result userRegister(String username, String email, String emailCode, String password) {
        // 1. 先做空值校验和输入清洗，统一后续校验入口。
        if (StrUtil.isBlank(username)) return Result.fail("用户名为空");
        if (StrUtil.isBlank(email)) return Result.fail("邮箱为空");
        if (StrUtil.isBlank(emailCode)) return Result.fail("验证码为空");
        if (StrUtil.isBlank(password)) return Result.fail("密码为空");

        username = username.trim();
        email = email.trim();
        emailCode = emailCode.trim();
        password = password.trim();

        // 2. 复用统一校验组件校验用户名、密码和邮箱格式。
        Result resultUsername = checkUsername(username);
        if (Boolean.FALSE.equals(resultUsername.getSuccess())) return resultUsername;

        Result checkPwd = checkUserPassword(password);
        if (Boolean.FALSE.equals(checkPwd.getSuccess())) return checkPwd;

        Result checkEmailResult = checkEmail(email);
        if (Boolean.FALSE.equals(checkEmailResult.getSuccess())) return checkEmailResult;

        String checkCode = stringRedisTemplate.opsForValue().get(REDIS_EMAIL_CODE_KEY + email);
        if (StrUtil.isBlank(checkCode)) return Result.fail("验证码已过期");
        if (!checkCode.equals(emailCode)) return Result.fail("验证码错误");

        // 3. 以邮箱为幂等主键加分布式锁，防止并发重复注册。
        // 注册场景以邮箱为幂等主键，加锁后再做唯一性检查，避免并发请求穿透出重复账号。
        String lockKey = "lock:user:email:" + email;
        String lockValue = cn.hutool.core.lang.UUID.randomUUID().toString();
        try {
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(locked)) return Result.fail("操作太频繁，请稍后重试");

            if (this.exists(new LambdaQueryWrapper<User>().eq(User::getUserEmail, email))) {
                return Result.fail("当前邮箱已注册");
            }

            // 4. 生成账号并创建用户，密码加密由 save 重写逻辑自动完成。
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

            // 5. 注册成功后清掉邮箱验证码，并返回轻量用户信息。
            stringRedisTemplate.delete(REDIS_EMAIL_CODE_KEY + email);

            UserFormat userFormat = new UserFormat();
            BeanUtils.copyProperties(user, userFormat);
            return Result.ok(userFormat);

        } catch (Exception e) {
            log.error("用户注册失败，邮箱：{}", email, e);
            return Result.fail("系统异常，请联系管理员");
        } finally {
            // 无论成功还是失败，都要尝试释放邮箱注册锁。
            releaseRedisLock(lockKey, lockValue);
        }
    }

    /**
     * 发送邮箱验证码。
     * 发送冷却和验证码有效期拆开控制，避免用户因收信延迟被迫等待整个验证码 TTL。
     */
    @Override
    public Result sendCode(String email) {
        // 1. 先校验邮箱格式，再检查发送冷却，避免频繁刷邮件服务。
        if (StrUtil.isBlank(email)) return Result.fail("邮箱为空");
        email = email.trim();

        Result checkEmailResult = checkEmail(email);
        if (!checkEmailResult.getSuccess()) return checkEmailResult;

        String sendLimitKey = REDIS_EMAIL_SEND_LIMIT_KEY + email;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(sendLimitKey))) {
            return Result.fail("发送过于频繁，请 60 秒后重试");
        }

        String code = String.valueOf(ThreadLocalRandom.current().nextInt(900000) + 100000);
        String emailContent = EmailTemplateEnum.VERIFICATION_CODE_EMAIL_HTML.set(code);
        String emailSubject = EmailTemplateEnum.VERIFICATION_CODE_EMAIL_HTML.getSubject();

        try {
            // 2. 发送成功后分别写入验证码 TTL 和发送冷却 TTL。
            asyncMessageService.sendHtmlEmail(email, emailSubject, emailContent);
            stringRedisTemplate.opsForValue().set(REDIS_EMAIL_CODE_KEY + email, code, 5, TimeUnit.MINUTES);
            stringRedisTemplate.opsForValue().set(sendLimitKey, "1", 60, TimeUnit.SECONDS);
            return Result.ok("验证码发送成功");
        } catch (Exception e) {
            log.error("发送验证码失败，邮箱：{}", email, e);
            return Result.fail("验证码发送失败，请稍后重试");
        }
    }

    /**
     * 忘记密码。
     * 通过邮箱验证码校验后重置密码，并限制短时间内重复修改。
     */
    @Override
    public Result forgetPassword(String email, String emailCode, String newPassword) {
        // 1. 先校验邮箱、新密码和验证码参数。
        if (StrUtil.isBlank(email)) return Result.fail("邮箱不能为空");
        if (StrUtil.isBlank(emailCode)) return Result.fail("验证码为空");
        if (StrUtil.isBlank(newPassword)) return Result.fail("密码为空");

        email = email.trim();
        newPassword = newPassword.trim();

        Result checkPwd = checkUserPassword(newPassword);
        if (!checkPwd.getSuccess()) return checkPwd;

        Result checkedEmail = checkEmail(email);
        if (!checkedEmail.getSuccess()) return checkedEmail;

        // 2. 校验邮箱验证码，并限制短时间内重复改密。
        String checkCode = stringRedisTemplate.opsForValue().get(REDIS_EMAIL_CODE_KEY + email);
        if (StrUtil.isBlank(checkCode)) return Result.fail("验证码已过期");
        if (!checkCode.equals(emailCode)) return Result.fail("验证码错误");

        String limitKey = REDIS_FORGET_PASSWORD_LIMIT_KEY + email;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(limitKey))) {
            return Result.fail("修改过于频繁，请稍后再试");
        }

        // 3. 更新数据库中的密码密文，并清理验证码和限流状态。
        String encoded = PASSWORD_ENCODER.encode(newPassword);
        boolean updated = this.update(new LambdaUpdateWrapper<User>()
                .eq(User::getUserEmail, email)
                .eq(User::getIsDelete, 0)
                .set(User::getUserPassword, encoded));
        if (!updated) return Result.fail("邮箱不存在或账号异常");

        stringRedisTemplate.delete(REDIS_EMAIL_CODE_KEY + email);
        stringRedisTemplate.opsForValue().set(limitKey, "1", 1, TimeUnit.MINUTES);

        return Result.ok("密码修改成功");
    }

    /**
     * 获取客户端真实 IP。
     * 优先读取反向代理头，兜底使用 servlet 原始远端地址。
     */
    private String getClientIp(HttpServletRequest request) {
        // 1. 优先读取 X-Forwarded-For，兼容经由网关或反向代理转发的场景。
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能含多个 IP（逗号分隔），取第一个
            return ip.split(",")[0].trim();
        }
        // 2. 次优先读取 X-Real-IP，适配常见 Nginx 代理配置。
        ip = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        // 3. 两种代理头都没有时，再退回 servlet 原始来源地址。
        return request.getRemoteAddr();
    }

    /**
     * 异步更新用户最后登录时间和 IP。
     */
    private void asyncUpdateLoginInfo(Long userId, String ip) {
        Thread.ofVirtual().start(() -> {
            try {
                // 1. 登录信息更新放到异步线程，失败也不影响本次登录成功。
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
     * 异步写入登录日志。
     */
    private void asyncWriteLoginLog(Long userId, String ip, int result) {
        Thread.ofVirtual().start(() -> {
            try {
                // 1. 登录日志属于审计信息，异步发送即可，不应阻塞认证主链路。
                asyncMessageService.sendLoginLog(userId, ip, 1, result);
            } catch (Exception e) {
                log.warn("异步写入登录日志失败，userId={}", userId, e);
            }
        });
    }

    /**
     * 账号格式校验。
     *
     * @param userAccount 账号字符串
     * @return 校验结果
     */
    private Result checkUserAccount(String userAccount) {
        return userValidationSupport.checkUserAccount(userAccount);
    }

    /**
     * 密码规则校验。
     *
     * @param userPassword 密码字符串
     * @return 校验结果
     */
    private Result checkUserPassword(String userPassword) {
        return userValidationSupport.checkUserPassword(userPassword);
    }

    /**
     * 用户昵称校验。
     *
     * @param trimUsername 去除首尾空格后的用户名
     * @return 校验结果
     */
    public Result checkUsername(String trimUsername) {
        return userValidationSupport.checkUsername(trimUsername);
    }

    /**
     * 邮箱格式校验。
     *
     * @param trimEmail 去除首尾空格后的邮箱
     * @return 校验结果
     */
    public Result checkEmail(String trimEmail) {
        return userValidationSupport.checkEmail(trimEmail);
    }

    /**
     * 原子释放 Redis 分布式锁。
     * 通过 Lua 保证“校验锁值 + 删除锁”是一个原子操作。
     */
    private void releaseRedisLock(String lockKey, String lockValue) {
        // 1. 先定义 Lua 脚本，只在锁值匹配时才执行删除，避免误删他人锁。
        String luaScript = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
                """;

        // 2. 通过 Lua 原子完成“比对锁值 + 删除锁”，防止误删他人锁。
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(luaScript);
        redisScript.setResultType(Long.class);
        stringRedisTemplate.execute(redisScript, Collections.singletonList(lockKey), lockValue);
    }

    /**
     * 生成一个新的雪花算法账号。
     *
     * @return 新账号字符串
     */
    private String generateSnowflakeAccount() {
        // 1. 账号生成统一委托给专门支撑类，避免认证主服务承载过多实现细节。
        return userAccountGeneratorSupport.generateSnowflakeAccount();
    }
}

    
