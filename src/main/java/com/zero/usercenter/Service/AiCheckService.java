package com.zero.usercenter.Service;

/**
 * AI 文本审核服务接口。
 */
public interface AiCheckService {

    /**
     * 审核文本内容是否违规。
     *
     * @param content 待审核的文本内容
     * @return 0 表示正常，1 表示违规，-1 表示调用失败或无法确定
     */
    int checkContent(String content);
}
