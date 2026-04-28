package com.zero.usercenter.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 推荐点击行为上报 DTO。
 */
@Data
public class RecommendClickDTO {

    /** 推荐记录ID。 */
    @JsonProperty("recommend_id")
    private Long recommendId;

    /** 推荐类型：1-用户推荐，2-团队推荐。 */
    @JsonProperty("recommend_type")
    private Integer recommendType;
}
