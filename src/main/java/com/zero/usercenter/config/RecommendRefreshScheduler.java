package com.zero.usercenter.config;

import com.zero.usercenter.Service.SearchRecommendRefreshService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 推荐结果定时更新任务
 */
@Slf4j
@Component
public class RecommendRefreshScheduler {

    @Autowired
    private SearchRecommendRefreshService searchRecommendService;

    /**
     * 每天凌晨2点全量刷新推荐
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void refreshAllRecommendDaily() {
        searchRecommendService.refreshRecommendForAllUsers();
        log.info("[定时任务] 全量推荐刷新完成");
    }

    /**
     * 每小时增量刷新（当前按活跃用户实时策略可近似，先全量覆盖）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void refreshRecommendHourly() {
        searchRecommendService.refreshRecommendForAllUsers();
        log.info("[定时任务] 每小时推荐刷新完成");
    }
}
