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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class SearchRecommendationService {

    @Resource
    private SearchRecommendSupportService supportService;

    @Transactional
    public Result getRecommendUsers(int limit) {
        Long currentUserId = supportService.requireLogin();
        limit = supportService.normalizeLimit(limit);

        String cacheKey = supportService.buildRecommendUsersCacheKey(currentUserId, limit);
        String cached = supportService.stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            return Result.ok(JSON.parseArray(cached, RecommendUserVO.class));
        }
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

        List<RecommendUserVO> result = new ArrayList<>();
        for (User c : candidates) {
            if (blacklistedIds.contains(c.getId())) continue;

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

        result.sort((a, b) -> Integer.compare(b.getRecommendScore(), a.getRecommendScore()));
        if (result.size() > limit) result = result.subList(0, limit);
        int randomHours = ThreadLocalRandom.current().nextInt(0, 6);
        supportService.stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(result), 24L + randomHours, TimeUnit.HOURS);
        return Result.ok(result);
    }

    @Transactional
    public Result getRecommendTeams(int limit) {
        Long currentUserId = supportService.requireLogin();
        limit = supportService.normalizeLimit(limit);

        String cacheKey = supportService.buildRecommendTeamsCacheKey(currentUserId, limit);
        String cached = supportService.stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            return Result.ok(JSON.parseArray(cached, RecommendTeamVO.class));
        }
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

            int score = Math.min(100, tagScore + overlapScore + hotScore + newScore);
            if (score <= 0) continue;

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

        result.sort((a, b) -> Integer.compare(b.getRecommendScore(), a.getRecommendScore()));
        if (result.size() > limit) result = result.subList(0, limit);
        int randomHours = ThreadLocalRandom.current().nextInt(0, 6);
        supportService.stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(result), 24L + randomHours, TimeUnit.HOURS);
        return Result.ok(result);
    }

    public Result recordRecommendClick(Long recommendId, Integer recommendType) {
        Long currentUserId = supportService.requireLogin();
        if (recommendId == null || recommendType == null) throw new BusinessException("参数不能为空");
        recommendType = supportService.validateRecommendType(recommendType);

        if (recommendType == SearchRecommendSupportService.RECOMMEND_TYPE_USER) {
            LambdaUpdateWrapper<UserRecommendation> uw = new LambdaUpdateWrapper<>();
            uw.eq(UserRecommendation::getId, recommendId)
                    .eq(UserRecommendation::getRecommendToUserId, currentUserId)
                    .eq(UserRecommendation::getIsDelete, 0)
                    .set(UserRecommendation::getIsClicked, 1);
            supportService.userRecommendationMapper.update(null, uw);
            return Result.ok("已记录点击");
        }

        if (recommendType == SearchRecommendSupportService.RECOMMEND_TYPE_TEAM) {
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

    @Transactional
    public void refreshRecommendForUser(Long userId) {
        if (userId == null) return;
        supportService.deleteKeysByPrefix(supportService.buildRecommendUsersCachePrefix(userId));
        supportService.deleteKeysByPrefix(supportService.buildRecommendTeamsCachePrefix(userId));
        User user = supportService.userMapper.selectById(userId);
        if (user == null) return;

        UserHolder.saveUser(supportService.buildUserFormat(user));
        try {
            getRecommendUsers(SearchRecommendSupportService.DEFAULT_RECOMMEND_REFRESH_LIMIT);
            getRecommendTeams(SearchRecommendSupportService.DEFAULT_RECOMMEND_REFRESH_LIMIT);
        } finally {
            UserHolder.removeUser();
        }
    }

    @Transactional
    public void refreshRecommendForAllUsers() {
        List<User> users = supportService.userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getIsDelete, 0));
        for (User user : users) {
            refreshRecommendForUser(user.getId());
        }
    }
}
