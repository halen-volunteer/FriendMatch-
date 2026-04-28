package com.zero.usercenter.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiniu.common.QiniuException;
import com.qiniu.storage.BucketManager;
import com.zero.usercenter.Mapper.ChatMessageMapper;
import com.zero.usercenter.Model.ChatMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class OssExpireScheduler {

    private static final int EXPIRE_DAYS = 14;

    @Resource
    private ChatMessageMapper chatMessageMapper;
    @Resource
    private BucketManager bucketManager;
    @Resource
    private OssConfig ossConfig;

    @Scheduled(fixedDelay = 3600000)
    public void clearExpiredChatFiles() {
        LocalDateTime expireBefore = LocalDateTime.now().minusDays(EXPIRE_DAYS);
        LambdaQueryWrapper<ChatMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(ChatMessage::getIsDelete, 0)
          .in(ChatMessage::getMsgType, 2, 3)
          .le(ChatMessage::getCreateTime, expireBefore);
        List<ChatMessage> expiredMessages = chatMessageMapper.selectList(qw);
        if (expiredMessages.isEmpty()) return;

        int success = 0;
        for (ChatMessage message : expiredMessages) {
            if (expireMessageFile(message)) success++;
        }
        if (success > 0) {
            log.info("[定时任务] 清理过期聊天文件 {} 条", success);
        }
    }

    private boolean expireMessageFile(ChatMessage message) {
        try {
            JSONObject content = JSON.parseObject(message.getMsgContent());
            if (content == null || Boolean.TRUE.equals(content.getBoolean("expired"))) return false;

            String url = content.getString("url");
            if (url != null && !url.isBlank()) {
                String key = resolveKey(url);
                if (key != null && !key.isBlank()) {
                    try {
                        bucketManager.delete(ossConfig.getBucketName(), key);
                    } catch (QiniuException e) {
                        int code = e.code();
                        if (code != 612) throw e;
                    }
                }
            }

            content.put("url", "");
            content.put("expired", true);
            LambdaUpdateWrapper<ChatMessage> uw = new LambdaUpdateWrapper<>();
            uw.eq(ChatMessage::getId, message.getId())
              .set(ChatMessage::getMsgContent, JSON.toJSONString(content));
            chatMessageMapper.update(null, uw);
            return true;
        } catch (Exception e) {
            log.warn("[定时任务] 处理过期聊天文件失败 msgId={}", message.getId(), e);
            return false;
        }
    }

    private String resolveKey(String url) {
        String prefix = ossConfig.getUrlPrefix();
        if (prefix == null || prefix.isBlank() || url == null || url.isBlank()) return null;
        if (!url.startsWith(prefix + "/")) return null;
        return url.substring(prefix.length() + 1);
    }
}
