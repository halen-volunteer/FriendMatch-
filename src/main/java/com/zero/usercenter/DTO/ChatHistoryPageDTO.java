package com.zero.usercenter.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 聊天历史游标分页结果。
 * 进入会话时只拉取一小页正文，后续继续上滑时再基于 nextCursor 加载更早的历史消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryPageDTO {

    /**
     * 当前页消息列表，时间顺序为从旧到新，便于前端直接渲染。
     */
    private List<Map<String, Object>> records;

    /**
     * 下一次继续向前翻页时使用的游标。
     * 约定为“当前页最早一条消息的 ID”，下一页查询条件为 id < nextCursor。
     */
    private Long nextCursor;

    /**
     * 是否还有更早的历史消息可加载。
     */
    private Boolean hasMore;
}
