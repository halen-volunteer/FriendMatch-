package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zero.usercenter.Mapper.AdminUserMapper;
import com.zero.usercenter.Mapper.AppealAdminHistoryMapper;
import com.zero.usercenter.Mapper.AppealMapper;
import com.zero.usercenter.Mapper.ReportCaseMapper;
import com.zero.usercenter.Model.AdminUser;
import com.zero.usercenter.Model.Appeal;
import com.zero.usercenter.Model.AppealAdminHistory;
import com.zero.usercenter.Model.ReportCase;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 举报 / 申诉管理员分配支持。
 * 统一按当前待处理工作量最少的管理员分配，避免同一单被多个管理员同时看到并处理。
 */
@Service
public class ReportAdminAssignSupport {

    @Resource
    private AdminUserMapper adminUserMapper;

    @Resource
    private ReportCaseMapper reportCaseMapper;

    @Resource
    private AppealMapper appealMapper;

    @Resource
    private AppealAdminHistoryMapper appealAdminHistoryMapper;

    /**
     * 为举报/申诉分配处理管理员。
     *
     * @param relatedReportId   关联举报单 ID
     * @param relatedReportType 关联举报类型
     * @return 被选中的管理员用户 ID；若当前没有可用管理员则返回 null
     */
    public Long allocateAdmin(Long relatedReportId, Integer relatedReportType) {
        return allocateAdmin(relatedReportId, relatedReportType, false);
    }

    /**
     * 为举报/申诉分配处理管理员。
     *
     * @param relatedReportId    关联举报单 ID
     * @param relatedReportType  关联举报类型
     * @param avoidHistoryAdmins 是否尽量避开历史处理过该单的管理员
     * @return 被选中的管理员用户 ID；若当前没有可用管理员则返回 null
     */
    public Long allocateAdmin(Long relatedReportId, Integer relatedReportType, boolean avoidHistoryAdmins) {
        // 1. 先筛出当前启用中的管理员账号，停用或已删除账号不参与分配。
        LambdaQueryWrapper<AdminUser> adminQuery = new LambdaQueryWrapper<>();
        adminQuery.eq(AdminUser::getAdminStatus, 1)
                .eq(AdminUser::getIsDelete, 0);
        List<AdminUser> admins = adminUserMapper.selectList(adminQuery);
        if (admins == null || admins.isEmpty()) {
            return null;
        }

        // 2. 抽取可参与分配的管理员用户 ID。
        List<Long> adminIds = new ArrayList<>();
        for (AdminUser admin : admins) {
            if (admin.getUserId() != null) {
                adminIds.add(admin.getUserId());
            }
        }
        if (adminIds.isEmpty()) {
            return null;
        }

        if (avoidHistoryAdmins) {
            // 3. 申诉复核场景下，优先排除已经处理过该单的管理员，减少重复判断偏差。
            List<Long> filtered = filterHistoryAdmins(adminIds, relatedReportId, relatedReportType);
            if (!filtered.isEmpty()) {
                adminIds = filtered;
            }
        }

        // 4. 按当前待处理工作量最少原则挑选管理员，工作量相同则取用户 ID 更小者。
        Map<Long, Long> workloadMap = loadWorkloadMap(adminIds);
        Long selectedAdminId = null;
        long minWorkload = Long.MAX_VALUE;
        for (Long adminId : adminIds) {
            long workload = workloadMap.getOrDefault(adminId, 0L);
            if (selectedAdminId == null || workload < minWorkload || (workload == minWorkload && adminId < selectedAdminId)) {
                selectedAdminId = adminId;
                minWorkload = workload;
            }
        }
        return selectedAdminId;
    }

    /**
     * 判断当前管理员是否就是该单的指派管理员。
     *
     * @param currentAdminId  当前管理员 ID
     * @param assignedAdminId 工单指派管理员 ID
     * @return true 表示是当前指派管理员
     */
    public boolean isAssignedAdmin(Long currentAdminId, Long assignedAdminId) {
        return currentAdminId != null && assignedAdminId != null && currentAdminId.equals(assignedAdminId);
    }

