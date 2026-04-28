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

@Service
public class SearchQueryService {

    @Resource
    private SearchRecommendSupportService supportService;

    public Result searchUsers(String keyword, int page, int pageSize) {
        Long currentUserId = supportService.requireLogin();
        keyword = supportService.normalizeKeyword(keyword);
        page = supportService.normalizePage(page);
        pageSize = supportService.normalizePageSize(pageSize);

        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        String finalKeyword = keyword;
        qw.eq(User::getIsDelete, 0)
                .and(w -> w.like(User::getUserNickname, finalKeyword)
                        .or().like(User::getUserAccount, finalKeyword)
                        .or().like(User::getUserTags, finalKeyword))
                .orderByDesc(User::getId);

        List<User> users = supportService.userMapper.selectList(qw);
        Set<Long> candidateUserIds = users.stream().map(User::getId).filter(id -> !id.equals(currentUserId)).collect(Collectors.toSet());
        Set<Long> blacklistedIds = supportService.getBlacklistedUserIds(currentUserId, candidateUserIds);
        List<UserSearchVO> all = users.stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .filter(u -> !blacklistedIds.contains(u.getId()))
                .map(u -> supportService.buildUserSearchItem(u, finalKeyword))
                .sorted((a, b) -> Integer.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                .collect(Collectors.toList());

        supportService.persistSearch(currentUserId, SearchRecommendSupportService.SEARCH_USER, keyword);

        int from = (page - 1) * pageSize;
        if (from >= all.size()) return Result.ok(Collections.emptyList(), (long) all.size());
        int to = Math.min(from + pageSize, all.size());
        return Result.ok(all.subList(from, to), (long) all.size());
    }

    public Result searchTeams(String keyword, int page, int pageSize) {
        Long currentUserId = supportService.requireLogin();
        keyword = supportService.normalizeKeyword(keyword);
        page = supportService.normalizePage(page);
        pageSize = supportService.normalizePageSize(pageSize);

        LambdaQueryWrapper<Team> qw = new LambdaQueryWrapper<>();
        String finalKeyword = keyword;
        qw.eq(Team::getIsDelete, 0)
                .and(w -> w.like(Team::getTeamName, finalKeyword)
                        .or().like(Team::getTeamTags, finalKeyword)
                        .or().like(Team::getTeamIntro, finalKeyword))
                .orderByDesc(Team::getId);

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

        supportService.persistSearch(currentUserId, SearchRecommendSupportService.SEARCH_TEAM, keyword);

        int from = (page - 1) * pageSize;
        if (from >= all.size()) return Result.ok(Collections.emptyList(), (long) all.size());
        int to = Math.min(from + pageSize, all.size());
        return Result.ok(all.subList(from, to), (long) all.size());
    }

    public Result suggestSearch(String keyword, Integer type, int limit) {
        supportService.requireLogin();
        type = supportService.validateSuggestType(type);
        keyword = supportService.normalizeKeyword(keyword);
        limit = supportService.normalizeLimit(limit);
        final String finalKeyword = keyword;
        final int finalLimit = limit;

        List<String> suggestions;
        if (type == SearchRecommendSupportService.SEARCH_USER) {
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

        return Result.ok(suggestions);
    }
}
