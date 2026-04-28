package com.zero.usercenter.Service;

/**
 * AI 内容审核服务接口
 */
public interface AiCheckService {

    /**
     * 检测文本内容是否违规
     *
     * @param content 待检测文本内容
     * @return 检测结果：0-正常，1-违规，-1-检测失败（默认放行）
     */
    int checkContent(String content);
}
