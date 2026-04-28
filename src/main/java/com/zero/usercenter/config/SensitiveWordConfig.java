package com.zero.usercenter.config;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 敏感词检测配置
 * 将 SensitiveWordBs 注册为 Spring Bean，供 TeamServiceImpl 等注入使用
 */
@Configuration
public class SensitiveWordConfig {

    @Bean
    public SensitiveWordBs sensitiveWordBs() {
        return SensitiveWordBs.newInstance()
                .ignoreCase(true)
                .ignoreWidth(true)
                .ignoreNumStyle(true)
                .enableNumCheck(true)
                .enableEmailCheck(true)
                .init();
    }
}
