package com.zero.usercenter.Service.impl;

import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.zero.usercenter.DTO.OssPresignDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.OssService;
import com.zero.usercenter.config.OssConfig;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 七牛云 OSS 预签名上传服务实现
 *
 * 七牛云上传流程（与阿里云不同）：
 * 1. 后端生成 UploadToken（上传凭证），有效期内前端可直接上传
 * 2. 前端使用 UploadToken 通过七牛上传接口（https://up.qiniup.com）POST 上传文件
 * 3. 上传成功后，前端将 fileUrl（urlPrefix + key）传给聊天发送接口
 *
 * 注意：七牛云公开空间上传使用 UploadToken，不使用 PUT 预签名 URL
 * 前端上传接口：POST https://up.qiniup.com（华东）或对应区域上传域名
 */
@Service
public class OssServiceImpl implements OssService {

    @Resource
    private Auth qiniuAuth;

    @Resource
    private UploadManager uploadManager;

    @Resource
    private OssConfig ossConfig;

    // 图片允许的扩展名
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
    // 文件允许的扩展名
    private static final Set<String> FILE_EXT = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "zip", "rar", "7z", "mp3", "mp4", "csv");
    // 表情包允许的扩展名
    private static final Set<String> EMOJI_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    // 大小限制
    private static final long IMAGE_MAX_SIZE = 10L * 1024 * 1024;   // 10 MB
    private static final long FILE_MAX_SIZE  = 1024L * 1024 * 1024;  // 1 GB
    private static final long EMOJI_MAX_SIZE = 2L * 1024 * 1024;    // 2 MB

    @Override
    public Result presign(OssPresignDTO dto) {
        // 1. 先确认登录和 OSS 基础配置完整，避免生成出不可用凭证。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        if (ossConfig.getAccessKeyId() == null || ossConfig.getAccessKeyId().isBlank()) {
            return Result.fail("OSS 未配置 accessKeyId");
        }
        if (ossConfig.getAccessKeySecret() == null || ossConfig.getAccessKeySecret().isBlank()) {
            return Result.fail("OSS 未配置 accessKeySecret");
        }
        if (ossConfig.getBucketName() == null || ossConfig.getBucketName().isBlank()) {
            return Result.fail("OSS 未配置 bucketName");
        }
        if (ossConfig.getUrlPrefix() == null || ossConfig.getUrlPrefix().isBlank()) {
            return Result.fail("OSS 未配置 urlPrefix");
        }
        if (ossConfig.getAccessKeyId().equals(ossConfig.getAccessKeySecret())) {
            return Result.fail("OSS accessKeySecret 配置异常，请填写正确的七牛 SecretKey");
        }

        // 2. 校验文件名和消息类型，消息类型决定允许上传的文件范围。
        if (dto.getFileName() == null || dto.getFileName().isBlank())
            return Result.fail("文件名不能为空");
        if (dto.getMsgType() == null || dto.getMsgType() < 2 || dto.getMsgType() > 4)
            return Result.fail("msgType 无效（2-图片，3-文件，4-表情包）");

        String originalName = dto.getFileName().trim();
        String ext = extractExt(originalName).toLowerCase();
        if (ext.isEmpty()) return Result.fail("文件名缺少扩展名");

        // 3. 按 msgType 校验扩展名和文件大小，防止前端越权上传不允许的内容。
        switch (dto.getMsgType()) {
            case 2 -> {
                if (!IMAGE_EXT.contains(ext)) return Result.fail("不支持的图片格式，允许：" + IMAGE_EXT);
                if (dto.getFileSize() != null && dto.getFileSize() > IMAGE_MAX_SIZE)
                    return Result.fail("图片大小不能超过 10MB");
            }
            case 3 -> {
                if (!FILE_EXT.contains(ext)) return Result.fail("不支持的文件格式，允许：" + FILE_EXT);
                if (dto.getFileSize() != null && dto.getFileSize() > FILE_MAX_SIZE)
                    return Result.fail("文件大小不能超过 1GB");
            }
            case 4 -> {
                if (!EMOJI_EXT.contains(ext)) return Result.fail("不支持的表情包格式，允许：" + EMOJI_EXT);
                if (dto.getFileSize() != null && dto.getFileSize() > EMOJI_MAX_SIZE)
                    return Result.fail("表情包大小不能超过 2MB");
            }
        }

        // 4. 生成唯一文件 key，并按资源类型分目录，方便后续运维和资源管理。
        String dir = switch (dto.getMsgType()) {
            case 2 -> "images";
            case 3 -> "files";
            case 4 -> "emojis";
            default -> "others";
        };
        String key = dir + "/" + userId + "/" + UUID.randomUUID() + "." + ext;

        // 5. 生成七牛 UploadToken，并绑定固定 key，防止前端任意覆盖其他路径文件。
        String uploadToken = qiniuAuth.uploadToken(
                ossConfig.getBucketName(),
                key,
                ossConfig.getPresignExpireSeconds(),
                null
        );

        // 6. 组装最终访问地址和上传参数，前端据此直接走七牛直传。
        String fileUrl = ossConfig.getUrlPrefix() + "/" + key;

        Map<String, Object> data = new HashMap<>();
        data.put("uploadToken", uploadToken);   // 前端上传时携带的凭证
        data.put("key", key);                   // 前端上传时指定的文件 key
        data.put("fileUrl", fileUrl);           // 上传完成后传给发消息接口
        data.put("uploadUrl", resolveUploadUrl()); // 七牛上传域名
        data.put("expireAt", System.currentTimeMillis() + ossConfig.getPresignExpireSeconds() * 1000);//凭证过期时间

        return Result.ok(data);
    }

    /**
     * 七牛云各区域上传域名
     * 使用 autoRegion 时统一用华东入口，SDK 自动路由
     */
    private String resolveUploadUrl() {
        // 优先使用显式配置的上传域名，没有配置时回退到默认区域入口。
        if (ossConfig.getUploadUrl() != null && !ossConfig.getUploadUrl().isBlank()) {
            return ossConfig.getUploadUrl().trim();
        }
        return "https://up-z2.qiniup.com";
    }

    /**
     * 从文件名中提取扩展名
     */
    private String extractExt(String fileName) {
        // 从原始文件名中提取扩展名，供格式白名单校验使用。
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == fileName.length() - 1) return "";
        return fileName.substring(dotIdx + 1);
    }
}
