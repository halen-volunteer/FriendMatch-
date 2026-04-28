package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.PunishCancelDTO;
import com.zero.usercenter.DTO.PunishDTO;
import com.zero.usercenter.DTO.Result;

/**
 * 处罚管理服务接口
 */
public interface PunishService {

    /**
     * 管理员对用户执行处罚（全局禁言/封号）
     * 处罚类型：1-全局禁言，2-永久封号
     * 梯度处罚：违规次数达到阈值时自动升级
     *
     * @param dto 处罚数据传输对象，包含目标用户 ID、处罚类型、原因、时长等
     * @return 处罚结果
     */
    Result punishUser(PunishDTO dto);

    /**
     * 撤销处罚（管理员操作）
     * 同步清除 User 表禁言字段 + Redis 缓存
     *
     * @param dto 撤销处罚数据传输对象，包含处罚记录 ID
     * @return 撤销结果
     */
    Result cancelPunish(PunishCancelDTO dto);

    /**
     * 查询指定用户的处罚记录（分页）
     * 管理员可查任意用户，普通用户只能查自己
     *
     * @param userId   目标用户 ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 处罚记录分页列表
     */
    Result getPunishLogs(Long userId, int page, int pageSize);

    /**
     * 查询我的处罚记录（普通用户）
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 我的处罚记录分页列表
     */
    Result getMyPunishLogs(int page, int pageSize);

    /**
     * 获取用户违规统计信息
     *
     * @param userId 目标用户 ID
     * @return 用户违规次数统计信息
     */
    Result getViolationCount(Long userId);
}
