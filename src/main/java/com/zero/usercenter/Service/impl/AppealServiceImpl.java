package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zero.usercenter.DTO.AppealHandleDTO;
import com.zero.usercenter.DTO.AppealSubmitDTO;
import com.zero.usercenter.DTO.PunishCancelDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Mapper.AdminUserMapper;
import com.zero.usercenter.Mapper.AppealMapper;
import com.zero.usercenter.Mapper.MessageReportMapper;
import com.zero.usercenter.Mapper.TeamReportMapper;
import com.zero.usercenter.Mapper.UserReportMapper;
import com.zero.usercenter.Model.AdminUser;
import com.zero.usercenter.Model.Appeal;
import com.zero.usercenter.Model.MessageReport;
import com.zero.usercenter.Model.TeamReport;
import com.zero.usercenter.Model.UserReport;
import com.zero.usercenter.Service.AdminAuthService;
import com.zero.usercenter.Service.AppealService;
import com.zero.usercenter.Service.PunishService;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 申诉服务实现类。
 * <p>
 * 提供申诉提交、申诉查询与管理员处理能力；
 * 管理员处理通过后会联动撤销关联处罚记录。
 */
@Service
public class AppealServiceImpl implements AppealService {

    @Resource
    private AppealMapper appealMapper;
    @Resource
    private UserReportMapper userReportMapper;
    @Resource
    private MessageReportMapper messageReportMapper;
    @Resource
    private TeamReportMapper teamReportMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private PunishService punishService;
    @Resource
    private AdminAuthService adminAuthService;

    private final Random random = new Random();

    /**
     * 提交申诉并分配处理管理员。
     *
     * @param dto 申诉提交参数，包含关联举报信息、申诉人类型、申诉理由等
     * @return 提交结果，含 appealId、appealRound、appealStatus
     */
    @Override
    @Transactional
    public Result submitAppeal(AppealSubmitDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto == null || dto.getRelatedReportId() == null || dto.getRelatedReportType() == null) return Result.fail("关联举报信息不能为空");
        if (dto.getAppellantType() == null || (dto.getAppellantType() != 1 && dto.getAppellantType() != 2)) return Result.fail("申诉人类型不合法");
        if (dto.getAppealReason() == null || dto.getAppealReason().isBlank()) return Result.fail("申诉理由不能为空");

        int usedCount = getAppealCount(dto.getRelatedReportType(), dto.getRelatedReportId());
        if (usedCount >= 3) return Result.fail("该举报已达最大申诉次数（3次），无法继续申诉");

        Long adminId = allocateAdmin(dto.getRelatedReportId(), dto.getRelatedReportType());
        if (adminId == null) return Result.fail("暂无可分配管理员，请稍后再试");

        Appeal appeal = new Appeal();
        appeal.setAppellantId(userId);
        appeal.setAppellantType(dto.getAppellantType());
        appeal.setRelatedReportId(dto.getRelatedReportId());
        appeal.setRelatedReportType(dto.getRelatedReportType());
        appeal.setRelatedPunishId(dto.getRelatedPunishId());
        appeal.setAppealRound(usedCount + 1);
        appeal.setAppealReason(dto.getAppealReason().trim());
        appeal.setAppealEvidence(dto.getAppealEvidence() == null ? "" : dto.getAppealEvidence().trim());
        appeal.setAppealStatus(0);
        appeal.setAdminId(adminId);
        appeal.setIsDelete(0);
        appealMapper.insert(appeal);

