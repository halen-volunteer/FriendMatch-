package com.zero.usercenter.Service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zero.usercenter.DTO.RecommendTeamVO;
import com.zero.usercenter.DTO.RecommendUserVO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Model.*;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 推荐结果查询与刷新服务。
 * 负责用户推荐、团队推荐、推荐点击上报，以及单用户 / 全量推荐重算。
 */
@Service
public class SearchRecommendationService {

    @Resource
    private SearchRecommendSupportService supportService;

    /**
     * 获取推荐用户列表。
     *
     * @param limit 返回条数
     * @return 推荐用户结果
     */
    @Transactional
    public Result getRecommendUsers(int limit) {
        // 1. 推荐页优先走缓存，只有未命中时才重新计算画像和得分。
        Long currentUserId = supportService.requireLogin();
        limit = supportService.normalizeLimit(limit);

        String cacheKey = supportService.buildRecommendUsersCacheKey(currentUserId, limit);
        String cached = supportService.stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            List<RecommendUserVO> cachedList = JSON.parseArray(cached, RecommendUserVO.class);
            List<RecommendUserVO> filteredCached = filterInvalidRecommendUsers(currentUserId, cachedList);
            if (filteredCached.size() == cachedList.size()) {
                return Result.ok(filteredCached);
            }
            supportService.stringRedisTemplate.delete(cacheKey);
        }

        // 2. 当前用户的标签、好友、私聊对象和共同团队，都会成为推荐打分输入。
        // 推荐不是简单“查库取前 N”，而是一次重新打分：
        // 标签、共同好友、私聊关系、共同团队分别给权重，最后再把结果落表和缓存。
        User me = supportService.userMapper.selectById(currentUserId);
        Set<String> myTags = supportService.splitTags(me == null ? "" : me.getUserTags());
        Set<Long> myTeamIds = supportService.getUserTeamIds(currentUserId);
        Set<Long> myFriendIds = supportService.getFriendIds(currentUserId);
        Set<Long> myChatPartners = supportService.getPrivateChatPartners(currentUserId);

        LambdaUpdateWrapper<UserRecommendation> clear = new LambdaUpdateWrapper<>();
        clear.eq(UserRecommendation::getRecommendToUserId, currentUserId)
                .eq(UserRecommendation::getIsDelete, 0)
                .set(UserRecommendation::getIsDelete, 1);
        supportService.userRecommendationMapper.update(null, clear);

