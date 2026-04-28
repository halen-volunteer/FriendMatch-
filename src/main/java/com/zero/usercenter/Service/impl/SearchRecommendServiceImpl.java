package com.zero.usercenter.Service.impl;

import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.SearchRecommendRefreshService;
import com.zero.usercenter.Service.SearchRecommendService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class SearchRecommendServiceImpl implements SearchRecommendService, SearchRecommendRefreshService {

    @Resource
    private SearchQueryService searchQueryService;

    @Resource
    private SearchHistoryService searchHistoryService;

    @Resource
    private SearchRecommendationService searchRecommendationService;

    @Override
    public Result searchUsers(String keyword, int page, int pageSize) {
        return searchQueryService.searchUsers(keyword, page, pageSize);
    }

    @Override
    public Result searchTeams(String keyword, int page, int pageSize) {
        return searchQueryService.searchTeams(keyword, page, pageSize);
    }

    @Override
    public Result getSearchHistory(Integer searchType, int limit) {
        return searchHistoryService.getSearchHistory(searchType, limit);
    }

    @Override
    public Result getHotKeywords(Integer searchType, int limit) {
        return searchHistoryService.getHotKeywords(searchType, limit);
    }

    @Override
    public Result clearSearchHistory() {
        return searchHistoryService.clearSearchHistory();
    }

    @Override
    public Result suggestSearch(String keyword, Integer type, int limit) {
        return searchQueryService.suggestSearch(keyword, type, limit);
    }

    @Override
    public Result getRecommendUsers(int limit) {
        return searchRecommendationService.getRecommendUsers(limit);
    }

    @Override
    public Result getRecommendTeams(int limit) {
        return searchRecommendationService.getRecommendTeams(limit);
    }

    @Override
    public Result recordRecommendClick(Long recommendId, Integer recommendType) {
        return searchRecommendationService.recordRecommendClick(recommendId, recommendType);
    }

    @Override
    public void refreshRecommendForUser(Long userId) {
        searchRecommendationService.refreshRecommendForUser(userId);
    }

    @Override
    public void refreshRecommendForAllUsers() {
        searchRecommendationService.refreshRecommendForAllUsers();
    }
}