        Map<String, Object> data = new HashMap<>();
        data.put("appealId", appeal.getId());
        data.put("appealRound", appeal.getAppealRound());
        data.put("appealStatus", appeal.getAppealStatus());
        data.put("assignedAdminId", adminId);
        data.put("message", "申诉已提交，系统已随机分配未处理过该举报的管理员，请耐心等待");
        return Result.ok(data);
    }

    /**
     * 分页查询当前用户提交的申诉。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @return 申诉分页数据
     */
    @Override
    public Result getMyAppeals(int page, int pageSize) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        Page<Appeal> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<Appeal> qw = new LambdaQueryWrapper<>();
        qw.eq(Appeal::getAppellantId, userId)
          .eq(Appeal::getIsDelete, 0)
          .orderByDesc(Appeal::getCreateTime);
        Page<Appeal> result = appealMapper.selectPage(pageObj, qw);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    /**
     * 分页查询当前管理员被分配到的待处理申诉。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @return 待处理申诉分页数据
     */
    @Override
    public Result getPendingAppeals(int page, int pageSize) {
        Long adminId = UserHolder.getUserId();
        if (adminId == null) throw new BusinessException("用户未登录");
        adminAuthService.assertAdmin(adminId);
        Page<Appeal> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<Appeal> qw = new LambdaQueryWrapper<>();
        qw.eq(Appeal::getAppealStatus, 0)
          .eq(Appeal::getAdminId, adminId)
          .eq(Appeal::getIsDelete, 0)
          .orderByAsc(Appeal::getCreateTime);
        Page<Appeal> result = appealMapper.selectPage(pageObj, qw);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    /**
     * 管理员处理申诉并联动处罚状态。
     *
     * @param dto 申诉处理参数，包含 appealId、appealStatus、adminReply
     * @return 处理结果
     */
    @Override
    @Transactional
    public Result handleAppeal(AppealHandleDTO dto) {
        Long operatorId = UserHolder.getUserId();
        if (operatorId == null) throw new BusinessException("用户未登录");
        adminAuthService.assertAdmin(operatorId);
        if (dto == null || dto.getAppealId() == null) return Result.fail("申诉ID不能为空");
        if (dto.getAppealStatus() == null || (dto.getAppealStatus() != 1 && dto.getAppealStatus() != 2)) return Result.fail("申诉状态不合法");

        Appeal appeal = appealMapper.selectById(dto.getAppealId());
        if (appeal == null || appeal.getIsDelete() == 1) return Result.fail("申诉记录不存在");
        if (appeal.getAppealStatus() != 0) return Result.fail("该申诉已处理");
        if (appeal.getAdminId() == null) return Result.fail("该申诉未分配处理管理员");
        if (!operatorId.equals(appeal.getAdminId())) return Result.fail("当前申诉不属于您处理");

        appeal.setAppealStatus(dto.getAppealStatus());
        appeal.setAdminReply(dto.getAdminReply() == null ? "" : dto.getAdminReply().trim());
        appeal.setProcessTime(LocalDateTime.now());
        appealMapper.updateById(appeal);

        if (dto.getAppealStatus() == 1 && appeal.getRelatedPunishId() != null) {
            PunishCancelDTO cancelDTO = new PunishCancelDTO();
            cancelDTO.setPunishLogId(appeal.getRelatedPunishId());
            cancelDTO.setCancelReason("申诉成立，自动撤销处罚");
            punishService.cancelPunish(cancelDTO);
        }

        if (appeal.getRelatedReportType() == 1) {
            UserReport report = userReportMapper.selectById(appeal.getRelatedReportId());
            if (report != null && report.getIsDelete() == 0) {
                if (dto.getAppealStatus() == 2) report.setAppealCount((report.getAppealCount() == null ? 0 : report.getAppealCount()) + 1);
                report.setProcessTime(LocalDateTime.now());
                userReportMapper.updateById(report);
            }
        } else if (appeal.getRelatedReportType() == 2) {
            MessageReport report = messageReportMapper.selectById(appeal.getRelatedReportId());
            if (report != null && report.getIsDelete() == 0 && dto.getAppealStatus() == 2) {
                report.setAppealCount((report.getAppealCount() == null ? 0 : report.getAppealCount()) + 1);
                messageReportMapper.updateById(report);
            }
        } else if (appeal.getRelatedReportType() == 3) {
            TeamReport report = teamReportMapper.selectById(appeal.getRelatedReportId());
            if (report != null && report.getIsDelete() == 0) {
                report.setProcessTime(LocalDateTime.now());
                teamReportMapper.updateById(report);
            }
        }

        return Result.ok("申诉已处理");
    }

    /**
     * 读取指定举报已使用申诉次数。
     *
     * @param relatedReportType 关联举报类型（1-用户举报，2-消息举报，3-团队举报）
     * @param relatedReportId 关联举报ID
     * @return 已使用申诉次数
     */
    private int getAppealCount(Integer relatedReportType, Long relatedReportId) {
        if (relatedReportType == 1) {
            UserReport report = userReportMapper.selectById(relatedReportId);
            if (report == null || report.getIsDelete() == 1) throw new BusinessException("关联举报不存在");
            return report.getAppealCount() == null ? 0 : report.getAppealCount();
        }
        if (relatedReportType == 2) {
            MessageReport report = messageReportMapper.selectById(relatedReportId);
            if (report == null || report.getIsDelete() == 1) throw new BusinessException("关联举报不存在");
            return report.getAppealCount() == null ? 0 : report.getAppealCount();
        }
        if (relatedReportType == 3) {
            TeamReport report = teamReportMapper.selectById(relatedReportId);
            if (report == null || report.getIsDelete() == 1) throw new BusinessException("关联举报不存在");
            return 0;
        }
        throw new BusinessException("关联举报类型不支持");
    }

    /**
     * 为申诉随机分配一个未处理过该举报的管理员。
     *
     * @param relatedReportId 关联举报ID
     * @param relatedReportType 关联举报类型
     * @return 可分配管理员用户ID，无可用管理员时返回 null
     */
    private Long allocateAdmin(Long relatedReportId, Integer relatedReportType) {
        LambdaQueryWrapper<Appeal> appealQw = new LambdaQueryWrapper<>();
        appealQw.eq(Appeal::getRelatedReportId, relatedReportId)
                .eq(Appeal::getRelatedReportType, relatedReportType)
                .eq(Appeal::getIsDelete, 0)
                .isNotNull(Appeal::getAdminId);
        List<Appeal> usedAppeals = appealMapper.selectList(appealQw);
        Set<Long> usedAdminIds = usedAppeals.stream().map(Appeal::getAdminId).collect(Collectors.toCollection(HashSet::new));

        LambdaQueryWrapper<AdminUser> adminQw = new LambdaQueryWrapper<>();
        adminQw.eq(AdminUser::getAdminStatus, 1)
               .eq(AdminUser::getIsDelete, 0);
        List<AdminUser> admins = adminUserMapper.selectList(adminQw);

        List<Long> candidateAdminIds = new ArrayList<>();
        for (AdminUser admin : admins) {
            if (!usedAdminIds.contains(admin.getUserId())) {
                candidateAdminIds.add(admin.getUserId());
            }
        }
        if (candidateAdminIds.isEmpty()) {
            return null;
        }
        return candidateAdminIds.get(random.nextInt(candidateAdminIds.size()));
    }
}
