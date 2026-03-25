package com.feng.rag.model.siliconflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.rag.controller.R;
import com.feng.rag.model.AbstractModel;
import com.feng.rag.model.config.GlobalModelProperties;
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
        objectMapper = new ObjectMapper();

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
}
