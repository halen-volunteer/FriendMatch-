package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.PunishCancelDTO;
import com.zero.usercenter.DTO.PunishDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.PunishService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 处罚管理 Controller
 *
 * 基础路径：/api/punish
 * 所有接口需在请求头携带 Authorization: {token}
 * 执行处罚/撤销处罚接口仅限管理员调用（业务层校验）
 */
@RestController
@RequestMapping("/api/punish")
public class PunishController {

    @Resource
    private PunishService punishService;

    /**
     * 对用户执行处罚（管理员）
     * POST /api/punish/execute
     * 处罚类型：1-全局禁言，2-永久封号
     * 自动更新违规统计 + 发送处罚通知
     *
     * @param dto 处罚数据传输对象，包含目标用户 ID、处罚类型、原因、时长等
     * @return 处罚结果
     */
    @PostMapping("/execute")
    public Result punishUser(@RequestBody PunishDTO dto) {
        return punishService.punishUser(dto);
    }

    /**
     * 撤销处罚（管理员）
     * POST /api/punish/cancel
     * 同步清除禁言状态 + Redis 缓存
     *
     * @param dto 撤销处罚数据传输对象，包含处罚记录 ID
     * @return 撤销结果
     */
    @PostMapping("/cancel")
    public Result cancelPunish(@RequestBody PunishCancelDTO dto) {
        return punishService.cancelPunish(dto);
    }

    /**
     * 查询指定用户的处罚记录（管理员）
     * GET /api/punish/logs?userId=1001&page=1&pageSize=20
     *
     * @param userId   目标用户 ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 处罚记录分页列表
     */
    @GetMapping("/logs")
    public Result getPunishLogs(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return punishService.getPunishLogs(userId, page, pageSize);
    }

    /**
     * 查询我的处罚记录（普通用户）
     * GET /api/punish/my-logs?page=1&pageSize=20
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 我的处罚记录分页列表
     */
    @GetMapping("/my-logs")
    public Result getMyPunishLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return punishService.getMyPunishLogs(page, pageSize);
    }

    /**
     * 获取用户违规统计信息（管理员）
     * GET /api/punish/violation-count?userId=1001
     *
     * @param userId 目标用户 ID
     * @return 用户违规次数统计信息
     */
    @GetMapping("/violation-count")
    public Result getViolationCount(@RequestParam Long userId) {
        return punishService.getViolationCount(userId);
    }
}
