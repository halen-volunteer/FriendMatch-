package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Mapper.*;
import com.zero.usercenter.Model.*;
import com.zero.usercenter.Service.TeamService;
import com.zero.usercenter.aop.annotation.RequireTeamRole;
import com.zero.usercenter.aop.annotation.TeamRoleScope;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.mq.AsyncMessageService;
import com.zero.usercenter.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.zero.usercenter.utils.Number.*;

/**
 * 团队管理服务实现。
 * 负责团队创建、查询、加入审批、成员权限管理、禁言控制和系统通知分发。
 */
@Slf4j
@Service
public class TeamServiceImpl implements TeamService {

    @Resource private TeamMapper teamMapper;
    @Resource private TeamMemberMapper teamMemberMapper;
    @Resource private TeamApplyMapper teamApplyMapper;
    @Resource private UserMapper userMapper;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private SensitiveWordBs sensitiveWordBs;
    @Resource private SearchRecommendServiceImpl searchRecommendService;
    @Resource private AsyncMessageService asyncMessageService;

    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 创建团队并同步写入队长成员关系。
     */
    @Override
    @Transactional
    public Result createTeam(TeamCreateDTO dto) {
        // 1. 先做登录态和基础参数校验，避免非法团队数据进入后续流程。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto.getTeamName() == null || dto.getTeamName().trim().isEmpty()) return Result.fail("团队名称不能为空");
        String teamName = dto.getTeamName().trim();
        if (teamName.length() > 64) return Result.fail("团队名称长度必须在1-64字符之间");
        if (dto.getTeamIntro() != null && dto.getTeamIntro().length() > 512) return Result.fail("团队简介不能超过512字符");
        if (dto.getTeamTags() != null && !dto.getTeamTags().isEmpty()) {
            String[] tags = dto.getTeamTags().split(",");
            if (tags.length > 5) return Result.fail("团队标签最多5个");
            for (String tag : tags) if (tag.trim().length() > 20) return Result.fail("单个标签长度不能超过20字符");
        }
        if (dto.getMaxMember() != null && (dto.getMaxMember() < 1 || dto.getMaxMember() > 1000)) return Result.fail("最大成员数必须在1-1000之间");
        if (dto.getTeamType() == null || dto.getTeamType() < 1 || dto.getTeamType() > 2) return Result.fail("团队类型参数无效");
        if (dto.getJoinRule() == null || dto.getJoinRule() < 1 || dto.getJoinRule() > 3) return Result.fail("加入规则参数无效");
        if (dto.getJoinRule() == 3 && (dto.getJoinPassword() == null || dto.getJoinPassword().trim().isEmpty())) return Result.fail("密码加入方式必须设置密码");

        // 2. 敏感词先过滤掉，避免创建完成后还要做脏数据修正。
        if (sensitiveWordBs.contains(teamName)) return Result.fail("团队名称包含敏感词");
        if (dto.getTeamIntro() != null && sensitiveWordBs.contains(dto.getTeamIntro())) return Result.fail("团队简介包含敏感词");

        // 3. 组装团队实体并落库。
        Team team = new Team();
        team.setTeamName(teamName);
        team.setTeamAvatar(dto.getTeamAvatar() != null ? dto.getTeamAvatar() : "");
        team.setTeamIntro(dto.getTeamIntro() != null ? dto.getTeamIntro() : "");
        team.setTeamTags(dto.getTeamTags() != null ? dto.getTeamTags() : "");
        team.setCreatorId(userId);
        team.setMaxMember(dto.getMaxMember() != null ? dto.getMaxMember() : 200);
        team.setTeamType(dto.getTeamType());
        team.setJoinRule(dto.getJoinRule());
        team.setTeamAllMute(0);
        team.setIsDelete(0);
        team.setJoinPassword(dto.getJoinRule() == 3 ? passwordEncoder.encode(dto.getJoinPassword().trim()) : "");
        teamMapper.insert(team);

        // 4. 团队创建后立即插入队长成员记录，保证后续权限判断和成员列表都有统一入口。
        TeamMember leader = new TeamMember();
        leader.setTeamId(team.getId());
        leader.setUserId(userId);
        leader.setRoleType(1);
        leader.setTeamMuteType(0);
        leader.setJoinTime(LocalDateTime.now());
        leader.setLastActiveTime(LocalDateTime.now());
        leader.setIsQuit(0);
        leader.setIsDelete(0);
        teamMemberMapper.insert(leader);

        // 5. 返回统一的团队展示结构，前端可以直接渲染。
        return Result.ok(buildTeamResponse(team, 1, 1));
    }

    /**
     * 获取团队列表。
     * 支持按团队类型筛选、按创建时间或 ID 排序，并批量补齐成员数和当前用户是否已加入。
     */
    @Override
    public Result getTeamList(int page, int pageSize, Integer teamType, String sort) {
        // 1. 先确认当前用户已登录，团队列表也依赖用户态判断“是否已加入”。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 2. 先按数据库条件粗筛，再在内存里补成员数和加入状态。
        // 构建分页查询。
        Page<Team> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<Team> qw = new LambdaQueryWrapper<>();
        qw.eq(Team::getIsDelete, 0);
        if (teamType != null) qw.eq(Team::getTeamType, teamType);
        if ("createTime".equals(sort)) qw.orderByDesc(Team::getCreateTime);
        else qw.orderByDesc(Team::getId);
        Page<Team> result = teamMapper.selectPage(pageObj, qw);
        if (result.getRecords().isEmpty()) return Result.ok(Collections.emptyList(), 0L);

        // 3. 批量统计成员数，避免对每个团队单独查一次数据库。
        // 批量查成员数
        List<Long> teamIds = result.getRecords().stream().map(Team::getId).collect(Collectors.toList());
        Map<Long, Integer> memberCountMap = batchGetMemberCount(teamIds);

        // 4. 批量判断当前用户是否已加入这些团队。
        // 批量查当前用户是否为成员
        Set<Long> myTeamIds = getMyTeamIds(userId, teamIds);
        List<Map<String, Object>> list = result.getRecords().stream()
                .map(t -> buildTeamResponse(t,
                        memberCountMap.getOrDefault(t.getId(), 0),
                        myTeamIds.contains(t.getId()) ? 1 : 0))
                .collect(Collectors.toList());
        return Result.ok(list, result.getTotal());
    }

