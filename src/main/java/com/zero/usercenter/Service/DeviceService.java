package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.DeviceBindDTO;
import com.zero.usercenter.DTO.Result;

/**
 * 设备管理服务接口。
 */
public interface DeviceService {

    /**
     * 绑定（或更新）当前登录用户设备。
     *
     * @param dto 设备绑定参数
     * @return 绑定结果
     */
    Result bindDevice(DeviceBindDTO dto);

    /**
     * 查询当前登录用户设备列表。
     *
     * @return 设备列表
     */
    Result getMyDevices();

    /**
     * 将指定设备设为信任设备。
     *
     * @param deviceId 设备唯一标识
     * @return 操作结果
     */
    Result trustDevice(String deviceId);

    /**
     * 下线指定设备。
     *
     * @param deviceId 设备唯一标识
     * @return 操作结果
     */
    Result logoutDevice(String deviceId);

    /**
     * 删除指定设备（软删除）。
     *
     * @param deviceId 设备唯一标识
     * @return 操作结果
     */
    Result deleteDevice(String deviceId);
}
