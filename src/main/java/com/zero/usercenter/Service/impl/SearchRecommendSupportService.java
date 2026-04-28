package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zero.usercenter.DTO.TeamSearchVO;
import com.zero.usercenter.DTO.UserFormat;
import com.zero.usercenter.DTO.UserSearchVO;
import com.zero.usercenter.Mapper.*;
import com.zero.usercenter.Model.*;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchRecommendSupportService {

    public static final int SEARCH_USER = 1;
    public static final int SEARCH_TEAM = 2;
    public static final int RECOMMEND_TYPE_USER = 1;
    public static final int RECOMMEND_TYPE_TEAM = 2;
    public static final int DEFAULT_RECOMMEND_REFRESH_LIMIT = 10;

    public static final String HOT_KEYWORDS_CACHE_KEY = "search:hot:";
    public static final String RECOMMEND_USERS_CACHE_KEY = "recommend:users:";
    public static final String RECOMMEND_TEAMS_CACHE_KEY = "recommend:teams:";

    public String buildHotKeywordCacheKey(Integer searchType, int limit) {
        return HOT_KEYWORDS_CACHE_KEY + searchType + ":" + limit;
    }

    public String buildHotKeywordCachePrefix(Integer searchType) {
        return HOT_KEYWORDS_CACHE_KEY + searchType + ":";
    }

    public String buildRecommendUsersCacheKey(Long userId, int limit) {
        return RECOMMEND_USERS_CACHE_KEY + userId + ":" + limit;
    }

    public String buildRecommendUsersCachePrefix(Long userId) {
        return RECOMMEND_USERS_CACHE_KEY + userId + ":";
    }

    public String buildRecommendTeamsCacheKey(Long userId, int limit) {
        return RECOMMEND_TEAMS_CACHE_KEY + userId + ":" + limit;
    }

    public String buildRecommendTeamsCachePrefix(Long userId) {
        return RECOMMEND_TEAMS_CACHE_KEY + userId + ":";
    }

    public void deleteKeysByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        Set<String> keys = stringRedisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Resource
    UserMapper userMapper;
    @Resource
    TeamMapper teamMapper;
    @Resource
    TeamMemberMapper teamMemberMapper;
    @Resource
    UserFriendMapper userFriendMapper;
    @Resource
    UserBlacklistMapper userBlacklistMapper;
    @Resource
    SearchHistoryMapper searchHistoryMapper;
    @Resource
    SearchHotKeywordMapper searchHotKeywordMapper;
    @Resource
    UserRecommendationMapper userRecommendationMapper;
    @Resource
    TeamRecommendationMapper teamRecommendationMapper;
    @Resource
    ChatMessageMapper chatMessageMapper;
    @Resource
    StringRedisTemplate stringRedisTemplate;

    public Long requireLogin() {
        Long uid = UserHolder.getUserId();
        if (uid == null) throw new BusinessException("用户未登录");
        return uid;
    }

    public String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) throw new BusinessException("搜索关键词不能为空");
        return keyword.trim();
    }

    public int normalizePage(int page) {
        return Math.max(1, page);
    }

    public int normalizePageSize(int pageSize) {
        return Math.max(1, pageSize);
    }

    public int normalizeLimit(int limit) {
        return Math.max(1, limit);
    }

    public int validateSearchType(Integer searchType) {
        if (searchType == null || (searchType != SEARCH_USER && searchType != SEARCH_TEAM)) {
            throw new BusinessException("searchType 参数无效");
        }
        return searchType;
    }

    public int validateSuggestType(Integer type) {
        if (type == null || (type != SEARCH_USER && type != SEARCH_TEAM)) {
            throw new BusinessException("type 参数无效");
        }
        return type;
    }

    public int validateRecommendType(Integer recommendType) {
        if (recommendType == null || (recommendType != RECOMMEND_TYPE_USER && recommendType != RECOMMEND_TYPE_TEAM)) {
            throw new BusinessException("recommendType 参数无效");
        }
        return recommendType;
    }

    public void persistSearch(Long userId, int searchType, String keyword) {
        LambdaQueryWrapper<SearchHistory> qw = new LambdaQueryWrapper<>();
        qw.eq(SearchHistory::getUserId, userId)
                .eq(SearchHistory::getSearchType, searchType)
                .eq(SearchHistory::getSearchKeyword, keyword);

        SearchHistory his = searchHistoryMapper.selectOne(qw);
        if (his == null) {
            his = new SearchHistory();
            his.setUserId(userId);
            his.setSearchType(searchType);
            his.setSearchKeyword(keyword);
            his.setSearchCount(1);
            his.setLastSearchTime(LocalDateTime.now());
            his.setIsDelete(0);
            searchHistoryMapper.insert(his);
        } else {
            his.setSearchCount(Optional.ofNullable(his.getSearchCount()).orElse(0) + 1);
            his.setLastSearchTime(LocalDateTime.now());
            his.setIsDelete(0);
            searchHistoryMapper.updateById(his);
        }

        LambdaQueryWrapper<SearchHotKeyword> hotQw = new LambdaQueryWrapper<>();
        hotQw.eq(SearchHotKeyword::getKeyword, keyword).eq(SearchHotKeyword::getSearchType, searchType);
        SearchHotKeyword hot = searchHotKeywordMapper.selectOne(hotQw);
        if (hot == null) {
            hot = new SearchHotKeyword();
            hot.setKeyword(keyword);
            hot.setSearchType(searchType);
            hot.setSearchCount(1);
            hot.setRank(0);
            hot.setIsDelete(0);
            searchHotKeywordMapper.insert(hot);
        } else {
            hot.setSearchCount(Optional.ofNullable(hot.getSearchCount()).orElse(0) + 1);
            hot.setIsDelete(0);
            searchHotKeywordMapper.updateById(hot);
        }
        deleteKeysByPrefix(buildHotKeywordCachePrefix(searchType));
    }

    public UserSearchVO buildUserSearchItem(User u, String keyword) {
        String kw = keyword.toLowerCase();
        String nick = Optional.ofNullable(u.getUserNickname()).orElse("");
        String acc = Optional.ofNullable(u.getUserAccount()).orElse("");
        String tags = Optional.ofNullable(u.getUserTags()).orElse("");

        int score;
        String reason;
        if (nick.equalsIgnoreCase(keyword) || acc.equalsIgnoreCase(keyword)) {
            score = 100;
            reason = "精确匹配";
        } else if (nick.toLowerCase().startsWith(kw) || acc.toLowerCase().startsWith(kw)) {
            score = 90;
            reason = "前缀匹配";
        } else if (nick.toLowerCase().contains(kw) || acc.toLowerCase().contains(kw)) {
            score = 80;
            reason = "名称匹配";
        } else if (tags.toLowerCase().contains(kw)) {
            score = 70;
            reason = "标签匹配";
        } else {
            score = 60;
            reason = "相关匹配";
        }

        UserSearchVO vo = new UserSearchVO();
        vo.setUserId(u.getId());
        vo.setUserAccount(u.getUserAccount());
        vo.setUserNickname(u.getUserNickname());
        vo.setUserAvatar(u.getUserAvatar());
        vo.setUserIntro(u.getUserIntro());
        vo.setUserTags(u.getUserTags());
        vo.setSimilarityScore(score);
        vo.setMatchReason(reason);
        return vo;
    }

    public TeamSearchVO buildTeamSearchItem(Long currentUserId, Team t, String keyword) {
        int memberCount = getTeamMemberCount(t.getId());
        int isMember = getUserTeamIds(currentUserId).contains(t.getId()) ? 1 : 0;
        return buildTeamSearchItem(t, keyword, memberCount, isMember);
    }

    public TeamSearchVO buildTeamSearchItem(Team t, String keyword, int memberCount, int isMember) {
        String kw = keyword.toLowerCase();
        String name = Optional.ofNullable(t.getTeamName()).orElse("");
        String tags = Optional.ofNullable(t.getTeamTags()).orElse("");
        String intro = Optional.ofNullable(t.getTeamIntro()).orElse("");

        int base;
        String reason;
        if (name.equalsIgnoreCase(keyword)) {
            base = 95;
            reason = "团队名精确匹配";
        } else if (name.toLowerCase().contains(kw)) {
            base = 85;
            reason = "团队名匹配";
        } else if (tags.toLowerCase().contains(kw)) {
            base = 75;
            reason = "标签匹配";
        } else if (intro.toLowerCase().contains(kw)) {
            base = 65;
            reason = "简介匹配";
        } else {
            base = 55;
            reason = "相关匹配";
        }
        int score = Math.min(100, base + Math.min(10, memberCount / 10));

        TeamSearchVO vo = new TeamSearchVO();
        vo.setTeamId(t.getId());
        vo.setTeamName(t.getTeamName());
        vo.setTeamAvatar(t.getTeamAvatar());
        vo.setTeamIntro(t.getTeamIntro());
        vo.setTeamTags(t.getTeamTags());
        vo.setMemberCount(memberCount);
        vo.setIsMember(isMember);
        vo.setSimilarityScore(score);
        vo.setMatchReason(reason);
        return vo;
    }

    public boolean isBlacklistedEitherWay(Long a, Long b) {
        LambdaQueryWrapper<UserBlacklist> qw1 = new LambdaQueryWrapper<>();
        qw1.eq(UserBlacklist::getUserId, a).eq(UserBlacklist::getBlackUserId, b).eq(UserBlacklist::getIsDelete, 0);
        if (userBlacklistMapper.selectCount(qw1) > 0) return true;

        LambdaQueryWrapper<UserBlacklist> qw2 = new LambdaQueryWrapper<>();
        qw2.eq(UserBlacklist::getUserId, b).eq(UserBlacklist::getBlackUserId, a).eq(UserBlacklist::getIsDelete, 0);
        return userBlacklistMapper.selectCount(qw2) > 0;
    }

    public Set<Long> getBlacklistedUserIds(Long currentUserId, Collection<Long> candidateUserIds) {
        if (currentUserId == null || candidateUserIds == null || candidateUserIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> ids = candidateUserIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }

        List<UserBlacklist> blacklistRows = userBlacklistMapper.selectList(new LambdaQueryWrapper<UserBlacklist>()
                .eq(UserBlacklist::getIsDelete, 0)
                .and(w -> w.eq(UserBlacklist::getUserId, currentUserId)
                        .in(UserBlacklist::getBlackUserId, ids)
                        .or(inner -> inner.eq(UserBlacklist::getBlackUserId, currentUserId)
                                .in(UserBlacklist::getUserId, ids))));

        Set<Long> result = new HashSet<>();
        for (UserBlacklist row : blacklistRows) {
            if (currentUserId.equals(row.getUserId())) {
                result.add(row.getBlackUserId());
            } else {
                result.add(row.getUserId());
            }
        }
        return result;
    }

    public Set<Long> getFriendIds(Long userId) {
        LambdaQueryWrapper<UserFriend> qw = new LambdaQueryWrapper<>();
        qw.eq(UserFriend::getFriendStatus, 1)
                .and(w -> w.eq(UserFriend::getUserId, userId)
                        .or().eq(UserFriend::getFriendId, userId));
        return userFriendMapper.selectList(qw).stream()
                .map(f -> userId.equals(f.getUserId()) ? f.getFriendId() : f.getUserId())
                .collect(Collectors.toSet());
    }

    public Set<Long> getPrivateChatPartners(Long userId) {
        LambdaQueryWrapper<ChatMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(ChatMessage::getRecvType, 1)
                .eq(ChatMessage::getIsDelete, 0)
                .and(w -> w.eq(ChatMessage::getSenderId, userId)
                        .or().eq(ChatMessage::getRecvId, userId));
        return chatMessageMapper.selectList(qw).stream()
                .map(m -> userId.equals(m.getSenderId()) ? m.getRecvId() : m.getSenderId())
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(userId))
                .collect(Collectors.toSet());
    }

    public Set<Long> getUserTeamIds(Long userId) {
        return teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getIsQuit, 0))
                .stream().map(TeamMember::getTeamId).collect(Collectors.toSet());
    }

    public Map<Long, Set<Long>> getUserTeamIdsMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Set<Long>> result = new HashMap<>();
        ids.forEach(id -> result.put(id, new HashSet<>()));
        List<TeamMember> rows = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .in(TeamMember::getUserId, ids)
                .eq(TeamMember::getIsQuit, 0));
        for (TeamMember row : rows) {
            result.computeIfAbsent(row.getUserId(), k -> new HashSet<>()).add(row.getTeamId());
        }
        return result;
    }

    public Set<Long> getTeamMemberIds(Long teamId) {
        return teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getIsQuit, 0))
                .stream().map(TeamMember::getUserId).collect(Collectors.toSet());
    }

    public Map<Long, Set<Long>> getTeamMemberIdsMap(Collection<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = teamIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Set<Long>> result = new HashMap<>();
        ids.forEach(id -> result.put(id, new HashSet<>()));
        List<TeamMember> rows = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .in(TeamMember::getTeamId, ids)
                .eq(TeamMember::getIsQuit, 0));
        for (TeamMember row : rows) {
            result.computeIfAbsent(row.getTeamId(), k -> new HashSet<>()).add(row.getUserId());
        }
        return result;
    }

    public Set<Long> collectTeamMembers(Set<Long> teamIds) {
        Set<Long> members = new HashSet<>();
        for (Long teamId : teamIds) {
            members.addAll(getTeamMemberIds(teamId));
        }
        return members;
    }

    public int getTeamMemberCount(Long teamId) {
        return teamMemberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getIsQuit, 0)).intValue();
    }

    public Map<Long, Integer> getTeamMemberCountMap(Collection<Long> teamIds) {
        Map<Long, Integer> result = new HashMap<>();
        Map<Long, Set<Long>> memberMap = getTeamMemberIdsMap(teamIds);
        for (Map.Entry<Long, Set<Long>> entry : memberMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue().size());
        }
        return result;
    }

    public int getTeamMessageCount(Long teamId) {
        return chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRecvType, 2)
                .eq(ChatMessage::getRecvId, teamId)
                .eq(ChatMessage::getIsDelete, 0)).intValue();
    }

    public Map<Long, Integer> getTeamMessageCountMap(Collection<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = teamIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Integer> result = new HashMap<>();
        ids.forEach(id -> result.put(id, 0));
        List<ChatMessage> rows = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRecvType, 2)
                .in(ChatMessage::getRecvId, ids)
                .eq(ChatMessage::getIsDelete, 0));
        for (ChatMessage row : rows) {
            result.merge(row.getRecvId(), 1, Integer::sum);
        }
        return result;
    }

    public Map<Long, Set<Long>> getFriendIdsMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Set<Long>> result = new HashMap<>();
        ids.forEach(id -> result.put(id, new HashSet<>()));
        List<UserFriend> rows = userFriendMapper.selectList(new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getFriendStatus, 1)
                .and(w -> w.in(UserFriend::getUserId, ids)
                        .or().in(UserFriend::getFriendId, ids)));
        for (UserFriend row : rows) {
            if (ids.contains(row.getUserId())) {
                result.computeIfAbsent(row.getUserId(), k -> new HashSet<>()).add(row.getFriendId());
            }
            if (ids.contains(row.getFriendId())) {
                result.computeIfAbsent(row.getFriendId(), k -> new HashSet<>()).add(row.getUserId());
            }
        }
        return result;
    }

    public Map<Long, Set<Long>> getPrivateChatPartnersMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Set<Long>> result = new HashMap<>();
        ids.forEach(id -> result.put(id, new HashSet<>()));
        List<ChatMessage> rows = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRecvType, 1)
                .eq(ChatMessage::getIsDelete, 0)
                .and(w -> w.in(ChatMessage::getSenderId, ids)
                        .or().in(ChatMessage::getRecvId, ids)));
        for (ChatMessage row : rows) {
            Long senderId = row.getSenderId();
            Long recvId = row.getRecvId();
            if (senderId != null && recvId != null) {
                if (ids.contains(senderId) && !senderId.equals(recvId)) {
                    result.computeIfAbsent(senderId, k -> new HashSet<>()).add(recvId);
                }
                if (ids.contains(recvId) && !recvId.equals(senderId)) {
                    result.computeIfAbsent(recvId, k -> new HashSet<>()).add(senderId);
                }
            }
        }
        return result;
    }

    public int calcTagScore(Set<String> a, Set<String> b, int maxScore) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0;
        int inter = intersectionCount(a, b);
        int union = a.size() + b.size() - inter;
        if (union <= 0) return 0;
        return (int) Math.round((double) inter * maxScore / union);
    }

    public <T> int calcMinBaseScore(Set<T> a, Set<T> b, int maxScore) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0;
        int inter = intersectionCount(a, b);
        int base = Math.min(a.size(), b.size());
        if (base <= 0) return 0;
        return (int) Math.round((double) inter * maxScore / base);
    }

    public int calcTeamHotScore(Team team, int memberCount, int maxScore) {
        return calcTeamHotScore(memberCount, getTeamMessageCount(team.getId()), team, maxScore);
    }

    public int calcTeamHotScore(int memberCount, int messageCount, Team team, int maxScore) {
        double days = 1.0;
        if (team.getCreateTime() != null) {
            days = Math.max(1.0, java.time.Duration.between(team.getCreateTime(), LocalDateTime.now()).toDays());
        }
        double heat = (memberCount + messageCount) / days;
        return Math.min(maxScore, (int) Math.round(heat));
    }

    public int calcTeamNewScore(Team team, int maxScore) {
        if (team.getCreateTime() == null) return 0;
        double hours = Math.max(1.0, java.time.Duration.between(team.getCreateTime(), LocalDateTime.now()).toHours());
        int score = (int) Math.round((24.0 / hours) * maxScore);
        return Math.max(0, Math.min(maxScore, score));
    }

    public <T> int intersectionCount(Set<T> a, Set<T> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0;
        Set<T> t = new HashSet<>(a);
        t.retainAll(b);
        return t.size();
    }

    public String reasonTextForUser(int reason) {
        return switch (reason) {
            case 2 -> "共同好友较多";
            case 3 -> "聊天兴趣接近";
            case 4 -> "共同团队较多";
            default -> "标签相似";
        };
    }

    public String reasonTextForTeam(int reason) {
        return switch (reason) {
            case 2 -> "共同成员较多";
            case 3 -> "热门团队";
            case 4 -> "新建团队";
            default -> "标签相似";
        };
    }

    public Set<String> splitTags(String tags) {
        if (tags == null || tags.trim().isEmpty()) return Collections.emptySet();
        return Arrays.stream(tags.replace("，", ",").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public UserFormat buildUserFormat(User user) {
        UserFormat uf = new UserFormat();
        uf.setId(user.getId());
        uf.setUserAccount(user.getUserAccount());
        uf.setUserNickname(user.getUserNickname());
        uf.setUserAvatar(user.getUserAvatar());
        uf.setUserTags(user.getUserTags());
        uf.setUserIntro(user.getUserIntro());
        return uf;
    }
}
