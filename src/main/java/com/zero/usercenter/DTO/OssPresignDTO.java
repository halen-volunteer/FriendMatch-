package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * OSS 预签名上传请求 DTO
 */
@Data
public class OssPresignDTO {

    /**
     * 文件原始名称（含扩展名），如 "设计文档.pdf"
     * 用于校验文件类型和生成存储路径
     */
    private String fileName;

    /**
     * 消息类型：2-图片，3-文件，4-表情包
     * 用于分目录存储和大小限制校验
     */
    private Integer msgType;

    /**
     * 文件大小（字节），可选
     * 用于超出限制时提前拒绝
     */
    private Long fileSize;
}
