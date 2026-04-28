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
        Long currentUserId = supportService.requireLogin();
        searchType = supportService.validateSearchType(searchType);
        limit = supportService.normalizeLimit(limit);

        LambdaQueryWrapper<SearchHistory> qw = new LambdaQueryWrapper<>();
        qw.eq(SearchHistory::getUserId, currentUserId)
                .eq(SearchHistory::getSearchType, searchType)
                .eq(SearchHistory::getIsDelete, 0)
                .orderByDesc(SearchHistory::getLastSearchTime)
                .last("limit " + limit);

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
        supportService.requireLogin();
        searchType = supportService.validateSearchType(searchType);
        limit = supportService.normalizeLimit(limit);

        String cacheKey = supportService.buildHotKeywordCacheKey(searchType, limit);
        String cached = supportService.stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            return Result.ok(JSON.parseArray(cached, HotKeywordVO.class));
        }

        LambdaQueryWrapper<SearchHotKeyword> qw = new LambdaQueryWrapper<>();
        qw.eq(SearchHotKeyword::getSearchType, searchType)
                .eq(SearchHotKeyword::getIsDelete, 0)
                .orderByDesc(SearchHotKeyword::getSearchCount)
                .orderByAsc(SearchHotKeyword::getId)
                .last("limit " + limit);

        List<SearchHotKeyword> list = supportService.searchHotKeywordMapper.selectList(qw);
        List<HotKeywordVO> data = new ArrayList<>();
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
        int randomMinutes = ThreadLocalRandom.current().nextInt(5, 16);
        supportService.stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(data), 60L + randomMinutes, TimeUnit.MINUTES);
        return Result.ok(data);
    }

    public Result clearSearchHistory() {
        Long currentUserId = supportService.requireLogin();

        LambdaUpdateWrapper<SearchHistory> uw = new LambdaUpdateWrapper<>();
        uw.eq(SearchHistory::getUserId, currentUserId)
                .eq(SearchHistory::getIsDelete, 0)
                .set(SearchHistory::getIsDelete, 1);
        supportService.searchHistoryMapper.update(null, uw);

        return Result.ok("搜索历史已清空");
    }
}
