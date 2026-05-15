package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.DeviceBindDTO;
import com.zero.usercenter.DTO.Result;

/**
 * 设备管理服务接口。
 */
public interface DeviceService {

    /**
     * 绑定或更新当前登录用户设备。
     *
     * @param dto 设备绑定参数，包含设备编号、设备类型、设备名称等信息
     * @return 统一响应结果，成功时表示设备信息已绑定或更新
     */
    Result bindDevice(DeviceBindDTO dto);

    /**
     * 查询当前登录用户设备列表。
     *
     * @return 统一响应结果，成功时包含当前用户设备列表
     */
    Result getMyDevices();

    /**
     * 将指定设备设为信任设备。
     *
     * @param deviceId 设备唯一标识
     * @return 统一响应结果，成功时表示设备已标记为信任
     */
    Result trustDevice(String deviceId);

    /**
     * 下线指定设备。
     *
     * @param deviceId 设备唯一标识
     * @return 统一响应结果，成功时表示设备已被下线
     */
    Result logoutDevice(String deviceId);

    /**
     * 删除指定设备。
     *
     * @param deviceId 设备唯一标识
     * @return 统一响应结果，成功时表示设备记录已删除
     */
    Result deleteDevice(String deviceId);
}
