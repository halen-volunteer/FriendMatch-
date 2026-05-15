package com.zero.usercenter.Service;

/**
 * 搜索推荐刷新能力接口。
 */
public interface SearchRecommendRefreshService {

    /**
     * 刷新指定用户推荐结果。
     *
     * @param userId 需要刷新推荐结果的用户 ID
     */
    void refreshRecommendForUser(Long userId);

    /**
     * 刷新全部用户推荐结果。
     */
    void refreshRecommendForAllUsers();
}