    /**
     * 搜索团队。
     * 支持按团队名称或标签模糊搜索，并批量补齐成员数和加入状态。
     */
    @Override
    public Result searchTeam(String keyword, String type, int page, int pageSize) {
        // 1. 参数校验放前面，避免把无意义请求送到数据库。
        // 参数校验。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (keyword == null || keyword.trim().isEmpty()) return Result.fail("搜索关键词不能为空");
        if (!"name".equals(type) && !"tag".equals(type)) return Result.fail("搜索类型无效");

        // 2. 根据搜索类型构建查询条件，先让数据库完成第一轮筛选。
        // 根据关键词搜索团队。
        Page<Team> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<Team> qw = new LambdaQueryWrapper<>();
        qw.eq(Team::getIsDelete, 0);
        if ("name".equals(type)) qw.like(Team::getTeamName, keyword.trim());
        else qw.like(Team::getTeamTags, keyword.trim());
        Page<Team> result = teamMapper.selectPage(pageObj, qw);
        if (result.getRecords().isEmpty()) return Result.ok(Collections.emptyList(), 0L);

        // 3. 结果集再统一补成员数和“当前用户是否已加入”状态。
        // 批量查成员数
        List<Long> teamIds = result.getRecords().stream().map(Team::getId).collect(Collectors.toList());
        Map<Long, Integer> memberCountMap = batchGetMemberCount(teamIds);
        // 批量查当前用户是否为成员
        Set<Long> myTeamIds = getMyTeamIds(userId, teamIds);
        List<Map<String, Object>> list = result.getRecords().stream()
                .map(t -> buildTeamResponse(t,
                        memberCountMap.getOrDefault(t.getId(), 0),
                        myTeamIds.contains(t.getId()) ? 1 : 0))
                .collect(Collectors.toList());
        return Result.ok(list, result.getTotal());
    }

    /**
     * 获取团队详情。
     * 返回团队基础信息、成员数，以及当前用户在团队中的角色标识。
     */
    @Override
    public Result getTeamDetail(Long teamId) {
        // 1. 详情页必须基于登录态判断当前用户能看到什么。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (teamId == null || teamId <= 0) return Result.fail("团队ID无效");

        // 2. 先查团队本体，再判断成员状态和角色。
        Team team = teamMapper.selectById(teamId);
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        Map<String, Object> resp = buildTeamResponse(team, getMemberCount(teamId), isMember(teamId, userId) ? 1 : 0);
        TeamMember member = getMemberRecord(teamId, userId);
        resp.put("roleType", member != null ? member.getRoleType() : -1);
        return Result.ok(resp);
    }

    /**
     * 更新团队信息。
     * 仅队长可操作，且最大成员数不能小于当前实际成员数。
     */
    @Override
    @Transactional
    @RequireTeamRole(
            value = TeamRoleScope.LEADER,
            teamId = "#p0.teamId",
            invalidTeamIdMessage = "团队ID不能为空",
            forbiddenMessage = "仅队长可编辑团队信息")
    public Result updateTeam(TeamUpdateDTO dto) {
        // 1. 先确认目标团队存在，避免空更新和脏写。
        if (dto.getTeamId() == null) return Result.fail("团队ID不能为空");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");

        // 2. 逐字段覆盖更新，哪些字段传了就改哪些字段。
        if (dto.getTeamName() != null) {
            String name = dto.getTeamName().trim();
            if (name.isEmpty() || name.length() > 64) return Result.fail("团队名称长度必须在1-64字符之间");
            if (sensitiveWordBs.contains(name)) return Result.fail("团队名称包含敏感词");
            team.setTeamName(name);
        }
        if (dto.getTeamAvatar() != null) team.setTeamAvatar(dto.getTeamAvatar());
        if (dto.getTeamIntro() != null) {
            if (dto.getTeamIntro().length() > 512) return Result.fail("团队简介不能超过512字符");
            if (sensitiveWordBs.contains(dto.getTeamIntro())) return Result.fail("团队简介包含敏感词");
            team.setTeamIntro(dto.getTeamIntro());
        }
        if (dto.getTeamTags() != null) {
            String[] tags = dto.getTeamTags().split(",");
            if (tags.length > 5) return Result.fail("团队标签最多5个");
            team.setTeamTags(dto.getTeamTags());
        }
        if (dto.getMaxMember() != null) {
            if (dto.getMaxMember() < 1 || dto.getMaxMember() > 1000) return Result.fail("最大成员数必须在1-1000之间");
            if (dto.getMaxMember() < getMemberCount(dto.getTeamId())) return Result.fail("最大成员数不能低于当前成员数");
            team.setMaxMember(dto.getMaxMember());
        }
        if (dto.getTeamType() != null) {
            if (dto.getTeamType() < 1 || dto.getTeamType() > 2) return Result.fail("团队类型参数无效");
            team.setTeamType(dto.getTeamType());
        }
        if (dto.getJoinRule() != null) {
            if (dto.getJoinRule() < 1 || dto.getJoinRule() > 3) return Result.fail("加入规则参数无效");
            if (dto.getJoinRule() == 3) {
                if ((team.getJoinRule() == null || team.getJoinRule() != 3)
                        && (dto.getJoinPassword() == null || dto.getJoinPassword().trim().isEmpty())) {
                    return Result.fail("密码加入方式必须设置密码");
                }
                if (dto.getJoinPassword() != null && !dto.getJoinPassword().trim().isEmpty()) {
                    team.setJoinPassword(passwordEncoder.encode(dto.getJoinPassword().trim()));
                }
            } else {
                team.setJoinPassword("");
            }
            team.setJoinRule(dto.getJoinRule());
        }
        teamMapper.updateById(team);
        return Result.ok("团队信息已更新");
    }

