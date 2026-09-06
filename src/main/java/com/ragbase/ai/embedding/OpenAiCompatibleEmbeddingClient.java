package com.ragbase.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbase.common.web.BizException;
import com.ragbase.config.AiProperties;
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
 * OpenAI 兼容的 Embedding 客户端（硅基流动 / OpenAI / 智谱等均兼容）
 */
@Slf4j
@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_BATCH = 32;

    private final AiProperties props;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiCompatibleEmbeddingClient(AiProperties props) {
        this.props = props;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> result = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += MAX_BATCH) {
            List<String> batch = texts.subList(i, Math.min(i + MAX_BATCH, texts.size()));
            result.addAll(embedBatch(batch));
        }
        return result;
    }

    @Override
    public int dimension() {
        return props.getEmbedding().getDimension();
    }

    private List<float[]> embedBatch(List<String> texts) {
        AiProperties.Embedding cfg = props.getEmbedding();
        if (!StringUtils.hasText(cfg.getApiKey())) {
            throw new BizException("EMBEDDING_API_KEY 未配置，请在环境变量或 application.yml 中配置");
        }
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", cfg.getModel(),
                    "input", texts
            ));
            Request request = new Request.Builder()
                    .url(cfg.getBaseUrl() + "/v1/embeddings")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .post(RequestBody.create(body, JSON))
                    .build();

            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new BizException("EMBEDDING_ERROR",
                            "Embedding 调用失败: HTTP " + response.code() + " " + response.body() == null ? "" : response.body().string());
                }
                JsonNode root = mapper.readTree(response.body().string());
                List<float[]> vectors = new ArrayList<>(texts.size());
                for (JsonNode node : root.path("data")) {
                    JsonNode embedding = node.path("embedding");
                    float[] vector = new float[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        vector[i] = (float) embedding.get(i).asDouble();
                    }
                    vectors.add(vector);
                }
                return vectors;
            }
        } catch (IOException e) {
            throw new BizException("EMBEDDING_ERROR", "Embedding 网络异常: " + e.getMessage());
        }
    }
}
