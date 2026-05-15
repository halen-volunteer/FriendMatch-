package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.PunishCancelDTO;
import com.zero.usercenter.DTO.PunishDTO;
import com.zero.usercenter.DTO.Result;

/**
 * 处罚管理服务接口。
 * 覆盖处罚执行、撤销、查询和违规统计能力。
 */
public interface PunishService {

    /**
     * 管理员对用户执行处罚。
     * 支持全局禁言和永久封号，并结合违规次数做梯度升级。
     *
     * @param dto 处罚参数，包含被处罚用户、处罚类型、时长和原因等信息
     * @return 统一响应结果，成功时表示处罚已生效
     */
    Result punishUser(PunishDTO dto);

    /**
     * 撤销处罚。
     * 需要同步清理数据库状态和 Redis 缓存。
     *
     * @param dto 撤销处罚参数，包含处罚记录 ID 和撤销原因等信息
     * @return 统一响应结果，成功时表示处罚已撤销
     */
    Result cancelPunish(PunishCancelDTO dto);

    /**
     * 查询指定用户的处罚记录。
     * 管理员可查任意用户，普通用户只能查看自己。
     *
     * @param userId 目标用户 ID
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含处罚记录分页数据
     */
    Result getPunishLogs(Long userId, int page, int pageSize);

    /**
     * 查询我的处罚记录。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含当前用户的处罚记录分页数据
     */
    Result getMyPunishLogs(int page, int pageSize);

    /**
     * 获取用户违规统计信息。
     *
     * @param userId 目标用户 ID
     * @return 统一响应结果，成功时包含累计违规次数等统计信息
     */
    Result getViolationCount(Long userId);
}
