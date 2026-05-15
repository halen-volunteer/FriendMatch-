package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.OssPresignDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.OssService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 七牛云上传凭证下发 Controller。
 *
 * 当前采用七牛 UploadToken 直传模式，不是 PUT 预签名 URL 模式：
 * 1. 前端调用 `/api/oss/presign` 获取 uploadToken、key、fileUrl、uploadUrl。
 * 2. 前端携带 uploadToken 直接上传到七牛。
 * 3. 上传成功后再把 fileUrl 作为业务消息内容的一部分回传后端。
 */
@RestController
@RequestMapping("/api/oss")
public class OssController {

    @Resource
    private OssService ossService;

    /**
     * 获取七牛上传凭证。
     *
     * @param dto 上传文件的基础描述，例如文件名、大小和消息类型
     * @return 上传凭证、文件 key、访问地址、上传域名等直传参数
     */
    @PostMapping("/presign")
    public Result presign(@RequestBody OssPresignDTO dto) {
        // 凭证生成会在 service 层完成文件类型校验、key 生成和七牛上传参数组装。
        return ossService.presign(dto);
    }
}
