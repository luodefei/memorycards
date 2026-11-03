package com.luodefei.memorycards.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.luodefei.memorycards.server.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 卡片深度研究：把卡片正反面发给本机 shim（DeepSeek Anthropic 兼容代理），
 * 由 shim 转发到上游大模型，返回研究文本。
 */
@Service
public class ResearchService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final String shimUrl;
    private final String model;
    private final int maxTokens;

    public ResearchService(ObjectMapper objectMapper,
                           @Value("${app.research.shim-url:http://127.0.0.1:8318}") String shimUrl,
                           @Value("${app.research.model:DeepSeek-V4-Flash}") String model,
                           @Value("${app.research.max-tokens:4096}") int maxTokens,
                           @Value("${app.research.timeout-seconds:120}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.shimUrl = shimUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
    }

    public String deepResearch(String front, String back) {
        String prompt = buildPrompt(front, back == null ? "" : back);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        ArrayNode messages = body.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(shimUrl + "/v1/messages"))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "研究服务返回错误(" + response.statusCode() + ")");
            }
            JsonNode root = objectMapper.readTree(response.body());
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    sb.append(block.path("text").asText());
                }
            }
            String result = sb.toString().trim();
            if (result.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "研究服务未返回内容");
            }
            return result;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "研究服务调用失败：" + e.getMessage());
        }
    }

    private String buildPrompt(String front, String back) {
        return "你是一位知识专家，请对下面这张记忆卡片做一次「深度研究」，帮助学习者真正吃透这个知识点。\n"
                + "\n"
                + "卡片正面（问题/关键词）：\n" + front + "\n"
                + (back.isBlank() ? "" : "\n卡片背面（现有答案）：\n" + back + "\n")
                + "\n"
                + "请用中文输出，结构如下：\n"
                + "1. 核心概念：用 2-3 句话讲清本质\n"
                + "2. 深入解析：原理、机制或推导过程\n"
                + "3. 举例说明：1-2 个具体例子或应用场景\n"
                + "4. 常见混淆：易混淆的概念或误区对比\n"
                + "5. 记忆技巧：一个帮助记忆的口诀、联想或类比\n"
                + "\n"
                + "要求：内容准确、详实但不冗长，总长度控制在 800 字以内。";
    }
}
