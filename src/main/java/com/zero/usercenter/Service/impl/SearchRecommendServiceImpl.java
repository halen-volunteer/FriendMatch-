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

    // 这里保持为轻量门面层，统一聚合搜索查询、搜索历史和推荐刷新入口，
    // 便于控制器只依赖一个服务接口，而具体能力继续拆在子服务中演进。
    @Override
    public Result searchUsers(String keyword, int page, int pageSize) {
        // 用户搜索入口直接下沉到查询子服务，保持当前类只做能力聚合。
        return searchQueryService.searchUsers(keyword, page, pageSize);
    }

    @Override
    public Result searchTeams(String keyword, int page, int pageSize) {
        // 团队搜索与用户搜索共用同一门面，但实际实现拆分在独立查询服务中。
        return searchQueryService.searchTeams(keyword, page, pageSize);
    }

    @Override
    public Result getSearchHistory(Integer searchType, int limit) {
        // 搜索历史读取交给历史子服务，便于后续单独演进缓存或统计逻辑。
        return searchHistoryService.getSearchHistory(searchType, limit);
    }

    @Override
    public Result getHotKeywords(Integer searchType, int limit) {
        // 热词计算和缓存策略集中在历史子服务中，门面层不持有实现细节。
        return searchHistoryService.getHotKeywords(searchType, limit);
    }

    @Override
    public Result clearSearchHistory() {
        // 清空历史同样走历史子服务，避免控制器感知多个底层实现。
        return searchHistoryService.clearSearchHistory();
    }

    @Override
    public Result suggestSearch(String keyword, Integer type, int limit) {
        // 联想词本质属于查询能力，因此仍然复用查询子服务。
        return searchQueryService.suggestSearch(keyword, type, limit);
    }

    @Override
    public Result getRecommendUsers(int limit) {
        // 推荐用户和推荐团队都放在推荐子服务中，便于后续替换推荐算法。
        return searchRecommendationService.getRecommendUsers(limit);
    }

    @Override
    public Result getRecommendTeams(int limit) {
        // 团队推荐仍然通过统一推荐子服务输出。
        return searchRecommendationService.getRecommendTeams(limit);
    }

    @Override
    public Result recordRecommendClick(Long recommendId, Integer recommendType) {
        // 点击回传只转发，不在门面层沉淀任何推荐统计逻辑。
        return searchRecommendationService.recordRecommendClick(recommendId, recommendType);
    }

    @Override
    public void refreshRecommendForUser(Long userId) {
        // 定向刷新某个用户的推荐结果时，直接委托推荐子服务执行。
        searchRecommendationService.refreshRecommendForUser(userId);
    }

    @Override
    public void refreshRecommendForAllUsers() {
        // 全量刷新入口也保持薄封装，方便定时任务只依赖当前门面接口。
        searchRecommendationService.refreshRecommendForAllUsers();
    }
}
