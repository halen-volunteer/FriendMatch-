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
 * 七牛云 OSS 配置
 * 从 application.yaml oss.* 读取配置项
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
     * 七牛云鉴权对象（全局复用）
     */
    @Bean
    public Auth qiniuAuth() {
        return Auth.create(accessKeyId == null ? "" : accessKeyId, accessKeySecret == null ? "" : accessKeySecret);
    }

    /**
     * 七牛云上传管理器
     * Region 根据 Bucket 所在区域选择：
     *   华东 z0：Region.region0()
     *   华北 z1：Region.region1()
     *   华南 z2：Region.region2()
     *   北美  na0：Region.regionNa0()
     *   东南亚 as0：Region.regionAs0()
     */
    @Bean
    public UploadManager uploadManager() {
        Configuration cfg = new Configuration(Region.autoRegion());
        return new UploadManager(cfg);
    }

    @Bean
    public BucketManager bucketManager(Auth qiniuAuth) {
        Configuration cfg = new Configuration(Region.autoRegion());
        return new BucketManager(qiniuAuth, cfg);
    }
}
