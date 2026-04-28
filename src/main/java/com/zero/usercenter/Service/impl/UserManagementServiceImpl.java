package com.zero.usercenter.Service.impl;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Mapper.SystemNoticeMapper;
import com.zero.usercenter.Mapper.UserBlacklistMapper;
import com.zero.usercenter.Mapper.UserFriendMapper;
import com.zero.usercenter.Mapper.UserMapper;
import com.zero.usercenter.Model.*;
import com.zero.usercenter.Service.UserManagementService;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import com.zero.usercenter.websocket.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static com.zero.usercenter.utils.Number.*;

/**
 * 用户管理模块 Service 实现类
 * 负责处理用户信息、隐私设置、好友关系、黑名单等业务逻辑
 */
@Service
public class UserManagementServiceImpl extends ServiceImpl<UserMapper, User> implements UserManagementService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private UserFriendMapper userFriendMapper;
    @Resource
    private UserBlacklistMapper userBlacklistMapper;
    @Resource
    private SystemNoticeMapper systemNoticeMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private com.zero.usercenter.Mapper.TeamMemberMapper teamMemberMapper;
    @Resource
    private SensitiveWordBs sensitiveWordBs;
    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;
    @Resource
    private SearchRecommendServiceImpl searchRecommendService;

    /**
     * 更新用户资料
     * 支持编辑昵称、头像、简介、标签
     *
     * @param dto 用户资料更新数据传输对象
     * @return 更新后的用户信息
     */
    @Override
    @Transactional
    public Result updateUserProfile(UserProfileUpdateDTO dto) {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 校验并处理昵称：3-16位
        if (dto.getUserNickname() != null) {
            String nickname = dto.getUserNickname().trim();
            if (nickname.length() < 3 || nickname.length() > 16) return Result.fail("昵称长度必须在3-16位之间");
            if (sensitiveWordBs.contains(nickname)) return Result.fail("昵称包含敏感词");
            dto.setUserNickname(nickname);
        }

        // 校验并处理个人简介：≤512个字符
        if (dto.getUserIntro() != null) {
            String intro = dto.getUserIntro().trim();
            if (intro.length() > 512) return Result.fail("个人简介不能超过512字符");
            if (sensitiveWordBs.contains(intro)) return Result.fail("个人简介包含敏感词");
            dto.setUserIntro(intro);
        }

        // 校验并处理标签：≤5个，每个≤20个字符
        if (dto.getUserTags() != null) {
            String tags = dto.getUserTags().trim();
            String[] tagArray = tags.split(",");
            if (tagArray.length > 5) return Result.fail("标签数量不能超过5个");
            for (String tag : tagArray) if (tag.length() > 20) return Result.fail("单个标签长度不能超过20字符");
            dto.setUserTags(tags);
        }

        // 构建更新对象，只更新非空字段
        User user = new User();
        user.setId(userId);
        if (dto.getUserNickname() != null) user.setUserNickname(dto.getUserNickname());
        if (dto.getUserAvatar() != null) user.setUserAvatar(dto.getUserAvatar());
        if (dto.getUserIntro() != null) user.setUserIntro(dto.getUserIntro());
        if (dto.getUserTags() != null) user.setUserTags(dto.getUserTags());

        // 更新数据库
        userMapper.updateById(user);

        // 同步更新 Redis Token 缓存，确保 /api/auth/me 返回最新数据
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String token = request.getHeader("authorization");
            if (token != null && !token.isBlank()) {
                String redisKey = TOKEN_KEY + token;
                if (dto.getUserNickname() != null)
                    stringRedisTemplate.opsForHash().put(redisKey, "userNickname", dto.getUserNickname());
                if (dto.getUserAvatar() != null)
                    stringRedisTemplate.opsForHash().put(redisKey, "userAvatar", dto.getUserAvatar());
                if (dto.getUserIntro() != null)
                    stringRedisTemplate.opsForHash().put(redisKey, "userIntro", dto.getUserIntro());
                if (dto.getUserTags() != null)
                    stringRedisTemplate.opsForHash().put(redisKey, "userTags", dto.getUserTags());
            }
        } catch (Exception ignored) {}

        // 清除 user_info 缓存，确保他人下次查看时读到最新资料
        stringRedisTemplate.delete(USER_INFO_CACHE_KEY + userId);

        // 查询更新后的用户信息并返回
        User updatedUser = userMapper.selectById(userId);
        return Result.ok(buildUserResponse(updatedUser));
    }

    /**
     * 获取用户隐私设置
     * 返回用户的隐私设置，包括资料可见性、消息接收权限、邮箱搜索权限
     *
     * @return 隐私设置信息
     */
    @Override
    public Result getPrivacySetting() {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 查询用户信息
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        // 获取隐私设置JSON字符串
        String privacySettingStr = user.getPrivacySetting();

        // 如果隐私设置为空，返回默认值
        if (privacySettingStr == null || privacySettingStr.isEmpty()) {
            PrivacySettingDTO defaultSetting = new PrivacySettingDTO();
            defaultSetting.setViewInfo(1);        // 默认：所有人可见
            defaultSetting.setSendMsg(1);         // 默认：所有人可发
            defaultSetting.setSearchByEmail(0);   // 默认：不允许邮箱搜索
            return Result.ok(defaultSetting);
        }

        // 解析JSON字符串为隐私设置对象
        return Result.ok(JSON.parseObject(privacySettingStr, PrivacySettingDTO.class));
    }

    /**
     * 更新用户隐私设置
     * 支持更新资料可见性、消息接收权限、邮箱搜索权限
     *
     * @param dto 隐私设置数据传输对象
     * @return 更新后的隐私设置
     */
    @Override
    @Transactional
    public Result updatePrivacySetting(PrivacySettingDTO dto) {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 参数校验：资料可见性（1-所有人，2-仅团队成员）
        if (dto.getViewInfo() != null && (dto.getViewInfo() < 1 || dto.getViewInfo() > 2))
            return Result.fail("资料可见性参数无效");

        // 参数校验：消息接收权限（1-所有人，2-仅团队成员，3-需验证）
        if (dto.getSendMsg() != null && (dto.getSendMsg() < 1 || dto.getSendMsg() > 3))
            return Result.fail("消息接收权限参数无效");

        // 参数校验：邮箱搜索权限（0-不允许，1-允许）
        if (dto.getSearchByEmail() != null && (dto.getSearchByEmail() < 0 || dto.getSearchByEmail() > 1))
            return Result.fail("邮箱搜索权限参数无效");

        // 查询用户当前隐私设置
        User user = userMapper.selectById(userId);
        String privacySettingStr = user.getPrivacySetting();

        // 解析当前隐私设置，如果为空则创建新对象
        PrivacySettingDTO currentSetting = privacySettingStr != null && !privacySettingStr.isEmpty()
                ? JSON.parseObject(privacySettingStr, PrivacySettingDTO.class)
                : new PrivacySettingDTO();

        // 合并新设置：只更新非空字段，保留其他字段的原值
        if (dto.getViewInfo() != null) currentSetting.setViewInfo(dto.getViewInfo());
        if (dto.getSendMsg() != null) currentSetting.setSendMsg(dto.getSendMsg());
        if (dto.getSearchByEmail() != null) currentSetting.setSearchByEmail(dto.getSearchByEmail());

        // 构建更新对象
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setPrivacySetting(JSON.toJSONString(currentSetting));

        // 更新数据库
        userMapper.updateById(updateUser);

        // 返回更新后的隐私设置
        return Result.ok(currentSetting);
    }

    /**
     * 添加好友
     * 发起好友申请，检查是否已是好友、是否被拉黑、隐私设置等
     *
     * @param dto 好友操作数据传输对象
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result addFriend(FriendOperationDTO dto) {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 参数校验
        Long friendId = dto.getFriendId();
        if (friendId == null || friendId <= 0) return Result.fail("好友ID无效");
        if (userId.equals(friendId)) return Result.fail("不能添加自己为好友");

        // 检查好友是否存在
        User friendUser = userMapper.selectById(friendId);
        if (friendUser == null || friendUser.getIsDelete() == 1) return Result.fail("用户不存在");

        // 检查是否已是好友（friend_status=1表示已成为好友）
        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getFriendStatus, 1);
        if (userFriendMapper.selectOne(queryWrapper) != null) return Result.fail("已经是好友了");

        // 检查是否被对方拉黑（is_delete=0表示未解除拉黑）
        LambdaQueryWrapper<UserBlacklist> blacklistWrapper = new LambdaQueryWrapper<>();
        blacklistWrapper.eq(UserBlacklist::getUserId, friendId)
                .eq(UserBlacklist::getBlackUserId, userId)
                .eq(UserBlacklist::getIsDelete, 0);
        if (userBlacklistMapper.selectOne(blacklistWrapper) != null)
            return Result.fail("对方已拉黑你，无法添加好友");

        // 检查对方隐私设置
        String privacySettingStr = friendUser.getPrivacySetting();
        PrivacySettingDTO privacySetting = privacySettingStr != null && !privacySettingStr.isEmpty()
                ? JSON.parseObject(privacySettingStr, PrivacySettingDTO.class)
                : new PrivacySettingDTO();

        // 如果对方设置为需验证（sendMsg=3），检查是否已申请过
        if (privacySetting.getSendMsg() != null && privacySetting.getSendMsg() == 3) {
            LambdaQueryWrapper<UserFriend> pendingWrapper = new LambdaQueryWrapper<>();
            pendingWrapper.eq(UserFriend::getUserId, userId)
                    .eq(UserFriend::getFriendId, friendId)
                    .eq(UserFriend::getFriendStatus, 0);
            if (userFriendMapper.selectOne(pendingWrapper) != null)
                return Result.fail("已经申请过了，请等待对方同意");
        }

        // 插入好友申请记录（friend_status=0表示待验证）
        UserFriend userFriend = new UserFriend();
        userFriend.setUserId(userId);
        userFriend.setFriendId(friendId);
        userFriend.setFriendStatus(0);
        userFriend.setFriendRemark(dto.getFriendRemark());
        userFriendMapper.insert(userFriend);

        // 异步发送系统通知给对方（noticeType=1：好友申请），不阻塞主流程
        String senderNickname = userMapper.selectById(userId).getUserNickname();
        Thread.ofVirtual().start(() -> {
            SystemNotice notice = new SystemNotice();
            notice.setUserId(friendId);
            notice.setNoticeType(1);
            notice.setNoticeContent("用户【" + senderNickname + "】向你发送了好友申请");
            notice.setRelatedId(userId);
            notice.setIsRead(0);
            notice.setIsDelete(0);
            sendRealtimeSystemNotice(notice);
        });

        return Result.ok("好友申请已发送");
    }

    /**
     * 获取好友列表
     * 分页返回当前用户的所有好友（friend_status=1）
     * 好友 ID 列表用 Redis Set 缓存，TTL 24小时
     *
     * @param page 页码
     * @param pageSize 每页数量
     * @return 好友列表
     */
    @Override
    public Result getFriendList(int page, int pageSize) {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        String cacheKey = FRIEND_LIST_CACHE_KEY + userId;

        // 1. 先从 Redis Set 中获取好友 ID 列表
        // 用 hasKey 区分「缓存不存在」和「好友列表为空」，防止缓存穿透
        Boolean cacheExists = stringRedisTemplate.hasKey(cacheKey);
        List<Long> friendIds;
        if (Boolean.TRUE.equals(cacheExists)) {
            // 缓存命中（含空列表占位符场景）
            Set<String> cachedIds = stringRedisTemplate.opsForSet().members(cacheKey);
            friendIds = cachedIds == null ? Collections.emptyList() :
                    cachedIds.stream()
                            .filter(s -> !"0".equals(s))   // 过滤防穿透占位符
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
        } else {
            // 缓存未命中，查数据库（加互斥锁防击穿）
            String lockKey = "lock:friend_list:" + userId;
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
            try {
                if (!Boolean.TRUE.equals(locked)) {
                    // 未拿到锁，短暂等待后重读缓存
                    Thread.sleep(50);
                    Set<String> retryIds = stringRedisTemplate.opsForSet().members(cacheKey);
                    friendIds = retryIds == null ? Collections.emptyList() :
                            retryIds.stream().filter(s -> !"0".equals(s)).map(Long::parseLong).collect(Collectors.toList());
                } else {
                    LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
                    queryWrapper.eq(UserFriend::getUserId, userId)
                            .eq(UserFriend::getFriendStatus, 1);
                    List<UserFriend> friendRecords = userFriendMapper.selectList(queryWrapper);
                    friendIds = friendRecords.stream()
                            .map(UserFriend::getFriendId)
                            .collect(Collectors.toList());
                    // TTL 加随机抖动（±2小时），防雪崩
                    long ttl = FRIEND_LIST_CACHE_TTL_HOURS + ThreadLocalRandom.current().nextLong(-2, 3);
                    if (!friendIds.isEmpty()) {
                        String[] idArr = friendIds.stream().map(String::valueOf).toArray(String[]::new);
                        stringRedisTemplate.opsForSet().add(cacheKey, idArr);
                    } else {
                        // 空列表写占位符「0」防缓存穿透，TTL 缩短为 5 分钟
                        stringRedisTemplate.opsForSet().add(cacheKey, "0");
                        ttl = 0;
                    }
                    stringRedisTemplate.expire(cacheKey, ttl == 0 ? 5 : ttl, ttl == 0 ? TimeUnit.MINUTES : TimeUnit.HOURS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                friendIds = Collections.emptyList();
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        }

        // 2. 手动分页
        int total = friendIds.size();
        int fromIndex = Math.min((page - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Long> pagedIds = friendIds.subList(fromIndex, toIndex);

        // 3. 批量查询好友详情（避免 N+1 查询问题）
        List<User> friendUsers = pagedIds.isEmpty() ? Collections.emptyList() : userMapper.selectByIds(pagedIds);
        Map<Long, User> friendUserMap = friendUsers.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 批量查好友关系记录（备注 + 同意时间）
        Map<Long, UserFriend> ufMap = Collections.emptyMap();
        if (!pagedIds.isEmpty()) {
            LambdaQueryWrapper<UserFriend> batchUfq = new LambdaQueryWrapper<>();
            batchUfq.eq(UserFriend::getUserId, userId)
                    .in(UserFriend::getFriendId, pagedIds)
                    .eq(UserFriend::getFriendStatus, 1);
            ufMap = userFriendMapper.selectList(batchUfq).stream()
                    .collect(Collectors.toMap(UserFriend::getFriendId, uf -> uf));
        }

        final Map<Long, UserFriend> finalUfMap = ufMap;
        List<Map<String, Object>> friendList = pagedIds.stream().map(fid -> {
            User friend = friendUserMap.get(fid);
            UserFriend uf = finalUfMap.get(fid);
            Map<String, Object> friendInfo = new HashMap<>();
            friendInfo.put("friendId", fid);
            friendInfo.put("friendRemark", uf != null ? uf.getFriendRemark() : null);
            friendInfo.put("userNickname", friend != null ? friend.getUserNickname() : "");
            friendInfo.put("userAvatar", friend != null ? friend.getUserAvatar() : "");
            friendInfo.put("userIntro", friend != null ? friend.getUserIntro() : "");
            friendInfo.put("agreeTime", uf != null ? uf.getAgreeTime() : null);
            return friendInfo;
        }).collect(Collectors.toList());

        return Result.ok(friendList, (long) total);
    }

    /**
     * 同意好友申请
     * 将好友关系状态从待验证(0)改为已成为好友(1)，并插入反向记录实现双向关系
     * 同时清除双方的好友列表缓存
     *
     * @param friendId 申请人ID
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result agreeFriend(Long friendId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (friendId == null || friendId <= 0) return Result.fail("好友ID无效");

        // 查询申请记录
        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFriend::getUserId, friendId)
                .eq(UserFriend::getFriendId, userId)
                .eq(UserFriend::getFriendStatus, 0);
        UserFriend friendRequest = userFriendMapper.selectOne(queryWrapper);
        if (friendRequest == null) return Result.fail("好友申请不存在");

        // 更新申请记录状态
        friendRequest.setFriendStatus(1);
        friendRequest.setAgreeTime(LocalDateTime.now());
        userFriendMapper.updateById(friendRequest);

        // 插入反向记录
        UserFriend reverseFriend = new UserFriend();
        reverseFriend.setUserId(userId);
        reverseFriend.setFriendId(friendId);
        reverseFriend.setFriendStatus(1);
        reverseFriend.setAgreeTime(LocalDateTime.now());
        userFriendMapper.insert(reverseFriend);

        // 清除双方的好友列表缓存
        stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + userId);
        stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + friendId);

        // 即时刷新双方推荐（异步）
        Thread.ofVirtual().start(() -> {
            searchRecommendService.refreshRecommendForUser(userId);
            searchRecommendService.refreshRecommendForUser(friendId);
        });

        // 异步发送系统通知给申请者（noticeType=3：好友申请通过）
        String currentNickname = userMapper.selectById(userId).getUserNickname();
        Thread.ofVirtual().start(() -> {
            SystemNotice notice = new SystemNotice();
            notice.setUserId(friendId);
            notice.setNoticeType(3);
            notice.setNoticeContent("用户【" + currentNickname + "】已同意你的好友申请");
            notice.setRelatedId(userId);
            notice.setIsRead(0);
            notice.setIsDelete(0);
            sendRealtimeSystemNotice(notice);
        });

        return Result.ok("已同意好友申请");
    }

    /**
     * 拒绝好友申请
     * 将好友关系状态从待验证(0)改为已拒绝(2)
     *
     * @param friendId 申请人ID
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result rejectFriend(Long friendId) {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (friendId == null || friendId <= 0) return Result.fail("好友ID无效");

        // 查询申请记录：查找friendId发起、userId接收、状态为待验证(0)的记录
        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFriend::getUserId, friendId)
                .eq(UserFriend::getFriendId, userId)
                .eq(UserFriend::getFriendStatus, 0);
        UserFriend friendRequest = userFriendMapper.selectOne(queryWrapper);
        if (friendRequest == null) return Result.fail("好友申请不存在");

        // 更新申请记录：状态改为已拒绝(2)
        friendRequest.setFriendStatus(2);
        userFriendMapper.updateById(friendRequest);

        // 异步发送系统通知给申请者（noticeType=2：好友申请拒绝），不阻塞主流程
        String rejecterNickname = userMapper.selectById(userId).getUserNickname();
        Thread.ofVirtual().start(() -> {
            SystemNotice rejectNotice = new SystemNotice();
            rejectNotice.setUserId(friendId);
            rejectNotice.setNoticeType(2);
            rejectNotice.setNoticeContent("用户【" + rejecterNickname + "】拒绝了你的好友申请");
            rejectNotice.setRelatedId(userId);
            rejectNotice.setIsRead(0);
            rejectNotice.setIsDelete(0);
            sendRealtimeSystemNotice(rejectNotice);
        });

        return Result.ok("已拒绝好友申请");
    }

    /**
     * 删除好友
     * 删除双向好友关系记录，并清除双方好友列表缓存
     *
     * @param friendId 好友ID
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result deleteFriend(Long friendId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (friendId == null || friendId <= 0) return Result.fail("好友ID无效");

        // 删除当前用户到好友的关系记录
        LambdaQueryWrapper<UserFriend> queryWrapper1 = new LambdaQueryWrapper<>();
        queryWrapper1.eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getFriendStatus, 1);
        userFriendMapper.delete(queryWrapper1);

        // 删除好友到当前用户的反向关系记录
        LambdaQueryWrapper<UserFriend> queryWrapper2 = new LambdaQueryWrapper<>();
        queryWrapper2.eq(UserFriend::getUserId, friendId)
                .eq(UserFriend::getFriendId, userId)
                .eq(UserFriend::getFriendStatus, 1);
        userFriendMapper.delete(queryWrapper2);

        // 清除双方的好友列表缓存
        stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + userId);
        stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + friendId);

        return Result.ok("已删除好友");
    }

    /**
     * 拉黑用户
     * 将用户加入黑名单，并删除已有的好友关系
     * 同时清除双方好友列表缓存和黑名单缓存
     *
     * @param dto 黑名单操作数据传输对象
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result addBlacklist(BlacklistOperationDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        Long blackUserId = dto.getBlackUserId();
        if (blackUserId == null || blackUserId <= 0) return Result.fail("用户ID无效");
        if (userId.equals(blackUserId)) return Result.fail("不能拉黑自己");

        User blackUser = userMapper.selectById(blackUserId);
        if (blackUser == null || blackUser.getIsDelete() == 1) return Result.fail("用户不存在");

        // 检查是否已拉黑（is_delete=0表示未解除拉黑）
        LambdaQueryWrapper<UserBlacklist> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlackUserId, blackUserId)
                .eq(UserBlacklist::getIsDelete, 0);
        if (userBlacklistMapper.selectOne(queryWrapper) != null) return Result.fail("已经拉黑该用户");

        // 插入黑名单记录
        UserBlacklist blacklist = new UserBlacklist();
        blacklist.setUserId(userId);
        blacklist.setBlackUserId(blackUserId);
        blacklist.setBlackReason(dto.getBlackReason());
        blacklist.setIsDelete(0);
        userBlacklistMapper.insert(blacklist);

        // 删除与被拉黑用户的好友关系（如果存在）
        LambdaQueryWrapper<UserFriend> friendWrapper1 = new LambdaQueryWrapper<>();
        friendWrapper1.eq(UserFriend::getUserId, userId).eq(UserFriend::getFriendId, blackUserId);
        userFriendMapper.delete(friendWrapper1);

        LambdaQueryWrapper<UserFriend> friendWrapper2 = new LambdaQueryWrapper<>();
        friendWrapper2.eq(UserFriend::getUserId, blackUserId).eq(UserFriend::getFriendId, userId);
        userFriendMapper.delete(friendWrapper2);

        // 清除好友列表缓存（双方）和黑名单缓存（当前用户）
        stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + userId);
        stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + blackUserId);
        stringRedisTemplate.delete(BLACKLIST_CACHE_KEY + userId);

        return Result.ok("已拉黑用户");
    }

    /**
     * 解除拉黑
     * 将黑名单记录标记为已解除（软删除），并清除黑名单缓存
     *
     * @param blackUserId 被拉黑用户ID
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result removeBlacklist(Long blackUserId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (blackUserId == null || blackUserId <= 0) return Result.fail("用户ID无效");

        LambdaQueryWrapper<UserBlacklist> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlackUserId, blackUserId)
                .eq(UserBlacklist::getIsDelete, 0);
        UserBlacklist blacklist = userBlacklistMapper.selectOne(queryWrapper);
        if (blacklist == null) return Result.fail("黑名单记录不存在");

        blacklist.setIsDelete(1);
        userBlacklistMapper.updateById(blacklist);

        // 清除黑名单缓存
        stringRedisTemplate.delete(BLACKLIST_CACHE_KEY + userId);

        return Result.ok("已解除拉黑");
    }

    /**
     * 获取黑名单列表
     * 分页返回当前用户的所有黑名单记录（is_delete=0）
     *
     * @param page 页码
     * @param pageSize 每页数量
     * @return 黑名单列表
     */
    @Override
    public Result getBlacklist(int page, int pageSize) {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 构建分页对象
        Page<UserBlacklist> pageObj = new Page<>(page, pageSize);

        // 构建查询条件：查询当前用户的所有未解除的黑名单记录（is_delete=0）
        LambdaQueryWrapper<UserBlacklist> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getIsDelete, 0)
                .orderByDesc(UserBlacklist::getCreateTime);

        // 执行分页查询
        Page<UserBlacklist> result = userBlacklistMapper.selectPage(pageObj, queryWrapper);

        // 构建返回数据：获取每个被拉黑用户的详细信息
        List<Map<String, Object>> blacklistData = result.getRecords().stream().map(bl -> {
            // 查询被拉黑用户的信息
            User blackUser = userMapper.selectById(bl.getBlackUserId());
            Map<String, Object> blackInfo = new HashMap<>();
            blackInfo.put("id", bl.getId());
            blackInfo.put("blackUserId", bl.getBlackUserId());
            blackInfo.put("userNickname", blackUser.getUserNickname());
            blackInfo.put("userAvatar", blackUser.getUserAvatar());
            blackInfo.put("blackReason", bl.getBlackReason());
            blackInfo.put("createTime", bl.getCreateTime());
            return blackInfo;
        }).collect(Collectors.toList());

        return Result.ok(blacklistData, result.getTotal());
    }

    /**
     * 搜索用户
     * 支持按账号、昵称、标签、邮箱搜索，自动过滤被拉黑的用户
     *
     * @param keyword 搜索关键词
     * @param type 搜索类型（account-账号，nickname-昵称，tag-标签，email-邮箱）
     * @param page 页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    @Override
    public Result searchUser(String keyword, String type, int page, int pageSize) {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 参数校验：关键词不能为空
        if (keyword == null || keyword.trim().isEmpty()) return Result.fail("搜索关键词不能为空");
        keyword = keyword.trim();

        // 构建分页对象
        Page<User> pageObj = new Page<>(page, pageSize);

        // 构建查询条件：只查询未删除的用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getIsDelete, 0);

        // 根据搜索类型构建不同的查询条件
        if ("account".equals(type)) {
            // 按账号模糊搜索
            queryWrapper.like(User::getUserAccount, keyword);
        } else if ("nickname".equals(type)) {
            // 按昵称模糊搜索
            queryWrapper.like(User::getUserNickname, keyword);
        } else if ("tag".equals(type)) {
            // 按标签模糊搜索
            queryWrapper.like(User::getUserTags, keyword);
        } else if ("email".equals(type)) {
            // 按邮箱搜索（由隐私开关 searchByEmail 决定是否可见）
            queryWrapper.like(User::getUserEmail, keyword);
        } else {
            return Result.fail("搜索类型无效");
        }

        // 执行分页查询
        Page<User> result = userMapper.selectPage(pageObj, queryWrapper);

        // 过滤并构建返回数据
        final String finalType = type;
        List<Map<String, Object>> searchResult = result.getRecords().stream()
                .filter(user -> !user.getId().equals(userId))           // 排除自己
                .filter(user -> !isBlacklisted(userId, user.getId()))   // 排除自己拉黑的用户
                .filter(user -> !isBlacklisted(user.getId(), userId))   // 排除把自己拉黑的用户
                .filter(user -> canViewUserInfo(userId, user))          // 资料可见性过滤
                .filter(user -> !"email".equals(finalType) || canSearchByEmail(user)) // 邮箱搜索开关过滤
                .peek(user -> cacheUserInfo(user))                      // 异步回写用户信息缓存
                .map(user -> buildSearchUserResponse(userId, user))     // 构建脱敏用户信息
                .collect(Collectors.toList());

        return Result.ok(searchResult, result.getTotal());
    }

    /**
     * 获取用户列表
     * 分页返回所有用户，支持按创建时间或ID排序，自动过滤被拉黑的用户
     *
     * @param page 页码
     * @param pageSize 每页数量
     * @param sort 排序字段（createTime-创建时间，id-用户ID）
     * @return 用户列表
     */
    @Override
    public Result getUserList(int page, int pageSize, String sort) {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 构建分页对象
        Page<User> pageObj = new Page<>(page, pageSize);

        // 构建查询条件：只查询未删除的用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getIsDelete, 0);

        // 根据排序参数设置排序方式
        if ("createTime".equals(sort)) {
            // 按创建时间倒序
            queryWrapper.orderByDesc(User::getCreateTime);
        } else {
            // 默认按ID倒序
            queryWrapper.orderByDesc(User::getId);
        }

        // 执行分页查询
        Page<User> result = userMapper.selectPage(pageObj, queryWrapper);

        // 过滤并构建返回数据
        List<Map<String, Object>> userList = result.getRecords().stream()
                .filter(user -> !user.getId().equals(userId))           // 排除自己
                .filter(user -> !isBlacklisted(userId, user.getId()))   // 排除自己拉黑的用户
                .filter(user -> !isBlacklisted(user.getId(), userId))   // 排除把自己拉黑的用户
                .filter(user -> canViewUserInfo(userId, user))          // 资料可见性过滤
                .map(user -> buildSearchUserResponse(userId, user))     // 构建脱敏用户信息
                .collect(Collectors.toList());

        return Result.ok(userList, result.getTotal());
    }

    /**
     * 查看用户资料
     * 根据隐私设置和黑名单状态返回相应权限的用户信息
     *
     * @param userId 要查看的用户ID
     * @return 用户资料
     */
    @Override
    public Result getUserProfile(Long userId) {
        // 获取当前登录用户ID
        Long currentUserId = UserHolder.getUserId();
        if (currentUserId == null) throw new BusinessException("用户未登录");

        // 参数校验
        if (userId == null || userId <= 0) return Result.fail("用户ID无效");

        // 如果是查看自己的资料，直接查库返回完整信息（无需缓存，包含隐私字段）
        if (userId.equals(currentUserId)) {
            User self = userMapper.selectById(userId);
            if (self == null || self.getIsDelete() == 1) return Result.fail("用户不存在");
            return Result.ok(buildUserResponse(self));
        }

        // 优先从 user_info:{userId} 缓存读取目标用户基础信息
        String cacheKey = USER_INFO_CACHE_KEY + userId;
        Map<Object, Object> cached = stringRedisTemplate.opsForHash().entries(cacheKey);
        User user;
        if (!cached.isEmpty()) {
            // 命中缓存：判断是否为空值占位符（防穿透）
            if ("1".equals(cached.get("__empty__"))) return Result.fail("用户不存在");
            user = new User();
            user.setId(Long.parseLong(String.valueOf(cached.get("id"))));
            user.setUserAccount(String.valueOf(cached.getOrDefault("userAccount", "")));
            user.setUserNickname(String.valueOf(cached.getOrDefault("userNickname", "")));
            user.setUserAvatar(String.valueOf(cached.getOrDefault("userAvatar", "")));
            user.setUserIntro(String.valueOf(cached.getOrDefault("userIntro", "")));
            user.setUserTags(String.valueOf(cached.getOrDefault("userTags", "")));
            user.setPrivacySetting(String.valueOf(cached.getOrDefault("privacySetting", "")));
            user.setIsDelete(0);
        } else {
            // 缓存未命中，查数据库
            user = userMapper.selectById(userId);
            if (user == null || user.getIsDelete() == 1) {
                // 不存在：写空值占位符防穿透，TTL 1 分钟
                stringRedisTemplate.opsForHash().put(cacheKey, "__empty__", "1");
                stringRedisTemplate.expire(cacheKey, 1, TimeUnit.MINUTES);
                return Result.fail("用户不存在");
            }
            // 回填缓存
            cacheUserInfo(user);
        }

        // 检查是否被拉黑（双向）：任一方拉黑对方，均返回用户不存在
        if (isBlacklisted(currentUserId, userId)) return Result.fail("用户不存在");
        if (isBlacklisted(userId, currentUserId)) return Result.fail("用户不存在");

        // 检查目标用户的隐私设置
        String privacySettingStr = user.getPrivacySetting();
        PrivacySettingDTO privacySetting = privacySettingStr != null && !privacySettingStr.isEmpty()
                ? JSON.parseObject(privacySettingStr, PrivacySettingDTO.class)
                : new PrivacySettingDTO();

        // 如果隐私设置为仅团队成员可见(viewInfo=2)，检查是否在同一团队
        if (privacySetting.getViewInfo() != null && privacySetting.getViewInfo() == 2) {
            LambdaQueryWrapper<TeamMember> qw1 = new LambdaQueryWrapper<>();
            qw1.eq(TeamMember::getUserId, currentUserId)
                    .eq(TeamMember::getIsQuit, 0);
            List<Long> myTeamIds = teamMemberMapper.selectList(qw1).stream()
                    .map(TeamMember::getTeamId)
                    .collect(Collectors.toList());
            boolean inSameTeam = false;
            if (!myTeamIds.isEmpty()) {
                LambdaQueryWrapper<TeamMember> qw2 = new LambdaQueryWrapper<>();
                qw2.eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getIsQuit, 0)
                        .in(TeamMember::getTeamId, myTeamIds);
                inSameTeam = teamMemberMapper.selectCount(qw2) > 0;
            }
            if (!inSameTeam) return Result.fail("无权查看该用户资料");
        }

        // 返回脱敏用户信息
        return Result.ok(buildSearchUserResponse(currentUserId, user));
    }

    /**
     * 检查是否被拉黑
     * 优先查询 Redis Set 缓存，缓存未命中时查数据库并写入缓存
     *
     * @param userId       拉黑方用户ID
     * @param targetUserId 被拉黑方用户ID
     * @return true表示已拉黑，false表示未拉黑
     */
    private boolean isBlacklisted(Long userId, Long targetUserId) {
        String cacheKey = BLACKLIST_CACHE_KEY + userId;

        // 1. 先判断缓存 Key 是否存在
        Boolean hasKey = stringRedisTemplate.hasKey(cacheKey);
        if (Boolean.TRUE.equals(hasKey)) {
            // 缓存命中，直接判断目标用户是否在黑名单 Set 中
            return Boolean.TRUE.equals(
                    stringRedisTemplate.opsForSet().isMember(cacheKey, String.valueOf(targetUserId)));
        }

        // 2. 缓存不存在，加互斥锁防止缓存击穿（大量并发同时穿透DB）
        String lockKey = "lock:blacklist:" + userId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
        try {
            if (!Boolean.TRUE.equals(locked)) {
                // 未拿到锁，短暂等待后重读缓存（此时持锁线程已回填缓存）
                Thread.sleep(50);
                Boolean hasKeyRetry = stringRedisTemplate.hasKey(cacheKey);
                if (Boolean.TRUE.equals(hasKeyRetry)) {
                    return Boolean.TRUE.equals(
                            stringRedisTemplate.opsForSet().isMember(cacheKey, String.valueOf(targetUserId)));
                }
                return false;
            }
            // 3. 拿到锁，从数据库加载当前用户的完整黑名单
            LambdaQueryWrapper<UserBlacklist> listWrapper = new LambdaQueryWrapper<>();
            listWrapper.eq(UserBlacklist::getUserId, userId)
                    .eq(UserBlacklist::getIsDelete, 0)
                    .select(UserBlacklist::getBlackUserId);
            List<UserBlacklist> blacklistRecords = userBlacklistMapper.selectList(listWrapper);
            // TTL 加随机抖动（±2小时），防雪崩
            long ttl = BLACKLIST_CACHE_TTL_HOURS + ThreadLocalRandom.current().nextLong(-2, 3);
            if (!blacklistRecords.isEmpty()) {
                String[] ids = blacklistRecords.stream()
                        .map(b -> String.valueOf(b.getBlackUserId()))
                        .toArray(String[]::new);
                stringRedisTemplate.opsForSet().add(cacheKey, ids);
                stringRedisTemplate.expire(cacheKey, ttl, TimeUnit.HOURS);
                return blacklistRecords.stream().anyMatch(b -> b.getBlackUserId().equals(targetUserId));
            } else {
                // 黑名单为空，写占位符「0」防缓存穿透，TTL 缩短为 5 分钟
                stringRedisTemplate.opsForSet().add(cacheKey, "0");
                stringRedisTemplate.expire(cacheKey, 5, TimeUnit.MINUTES);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 判断用户是否允许被邮箱搜索（searchByEmail=1 才允许）
     *
     * @param targetUser 目标用户实体
     * @return true 表示允许邮箱搜索，false 表示不允许
     */
    private boolean canSearchByEmail(User targetUser) {
        String privacySettingStr = targetUser.getPrivacySetting();
        if (privacySettingStr == null || privacySettingStr.isEmpty()) return false;

        try {
            PrivacySettingDTO privacySetting = JSON.parseObject(privacySettingStr, PrivacySettingDTO.class);
            return privacySetting != null && privacySetting.getSearchByEmail() != null && privacySetting.getSearchByEmail() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断当前用户是否有权限在搜索/列表场景查看目标用户资料
     * 规则：viewInfo=1 所有人可见；viewInfo=2 仅同团队成员可见；其他值按不可见处理
     *
     * @param currentUserId 当前登录用户 ID
     * @param targetUser    目标用户实体
     * @return true 表示有权查看，false 表示无权查看
     */
    private boolean canViewUserInfo(Long currentUserId, User targetUser) {
        String privacySettingStr = targetUser.getPrivacySetting();
        if (privacySettingStr == null || privacySettingStr.isEmpty()) return true;

        PrivacySettingDTO privacySetting;
        try {
            privacySetting = JSON.parseObject(privacySettingStr, PrivacySettingDTO.class);
        } catch (Exception e) {
            return true;
        }
        if (privacySetting == null || privacySetting.getViewInfo() == null) return true;

        // 1-所有人可见
        if (privacySetting.getViewInfo() == 1) return true;

        // 2-仅团队成员可见
        if (privacySetting.getViewInfo() == 2) {
            LambdaQueryWrapper<com.zero.usercenter.Model.TeamMember> qw1 = new LambdaQueryWrapper<>();
            qw1.eq(com.zero.usercenter.Model.TeamMember::getUserId, currentUserId)
                    .eq(com.zero.usercenter.Model.TeamMember::getIsQuit, 0);
            List<Long> myTeamIds = teamMemberMapper.selectList(qw1).stream()
                    .map(com.zero.usercenter.Model.TeamMember::getTeamId)
                    .collect(java.util.stream.Collectors.toList());
            if (myTeamIds.isEmpty()) return false;

            LambdaQueryWrapper<com.zero.usercenter.Model.TeamMember> qw2 = new LambdaQueryWrapper<>();
            qw2.eq(com.zero.usercenter.Model.TeamMember::getUserId, targetUser.getId())
                    .eq(com.zero.usercenter.Model.TeamMember::getIsQuit, 0)
                    .in(com.zero.usercenter.Model.TeamMember::getTeamId, myTeamIds);
            return teamMemberMapper.selectCount(qw2) > 0;
        }

        return false;
    }

    /**
     * 将用户基础信息回写到 Redis Hash 缓存（user_info:{userId}）
     * TTL：USER_INFO_CACHE_TTL_MINUTES ± 随机1分钟，防雪崩
     * 写入字段：id/userAccount/userNickname/userAvatar/userIntro/userTags/privacySetting
     * 调用场景：searchUser 命中结果时 peek 回写；getUserProfile 查库后回写
     *
     * @param user 要缓存的用户实体
     */
    private void cacheUserInfo(User user) {
        String cacheKey = USER_INFO_CACHE_KEY + user.getId();
        Map<String, String> map = new HashMap<>();
        map.put("id", String.valueOf(user.getId()));
        map.put("userAccount", user.getUserAccount() != null ? user.getUserAccount() : "");
        map.put("userNickname", user.getUserNickname() != null ? user.getUserNickname() : "");
        map.put("userAvatar", user.getUserAvatar() != null ? user.getUserAvatar() : "");
        map.put("userIntro", user.getUserIntro() != null ? user.getUserIntro() : "");
        map.put("userTags", user.getUserTags() != null ? user.getUserTags() : "");
        map.put("privacySetting", user.getPrivacySetting() != null ? user.getPrivacySetting() : "");
        stringRedisTemplate.opsForHash().putAll(cacheKey, map);
        // TTL 加随机抖动（±1分钟），防止批量搜索后缓存集中过期
        long ttl = USER_INFO_CACHE_TTL_MINUTES + ThreadLocalRandom.current().nextLong(-1, 2);
        stringRedisTemplate.expire(cacheKey, Math.max(ttl, 1), TimeUnit.MINUTES);
    }

    /**
     * 落库并实时推送系统通知
     * 先写入 t_system_notice，再通过 WebSocket 推送；用户离线时推送静默跳过，通知不丢失
     *
     * @param notice 已填充好字段的系统通知实体
     */
    private void sendRealtimeSystemNotice(SystemNotice notice) {
        systemNoticeMapper.insert(notice);

        Map<String, Object> push = new HashMap<>();
        push.put("type", "system_notice");
        Map<String, Object> data = new HashMap<>();
        data.put("noticeId", notice.getId());
        data.put("noticeType", notice.getNoticeType());
        data.put("noticeContent", notice.getNoticeContent());
        data.put("relatedId", notice.getRelatedId());
        push.put("data", data);
        chatWebSocketHandler.sendToUser(notice.getUserId(), JSON.toJSONString(push));
    }

    /**
     * 构建用户响应信息（完整版）
     * 用于返回当前用户自己的完整信息，包括隐私设置
     *
     * @param user 用户对象
     * @return 用户信息Map
     */
    private Map<String, Object> buildUserResponse(User user) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("userAccount", user.getUserAccount());
        response.put("userNickname", user.getUserNickname());
        response.put("userAvatar", user.getUserAvatar());
        response.put("userIntro", user.getUserIntro());
        response.put("userTags", user.getUserTags());
        response.put("userEmail", user.getUserEmail());

        // 解析并返回隐私设置
        if (user.getPrivacySetting() != null && !user.getPrivacySetting().isEmpty()) {
            response.put("privacySetting", JSON.parseObject(user.getPrivacySetting()));
        }

        response.put("createTime", user.getCreateTime());
        response.put("updateTime", user.getUpdateTime());
        return response;
    }

    /**
     * 构建搜索用户响应信息（脱敏版）
     * 用于返回搜索结果或用户列表中的用户信息，包含好友关系状态
     *
     * @param currentUserId 当前用户ID
     * @param user 用户对象
     * @return 用户信息Map
     */
    private Map<String, Object> buildSearchUserResponse(Long currentUserId, User user) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("userAccount", user.getUserAccount());
        response.put("userNickname", user.getUserNickname());
        response.put("userAvatar", user.getUserAvatar());
        response.put("userIntro", user.getUserIntro());
        response.put("userTags", user.getUserTags());

        // 查询与当前用户的好友关系
        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFriend::getUserId, currentUserId)
                .eq(UserFriend::getFriendId, user.getId());
        UserFriend friendRelation = userFriendMapper.selectOne(queryWrapper);

        // 设置好友关系状态
        if (friendRelation != null) {
            response.put("isFriend", friendRelation.getFriendStatus() == 1);  // 是否已成为好友
            response.put("friendStatus", friendRelation.getFriendStatus());   // 好友状态（0-待验证，1-已成为好友，2-已拒绝）
        } else {
            response.put("isFriend", false);
            response.put("friendStatus", -1);  // -1表示无好友关系
        }

        return response;
    }

    /**
     * 获取好友申请列表
     * 分页返回当前用户收到的所有好友申请（friend_status=0）
     *
     * @param page 页码
     * @param pageSize 每页数量
     * @return 好友申请列表
     */
    @Override
    public Result getFriendRequests(int page, int pageSize) {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 构建分页对象
        Page<UserFriend> pageObj = new Page<>(page, pageSize);

        // 构建查询条件：查询当前用户收到的所有待验证申请（friend_status=0）
        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFriend::getFriendId, userId)
                .eq(UserFriend::getFriendStatus, 0)
                .orderByDesc(UserFriend::getCreateTime);

        // 执行分页查询
        Page<UserFriend> result = userFriendMapper.selectPage(pageObj, queryWrapper);

        // 构建返回数据：获取每个申请人的详细信息
        List<Map<String, Object>> requestList = result.getRecords().stream().map(uf -> {
            // 查询申请人的用户信息
            User applicant = userMapper.selectById(uf.getUserId());
            Map<String, Object> requestInfo = new HashMap<>();
            requestInfo.put("requestId", uf.getId());
            requestInfo.put("applicantId", uf.getUserId());
            requestInfo.put("userNickname", applicant.getUserNickname());
            requestInfo.put("userAvatar", applicant.getUserAvatar());
            requestInfo.put("userIntro", applicant.getUserIntro());
            requestInfo.put("userTags", applicant.getUserTags());
            requestInfo.put("applyMsg", uf.getFriendRemark());  // 申请备注
            requestInfo.put("createTime", uf.getCreateTime());
            return requestInfo;
        }).collect(Collectors.toList());

        return Result.ok(requestList, result.getTotal());
    }

    /**
     * 获取未读通知数
     * 返回当前用户的未读通知总数
     *
     * @return 未读通知数
     */
    @Override
    public Result getUnreadNoticeCount() {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 查询未读通知数
        LambdaQueryWrapper<SystemNotice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SystemNotice::getUserId, userId)
                .eq(SystemNotice::getIsRead, 0)
                .eq(SystemNotice::getIsDelete, 0);
        long unreadCount = systemNoticeMapper.selectCount(queryWrapper);

        return Result.ok(unreadCount);
    }

    /**
     * 获取通知列表
     * 分页返回当前用户的通知列表
     *
     * @param page 页码
     * @param pageSize 每页数量
     * @param isRead 是否已读（0-未读，1-已读，null-全部）
     * @return 通知列表
     */
    @Override
    public Result getNoticeList(int page, int pageSize, Integer isRead) {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 构建分页对象
        Page<SystemNotice> pageObj = new Page<>(page, pageSize);

        // 构建查询条件
        LambdaQueryWrapper<SystemNotice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SystemNotice::getUserId, userId)
                .eq(SystemNotice::getIsDelete, 0);

        // 如果指定了 isRead 参数，则按该条件过滤
        if (isRead != null) {
            queryWrapper.eq(SystemNotice::getIsRead, isRead);
        }

        // 按创建时间倒序
        queryWrapper.orderByDesc(SystemNotice::getCreateTime);

        // 执行分页查询
        Page<SystemNotice> result = systemNoticeMapper.selectPage(pageObj, queryWrapper);

        // 构建返回数据
        List<Map<String, Object>> noticeList = result.getRecords().stream().map(notice -> {
            Map<String, Object> noticeInfo = new HashMap<>();
            noticeInfo.put("id", notice.getId());
            noticeInfo.put("noticeType", notice.getNoticeType());
            noticeInfo.put("noticeContent", notice.getNoticeContent());
            noticeInfo.put("relatedId", notice.getRelatedId());
            noticeInfo.put("isRead", notice.getIsRead());
            noticeInfo.put("createTime", notice.getCreateTime());
            return noticeInfo;
        }).collect(Collectors.toList());

        return Result.ok(noticeList, result.getTotal());
    }

    /**
     * 标记通知为已读
     * 将指定的通知标记为已读
     *
     * @param noticeIds 通知ID列表
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result markNoticeAsRead(List<Long> noticeIds) {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 参数校验
        if (noticeIds == null || noticeIds.isEmpty()) {
            return Result.fail("通知ID列表不能为空");
        }

        // 批量更新通知为已读
        LambdaUpdateWrapper<SystemNotice> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SystemNotice::getUserId, userId)
                .in(SystemNotice::getId, noticeIds)
                .set(SystemNotice::getIsRead, 1)
                .set(SystemNotice::getReadTime, LocalDateTime.now());

        systemNoticeMapper.update(null, updateWrapper);

        return Result.ok("通知已标记为已读");
    }

    /**
     * 删除通知
     * 软删除指定的通知
     *
     * @param noticeIds 通知ID列表
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result deleteNotice(List<Long> noticeIds) {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 参数校验
        if (noticeIds == null || noticeIds.isEmpty()) {
            return Result.fail("通知ID列表不能为空");
        }

        // 批量软删除通知
        LambdaUpdateWrapper<SystemNotice> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SystemNotice::getUserId, userId)
                .in(SystemNotice::getId, noticeIds)
                .set(SystemNotice::getIsDelete, 1);

        systemNoticeMapper.update(null, updateWrapper);

        return Result.ok("通知已删除");
    }
}
