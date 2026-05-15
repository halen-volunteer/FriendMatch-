package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.AppealHandleDTO;
import com.zero.usercenter.DTO.AppealSubmitDTO;
import com.zero.usercenter.DTO.Result;

/**
 * 申诉服务接口。
 * 覆盖用户提交申诉、查询申诉以及管理员处理申诉流程。
 */
public interface AppealService {

    /**
     * 提交申诉。
     *
     * @param dto 申诉提交参数，包含申诉目标、原因和补充说明等信息
     * @return 统一响应结果，成功时表示申诉已创建
     */
    Result submitAppeal(AppealSubmitDTO dto);

    /**
     * 查询当前登录用户提交的申诉记录。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含分页后的申诉列表
     */
    Result getMyAppeals(int page, int pageSize);

    /**
     * 查询待处理申诉列表。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含待处理申诉分页数据
     */
    Result getPendingAppeals(int page, int pageSize);

    /**
     * 管理员处理申诉。
     *
     * @param dto 申诉处理参数，包含申诉 ID、处理结果和处理说明
     * @return 统一响应结果，成功时表示申诉处理完成
     */
    Result handleAppeal(AppealHandleDTO dto);
}
