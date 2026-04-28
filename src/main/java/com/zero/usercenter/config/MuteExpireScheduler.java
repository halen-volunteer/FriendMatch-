package com.zero.usercenter.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zero.usercenter.Mapper.TeamMemberMapper;
import com.zero.usercenter.Mapper.UserMapper;
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

import static com.zero.usercenter.utils.Number.TEAM_MUTE_KEY;
import static com.zero.usercenter.utils.Number.USER_PUNISH_KEY;
import static com.zero.usercenter.utils.Number.USER_ONLINE_KEY;
import static com.zero.usercenter.utils.Number.USER_ONLINE_TTL_KEY;

/**
 * 禁言自动解除定时任务
 * 每分钟扫描一次，解除已过期的全局禁言和团队禁言
 */
@Slf4j
@Component
public class MuteExpireScheduler {

    @Resource
    private UserMapper userMapper;
    @Resource
    private TeamMemberMapper teamMemberMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 每分钟执行一次：解除已到期的全局禁言
     * 条件：global_punish_type=1 且 global_unpunish_time <= now()
     */
    @Scheduled(fixedDelay = 60000)
    public void releaseExpiredGlobalMute() {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getGlobalPunishType, 1)
          .le(User::getGlobalUnpunishTime, LocalDateTime.now())
          .eq(User::getIsDelete, 0);
        List<User> expiredUsers = userMapper.selectList(qw);
        if (expiredUsers.isEmpty()) return;

        for (User user : expiredUsers) {
            // 更新数据库
            LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<>();
            uw.eq(User::getId, user.getId())
              .set(User::getGlobalPunishType, 0)
              .set(User::getGlobalUnpunishTime, null);
            userMapper.update(null, uw);
            // 删除 Redis 缓存
            stringRedisTemplate.delete(USER_PUNISH_KEY + user.getId());
        }
        log.info("[定时任务] 解除全局禁言 {} 人", expiredUsers.size());
    }

    /**
     * 每分钟执行一次：解除已到期的团队内禁言
     * 条件：team_mute_type=1 且 team_mute_unpunish_time <= now()
     */
    @Scheduled(fixedDelay = 60000)
    public void releaseExpiredTeamMute() {
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamMuteType, 1)
          .le(TeamMember::getTeamMuteUnpunishTime, LocalDateTime.now())
          .eq(TeamMember::getIsQuit, 0);
        List<TeamMember> expiredMembers = teamMemberMapper.selectList(qw);
        if (expiredMembers.isEmpty()) return;

        for (TeamMember member : expiredMembers) {
            // 更新数据库
            LambdaUpdateWrapper<TeamMember> uw = new LambdaUpdateWrapper<>();
            uw.eq(TeamMember::getId, member.getId())
              .set(TeamMember::getTeamMuteType, 0)
              .set(TeamMember::getTeamMuteUnpunishTime, null);
            teamMemberMapper.update(null, uw);
            // 删除 Redis 缓存
            stringRedisTemplate.delete(TEAM_MUTE_KEY + member.getTeamId() + "_" + member.getUserId());
        }
        log.info("[定时任务] 解除团队禁言 {} 条", expiredMembers.size());
    }

    /**
     * 每分钟执行一次：清理超时的用户在线状态
     * 从 user_online_ttl ZSet 中找出 score(过期时间戳) <= now 的用户，
     * 从 user_online Hash 中删除对应 field
     */
    @Scheduled(fixedDelay = 60000)
    public void cleanExpiredOnlineStatus() {
        long now = System.currentTimeMillis();
        // 取出所有已过期的 userId（score <= now）
        Set<String> expiredUserIds =
                stringRedisTemplate.opsForZSet().rangeByScore(USER_ONLINE_TTL_KEY, 0, now);
        if (expiredUserIds == null || expiredUserIds.isEmpty()) return;

        for (String userId : expiredUserIds) {
            stringRedisTemplate.opsForHash().delete(USER_ONLINE_KEY, userId);
            stringRedisTemplate.opsForZSet().remove(USER_ONLINE_TTL_KEY, userId);
        }
        log.info("[定时任务] 清理过期在线状态 {} 个", expiredUserIds.size());
    }
}
