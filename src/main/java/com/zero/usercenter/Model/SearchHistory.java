package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 搜索历史实体，对应表 `t_search_history`。
 */
@Data
@TableName("t_search_history")
public class SearchHistory {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 搜索类型（1-用户，2-团队） */
    private Integer searchType;

    /** 搜索关键词 */
    private String searchKeyword;

    /** 搜索次数 */
    private Integer searchCount;

    /** 最近搜索时间 */
    private LocalDateTime lastSearchTime;

    /** 软删除标记（0-未删，1-已删） */
    private Integer isDelete;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
