package com.zero.usercenter.config;

import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 七牛云 OSS 配置。
 * 从 application 配置中读取鉴权和上传参数，并注册上传所需 Bean。
 */
@Data
@org.springframework.context.annotation.Configuration
@ConfigurationProperties(prefix = "oss")
public class OssConfig {

    /** 七牛云 AccessKey */
    private String accessKeyId;

    /** 七牛云 SecretKey */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucketName;

    /** 访问域名前缀，如 https://xxxxx.bkt.clouddn.com */
    private String urlPrefix;

    /** 七牛上传域名，如 https://up-z2.qiniup.com */
    private String uploadUrl;

    /** 上传凭证（UploadToken）有效期，单位秒，默认 300 秒 */
    private long presignExpireSeconds = 300;

    /**
     * 七牛云鉴权对象。
     */
    @Bean
    public Auth qiniuAuth() {
        // 基于配置文件中的 AK/SK 创建鉴权对象，供生成上传凭证和资源管理时复用。
        return Auth.create(accessKeyId == null ? "" : accessKeyId, accessKeySecret == null ? "" : accessKeySecret);
    }

    /**
     * 七牛云上传管理器。
     * 当前使用 autoRegion，避免在本地和部署环境间手动切换区域配置。
     */
    @Bean
    public UploadManager uploadManager() {
        // 上传时由 SDK 自动识别区域，降低环境迁移时的配置复杂度。
        Configuration cfg = new Configuration(Region.autoRegion());
        return new UploadManager(cfg);
    }

    /**
     * 七牛云 Bucket 管理器。
     *
     * @param qiniuAuth 七牛云鉴权对象
     * @return 七牛 Bucket 管理器，用于删除资源等管理操作
     */
    @Bean
    public BucketManager bucketManager(Auth qiniuAuth) {
        // 和上传管理器保持相同区域策略，避免上传和删除走不同配置。
        Configuration cfg = new Configuration(Region.autoRegion());
        return new BucketManager(qiniuAuth, cfg);
    }
}
