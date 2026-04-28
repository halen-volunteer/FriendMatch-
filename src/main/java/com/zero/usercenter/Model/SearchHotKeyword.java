package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 热门搜索词实体，对应表 `t_search_hot_keyword`。
 */
@Data
@TableName("t_search_hot_keyword")
public class SearchHotKeyword {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关键词 */
    private String keyword;

    /** 搜索类型（1-用户，2-团队） */
    private Integer searchType;

    /** 被搜索次数 */
    private Integer searchCount;

    /** 热度排名 */
    @TableField("`rank`")
    private Integer rank;

    /** 软删除标记（0-未删，1-已删） */
    private Integer isDelete;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
