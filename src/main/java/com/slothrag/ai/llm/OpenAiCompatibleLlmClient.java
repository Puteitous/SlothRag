package com.slothrag.ai.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slothrag.common.web.BizException;
import com.slothrag.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * OpenAI 兼容的流式对话客户端（DeepSeek / OpenAI / 硅基流动均兼容）
 * <p>
 * 支持工具调用：调用时可传入 {@code tools}，模型若返回 {@code tool_calls}
 * 会通过 {@link StreamCallback#onToolCalls} 回调出来，供上层执行工具后回喂继续生成。
 */
@Slf4j
@Component
public class OpenAiCompatibleLlmClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String DONE_SENTINEL = "[DONE]";

    private final AiProperties props;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiCompatibleLlmClient(AiProperties props) {
        this.props = props;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 不携带工具的流式对话
     */
    public Runnable streamChat(List<ChatMessage> messages, StreamCallback callback) {
        return streamChat(messages, null, callback);
    }

    /**
     * 携带工具的流式对话，返回取消句柄
     */
    public Runnable streamChat(List<ChatMessage> messages, List<ToolDefinition> tools, StreamCallback callback) {
        AiProperties.Llm cfg = props.getLlm();
        if (!StringUtils.hasText(cfg.getApiKey())) {
            throw new BizException("LLM_API_KEY 未配置，请在环境变量或 application.yml 中配置");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", cfg.getModel());
            payload.put("messages", messages);
            payload.put("stream", true);
            payload.put("temperature", 0.3);
            if (tools != null && !tools.isEmpty()) {
                payload.put("tools", tools.stream().map(ToolDefinition::toPayload).toList());
                payload.put("tool_choice", "auto");
            }
            String body = mapper.writeValueAsString(payload);
            Request request = new Request.Builder()
                    .url(cfg.getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .post(RequestBody.create(body, JSON))
                    .build();
            Call call = http.newCall(request);

            new Thread(() -> {
                try (Response response = call.execute()) {
                    if (!response.isSuccessful()) {
                        callback.onError(new BizException("LLM_ERROR",
                                "LLM 调用失败: HTTP " + response.code()));
                        return;
                    }
                    ResponseBody responseBody = response.body();
                    if (responseBody == null) {
                        callback.onError(new BizException("LLM_ERROR", "LLM 响应为空"));
                        return;
                    }

                    List<ToolCall> toolCalls = new ArrayList<>();
                    String finishReason = null;
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data:")) {
                                continue;
                            }
                            String data = line.substring(5).trim();
                            if (data.isEmpty()) {
                                continue;
                            }
                            if (DONE_SENTINEL.equals(data)) {
                                break;
                            }
                            try {
                                JsonNode node = mapper.readTree(data);
                                JsonNode choices = node.path("choices");
                                if (choices.isEmpty() || !choices.isArray()) {
                                    continue;
                                }
                                JsonNode choice = choices.get(0);
                                JsonNode delta = choice.path("delta");
                                if (delta.isObject()) {
                                    String content = delta.path("content").asText(null);
                                    if (content != null && !content.isEmpty()) {
                                        callback.onDelta(content);
                                    }
                                    JsonNode tcs = delta.path("tool_calls");
                                    if (tcs.isArray()) {
                                        for (JsonNode tc : tcs) {
                                            mergeToolCall(toolCalls, tc);
                                        }
                                    }
                                }
                                if (choice.hasNonNull("finish_reason")) {
                                    String fr = choice.get("finish_reason").asText();
                                    if (!fr.isEmpty()) {
                                        finishReason = fr;
                                    }
                                }
                            } catch (IOException e) {
                                log.debug("SSE 行解析失败: {}", data);
                            }
                        }
                        if ("tool_calls".equals(finishReason) || !toolCalls.isEmpty()) {
                            callback.onToolCalls(validToolCalls(toolCalls));
                        }
                        callback.onComplete();
                    }
                } catch (Exception e) {
                    log.error("LLM 流式调用异常", e);
                    callback.onError(e);
                }
            }, "llm-stream").start();

            return () -> {
                try {
                    call.cancel();
                } catch (Exception ignored) {
                }
            };
        } catch (IOException e) {
            throw new BizException("LLM_ERROR", "LLM 请求构造失败: " + e.getMessage());
        }
    }

    private void mergeToolCall(List<ToolCall> acc, JsonNode tcNode) {
        int index = tcNode.path("index").asInt(0);
        while (acc.size() <= index) {
            acc.add(new ToolCall());
        }
        ToolCall target = acc.get(index);

        if (tcNode.hasNonNull("id")) {
            target.setId(tcNode.get("id").asText());
        }
        if (tcNode.hasNonNull("type")) {
            target.setType(tcNode.get("type").asText());
        }
        JsonNode fn = tcNode.path("function");
        if (!fn.isObject()) {
            return;
        }
        if (target.getFunction() == null) {
            target.setFunction(new FunctionCall());
        }
        FunctionCall f = target.getFunction();
        if (fn.hasNonNull("name")) {
            f.setName(fn.get("name").asText());
        }
        if (fn.hasNonNull("arguments")) {
            String cur = f.getArguments() == null ? "" : f.getArguments();
            f.setArguments(cur + fn.get("arguments").asText());
        }
    }

    private List<ToolCall> validToolCalls(List<ToolCall> all) {
        List<ToolCall> out = new ArrayList<>();
        for (ToolCall tc : all) {
            if (tc.getFunction() != null && StringUtils.hasText(tc.getFunction().getName())) {
                out.add(tc);
            }
        }
        return out;
    }
}