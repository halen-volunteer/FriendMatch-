package com.zero.usercenter.Service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zero.usercenter.Service.AiCheckService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 通义千问大模型内容审核服务实现
 *
 * 使用 OpenAI 兼容接口（dashscope compatible-mode），
 * 通过 Prompt 让模型判断文本是否违规，返回固定 JSON 格式避免解析失败。
 */
@Service
public class AiCheckServiceImpl implements AiCheckService {

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.model}")
    private String model;

    @Value("${ai.timeout-seconds:15}")
    private int timeoutSeconds;

    /**
     * 审核提示词
     * 要求模型只返回 JSON，避免自由发挥导致解析失败
     */
    private static final String SYSTEM_PROMPT =
            "你是一个内容安全审核员。判断用户发送的内容是否属于违规内容。" +
            "违规类型包括：色情低俗、暴力血腥、骚扰辱骂、诈骗广告、散布谣言。" +
            "请只返回如下 JSON 格式，不要有任何额外文字：" +
            "{\"result\": 0} 表示内容正常，{\"result\": 1} 表示内容违规。";

    @Override
    public int checkContent(String content) {
        if (content == null || content.isBlank()) return 0;

        try {
            //构建HTTP请求，调用AI
            RestClient client = RestClient.builder()
                    .requestInitializer(request ->
                            request.getHeaders().setContentType(MediaType.APPLICATION_JSON))
                    .build();
            //构造请求体
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),//规则
                            Map.of("role", "user", "content", content)//审核内容
                    ),
                    "max_tokens", 20,       // 只需要极短回复
                    "temperature", 0        // 确定性输出，不随机
            );
            //发送请求
            String response = client.post()
                    .uri(baseUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            // 解析响应：取 choices[0].message.content
            JSONObject respJson = JSON.parseObject(response);
            String resultText = respJson
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

/*  AI响应格式：
            {
                "choices": [
                {
                    "message": {
                    "content": "{\"result\":0}"
                                }
                }
                ]
            }
*/

            // 提取 result 字段
            JSONObject resultJson = JSON.parseObject(resultText.trim());
            return resultJson.getIntValue("result"); // 0 或 1

        } catch (Exception e) {
            // 调用失败（网络超时、解析异常等）默认返回 -1，主流程不受影响
            return -1;
        }
    }
}