        List<User> candidates = supportService.userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getIsDelete, 0)
                .ne(User::getId, currentUserId));

        List<Long> candidateUserIds = candidates.stream().map(User::getId).toList();
        Set<Long> blacklistedIds = supportService.getBlacklistedUserIds(currentUserId, candidateUserIds);
        java.util.Map<Long, Set<Long>> candidateTeamMap = supportService.getUserTeamIdsMap(candidateUserIds);
        java.util.Map<Long, Set<Long>> candidateFriendMap = supportService.getFriendIdsMap(candidateUserIds);
        java.util.Map<Long, Set<Long>> candidateChatMap = supportService.getPrivateChatPartnersMap(candidateUserIds);

        // 3. 遍历候选用户逐个打分，跳过自己和存在黑名单关系的用户。
        List<RecommendUserVO> result = new ArrayList<>();
        for (User c : candidates) {
            if (blacklistedIds.contains(c.getId())) continue;
            if (myFriendIds.contains(c.getId())) continue;
            if (!supportService.canViewUserInfo(currentUserId, c)) continue;

            Set<String> cTags = supportService.splitTags(c.getUserTags());
            Set<Long> cTeamIds = candidateTeamMap.getOrDefault(c.getId(), java.util.Collections.emptySet());
            Set<Long> cFriendIds = candidateFriendMap.getOrDefault(c.getId(), java.util.Collections.emptySet());
            Set<Long> cChatPartners = candidateChatMap.getOrDefault(c.getId(), java.util.Collections.emptySet());

            int tagScore = supportService.calcTagScore(myTags, cTags, 40);
            int friendScore = supportService.calcMinBaseScore(myFriendIds, cFriendIds, 30);
            int chatScore = supportService.calcMinBaseScore(myChatPartners, cChatPartners, 20);
            int teamScore = supportService.calcMinBaseScore(myTeamIds, cTeamIds, 10);
            int score = Math.min(100, tagScore + friendScore + chatScore + teamScore);
            if (score <= 0) continue;

            // 4. 推荐理由取最高得分维度，用来生成前端可读的解释文案。
            // recommendReason 不是额外规则表，而是“哪一项得分最高”对应的人类可读原因。
            int reason = 1;
            int max = tagScore;
            if (friendScore > max) { max = friendScore; reason = 2; }
            if (chatScore > max) { max = chatScore; reason = 3; }
            if (teamScore > max) { reason = 4; }

            UserRecommendation rec = new UserRecommendation();
            rec.setUserId(c.getId());
            rec.setRecommendToUserId(currentUserId);
            rec.setRecommendReason(reason);
            rec.setRecommendScore(score);
            rec.setIsClicked(0);
            rec.setIsAdded(0);
            rec.setIsDelete(0);
            supportService.userRecommendationMapper.insert(rec);

            int mutualFriends = supportService.intersectionCount(myFriendIds, cFriendIds);
            int mutualTeams = supportService.intersectionCount(myTeamIds, cTeamIds);

            RecommendUserVO vo = new RecommendUserVO();
            vo.setRecommendId(rec.getId());
            vo.setUserId(c.getId());
            vo.setUserNickname(c.getUserNickname());
            vo.setUserAvatar(c.getUserAvatar());
            vo.setUserIntro(c.getUserIntro());
            vo.setUserTags(c.getUserTags());
            vo.setMutualFriends(mutualFriends);
            vo.setMutualTeams(mutualTeams);
            vo.setRecommendScore(score);
            vo.setRecommendReason(supportService.reasonTextForUser(reason));
            result.add(vo);
        }

        // 5. 排序后写缓存，减少用户频繁打开推荐页时的重复计算。
        result.sort((a, b) -> Integer.compare(b.getRecommendScore(), a.getRecommendScore()));
        if (result.size() > limit) result = result.subList(0, limit);
        int randomHours = ThreadLocalRandom.current().nextInt(0, 6);
        supportService.stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(result), 24L + randomHours, TimeUnit.HOURS);
        return Result.ok(result);
    }

    /**
     * 过滤推荐缓存中已经失效的用户。
     * 主要兜底好友关系变化、黑名单变化和资料可见性变化后的旧缓存残留。
     *
     * @param currentUserId 当前登录用户 ID
     * @param cachedList    缓存中的推荐列表
     * @return 过滤后的推荐列表
     */
    private List<RecommendUserVO> filterInvalidRecommendUsers(Long currentUserId, List<RecommendUserVO> cachedList) {
        if (cachedList == null || cachedList.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        Set<Long> candidateIds = cachedList.stream()
                .map(RecommendUserVO::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (candidateIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        Set<Long> myFriendIds = supportService.getFriendIds(currentUserId);
        Set<Long> blacklistedIds = supportService.getBlacklistedUserIds(currentUserId, candidateIds);
        Map<Long, User> userMap = new HashMap<>();
        for (User user : supportService.userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getId, candidateIds)
                .eq(User::getIsDelete, 0))) {
            userMap.put(user.getId(), user);
        }

        List<RecommendUserVO> filtered = new ArrayList<>();
        for (RecommendUserVO item : cachedList) {
            Long userId = item.getUserId();
            if (userId == null || myFriendIds.contains(userId) || blacklistedIds.contains(userId)) {
                continue;
            }
            User user = userMap.get(userId);
            if (user == null || !supportService.canViewUserInfo(currentUserId, user)) {
                continue;
            }
            filtered.add(item);
        }
        return filtered;
    }

    /**
     * 获取推荐团队列表。
     *
     * @param limit 返回条数
     * @return 推荐团队结果
     */
    @Transactional
    public Result getRecommendTeams(int limit) {
        // 1. 团队推荐同样先走缓存，再决定是否重算。
        Long currentUserId = supportService.requireLogin();
        limit = supportService.normalizeLimit(limit);

        String cacheKey = supportService.buildRecommendTeamsCacheKey(currentUserId, limit);
        String cached = supportService.stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            return Result.ok(JSON.parseArray(cached, RecommendTeamVO.class));
        }
        // 2. 团队推荐更关注兴趣标签、团队成员重叠和团队活跃度。
        Set<Long> joinedTeamIds = supportService.getUserTeamIds(currentUserId);
        Set<String> userTagSet = supportService.splitTags(Optional.ofNullable(supportService.userMapper.selectById(currentUserId)).map(User::getUserTags).orElse(""));
        Set<Long> myRelatedMembers = supportService.collectTeamMembers(joinedTeamIds);

        LambdaUpdateWrapper<TeamRecommendation> clear = new LambdaUpdateWrapper<>();
        clear.eq(TeamRecommendation::getRecommendToUserId, currentUserId)
                .eq(TeamRecommendation::getIsDelete, 0)
                .set(TeamRecommendation::getIsDelete, 1);
        supportService.teamRecommendationMapper.update(null, clear);

        List<Team> candidates = supportService.teamMapper.selectList(new LambdaQueryWrapper<Team>()
                .eq(Team::getIsDelete, 0));

        List<Long> candidateTeamIds = candidates.stream().map(Team::getId).toList();
        java.util.Map<Long, Set<Long>> teamMemberMap = supportService.getTeamMemberIdsMap(candidateTeamIds);
        java.util.Map<Long, Integer> memberCountMap = supportService.getTeamMemberCountMap(candidateTeamIds);
        java.util.Map<Long, Integer> teamMessageCountMap = supportService.getTeamMessageCountMap(candidateTeamIds);

        // 3. 遍历候选团队逐个打分，已加入团队不会重复推荐。
        List<RecommendTeamVO> result = new ArrayList<>();
        for (Team t : candidates) {
            if (joinedTeamIds.contains(t.getId())) continue;

            int memberCount = memberCountMap.getOrDefault(t.getId(), 0);
            Set<String> teamTags = supportService.splitTags(t.getTeamTags());
            Set<Long> teamMembers = teamMemberMap.getOrDefault(t.getId(), java.util.Collections.emptySet());

            int tagScore = supportService.calcTagScore(userTagSet, teamTags, 40);
            int overlapScore = supportService.calcMinBaseScore(myRelatedMembers, teamMembers, 30);
            int hotScore = supportService.calcTeamHotScore(memberCount, teamMessageCountMap.getOrDefault(t.getId(), 0), t, 20);
            int newScore = supportService.calcTeamNewScore(t, 10);

            // 团队推荐更强调“内容相关 + 人脉重叠 + 团队活跃度”，因此打分维度和用户推荐不同。
            int score = Math.min(100, tagScore + overlapScore + hotScore + newScore);
            if (score <= 0) continue;

            // 4. 推荐理由同样取最高得分维度，便于前端解释“为什么推荐这个团队”。
            int reason = 1;
            int max = tagScore;
            if (overlapScore > max) { max = overlapScore; reason = 2; }
            if (hotScore > max) { max = hotScore; reason = 3; }
            if (newScore > max) { reason = 4; }

            TeamRecommendation rec = new TeamRecommendation();
            rec.setTeamId(t.getId());
            rec.setRecommendToUserId(currentUserId);
            rec.setRecommendReason(reason);
            rec.setRecommendScore(score);
            rec.setIsClicked(0);
            rec.setIsJoined(0);
            rec.setIsDelete(0);
            supportService.teamRecommendationMapper.insert(rec);

            RecommendTeamVO vo = new RecommendTeamVO();
            vo.setRecommendId(rec.getId());
            vo.setTeamId(t.getId());
            vo.setTeamName(t.getTeamName());
            vo.setTeamAvatar(t.getTeamAvatar());
            vo.setTeamIntro(t.getTeamIntro());
            vo.setTeamTags(t.getTeamTags());
            vo.setMemberCount(memberCount);
            vo.setCommonMembers(supportService.intersectionCount(myRelatedMembers, teamMembers));
            vo.setRecommendScore(score);
            vo.setRecommendReason(supportService.reasonTextForTeam(reason));
            result.add(vo);
        }

        // 5. 最终按分数排序并写缓存。
        result.sort((a, b) -> Integer.compare(b.getRecommendScore(), a.getRecommendScore()));
        if (result.size() > limit) result = result.subList(0, limit);
        int randomHours = ThreadLocalRandom.current().nextInt(0, 6);
        supportService.stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(result), 24L + randomHours, TimeUnit.HOURS);
        return Result.ok(result);
    }

    /**
     * 记录推荐点击行为。
     *
     * @param recommendId   推荐记录 ID
     * @param recommendType 推荐类型
     * @return 处理结果
     */
    public Result recordRecommendClick(Long recommendId, Integer recommendType) {
        // 1. 点击上报只回写推荐表里的点击状态，后续可用于统计推荐效果。
        Long currentUserId = supportService.requireLogin();
        if (recommendId == null || recommendType == null) throw new BusinessException("参数不能为空");
        recommendType = supportService.validateRecommendType(recommendType);

        if (recommendType == SearchRecommendSupportService.RECOMMEND_TYPE_USER) {
            // 2. 用户推荐点击只更新当前用户自己的推荐记录。
            LambdaUpdateWrapper<UserRecommendation> uw = new LambdaUpdateWrapper<>();
            uw.eq(UserRecommendation::getId, recommendId)
                    .eq(UserRecommendation::getRecommendToUserId, currentUserId)
                    .eq(UserRecommendation::getIsDelete, 0)
                    .set(UserRecommendation::getIsClicked, 1);
            supportService.userRecommendationMapper.update(null, uw);
            return Result.ok("已记录点击");
        }

        if (recommendType == SearchRecommendSupportService.RECOMMEND_TYPE_TEAM) {
            // 3. 团队推荐点击同样只更新当前用户视角下的推荐记录。
            LambdaUpdateWrapper<TeamRecommendation> uw = new LambdaUpdateWrapper<>();
            uw.eq(TeamRecommendation::getId, recommendId)
                    .eq(TeamRecommendation::getRecommendToUserId, currentUserId)
                    .eq(TeamRecommendation::getIsDelete, 0)
                    .set(TeamRecommendation::getIsClicked, 1);
            supportService.teamRecommendationMapper.update(null, uw);
            return Result.ok("已记录点击");
        }

        throw new BusinessException("recommendType 参数无效");
    }

    /**
     * 刷新单个用户的推荐结果。
     *
     * @param userId 用户 ID
     */
    @Transactional
    public void refreshRecommendForUser(Long userId) {
        // 1. 刷新单个用户推荐时，先清缓存，再以该用户身份重跑推荐逻辑。
        if (userId == null) return;
        supportService.deleteKeysByPrefix(supportService.buildRecommendUsersCachePrefix(userId));
        supportService.deleteKeysByPrefix(supportService.buildRecommendTeamsCachePrefix(userId));
        User user = supportService.userMapper.selectById(userId);
        if (user == null) return;

        // 2. 推荐逻辑依赖 UserHolder 登录态，因此刷新时临时把目标用户放进上下文。
        UserHolder.saveUser(supportService.buildUserFormat(user));
        try {
            // 3. 按默认刷新条数分别重算用户推荐和团队推荐。
            getRecommendUsers(SearchRecommendSupportService.DEFAULT_RECOMMEND_REFRESH_LIMIT);
            getRecommendTeams(SearchRecommendSupportService.DEFAULT_RECOMMEND_REFRESH_LIMIT);
        } finally {
            // 4. 无论成功失败都清理线程上下文，避免污染后续请求。
            UserHolder.removeUser();
        }
    }

    /**
     * 刷新所有正常用户的推荐结果。
     */
    @Transactional
    public void refreshRecommendForAllUsers() {
        // 1. 全量刷新就是遍历所有正常用户，逐个执行单用户刷新。
        List<User> users = supportService.userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getIsDelete, 0));
        for (User user : users) {
            refreshRecommendForUser(user.getId());
        }
    }
}