    /**
     * 解散团队。
     * 仅队长可操作，软删除团队与成员关系，并异步清理通知和禁言缓存。
     */
    @Override
    @Transactional
    @RequireTeamRole(
            value = TeamRoleScope.LEADER,
            teamId = "#p0",
            forbiddenMessage = "仅队长可解散团队")
    public Result dissolveTeam(Long teamId) {
        // 1. 先软删除团队本体，后续成员关系和缓存清理交给异步任务处理。
        Long userId = UserHolder.getUserId();
        if (teamId == null || teamId <= 0) return Result.fail("团队ID无效");
        Team team = teamMapper.selectById(teamId);
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        team.setIsDelete(1);
        teamMapper.updateById(team);

        // 2. 批量结束未退出成员关系，避免团队解散后还残留可用成员记录。
        LambdaUpdateWrapper<TeamMember> uw = new LambdaUpdateWrapper<>();
        uw.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getIsQuit, 0)
          .set(TeamMember::getIsQuit, 1).set(TeamMember::getQuitTime, LocalDateTime.now());
        teamMemberMapper.update(null, uw);

        // 3. 通知、缓存清理放到异步线程，避免主流程被消息发送拖慢。
        // 异步发送通知和清理缓存
        final String teamName = team.getTeamName();
        Thread.ofVirtual().start(() -> {
            try {
                sendNoticeToAllMembers(teamId, userId, 5, "你所在的团队【" + teamName + "】已被解散", null);
                stringRedisTemplate.delete(TEAM_ALL_MUTE_KEY + teamId);
            } catch (Exception e) {
                log.error("解散团队异步任务执行失败，teamID:{}",teamId,e);
            }
        });
        
