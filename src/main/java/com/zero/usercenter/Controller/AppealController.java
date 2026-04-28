package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.AppealHandleDTO;
import com.zero.usercenter.DTO.AppealSubmitDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.AppealService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 申诉模块 Controller。
 */
@RestController
@RequestMapping("/api/appeal")
public class AppealController {

    @Resource
    private AppealService appealService;

    /**
     * 提交申诉。
     *
     * @param dto 申诉提交参数
     * @return 提交结果
     */
    @PostMapping("/submit")
    public Result submitAppeal(@RequestBody AppealSubmitDTO dto) {
        return appealService.submitAppeal(dto);
    }

    /**
     * 查询我的申诉。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @return 我的申诉列表
     */
    @GetMapping("/my")
    public Result getMyAppeals(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int pageSize) {
        return appealService.getMyAppeals(page, pageSize);
    }

    /**
     * 查询待处理申诉（管理员）。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @return 待处理申诉列表
     */
    @GetMapping("/pending")
    public Result getPendingAppeals(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        return appealService.getPendingAppeals(page, pageSize);
    }

    /**
     * 处理申诉（管理员）。
     *
     * @param dto 申诉处理参数
     * @return 处理结果
     */
    @PostMapping("/handle")
    public Result handleAppeal(@RequestBody AppealHandleDTO dto) {
        return appealService.handleAppeal(dto);
    }
}

