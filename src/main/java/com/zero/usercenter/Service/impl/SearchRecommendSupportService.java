package com.zero.usercenter.Service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zero.usercenter.DTO.PrivacySettingDTO;
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

/**
 * 搜索与推荐支撑服务。
 * 统一沉淀搜索参数校验、关系数据聚合、推荐打分辅助和缓存 Key 规则，
 * 让查询服务与推荐刷新服务都能复用同一套底层规则。
 */
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

    /**
     * 构建热词缓存键。
     *
     * @param searchType 搜索类型
     * @param limit      返回条数
     * @return 热词缓存键
     */
    public String buildHotKeywordCacheKey(Integer searchType, int limit) {
        // 热词和推荐结果都按“用户 / 类型 / limit”维度拆 key，
        // 这样既便于局部失效，也能避免不同展示位之间缓存相互污染。
        return HOT_KEYWORDS_CACHE_KEY + searchType + ":" + limit;
    }

    /**
     * 构建热词缓存前缀。
     *
     * @param searchType 搜索类型
     * @return 热词缓存前缀
     */
    public String buildHotKeywordCachePrefix(Integer searchType) {
        return HOT_KEYWORDS_CACHE_KEY + searchType + ":";
    }

    /**
     * 构建用户推荐缓存键。
     *
     * @param userId 用户 ID
     * @param limit  返回条数
     * @return 用户推荐缓存键
     */
    public String buildRecommendUsersCacheKey(Long userId, int limit) {
        return RECOMMEND_USERS_CACHE_KEY + userId + ":" + limit;
    }

    /**
     * 构建用户推荐缓存前缀。
     *
     * @param userId 用户 ID
     * @return 用户推荐缓存前缀
     */
    public String buildRecommendUsersCachePrefix(Long userId) {
        return RECOMMEND_USERS_CACHE_KEY + userId + ":";
    }

    /**
     * 构建团队推荐缓存键。
     *
     * @param userId 用户 ID
     * @param limit  返回条数
     * @return 团队推荐缓存键
     */
    public String buildRecommendTeamsCacheKey(Long userId, int limit) {
        return RECOMMEND_TEAMS_CACHE_KEY + userId + ":" + limit;
    }

    /**
     * 构建团队推荐缓存前缀。
     *
     * @param userId 用户 ID
     * @return 团队推荐缓存前缀
     */
    public String buildRecommendTeamsCachePrefix(Long userId) {
        return RECOMMEND_TEAMS_CACHE_KEY + userId + ":";
    }

    /**
     * 按前缀批量删除缓存键。
     *
     * @param prefix 缓存前缀
     */
    public void deleteKeysByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        // 1. 推荐和热词缓存都按前缀局部失效，避免每次更新都维护一长串具体 key。
        Set<String> keys = stringRedisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            // 2. 只有实际命中键时才执行删除，避免无意义调用 Redis。
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

    /**
     * 获取当前登录用户 ID。
     *
     * @return 当前登录用户 ID
     */
    public Long requireLogin() {
        // 1. 搜索与推荐接口统一从线程上下文读取登录用户。
        Long uid = UserHolder.getUserId();
        if (uid == null) throw new BusinessException("用户未登录");
        return uid;
    }

    /**
     * 标准化搜索关键词。
     *
     * @param keyword 原始关键词
     * @return 去除首尾空格后的关键词
     */
    public String normalizeKeyword(String keyword) {
        // 1. 搜索关键词为空时直接拒绝，避免生成全表扫描语义。
        if (keyword == null || keyword.trim().isEmpty()) throw new BusinessException("搜索关键词不能为空");
        return keyword.trim();
    }

    /**
     * 判断关键词是否应按邮箱搜索处理。
     *
     * @param keyword 搜索关键词
     * @return true 表示当前关键词更像邮箱
     */
    public boolean looksLikeEmailKeyword(String keyword) {
        if (keyword == null) {
            return false;
        }
        String normalized = keyword.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        return normalized.contains("@");
    }

    /**
     * 规范化页码。
     *
     * @param page 原始页码
     * @return 至少为 1 的页码
     */
    public int normalizePage(int page) {
        return Math.max(1, page);
    }

    /**
     * 规范化分页大小。
     *
     * @param pageSize 原始分页大小
     * @return 至少为 1 的分页大小
     */
    public int normalizePageSize(int pageSize) {
        return Math.max(1, pageSize);
    }

    /**
     * 规范化 limit 参数。
     *
     * @param limit 原始 limit
     * @return 至少为 1 的 limit
     */
    public int normalizeLimit(int limit) {
        return Math.max(1, limit);
    }

    /**
     * 校验搜索类型参数。
     *
     * @param searchType 搜索类型
     * @return 合法的搜索类型
     */
    public int validateSearchType(Integer searchType) {
        if (searchType == null || (searchType != SEARCH_USER && searchType != SEARCH_TEAM)) {
            throw new BusinessException("searchType 参数无效");
        }
        return searchType;
    }

    /**
     * 校验联想类型参数。
     *
     * @param type 联想类型
     * @return 合法的联想类型
     */
    public int validateSuggestType(Integer type) {
        if (type == null || (type != SEARCH_USER && type != SEARCH_TEAM)) {
            throw new BusinessException("type 参数无效");
        }
        return type;
    }

    /**
     * 校验推荐类型参数。
     *
     * @param recommendType 推荐类型
     * @return 合法的推荐类型
     */
    public int validateRecommendType(Integer recommendType) {
        if (recommendType == null || (recommendType != RECOMMEND_TYPE_USER && recommendType != RECOMMEND_TYPE_TEAM)) {
            throw new BusinessException("recommendType 参数无效");
        }
        return recommendType;
    }

    /**
     * 持久化搜索记录并刷新热词缓存。
     *
     * @param userId     搜索用户 ID
     * @param searchType 搜索类型
     * @param keyword    搜索关键词
     */
    public void persistSearch(Long userId, int searchType, String keyword) {
        // 1. 搜索历史和热词榜同步写库，随后按前缀清缓存，
        // 避免高频搜索场景下每次都做全量排行重算。
        LambdaQueryWrapper<SearchHistory> qw = new LambdaQueryWrapper<>();
        qw.eq(SearchHistory::getUserId, userId)
                .eq(SearchHistory::getSearchType, searchType)
                .eq(SearchHistory::getSearchKeyword, keyword);

        SearchHistory his = searchHistoryMapper.selectOne(qw);
        if (his == null) {
            // 2. 用户首次搜索该关键词时新增历史记录。
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

        // 3. 热词榜按关键词维度累计搜索次数，供后续排行榜和联想词使用。
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

        // 4. 数据更新后按前缀清理热词缓存，让下次读取看到最新排行。
        deleteKeysByPrefix(buildHotKeywordCachePrefix(searchType));
    }

    /**
     * 构建用户搜索结果项。
     *
     * @param u       用户实体
     * @param keyword 搜索关键词
     * @return 用户搜索展示对象
     */
    public UserSearchVO buildUserSearchItem(User u, String keyword) {
        // 1. 用户搜索得分是规则式评分，不是模型推理，便于解释和调权。
        String kw = keyword.toLowerCase();
        String nick = Optional.ofNullable(u.getUserNickname()).orElse("");
        String acc = Optional.ofNullable(u.getUserAccount()).orElse("");
        String email = Optional.ofNullable(u.getUserEmail()).orElse("");
        String tags = Optional.ofNullable(u.getUserTags()).orElse("");

        int score;
        String reason;
        if (nick.equalsIgnoreCase(keyword) || acc.equalsIgnoreCase(keyword) || email.equalsIgnoreCase(keyword)) {
            score = 100;
            reason = email.equalsIgnoreCase(keyword) ? "邮箱精确匹配" : "精确匹配";
        } else if (nick.toLowerCase().startsWith(kw) || acc.toLowerCase().startsWith(kw) || email.toLowerCase().startsWith(kw)) {
            score = 90;
            reason = email.toLowerCase().startsWith(kw) ? "邮箱前缀匹配" : "前缀匹配";
        } else if (nick.toLowerCase().contains(kw) || acc.toLowerCase().contains(kw) || email.toLowerCase().contains(kw)) {
            score = 80;
            reason = email.toLowerCase().contains(kw) ? "邮箱匹配" : "名称匹配";
        } else if (tags.toLowerCase().contains(kw)) {
            score = 70;
            reason = "标签匹配";
        } else {
            score = 60;
            reason = "相关匹配";
        }

        // 2. 将命中的用户基础信息和评分结果一起组装给前端展示。
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

    /**
     * 判断目标用户是否允许被邮箱搜索。
     *
     * @param targetUser 目标用户
     * @return true 表示允许邮箱搜索
     */
    public boolean canSearchByEmail(User targetUser) {
        // 1. 没有隐私配置时默认不允许邮箱搜索，避免邮箱被意外暴露。
        String privacySettingStr = targetUser.getPrivacySetting();
        if (privacySettingStr == null || privacySettingStr.isEmpty()) {
            return false;
        }

        try {
            // 2. 只有显式开启 searchByEmail=1 才允许通过邮箱命中。
            PrivacySettingDTO privacySetting = JSON.parseObject(privacySettingStr, PrivacySettingDTO.class);
            return privacySetting != null
                    && privacySetting.getSearchByEmail() != null
                    && privacySetting.getSearchByEmail() == 1;
        } catch (Exception e) {
            // 3. 配置解析失败时走最保守分支，避免脏数据扩大可见范围。
            return false;
        }
    }

    /**
     * 判断当前用户是否可以在搜索/推荐场景查看目标用户资料。
     *
     * @param currentUserId 当前登录用户 ID
     * @param targetUser    目标用户
     * @return true 表示允许查看
     */
    public boolean canViewUserInfo(Long currentUserId, User targetUser) {
        // 1. 没有隐私配置或解析失败时，兼容旧数据，默认允许公开资料展示。
        String privacySettingStr = targetUser.getPrivacySetting();
        if (privacySettingStr == null || privacySettingStr.isEmpty()) {
            return true;
        }

        PrivacySettingDTO privacySetting;
        try {
            privacySetting = JSON.parseObject(privacySettingStr, PrivacySettingDTO.class);
        } catch (Exception e) {
            return true;
        }
        if (privacySetting == null || privacySetting.getViewInfo() == null) {
            return true;
        }

        // 2. viewInfo=1 代表对所有人公开，直接放行。
        if (privacySetting.getViewInfo() == 1) {
            return true;
        }

        // 3. “仅团队成员可见”按双方当前仍未退出的共同团队判断。
        if (privacySetting.getViewInfo() == 2) {
            Set<Long> myTeamIds = getUserTeamIds(currentUserId);
            if (myTeamIds.isEmpty()) {
                return false;
            }
            return teamMemberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                    .eq(TeamMember::getUserId, targetUser.getId())
                    .eq(TeamMember::getIsQuit, 0)
                    .in(TeamMember::getTeamId, myTeamIds)) > 0;
        }

        // 4. 其它未识别配置按不可见处理，避免错误配置扩大公开范围。
        return false;
    }

    /**
     * 构建团队搜索结果项，并自动补齐成员数与是否已加入状态。
     *
     * @param currentUserId 当前登录用户 ID
     * @param t             团队实体
     * @param keyword       搜索关键词
     * @return 团队搜索展示对象
     */
    public TeamSearchVO buildTeamSearchItem(Long currentUserId, Team t, String keyword) {
        // 1. 先补齐团队搜索展示依赖的动态字段。
        int memberCount = getTeamMemberCount(t.getId());
        int isMember = getUserTeamIds(currentUserId).contains(t.getId()) ? 1 : 0;
        return buildTeamSearchItem(t, keyword, memberCount, isMember);
    }

    /**
     * 构建团队搜索结果项。
     *
     * @param t           团队实体
     * @param keyword     搜索关键词
     * @param memberCount 团队成员数
     * @param isMember    当前用户是否已加入
     * @return 团队搜索展示对象
     */
    public TeamSearchVO buildTeamSearchItem(Team t, String keyword, int memberCount, int isMember) {
        // 1. 团队搜索除了关键词匹配外，还会把成员规模作为轻量加分项。
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

        // 2. 组装团队展示字段、动态状态和匹配原因。
        TeamSearchVO vo = new TeamSearchVO();
        vo.setTeamId(t.getId());
        vo.setTeamName(t.getTeamName());
        vo.setTeamAvatar(t.getTeamAvatar());
        vo.setTeamIntro(t.getTeamIntro());
        vo.setTeamTags(t.getTeamTags());
        vo.setMemberCount(memberCount);
        vo.setMaxMember(t.getMaxMember());
        vo.setTeamType(t.getTeamType());
        vo.setJoinRule(t.getJoinRule());
        vo.setIsMember(isMember);
        vo.setSimilarityScore(score);
        vo.setMatchReason(reason);
        return vo;
    }

    /**
     * 判断两个用户之间是否存在任一方向的黑名单关系。
     *
     * @param a 用户 A
     * @param b 用户 B
     * @return true 表示任一方向存在黑名单关系
     */
    public boolean isBlacklistedEitherWay(Long a, Long b) {
        // 1. 搜索和推荐都把双向黑名单视为不可见关系，因此这里要查两个方向。
        LambdaQueryWrapper<UserBlacklist> qw1 = new LambdaQueryWrapper<>();
        qw1.eq(UserBlacklist::getUserId, a).eq(UserBlacklist::getBlackUserId, b).eq(UserBlacklist::getIsDelete, 0);
        if (userBlacklistMapper.selectCount(qw1) > 0) return true;

        // 2. 当前向未命中时，再检查反向是否把对方拉黑。
        LambdaQueryWrapper<UserBlacklist> qw2 = new LambdaQueryWrapper<>();
        qw2.eq(UserBlacklist::getUserId, b).eq(UserBlacklist::getBlackUserId, a).eq(UserBlacklist::getIsDelete, 0);
        return userBlacklistMapper.selectCount(qw2) > 0;
    }

    /**
     * 批量查询与当前用户存在黑名单关系的候选用户。
     *
     * @param currentUserId     当前登录用户 ID
     * @param candidateUserIds  候选用户 ID 集合
     * @return 需要过滤掉的用户 ID 集合
     */
    public Set<Long> getBlacklistedUserIds(Long currentUserId, Collection<Long> candidateUserIds) {
        // 1. 批量查双向黑名单，避免在推荐循环里对每个候选人逐个查库。
        if (currentUserId == null || candidateUserIds == null || candidateUserIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> ids = candidateUserIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }

        // 2. 一次性查出“我拉黑别人”和“别人拉黑我”两种关系。
        List<UserBlacklist> blacklistRows = userBlacklistMapper.selectList(new LambdaQueryWrapper<UserBlacklist>()
                .eq(UserBlacklist::getIsDelete, 0)
                .and(w -> w.eq(UserBlacklist::getUserId, currentUserId)
                        .in(UserBlacklist::getBlackUserId, ids)
                        .or(inner -> inner.eq(UserBlacklist::getBlackUserId, currentUserId)
                                .in(UserBlacklist::getUserId, ids))));

        // 3. 统一抽取需要排除的对端用户 ID。
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

    /**
     * 获取用户的好友 ID 集合。
     *
     * @param userId 用户 ID
     * @return 与当前用户存在有效好友关系的用户 ID 集合
     */
    public Set<Long> getFriendIds(Long userId) {
        // 1. 好友关系表按双向两条记录维护，这里统一抽象成“与我有关的好友 ID 集合”。
        LambdaQueryWrapper<UserFriend> qw = new LambdaQueryWrapper<>();
        qw.eq(UserFriend::getFriendStatus, 1)
                .and(w -> w.eq(UserFriend::getUserId, userId)
                        .or().eq(UserFriend::getFriendId, userId));
        return userFriendMapper.selectList(qw).stream()
                .map(f -> userId.equals(f.getUserId()) ? f.getFriendId() : f.getUserId())
                .collect(Collectors.toSet());
    }

    /**
     * 获取用户的私聊对象集合。
     *
     * @param userId 用户 ID
     * @return 与用户发生过私聊往来的用户 ID 集合
     */
    public Set<Long> getPrivateChatPartners(Long userId) {
        // 1. 私聊关系并不等同于好友关系，推荐时会把“有真实私聊往来”的用户单独作为强相关信号。
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

    /**
     * 获取用户当前所在团队 ID 集合。
     *
     * @param userId 用户 ID
     * @return 当前未退出的团队 ID 集合
     */
    public Set<Long> getUserTeamIds(Long userId) {
        return teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getIsQuit, 0))
                .stream().map(TeamMember::getTeamId).collect(Collectors.toSet());
    }

    /**
     * 批量获取多个用户所在团队集合。
     *
     * @param userIds 用户 ID 集合
     * @return Map&lt;用户 ID, 团队 ID 集合&gt;
     */
    public Map<Long, Set<Long>> getUserTeamIdsMap(Collection<Long> userIds) {
        // 1. 先清理空输入，只保留有效用户 ID。
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        // 2. 预先为每个用户放一个空集合，保证后续即使没数据也能返回稳定结构。
        Map<Long, Set<Long>> result = new HashMap<>();
        ids.forEach(id -> result.put(id, new HashSet<>()));

        // 3. 一次性查出这些用户的全部有效入队记录，再按 userId 聚合。
        List<TeamMember> rows = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .in(TeamMember::getUserId, ids)
                .eq(TeamMember::getIsQuit, 0));
        for (TeamMember row : rows) {
            result.computeIfAbsent(row.getUserId(), k -> new HashSet<>()).add(row.getTeamId());
        }
        return result;
    }

    /**
     * 获取团队当前成员 ID 集合。
     *
     * @param teamId 团队 ID
     * @return 团队未退出成员 ID 集合
     */
    public Set<Long> getTeamMemberIds(Long teamId) {
        return teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getIsQuit, 0))
                .stream().map(TeamMember::getUserId).collect(Collectors.toSet());
    }

    /**
     * 批量获取多个团队的成员集合。
     *
     * @param teamIds 团队 ID 集合
     * @return Map&lt;团队 ID, 成员 ID 集合&gt;
     */
    public Map<Long, Set<Long>> getTeamMemberIdsMap(Collection<Long> teamIds) {
        // 1. 过滤空输入，只保留有效团队 ID。
        if (teamIds == null || teamIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = teamIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        // 2. 提前补齐空集合，保证没有成员的团队也能在返回结果中占位。
        Map<Long, Set<Long>> result = new HashMap<>();
        ids.forEach(id -> result.put(id, new HashSet<>()));

        // 3. 一次性查出这些团队的有效成员，再按 teamId 聚合。
        List<TeamMember> rows = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .in(TeamMember::getTeamId, ids)
                .eq(TeamMember::getIsQuit, 0));
        for (TeamMember row : rows) {
            result.computeIfAbsent(row.getTeamId(), k -> new HashSet<>()).add(row.getUserId());
        }
        return result;
    }

    /**
     * 汇总多个团队的全部成员。
     *
     * @param teamIds 团队 ID 集合
     * @return 所有成员 ID 集合
     */
    public Set<Long> collectTeamMembers(Set<Long> teamIds) {
        // 1. 推荐算法需要“多个团队的成员并集”时，通过该方法统一收口。
        Set<Long> members = new HashSet<>();
        for (Long teamId : teamIds) {
            members.addAll(getTeamMemberIds(teamId));
        }
        return members;
    }

    /**
     * 获取团队成员数量。
     *
     * @param teamId 团队 ID
     * @return 团队未退出成员数
     */
    public int getTeamMemberCount(Long teamId) {
        return teamMemberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getIsQuit, 0)).intValue();
    }

    /**
     * 批量获取团队成员数。
     *
     * @param teamIds 团队 ID 集合
     * @return Map&lt;团队 ID, 成员数&gt;
     */
    public Map<Long, Integer> getTeamMemberCountMap(Collection<Long> teamIds) {
        // 1. 复用批量成员集合查询，再直接取集合大小即可。
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

    /**
     * 批量获取团队消息量。
     *
     * @param teamIds 团队 ID 集合
     * @return Map&lt;团队 ID, 消息数&gt;
     */
    public Map<Long, Integer> getTeamMessageCountMap(Collection<Long> teamIds) {
        // 1. 先过滤空输入，只保留有效团队 ID。
        if (teamIds == null || teamIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = teamIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        // 2. 先给每个团队默认 0，避免无消息团队在结果里缺失。
        Map<Long, Integer> result = new HashMap<>();
        ids.forEach(id -> result.put(id, 0));

        // 3. 一次性查出这些团队的群消息，再按 recvId 聚合计数。
        List<ChatMessage> rows = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRecvType, 2)
                .in(ChatMessage::getRecvId, ids)
                .eq(ChatMessage::getIsDelete, 0));
        for (ChatMessage row : rows) {
            result.merge(row.getRecvId(), 1, Integer::sum);
        }
        return result;
    }

    /**
     * 批量获取多个用户的好友集合。
     *
     * @param userIds 用户 ID 集合
     * @return Map&lt;用户 ID, 好友 ID 集合&gt;
     */
    public Map<Long, Set<Long>> getFriendIdsMap(Collection<Long> userIds) {
        // 1. 先过滤空输入，只保留有效用户 ID。
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        // 2. 预置空集合，保证没有好友的用户也能返回稳定结构。
        Map<Long, Set<Long>> result = new HashMap<>();
        ids.forEach(id -> result.put(id, new HashSet<>()));

        // 3. 一次性把和这些用户有关的好友关系都查出来，再按双向关系分别回填。
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

    /**
     * 批量获取多个用户的私聊对象集合。
     *
     * @param userIds 用户 ID 集合
     * @return Map&lt;用户 ID, 私聊对象 ID 集合&gt;
     */
    public Map<Long, Set<Long>> getPrivateChatPartnersMap(Collection<Long> userIds) {
        // 1. 先过滤空输入，只保留有效用户 ID。
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        // 2. 预置空集合，保证无私聊关系用户也能稳定返回。
        Map<Long, Set<Long>> result = new HashMap<>();
        ids.forEach(id -> result.put(id, new HashSet<>()));

        // 3. 一次性查出与这些用户相关的私聊消息，再按双向关系聚合聊天对象。
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

    /**
     * 计算标签相似度得分。
     *
     * @param a        标签集合 A
     * @param b        标签集合 B
     * @param maxScore 最大得分
     * @return 标签相似度得分
     */
    public int calcTagScore(Set<String> a, Set<String> b, int maxScore) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0;
        int inter = intersectionCount(a, b);
        int union = a.size() + b.size() - inter;
        if (union <= 0) return 0;
        return (int) Math.round((double) inter * maxScore / union);
    }

    /**
     * 计算基于较小集合的重合得分。
     *
     * @param a        集合 A
     * @param b        集合 B
     * @param maxScore 最大得分
     * @return 重合得分
     * @param <T> 集合元素类型
     */
    public <T> int calcMinBaseScore(Set<T> a, Set<T> b, int maxScore) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0;
        int inter = intersectionCount(a, b);
        int base = Math.min(a.size(), b.size());
        if (base <= 0) return 0;
        return (int) Math.round((double) inter * maxScore / base);
    }

    /**
     * 计算团队热度得分。
     *
     * @param team      团队实体
     * @param memberCount 成员数
     * @param maxScore  最大得分
     * @return 热度得分
     */
    public int calcTeamHotScore(Team team, int memberCount, int maxScore) {
        return calcTeamHotScore(memberCount, getTeamMessageCount(team.getId()), team, maxScore);
    }

    /**
     * 计算团队热度得分。
     *
     * @param memberCount 成员数
     * @param messageCount 消息数
     * @param team         团队实体
     * @param maxScore     最大得分
     * @return 热度得分
     */
    public int calcTeamHotScore(int memberCount, int messageCount, Team team, int maxScore) {
        // 1. 热度不是只看成员数，而是按“成员 + 消息活跃”再除以团队存续天数做轻量衰减，
        // 防止老团队凭历史沉淀长期霸榜。
        double days = 1.0;
        if (team.getCreateTime() != null) {
            days = Math.max(1.0, java.time.Duration.between(team.getCreateTime(), LocalDateTime.now()).toDays());
        }
        double heat = (memberCount + messageCount) / days;
        return Math.min(maxScore, (int) Math.round(heat));
    }

    /**
     * 计算团队新鲜度得分。
     *
     * @param team     团队实体
     * @param maxScore 最大得分
     * @return 新鲜度得分
     */
    public int calcTeamNewScore(Team team, int maxScore) {
        // 1. 新团队获得更高分，随着创建时间变久逐步衰减。
        if (team.getCreateTime() == null) return 0;
        double hours = Math.max(1.0, java.time.Duration.between(team.getCreateTime(), LocalDateTime.now()).toHours());
        int score = (int) Math.round((24.0 / hours) * maxScore);
        return Math.max(0, Math.min(maxScore, score));
    }

    /**
     * 计算两个集合的交集数量。
     *
     * @param a 集合 A
     * @param b 集合 B
     * @return 交集元素数量
     * @param <T> 集合元素类型
     */
    public <T> int intersectionCount(Set<T> a, Set<T> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0;
        Set<T> t = new HashSet<>(a);
        t.retainAll(b);
        return t.size();
    }

    /**
     * 将用户推荐原因编码转换成文案。
     *
     * @param reason 原因编码
     * @return 推荐原因文案
     */
    public String reasonTextForUser(int reason) {
        return switch (reason) {
            case 2 -> "共同好友较多";
            case 3 -> "聊天兴趣接近";
            case 4 -> "共同团队较多";
            default -> "标签相似";
        };
    }

    /**
     * 将团队推荐原因编码转换成文案。
     *
     * @param reason 原因编码
     * @return 推荐原因文案
     */
    public String reasonTextForTeam(int reason) {
        return switch (reason) {
            case 2 -> "共同成员较多";
            case 3 -> "热门团队";
            case 4 -> "新建团队";
            default -> "标签相似";
        };
    }

    /**
     * 将标签字符串拆成集合。
     *
     * @param tags 标签字符串
     * @return 标签集合
     */
    public Set<String> splitTags(String tags) {
        // 1. 同时兼容中英文逗号，并过滤空白标签。
        if (tags == null || tags.trim().isEmpty()) return Collections.emptySet();
        return Arrays.stream(tags.replace("，", ",").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * 将用户实体转换为推荐缓存使用的轻量结构。
     *
     * @param user 用户实体
     * @return 轻量用户格式对象
     */
    public UserFormat buildUserFormat(User user) {
        // 1. 只保留推荐列表需要的展示字段，减少缓存体积。
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
