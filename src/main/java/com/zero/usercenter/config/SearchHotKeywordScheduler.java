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
 * 搜索热词定时维护任务（无 ES 方案）
 */
@Slf4j
@Component
public class SearchHotKeywordScheduler {

    private static final String HOT_KEYWORDS_CACHE_KEY = "search:hot:";

    @Resource
    private SearchHotKeywordMapper searchHotKeywordMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 每小时刷新一次 rank（按搜索次数倒序）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void refreshHotKeywordRank() {
        refreshByType(1);
        refreshByType(2);
        // 常用 limit 缓存失效
        stringRedisTemplate.delete(HOT_KEYWORDS_CACHE_KEY + "1:10");
        stringRedisTemplate.delete(HOT_KEYWORDS_CACHE_KEY + "2:10");
        log.info("[定时任务] 热词 rank 已刷新");
    }

    private void refreshByType(int searchType) {
        LambdaQueryWrapper<SearchHotKeyword> qw = new LambdaQueryWrapper<>();
        qw.eq(SearchHotKeyword::getSearchType, searchType)
                .eq(SearchHotKeyword::getIsDelete, 0)
                .orderByDesc(SearchHotKeyword::getSearchCount)
                .orderByAsc(SearchHotKeyword::getId);
        List<SearchHotKeyword> list = searchHotKeywordMapper.selectList(qw);

        for (int i = 0; i < list.size(); i++) {
            SearchHotKeyword row = list.get(i);
            int rank = i + 1;
            if (row.getRank() != null && row.getRank() == rank) continue;

            LambdaUpdateWrapper<SearchHotKeyword> uw = new LambdaUpdateWrapper<>();
            uw.eq(SearchHotKeyword::getId, row.getId())
                    .set(SearchHotKeyword::getRank, rank);
            searchHotKeywordMapper.update(null, uw);
        }
    }
}
