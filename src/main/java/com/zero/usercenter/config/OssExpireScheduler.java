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
/**
 * OSS 过期资源清理任务。
 * 定时删除聊天中已经超过保留期的文件资源，并把消息改写为过期状态。
 */
public class OssExpireScheduler {

    /** 聊天资源保留时长，超过后会被标记过期并尝试删除远端文件。 */
    private static final int EXPIRE_DAYS = 14;

    @Resource
    private ChatMessageMapper chatMessageMapper;
    @Resource
    private BucketManager bucketManager;
    @Resource
    private OssConfig ossConfig;

    /**
     * 每小时扫描一次过期聊天资源。
     * 当前只处理图片和文件消息，表情包消息不在这里清理。
     */
    @Scheduled(fixedDelay = 3600000)
    public void clearExpiredChatFiles() {
        // 1. 先计算“超过多少天算过期”的时间边界。
        LocalDateTime expireBefore = LocalDateTime.now().minusDays(EXPIRE_DAYS);
        // 2. 查询仍未删除、且消息类型为图片/文件的历史消息。
        LambdaQueryWrapper<ChatMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(ChatMessage::getIsDelete, 0)
          .in(ChatMessage::getMsgType, 2, 3)
          .le(ChatMessage::getCreateTime, expireBefore);
        List<ChatMessage> expiredMessages = chatMessageMapper.selectList(qw);
        if (expiredMessages.isEmpty()) {
            return;
        }

        int success = 0;
        for (ChatMessage message : expiredMessages) {
            // 3. 逐条处理资源过期，单条失败不影响整个批次继续清理。
            if (expireMessageFile(message)) {
                success++;
            }
        }
        if (success > 0) {
            log.info("[定时任务] 清理过期聊天文件 {} 条", success);
        }
    }

    /**
     * 删除七牛资源并把消息内容改写为“已过期”状态。
     * 这里不删除聊天消息主记录，只移除 url，避免历史消息结构断裂。
     *
     * @param message 待清理的聊天消息
     * @return true-处理成功，false-无需处理或处理失败
     */
    private boolean expireMessageFile(ChatMessage message) {
        try {
            // 1. 先解析消息 JSON 内容，判断是否已经标记为过期。
            JSONObject content = JSON.parseObject(message.getMsgContent());
            if (content == null || Boolean.TRUE.equals(content.getBoolean("expired"))) {
                return false;
            }

            // 2. 如果存在远端文件 URL，则尝试反推出对象 key 并删除七牛资源。
            String url = content.getString("url");
            if (url != null && !url.isBlank()) {
                String key = resolveKey(url);
                if (key != null && !key.isBlank()) {
                    try {
                        bucketManager.delete(ossConfig.getBucketName(), key);
                    } catch (QiniuException e) {
                        int code = e.code();
                        // 612 表示资源不存在，说明远端已经删掉了，此时直接按成功处理即可。
                        if (code != 612) {
                            throw e;
                        }
                    }
                }
            }

            // 3. 不删除消息主记录，只把 url 清空并标记 expired，保证历史消息还能展示“文件已过期”。
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

    /**
     * 根据公开访问 URL 反推出七牛对象 key。
     *
     * @param url 文件公开访问地址
     * @return 七牛对象 key；如果 URL 不属于当前桶前缀则返回 null
     */
    private String resolveKey(String url) {
        // 只有当前项目配置的公开访问前缀，才能安全地反推出对象 key。
        String prefix = ossConfig.getUrlPrefix();
        if (prefix == null || prefix.isBlank() || url == null || url.isBlank()) {
            return null;
        }
        if (!url.startsWith(prefix + "/")) {
            return null;
        }
        return url.substring(prefix.length() + 1);
    }
}
