package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.Result;

/**
 * 搜索与推荐服务接口。
 * 其中 `searchType/type` 统一约定为 `1-用户`、`2-团队`。
 */
public interface SearchRecommendService {

    /**
     * 用户搜索。
     *
     * @param keyword 搜索关键词
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含用户搜索结果
     */
    Result searchUsers(String keyword, int page, int pageSize);

    /**
     * 团队搜索。
     *
     * @param keyword 搜索关键词
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含团队搜索结果
     */
    Result searchTeams(String keyword, int page, int pageSize);

    /**
     * 获取当前用户搜索历史。
     *
     * @param searchType 搜索类型，1 表示用户，2 表示团队
     * @param limit 返回记录条数上限
     * @return 统一响应结果，成功时包含搜索历史列表
     */
    Result getSearchHistory(Integer searchType, int limit);

    /**
     * 获取热搜关键词。
     *
     * @param searchType 搜索类型，1 表示用户，2 表示团队
     * @param limit 返回关键词条数上限
     * @return 统一响应结果，成功时包含热搜关键词列表
     */
    Result getHotKeywords(Integer searchType, int limit);

    /**
     * 清空当前用户搜索历史。
     *
     * @return 统一响应结果，成功时表示搜索历史已清空
     */
    Result clearSearchHistory();

    /**
     * 获取推荐用户列表。
     *
     * @param limit 返回推荐结果条数上限
     * @return 统一响应结果，成功时包含推荐用户列表
     */
    Result getRecommendUsers(int limit);

    /**
     * 获取推荐团队列表。
     *
     * @param limit 返回推荐结果条数上限
     * @return 统一响应结果，成功时包含推荐团队列表
     */
    Result getRecommendTeams(int limit);

    /**
     * 记录推荐点击行为。
     *
     * @param recommendId 推荐记录 ID
     * @param recommendType 推荐类型，通常 1 表示用户，2 表示团队
     * @return 统一响应结果，成功时表示点击行为已记录
     */
    Result recordRecommendClick(Long recommendId, Integer recommendType);

    /**
     * 搜索联想建议。
     *
     * @param keyword 搜索关键词前缀
     * @param type 联想类型，1 表示用户，2 表示团队
     * @param limit 返回建议条数上限
     * @return 统一响应结果，成功时包含联想建议列表
     */
    Result suggestSearch(String keyword, Integer type, int limit);
}
