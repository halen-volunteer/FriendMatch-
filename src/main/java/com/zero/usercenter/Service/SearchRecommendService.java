package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.Result;

/**
 * 搜索与推荐服务接口。
 *
 * <p>searchType / type 约定：</p>
 * <ul>
 *   <li>1：用户</li>
 *   <li>2：团队</li>
 * </ul>
 */
public interface SearchRecommendService {

    /**
     * 用户搜索（模糊匹配 + 分页）。
     *
     * @param keyword 搜索关键词
     * @param page 页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 用户搜索结果
     */
    Result searchUsers(String keyword, int page, int pageSize);

    /**
     * 团队搜索（模糊匹配 + 分页）。
     *
     * @param keyword 搜索关键词
     * @param page 页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 团队搜索结果
     */
    Result searchTeams(String keyword, int page, int pageSize);

    /**
     * 获取当前用户搜索历史。
     *
     * @param searchType 搜索类型（1-用户，2-团队）
     * @param limit 返回条数上限
     * @return 搜索历史列表
     */
    Result getSearchHistory(Integer searchType, int limit);

    /**
     * 获取热搜关键词。
     *
     * @param searchType 搜索类型（1-用户，2-团队）
     * @param limit 返回条数上限
     * @return 热搜关键词列表
     */
    Result getHotKeywords(Integer searchType, int limit);

    /**
     * 清空当前用户搜索历史。
     *
     * @return 清空结果
     */
    Result clearSearchHistory();

    /**
     * 获取推荐用户列表。
     *
     * @param limit 返回条数上限
     * @return 推荐用户数据
     */
    Result getRecommendUsers(int limit);

    /**
     * 获取推荐团队列表。
     *
     * @param limit 返回条数上限
     * @return 推荐团队数据
     */
    Result getRecommendTeams(int limit);

    /**
     * 记录推荐点击行为。
     *
     * @param recommendId 推荐记录ID
     * @param recommendType 推荐类型（1-用户推荐，2-团队推荐）
     * @return 记录结果
     */
    Result recordRecommendClick(Long recommendId, Integer recommendType);

    /**
     * 搜索联想建议。
     *
     * @param keyword 关键词前缀
     * @param type 联想类型（1-用户，2-团队）
     * @param limit 返回条数上限
     * @return 联想建议列表
     */
    Result suggestSearch(String keyword, Integer type, int limit);
}
