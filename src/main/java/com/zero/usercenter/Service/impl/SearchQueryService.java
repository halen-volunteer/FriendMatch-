package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.DTO.TeamSearchVO;
import com.zero.usercenter.DTO.UserSearchVO;
import com.zero.usercenter.Model.Team;
import com.zero.usercenter.Model.User;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 搜索查询服务。
 * 负责用户搜索、团队搜索和联想词查询，复用搜索推荐支撑服务提供的校验、关系过滤和打分能力。
 */
@Service
public class SearchQueryService {

    @Resource
    private SearchRecommendSupportService supportService;

    /**
     * 搜索用户。
     *
     * @param keyword  搜索关键词
     * @param page     页码
     * @param pageSize 每页大小
     * @return 用户搜索结果
     */
    public Result searchUsers(String keyword, int page, int pageSize) {
        // 1. 统一做登录校验、关键词清洗和分页参数归一化。
        Long currentUserId = supportService.requireLogin();
        keyword = supportService.normalizeKeyword(keyword);
        page = supportService.normalizePage(page);
        pageSize = supportService.normalizePageSize(pageSize);

        // 2. 先从用户表按昵称、账号、标签做模糊匹配；如果关键词像邮箱，则一并纳入邮箱命中。
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        String finalKeyword = keyword;
        boolean emailKeyword = supportService.looksLikeEmailKeyword(finalKeyword);
        qw.eq(User::getIsDelete, 0)
                .and(w -> w.like(User::getUserNickname, finalKeyword)
                        .or().like(User::getUserAccount, finalKeyword)
                        .or().like(User::getUserTags, finalKeyword));
        if (emailKeyword) {
            qw.or(w -> w.eq(User::getIsDelete, 0).like(User::getUserEmail, finalKeyword));
        }
        qw
                .orderByDesc(User::getId);

        // 3. 再过滤掉自己、黑名单、隐私不可见用户；邮箱搜索还要额外检查对方是否允许被邮箱搜索。
        List<User> users = supportService.userMapper.selectList(qw);
        Set<Long> candidateUserIds = users.stream().map(User::getId).filter(id -> !id.equals(currentUserId)).collect(Collectors.toSet());
        Set<Long> blacklistedIds = supportService.getBlacklistedUserIds(currentUserId, candidateUserIds);
        List<UserSearchVO> all = users.stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .filter(u -> !blacklistedIds.contains(u.getId()))
                .filter(u -> supportService.canViewUserInfo(currentUserId, u))
                .filter(u -> !emailKeyword || supportService.canSearchByEmail(u))
                .map(u -> supportService.buildUserSearchItem(u, finalKeyword))
                .sorted((a, b) -> Integer.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                .collect(Collectors.toList());

        // 4. 搜索成功后记录关键词，用于搜索历史和推荐特征积累。
        supportService.persistSearch(currentUserId, SearchRecommendSupportService.SEARCH_USER, keyword);

        // 5. 最后在内存结果集上分页，返回排序后的最终列表。
        int from = (page - 1) * pageSize;
        if (from >= all.size()) return Result.ok(Collections.emptyList(), (long) all.size());
        int to = Math.min(from + pageSize, all.size());
        return Result.ok(all.subList(from, to), (long) all.size());
    }

    /**
     * 搜索团队。
     *
     * @param keyword  搜索关键词
     * @param page     页码
     * @param pageSize 每页大小
     * @return 团队搜索结果
     */
    public Result searchTeams(String keyword, int page, int pageSize) {
        // 1. 统一做登录校验、关键词清洗和分页参数归一化。
        Long currentUserId = supportService.requireLogin();
        keyword = supportService.normalizeKeyword(keyword);
        page = supportService.normalizePage(page);
        pageSize = supportService.normalizePageSize(pageSize);

        // 2. 从团队表按名称、标签和简介做模糊匹配，得到候选团队。
        LambdaQueryWrapper<Team> qw = new LambdaQueryWrapper<>();
        String finalKeyword = keyword;
        qw.eq(Team::getIsDelete, 0)
                .and(w -> w.like(Team::getTeamName, finalKeyword)
                        .or().like(Team::getTeamTags, finalKeyword)
                        .or().like(Team::getTeamIntro, finalKeyword))
                .orderByDesc(Team::getId);

        // 3. 补齐当前用户是否已加入、团队成员数等展示信息，再计算相似度排序。
        List<Team> teams = supportService.teamMapper.selectList(qw);
        Set<Long> joinedTeamIds = supportService.getUserTeamIds(currentUserId);
        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        java.util.Map<Long, Integer> memberCountMap = supportService.getTeamMemberCountMap(teamIds);
        List<TeamSearchVO> all = teams.stream()
                .map(t -> supportService.buildTeamSearchItem(
                        t,
                        finalKeyword,
                        memberCountMap.getOrDefault(t.getId(), 0),
                        joinedTeamIds.contains(t.getId()) ? 1 : 0
                ))
                .sorted((a, b) -> Integer.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                .collect(Collectors.toList());

        // 4. 记录搜索行为，供历史记录和推荐系统复用。
        supportService.persistSearch(currentUserId, SearchRecommendSupportService.SEARCH_TEAM, keyword);

        // 5. 最后对排序后的结果做分页截取。
        int from = (page - 1) * pageSize;
        if (from >= all.size()) return Result.ok(Collections.emptyList(), (long) all.size());
        int to = Math.min(from + pageSize, all.size());
        return Result.ok(all.subList(from, to), (long) all.size());
    }

    /**
     * 获取联想搜索词。
     *
     * @param keyword 搜索关键词前缀
     * @param type    联想类型
     * @param limit   返回条数
     * @return 联想词列表
     */
    public Result suggestSearch(String keyword, Integer type, int limit) {
        // 1. 联想词也要求登录，并统一校验搜索类型、关键词和数量上限。
        supportService.requireLogin();
        type = supportService.validateSuggestType(type);
        keyword = supportService.normalizeKeyword(keyword);
        limit = supportService.normalizeLimit(limit);
        final String finalKeyword = keyword;
        final int finalLimit = limit;

        List<String> suggestions;
        if (type == SearchRecommendSupportService.SEARCH_USER) {
            // 2. 用户联想优先返回昵称，其次回退账号名，最终做去重和截断。
            LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
            qw.eq(User::getIsDelete, 0)
                    .and(w -> w.like(User::getUserNickname, finalKeyword)
                            .or().like(User::getUserAccount, finalKeyword))
                    .orderByDesc(User::getId)
                    .last("limit " + finalLimit);
            suggestions = supportService.userMapper.selectList(qw).stream()
                    .map(u -> {
                        String nick = Optional.ofNullable(u.getUserNickname()).orElse("").trim();
                        String acc = Optional.ofNullable(u.getUserAccount()).orElse("").trim();
                        return !nick.isEmpty() ? nick : acc;
                    })
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .limit(limit)
                    .collect(Collectors.toList());
        } else {
            // 3. 团队联想直接返回团队名称，同样做去重和数量控制。
            LambdaQueryWrapper<Team> qw = new LambdaQueryWrapper<>();
            qw.eq(Team::getIsDelete, 0)
                    .and(w -> w.like(Team::getTeamName, finalKeyword)
                            .or().like(Team::getTeamTags, finalKeyword))
                    .orderByDesc(Team::getId)
                    .last("limit " + finalLimit);
            suggestions = supportService.teamMapper.selectList(qw).stream()
                    .map(t -> Optional.ofNullable(t.getTeamName()).orElse("").trim())
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        // 4. 返回去重、截断后的联想词列表。
        return Result.ok(suggestions);
    }
}
