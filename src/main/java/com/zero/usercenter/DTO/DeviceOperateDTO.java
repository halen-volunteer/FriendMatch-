package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 设备操作请求 DTO。
 *
 * <p>用于信任设备、设备下线、设备删除等接口的统一入参。</p>
 */
@Data
public class DeviceOperateDTO {

    /** 设备唯一标识。 */
    private String deviceId;
}
