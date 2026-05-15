package com.zero.usercenter.Service.impl;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zero.usercenter.DTO.DeviceBindDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Mapper.UserDeviceMapper;
import com.zero.usercenter.Model.UserDevice;
import com.zero.usercenter.Service.DeviceService;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DeviceServiceImpl implements DeviceService {

    private static final int DEVICE_ID_MAX_LENGTH = 64;
    private static final int DEVICE_NAME_MAX_LENGTH = 64;
    private static final int DEVICE_OS_MAX_LENGTH = 64;
    private static final int DEVICE_BROWSER_MAX_LENGTH = 64;
    private static final int DEVICE_IP_MAX_LENGTH = 64;
    private static final int DEVICE_LOCATION_MAX_LENGTH = 128;

    @Resource
    private UserDeviceMapper userDeviceMapper;

    @Override
    public Result bindDevice(DeviceBindDTO dto) {
        // 1. 先校验登录态和设备标识，设备表所有记录都必须明确归属到当前用户。
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        if (dto == null || dto.getDeviceId() == null || dto.getDeviceId().isBlank()) {
            return Result.fail("设备ID不能为空");
        }

        // 2. 对设备标识和展示字段做归一化，避免前端上报值过长或格式不稳定。
        String normalizedDeviceId = normalizeDeviceId(dto.getDeviceId());
        if (normalizedDeviceId == null || normalizedDeviceId.isBlank()) {
            return Result.fail("设备ID不能为空");
        }

        String deviceName = normalizeText(dto.getDeviceName(), DEVICE_NAME_MAX_LENGTH);
        String deviceOs = normalizeText(dto.getDeviceOs(), DEVICE_OS_MAX_LENGTH);
        String deviceBrowser = normalizeText(dto.getDeviceBrowser(), DEVICE_BROWSER_MAX_LENGTH);
        String deviceIp = normalizeText(dto.getDeviceIp(), DEVICE_IP_MAX_LENGTH);
        String deviceLocation = normalizeText(dto.getDeviceLocation(), DEVICE_LOCATION_MAX_LENGTH);

        // 3. 设备绑定按“同用户 + 归一化 deviceId”做幂等 upsert，
        // 多次登录同一设备时只刷新最后登录信息，不重复插入设备记录。
        LambdaQueryWrapper<UserDevice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, normalizedDeviceId)
                .eq(UserDevice::getIsDelete, 0);
        UserDevice exist = userDeviceMapper.selectOne(queryWrapper);

        if (exist == null) {
            // 4. 首次出现的设备直接插入，并默认标记为活跃未信任。
            UserDevice device = new UserDevice();
            device.setUserId(userId);
            device.setDeviceId(normalizedDeviceId);
            device.setDeviceName(deviceName);
            device.setDeviceType(dto.getDeviceType());
            device.setDeviceOs(deviceOs);
            device.setDeviceBrowser(deviceBrowser);
            device.setDeviceIp(deviceIp);
            device.setDeviceLocation(deviceLocation);
            device.setLastLoginTime(LocalDateTime.now());
            device.setIsTrusted(0);
            device.setIsActive(1);
            device.setIsDelete(0);
            userDeviceMapper.insert(device);
            return Result.ok("设备已绑定");
        }

        // 5. 老设备不重复建记录，只刷新名称、环境信息和最后登录时间。
        LambdaUpdateWrapper<UserDevice> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserDevice::getId, exist.getId())
                .set(UserDevice::getDeviceName, deviceName)
                .set(UserDevice::getDeviceType, dto.getDeviceType())
                .set(UserDevice::getDeviceOs, deviceOs)
                .set(UserDevice::getDeviceBrowser, deviceBrowser)
                .set(UserDevice::getDeviceIp, deviceIp)
                .set(UserDevice::getDeviceLocation, deviceLocation)
                .set(UserDevice::getLastLoginTime, LocalDateTime.now())
                .set(UserDevice::getIsActive, 1);
        userDeviceMapper.update(null, updateWrapper);
        return Result.ok("设备信息已更新");
    }

    @Override
    public Result getMyDevices() {
        // 设备列表按最近登录时间倒序，方便用户优先识别最近活跃设备。
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        LambdaQueryWrapper<UserDevice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getIsDelete, 0)
                .orderByDesc(UserDevice::getLastLoginTime);
        return Result.ok(userDeviceMapper.selectList(queryWrapper));
    }

    @Override
    public Result trustDevice(String deviceId) {
        // 信任设备本质上是更新当前用户名下指定设备的 trust 标记。
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        if (deviceId == null || deviceId.isBlank()) {
            return Result.fail("设备ID不能为空");
        }

        String normalizedDeviceId = normalizeDeviceId(deviceId);
        LambdaUpdateWrapper<UserDevice> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, normalizedDeviceId)
                .eq(UserDevice::getIsDelete, 0)
                .set(UserDevice::getIsTrusted, 1);
        int rows = userDeviceMapper.update(null, updateWrapper);
        if (rows == 0) {
            return Result.fail("设备不存在");
        }
        return Result.ok("设备已设为信任");
    }

    @Override
    public Result logoutDevice(String deviceId) {
        // 设备下线只更新活跃状态，不删除记录，便于保留登录审计痕迹。
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        if (deviceId == null || deviceId.isBlank()) {
            return Result.fail("设备ID不能为空");
        }

        String normalizedDeviceId = normalizeDeviceId(deviceId);
        LambdaUpdateWrapper<UserDevice> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, normalizedDeviceId)
                .eq(UserDevice::getIsDelete, 0)
                .set(UserDevice::getIsActive, 0);
        int rows = userDeviceMapper.update(null, updateWrapper);
        if (rows == 0) {
            return Result.fail("设备不存在");
        }
        return Result.ok("设备已下线");
    }

    @Override
    public Result deleteDevice(String deviceId) {
        // 删除设备采用软删除，既能让列表里消失，也能保留历史数据便于追查。
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        if (deviceId == null || deviceId.isBlank()) {
            return Result.fail("设备ID不能为空");
        }

        String normalizedDeviceId = normalizeDeviceId(deviceId);
        LambdaUpdateWrapper<UserDevice> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, normalizedDeviceId)
                .eq(UserDevice::getIsDelete, 0)
                .set(UserDevice::getIsDelete, 1)
                .set(UserDevice::getIsActive, 0);
        int rows = userDeviceMapper.update(null, updateWrapper);
        if (rows == 0) {
            return Result.fail("设备不存在");
        }
        return Result.ok("设备已删除");
    }

    private String normalizeDeviceId(String deviceId) {
        if (deviceId == null) {
            return null;
        }
        String normalized = deviceId.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > DEVICE_ID_MAX_LENGTH) {
            // 前端上报的指纹串可能远超数据库字段长度，超过上限时用摘要落库，
            // 保证同一原始指纹仍能稳定命中同一设备记录。
            return SecureUtil.sha256(normalized);
        }
        return normalized;
    }

    private String normalizeText(String value, int maxLength) {
        // 普通文本字段统一裁剪长度，避免浏览器、系统名等元信息把数据库字段撑爆。
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }
}
