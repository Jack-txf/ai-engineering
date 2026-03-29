package com.feng.rag.controller;

import com.feng.rag.retrieval.RetrievalService;
import com.feng.rag.retrieval.model.DialogueTurn;
import com.feng.rag.retrieval.model.ProcessedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 在线检索 Controller - 处理用户查询的在线RAG流程
 *
 * @author txf
 * @since 2026/3/29
 */
@Slf4j
@RestController
@RequestMapping("/v1/retrieval")
@RequiredArgsConstructor
public class RetrievalController {

    private final RetrievalService retrievalService;

    /**
     * 处理用户查询（完整流程入口）
     *
     * @param request 请求体（包含query和sessionId）
     * @return 处理结果
     */
    @PostMapping("/query")
    public R processQuery(@RequestBody QueryRequest request) {
        String sessionId = request.sessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        log.info("[RetrievalController] 收到查询: sessionId={}, query={}", sessionId, request.query());

        ProcessedQuery result = retrievalService.processUserQuery(request.query(), sessionId);

        return R.ok()
                .add("sessionId", sessionId)
                .add("originalQuery", result.getOriginalQuery())
                .add("intent", result.getIntent().getCode())
                .add("intentDesc", result.getIntent().getDescription())
                .add("needsRetrieval", result.needsRetrieval())
                .add("shouldReject", result.shouldReject())
                .add("rewrittenQuery", result.getRewrittenQuery())
                .add("reason", result.getIntentReason());
    }

    /**
     * 快速测试 - 只进行意图识别和查询重写
     *
     * @param query 用户输入
     * @return 处理结果
     */
    @GetMapping("/test")
    public R quickTest(@RequestParam String query) {
        log.info("[RetrievalController] 快速测试: {}", query);

        ProcessedQuery result = retrievalService.processUserQuery(query);

        return R.ok()
                .add("originalQuery", result.getOriginalQuery())
                .add("intent", result.getIntent().getCode())
                .add("intentDesc", result.getIntent().getDescription())
                .add("rewrittenQuery", result.getRewrittenQuery())
                .add("reason", result.getIntentReason());
    }

    /**
     * 获取对话历史
     *
     * @param sessionId 会话ID
     * @return 对话历史
     */
    @GetMapping("/history/{sessionId}")
    public R getHistory(@PathVariable String sessionId) {
        List<DialogueTurn> history = retrievalService.getDialogueHistory(sessionId);
        return R.ok().add("history", history);
    }

    /**
     * 清除对话历史
     *
     * @param sessionId 会话ID
     * @return 结果
     */
    @DeleteMapping("/history/{sessionId}")
    public R clearHistory(@PathVariable String sessionId) {
        retrievalService.clearDialogueHistory(sessionId);
        return R.ok().add("cleared", true);
    }

    /**
     * 请求体
     */
    public record QueryRequest(String query, String sessionId) {
    }
}
