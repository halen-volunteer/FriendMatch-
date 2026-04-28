package com.zero.usercenter.Service.impl;

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

    /**
     * 设备管理服务实现：
     * - 设备绑定/更新
     * - 设备查询、信任、下线、删除
     */
    @Resource
    private UserDeviceMapper userDeviceMapper;

    /**
     * 绑定设备：若设备不存在则新增，存在则更新最新设备信息。
     *
     * @param dto 设备绑定参数
     * @return 绑定或更新结果
     */
    @Override
    public Result bindDevice(DeviceBindDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto == null || dto.getDeviceId() == null || dto.getDeviceId().isBlank()) return Result.fail("设备ID不能为空");

        LambdaQueryWrapper<UserDevice> qw = new LambdaQueryWrapper<>();
        qw.eq(UserDevice::getUserId, userId)
          .eq(UserDevice::getDeviceId, dto.getDeviceId().trim())
          .eq(UserDevice::getIsDelete, 0);
        UserDevice exist = userDeviceMapper.selectOne(qw);

        if (exist == null) {
            UserDevice device = new UserDevice();
            device.setUserId(userId);
            device.setDeviceId(dto.getDeviceId().trim());
            device.setDeviceName(dto.getDeviceName());
            device.setDeviceType(dto.getDeviceType());
            device.setDeviceOs(dto.getDeviceOs());
            device.setDeviceBrowser(dto.getDeviceBrowser());
            device.setDeviceIp(dto.getDeviceIp());
            device.setDeviceLocation(dto.getDeviceLocation());
            device.setLastLoginTime(LocalDateTime.now());
            device.setIsTrusted(0);
            device.setIsActive(1);
            device.setIsDelete(0);
            userDeviceMapper.insert(device);
            return Result.ok("设备已绑定");
        }

        LambdaUpdateWrapper<UserDevice> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserDevice::getId, exist.getId())
          .set(UserDevice::getDeviceName, dto.getDeviceName())
          .set(UserDevice::getDeviceType, dto.getDeviceType())
          .set(UserDevice::getDeviceOs, dto.getDeviceOs())
          .set(UserDevice::getDeviceBrowser, dto.getDeviceBrowser())
          .set(UserDevice::getDeviceIp, dto.getDeviceIp())
          .set(UserDevice::getDeviceLocation, dto.getDeviceLocation())
          .set(UserDevice::getLastLoginTime, LocalDateTime.now())
          .set(UserDevice::getIsActive, 1);
        userDeviceMapper.update(null, uw);
        return Result.ok("设备信息已更新");
    }

    /**
     * 获取当前用户的设备列表。
     *
     * @return 设备列表
     */
    @Override
    public Result getMyDevices() {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        LambdaQueryWrapper<UserDevice> qw = new LambdaQueryWrapper<>();
        qw.eq(UserDevice::getUserId, userId)
          .eq(UserDevice::getIsDelete, 0)
          .orderByDesc(UserDevice::getLastLoginTime);
        return Result.ok(userDeviceMapper.selectList(qw));
    }

    /**
     * 将指定设备标记为信任设备。
     *
     * @param deviceId 设备唯一标识
     * @return 操作结果
     */
    @Override
    public Result trustDevice(String deviceId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (deviceId == null || deviceId.isBlank()) return Result.fail("设备ID不能为空");

        LambdaUpdateWrapper<UserDevice> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserDevice::getUserId, userId)
          .eq(UserDevice::getDeviceId, deviceId)
          .eq(UserDevice::getIsDelete, 0)
          .set(UserDevice::getIsTrusted, 1);
        int rows = userDeviceMapper.update(null, uw);
        if (rows == 0) return Result.fail("设备不存在");
        return Result.ok("设备已信任");
    }

    /**
     * 将指定设备设置为下线状态。
     *
     * @param deviceId 设备唯一标识
     * @return 操作结果
     */
    @Override
    public Result logoutDevice(String deviceId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (deviceId == null || deviceId.isBlank()) return Result.fail("设备ID不能为空");

        LambdaUpdateWrapper<UserDevice> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserDevice::getUserId, userId)
          .eq(UserDevice::getDeviceId, deviceId)
          .eq(UserDevice::getIsDelete, 0)
          .set(UserDevice::getIsActive, 0);
        int rows = userDeviceMapper.update(null, uw);
        if (rows == 0) return Result.fail("设备不存在");
        return Result.ok("设备已下线");
    }

    /**
     * 删除指定设备（软删除）。
     *
     * @param deviceId 设备唯一标识
     * @return 操作结果
     */
    @Override
    public Result deleteDevice(String deviceId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (deviceId == null || deviceId.isBlank()) return Result.fail("设备ID不能为空");

        LambdaUpdateWrapper<UserDevice> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserDevice::getUserId, userId)
          .eq(UserDevice::getDeviceId, deviceId)
          .eq(UserDevice::getIsDelete, 0)
          .set(UserDevice::getIsDelete, 1)
          .set(UserDevice::getIsActive, 0);
        int rows = userDeviceMapper.update(null, uw);
        if (rows == 0) return Result.fail("设备不存在");
        return Result.ok("设备已删除");
    }
}

