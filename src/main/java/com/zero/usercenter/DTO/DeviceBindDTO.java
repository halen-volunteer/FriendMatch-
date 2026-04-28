package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 设备绑定请求 DTO。
 *
 * <p>用于首登绑定新设备或已绑定设备的指纹信息刷新。</p>
 */
@Data
public class DeviceBindDTO {

    /** 设备唯一标识（同一用户下用于判重）。 */
    private String deviceId;

    /** 设备展示名称（如 iPhone 14、Windows-PC）。 */
    private String deviceName;

    /** 设备类型（具体枚举由前端约定）。 */
    private Integer deviceType;

    /** 设备操作系统信息。 */
    private String deviceOs;

    /** 浏览器或客户端标识。 */
    private String deviceBrowser;

    /** 最近登录 IP。 */
    private String deviceIp;

    /** 最近登录地理位置。 */
    private String deviceLocation;
}