    /**
     * 统计管理员当前待处理工作量。
     * 工作量由“待处理举报单 + 待处理申诉单”组成，供负载均衡分配使用。
     *
     * @param adminIds 管理员用户 ID 列表
     * @return Map&lt;管理员 ID, 当前待处理工单数&gt;
     */
    private Map<Long, Long> loadWorkloadMap(List<Long> adminIds) {
        Map<Long, Long> result = new HashMap<>();
        if (adminIds == null || adminIds.isEmpty()) {
            return result;
        }

        // 1. 统计这些管理员名下尚未处理完成的举报单数量。
        LambdaQueryWrapper<ReportCase> reportQuery = new LambdaQueryWrapper<>();
        reportQuery.eq(ReportCase::getIsDelete, 0)
                .eq(ReportCase::getAdminStatus, 0)
                .isNotNull(ReportCase::getAdminId)
                .in(ReportCase::getAdminId, adminIds);
        for (ReportCase reportCase : reportCaseMapper.selectList(reportQuery)) {
            if (reportCase.getAdminId() != null) {
                result.merge(reportCase.getAdminId(), 1L, Long::sum);
            }
        }

        // 2. 再叠加尚未处理完成的申诉单数量，形成管理员总工作量。
        LambdaQueryWrapper<Appeal> appealQuery = new LambdaQueryWrapper<>();
        appealQuery.eq(Appeal::getIsDelete, 0)
                .eq(Appeal::getAppealStatus, 0)
                .isNotNull(Appeal::getAdminId)
                .in(Appeal::getAdminId, adminIds);
        for (Appeal appeal : appealMapper.selectList(appealQuery)) {
            if (appeal.getAdminId() != null) {
                result.merge(appeal.getAdminId(), 1L, Long::sum);
            }
        }

        // 3. 对没有待处理工单的管理员补 0，方便后续统一比较最小值。
        for (Long adminId : adminIds) {
            result.putIfAbsent(adminId, 0L);
        }
        return result;
    }

    /**
     * 过滤掉历史上处理过该举报/申诉的管理员。
     *
     * @param adminIds          候选管理员 ID 列表
     * @param relatedReportId   关联举报单 ID
     * @param relatedReportType 关联举报类型
     * @return 过滤后的管理员 ID 列表
     */
    private List<Long> filterHistoryAdmins(List<Long> adminIds, Long relatedReportId, Integer relatedReportType) {
        // 1. 参数不完整时不做过滤，直接返回原候选集合。
        if (adminIds == null || adminIds.isEmpty() || relatedReportId == null || relatedReportType == null) {
            return adminIds;
        }

        // 2. 查出历史上处理过该关联举报单的管理员 ID。
        LambdaQueryWrapper<AppealAdminHistory> historyQuery = new LambdaQueryWrapper<>();
        historyQuery.eq(AppealAdminHistory::getRelatedReportId, relatedReportId)
                .eq(AppealAdminHistory::getRelatedReportType, relatedReportType)
                .eq(AppealAdminHistory::getIsDelete, 0)
                .isNotNull(AppealAdminHistory::getAdminId);

        Set<Long> usedAdminIds = new HashSet<>();
        for (AppealAdminHistory history : appealAdminHistoryMapper.selectList(historyQuery)) {
            if (history.getAdminId() != null) {
                usedAdminIds.add(history.getAdminId());
            }
        }
        if (usedAdminIds.isEmpty()) {
            return adminIds;
        }

        // 3. 仅保留未参与过该单处理的管理员，作为更优候选集合。
        List<Long> filtered = new ArrayList<>();
        for (Long adminId : adminIds) {
            if (!usedAdminIds.contains(adminId)) {
                filtered.add(adminId);
            }
        }
        return filtered;
    }
}
