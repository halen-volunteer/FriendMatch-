package com.zero.usercenter.Service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zero.usercenter.Service.AiCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 文本审核服务实现。
 * 返回值约定：
 * 0-正常，1-违规，-1-调用失败或结果无法可靠解析。
 */
@Service
public class AiCheckServiceImpl implements AiCheckService {

    private static final Logger log = LoggerFactory.getLogger(AiCheckServiceImpl.class);
    private static final Pattern RESULT_PATTERN = Pattern.compile("\"result\"\\s*:\\s*(-?\\d+)");
    private static final String SYSTEM_PROMPT =
            "你是一个内容安全审核员。请判断用户发送的内容是否属于违规内容。"
                    + "违规类型包括：色情低俗、暴力血腥、骚扰辱骂、诈骗广告、散布谣言等。"
                    + "请只返回 JSON，不要返回任何解释文字。"
                    + "合法内容返回 {\"result\":0}，违规内容返回 {\"result\":1}。";

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.base-url:}")
    private String baseUrl;

    @Value("${ai.model:}")
    private String model;

    @Value("${ai.timeout-seconds:15}")
    private int timeoutSeconds;

    @Override
    public int checkContent(String content) {
        // 1. 空内容直接视为正常，避免无意义请求打到外部模型。
        if (content == null || content.isBlank()) {
            return 0;
        }
        // 2. 配置不完整时直接降级，避免因为外部审核不可用而阻塞主流程。
        if (isBlank(apiKey) || isBlank(baseUrl) || isBlank(model)) {
            log.warn("AI audit skipped because config is incomplete. apiKeyConfigured={}, baseUrl='{}', model='{}'",
                    !isBlank(apiKey), safeValue(baseUrl), safeValue(model));
            return -1;
        }

        try {
            // 3. 构造模型请求体，采用 system + user 的标准对话格式控制输出。
            RestClient client = buildRestClient();
            Map<String, Object> requestBody = Map.of(
                    "model", model.trim(),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", content)
                    ),
                    "max_tokens", 20,
                    "temperature", 0
            );

            // 4. 调用外部审核接口，拿到原始响应后再做二次解析。
            String response = client.post()
                    .uri(URI.create(resolveRequestUrl()))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            // 5. 统一解析结果，失败或无法确认时返回 -1 交给上层兜底处理。
            int parsed = parseAiResponse(response);
            if (parsed == -1) {
                log.warn("AI audit response parsed as uncertain. model='{}', baseUrl='{}', response={}",
                        safeValue(model), safeValue(baseUrl), limitText(response, 1200));
            } else {
                log.info("AI audit succeeded. model='{}', result={}", safeValue(model), parsed);
            }
            return parsed;
        } catch (Exception e) {
            log.error("AI audit request failed. model='{}', baseUrl='{}', timeoutSeconds={}, message={}",
                    safeValue(model), safeValue(baseUrl), timeoutSeconds, e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 构建审核请求客户端。
     *
     * @return 预置超时和 JSON 头的 RestClient
     */
    private RestClient buildRestClient() {
        // 1. 构造带超时控制的 HTTP 客户端，避免外部接口卡住主线程太久。
        int safeTimeoutSeconds = Math.max(timeoutSeconds, 1);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(safeTimeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(safeTimeoutSeconds));
        // 2. 统一为请求配置 JSON Content-Type，避免每次调用手动重复设置。
        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInitializer(request -> request.getHeaders().setContentType(MediaType.APPLICATION_JSON))
                .build();
    }

    /**
     * 兼容配置里只写域名、`/v1` 或已带 `/chat/completions` 的几种情况。
     */
    private String resolveRequestUrl() {
        // 1. 兼容不同供应商的 baseUrl 配置方式，尽量让只配域名或 /v1 的场景也能直接工作。
        String normalized = safeValue(baseUrl);
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        // 2. 常见 OpenAI 兼容接口默认补上 /chat/completions 末尾路径。
        if (normalized.endsWith("/compatible-mode/v1")) {
            return normalized + "/chat/completions";
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized;
    }

    /**
     * 解析大模型返回结果。
     * 优先解析 OpenAI 兼容格式的 `choices[0].message.content`，失败时再做文本兜底提取。
     */
    private int parseAiResponse(String response) {
        // 1. 先按标准 JSON 结构解析，再做文本兜底，尽量提高审核结果可解析率。
        if (isBlank(response)) {
            return -1;
        }
        try {
            JSONObject respJson = JSON.parseObject(response);
            if (respJson == null) {
                return -1;
            }
            if (respJson.containsKey("error")) {
                log.warn("AI audit provider returned error payload: {}", limitText(response, 1200));
                return -1;
            }

            // 2. 优先按 OpenAI 兼容格式解析 choices[0].message.content。
            JSONArray choices = respJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return tryExtractResultValue(response);
            }

            JSONObject firstChoice = choices.getJSONObject(0);
            if (firstChoice == null) {
                return tryExtractResultValue(response);
            }

            JSONObject message = firstChoice.getJSONObject("message");
            String content = message == null ? null : message.getString("content");
            if (isBlank(content)) {
                return tryExtractResultValue(response);
            }

            // 3. 成功拿到 message.content 后，再交给统一文本解析器处理。
            return parseResultText(content);
        } catch (Exception e) {
            log.warn("AI audit response JSON parse failed. response={}", limitText(response, 1200), e);
            return tryExtractResultValue(response);
        }
    }

    /**
     * 从模型返回的文本内容中解析审核结论。
     *
     * @param resultText 模型返回文本
     * @return 0-正常，1-违规，-1-无法确定
     */
    private int parseResultText(String resultText) {
        // 1. 模型有时会返回包裹 JSON 或自然语言，这里尽量从中提取 result 字段或语义关键词。
        if (isBlank(resultText)) {
            return -1;
        }
        String cleaned = cleanupResultText(resultText);

        try {
            // 2. 优先尝试把清洗后的文本直接按 JSON 解析。
            JSONObject resultJson = JSON.parseObject(cleaned);
            if (resultJson != null && resultJson.containsKey("result")) {
                return normalizeResultValue(resultJson.getIntValue("result"));
            }
        } catch (Exception ignored) {
        }

        // 3. JSON 解析失败时，继续兜底提取 result 数值或中文语义关键词。
        int extracted = tryExtractResultValue(cleaned);
        if (extracted != -1) {
            return extracted;
        }

        String lower = cleaned.toLowerCase();
        if (lower.contains("违规")) {
            return 1;
        }
        if (lower.contains("正常") || lower.contains("未违规") || lower.contains("不违规")) {
            return 0;
        }
        return -1;
    }

    /**
     * 尝试从任意文本中提取 result 数值。
     *
     * @param text 原始文本
     * @return 0-正常，1-违规，-1-无法确定
     */
    private int tryExtractResultValue(String text) {
        // 1. 兜底从文本中提取 `"result":0/1` 这类片段。
        if (isBlank(text)) {
            return -1;
        }
        Matcher matcher = RESULT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return normalizeResultValue(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 规范化模型返回的结果值。
     *
     * @param value 原始结果值
     * @return 0-正常，1-违规，其他统一映射为 -1
     */
    private int normalizeResultValue(int value) {
        if (value == 1) {
            return 1;
        }
        if (value == 0) {
            return 0;
        }
        return -1;
    }

    /**
     * 清洗模型返回文本，只保留核心 JSON 片段。
     *
     * @param text 原始文本
     * @return 清洗后的文本
     */
    private String cleanupResultText(String text) {
        // 1. 去掉代码块标记和多余包裹，只保留真正的 JSON 片段。
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "");
            cleaned = cleaned.replaceFirst("\\s*```$", "");
        }
        // 2. 如果文本中混有解释性内容，则截出最外层 JSON 对象范围。
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        return cleaned.trim();
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 字符串
     * @return true 表示为空白
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 安全获取字符串值并去除首尾空格。
     *
     * @param value 原始字符串
     * @return 非 null 字符串
     */
    private String safeValue(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 截断过长日志文本。
     *
     * @param text      原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String limitText(String text, int maxLength) {
        // 1. 只保留日志中必要的前缀内容，避免大响应把日志刷爆。
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength) + "...";
    }
}
