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
        return deviceService.bindDevice(dto);
    }

    /**
     * 获取我的设备列表。
     *
     * @return 设备列表
     */
    @GetMapping("/devices")
    public Result getMyDevices() {
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
        return deviceService.deleteDevice(deviceId);
    }
}

