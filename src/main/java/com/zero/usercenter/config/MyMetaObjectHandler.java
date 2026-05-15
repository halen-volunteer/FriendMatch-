package com.zero.usercenter.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器。
 * 统一补齐创建时间和更新时间，避免业务层重复赋值。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充审计字段。
     *
     * @param metaObject MyBatis-Plus 当前处理的实体元信息
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 插入时同时补齐 createTime 和 updateTime，保持审计字段完整。
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 更新时自动刷新更新时间。
     *
     * @param metaObject MyBatis-Plus 当前处理的实体元信息
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新时只刷新 updateTime，避免误改创建时间。
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
