package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.OssPresignDTO;
import com.zero.usercenter.DTO.Result;

/**
 * OSS 预签名上传服务接口
 */
public interface OssService {

    /**
     * 生成预签名上传 URL
     *
     * @param dto 包含文件名、消息类型（msgType）、文件大小（fileSize）的请求对象
     * @return 预签名 URL、对象 Key、最终访问 URL 及过期时间
     */
    Result presign(OssPresignDTO dto);
}
