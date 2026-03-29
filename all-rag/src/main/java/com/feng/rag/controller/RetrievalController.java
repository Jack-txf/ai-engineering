package com.feng.rag.controller;

import com.feng.rag.retrieval.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
        // TODO 后续加上会话ID
        retrievalService.vectorRetrieve(request.query());

        return R.ok();
    }

    /**
     * 向量检索接口
     *
     * @param query 查询文本
     * @param topK  返回结果数量（默认10）
     * @param orgId 组织ID（默认default）
     * @return 检索结果
     */
    @GetMapping("/vector-search")
    public R vectorSearch(@RequestParam String query,
                          @RequestParam(required = false, defaultValue = "10") Integer topK,
                          @RequestParam(required = false, defaultValue = "default") String orgId) {
        log.info("[RetrievalController] 向量检索: query={}, topK={}, orgId={}", query, topK, orgId);

        retrievalService.vectorRetrieve(query, topK, orgId);

        return R.ok();
    }

    /**
     * 请求体
     */
    public record QueryRequest(String query, String sessionId) {
    }
}
