package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.AppealHandleDTO;
import com.zero.usercenter.DTO.AppealSubmitDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.AppealService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 申诉中心接口入口。
 * 统一暴露用户提交申诉、查询个人申诉，以及管理员待处理与处理申诉的能力。
 */
@RestController
@RequestMapping("/api/appeal")
public class AppealController {

    @Resource
    private AppealService appealService;

    /**
     * 提交申诉。
     */
    @PostMapping("/submit")
    public Result submitAppeal(@RequestBody AppealSubmitDTO dto) {
        // 提交申诉会校验处罚关联关系、重复提交和附件信息，具体逻辑下沉到 service。
        return appealService.submitAppeal(dto);
    }

    /**
     * 查询当前用户发起的申诉列表。
     */
    @GetMapping("/my")
    public Result getMyAppeals(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int pageSize) {
        // 我的申诉列表只查询当前用户视角的数据，分页和状态组装都在 service 中。
        return appealService.getMyAppeals(page, pageSize);
    }

    /**
     * 管理员查询分配给自己的待处理申诉列表。
     */
    @GetMapping("/pending")
    public Result getPendingAppeals(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        // 管理员待处理列表会按处理人和状态过滤，controller 不持有审核规则。
        return appealService.getPendingAppeals(page, pageSize);
    }

    /**
     * 管理员处理申诉。
     */
    @PostMapping("/handle")
    public Result handleAppeal(@RequestBody AppealHandleDTO dto) {
        // 申诉处理会触发处罚撤销、通知等副作用，因此由 service 统一编排。
        return appealService.handleAppeal(dto);
    }
}
