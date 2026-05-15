package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.OssPresignDTO;
import com.zero.usercenter.DTO.Result;

/**
 * OSS 预签名上传服务接口。
 */
public interface OssService {

    /**
     * 生成预签名上传凭证和访问地址。
     *
     * @param dto 预签名请求参数，通常包含业务目录、文件名和文件类型等信息
     * @return 统一响应结果，成功时包含上传凭证与可访问地址
     */
    Result presign(OssPresignDTO dto);
}
