package com.zero.usercenter.Service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Mapper.*;
import com.zero.usercenter.Model.*;
import com.zero.usercenter.Service.TeamService;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import com.zero.usercenter.websocket.ChatWebSocketHandler;
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
 * 团队管理模块 Service 实现类
 * 负责处理团队创建、查询、搜索、成员管理、加入审批、禁言等业务逻辑
 */
@Slf4j
@Service
public class TeamServiceImpl implements TeamService {

    @Resource private TeamMapper teamMapper;
    @Resource private TeamMemberMapper teamMemberMapper;
    @Resource private TeamApplyMapper teamApplyMapper;
    @Resource private UserMapper userMapper;
    @Resource private SystemNoticeMapper systemNoticeMapper;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private SensitiveWordBs sensitiveWordBs;
    @Resource private ChatWebSocketHandler chatWebSocketHandler;
    @Resource private SearchRecommendServiceImpl searchRecommendService;

    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 创建团队并写入队长成员关系。
     * @param dto 请求数据对象
     * @return 方法执行结果
     */
    @Override
    @Transactional
    public Result createTeam(TeamCreateDTO dto) {
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
        if (sensitiveWordBs.contains(teamName)) return Result.fail("团队名称包含敏感词");
        if (dto.getTeamIntro() != null && sensitiveWordBs.contains(dto.getTeamIntro())) return Result.fail("团队简介包含敏感词");

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
        return Result.ok(buildTeamResponse(team, 1, 1));
    }

    /**
     * 获取团队列表
     * 分页返回所有团队，支持按团队类型过滤和排序，批量查询成员数避免 N+1
     *
     * @param page     页码
     * @param pageSize 每页数量
     * @param teamType 团队类型（1-竞技，2-休闲），为 null 时查全部
     * @param sort     排序方式（createTime-按创建时间，其他-按ID降序）
     * @return 团队列表及总数
     */
    /**
     * getTeamList 的核心业务逻辑。
     * @param page 页码
     * @param pageSize 每页条数
     * @param teamType 团队类型
     * @param sort 排序字段
     * @return 方法执行结果
     */
    @Override
    public Result getTeamList(int page, int pageSize, Integer teamType, String sort) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        //构建分页查询
        Page<Team> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<Team> qw = new LambdaQueryWrapper<>();
        qw.eq(Team::getIsDelete, 0);
        if (teamType != null) qw.eq(Team::getTeamType, teamType);
        if ("createTime".equals(sort)) qw.orderByDesc(Team::getCreateTime);
        else qw.orderByDesc(Team::getId);
        Page<Team> result = teamMapper.selectPage(pageObj, qw);
        if (result.getRecords().isEmpty()) return Result.ok(Collections.emptyList(), 0L);
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
     * 搜索团队
     * 支持按团队名称或标签模糊搜索，批量查询成员数避免
     *
     * @param keyword  搜索关键词
     * @param type     搜索类型（name-按名称，tag-按标签）
     * @param page     页码
     * @param pageSize 每页数量
     * @return 搜索结果列表及总数
     */
    /**
     * searchTeam 的核心业务逻辑。
     * @param keyword 搜索关键词
     * @param type 类型参数
     * @param page 页码
     * @param pageSize 每页条数
     * @return 方法执行结果
     */
    @Override
    public Result searchTeam(String keyword, String type, int page, int pageSize) {
        //参数校验
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (keyword == null || keyword.trim().isEmpty()) return Result.fail("搜索关键词不能为空");
        if (!"name".equals(type) && !"tag".equals(type)) return Result.fail("搜索类型无效");

        //根据关键词搜索团队
        Page<Team> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<Team> qw = new LambdaQueryWrapper<>();
        qw.eq(Team::getIsDelete, 0);
        if ("name".equals(type)) qw.like(Team::getTeamName, keyword.trim());
        else qw.like(Team::getTeamTags, keyword.trim());
        Page<Team> result = teamMapper.selectPage(pageObj, qw);
        if (result.getRecords().isEmpty()) return Result.ok(Collections.emptyList(), 0L);
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
     * 获取团队详情
     * 返回团队完整信息，包含当前用户在团队中的角色（-1 表示非成员）
     *
     * @param teamId 团队ID
     * @return 团队详情，含 roleType 字段
     */
    /**
     * getTeamDetail 的核心业务逻辑。
     * @param teamId 团队ID
     * @return 方法执行结果
     */
    @Override
    public Result getTeamDetail(Long teamId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (teamId == null || teamId <= 0) return Result.fail("团队ID无效");
        Team team = teamMapper.selectById(teamId);
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        Map<String, Object> resp = buildTeamResponse(team, getMemberCount(teamId), isMember(teamId, userId) ? 1 : 0);
        TeamMember member = getMemberRecord(teamId, userId);
        resp.put("roleType", member != null ? member.getRoleType() : -1);
        return Result.ok(resp);
    }

    /**
     * 更新团队信息
     * 仅队长可操作，支持更新名称、头像、简介、标签、最大成员数
     * 最大成员数不能低于当前实际成员数
     *
     * @param dto 团队更新数据传输对象
     * @return 更新结果
     */
    /**
     * updateTeam 的核心业务逻辑。
     * @param dto 请求数据对象
     * @return 方法执行结果
     */
    @Override
    @Transactional
    public Result updateTeam(TeamUpdateDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto.getTeamId() == null) return Result.fail("团队ID不能为空");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        if (!isLeader(dto.getTeamId(), userId)) return Result.fail("仅队长可编辑团队信息");
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
        teamMapper.updateById(team);
        return Result.ok("团队信息已更新");
    }

