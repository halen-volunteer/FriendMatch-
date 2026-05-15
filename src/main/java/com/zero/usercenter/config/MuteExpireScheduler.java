package com.zero.usercenter.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zero.usercenter.Mapper.TeamMapper;
import com.zero.usercenter.Mapper.TeamMemberMapper;
import com.zero.usercenter.Mapper.UserMapper;
import com.zero.usercenter.Model.Team;
import com.zero.usercenter.Model.TeamMember;
import com.zero.usercenter.Model.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.zero.usercenter.utils.Number.TEAM_ALL_MUTE_KEY;
import static com.zero.usercenter.utils.Number.TEAM_MUTE_KEY;
import static com.zero.usercenter.utils.Number.USER_ONLINE_KEY;
import static com.zero.usercenter.utils.Number.USER_ONLINE_TTL_KEY;
import static com.zero.usercenter.utils.Number.USER_PUNISH_KEY;

@Slf4j
@Component
/**
 * 禁言与在线状态过期清理任务。
 * 定时释放到期处罚，并把长时间无心跳的用户从在线状态缓存中移除。
 */
public class MuteExpireScheduler {

    @Resource
    private UserMapper userMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private TeamMemberMapper teamMemberMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 释放已到期的全局禁言，并同步删除 Redis 中的生效处罚缓存。
     */
    @Scheduled(fixedDelay = 60000)
    public void releaseExpiredGlobalMute() {
        // 1. 查询已经到达解禁时间、且当前仍处于全局禁言状态的用户。
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getGlobalPunishType, 1)
          .le(User::getGlobalUnpunishTime, LocalDateTime.now())
          .eq(User::getIsDelete, 0);
        List<User> expiredUsers = userMapper.selectList(qw);
        if (expiredUsers.isEmpty()) return;

        for (User user : expiredUsers) {
            // 2. 先把数据库中的处罚状态恢复，确保后续查库逻辑立即生效。
            LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<>();
            uw.eq(User::getId, user.getId())
              .set(User::getGlobalPunishType, 0)
              .set(User::getGlobalUnpunishTime, null);
            userMapper.update(null, uw);
            // 3. 再删除 Redis 中的处罚缓存，避免缓存继续命中过期禁言状态。
            stringRedisTemplate.delete(USER_PUNISH_KEY + user.getId());
        }
        log.info("[定时任务] 解除全局禁言 {} 人", expiredUsers.size());
    }

    /**
     * 释放已到期的团队全员禁言。
     */
    @Scheduled(fixedDelay = 60000)
    public void releaseExpiredTeamAllMute() {
        // 1. 查询已经到期但数据库中仍显示“全员禁言”的团队。
        LambdaQueryWrapper<Team> qw = new LambdaQueryWrapper<>();
        qw.eq(Team::getTeamAllMute, 1)
          .le(Team::getTeamAllMuteUnpunishTime, LocalDateTime.now())
          .eq(Team::getIsDelete, 0);
        List<Team> expiredTeams = teamMapper.selectList(qw);
        if (expiredTeams.isEmpty()) return;

        for (Team team : expiredTeams) {
            // 2. 先恢复团队表中的全员禁言字段。
            LambdaUpdateWrapper<Team> uw = new LambdaUpdateWrapper<>();
            uw.eq(Team::getId, team.getId())
              .set(Team::getTeamAllMute, 0)
              .set(Team::getTeamAllMuteUnpunishTime, null);
            teamMapper.update(null, uw);
            // 3. 再清理团队级禁言缓存，让发消息校验尽快感知解禁结果。
            stringRedisTemplate.delete(TEAM_ALL_MUTE_KEY + team.getId());
        }
        log.info("[定时任务] 解除团队全员禁言 {} 个", expiredTeams.size());
    }

    /**
     * 释放已到期的成员级团队禁言。
     */
    @Scheduled(fixedDelay = 60000)
    public void releaseExpiredTeamMute() {
        // 1. 查询已经到期但仍保留成员禁言标记的团队成员关系。
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamMuteType, 1)
          .le(TeamMember::getTeamMuteUnpunishTime, LocalDateTime.now())
          .eq(TeamMember::getIsQuit, 0);
        List<TeamMember> expiredMembers = teamMemberMapper.selectList(qw);
        if (expiredMembers.isEmpty()) return;

        for (TeamMember member : expiredMembers) {
            // 2. 先更新数据库中的成员禁言状态。
            LambdaUpdateWrapper<TeamMember> uw = new LambdaUpdateWrapper<>();
            uw.eq(TeamMember::getId, member.getId())
              .set(TeamMember::getTeamMuteType, 0)
              .set(TeamMember::getTeamMuteUnpunishTime, null);
            teamMemberMapper.update(null, uw);
            // 3. 删除 teamId + userId 维度的禁言缓存，避免缓存和数据库不一致。
            stringRedisTemplate.delete(TEAM_MUTE_KEY + member.getTeamId() + "_" + member.getUserId());
        }
        log.info("[定时任务] 解除团队成员禁言 {} 条", expiredMembers.size());
    }

    /**
     * 清理已经超时的在线状态。
     * Redis 里同时维护 Hash 和 ZSet，这里需要两边一起删除。
     */
    @Scheduled(fixedDelay = 60000)
    public void cleanExpiredOnlineStatus() {
        // 1. 从在线状态过期 ZSet 中查出已经超过心跳截止时间的用户。
        long now = System.currentTimeMillis();
        Set<String> expiredUserIds = stringRedisTemplate.opsForZSet().rangeByScore(USER_ONLINE_TTL_KEY, 0, now);
        if (expiredUserIds == null || expiredUserIds.isEmpty()) return;

        for (String userId : expiredUserIds) {
            // 2. 同时删除在线状态 Hash 和过期时间 ZSet，避免单边残留脏数据。
            stringRedisTemplate.opsForHash().delete(USER_ONLINE_KEY, userId);
            stringRedisTemplate.opsForZSet().remove(USER_ONLINE_TTL_KEY, userId);
        }
        log.info("[定时任务] 清理过期在线状态 {} 个", expiredUserIds.size());
    }
}
