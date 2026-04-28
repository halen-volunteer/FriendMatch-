package com.zero.usercenter.Service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Mapper.*;
import com.zero.usercenter.Model.*;
import com.zero.usercenter.websocket.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.zero.usercenter.utils.Number.*;

@Service
public class ChatSendService {
    @Resource private SensitiveWordBs sensitiveWordBs;
    @Resource private ChatMessageMapper chatMessageMapper;
    @Resource private UserMapper userMapper;
    @Resource private UserBlacklistMapper userBlacklistMapper;
    @Resource private UserFriendMapper userFriendMapper;
    @Resource private TeamMemberMapper teamMemberMapper;
    @Resource private TeamMapper teamMapper;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private ChatWebSocketHandler chatWebSocketHandler;
    @Resource private ChatSupportService chatSupportService;

    @Transactional
    public Result sendPrivateMsg(PrivateMsgDTO dto) {
        Long userId = chatSupportService.requireLogin();
        Long recipientId = dto.getRecipientId();
        if (recipientId == null || recipientId <= 0) return Result.fail("接收方ID无效");
        if (userId.equals(recipientId)) return Result.fail("不能给自己发消息");
        if (dto.getMsgType() == null || dto.getMsgType() < 1 || dto.getMsgType() > 5) return Result.fail("消息类型无效");
        switch (dto.getMsgType()) {
            case 1, 5 -> {
                if (dto.getMsgContent() == null || dto.getMsgContent().isBlank()) return Result.fail("消息内容不能为空");
                if (dto.getMsgContent().length() > 2000) return Result.fail("消息内容不能超过2000字符");
            }
            case 2 -> { if (dto.getFileUrl() == null || dto.getFileUrl().isBlank()) return Result.fail("图片URL不能为空"); }
            case 3 -> {
                if (dto.getFileUrl() == null || dto.getFileUrl().isBlank()) return Result.fail("文件URL不能为空");
                if (dto.getFileName() == null || dto.getFileName().isBlank()) return Result.fail("文件名不能为空");
            }
            case 4 -> { if (dto.getEmojiId() == null || dto.getEmojiId().isBlank()) return Result.fail("表情包标识不能为空"); }
        }
        User recipient = userMapper.selectById(recipientId);
        if (recipient == null || recipient.getIsDelete() == 1) return Result.fail("用户不存在");
        LambdaQueryWrapper<UserBlacklist> bq = new LambdaQueryWrapper<>();
        bq.eq(UserBlacklist::getUserId, recipientId).eq(UserBlacklist::getBlackUserId, userId).eq(UserBlacklist::getIsDelete, 0);
        if (userBlacklistMapper.selectOne(bq) != null) return Result.fail("无法发送消息");
        LambdaQueryWrapper<UserBlacklist> bq2 = new LambdaQueryWrapper<>();
        bq2.eq(UserBlacklist::getUserId, userId).eq(UserBlacklist::getBlackUserId, recipientId).eq(UserBlacklist::getIsDelete, 0);
        if (userBlacklistMapper.selectOne(bq2) != null) return Result.fail("您已将对方加入黑名单，无法发送消息");
        String privacyStr = recipient.getPrivacySetting();
        if (privacyStr != null && !privacyStr.isBlank()) {
            PrivacySettingDTO privacy = JSON.parseObject(privacyStr, PrivacySettingDTO.class);
            if (privacy.getSendMsg() != null) {
                if (privacy.getSendMsg() == 2) {
                    LambdaQueryWrapper<TeamMember> tq1 = new LambdaQueryWrapper<>();
                    tq1.eq(TeamMember::getUserId, userId).eq(TeamMember::getIsQuit, 0);
                    List<Long> myTeamIds = teamMemberMapper.selectList(tq1).stream().map(TeamMember::getTeamId).collect(Collectors.toList());
                    boolean inSameTeam = false;
                    if (!myTeamIds.isEmpty()) {
                        LambdaQueryWrapper<TeamMember> tq2 = new LambdaQueryWrapper<>();
                        tq2.eq(TeamMember::getUserId, recipientId).eq(TeamMember::getIsQuit, 0).in(TeamMember::getTeamId, myTeamIds);
                        inSameTeam = teamMemberMapper.selectCount(tq2) > 0;
                    }
                    if (!inSameTeam) return Result.fail("对方仅接收团队成员消息");
                } else if (privacy.getSendMsg() == 3) {
                    LambdaQueryWrapper<UserFriend> fq = new LambdaQueryWrapper<>();
                    fq.eq(UserFriend::getUserId, userId).eq(UserFriend::getFriendId, recipientId).eq(UserFriend::getFriendStatus, 1);
                    if (userFriendMapper.selectOne(fq) == null) return Result.fail("需先成为好友才能发消息");
                }
            }
        }
        Result muteCheck = checkGlobalMute(userId);
        if (muteCheck != null) return muteCheck;
        if (dto.getMsgType() == 1 && sensitiveWordBs.contains(dto.getMsgContent())) return Result.fail("消息包含违禁内容");
        String conversationId = chatSupportService.buildConvId(userId, recipientId);
        String storedContent = chatSupportService.buildStoredContent(dto.getMsgType(), dto.getMsgContent(), dto.getFileUrl(), dto.getFileName(), dto.getFileSize(), dto.getEmojiId(), null);
        ChatMessage msg = new ChatMessage();
        msg.setSenderId(userId); msg.setRecvType(1); msg.setRecvId(recipientId); msg.setConversationId(conversationId); msg.setMsgType(dto.getMsgType()); msg.setMsgContent(storedContent); msg.setIsDelete(0);
        chatMessageMapper.insert(msg);
        chatSupportService.writeReceipt(msg.getId(), recipientId, 1);
        stringRedisTemplate.opsForValue().increment(UNREAD_COUNT_KEY + recipientId + ":" + conversationId);
        chatSupportService.cacheLastMsg(msg);
        chatWebSocketHandler.sendToUser(recipientId, chatSupportService.buildPrivatePush(msg));
        return Result.ok(msg.getId());
    }

