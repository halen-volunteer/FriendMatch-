package com.zero.usercenter.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zero.usercenter.Mapper.SearchHotKeywordMapper;
import com.zero.usercenter.Model.SearchHotKeyword;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 搜索热词定时维护任务。
 * 在无独立搜索引擎的前提下，定时把搜索次数转换成榜单排名。
 */
@Slf4j
@Component
public class SearchHotKeywordScheduler {

    /** 热词榜单缓存前缀，格式：search:hot:{searchType}:{limit}。 */
    private static final String HOT_KEYWORDS_CACHE_KEY = "search:hot:";

    @Resource
    private SearchHotKeywordMapper searchHotKeywordMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 每小时刷新一次热词排名，并清理常用榜单缓存。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void refreshHotKeywordRank() {
        // 1. 分别刷新用户搜索和团队搜索两类热词排名。
        refreshByType(1);
        refreshByType(2);
        // 只清理最常用的榜单缓存，降低下次读取时命中旧排名的概率。
        stringRedisTemplate.delete(HOT_KEYWORDS_CACHE_KEY + "1:10");
        stringRedisTemplate.delete(HOT_KEYWORDS_CACHE_KEY + "2:10");
        log.info("[定时任务] 热词 rank 已刷新");
    }

    /**
     * 按搜索类型重算热词排名。
     *
     * @param searchType 搜索类型：1-用户搜索，2-团队搜索
     */
    private void refreshByType(int searchType) {
        // 1. 按搜索次数倒序、主键升序查出当前类型的热词记录。
        LambdaQueryWrapper<SearchHotKeyword> qw = new LambdaQueryWrapper<>();
        qw.eq(SearchHotKeyword::getSearchType, searchType)
                .eq(SearchHotKeyword::getIsDelete, 0)
                .orderByDesc(SearchHotKeyword::getSearchCount)
                .orderByAsc(SearchHotKeyword::getId);
        List<SearchHotKeyword> list = searchHotKeywordMapper.selectList(qw);

        for (int i = 0; i < list.size(); i++) {
            SearchHotKeyword row = list.get(i);
            int rank = i + 1;
            // 2. 排名没有变化时跳过更新，避免产生无意义写操作。
            if (row.getRank() != null && row.getRank() == rank) {
                continue;
            }

            // 3. 只更新发生变化的记录，降低数据库写压力。
            LambdaUpdateWrapper<SearchHotKeyword> uw = new LambdaUpdateWrapper<>();
            uw.eq(SearchHotKeyword::getId, row.getId())
                    .set(SearchHotKeyword::getRank, rank);
            searchHotKeywordMapper.update(null, uw);
        }
    }
}
