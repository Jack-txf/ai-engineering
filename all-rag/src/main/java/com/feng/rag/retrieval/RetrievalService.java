package com.feng.rag.retrieval;

import com.feng.rag.retrieval.model.DialogueTurn;
import com.feng.rag.retrieval.model.ProcessedQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 检索服务 - 在线RAG流程的核心服务
 * 职责：协调用户输入处理、检索、重排等流程
 *
 * @author txf
 * @since 2026/3/28
 */
@Service
@Slf4j
public class RetrievalService {

    private final UserInputProcessor userInputProcessor;

    /**
     * 简单的对话历史存储（内存中）
     * 实际生产环境应该使用 Redis 或数据库存储
     * Key: sessionId, Value: 对话历史列表
     */
    private final Map<String, List<DialogueTurn>> dialogueHistoryStore = new ConcurrentHashMap<>();

    public RetrievalService(UserInputProcessor userInputProcessor) {
        this.userInputProcessor = userInputProcessor;
    }

    /**
     * 处理用户查询（完整的在线RAG流程入口）
     *
     * @param query     用户原始输入
     * @param sessionId 会话ID（用于获取对话历史）
     * @return 处理后的查询结果
     */
    public ProcessedQuery processUserQuery(String query, String sessionId) {
        log.info("[RetrievalService] 处理用户查询: sessionId={}, query={}", sessionId, query);
        // 1. 获取对话历史
        List<DialogueTurn> history = dialogueHistoryStore.getOrDefault(sessionId, new ArrayList<>());
        // 2. 用户输入处理（意图识别 + 查询重写）
        ProcessedQuery processedQuery = userInputProcessor.process(query, history);
        // 3. 如果不需要检索，直接返回
        if (!processedQuery.needsRetrieval()) {
            log.info("[RetrievalService] 无需检索，直接返回: intent={}", processedQuery.getIntent());
            // 记录当前轮次（AI回答会在后续补充）
            addDialogueTurn(sessionId, query, null);
            return processedQuery;
        }
        // 4. 后续步骤（检索、重排等）TODO
        // String rewrittenQuery = processedQuery.getRewrittenQuery();
        // List<SearchResult> results = hybridRetrieve(rewrittenQuery);
        // results = rerank(rewrittenQuery, results);
        // 5. 记录当前轮次
        addDialogueTurn(sessionId, query, null);
        return processedQuery;
    }

    /**
     * 处理用户查询（无会话ID，无对话历史）
     *
     * @param query 用户原始输入
     * @return 处理后的查询结果
     */
    public ProcessedQuery processUserQuery(String query) {
        return userInputProcessor.process(query);
    }

    /**
     * 添加对话轮次
     *
     * @param sessionId   会话ID
     * @param userQuery   用户问题
     * @param aiResponse  AI回答（可能为null，后续补充）
     */
    public void addDialogueTurn(String sessionId, String userQuery, String aiResponse) {
        dialogueHistoryStore.computeIfAbsent(sessionId, k -> new ArrayList<>());

        List<DialogueTurn> history = dialogueHistoryStore.get(sessionId);
        DialogueTurn turn = DialogueTurn.builder()
                .userQuery(userQuery)
                .aiResponse(aiResponse)
                .turnNumber(history.size() + 1)
                .build();

        history.add(turn);

        // 限制历史长度，保留最近10轮
        if (history.size() > 10) {
            history.remove(0);
        }
    }

    /**
     * 更新最后一轮AI的回答
     *
     * @param sessionId  会话ID
     * @param aiResponse AI回答
     */
    public void updateLastAiResponse(String sessionId, String aiResponse) {
        List<DialogueTurn> history = dialogueHistoryStore.get(sessionId);
        if (history != null && !history.isEmpty()) {
            DialogueTurn lastTurn = history.get(history.size() - 1);
            lastTurn.setAiResponse(aiResponse);
        }
    }

    /**
     * 获取对话历史
     *
     * @param sessionId 会话ID
     * @return 对话历史列表
     */
    public List<DialogueTurn> getDialogueHistory(String sessionId) {
        return dialogueHistoryStore.getOrDefault(sessionId, new ArrayList<>());
    }

    /**
     * 清除对话历史
     *
     * @param sessionId 会话ID
     */
    public void clearDialogueHistory(String sessionId) {
        dialogueHistoryStore.remove(sessionId);
    }
}