    public Result getPrivateHistory(Long friendId, int page, int pageSize) {
        Long userId = chatSupportService.requireLogin();
        if (friendId == null || friendId <= 0) return Result.fail("好友ID无效");
        String convId = chatSupportService.buildConvId(userId, friendId);
        Page<ChatMessage> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<ChatMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(ChatMessage::getConversationId, convId).eq(ChatMessage::getIsDelete, 0).orderByDesc(ChatMessage::getCreateTime);
        Page<ChatMessage> result = chatMessageMapper.selectPage(pageObj, qw);
        List<Long> ids = result.getRecords().stream().map(ChatMessage::getId).collect(Collectors.toList());
        if (!ids.isEmpty()) Thread.ofVirtual().start(() -> chatSupportService.doMarkRead(convId, ids, userId));
        return Result.ok(chatSupportService.buildVoList(result.getRecords(), userId), result.getTotal());
    }

    @Transactional
    public Result sendTeamMsg(TeamMsgDTO dto) {
        Long userId = chatSupportService.requireLogin();
        Long teamId = dto.getTeamId();
        if (teamId == null || teamId <= 0) return Result.fail("团队ID无效");
        if (dto.getMsgType() == null || dto.getMsgType() < 1 || dto.getMsgType() > 5) return Result.fail("消息类型无效");
        switch (dto.getMsgType()) {
            case 1, 5 -> {
                if (dto.getMsgContent() == null || dto.getMsgContent().isBlank()) return Result.fail("消息内容不能为空");
                if (dto.getMsgContent().length() > 2000) return Result.fail("消息内容不能超过2000字符");
            }
            case 2 -> { if (dto.getFileUrl() == null || dto.getFileUrl().isBlank()) return Result.fail("图片URL不能为空"); }
            case 3 -> {
                if (dto.getFileUrl() == null || dto.getFileUrl().isBlank()) return Result.fail("文件URL不能为空");
                if (dto.getFileName() == null || dto.getFileName().isBlank()) return Result.fail("文件名不能为空");
            }
            case 4 -> { if (dto.getEmojiId() == null || dto.getEmojiId().isBlank()) return Result.fail("表情包标识不能为空"); }
        }
        Team team = teamMapper.selectById(teamId);
        if (team == null || team.getIsDelete() == 1) return Result.fail("团队不存在");
        TeamMember member = chatSupportService.getMember(teamId, userId);
        if (member == null) return Result.fail("您不是该团队成员");
        Result globalMute = checkGlobalMute(userId);
        if (globalMute != null) return globalMute;
        if (member.getRoleType() >= 3) {
            Result allMute = checkTeamAllMute(team);
            if (allMute != null) return allMute;
            Result memberMute = checkMemberMute(member);
            if (memberMute != null) return memberMute;
        }
        List<Long> atUserIds = Collections.emptyList();
        if (dto.getMsgType() == 5 && dto.getAtUsers() != null && !dto.getAtUsers().isEmpty()) {
            atUserIds = dto.getAtUsers().stream().distinct().collect(Collectors.toList());
            LambdaQueryWrapper<TeamMember> atQw = new LambdaQueryWrapper<>();
            atQw.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getIsQuit, 0).in(TeamMember::getUserId, atUserIds);
            long validCount = teamMemberMapper.selectCount(atQw);
            if (validCount != atUserIds.size()) return Result.fail("存在非团队成员，无法@消息");
        }
        if (dto.getMsgType() == 1 && sensitiveWordBs.contains(dto.getMsgContent())) return Result.fail("消息包含违禁内容");
        String convId = "team_" + teamId;
        String storedContent = chatSupportService.buildStoredContent(dto.getMsgType(), dto.getMsgContent(), dto.getFileUrl(), dto.getFileName(), dto.getFileSize(), dto.getEmojiId(), dto.getAtUsers());
        ChatMessage msg = new ChatMessage();
        msg.setSenderId(userId); msg.setRecvType(2); msg.setRecvId(teamId); msg.setConversationId(convId); msg.setMsgType(dto.getMsgType()); msg.setMsgContent(storedContent); msg.setIsDelete(0);
        chatMessageMapper.insert(msg);
        Long msgId = msg.getId();
        Thread.ofVirtual().start(() -> chatSupportService.writeTeamReceipts(teamId, msgId, userId));
        Thread.ofVirtual().start(() -> {
            LambdaQueryWrapper<TeamMember> unreadQw = new LambdaQueryWrapper<>();
            unreadQw.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getIsQuit, 0).ne(TeamMember::getUserId, userId);
            teamMemberMapper.selectList(unreadQw).forEach(m -> stringRedisTemplate.opsForValue().increment(UNREAD_COUNT_KEY + m.getUserId() + ":" + convId));
        });
        chatSupportService.cacheLastMsg(msg);
        chatWebSocketHandler.sendToTeam(teamId, chatSupportService.buildTeamPush(msg), userId);
        if (!atUserIds.isEmpty()) {
            List<Long> finalAtUserIds = atUserIds;
            Long finalTeamId = teamId;
            Long finalSenderId = userId;
            Long finalMsgId = msgId;
            Thread.ofVirtual().start(() -> chatSupportService.sendAtNotices(finalAtUserIds, finalTeamId, finalSenderId, finalMsgId));
        }
        return Result.ok(msgId);
    }

    public Result getTeamHistory(Long teamId, int page, int pageSize) {
        Long userId = chatSupportService.requireLogin();
        if (teamId == null || teamId <= 0) return Result.fail("团队ID无效");
        if (chatSupportService.getMember(teamId, userId) == null) return Result.fail("您不是该团队成员");
        String convId = "team_" + teamId;
        Page<ChatMessage> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<ChatMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(ChatMessage::getConversationId, convId).eq(ChatMessage::getIsDelete, 0).orderByDesc(ChatMessage::getCreateTime);
        Page<ChatMessage> result = chatMessageMapper.selectPage(pageObj, qw);
        List<Long> ids = result.getRecords().stream().map(ChatMessage::getId).collect(Collectors.toList());
        if (!ids.isEmpty()) Thread.ofVirtual().start(() -> chatSupportService.doMarkRead(convId, ids, userId));
        return Result.ok(chatSupportService.buildVoList(result.getRecords(), userId), result.getTotal());
    }

    private Result checkGlobalMute(Long userId) {
        String cacheKey = USER_PUNISH_KEY + userId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("0".equals(cached)) return null;
            String[] parts = cached.split("\\|");
            int punishType = Integer.parseInt(parts[0]);
            if (punishType == 1) {
                if (parts.length < 2) return Result.fail("您已被全局禁言");
                LocalDateTime unpunishTime = LocalDateTime.parse(parts[1]);
                if (unpunishTime.isAfter(LocalDateTime.now())) return Result.fail("您已被全局禁言");
                stringRedisTemplate.delete(cacheKey);
            } else if (punishType == 2) {
                return Result.fail("账号已被封禁");
            }
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null) return Result.fail("用户不存在");
        long ttl = USER_PUNISH_CACHE_TTL_MINUTES + ThreadLocalRandom.current().nextLong(-1, 2);
        if (user.getGlobalPunishType() == null || user.getGlobalPunishType() == 0) {
            stringRedisTemplate.opsForValue().set(cacheKey, "0", ttl, TimeUnit.MINUTES);
            return null;
        } else if (user.getGlobalPunishType() == 1) {
            String val = "1|" + (user.getGlobalUnpunishTime() != null ? user.getGlobalUnpunishTime().toString() : "");
            stringRedisTemplate.opsForValue().set(cacheKey, val, ttl, TimeUnit.MINUTES);
            if (user.getGlobalUnpunishTime() == null || user.getGlobalUnpunishTime().isAfter(LocalDateTime.now())) return Result.fail("您已被全局禁言");
        } else if (user.getGlobalPunishType() == 2) {
            stringRedisTemplate.opsForValue().set(cacheKey, "2", ttl, TimeUnit.MINUTES);
            return Result.fail("账号已被封禁");
        }
        return null;
    }

    private Result checkTeamAllMute(Team team) {
        String cached = stringRedisTemplate.opsForValue().get(TEAM_ALL_MUTE_KEY + team.getId());
        int muteVal = cached != null ? Integer.parseInt(cached) : team.getTeamAllMute();
        if (muteVal == 1) return Result.fail("团队当前已开启全员禁言");
        return null;
    }

    private Result checkMemberMute(TeamMember member) {
        if (member.getTeamMuteType() == 1) {
            if (member.getTeamMuteUnpunishTime() == null || member.getTeamMuteUnpunishTime().isAfter(LocalDateTime.now())) return Result.fail("您已被禁言");
        }
        return null;
    }
}
