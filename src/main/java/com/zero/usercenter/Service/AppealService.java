package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.AppealHandleDTO;
import com.zero.usercenter.DTO.AppealSubmitDTO;
import com.zero.usercenter.DTO.Result;

/**
 * 申诉服务接口。
 */
public interface AppealService {

    /**
     * 提交申诉。
     *
     * @param dto 申诉提交参数
     * @return 提交结果
     */
    Result submitAppeal(AppealSubmitDTO dto);

    /**
     * 查询当前登录用户的申诉列表。
     *
     * @param page 页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 申诉分页结果
     */
    Result getMyAppeals(int page, int pageSize);

    /**
     * 查询待处理申诉列表（管理员）。
     *
     * @param page 页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 待处理申诉分页结果
     */
    Result getPendingAppeals(int page, int pageSize);

    /**
     * 处理申诉（管理员）。
     *
     * @param dto 申诉处理参数
     * @return 处理结果
     */
    Result handleAppeal(AppealHandleDTO dto);
}
