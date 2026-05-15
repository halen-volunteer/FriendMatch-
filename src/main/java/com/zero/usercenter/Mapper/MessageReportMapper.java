package com.zero.usercenter.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zero.usercenter.Model.MessageReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息举报 Mapper。
 * 对接消息举报主表，供举报提交、后台审核、申诉流转等链路复用。
 */
@Mapper
public interface MessageReportMapper extends BaseMapper<MessageReport> {
}
