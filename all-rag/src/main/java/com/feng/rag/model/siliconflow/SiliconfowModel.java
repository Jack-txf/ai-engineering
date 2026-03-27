package com.feng.rag.model.siliconflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.feng.rag.controller.R;
import com.feng.rag.model.AbstractModel;
import com.feng.rag.model.config.GlobalModelProperties;
import com.feng.rag.model.embedding.EmbeddingResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSources;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Description: 硅基流动厂商的配置管理类
 * @Author: txf
 * @Date: 2026/3/24
 */
@Slf4j
public class SiliconfowModel extends AbstractModel {

    public static final String SILICONFLOW = "siliconflow";

    public static final String CHAT_URL = "/chat/completions";
    public static final String EMBED_URL = "/embeddings";
    public static final String RERANK_URL = "/rerank";

    private final OkHttpClient siliconfowClient;
    private final ObjectMapper objectMapper;
    private final EventSource.Factory factory;

    private final GlobalModelProperties.ProviderConfig providerConfig;

    public SiliconfowModel(GlobalModelProperties.ProviderConfig providerConfig) {
        this.providerConfig = providerConfig;
        objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        // 创建OkHttpClient
        this.siliconfowClient = new OkHttpClient.Builder()
                .connectTimeout(3000, TimeUnit.MILLISECONDS) // 设置连接超时时间
                .retryOnConnectionFailure(true) // 允许重试
                .build();
        this.factory = EventSources.createFactory(this.siliconfowClient); // 创建 EventSource.Factory
    }

    @Override
    public R chatSync(List<Message> messages) {
        log.info("开始调用硅基流动[同步chat]...");
        String json = buildBodyJson(messages, false);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(providerConfig.getBaseUrl() + CHAT_URL)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + providerConfig.getApiKey())
                .post(body)
                .build();
        Call call = siliconfowClient.newCall(request);
        Response response;
        try {
            response = call.execute();
            String responseBody = response.body().string();
            log.info("硅基流动[同步chat]返回：{}", responseBody);
            return R.ok().add("aiRes", responseBody);
        } catch (SocketTimeoutException e) {
            log.error("SocketTimeoutException: {}", e.getMessage());
            return R.error("SocketTimeoutException: " + e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SseEmitter chatStream(List<Message> messages) {
        log.info("开始调用硅基流动[流式chat]...");
        String json = buildBodyJson(messages, true);

        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(providerConfig.getBaseUrl() + CHAT_URL)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + providerConfig.getApiKey())
                .post(body)
                .build();

        // 创建 SseEmitter，超时时间设为0表示不超时
        SseEmitter emitter = new SseEmitter(0L);
        factory.newEventSource(request, new StreamHandler(emitter));
        // 客户端断开连接时的处理
        emitter.onCompletion(() -> log.info("硅基流动[流式chat]连接已关闭"));
        emitter.onTimeout(() -> log.warn("硅基流动[流式chat]连接超时"));
        emitter.onError(e -> log.error("硅基流动[流式chat]连接错误", e));

        return emitter;
    }
    /**
     * 构建请求体 JSON
     * @param messages 消息列表
     * @param stream   是否流式
     * @return JSON 字符串
     */
    private String buildBodyJson(List<Message> messages, boolean stream) {
        Map<String, Object> map = new HashMap<>();
        map.put("model", providerConfig.getChatModel().getFirst());
        map.put("messages", messages);
        map.put("stream", stream);
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("构建JSON请求体失败", e);
        }
    }

    // -------------------------------------------- Embedding部分 -------------------------
    @Override
    public EmbeddingResponse embedding(List<String> text) {
        if (text == null || text.isEmpty()) {
            log.warn("输入文本为空，无法进行嵌入！");
            return EmbeddingResponse.builder()
                    .errorMsg("输入文本为空，无法进行嵌入！")
                    .build();
        }
        if ( text.size() > 32 ) {
            log.warn("这一批次超过最大限制，请降低分批数量！");
            return EmbeddingResponse.builder()
                    .errorMsg("这一批次超过最大限制，请降低分批数量！")
                    .build();
        }
        log.info("开始调用硅基流动[embedding]...");
        // 1.构建请求
        String jsonData = buildEmbeddingBodyJson(text);
        RequestBody body = RequestBody.create(jsonData, MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(providerConfig.getBaseUrl() + EMBED_URL)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + providerConfig.getApiKey())
                .post(body)
                .build();

        Call call = siliconfowClient.newCall(request);
        try {
            Response response = call.execute();
            if (!response.isSuccessful()) {
                log.error(" SilvaFlow Embedding not success: {}", response.body().string());
                return EmbeddingResponse.builder()
                        .errorMsg(" SilvaFlow Embedding Error: " + response.body().string())
                        .build();
            }
            return buildEmbeddingResponse(response.body().string());
        } catch (IOException e) {
            log.error(" SilvaFlow Embedding Error: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
    private String buildEmbeddingBodyJson(List<String> text){
        Map<String, Object> map = new HashMap<>();
        map.put("model", providerConfig.getEmbedModel().getFirst());
        map.put("input", text);
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("构建JSON请求体失败", e);
        }
    }
    private EmbeddingResponse buildEmbeddingResponse(String responseBody) {
        try {
            EmbeddingResponse embeddingResponse = objectMapper.readValue(responseBody, EmbeddingResponse.class);
            embeddingResponse.setErrorMsg(null);
            return embeddingResponse;
        } catch (JsonProcessingException e) {
            log.error(" SilvaFlow Embedding response convert to json error: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