        return Result.ok("团队已解散");
    }

    // ==================== 加入团队 ====================

    /**
     * 申请加入团队。
     * 仅适用于“申请加入”模式，且同一用户不能重复提交待审核申请。
     */
    @Override
    @Transactional
    public Result applyTeam(TeamApplyDTO dto) {
        // 1. 申请入队前先确认登录、团队存在以及加入规则是否允许申请。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto.getTeamId() == null) return Result.fail("团队ID不能为空");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        if (team.getJoinRule() != 1) return Result.fail("该团队不支持申请加入");
        if (isMember(dto.getTeamId(), userId)) return Result.fail("已经是团队成员了");
        if (getMemberCount(dto.getTeamId()) >= team.getMaxMember()) return Result.fail("团队成员已满");

        LambdaQueryWrapper<TeamApply> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamApply::getTeamId, dto.getTeamId()).eq(TeamApply::getApplyUserId, userId)
          .eq(TeamApply::getAuditStatus, 0).eq(TeamApply::getIsDelete, 0);

        if (teamApplyMapper.selectOne(qw) != null) return Result.fail("已提交申请，请等待审核");
        TeamApply apply = new TeamApply();
        apply.setTeamId(dto.getTeamId());
        apply.setApplyUserId(userId);
        apply.setApplyMsg(dto.getApplyMsg() != null ? dto.getApplyMsg() : "");
        apply.setAuditStatus(0);
        apply.setIsDelete(0);
        teamApplyMapper.insert(apply);

        // 2. 申请记录入库后，再异步通知管理员，避免阻塞用户主请求。
        // 异步发送通知给管理员，不阻塞主流程
        String applicantNickname = userMapper.selectById(userId).getUserNickname();
        String teamName = team.getTeamName();
        Long applyTeamId = dto.getTeamId();
        Long applyUserId = userId;
        Thread.ofVirtual().start(() ->
            sendNoticeToAdmins(applyTeamId, "用户【" + applicantNickname + "】申请加入团队【" + teamName + "】，请及时审核", applyUserId)
        );
        return Result.ok("申请已提交，请等待审核");
    }

    /**
     * 密码加入团队。
     * 仅适用于密码加入模式，团队密码使用 BCrypt 校验。
     */
    @Override
    @Transactional
    public Result joinByPassword(TeamJoinByPasswordDTO dto) {
        // 1. 先校验团队、密码和成员容量，避免无效加入请求。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto.getTeamId() == null) return Result.fail("团队ID不能为空");
        if (dto.getPassword() == null || dto.getPassword().trim().isEmpty()) return Result.fail("密码不能为空");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        if (team.getJoinRule() != 3) return Result.fail("该团队不支持密码加入");
        if (isMember(dto.getTeamId(), userId)) return Result.fail("已经是团队成员了");
        if (getMemberCount(dto.getTeamId()) >= team.getMaxMember()) return Result.fail("团队成员已满");

        // 2. 密码校验通过后，直接复用统一的成员入队逻辑。
        if (!passwordEncoder.matches(dto.getPassword().trim(), team.getJoinPassword())) return Result.fail("密码错误");
        addMember(dto.getTeamId(), userId, 3, 1, null);
        return Result.ok("加入团队成功");
    }

    /**
     * 邀请成员加入团队。
     * 仅队长或管理员可操作，邀请成功后直接入队，并异步通知被邀请人。
     */
    @Override
    @Transactional
    @RequireTeamRole(
            value = TeamRoleScope.ADMIN_OR_LEADER,
            teamId = "#p0.teamId",
            invalidTeamIdMessage = "参数不能为空",
            forbiddenMessage = "仅队长/管理员可邀请成员")
    public Result inviteMember(TeamInviteDTO dto) {
        // 1. 邀请动作先校验目标用户、团队状态和成员上限。
        Long userId = UserHolder.getUserId();
        if (dto.getTeamId() == null || dto.getUserId() == null) return Result.fail("参数不能为空");
        if (userId.equals(dto.getUserId())) return Result.fail("不能邀请自己");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        User invitee = userMapper.selectById(dto.getUserId());
        if (invitee == null || invitee.getIsDelete() == 1) return Result.fail("目标用户不存在");
        if (isMember(dto.getTeamId(), dto.getUserId())) return Result.fail("该用户已是团队成员");
        if (getMemberCount(dto.getTeamId()) >= team.getMaxMember()) return Result.fail("团队成员已满");

        // 2. 成员记录先写库，再异步补发邀请通知。
        addMember(dto.getTeamId(), dto.getUserId(), 3, 2, userId);
        // 异步发送邀请通知，不阻塞主流程
        Long inviteUserId = dto.getUserId();
        Long inviteTeamId = dto.getTeamId();
        String inviteTeamName = team.getTeamName();
        Thread.ofVirtual().start(() ->
            sendNotice(inviteUserId, 3, "你已被邀请加入团队【" + inviteTeamName + "】", inviteTeamId)
        );
        return Result.ok("邀请成功");
    }

    // ==================== 审批 ====================

    /**
     * 获取待审批申请列表
     * 仅队长/管理员可查看，分页返回 auditStatus=0 的申请记录
     *
     * @param teamId   团队ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 申请列表及总数
     */
    @Override
    @RequireTeamRole(
            value = TeamRoleScope.ADMIN_OR_LEADER,
            teamId = "#p0",
            forbiddenMessage = "仅队长/管理员可查看申请列表")
    public Result getPendingApplyList(Long teamId, int page, int pageSize) {
        // 1. 先按申请表分页，再回查申请人信息做展示补齐。
        Page<TeamApply> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<TeamApply> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamApply::getTeamId, teamId).eq(TeamApply::getAuditStatus, 0)
          .eq(TeamApply::getIsDelete, 0).orderByDesc(TeamApply::getCreateTime);
        Page<TeamApply> result = teamApplyMapper.selectPage(pageObj, qw);
        List<Map<String, Object>> list = result.getRecords().stream().map(apply -> {
            User applicant = userMapper.selectById(apply.getApplyUserId());
            Map<String, Object> m = new HashMap<>();
            m.put("applyId", apply.getId());
            m.put("applicantId", apply.getApplyUserId());
            m.put("userNickname", applicant != null ? applicant.getUserNickname() : "");
            m.put("userAvatar", applicant != null ? applicant.getUserAvatar() : "");
            m.put("applyMsg", apply.getApplyMsg());
            m.put("createTime", apply.getCreateTime());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(list, result.getTotal());
    }

    /**
     * 审批加入申请。
     * 仅队长或管理员可操作，通过时直接加入团队，拒绝时可附带审核说明。
     * 该场景需要先按 applyId 反查申请记录，再确定 teamId 与申请状态，因此当前保留在业务层显式校验。
     */
    @Override
    @Transactional
    public Result auditApply(TeamAuditDTO dto) {
        // 1. 审批动作先确认申请存在、状态合法，并且当前操作者有权限处理。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto.getApplyId() == null) return Result.fail("申请ID不能为空");
        if (dto.getAuditStatus() == null || (dto.getAuditStatus() != 1 && dto.getAuditStatus() != 2))
            return Result.fail("审核状态参数无效");
        TeamApply apply = teamApplyMapper.selectById(dto.getApplyId());
        if (apply == null || apply.getIsDelete() == 1) return Result.fail("申请记录不存在");
        if (apply.getAuditStatus() != 0) return Result.fail("该申请已处理");
        if (!isAdmin(apply.getTeamId(), userId)) return Result.fail("仅队长/管理员可审批申请");
        Team team = teamMapper.selectById(apply.getTeamId());
        if (dto.getAuditStatus() == 1) {
            // 2. 审批通过时直接把申请人转成团队成员，并补发通过通知。
            if (getMemberCount(apply.getTeamId()) >= team.getMaxMember()) return Result.fail("团队成员已满");
            addMember(apply.getTeamId(), apply.getApplyUserId(), 3, 3, null);
            // 异步发送审批通过通知
            Long auditApplyUserId = apply.getApplyUserId();
            Long auditTeamId = apply.getTeamId();
            String auditTeamName = team.getTeamName();
            Thread.ofVirtual().start(() ->
                sendNotice(auditApplyUserId, 3, "你加入团队【" + auditTeamName + "】的申请已通过", auditTeamId)
            );
        } else {
            // 3. 审批拒绝时只写回审核意见，不创建成员关系。
            String noticeMsg = "你加入团队【" + team.getTeamName() + "】的申请已被拒绝";
            if (dto.getAuditMsg() != null && !dto.getAuditMsg().isEmpty()) noticeMsg += "：" + dto.getAuditMsg();
            // 异步发送审批拒绝通知
            Long rejectApplyUserId = apply.getApplyUserId();
            Long rejectTeamId = apply.getTeamId();
            final String finalNoticeMsg = noticeMsg;
            Thread.ofVirtual().start(() ->
                sendNotice(rejectApplyUserId, 4, finalNoticeMsg, rejectTeamId)
            );
        }
        // 4. 无论通过还是拒绝，最后都要把申请状态、审核人和审核说明回写到申请表。
        LambdaUpdateWrapper<TeamApply> uw = new LambdaUpdateWrapper<>();
        uw.eq(TeamApply::getId, dto.getApplyId())
          .set(TeamApply::getAuditStatus, dto.getAuditStatus())
          .set(TeamApply::getAuditUserId, userId)
          .set(TeamApply::getAuditMsg, dto.getAuditMsg() != null ? dto.getAuditMsg() : "")
          .set(TeamApply::getAuditTime, LocalDateTime.now());
        teamApplyMapper.update(null, uw);
        return Result.ok(dto.getAuditStatus() == 1 ? "已通过申请" : "已拒绝申请");
    }

    // ==================== 成员管理 ====================

    /**
     * 获取团队成员列表。
     * 按角色顺序返回未退出成员，并附带头像、简介、禁言状态等展示字段。
     */
    @Override
    public Result getMemberList(Long teamId, int page, int pageSize) {
        return getMemberListByRole(teamId, null, page, pageSize);
    }

    /**
     * 按角色筛选团队成员列表。
     */
    @Override
    public Result getMemberListByRole(Long teamId, Integer roleType, int page, int pageSize) {
        // 1. 先校验团队存在，再按角色和分页做数据库查询。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (teamId == null || teamId <= 0) return Result.fail("团队ID无效");
        Team team = teamMapper.selectById(teamId);
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");


        Page<TeamMember> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getIsQuit, 0);
        if (roleType != null) {
            if (roleType < 1 || roleType > 3) return Result.fail("角色类型无效");
            qw.eq(TeamMember::getRoleType, roleType);
        }
        qw.orderByAsc(TeamMember::getRoleType);

        // 2. 查询结果再补齐用户基础信息和角色名称，方便前端直接展示。
        Page<TeamMember> result = teamMemberMapper.selectPage(pageObj, qw);
        List<Map<String, Object>> list = result.getRecords().stream().map(m -> {
            User u = userMapper.selectById(m.getUserId());
            Map<String, Object> info = new HashMap<>();
            info.put("userId", m.getUserId());
            info.put("roleType", m.getRoleType());
            info.put("roleName", roleNameOf(m.getRoleType()));
            info.put("userNickname", u != null ? u.getUserNickname() : "");
            info.put("userAvatar", u != null ? u.getUserAvatar() : "");
            info.put("userIntro", u != null ? u.getUserIntro() : "");
            info.put("joinTime", m.getJoinTime());
            info.put("lastActiveTime", m.getLastActiveTime());
            info.put("teamMuteType", m.getTeamMuteType());
            info.put("teamMuteUnpunishTime", m.getTeamMuteUnpunishTime());
            return info;
        }).collect(Collectors.toList());
        return Result.ok(list, result.getTotal());
    }

    /**
     * 移除团队成员。
     * 队长可移除管理员和普通成员，管理员只能移除普通成员，同时会清理目标成员的团队禁言状态。
     */
    @Override
    @Transactional
    @RequireTeamRole(
            value = TeamRoleScope.ADMIN_OR_LEADER,
            teamId = "#p0.teamId",
            invalidTeamIdMessage = "参数不能为空",
            forbiddenMessage = "仅队长/管理员可移除成员")
    public Result removeMember(TeamMemberOperationDTO dto) {
        // 1. 先确认目标成员真实存在，并检查操作者与目标成员之间的角色约束。
        Long userId = UserHolder.getUserId();
        if (dto.getTeamId() == null || dto.getUserId() == null) return Result.fail("参数不能为空");
        if (userId.equals(dto.getUserId())) return Result.fail("不能移除自己");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        TeamMember target = getMemberRecord(dto.getTeamId(), dto.getUserId());
        if (target == null) return Result.fail("该用户不是团队成员");
        if (target.getRoleType() == 1) return Result.fail("不能移除队长");
        TeamMember operator = getMemberRecord(dto.getTeamId(), userId);
        if (operator != null && operator.getRoleType() == 2 && target.getRoleType() == 2)
            return Result.fail("管理员不能移除其他管理员");

        // 2. 软退出成员，并同步清理禁言状态。
        LambdaUpdateWrapper<TeamMember> uw = new LambdaUpdateWrapper<>();
        uw.eq(TeamMember::getTeamId, dto.getTeamId()).eq(TeamMember::getUserId, dto.getUserId())
          .set(TeamMember::getIsQuit, 1)
          .set(TeamMember::getQuitTime, LocalDateTime.now())
          .set(TeamMember::getTeamMuteType, 0)
          .set(TeamMember::getTeamMuteUnpunishTime, null);
        teamMemberMapper.update(null, uw);
        // 异步发送移除通知 + 清理禁言缓存，不阻塞主流程
        Long removedUserId = dto.getUserId();
        Long removeTeamId = dto.getTeamId();
        String removeTeamName = team.getTeamName();
        Thread.ofVirtual().start(() -> {
            sendNotice(removedUserId, 5, "你已被移出团队【" + removeTeamName + "】", removeTeamId);
            stringRedisTemplate.delete(TEAM_MUTE_KEY + removeTeamId + "_" + removedUserId);
        });
        return Result.ok("已移除成员");
    }

    /**
     * 修改成员角色。
     * 仅队长可操作，只允许在管理员和普通成员之间切换。
     */
    @Override
    @Transactional
    @RequireTeamRole(
            value = TeamRoleScope.LEADER,
            teamId = "#p0.teamId",
            invalidTeamIdMessage = "参数不能为空",
            forbiddenMessage = "仅队长可修改成员角色")
    public Result updateMemberRole(TeamMemberOperationDTO dto) {
        // 1. 先校验角色合法性和目标成员身份。
        if (dto.getTeamId() == null || dto.getUserId() == null || dto.getRoleType() == null) return Result.fail("参数不能为空");
        if (dto.getRoleType() != 2 && dto.getRoleType() != 3) return Result.fail("角色类型只能设置为2(管理员)或3(普通成员)");
        TeamMember target = getMemberRecord(dto.getTeamId(), dto.getUserId());
        if (target == null) return Result.fail("该用户不是团队成员");
        if (target.getRoleType() == 1) return Result.fail("不能修改队长角色");

        // 2. 直接覆盖 roleType，保持团队角色模型简单清晰。
        LambdaUpdateWrapper<TeamMember> uw = new LambdaUpdateWrapper<>();
        uw.eq(TeamMember::getTeamId, dto.getTeamId()).eq(TeamMember::getUserId, dto.getUserId())
          .set(TeamMember::getRoleType, dto.getRoleType());
        teamMemberMapper.update(null, uw);
        return Result.ok("成员角色已更新");
    }

    /**
     * 转让队长权限。
     * 原队长会降为管理员，目标成员升级为队长，并收到系统通知。
     */
    @Override
    @Transactional
    @RequireTeamRole(
            value = TeamRoleScope.LEADER,
            teamId = "#p0.teamId",
            invalidTeamIdMessage = "参数不能为空",
            forbiddenMessage = "仅队长可转让权限")
    public Result transferLeader(TeamMemberOperationDTO dto) {
        // 1. 队长转让前，先确认目标成员有效且不是自己。
        Long userId = UserHolder.getUserId();

        Long targetUserId = dto.getUserId() != null ? dto.getUserId() : dto.getNewCaptainId();
        if (dto.getTeamId() == null || targetUserId == null) return Result.fail("参数不能为空");
        if (userId.equals(targetUserId)) return Result.fail("不能转让给自己");

        TeamMember target = getMemberRecord(dto.getTeamId(), targetUserId);
        if (target == null) return Result.fail("目标用户不是团队成员");

        // 2. 原队长降为管理员，新成员升为队长。
        LambdaUpdateWrapper<TeamMember> uw1 = new LambdaUpdateWrapper<>();
        uw1.eq(TeamMember::getTeamId, dto.getTeamId()).eq(TeamMember::getUserId, userId).set(TeamMember::getRoleType, 2);
        teamMemberMapper.update(null, uw1);

        LambdaUpdateWrapper<TeamMember> uw2 = new LambdaUpdateWrapper<>();
        uw2.eq(TeamMember::getTeamId, dto.getTeamId()).eq(TeamMember::getUserId, targetUserId).set(TeamMember::getRoleType, 1);
        teamMemberMapper.update(null, uw2);

        Team team = teamMapper.selectById(dto.getTeamId());
        User newLeader = userMapper.selectById(targetUserId);
        sendNotice(targetUserId, 3, "你已成为团队【" + team.getTeamName() + "】的新队长", dto.getTeamId());
        return Result.ok("队长权限已转让给【" + (newLeader != null ? newLeader.getUserNickname() : targetUserId) + "】");
    }

    /**
     * 退出团队。
     * 队长不能直接退出，普通成员和管理员退出时会同步清理自己的团队禁言状态。
     */
    @Override
    @Transactional
    public Result quitTeam(Long teamId) {
        // 1. 普通成员或管理员可主动退出，队长需要先转让权限或解散团队。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (teamId == null || teamId <= 0) return Result.fail("团队ID无效");
        TeamMember member = getMemberRecord(teamId, userId);
        if (member == null) return Result.fail("你不是该团队成员");
        if (member.getRoleType() == 1) return Result.fail("队长不能直接退出，请先转让队长权限或解散团队");

        // 2. 退出时同步清掉团队禁言状态和缓存。
        LambdaUpdateWrapper<TeamMember> uw = new LambdaUpdateWrapper<>();
        uw.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId)
          .set(TeamMember::getIsQuit, 1)
          .set(TeamMember::getQuitTime, LocalDateTime.now())
          .set(TeamMember::getTeamMuteType, 0)
          .set(TeamMember::getTeamMuteUnpunishTime, null);
        teamMemberMapper.update(null, uw);
        stringRedisTemplate.delete(TEAM_MUTE_KEY + teamId + "_" + userId);
        return Result.ok("已退出团队");
    }

    // ==================== 禁言管理 ====================

    /**
     * 设置或解除全员禁言。
     * 同步更新数据库和 Redis，并记录全员禁言截止时间。
     */
    @Override
    @Transactional
    @RequireTeamRole(
            value = TeamRoleScope.ADMIN_OR_LEADER,
            teamId = "#p0.teamId",
            invalidTeamIdMessage = "参数不能为空",
            forbiddenMessage = "仅队长/管理员可设置全员禁言")
    public Result muteAll(TeamMuteDTO dto) {
        // 1. 全员禁言先校验团队存在和时长参数，避免无效状态写入缓存。
        if (dto.getTeamId() == null || dto.getIsMute() == null) return Result.fail("参数不能为空");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");

        // 2. 根据开启/关闭动作计算禁言标记和到期时间，关闭时到期时间清空。
        int muteVal = dto.getIsMute() ? 1 : 0;
        LocalDateTime unpunishTime = null;
        if (dto.getIsMute()) {
            if (dto.getMuteDuration() == null || dto.getMuteDuration() <= 0) {
                return Result.fail("全员禁言时长必须大于0");
            }
            unpunishTime = LocalDateTime.now().plusMinutes(dto.getMuteDuration());
        }
        // 3. 数据库持久化团队全员禁言状态，保证重启后仍能恢复真实状态。
        LambdaUpdateWrapper<Team> uw = new LambdaUpdateWrapper<>();
        uw.eq(Team::getId, dto.getTeamId())
          .set(Team::getTeamAllMute, muteVal)
          .set(Team::getTeamAllMuteUnpunishTime, unpunishTime);
        teamMapper.update(null, uw);
        // 4. Redis 只缓存当前生效的禁言状态，便于发消息链路快速判断。
        if (dto.getIsMute()) {
            stringRedisTemplate.opsForValue().set(
                    TEAM_ALL_MUTE_KEY + dto.getTeamId(),
                    muteVal + "|" + unpunishTime,
                    Math.max(dto.getMuteDuration(), TEAM_MUTE_CACHE_TTL_MINUTES),
                    TimeUnit.MINUTES);
        } else {
            stringRedisTemplate.delete(TEAM_ALL_MUTE_KEY + dto.getTeamId());
        }
        return Result.ok(dto.getIsMute() ? "已开启全员禁言" : "已解除全员禁言");
    }

    /**
     * 禁言指定成员。
     * 队长可禁言管理员和普通成员，管理员只能禁言普通成员。
     */
    @Override
    @Transactional
    @RequireTeamRole(
            value = TeamRoleScope.ADMIN_OR_LEADER,
            teamId = "#p0.teamId",
            invalidTeamIdMessage = "参数不能为空",
            forbiddenMessage = "仅队长/管理员可禁言成员")
    public Result muteMember(TeamMuteDTO dto) {
        // 1. 先校验目标成员、禁言时长和操作者权限。
        Long userId = UserHolder.getUserId();
        if (dto.getTeamId() == null || dto.getUserId() == null || dto.getMuteDuration() == null)
            return Result.fail("参数不能为空");
        if (dto.getMuteDuration() <= 0) return Result.fail("禁言时长必须大于0");
        if (userId.equals(dto.getUserId())) return Result.fail("不能禁言自己");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        TeamMember target = getMemberRecord(dto.getTeamId(), dto.getUserId());
        if (target == null) return Result.fail("该用户不是团队成员");
        if (target.getRoleType() == 1) return Result.fail("不能禁言队长");
        TeamMember operator = getMemberRecord(dto.getTeamId(), userId);
        if (operator != null && operator.getRoleType() == 2 && target.getRoleType() == 2)
            return Result.fail("管理员不能禁言其他管理员");

        // 2. 计算本次成员禁言的到期时间，后续数据库和缓存都以它为准。
        LocalDateTime unpunishTime = LocalDateTime.now().plusMinutes(dto.getMuteDuration());

        // 3. 数据库和 Redis 同步写入，确保禁言状态既可持久化又能快速命中。
        LambdaUpdateWrapper<TeamMember> uw = new LambdaUpdateWrapper<>();
        uw.eq(TeamMember::getTeamId, dto.getTeamId()).eq(TeamMember::getUserId, dto.getUserId())
          .set(TeamMember::getTeamMuteType, 1)
          .set(TeamMember::getTeamMuteUnpunishTime, unpunishTime);
        teamMemberMapper.update(null, uw);
        // 更新 Redis 缓存。
        stringRedisTemplate.opsForValue().set(
                TEAM_MUTE_KEY + dto.getTeamId() + "_" + dto.getUserId(), "1",
                TEAM_MUTE_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return Result.ok("已禁言该成员 " + dto.getMuteDuration() + " 分钟");
    }

    /**
     * 解除指定成员禁言。
     * 仅队长或管理员可操作，并会同步清理 Redis 中的禁言缓存。
     */
    @Override
    @Transactional
    @RequireTeamRole(
            value = TeamRoleScope.ADMIN_OR_LEADER,
            teamId = "#p0.teamId",
            invalidTeamIdMessage = "参数不能为空",
            forbiddenMessage = "仅队长/管理员可解除禁言")
    public Result unmuteMember(TeamMuteDTO dto) {
        // 1. 先确认目标成员确实处于有效禁言状态，避免重复操作。
        if (dto.getTeamId() == null || dto.getUserId() == null) return Result.fail("参数不能为空");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        // 检查目标用户是否仍在禁言中（team_mute_type=1 且未到期）
        TeamMember target = getMemberRecord(dto.getTeamId(), dto.getUserId());
        if (target == null) return Result.fail("该用户不是团队成员");
        if (target.getTeamMuteType() != 1) return Result.fail("该用户当前未被禁言");
        // 禁言已自然到期（定时任务尚未清理），视为已解除
        if (target.getTeamMuteUnpunishTime() != null
                && !target.getTeamMuteUnpunishTime().isAfter(LocalDateTime.now())) {
            return Result.fail("该用户禁言已到期，无需手动解除");
        }

        // 2. 解除时同时更新数据库和缓存，避免前后端看到不一致的禁言状态。
        LambdaUpdateWrapper<TeamMember> uw = new LambdaUpdateWrapper<>();
        uw.eq(TeamMember::getTeamId, dto.getTeamId()).eq(TeamMember::getUserId, dto.getUserId())
          .set(TeamMember::getTeamMuteType, 0)
          .set(TeamMember::getTeamMuteUnpunishTime, null);
        teamMemberMapper.update(null, uw);
        // 3. 删除 Redis 缓存后，后续发消息权限判断会立即感知解除结果。
        stringRedisTemplate.delete(TEAM_MUTE_KEY + dto.getTeamId() + "_" + dto.getUserId());
        return Result.ok("已解除禁言");
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断用户是否为队长
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     * @return true 表示是队长（role_type=1），false 表示不是
     */
    private boolean isLeader(Long teamId, Long userId) {
        // 1. 先复用统一成员查询，确保只按“当前未退出成员”判断角色。
        TeamMember m = getMemberRecord(teamId, userId);
        return m != null && m.getRoleType() == 1;
    }

    /**
     * 判断用户是否为队长或管理员
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     * @return true 表示是队长或管理员（role_type=1/2），false 表示不是
     */
    private boolean isAdmin(Long teamId, Long userId) {
        // 1. 队长和管理员共用一套管理权限，因此这里统一识别 roleType=1/2。
        TeamMember m = getMemberRecord(teamId, userId);
        return m != null && (m.getRoleType() == 1 || m.getRoleType() == 2);
    }

    /**
     * 判断用户是否为团队成员（未退出）
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     * @return true 表示是未退出的成员，false 表示不是
     */
    private boolean isMember(Long teamId, Long userId) {
        // 1. 是否成员完全以是否存在未退出成员记录为准。
        return getMemberRecord(teamId, userId) != null;
    }

    /**
     * 获取成员记录（未退出）
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     * @return 成员记录，不存在或已退出时返回 null
     */
    private TeamMember getMemberRecord(Long teamId, Long userId) {
        // 1. 只查询当前团队内仍有效的成员记录，历史退队数据不参与业务判断。
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId)
          .eq(TeamMember::getUserId, userId)
          .eq(TeamMember::getIsQuit, 0);
        return teamMemberMapper.selectOne(qw);
    }

    /**
     * 获取团队当前成员数（未退出）。
     *
     * @param teamId 团队 ID
     * @return 当前未退出的成员总数
     */
    private int getMemberCount(Long teamId) {
        // 1. 成员数只统计未退出成员，避免解散/退出历史污染容量判断。
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getIsQuit, 0);
        return teamMemberMapper.selectCount(qw).intValue();
    }

    /**
     * 批量查询多个团队的成员数，避免 N+1 查询
     *
     * @param teamIds 团队 ID 列表
     * @return Map&lt;teamId, memberCount&gt;
     */
    private Map<Long, Integer> batchGetMemberCount(List<Long> teamIds) {
        // 1. 空团队列表直接返回空结果，避免生成无意义 SQL。
        if (teamIds == null || teamIds.isEmpty()) return Collections.emptyMap();

        // 2. 一次性查出这些团队的所有有效成员，避免列表页出现 N+1 查询。
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.in(TeamMember::getTeamId, teamIds).eq(TeamMember::getIsQuit, 0);
        List<TeamMember> members = teamMemberMapper.selectList(qw);
        Map<Long, Integer> countMap = new HashMap<>();

        // 3. 在内存里按 teamId 聚合成员数，减少数据库分组语句的复杂度。
        for (TeamMember m : members) {
            countMap.merge(m.getTeamId(), 1, Integer::sum);
        }
        return countMap;
    }

    /**
     * 批量查询当前用户在哪些团队中（is_quit=0）
     *
     * @param userId  用户 ID
     * @param teamIds 待过滤的团队 ID 列表
     * @return 用户所在团队的 teamId Set
     */
    private Set<Long> getMyTeamIds(Long userId, List<Long> teamIds) {
        // 1. 空列表直接返回，避免 in 条件为空带来异常或全表扫描风险。
        if (teamIds == null || teamIds.isEmpty()) return Collections.emptySet();

        // 2. 只筛出当前用户在候选团队中仍未退出的团队 ID，供列表页判断“我是否已加入”。
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getUserId, userId)
          .in(TeamMember::getTeamId, teamIds)
          .eq(TeamMember::getIsQuit, 0);
        return teamMemberMapper.selectList(qw).stream()
                .map(TeamMember::getTeamId)
                .collect(Collectors.toSet());
    }

    /**
     * 将角色编码转换为可读角色名称。
     *
     * @param roleType 角色编码
     * @return 中文角色名
     */
    private String roleNameOf(Integer roleType) {
        // 1. 统一在这里做角色文案映射，避免控制层或前端分散维护硬编码。
        if (roleType == null) return "未知";
        if (roleType == 1) return "队长";
        if (roleType == 2) return "管理员";
        if (roleType == 3) return "普通成员";
        return "未知";
    }

    /**
     * 添加成员到团队。
     * 这里默认由调用方保证人数限制、权限和幂等校验。
     *
     * @param teamId       团队 ID
     * @param userId       新成员用户 ID
     * @param roleType     角色类型（1-队长，2-管理员，3-普通成员）
     * @param joinSource   加入来源（1-直接加入，2-邀请加入，3-申请审批）
     * @param inviteUserId 邀请人ID（邀请加入时有值）
     */
    private void addMember(Long teamId, Long userId, int roleType, int joinSource, Long inviteUserId) {
        // 1. 先构造成员实体，写入团队角色、来源、禁言初始化状态等基础信息。
        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setRoleType(roleType);
        member.setJoinSource(joinSource);
        member.setInviteUserId(inviteUserId);
        member.setTeamMuteType(0);
        member.setJoinTime(LocalDateTime.now());
        member.setLastActiveTime(LocalDateTime.now());
        member.setIsQuit(0);
        member.setIsDelete(0);
        teamMemberMapper.insert(member);

        // 2. 团队成员变化会影响推荐结果，因此异步刷新推荐，避免拖慢入队主链路。
        // 团队成员变化会影响团队推荐和用户推荐，所以这里异步刷新推荐结果，
        // 避免把推荐重算成本叠加到入队主链路上。
        Thread.ofVirtual().start(() -> searchRecommendService.refreshRecommendForUser(userId));
    }

    /**
     * 发送系统通知给单个用户（落库 + WebSocket 实时推送）
     *
     * @param userId     接收通知的用户 ID
     * @param noticeType 通知类型（见 noticeType 枚举说明）
     * @param content    通知内容文本
     * @param relatedId  关联业务 ID（如团队 ID、申请人 ID 等）
     */
    private void sendNotice(Long userId, int noticeType, String content, Long relatedId) {
        // 1. 通知的真正落库和异步推送统一交给异步消息服务，当前方法只负责收口参数。
        asyncMessageService.sendSystemNotice(userId, noticeType, content, relatedId);
    }

    /**
     * 发送通知给团队所有管理员（队长+管理员），排除指定用户
     *
     * @param teamId        团队 ID
     * @param content       通知内容文本
     * @param excludeUserId 不发送通知的用户 ID（通常为操作发起人）
     */
    private void sendNoticeToAdmins(Long teamId, String content, Long excludeUserId) {
        // 1. 先筛出团队内所有未退出的队长和管理员。
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId)
          .in(TeamMember::getRoleType, 1, 2)
          .eq(TeamMember::getIsQuit, 0);
        List<TeamMember> admins = teamMemberMapper.selectList(qw);

        // 2. 逐个发送通知，并排除当前操作发起人，避免自己收到自己触发的提醒。
        for (TeamMember admin : admins) {
            if (!admin.getUserId().equals(excludeUserId)) {
                sendNotice(admin.getUserId(), 1, content, teamId);
            }
        }
    }

    /**
     * 发送通知给团队所有成员，排除指定用户
     *
     * @param teamId        团队 ID
     * @param excludeUserId 不发送通知的用户 ID（通常为操作发起人）
     * @param noticeType    通知类型
     * @param content       通知内容文本
     * @param relatedId     关联业务 ID
     */
    private void sendNoticeToAllMembers(Long teamId, Long excludeUserId, int noticeType, String content, Long relatedId) {
        // 1. 先查询团队内全部未退出成员。
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getIsQuit, 0);
        List<TeamMember> members = teamMemberMapper.selectList(qw);

        // 2. 遍历发送通知，同时排除操作发起人，减少无意义提示。
        for (TeamMember m : members) {
            if (!m.getUserId().equals(excludeUserId)) {
                sendNotice(m.getUserId(), noticeType, content, relatedId);
            }
        }
    }

    /**
     * 构建团队响应信息
     *
     * @param team        团队实体
     * @param memberCount 当前成员数
     * @param isMember    当前用户是否为成员（1-是，0-否）
     * @return 包含团队字段的响应 Map
     */
    private Map<String, Object> buildTeamResponse(Team team, int memberCount, int isMember) {
        Map<String, Object> map = new HashMap<>();

        // 1. 先放团队对外展示的基础资料字段。
        map.put("id", team.getId());
        map.put("teamName", team.getTeamName());
        map.put("teamAvatar", team.getTeamAvatar());
        map.put("teamIntro", team.getTeamIntro());
        map.put("teamTags", team.getTeamTags());
        map.put("creatorId", team.getCreatorId());
        map.put("maxMember", team.getMaxMember());
        map.put("teamType", team.getTeamType());
        map.put("joinRule", team.getJoinRule());
        map.put("teamAllMute", team.getTeamAllMute());
        map.put("teamAllMuteUnpunishTime", team.getTeamAllMuteUnpunishTime());

        // 2. 再补充列表页和详情页都依赖的动态状态字段。
        map.put("memberCount", memberCount);
        map.put("isMember", isMember);
        map.put("createTime", team.getCreateTime());
        return map;
    }
}

