package com.feng.rag.retrieval;

import com.feng.rag.model.ModelFactory;
import com.feng.rag.retrieval.input.UserInputProcessor;
import com.feng.rag.retrieval.obj.DialogueTurn;
import com.feng.rag.retrieval.obj.ProcessedQuery;
import com.feng.rag.vector.service.VectorService;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
@Service()
@Slf4j
public class RetrievalService {

    private final UserInputProcessor userInputProcessor;
    private final VectorService milvusService;

    /**
     * 简单的对话历史存储（内存中）
     * 实际生产环境应该使用 Redis 或数据库存储
     * Key: sessionId, Value: 对话历史列表
     */
    private final Map<String, List<DialogueTurn>> dialogueHistoryStore = new ConcurrentHashMap<>();

    public RetrievalService(UserInputProcessor userInputProcessor,
                            ModelFactory modelFactory,
                            VectorService milvusService) {
        this.userInputProcessor = userInputProcessor;
        this.milvusService = milvusService;
    }


    /**
     * 单纯的向量检索
     *
     * @param userQuery 用户查询
     * @param topK      返回结果数量
     * @param orgId     组织ID
     * @return 搜索结果列表
     */
    public SearchResp vectorRetrieve(String userQuery, Integer topK, String orgId) {
        log.info("[RetrievalService] 开始向量检索: query={}, topK={}, orgId={}",
                userQuery.substring(0, Math.min(userQuery.length(), 50)), topK, orgId);
        // 1. 用户输入的意图识别与重写
        ProcessedQuery processed = userInputProcessor.process(userQuery);
        // 如果不需要检索（闲聊或敏感词），直接返回空列表
        if (!processed.needsRetrieval()) {
            log.info("[RetrievalService] 无需检索，直接返回: intent={}", processed.getIntent());
            return null;
        }
        // 使用重写后的查询进行检索
        String queryToSearch = processed.getRewrittenQuery() != null
                ? processed.getRewrittenQuery()
                : userQuery;
        // 2. 执行向量检索
        // 使用默认集合
        // 3. 打印输出显示
        // log.info("[RetrievalService] 向量检索完成，响应结果：{}", searchResp.getSearchResults());
        // 4. 返回结果
        return milvusService.vectorSearch(
                queryToSearch,
                topK != null ? topK : 10,
                orgId != null ? orgId : "default",
                null  // 使用默认集合
        );
    }

    /**
     * 单纯的向量检索（使用默认参数）
     *
     * @param userQuery 用户查询
     * @return 搜索结果列表
     */
    public SearchResp vectorRetrieve(String userQuery) {
        return vectorRetrieve(userQuery, 10, "org_id123456"); // TODO 这里先写死
    }

}
