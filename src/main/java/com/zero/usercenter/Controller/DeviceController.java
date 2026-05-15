package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.DeviceBindDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.DeviceService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 设备管理 Controller。
 */
@RestController
@RequestMapping("/api/user")
public class DeviceController {

    @Resource
    private DeviceService deviceService;

    /**
     * 绑定（或更新）设备。
     *
     * @param dto 设备绑定参数
     * @return 绑定结果
     */
    @PostMapping("/device/bind")
    public Result bindDevice(@RequestBody DeviceBindDTO dto) {
        // 绑定设备会走设备归一化和幂等 upsert 逻辑，controller 只做请求分发。
        return deviceService.bindDevice(dto);
    }

    /**
     * 获取我的设备列表。
     *
     * @return 设备列表
     */
    @GetMapping("/devices")
    public Result getMyDevices() {
        // 设备列表由 service 负责按最近活跃时间排序并过滤已删除记录。
        return deviceService.getMyDevices();
    }

    /**
     * 信任设备。
     *
     * @param deviceId 设备唯一标识
     * @return 操作结果
     */
    @PostMapping("/device/trust")
    public Result trustDevice(@RequestParam String deviceId) {
        // 信任设备只更新当前用户自己的设备记录，归属校验在 service 层完成。
        return deviceService.trustDevice(deviceId);
    }

    /**
     * 设备下线。
     *
     * @param deviceId 设备唯一标识
     * @return 操作结果
     */
    @PostMapping("/device/logout")
    public Result logoutDevice(@RequestParam String deviceId) {
        // 设备下线不会删记录，只更新活跃状态，具体处理由 service 负责。
        return deviceService.logoutDevice(deviceId);
    }

    /**
     * 删除设备（软删除）。
     *
     * @param deviceId 设备唯一标识
     * @return 操作结果
     */
    @DeleteMapping("/device/{deviceId}")
    public Result deleteDevice(@PathVariable String deviceId) {
        // 删除设备采用软删除，controller 只把设备标识转给 service。
        return deviceService.deleteDevice(deviceId);
    }
}

