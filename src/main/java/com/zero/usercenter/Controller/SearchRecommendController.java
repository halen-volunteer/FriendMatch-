package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.RecommendClickDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.SearchRecommendService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 搜索与推荐 Controller
 *
 * 路径说明：
 * - 搜索：/api/search/*
 * - 推荐：/api/recommend/*
 */
@RestController
public class SearchRecommendController {

    @Resource
    private SearchRecommendService searchRecommendService;

    /**
     * 搜索用户。
     *
     * @param keyword 搜索关键词
     * @param page 页码
     * @param pageSize 每页条数
     * @return 用户搜索结果
     */
    @GetMapping("/api/search/users")
    public Result searchUsers(@RequestParam String keyword,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int pageSize) {
        return searchRecommendService.searchUsers(keyword, page, pageSize);
    }

    /**
     * 搜索团队。
     *
     * @param keyword 搜索关键词
     * @param page 页码
     * @param pageSize 每页条数
     * @return 团队搜索结果
     */
    @GetMapping("/api/search/teams")
    public Result searchTeams(@RequestParam String keyword,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int pageSize) {
        return searchRecommendService.searchTeams(keyword, page, pageSize);
    }

    /**
     * 查询搜索历史。
     *
     * @param searchType 搜索类型（1-用户，2-团队）
     * @param limit 返回条数上限
     * @return 搜索历史列表
     */
    @GetMapping("/api/search/history")
    public Result getSearchHistory(@RequestParam Integer searchType,
                                   @RequestParam(defaultValue = "10") int limit) {
        return searchRecommendService.getSearchHistory(searchType, limit);
    }

    /**
     * 查询热搜关键词。
     *
     * @param searchType 搜索类型（1-用户，2-团队）
     * @param limit 返回条数上限
     * @return 热词列表
     */
    @GetMapping("/api/search/hot-keywords")
    public Result getHotKeywords(@RequestParam Integer searchType,
                                 @RequestParam(defaultValue = "10") int limit) {
        return searchRecommendService.getHotKeywords(searchType, limit);
    }

    /**
     * 清空当前用户搜索历史。
     *
     * @return 操作结果
     */
    @DeleteMapping("/api/search/history")
    public Result clearSearchHistory() {
        return searchRecommendService.clearSearchHistory();
    }

    /**
     * 搜索联想。
     *
     * @param keyword 关键词
     * @param type 联想类型（1-用户，2-团队）
     * @param limit 返回条数上限
     * @return 联想词列表
     */
    @GetMapping("/api/search/suggest")
    public Result suggestSearch(@RequestParam String keyword,
                                @RequestParam Integer type,
                                @RequestParam(defaultValue = "10") int limit) {
        return searchRecommendService.suggestSearch(keyword, type, limit);
    }

    /**
     * 获取推荐用户。
     *
     * @param limit 返回条数上限
     * @return 推荐用户列表
     */
    @GetMapping("/api/recommend/users")
    public Result getRecommendUsers(@RequestParam(defaultValue = "10") int limit) {
        return searchRecommendService.getRecommendUsers(limit);
    }

    /**
     * 获取推荐团队。
     *
     * @param limit 返回条数上限
     * @return 推荐团队列表
     */
    @GetMapping("/api/recommend/teams")
    public Result getRecommendTeams(@RequestParam(defaultValue = "10") int limit) {
        return searchRecommendService.getRecommendTeams(limit);
    }

    /**
     * 推荐点击回传。
     *
     * @param dto 推荐点击参数
     * @return 操作结果
     */
    @PostMapping("/api/recommend/click")
    public Result recordRecommendClick(@RequestBody RecommendClickDTO dto) {
        return searchRecommendService.recordRecommendClick(dto.getRecommendId(), dto.getRecommendType());
    }
}

