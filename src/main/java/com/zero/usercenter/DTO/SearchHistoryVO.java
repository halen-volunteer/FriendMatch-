package com.zero.usercenter.DTO;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SearchHistoryVO {
    private Long id;
    private Integer searchType;
    private String searchKeyword;
    private Integer searchCount;
    private LocalDateTime lastSearchTime;
}
