package com.slothrag.ai.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slothrag.common.web.BizException;
import com.slothrag.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 硅基流动 Rerank 客户端（BAAI/bge-reranker-v2-m3，Cohere 风格 /v1/rerank）
 */
@Slf4j
@Component
public class SiliconflowRerankClient implements RerankClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AiProperties props;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public SiliconflowRerankClient(AiProperties props) {
        this.props = props;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        AiProperties.Rerank cfg = props.getRerank();
        if (!StringUtils.hasText(cfg.getApiKey())) {
            throw new BizException("RERANK_API_KEY 未配置，请在环境变量或 application.yml 中配置");
        }
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", cfg.getModel(),
                    "query", query,
                    "documents", documents,
                    "top_n", topN));
            Request request = new Request.Builder()
                    .url(cfg.getBaseUrl() + "/v1/rerank")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .post(RequestBody.create(body, JSON))
                    .build();

            try (Response response = http.newCall(request).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                JsonNode root = mapper.readTree(responseBody);
                // 硅基流动错误以 HTTP 200 + body 业务错误码返回（如 code=20015），需显式识别
                if (root.has("code") && root.path("code").asInt() != 0) {
                    throw new BizException("RERANK_ERROR",
                            "Rerank 调用失败: " + root.path("message").asText("未知错误"));
                }
                if (!response.isSuccessful()) {
                    throw new BizException("RERANK_ERROR", "Rerank 调用失败: HTTP " + response.code());
                }
                List<RerankResult> results = new ArrayList<>();
                for (JsonNode node : root.path("results")) {
                    results.add(new RerankResult(
                            node.path("index").asInt(),
                            node.path("relevance_score").asDouble()));
                }
                return results;
            }
        } catch (IOException e) {
            throw new BizException("RERANK_ERROR", "Rerank 网络异常: " + e.getMessage());
        }
    }
}