    /**
     * 解散团队
     * 仅队长可操作，软删除团队并将所有成员标记为已退出
     * 异步发送解散通知给所有成员，并清理全员禁言 Redis 缓存
     *
     * @param teamId 团队ID
     * @return 解散结果
     */
    /**
     * dissolveTeam 的核心业务逻辑。
     * @param teamId 团队ID
     * @return 方法执行结果
     */
    @Override
    @Transactional
    public Result dissolveTeam(Long teamId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (teamId == null || teamId <= 0) return Result.fail("团队ID无效");
        Team team = teamMapper.selectById(teamId);
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        if (!isLeader(teamId, userId)) return Result.fail("仅队长可解散团队");
        team.setIsDelete(1);
        teamMapper.updateById(team);
        LambdaUpdateWrapper<TeamMember> uw = new LambdaUpdateWrapper<>();
        uw.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getIsQuit, 0)
          .set(TeamMember::getIsQuit, 1).set(TeamMember::getQuitTime, LocalDateTime.now());
        teamMemberMapper.update(null, uw);
        
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
     * 申请加入团队
     * 仅支持 joinRule=1（申请加入）的团队，不能重复提交申请
     * 异步通知团队管理员审核
     *
     * @param dto 申请数据传输对象（teamId、applyMsg）
     * @return 申请结果
     */
    /**
     * applyTeam 的核心业务逻辑。
     * @param dto 请求数据对象
     * @return 方法执行结果
     */
    @Override
    @Transactional
    public Result applyTeam(TeamApplyDTO dto) {
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
     * 密码加入团队
     * 仅支持 joinRule=3（密码加入）的团队，BCrypt 校验密码
     *
     * @param dto 密码加入数据传输对象（teamId、password）
     * @return 加入结果
     */
    /**
     * joinByPassword 的核心业务逻辑。
     * @param dto 请求数据对象
     * @return 方法执行结果
     */
    @Override
    @Transactional
    public Result joinByPassword(TeamJoinByPasswordDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto.getTeamId() == null) return Result.fail("团队ID不能为空");
        if (dto.getPassword() == null || dto.getPassword().trim().isEmpty()) return Result.fail("密码不能为空");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        if (team.getJoinRule() != 3) return Result.fail("该团队不支持密码加入");
        if (isMember(dto.getTeamId(), userId)) return Result.fail("已经是团队成员了");
        if (getMemberCount(dto.getTeamId()) >= team.getMaxMember()) return Result.fail("团队成员已满");
        if (!passwordEncoder.matches(dto.getPassword().trim(), team.getJoinPassword())) return Result.fail("密码错误");
        addMember(dto.getTeamId(), userId, 3, 1, null);
        return Result.ok("加入团队成功");
    }

