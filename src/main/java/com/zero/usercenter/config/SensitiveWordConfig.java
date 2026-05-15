package com.zero.usercenter.config;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 敏感词检测配置。
 * 统一注册敏感词引擎，供团队、资料、聊天等内容校验场景复用。
 */
@Configuration
public class SensitiveWordConfig {

    /**
     * 初始化敏感词引擎。
     * 这里开启大小写、全半角、数字变体等兼容能力，降低简单变形绕过的概率。
     *
     * @return 初始化完成的敏感词检测引擎
     */
    @Bean
    public SensitiveWordBs sensitiveWordBs() {
        // 统一在 Spring 容器中注册一个单例敏感词引擎，供用户资料、团队信息、聊天内容等场景复用。
        return SensitiveWordBs.newInstance()
                .ignoreCase(true)
                .ignoreWidth(true)
                .ignoreNumStyle(true)
                .enableNumCheck(true)
                .enableEmailCheck(true)
                .init();
    }
}
