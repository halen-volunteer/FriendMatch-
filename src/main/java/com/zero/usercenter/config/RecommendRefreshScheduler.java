package com.zero.usercenter.config;

import com.zero.usercenter.Service.SearchRecommendRefreshService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 推荐结果刷新任务。
 * 通过定时重建推荐结果，避免把复杂推荐计算压到用户实时请求上。
 */
@Slf4j
@Component
public class RecommendRefreshScheduler {

    @Autowired
    private SearchRecommendRefreshService searchRecommendService;

    /**
     * 每天凌晨执行一次全量刷新。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void refreshAllRecommendDaily() {
        // 凌晨做一次全量刷新，尽量避开白天用户访问高峰。
        searchRecommendService.refreshRecommendForAllUsers();
        log.info("[定时任务] 全量推荐刷新完成");
    }

    /**
     * 每小时执行一次兜底刷新。
     * 当前实现仍按全量覆盖处理，优先保证推荐结果不过期。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void refreshRecommendHourly() {
        // 小时级兜底刷新用于缩短画像变化到推荐结果生效之间的延迟。
        searchRecommendService.refreshRecommendForAllUsers();
        log.info("[定时任务] 每小时推荐刷新完成");
    }
}
