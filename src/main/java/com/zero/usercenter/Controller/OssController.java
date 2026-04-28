package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.OssPresignDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.OssService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 七牛云 OSS 预签名上传 Controller
 *
 * 使用流程（七牛云 UploadToken 模式，非阿里云 PUT 预签名）：
 * 1. 前端调用 POST /api/oss/presign，获取 uploadToken、key、fileUrl、uploadUrl
 * 2. 前端用 uploadToken 向 uploadUrl（https://up.qiniup.com）POST 上传文件
 * 3. 上传成功后，前端将 fileUrl 填入发消息 DTO（fileUrl 字段）发给后端
 *
 * 基础路径：/api/oss
 * 所有接口需在请求头携带 Authorization: {token}
 */
@RestController
@RequestMapping("/api/oss")
public class OssController {

    @Resource
    private OssService ossService;

    /**
     * 获取预签名上传 URL
     *
     * 请求体：
     * {
     *   "fileName": "设计文档.pdf",
     *   "msgType": 3,
     *   "fileSize": 2048576
     * }
     *
     * 响应：
     * {
     *   "success": true,
     *   "data": {
     *     "presignUrl": "https://...?Signature=xxx",  前端 PUT 上传地址（5分钟有效）
     *     "objectKey": "files/1001/uuid.pdf",
     *     "fileUrl": "https://friendmatch-files.oss-cn-hangzhou.aliyuncs.com/files/1001/uuid.pdf",
     *     "expireAt": 1710614700000
     *   }
     * }
     */
    /**
     * 获取预签名上传 URL
     * POST /api/oss/presign
     *
     * @param dto 包含文件名（fileName）、消息类型（msgType）、文件大小（fileSize）的请求对象
     * @return 预签名 URL、对象 Key、最终访问 URL 及过期时间
     */
    @PostMapping("/presign")
    public Result presign(@RequestBody OssPresignDTO dto) {
        return ossService.presign(dto);
    }
}
