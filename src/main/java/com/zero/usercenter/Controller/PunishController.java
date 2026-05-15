package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.PunishCancelDTO;
import com.zero.usercenter.DTO.PunishDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.PunishService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 处罚管理接口入口。
 * 统一暴露处罚执行、处罚撤销、处罚记录查询和违规统计查询能力。
 */
@RestController
@RequestMapping("/api/punish")
public class PunishController {

    @Resource
    private PunishService punishService;

    /**
     * 对用户执行处罚。
     * 支持管理员手动处罚，也支持系统内部复用同一处罚链路。
     */
    @PostMapping("/execute")
    public Result punishUser(@RequestBody PunishDTO dto) {
        // 处罚执行会串起违规次数、处罚日志、用户状态和通知链路，统一在 service 中落地。
        return punishService.punishUser(dto);
    }

    /**
     * 撤销处罚。
     * 撤销后会恢复用户当前有效处罚状态，并回滚对应违规统计。
     */
    @PostMapping("/cancel")
    public Result cancelPunish(@RequestBody PunishCancelDTO dto) {
        // 撤销处罚不仅改日志，还要重算当前处罚状态，因此必须走完整 service 链路。
        return punishService.cancelPunish(dto);
    }

    /**
     * 查询指定用户的处罚记录。
     * 该接口主要供管理员查看目标用户的处罚历史。
     */
    @GetMapping("/logs")
    public Result getPunishLogs(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 管理员查看指定用户处罚历史时，分页和视图转换都在 service 层完成。
        return punishService.getPunishLogs(userId, page, pageSize);
    }

    /**
     * 查询当前用户自己的处罚记录。
     * 用于用户端查看自己历史禁言、封号和撤销记录。
     */
    @GetMapping("/my-logs")
    public Result getMyPunishLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 当前用户查看自己的处罚记录时，仍复用同一套处罚日志查询能力。
        return punishService.getMyPunishLogs(page, pageSize);
    }

    /**
     * 获取用户违规统计信息。
     * 返回累计违规次数和最近一次违规时间。
     */
    @GetMapping("/violation-count")
    public Result getViolationCount(@RequestParam Long userId) {
        // 违规统计只暴露结果，累计规则和数据来源都封装在 service 中。
        return punishService.getViolationCount(userId);
    }
}
