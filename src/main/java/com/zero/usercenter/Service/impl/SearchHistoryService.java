package com.zero.usercenter.Service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zero.usercenter.DTO.HotKeywordVO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.DTO.SearchHistoryVO;
import com.zero.usercenter.Model.SearchHistory;
import com.zero.usercenter.Model.SearchHotKeyword;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SearchHistoryService {

    @Resource
    private SearchRecommendSupportService supportService;

    public Result getSearchHistory(Integer searchType, int limit) {
        // 1. 先统一校验登录态、搜索类型和返回条数上限。
        Long currentUserId = supportService.requireLogin();
        searchType = supportService.validateSearchType(searchType);
        limit = supportService.normalizeLimit(limit);

        // 2. 按当前用户和搜索类型查询最近搜索历史，只返回未删除记录。
        LambdaQueryWrapper<SearchHistory> qw = new LambdaQueryWrapper<>();
        qw.eq(SearchHistory::getUserId, currentUserId)
                .eq(SearchHistory::getSearchType, searchType)
                .eq(SearchHistory::getIsDelete, 0)
                .orderByDesc(SearchHistory::getLastSearchTime)
                .last("limit " + limit);

        // 3. 把数据库实体转换成前端更稳定的历史视图对象。
        List<SearchHistoryVO> data = supportService.searchHistoryMapper.selectList(qw).stream().map(h -> {
            SearchHistoryVO vo = new SearchHistoryVO();
            vo.setId(h.getId());
            vo.setSearchType(h.getSearchType());
            vo.setSearchKeyword(h.getSearchKeyword());
            vo.setSearchCount(h.getSearchCount());
            vo.setLastSearchTime(h.getLastSearchTime());
            return vo;
        }).collect(Collectors.toList());

        return Result.ok(data);
    }

    public Result getHotKeywords(Integer searchType, int limit) {
        // 1. 热词查询也先校验登录、类型和数量，保持参数处理一致。
        supportService.requireLogin();
        searchType = supportService.validateSearchType(searchType);
        limit = supportService.normalizeLimit(limit);

        // 2. 先读 Redis 缓存，避免每次都打数据库做排行榜查询。
        String cacheKey = supportService.buildHotKeywordCacheKey(searchType, limit);
        String cached = supportService.stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            return Result.ok(JSON.parseArray(cached, HotKeywordVO.class));
        }

        // 3. 缓存未命中时，从热词表按搜索次数降序查询排行榜。
        LambdaQueryWrapper<SearchHotKeyword> qw = new LambdaQueryWrapper<>();
        qw.eq(SearchHotKeyword::getSearchType, searchType)
                .eq(SearchHotKeyword::getIsDelete, 0)
                .orderByDesc(SearchHotKeyword::getSearchCount)
                .orderByAsc(SearchHotKeyword::getId)
                .last("limit " + limit);

        List<SearchHotKeyword> list = supportService.searchHotKeywordMapper.selectList(qw);
        List<HotKeywordVO> data = new ArrayList<>();
        // 4. 组装排名结果，给前端一个直接可展示的榜单结构。
        for (int i = 0; i < list.size(); i++) {
            SearchHotKeyword h = list.get(i);
            HotKeywordVO vo = new HotKeywordVO();
            vo.setId(h.getId());
            vo.setKeyword(h.getKeyword());
            vo.setSearchType(h.getSearchType());
            vo.setSearchCount(h.getSearchCount());
            vo.setRank(i + 1);
            data.add(vo);
        }
        // 5. 用带随机抖动的 TTL 回写缓存，避免多个 key 同时雪崩失效。
        int randomMinutes = ThreadLocalRandom.current().nextInt(5, 16);
        supportService.stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(data), 60L + randomMinutes, TimeUnit.MINUTES);
        return Result.ok(data);
    }

    public Result clearSearchHistory() {
        // 清空搜索历史采用逻辑删除，既能让前台看不到，也保留历史数据便于统计追溯。
        Long currentUserId = supportService.requireLogin();

        LambdaUpdateWrapper<SearchHistory> uw = new LambdaUpdateWrapper<>();
        uw.eq(SearchHistory::getUserId, currentUserId)
                .eq(SearchHistory::getIsDelete, 0)
                .set(SearchHistory::getIsDelete, 1);
        supportService.searchHistoryMapper.update(null, uw);

        return Result.ok("搜索历史已清空");
    }
}
