package com.zero.usercenter.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zero.usercenter.Model.SystemNotice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统通知 Mapper。
 * 负责通知中心相关的基础 CRUD，复杂筛选仍由 service 组装查询条件。
 */
@Mapper
public interface SystemNoticeMapper extends BaseMapper<SystemNotice> {
}
