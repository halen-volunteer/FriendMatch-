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
import com.zero.usercenter.mq.AsyncMessageService;
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
 * 用户管理服务。
 * 聚合资料编辑、隐私设置、好友关系、黑名单、通知中心等用户侧高频能力。
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
    @Resource
    private AsyncMessageService asyncMessageService;

    /**
     * 更新当前用户资料，并同步刷新登录态快照与用户资料缓存。
     */
    @Override
    @Transactional
    public Result updateUserProfile(UserProfileUpdateDTO dto) {
        // 1. 先做登录态和资料字段校验，保证昵称、简介、标签都在允许范围内。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

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

        if (dto.getUserTags() != null) {
            String tags = dto.getUserTags().trim();
            String[] tagArray = tags.split(",");
            if (tagArray.length > 5) return Result.fail("标签数量不能超过5个");
            for (String tag : tagArray) if (tag.length() > 20) return Result.fail("单个标签长度不能超过20字符");
            dto.setUserTags(tags);
        }

        // 2. 组装增量更新对象，只覆盖前端这次提交的字段。
        User user = new User();
        user.setId(userId);
        if (dto.getUserNickname() != null) user.setUserNickname(dto.getUserNickname());
        if (dto.getUserAvatar() != null) user.setUserAvatar(dto.getUserAvatar());
        if (dto.getUserIntro() != null) user.setUserIntro(dto.getUserIntro());
        if (dto.getUserTags() != null) user.setUserTags(dto.getUserTags());

        userMapper.updateById(user);

        // 3. 登录态里也缓存了一份轻量用户快照，这里同步回写，避免“资料已改但 token 视图还是旧值”。
        // 登录态里缓存了一份轻量用户快照，这里顺手同步，避免用户改资料后 `/api/auth/me` 仍返回旧值。
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

        // 4. 清掉用户资料缓存，并回表拿最新数据返回给前端。
        stringRedisTemplate.delete(USER_INFO_CACHE_KEY + userId);

        User updatedUser = userMapper.selectById(userId);
        return Result.ok(buildUserResponse(updatedUser));
    }

    /**
     * 获取当前用户隐私设置；未配置时返回默认值。
     */
    @Override
    public Result getPrivacySetting() {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        // 读取当前用户隐私配置字符串；未配置时后面会按默认值处理。
        String privacySettingStr = user.getPrivacySetting();

        if (privacySettingStr == null || privacySettingStr.isEmpty()) {
            PrivacySettingDTO defaultSetting = new PrivacySettingDTO();
            defaultSetting.setViewInfo(1);
            defaultSetting.setSendMsg(1);
            defaultSetting.setSearchByEmail(0);
            return Result.ok(defaultSetting);
        }

        return Result.ok(JSON.parseObject(privacySettingStr, PrivacySettingDTO.class));
    }

    /**
     * 更新当前用户隐私设置。
     */
    @Override
    @Transactional
    public Result updatePrivacySetting(PrivacySettingDTO dto) {
        // 1. 先对隐私配置枚举值做兜底校验，避免写入无法解释的状态。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        if (dto.getViewInfo() != null && (dto.getViewInfo() < 1 || dto.getViewInfo() > 2))
            return Result.fail("资料可见性参数无效");

        if (dto.getSendMsg() != null && (dto.getSendMsg() < 1 || dto.getSendMsg() > 3))
            return Result.fail("消息接收权限参数无效");

        if (dto.getSearchByEmail() != null && (dto.getSearchByEmail() < 0 || dto.getSearchByEmail() > 1))
            return Result.fail("邮箱搜索权限参数无效");

        User user = userMapper.selectById(userId);
        String privacySettingStr = user.getPrivacySetting();

        // 2. 读取旧配置并按字段增量覆盖，未传的设置保持原值不变。
        PrivacySettingDTO currentSetting = privacySettingStr != null && !privacySettingStr.isEmpty()
                ? JSON.parseObject(privacySettingStr, PrivacySettingDTO.class)
                : new PrivacySettingDTO();

        if (dto.getViewInfo() != null) currentSetting.setViewInfo(dto.getViewInfo());
        if (dto.getSendMsg() != null) currentSetting.setSendMsg(dto.getSendMsg());
        if (dto.getSearchByEmail() != null) currentSetting.setSearchByEmail(dto.getSearchByEmail());

        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setPrivacySetting(JSON.toJSONString(currentSetting));

        userMapper.updateById(updateUser);

        // 3. 隐私设置会影响搜索、私聊和资料展示能力，所以这里主动清理资料缓存。
        // 清理用户资料缓存，避免私聊页仍读取旧的 privacySetting，
        // 导致输入框可发送状态与实际发送校验不一致
        stringRedisTemplate.delete(USER_INFO_CACHE_KEY + userId);

        return Result.ok(currentSetting);
    }

    /**
     * 发起好友申请。
     */
    @Override
    @Transactional
    public Result addFriend(FriendOperationDTO dto) {
        // 1. 先确认目标用户存在且不允许添加自己。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        Long friendId = dto.getFriendId();
        if (friendId == null || friendId <= 0) return Result.fail("好友ID无效");
        if (userId.equals(friendId)) return Result.fail("不能添加自己为好友");

        User friendUser = userMapper.selectById(friendId);
        if (friendUser == null || friendUser.getIsDelete() == 1) return Result.fail("用户不存在");

        // 2. 先排除已是好友和被对方拉黑的场景，避免无意义申请。
        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper
                .and(w -> w.eq(UserFriend::getUserId, userId)
                        .eq(UserFriend::getFriendId, friendId))
                .or(w -> w.eq(UserFriend::getUserId, friendId)
                        .eq(UserFriend::getFriendId, userId)))
                .eq(UserFriend::getFriendStatus, 1);
        if (userFriendMapper.selectOne(queryWrapper) != null) return Result.fail("已经是好友了");

        LambdaQueryWrapper<UserBlacklist> blacklistWrapper = new LambdaQueryWrapper<>();
        blacklistWrapper.eq(UserBlacklist::getUserId, friendId)
                .eq(UserBlacklist::getBlackUserId, userId)
                .eq(UserBlacklist::getIsDelete, 0);
        if (userBlacklistMapper.selectOne(blacklistWrapper) != null)
            return Result.fail("对方已拉黑你，无法添加好友");

        String privacySettingStr = friendUser.getPrivacySetting();
        PrivacySettingDTO privacySetting = privacySettingStr != null && !privacySettingStr.isEmpty()
                ? JSON.parseObject(privacySettingStr, PrivacySettingDTO.class)
                : new PrivacySettingDTO();

        // 3. 申请记录只保留一条待处理正向申请，防止重复点击造成脏数据。
        // 好友申请只允许存在一条待处理正向申请，避免多端或重复点击制造脏数据。
        LambdaQueryWrapper<UserFriend> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getFriendStatus, 0);
        if (userFriendMapper.selectOne(pendingWrapper) != null)
            return Result.fail("已经申请过了，请等待对方同意");

        UserFriend userFriend = new UserFriend();
        userFriend.setUserId(userId);
        userFriend.setFriendId(friendId);
        userFriend.setFriendStatus(0);
        userFriend.setFriendRemark(dto.getFriendRemark());
        userFriendMapper.insert(userFriend);

        // 4. 主流程只负责入库，通知异步发送，避免好友申请接口被消息链路拖慢。
        String senderNickname = userMapper.selectById(userId).getUserNickname();
        Thread.ofVirtual().start(() -> {
            asyncMessageService.sendSystemNotice(friendId, 1,
                    "用户【" + senderNickname + "】向你发送了好友申请", userId);
        });

        return Result.ok("好友申请已发送");
    }

    /**
     * 获取好友列表
     * 好友 ID 先走 Redis Set 缓存，再批量回表查详情，避免高频列表请求持续扫关系表。
     */
    @Override
    public Result getFriendList(int page, int pageSize) {
        // 1. 好友列表优先读 Redis Set，命中时直接做分页和详情补齐。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        String cacheKey = FRIEND_LIST_CACHE_KEY + userId;

        Boolean cacheExists = stringRedisTemplate.hasKey(cacheKey);
        List<Long> friendIds;
        if (Boolean.TRUE.equals(cacheExists)) {
            Set<String> cachedIds = stringRedisTemplate.opsForSet().members(cacheKey);
            friendIds = cachedIds == null ? Collections.emptyList() :
                    cachedIds.stream()
                            .filter(s -> !"0".equals(s))
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
        } else {
            // 2. 缓存未命中时，用短锁保护数据库回源，避免高并发把关系表打穿。
            // 用 hasKey 区分“空列表缓存”和“缓存未建”，并在未命中时加短锁避免并发击穿。
            String lockKey = "lock:friend_list:" + userId;
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
            try {
                if (!Boolean.TRUE.equals(locked)) {
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
                    long ttl = FRIEND_LIST_CACHE_TTL_HOURS + ThreadLocalRandom.current().nextLong(-2, 3);
                    if (!friendIds.isEmpty()) {
                        String[] idArr = friendIds.stream().map(String::valueOf).toArray(String[]::new);
                        stringRedisTemplate.opsForSet().add(cacheKey, idArr);
                    } else {
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

        // 3. 拿到好友 ID 列表后，再分页查详情并拼装最终返回结构。
        int total = friendIds.size();
        int fromIndex = Math.min((page - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Long> pagedIds = friendIds.subList(fromIndex, toIndex);

        List<User> friendUsers = pagedIds.isEmpty() ? Collections.emptyList() : userMapper.selectByIds(pagedIds);
        Map<Long, User> friendUserMap = friendUsers.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

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
     * 同意好友申请。
     * 将待处理申请转为好友关系，并同步补齐反向关系、缓存与推荐刷新。
     */
    @Override
    @Transactional
    public Result agreeFriend(Long friendId) {
        // 1. 先查出这条待处理好友申请，确保同意动作有明确目标。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (friendId == null || friendId <= 0) return Result.fail("好友ID无效");

        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFriend::getUserId, friendId)
                .eq(UserFriend::getFriendId, userId)
                .eq(UserFriend::getFriendStatus, 0);
        UserFriend friendRequest = userFriendMapper.selectOne(queryWrapper);
        if (friendRequest == null) return Result.fail("好友申请不存在");

        LambdaQueryWrapper<UserFriend> reverseExistsWrapper = new LambdaQueryWrapper<>();
        reverseExistsWrapper.eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getFriendStatus, 1);
        if (userFriendMapper.selectOne(reverseExistsWrapper) != null) {
            // 2. 命中历史脏数据时，只把待处理申请补全为已同意，不再重复插入反向记录。
            // 兼容历史脏数据：如果当前用户侧已经存在好友记录，只需把待处理申请补成已同意即可，
            // 避免再次插入反向记录触发重复关系。
            friendRequest.setFriendStatus(1);
            if (friendRequest.getAgreeTime() == null) {
                friendRequest.setAgreeTime(LocalDateTime.now());
            }
            userFriendMapper.updateById(friendRequest);
            stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + userId);
            stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + friendId);
            return Result.ok("已同意好友申请");
        }

        friendRequest.setFriendStatus(1);
        friendRequest.setAgreeTime(LocalDateTime.now());
        userFriendMapper.updateById(friendRequest);

        // 3. 正常场景下补齐反向好友记录，后续查询和删除都能统一按单向关系处理。
        // 好友关系按双向两条记录维护，后续查询和删除都可直接按 userId -> friendId 方向处理。
        UserFriend reverseFriend = new UserFriend();
        reverseFriend.setUserId(userId);
        reverseFriend.setFriendId(friendId);
        reverseFriend.setFriendStatus(1);
        reverseFriend.setAgreeTime(LocalDateTime.now());
        userFriendMapper.insert(reverseFriend);

        // 3. 最后清理好友/黑名单缓存并隐藏当前用户侧会话入口，让界面状态立即同步。
        // 3. 最后清理好友/黑名单缓存并隐藏当前用户侧会话入口，让界面状态立即同步。
        // 3. 最后清理好友/黑名单缓存并隐藏当前用户侧会话入口，让界面状态立即同步。
        stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + userId);
        stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + friendId);

        // 4. 好友关系变化会影响推荐结果和通知中心，这些副作用异步执行。
        Thread.ofVirtual().start(() -> {
            searchRecommendService.refreshRecommendForUser(userId);
            searchRecommendService.refreshRecommendForUser(friendId);
        });

        String currentNickname = userMapper.selectById(userId).getUserNickname();
        Thread.ofVirtual().start(() -> {
            asyncMessageService.sendSystemNotice(friendId, 3,
                    "用户【" + currentNickname + "】已同意你的好友申请", userId);
        });

        return Result.ok("已同意好友申请");
    }

    /**
     * 拒绝好友申请。
     * 仅更新申请状态，不建立任何好友关系。
     */
    @Override
    @Transactional
    public Result rejectFriend(Long friendId) {
        // 1. 拒绝时只修改申请状态，不生成任何好友关系数据。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (friendId == null || friendId <= 0) return Result.fail("好友ID无效");

        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFriend::getUserId, friendId)
                .eq(UserFriend::getFriendId, userId)
                .eq(UserFriend::getFriendStatus, 0);
        UserFriend friendRequest = userFriendMapper.selectOne(queryWrapper);
        if (friendRequest == null) return Result.fail("好友申请不存在");

        friendRequest.setFriendStatus(2);
        userFriendMapper.updateById(friendRequest);

        // 2. 结果通过系统通知异步告知申请方。
        String rejecterNickname = userMapper.selectById(userId).getUserNickname();
        Thread.ofVirtual().start(() -> {
            SystemNotice rejectNotice = new SystemNotice();
            rejectNotice.setUserId(friendId);
            rejectNotice.setNoticeType(2);
            rejectNotice.setNoticeContent("用户【" + rejecterNickname + "】拒绝了你的好友申请");
            rejectNotice.setRelatedId(userId);
            rejectNotice.setIsRead(0);
            rejectNotice.setIsDelete(0);
            asyncMessageService.sendSystemNotice(
                    rejectNotice.getUserId(),
                    rejectNotice.getNoticeType(),
                    rejectNotice.getNoticeContent(),
                    rejectNotice.getRelatedId());
        });

        return Result.ok("已拒绝好友申请");
    }

    /**
     * 删除好友关系。
     * 同时移除当前用户侧会话入口，避免已删好友仍停留在最近会话列表。
     */
    @Override
    @Transactional
    public Result deleteFriend(Long friendId) {
        // 1. 删除好友需要同时删除双向关系，避免出现单边仍是好友的脏状态。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (friendId == null || friendId <= 0) return Result.fail("好友ID无效");

        LambdaQueryWrapper<UserFriend> queryWrapper1 = new LambdaQueryWrapper<>();
        queryWrapper1.eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getFriendStatus, 1);
        int removed1 = userFriendMapper.delete(queryWrapper1);

        LambdaQueryWrapper<UserFriend> queryWrapper2 = new LambdaQueryWrapper<>();
        queryWrapper2.eq(UserFriend::getUserId, friendId)
                .eq(UserFriend::getFriendId, userId)
                .eq(UserFriend::getFriendStatus, 1);
        int removed2 = userFriendMapper.delete(queryWrapper2);

        if (removed1 == 0 && removed2 == 0) {
            return Result.fail("好友关系不存在或已删除");
        }

        // 2. 删除后顺手清理缓存，并把当前用户侧最近会话入口隐藏起来。
        stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + userId);
        stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + friendId);
        hidePrivateConversation(userId, friendId);

        return Result.ok("已删除好友");
    }

    /**
     * 拉黑用户。
     * 拉黑会中断双方私聊能力，并把已有好友关系临时转为“已拉黑”状态，便于后续解除时恢复。
     */
    @Override
    @Transactional
    public Result addBlacklist(BlacklistOperationDTO dto) {
        // 1. 拉黑前先确认目标用户存在且不是自己。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        Long blackUserId = dto.getBlackUserId();
        if (blackUserId == null || blackUserId <= 0) return Result.fail("用户ID无效");
        if (userId.equals(blackUserId)) return Result.fail("不能拉黑自己");

        User blackUser = userMapper.selectById(blackUserId);
        if (blackUser == null || blackUser.getIsDelete() == 1) return Result.fail("用户不存在");

        // 黑名单存在唯一索引约束，这里先查活动记录做幂等兜底，避免重复点击导致唯一键冲突。
        LambdaQueryWrapper<UserBlacklist> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlackUserId, blackUserId)
                .eq(UserBlacklist::getIsDelete, 0);
        UserBlacklist activeRecord = userBlacklistMapper.selectOne(activeWrapper);
        if (activeRecord != null) {
            return Result.ok("已拉黑用户");
        }

        LambdaQueryWrapper<UserBlacklist> historyWrapper = new LambdaQueryWrapper<>();
        historyWrapper.eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlackUserId, blackUserId)
                .eq(UserBlacklist::getIsDelete, 1);
        UserBlacklist historyRecord = userBlacklistMapper.selectOne(historyWrapper);

        if (historyRecord != null) {
            historyRecord.setIsDelete(0);
            historyRecord.setBlackReason(dto.getBlackReason());
            userBlacklistMapper.updateById(historyRecord);
        } else {
            UserBlacklist blacklist = new UserBlacklist();
            blacklist.setUserId(userId);
            blacklist.setBlackUserId(blackUserId);
            blacklist.setBlackReason(dto.getBlackReason());
            blacklist.setIsDelete(0);
            userBlacklistMapper.insert(blacklist);
        }

        // 2. 拉黑不直接删好友，而是先把好友态降级成“已拉黑”，便于后续恢复。
        // 不直接物理删除好友关系，而是暂时降级成状态 3。
        // 这样解除拉黑后可以恢复双方一致的好友态，避免出现一边能发消息、一边不能发的单向异常。
        markFriendRelationAsBlacklisted(userId, blackUserId);
        markFriendRelationAsBlacklisted(blackUserId, userId);

        stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + userId);
        stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + blackUserId);
        // 黑名单缓存必须同步清理，避免后续查询继续命中旧拉黑结果。
        stringRedisTemplate.delete(BLACKLIST_CACHE_KEY + userId);
        hidePrivateConversation(userId, blackUserId);

        return Result.ok("已拉黑用户");
    }

    /**
     * 解除拉黑。
     * 若拉黑前双方原本是好友，则会一并恢复好友关系状态。
     */
    @Override
    @Transactional
    public Result removeBlacklist(Long blackUserId) {
        // 1. 解除拉黑先确认活动黑名单记录存在。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (blackUserId == null || blackUserId <= 0) return Result.fail("用户ID无效");

        LambdaQueryWrapper<UserBlacklist> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlackUserId, blackUserId)
                .eq(UserBlacklist::getIsDelete, 0);
        UserBlacklist blacklist = userBlacklistMapper.selectOne(queryWrapper);
        if (blacklist == null) return Result.fail("黑名单记录不存在");

        LambdaQueryWrapper<UserBlacklist> historyWrapper = new LambdaQueryWrapper<>();
        historyWrapper.eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlackUserId, blackUserId)
                .eq(UserBlacklist::getIsDelete, 1);
        UserBlacklist historyRecord = userBlacklistMapper.selectOne(historyWrapper);
        if (historyRecord != null) {
            userBlacklistMapper.deleteById(blacklist.getId());
        } else {
            blacklist.setIsDelete(1);
            userBlacklistMapper.updateById(blacklist);
        }

        // 2. 若拉黑前是好友，则尝试恢复双方好友状态，再统一清缓存。
        restoreFriendRelationIfNeeded(userId, blackUserId);
        restoreFriendRelationIfNeeded(blackUserId, userId);

        // 3. 关系恢复后同步清理黑名单和好友缓存，避免前端继续读到旧关系状态。
        stringRedisTemplate.delete(BLACKLIST_CACHE_KEY + userId);
        stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + userId);
        stringRedisTemplate.delete(FRIEND_LIST_CACHE_KEY + blackUserId);

        return Result.ok("已解除拉黑");
    }

    /**
     * 将单向好友关系标记为“已拉黑”状态。
     * 这里只处理当前仍处于好友态的记录，避免误伤待处理申请或历史拒绝记录。
     *
     * @param userId   发起拉黑的一侧用户 ID
     * @param friendId 被拉黑的好友用户 ID
     */
    private void markFriendRelationAsBlacklisted(Long userId, Long friendId) {
        // 1. 只把“当前还是好友”的关系降级为已拉黑，避免把其它状态的历史记录改坏。
        LambdaUpdateWrapper<UserFriend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getFriendStatus, 1)
                .set(UserFriend::getFriendStatus, 3);
        userFriendMapper.update(null, updateWrapper);
    }

    /**
     * 在解除拉黑时按需恢复历史好友关系。
     * 如果旧记录只是被降级成“已拉黑”，这里会把它恢复回来；
     * 如果期间已经产生新的好友记录，则删除旧残留，避免出现互相矛盾的双记录。
     *
     * @param userId   当前方向的用户 ID
     * @param friendId 对端用户 ID
     */
    private void restoreFriendRelationIfNeeded(Long userId, Long friendId) {
        // 1. 先看当前方向是否存在“已拉黑”关系，没有则无需恢复。
        LambdaQueryWrapper<UserFriend> blacklistedWrapper = new LambdaQueryWrapper<>();
        blacklistedWrapper.eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getFriendStatus, 3);
        UserFriend blacklistedRelation = userFriendMapper.selectOne(blacklistedWrapper);
        if (blacklistedRelation == null) {
            return;
        }

        // 2. 如果期间已经重新建立了正常好友关系，就删除旧的拉黑残留，避免双记录冲突。
        LambdaQueryWrapper<UserFriend> activeFriendWrapper = new LambdaQueryWrapper<>();
        activeFriendWrapper.eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getFriendStatus, 1);
        if (userFriendMapper.selectCount(activeFriendWrapper) > 0) {
            userFriendMapper.deleteById(blacklistedRelation.getId());
            return;
        }

        // 3. 没有新好友记录时，直接把旧记录恢复成好友态，并补齐同意时间。
        blacklistedRelation.setFriendStatus(1);
        if (blacklistedRelation.getAgreeTime() == null) {
            blacklistedRelation.setAgreeTime(LocalDateTime.now());
        }
        userFriendMapper.updateById(blacklistedRelation);
    }

    /**
     * 隐藏当前用户侧的私聊会话入口。
     * 这里只影响会话列表展示，不删除历史消息，也不会影响对端继续查看。
     *
     * @param userId   需要隐藏会话的一侧用户 ID
     * @param friendId 私聊对端用户 ID
     */
    private void hidePrivateConversation(Long userId, Long friendId) {
        // 1. 先过滤空参数，避免拼出无效会话键。
        if (userId == null || friendId == null) {
            return;
        }

        // 2. 会话 ID 按小 ID_大 ID 统一生成，保证和主私聊链路使用同一套标识。
        Long min = Math.min(userId, friendId);
        Long max = Math.max(userId, friendId);

        // 3. 只把当前用户视角的会话加入隐藏集合，不改消息表和对端状态。
        stringRedisTemplate.opsForSet().add(HIDDEN_CONVERSATIONS_KEY + userId, min + "_" + max);
    }

    /**
     * 获取当前用户黑名单列表。
     */
    @Override
    public Result getBlacklist(int page, int pageSize) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        Page<UserBlacklist> pageObj = new Page<>(page, pageSize);

        LambdaQueryWrapper<UserBlacklist> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getIsDelete, 0)
                .orderByDesc(UserBlacklist::getCreateTime);

        Page<UserBlacklist> result = userBlacklistMapper.selectPage(pageObj, queryWrapper);

        List<Map<String, Object>> blacklistData = result.getRecords().stream().map(bl -> {
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
     * 搜索用户。
     * 支持账号、昵称、标签、邮箱四种入口，并统一套用黑名单与隐私过滤规则。
     */
    @Override
    public Result searchUser(String keyword, String type, int page, int pageSize) {
        // 1. 搜索入口先校验关键词和类型，数据库只负责做粗筛。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        if (keyword == null || keyword.trim().isEmpty()) return Result.fail("搜索关键词不能为空");
        keyword = keyword.trim();

        Page<User> pageObj = new Page<>(page, pageSize);

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getIsDelete, 0);

        if ("account".equals(type)) {
            queryWrapper.like(User::getUserAccount, keyword);
        } else if ("nickname".equals(type)) {
            queryWrapper.like(User::getUserNickname, keyword);
        } else if ("tag".equals(type)) {
            queryWrapper.like(User::getUserTags, keyword);
        } else if ("email".equals(type)) {
            queryWrapper.like(User::getUserEmail, keyword);
        } else {
            return Result.fail("搜索类型无效");
        }

        Page<User> result = userMapper.selectPage(pageObj, queryWrapper);

        // 2. 结果集在内存层叠加黑名单、隐私和邮箱搜索规则，避免把复杂策略硬塞进 SQL。
        // 搜索结果先按数据库条件粗筛，再在内存层叠加隐私与黑名单规则，避免复杂业务规则直接硬编码进 SQL。
        final String finalType = type;
        List<Map<String, Object>> searchResult = result.getRecords().stream()
                .filter(user -> !user.getId().equals(userId))
                .filter(user -> !isBlacklisted(userId, user.getId()))
                .filter(user -> !isBlacklisted(user.getId(), userId))
                .filter(user -> canViewUserInfo(userId, user))
                .filter(user -> !"email".equals(finalType) || canSearchByEmail(user))
                .peek(this::cacheUserInfo)
                .map(user -> buildSearchUserResponse(userId, user))
                .collect(Collectors.toList());

        return Result.ok(searchResult, result.getTotal());
    }

    /**
     * 获取用户列表。
     * 复用与搜索一致的隐私和黑名单过滤逻辑。
     */
    @Override
    public Result getUserList(int page, int pageSize, String sort) {
        // 1. 列表接口与搜索接口保持同一套过滤规则，只是没有关键词条件。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        Page<User> pageObj = new Page<>(page, pageSize);

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getIsDelete, 0);

        if ("createTime".equals(sort)) {
            queryWrapper.orderByDesc(User::getCreateTime);
        } else {
            queryWrapper.orderByDesc(User::getId);
        }

        Page<User> result = userMapper.selectPage(pageObj, queryWrapper);

        // 2. 统一过滤自己、黑名单和隐私不可见用户，再拼装展示结果。
        List<Map<String, Object>> userList = result.getRecords().stream()
                .filter(user -> !user.getId().equals(userId))
                .filter(user -> !isBlacklisted(userId, user.getId()))
                .filter(user -> !isBlacklisted(user.getId(), userId))
                .filter(user -> canViewUserInfo(userId, user))
                .map(user -> buildSearchUserResponse(userId, user))
                .collect(Collectors.toList());

        return Result.ok(userList, result.getTotal());
    }

    /**
     * 查看用户资料。
     * 自己查看自己时返回完整信息；查看他人时复用缓存、黑名单与隐私校验。
     */
    @Override
    public Result getUserProfile(Long userId) {
        // 1. 自己看自己直接返回完整资料，查看他人则进入缓存和隐私校验链路。
        Long currentUserId = UserHolder.getUserId();
        if (currentUserId == null) throw new BusinessException("用户未登录");

        if (userId == null || userId <= 0) return Result.fail("用户ID无效");

        if (userId.equals(currentUserId)) {
            User self = userMapper.selectById(userId);
            if (self == null || self.getIsDelete() == 1) return Result.fail("用户不存在");
            return Result.ok(buildUserResponse(self));
        }

        // 2. 他人资料优先读缓存，未命中再回库并回填缓存。
        String cacheKey = USER_INFO_CACHE_KEY + userId;
        Map<Object, Object> cached = stringRedisTemplate.opsForHash().entries(cacheKey);
        User user;
        if (!cached.isEmpty()) {
            //缓存不为空
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
            //缓存为空
            user = userMapper.selectById(userId);
            if (user == null || user.getIsDelete() == 1) {
                stringRedisTemplate.opsForHash().put(cacheKey, "__empty__", "1");
                stringRedisTemplate.expire(cacheKey, 1, TimeUnit.MINUTES);
                return Result.fail("用户不存在");
            }
            cacheUserInfo(user);
        }

        // 3. 黑名单和隐私限制统一在这里兜底，对外始终收口成“用户不存在”或“无权查看”。
        // 对外统一返回“用户不存在”，避免借由接口行为推断自己是否被对方拉黑。
        if (isBlacklisted(currentUserId, userId)) return Result.fail("用户不存在");
        if (isBlacklisted(userId, currentUserId)) return Result.fail("用户不存在");

        // 4. 黑名单通过后，再读取目标用户隐私配置，决定资料是否允许当前用户查看。
        String privacySettingStr = user.getPrivacySetting();
        PrivacySettingDTO privacySetting = privacySettingStr != null && !privacySettingStr.isEmpty()
                ? JSON.parseObject(privacySettingStr, PrivacySettingDTO.class)
                : new PrivacySettingDTO();

        // 5. “仅团队成员可见”时，按双方当前共同团队做判定，没有共同团队则拒绝查看。
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

        // 6. 所有校验通过后，返回对外可展示的脱敏资料视图。
        return Result.ok(buildSearchUserResponse(currentUserId, user));
    }

    /**
     * 判断单向黑名单关系。
     * 优先读 Redis Set，未命中时加短锁回源数据库并回填缓存。
     *
     * @param userId       黑名单拥有者用户 ID
     * @param targetUserId 待判断的目标用户 ID
     * @return true 表示目标用户在黑名单中，false 表示不在
     */
    private boolean isBlacklisted(Long userId, Long targetUserId) {
        String cacheKey = BLACKLIST_CACHE_KEY + userId;

        // 1. 优先查 Redis Set，命中后直接判断目标用户是否在集合内。
        Boolean hasKey = stringRedisTemplate.hasKey(cacheKey);
        if (Boolean.TRUE.equals(hasKey)) {
            return Boolean.TRUE.equals(
                    stringRedisTemplate.opsForSet().isMember(cacheKey, String.valueOf(targetUserId)));
        }

        // 2. 缓存未命中时，通过短锁保护数据库回源，避免热点黑名单被并发击穿。
        String lockKey = "lock:blacklist:" + userId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
        try {
            if (!Boolean.TRUE.equals(locked)) {
                // 3. 没拿到锁说明已有线程在回源，这里短暂等待后重试缓存，尽量避免重复打库。
                Thread.sleep(50);
                Boolean hasKeyRetry = stringRedisTemplate.hasKey(cacheKey);
                if (Boolean.TRUE.equals(hasKeyRetry)) {
                    return Boolean.TRUE.equals(
                            stringRedisTemplate.opsForSet().isMember(cacheKey, String.valueOf(targetUserId)));
                }
                return false;
            }

            // 4. 当前线程拿到锁后，从数据库查询完整黑名单并整体回填缓存。
            LambdaQueryWrapper<UserBlacklist> listWrapper = new LambdaQueryWrapper<>();
            listWrapper.eq(UserBlacklist::getUserId, userId)
                    .eq(UserBlacklist::getIsDelete, 0)
                    .select(UserBlacklist::getBlackUserId);
            List<UserBlacklist> blacklistRecords = userBlacklistMapper.selectList(listWrapper);
            long ttl = BLACKLIST_CACHE_TTL_HOURS + ThreadLocalRandom.current().nextLong(-2, 3);
            if (!blacklistRecords.isEmpty()) {
                String[] ids = blacklistRecords.stream()
                        .map(b -> String.valueOf(b.getBlackUserId()))
                        .toArray(String[]::new);
                stringRedisTemplate.opsForSet().add(cacheKey, ids);
                stringRedisTemplate.expire(cacheKey, ttl, TimeUnit.HOURS);
                return blacklistRecords.stream().anyMatch(b -> b.getBlackUserId().equals(targetUserId));
            } else {
                // 5. 空黑名单也写占位值并设置短 TTL，减少缓存穿透。
                stringRedisTemplate.opsForSet().add(cacheKey, "0");
                stringRedisTemplate.expire(cacheKey, 5, TimeUnit.MINUTES);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            // 6. 无论命中与否都释放短锁，避免后续请求被异常阻塞。
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
        // 1. 没有隐私配置时默认不允许邮箱搜索，避免邮箱被意外暴露。
        String privacySettingStr = targetUser.getPrivacySetting();
        if (privacySettingStr == null || privacySettingStr.isEmpty()) return false;

        try {
            // 2. 只有显式配置 searchByEmail=1 才放行，其余情况都按关闭处理。
            PrivacySettingDTO privacySetting = JSON.parseObject(privacySettingStr, PrivacySettingDTO.class);
            return privacySetting != null && privacySetting.getSearchByEmail() != null && privacySetting.getSearchByEmail() == 1;
        } catch (Exception e) {
            // 3. 配置解析异常时走最保守分支，防止脏数据扩大可见范围。
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
        // 1. 没有隐私配置或解析失败时，兼容旧数据，默认允许公开资料展示。
        String privacySettingStr = targetUser.getPrivacySetting();
        if (privacySettingStr == null || privacySettingStr.isEmpty()) return true;

        PrivacySettingDTO privacySetting;
        try {
            privacySetting = JSON.parseObject(privacySettingStr, PrivacySettingDTO.class);
        } catch (Exception e) {
            return true;
        }
        if (privacySetting == null || privacySetting.getViewInfo() == null) return true;

        // 2. viewInfo=1 代表对所有人公开，直接放行。
        if (privacySetting.getViewInfo() == 1) return true;

        // 3. “仅团队成员可见”按当前仍未退出的共同团队判断，而不是历史入队记录。
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

        // 4. 其它未识别配置按不可见处理，避免错误配置扩大公开范围。
        return false;
    }

    /**
     * 回写用户资料缓存。
     * 缓存的是搜索/资料页共用的基础信息，不包含仅本人可见的完整字段集合。
     *
     * @param user 需要写入缓存的用户实体
     */
    private void cacheUserInfo(User user) {
        // 1. 只缓存公开资料和隐私配置，避免把邮箱等敏感字段带入通用缓存。
        String cacheKey = USER_INFO_CACHE_KEY + user.getId();
        Map<String, String> map = new HashMap<>();
        map.put("id", String.valueOf(user.getId()));
        map.put("userAccount", user.getUserAccount() != null ? user.getUserAccount() : "");
        map.put("userNickname", user.getUserNickname() != null ? user.getUserNickname() : "");
        map.put("userAvatar", user.getUserAvatar() != null ? user.getUserAvatar() : "");
        map.put("userIntro", user.getUserIntro() != null ? user.getUserIntro() : "");
        map.put("userTags", user.getUserTags() != null ? user.getUserTags() : "");
        map.put("privacySetting", user.getPrivacySetting() != null ? user.getPrivacySetting() : "");

        // 2. 使用 Hash 回填缓存，便于后续按字段读取并兼容空值。
        stringRedisTemplate.opsForHash().putAll(cacheKey, map);

        // 3. TTL 加少量随机值，避免大量用户资料同时过期。
        long ttl = USER_INFO_CACHE_TTL_MINUTES + ThreadLocalRandom.current().nextLong(-1, 2);
        stringRedisTemplate.expire(cacheKey, Math.max(ttl, 1), TimeUnit.MINUTES);
    }

    /**
     * 构建当前用户自己的完整资料视图。
     *
     * @param user 当前登录用户实体
     * @return 当前用户完整资料响应
     */
    private Map<String, Object> buildUserResponse(User user) {
        Map<String, Object> response = new HashMap<>();

        // 1. 先放入仅本人可见的完整基础字段。
        response.put("id", user.getId());
        response.put("userAccount", user.getUserAccount());
        response.put("userNickname", user.getUserNickname());
        response.put("userAvatar", user.getUserAvatar());
        response.put("userIntro", user.getUserIntro());
        response.put("userTags", user.getUserTags());
        response.put("userEmail", user.getUserEmail());

        // 2. 隐私配置转成 JSON 对象返回，方便资料设置页直接回显。
        if (user.getPrivacySetting() != null && !user.getPrivacySetting().isEmpty()) {
            response.put("privacySetting", JSON.parseObject(user.getPrivacySetting()));
        }

        // 3. 最后补齐资料的创建和更新时间。
        response.put("createTime", user.getCreateTime());
        response.put("updateTime", user.getUpdateTime());
        return response;
    }

    /**
     * 构建对外展示的脱敏用户视图。
     * 附带好友关系与当前私聊可发送状态，供搜索页、资料页、会话页复用。
     *
     * @param currentUserId 当前登录用户 ID
     * @param user          目标用户实体
     * @return 对外展示的脱敏资料
     */
    private Map<String, Object> buildSearchUserResponse(Long currentUserId, User user) {
        Map<String, Object> response = new HashMap<>();

        // 1. 先拼公开资料字段，确保搜索页和资料卡不会暴露敏感信息。
        response.put("id", user.getId());
        response.put("userAccount", user.getUserAccount());
        response.put("userNickname", user.getUserNickname());
        response.put("userAvatar", user.getUserAvatar());
        response.put("userIntro", user.getUserIntro());
        response.put("userTags", user.getUserTags());

        // 2. 先查当前方向的好友关系，给前端一个基础关系状态。
        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFriend::getUserId, currentUserId)
                .eq(UserFriend::getFriendId, user.getId());
        UserFriend friendRelation = userFriendMapper.selectOne(queryWrapper);

        if (friendRelation != null) {
            response.put("isFriend", friendRelation.getFriendStatus() == 1);
            response.put("friendStatus", friendRelation.getFriendStatus());
        } else {
            response.put("isFriend", false);
            response.put("friendStatus", -1);
        }

        // 3. 再补查反向关系，兼容历史上遗留的单边好友记录不一致问题。
        UserFriend reverseRelation = findFriendRelation(user.getId(), currentUserId);
        boolean hasActiveFriendRelation = isActiveFriendRelation(friendRelation) || isActiveFriendRelation(reverseRelation);
        if (hasActiveFriendRelation) {
            response.put("isFriend", true);
            response.put("friendStatus", 1);
        }

        // 4. 最后统一计算当前是否允许发私聊，把状态和原因一并返回给前端。
        Map<String, Object> sendAbility = buildPrivateMessageAbility(currentUserId, user, hasActiveFriendRelation);
        response.putAll(sendAbility);

        return response;
    }

    /**
     * 查询一条最新的单向好友关系记录。
     * 历史数据中可能存在多条关系，因此这里固定取最新一条供展示和修正逻辑复用。
     *
     * @param userId   发起侧用户 ID
     * @param friendId 对端用户 ID
     * @return 最新关系记录；参数无效或记录不存在时返回 null
     */
    private UserFriend findFriendRelation(Long userId, Long friendId) {
        // 1. 参数无效时直接返回，避免发起无意义查询。
        if (userId == null || friendId == null) {
            return null;
        }

        // 2. 按创建时间倒序只取一条最新记录，兼容历史多记录场景。
        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .orderByDesc(UserFriend::getCreateTime)
                .last("limit 1");
        return userFriendMapper.selectOne(queryWrapper);
    }

    /**
     * 判断一条好友关系记录是否处于有效好友态。
     *
     * @param relation 好友关系记录
     * @return true 表示 friendStatus=1，false 表示不是有效好友
     */
    private boolean isActiveFriendRelation(UserFriend relation) {
        return relation != null && Integer.valueOf(1).equals(relation.getFriendStatus());
    }

    /**
     * 构建当前用户对目标用户的私聊能力说明。
     * 统一收口黑名单、隐私设置、团队关系和好友关系，给出“能否发消息”和原因。
     *
     * @param currentUserId           当前登录用户 ID
     * @param targetUser              目标用户实体
     * @param hasActiveFriendRelation 双方是否存在有效好友关系
     * @return 包含私聊能力与原因说明的结果 Map
     */
    private Map<String, Object> buildPrivateMessageAbility(Long currentUserId, User targetUser, boolean hasActiveFriendRelation) {
        Map<String, Object> result = new HashMap<>();
        result.put("canSendPrivateMessage", true);
        result.put("sendPrivateMessageReason", "");

        // 1. 先处理空参等兜底场景，保证返回结构稳定。
        if (currentUserId == null || targetUser == null || targetUser.getId() == null) {
            result.put("canSendPrivateMessage", false);
            result.put("sendPrivateMessageReason", "当前无法发送消息");
            return result;
        }

        Long targetUserId = targetUser.getId();

        // 2. 黑名单优先级最高，一旦命中就直接拦截，后续无需再看隐私和好友关系。
        if (isBlacklisted(currentUserId, targetUserId)) {
            result.put("canSendPrivateMessage", false);
            result.put("sendPrivateMessageReason", "当前好友已被拉黑，无法发送消息");
            return result;
        }
        if (isBlacklisted(targetUserId, currentUserId)) {
            result.put("canSendPrivateMessage", false);
            result.put("sendPrivateMessageReason", "对方已将你加入黑名单，无法发送消息");
            return result;
        }

        String privacySettingStr = targetUser.getPrivacySetting();
        PrivacySettingDTO privacySetting = privacySettingStr != null && !privacySettingStr.isEmpty()
                ? JSON.parseObject(privacySettingStr, PrivacySettingDTO.class)
                : new PrivacySettingDTO();

        // 3. 最后根据目标用户的私聊权限配置判断是否允许发消息。
        // 私聊能力判断统一在这里收口，前端展示状态与真正发送校验都复用这套规则，
        // 避免出现“输入框可发，但发送时才失败”的体验割裂。
        if (privacySetting.getSendMsg() != null) {
            if (privacySetting.getSendMsg() == 2) {
                // 4. “仅团队成员可私聊”时，按双方当前共同团队做判定，而不是看历史入队记录。
                LambdaQueryWrapper<TeamMember> qw1 = new LambdaQueryWrapper<>();
                qw1.eq(TeamMember::getUserId, currentUserId)
                        .eq(TeamMember::getIsQuit, 0);
                List<Long> myTeamIds = teamMemberMapper.selectList(qw1).stream()
                        .map(TeamMember::getTeamId)
                        .collect(Collectors.toList());
                boolean inSameTeam = false;
                if (!myTeamIds.isEmpty()) {
                    LambdaQueryWrapper<TeamMember> qw2 = new LambdaQueryWrapper<>();
                    qw2.eq(TeamMember::getUserId, targetUserId)
                            .eq(TeamMember::getIsQuit, 0)
                            .in(TeamMember::getTeamId, myTeamIds);
                    inSameTeam = teamMemberMapper.selectCount(qw2) > 0;
                }
                if (!inSameTeam) {
                    result.put("canSendPrivateMessage", false);
                    result.put("sendPrivateMessageReason", "对方仅接收团队成员消息");
                    return result;
                }
            } else if (privacySetting.getSendMsg() == 3) {
                // 5. “仅好友可私聊”时，必须命中当前仍有效的好友关系。
                if (!hasActiveFriendRelation) {
                    result.put("canSendPrivateMessage", false);
                    result.put("sendPrivateMessageReason", "需先成为好友才能发消息");
                    return result;
                }
            }
        }

        // 6. 所有限制都未命中时，保留默认可发送状态返回给调用方。
        return result;
    }

    /**
     * 获取好友申请列表
     * 返回当前用户收到的待处理好友申请。
     */
    @Override
    public Result getFriendRequests(int page, int pageSize) {
        // 1. 好友申请列表只展示当前用户收到的待处理申请。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        Page<UserFriend> pageObj = new Page<>(page, pageSize);

        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFriend::getFriendId, userId)
                .eq(UserFriend::getFriendStatus, 0)
                .orderByDesc(UserFriend::getCreateTime);

        Page<UserFriend> result = userFriendMapper.selectPage(pageObj, queryWrapper);

        // 2. 结果集补齐申请人资料，方便前端直接渲染申请卡片。
        List<Map<String, Object>> requestList = result.getRecords().stream().map(uf -> {
            User applicant = userMapper.selectById(uf.getUserId());
            Map<String, Object> requestInfo = new HashMap<>();
            requestInfo.put("requestId", uf.getId());
            requestInfo.put("applicantId", uf.getUserId());
            requestInfo.put("userNickname", applicant.getUserNickname());
            requestInfo.put("userAvatar", applicant.getUserAvatar());
            requestInfo.put("userIntro", applicant.getUserIntro());
            requestInfo.put("userTags", applicant.getUserTags());
            requestInfo.put("applyMsg", uf.getFriendRemark());
            requestInfo.put("createTime", uf.getCreateTime());
            return requestInfo;
        }).collect(Collectors.toList());

        return Result.ok(requestList, result.getTotal());
    }

    /**
     * 返回当前用户未读通知总数。
     */
    @Override
    public Result getUnreadNoticeCount() {
        // 直接统计当前用户未读通知总数，给前端角标使用。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        LambdaQueryWrapper<SystemNotice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SystemNotice::getUserId, userId)
                .eq(SystemNotice::getIsRead, 0)
                .eq(SystemNotice::getIsDelete, 0);
        long unreadCount = systemNoticeMapper.selectCount(queryWrapper);

        return Result.ok(unreadCount);
    }

    /**
     * 分页查询当前用户通知列表。
     */
    @Override
    public Result getNoticeList(int page, int pageSize, Integer isRead) {
        // 1. 先按用户和已读状态分页查询通知记录。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        Page<SystemNotice> pageObj = new Page<>(page, pageSize);

        LambdaQueryWrapper<SystemNotice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SystemNotice::getUserId, userId)
                .eq(SystemNotice::getIsDelete, 0);

        if (isRead != null) {
            queryWrapper.eq(SystemNotice::getIsRead, isRead);
        }

        queryWrapper.orderByDesc(SystemNotice::getCreateTime);

        Page<SystemNotice> result = systemNoticeMapper.selectPage(pageObj, queryWrapper);

        // 2. 再映射成前端需要的通知展示结构。
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
     * 批量标记通知为已读。
     */
    @Override
    @Transactional
    public Result markNoticeAsRead(List<Long> noticeIds) {
        // 只更新当前用户自己的通知，避免误改其他用户状态。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        if (noticeIds == null || noticeIds.isEmpty()) {
            return Result.fail("通知ID列表不能为空");
        }

        LambdaUpdateWrapper<SystemNotice> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SystemNotice::getUserId, userId)
                .in(SystemNotice::getId, noticeIds)
                .set(SystemNotice::getIsRead, 1)
                .set(SystemNotice::getReadTime, LocalDateTime.now());

        systemNoticeMapper.update(null, updateWrapper);

        return Result.ok("通知已标记为已读");
    }

    /**
     * 批量软删除通知。
     */
    @Override
    @Transactional
    public Result deleteNotice(List<Long> noticeIds) {
        // 删除采用软删除，方便后续审计和问题排查。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        if (noticeIds == null || noticeIds.isEmpty()) {
            return Result.fail("通知ID列表不能为空");
        }

        LambdaUpdateWrapper<SystemNotice> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SystemNotice::getUserId, userId)
                .in(SystemNotice::getId, noticeIds)
                .set(SystemNotice::getIsDelete, 1);

        systemNoticeMapper.update(null, updateWrapper);

        return Result.ok("通知已删除");
    }
}
