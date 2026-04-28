package com.zero.usercenter.DTO;

import lombok.Data;

@Data
public class HotKeywordVO {
    private Long id;
    private String keyword;
    private Integer searchType;
    private Integer searchCount;
    private Integer rank;
}