    /**
     * 邀请成员加入团队
     * 仅队长/管理员可操作，直接加入无需审批，异步发送邀请通知
     *
     * @param dto 邀请数据传输对象（teamId、userId）
     * @return 邀请结果
     */
    /**
     * inviteMember 的核心业务逻辑。
     * @param dto 请求数据对象
     * @return 方法执行结果
     */
    @Override
    @Transactional
    public Result inviteMember(TeamInviteDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto.getTeamId() == null || dto.getUserId() == null) return Result.fail("参数不能为空");
        if (userId.equals(dto.getUserId())) return Result.fail("不能邀请自己");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        if (!isAdmin(dto.getTeamId(), userId)) return Result.fail("仅队长/管理员可邀请成员");
        User invitee = userMapper.selectById(dto.getUserId());
        if (invitee == null || invitee.getIsDelete() == 1) return Result.fail("目标用户不存在");
        if (isMember(dto.getTeamId(), dto.getUserId())) return Result.fail("该用户已是团队成员");
        if (getMemberCount(dto.getTeamId()) >= team.getMaxMember()) return Result.fail("团队成员已满");
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
    public Result getPendingApplyList(Long teamId, int page, int pageSize) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (!isAdmin(teamId, userId)) return Result.fail("仅队长/管理员可查看申请列表");
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
     * 审批加入申请
     * 仅队长/管理员可操作，auditStatus=1 通过（直接加入团队），auditStatus=2 拒绝
     * 异步发送审批结果通知给申请人
     *
     * @param dto 审批数据传输对象（applyId、auditStatus、auditMsg）
     * @return 审批结果
     */
    /**
     * auditApply 的核心业务逻辑。
     * @param dto 请求数据对象
     * @return 方法执行结果
     */
    @Override
    @Transactional
    public Result auditApply(TeamAuditDTO dto) {
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
     * 获取团队成员列表
     * 分页返回未退出的成员，按角色升序（队长→管理员→普通成员）
     * 包含成员用户信息、角色、禁言状态等
     *
     * @param teamId   团队ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 成员列表及总数
     */
    /**
     * getMemberList 的核心业务逻辑。
     * @param teamId 团队ID
     * @param page 页码
     * @param pageSize 每页条数
     * @return 方法执行结果
     */
    @Override
    public Result getMemberList(Long teamId, int page, int pageSize) {
        return getMemberListByRole(teamId, null, page, pageSize);
    }

    /**
     * getMemberListByRole 的核心业务逻辑。
     * @param teamId 团队ID
     * @param roleType 角色类型
     * @param page 页码
     * @param pageSize 每页条数
     * @return 方法执行结果
     */
    @Override
    public Result getMemberListByRole(Long teamId, Integer roleType, int page, int pageSize) {
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
     * 移除团队成员
     * 仅队长/管理员可操作，不能移除队长，管理员不能移除其他管理员
     * 异步发送被移除通知并清理该成员的禁言 Redis 缓存
     *
     * @param dto 成员操作数据传输对象（teamId、userId）
     * @return 移除结果
     */
    /**
     * removeMember 的核心业务逻辑。
     * @param dto 请求数据对象
     * @return 方法执行结果
     */
    @Override
    @Transactional
    public Result removeMember(TeamMemberOperationDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto.getTeamId() == null || dto.getUserId() == null) return Result.fail("参数不能为空");
        if (userId.equals(dto.getUserId())) return Result.fail("不能移除自己");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        if (!isAdmin(dto.getTeamId(), userId)) return Result.fail("仅队长/管理员可移除成员");
        TeamMember target = getMemberRecord(dto.getTeamId(), dto.getUserId());
        if (target == null) return Result.fail("该用户不是团队成员");
        if (target.getRoleType() == 1) return Result.fail("不能移除队长");
        TeamMember operator = getMemberRecord(dto.getTeamId(), userId);
        if (operator != null && operator.getRoleType() == 2 && target.getRoleType() == 2)
            return Result.fail("管理员不能移除其他管理员");
        LambdaUpdateWrapper<TeamMember> uw = new LambdaUpdateWrapper<>();
        uw.eq(TeamMember::getTeamId, dto.getTeamId()).eq(TeamMember::getUserId, dto.getUserId())
          .set(TeamMember::getIsQuit, 1).set(TeamMember::getQuitTime, LocalDateTime.now());
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
     * 修改成员角色
     * 仅队长可操作，只能将成员角色设置为管理员(2)或普通成员(3)，不可修改队长角色
     *
     * @param dto 成员操作数据传输对象（teamId、userId、roleType）
     * @return 修改结果
     */
    /**
     * updateMemberRole 的核心业务逻辑。
     * @param dto 请求数据对象
     * @return 方法执行结果
     */
    @Override
    @Transactional
    public Result updateMemberRole(TeamMemberOperationDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto.getTeamId() == null || dto.getUserId() == null || dto.getRoleType() == null) return Result.fail("参数不能为空");
        if (dto.getRoleType() != 2 && dto.getRoleType() != 3) return Result.fail("角色类型只能设置为2(管理员)或3(普通成员)");
        if (!isLeader(dto.getTeamId(), userId)) return Result.fail("仅队长可修改成员角色");
        TeamMember target = getMemberRecord(dto.getTeamId(), dto.getUserId());
        if (target == null) return Result.fail("该用户不是团队成员");
        if (target.getRoleType() == 1) return Result.fail("不能修改队长角色");
        LambdaUpdateWrapper<TeamMember> uw = new LambdaUpdateWrapper<>();
        uw.eq(TeamMember::getTeamId, dto.getTeamId()).eq(TeamMember::getUserId, dto.getUserId())
          .set(TeamMember::getRoleType, dto.getRoleType());
        teamMemberMapper.update(null, uw);
        return Result.ok("成员角色已更新");
    }

    /**
     * 转让队长权限
     * 仅队长可操作，转让后原队长降为管理员(2)，目标成员升为队长(1)
     * 同步发送通知给新队长
     *
     * @param dto 成员操作数据传输对象（teamId、userId）
     * @return 转让结果
     */
    /**
     * transferLeader 的核心业务逻辑。
     * @param dto 请求数据对象
     * @return 方法执行结果
     */
    @Override
    @Transactional
    public Result transferLeader(TeamMemberOperationDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        Long targetUserId = dto.getUserId() != null ? dto.getUserId() : dto.getNewCaptainId();
        if (dto.getTeamId() == null || targetUserId == null) return Result.fail("参数不能为空");
        if (userId.equals(targetUserId)) return Result.fail("不能转让给自己");
        if (!isLeader(dto.getTeamId(), userId)) return Result.fail("仅队长可转让权限");

        TeamMember target = getMemberRecord(dto.getTeamId(), targetUserId);
        if (target == null) return Result.fail("目标用户不是团队成员");

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
     * 退出团队
     * 队长不能直接退出，需先转让权限或解散团队
     * 退出后同步清理该成员的禁言 Redis 缓存
     *
     * @param teamId 团队ID
     * @return 退出结果
     */
    /**
     * quitTeam 的核心业务逻辑。
     * @param teamId 团队ID
     * @return 方法执行结果
     */
    @Override
    @Transactional
    public Result quitTeam(Long teamId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (teamId == null || teamId <= 0) return Result.fail("团队ID无效");
        TeamMember member = getMemberRecord(teamId, userId);
        if (member == null) return Result.fail("你不是该团队成员");
        if (member.getRoleType() == 1) return Result.fail("队长不能直接退出，请先转让队长权限或解散团队");
        LambdaUpdateWrapper<TeamMember> uw = new LambdaUpdateWrapper<>();
        uw.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId)
          .set(TeamMember::getIsQuit, 1).set(TeamMember::getQuitTime, LocalDateTime.now());
        teamMemberMapper.update(null, uw);
        stringRedisTemplate.delete(TEAM_MUTE_KEY + teamId + "_" + userId);
        return Result.ok("已退出团队");
    }

    // ==================== 禁言管理 ====================

    /**
     * 设置/解除全员禁言
     * 仅队长/管理员可操作，更新 t_team.team_all_mute 并同步写入 Redis 缓存
     *
     * @param dto 禁言数据传输对象（teamId、isMute）
     * @return 操作结果
     */
    /**
     * muteAll 的核心业务逻辑。
     * @param dto 请求数据对象
     * @return 方法执行结果
     */
    @Override
    @Transactional
    public Result muteAll(TeamMuteDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto.getTeamId() == null || dto.getIsMute() == null) return Result.fail("参数不能为空");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        if (!isAdmin(dto.getTeamId(), userId)) return Result.fail("仅队长/管理员可设置全员禁言");

        int muteVal = dto.getIsMute() ? 1 : 0;
        LambdaUpdateWrapper<Team> uw = new LambdaUpdateWrapper<>();
        uw.eq(Team::getId, dto.getTeamId()).set(Team::getTeamAllMute, muteVal);
        teamMapper.update(null, uw);
        stringRedisTemplate.opsForValue().set(
                TEAM_ALL_MUTE_KEY + dto.getTeamId(), String.valueOf(muteVal),
                TEAM_MUTE_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return Result.ok(dto.getIsMute() ? "已开启全员禁言" : "已解除全员禁言");
    }

    /**
     * 禁言指定成员
     * 仅队长/管理员可操作，不能禁言队长，管理员不能禁言其他管理员
     * 更新 t_team_member.team_mute_type 及解禁时间，并同步写入 Redis 缓存
     *
     * @param dto 禁言数据传输对象（teamId、userId、muteDuration 单位分钟）
     * @return 操作结果
     */
    /**
     * muteMember 的核心业务逻辑。
     * @param dto 请求数据对象
     * @return 方法执行结果
     */
    @Override
    @Transactional
    public Result muteMember(TeamMuteDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto.getTeamId() == null || dto.getUserId() == null || dto.getMuteDuration() == null)
            return Result.fail("参数不能为空");
        if (dto.getMuteDuration() <= 0) return Result.fail("禁言时长必须大于0");
        if (userId.equals(dto.getUserId())) return Result.fail("不能禁言自己");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        if (!isAdmin(dto.getTeamId(), userId)) return Result.fail("仅队长/管理员可禁言成员");
        TeamMember target = getMemberRecord(dto.getTeamId(), dto.getUserId());
        if (target == null) return Result.fail("该用户不是团队成员");
        if (target.getRoleType() == 1) return Result.fail("不能禁言队长");
        TeamMember operator = getMemberRecord(dto.getTeamId(), userId);
        if (operator != null && operator.getRoleType() == 2 && target.getRoleType() == 2)
            return Result.fail("管理员不能禁言其他管理员");

        LocalDateTime unpunishTime = LocalDateTime.now().plusMinutes(dto.getMuteDuration());
        LambdaUpdateWrapper<TeamMember> uw = new LambdaUpdateWrapper<>();
        uw.eq(TeamMember::getTeamId, dto.getTeamId()).eq(TeamMember::getUserId, dto.getUserId())
          .set(TeamMember::getTeamMuteType, 1)
          .set(TeamMember::getTeamMuteUnpunishTime, unpunishTime);
        teamMemberMapper.update(null, uw);
        // 更新 Redis 缓存
        stringRedisTemplate.opsForValue().set(
                TEAM_MUTE_KEY + dto.getTeamId() + "_" + dto.getUserId(), "1",
                TEAM_MUTE_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return Result.ok("已禁言该成员 " + dto.getMuteDuration() + " 分钟");
    }

    /**
     * 解除指定成员禁言
     * 仅队长/管理员可操作，清除 team_mute_type 及解禁时间，并删除 Redis 缓存
     *
     * @param dto 禁言数据传输对象（teamId、userId）
     * @return 操作结果
     */
    /**
     * unmuteMember 的核心业务逻辑。
     * @param dto 请求数据对象
     * @return 方法执行结果
     */
    @Override
    @Transactional
    public Result unmuteMember(TeamMuteDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto.getTeamId() == null || dto.getUserId() == null) return Result.fail("参数不能为空");
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        if (!isAdmin(dto.getTeamId(), userId)) return Result.fail("仅队长/管理员可解除禁言");
        // 检查目标用户是否仍在禁言中（team_mute_type=1 且未到期）
        TeamMember target = getMemberRecord(dto.getTeamId(), dto.getUserId());
        if (target == null) return Result.fail("该用户不是团队成员");
        if (target.getTeamMuteType() != 1) return Result.fail("该用户当前未被禁言");
        // 禁言已自然到期（定时任务尚未清理），视为已解除
        if (target.getTeamMuteUnpunishTime() != null
                && !target.getTeamMuteUnpunishTime().isAfter(LocalDateTime.now())) {
            return Result.fail("该用户禁言已到期，无需手动解除");
        }
        LambdaUpdateWrapper<TeamMember> uw = new LambdaUpdateWrapper<>();
        uw.eq(TeamMember::getTeamId, dto.getTeamId()).eq(TeamMember::getUserId, dto.getUserId())
          .set(TeamMember::getTeamMuteType, 0)
          .set(TeamMember::getTeamMuteUnpunishTime, null);
        teamMemberMapper.update(null, uw);
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
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId)
          .eq(TeamMember::getUserId, userId)
          .eq(TeamMember::getIsQuit, 0);
        return teamMemberMapper.selectOne(qw);
    }

    /**
     * 获取团队当前在线成员数（is_quit=0）
     *
     * @param teamId 团队 ID
     * @return 当前未退出的成员总数
     */
    private int getMemberCount(Long teamId) {
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
        if (teamIds == null || teamIds.isEmpty()) return Collections.emptyMap();
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.in(TeamMember::getTeamId, teamIds).eq(TeamMember::getIsQuit, 0);
        List<TeamMember> members = teamMemberMapper.selectList(qw);
        Map<Long, Integer> countMap = new HashMap<>();
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
        if (teamIds == null || teamIds.isEmpty()) return Collections.emptySet();
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getUserId, userId)
          .in(TeamMember::getTeamId, teamIds)
          .eq(TeamMember::getIsQuit, 0);
        return teamMemberMapper.selectList(qw).stream()
                .map(TeamMember::getTeamId)
                .collect(Collectors.toSet());
    }

    /**
     * 添加成员到团队（直接插入，不校验满员/权限，由调用方保证）
     *
     * @param teamId       团队 ID
     * @param userId       新成员用户 ID
     * @param roleType     角色类型（1-队长，2-管理员，3-普通成员）
     * @param joinSource   加入来源（1-直接加入，2-邀请加入，3-申请审批）
     * @param inviteUserId 邀请人ID（邀请加入时有值）
     */
    private String roleNameOf(Integer roleType) {
        if (roleType == null) return "未知";
        if (roleType == 1) return "队长";
        if (roleType == 2) return "管理员";
        if (roleType == 3) return "普通成员";
        return "未知";
    }

    /**
     * addMember 的核心业务逻辑。
     * @param teamId 团队ID
     * @param userId 用户ID
     * @param roleType 角色类型
     * @param joinSource 参数
     * @param inviteUserId 参数
     */
    private void addMember(Long teamId, Long userId, int roleType, int joinSource, Long inviteUserId) {
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
        SystemNotice notice = new SystemNotice();
        notice.setUserId(userId);
        notice.setNoticeType(noticeType);
        notice.setNoticeContent(content);
        notice.setRelatedId(relatedId);
        notice.setIsRead(0);
        notice.setIsDelete(0);
        sendRealtimeSystemNotice(notice);
    }

    /**
     * 落库并实时推送系统通知
     * 先写入 t_system_notice，再通过 WebSocket 推送；用户离线时推送静默跳过，通知不丢失
     *
     * @param notice 已填充好字段的系统通知实体
     */
    private void sendRealtimeSystemNotice(SystemNotice notice) {
        systemNoticeMapper.insert(notice);

        Map<String, Object> push = new HashMap<>();
        push.put("type", "system_notice");
        Map<String, Object> data = new HashMap<>();
        data.put("noticeId", notice.getId());
        data.put("noticeType", notice.getNoticeType());
        data.put("noticeContent", notice.getNoticeContent());
        data.put("relatedId", notice.getRelatedId());
        push.put("data", data);
        chatWebSocketHandler.sendToUser(notice.getUserId(), JSON.toJSONString(push));
    }

    /**
     * 发送通知给团队所有管理员（队长+管理员），排除指定用户
     *
     * @param teamId        团队 ID
     * @param content       通知内容文本
     * @param excludeUserId 不发送通知的用户 ID（通常为操作发起人）
     */
    private void sendNoticeToAdmins(Long teamId, String content, Long excludeUserId) {
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId)
          .in(TeamMember::getRoleType, 1, 2)
          .eq(TeamMember::getIsQuit, 0);
        List<TeamMember> admins = teamMemberMapper.selectList(qw);
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
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getIsQuit, 0);
        List<TeamMember> members = teamMemberMapper.selectList(qw);
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
        map.put("memberCount", memberCount);
        map.put("isMember", isMember);
        map.put("createTime", team.getCreateTime());
        return map;
    }
}

