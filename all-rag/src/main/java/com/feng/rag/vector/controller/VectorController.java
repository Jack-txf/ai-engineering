package com.feng.rag.vector.controller;

import com.feng.rag.controller.R;
import com.feng.rag.vector.dto.CreateCollectionRequest;
import com.feng.rag.vector.dto.SearchRequest;
import com.feng.rag.vector.entity.SearchResult;
import com.feng.rag.vector.entity.VectorDocument;
import com.feng.rag.vector.service.MilvusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 向量数据库操作 Controller
 *
 * @author txf
 * @since 2026/3/26
 */
@Slf4j
@RestController
@RequestMapping("/vector")
@RequiredArgsConstructor
public class VectorController {

    private final MilvusService milvusService;

    // ==================== 集合管理 ====================
    /**
     * 创建集合
     */
    @PostMapping("/collections")
    public R createCollection(
            @Validated @RequestBody CreateCollectionRequest request) {
        log.info("[VectorController] 创建集合: name={}, dimension={}",
                request.getCollectionName(), request.getDimension());

        boolean success = request.getCollectionName() != null
                ? milvusService.createCollection(request.getCollectionName(), request.getDimension())
                : milvusService.createCollection();

        return R.ok().add("success", success);
    }
    /**
     * 删除集合
     */
    @DeleteMapping("/collections/{collectionName}")
    public R dropCollection(
            @PathVariable String collectionName) {
        log.info("[VectorController] 删除集合: {}", collectionName);
        boolean success = milvusService.dropCollection(collectionName);
        return R.ok().add("success", success);
    }
    /**
     * 获取所有集合
     */
    @GetMapping("/collections")
    public R listCollections() {
        log.info("[VectorController] 获取集合列表");
        List<String> collections = milvusService.listCollections();
        return R.ok().add("collections", collections);
    }
    /**
     * 检查集合是否存在
     */
    @GetMapping("/collections/{collectionName}/exists")
    public R hasCollection(
            @PathVariable String collectionName) {
        boolean exists = milvusService.hasCollection(collectionName);
        return R.ok().add("exists", exists);
    }

    // ==================== 文档操作 ====================
    /**
     * 插入单个文档
     */
    @PostMapping("/collections/{collectionName}/documents")
    public R insertDocument(
            @PathVariable String collectionName,
            @Validated @RequestBody VectorDocument document) {
        log.info("[VectorController] 插入文档: collection={}", collectionName);
        String id = milvusService.insert(collectionName, document);
        return R.ok().add("id", id);
    }
    /**
     * 批量插入文档
     */
    @PostMapping("/collections/{collectionName}/documents/batch")
    public R insertBatch(
            @PathVariable String collectionName,
            @Validated @RequestBody List<VectorDocument> documents) {
        log.info("[VectorController] 批量插入文档: collection={}, count={}",
                collectionName, documents.size());
        List<String> ids = milvusService.insertBatch(collectionName, documents);
        return R.ok().add("ids", ids);
    }
    /**
     * 根据 ID 删除文档
     */
    @DeleteMapping("/collections/{collectionName}/documents")
    public R deleteDocuments(
            @PathVariable String collectionName,
            @RequestParam List<String> ids) {
        log.info("[VectorController] 删除文档: collection={}, ids={}", collectionName, ids);
        long count = milvusService.deleteByIds(collectionName, ids);
        return R.ok().add("deletedCount", count);
    }
    /**
     * 根据 ID 查询文档
     */
    @GetMapping("/collections/{collectionName}/documents")
    public R getDocuments(
            @PathVariable String collectionName,
            @RequestParam List<String> ids) {
        log.info("[VectorController] 查询文档: collection={}, ids={}", collectionName, ids);
        List<VectorDocument> documents = milvusService.getByIds(collectionName, ids);
        return R.ok().add("documents", documents);
    }
    /**
     * 获取集合文档数量
     */
    @GetMapping("/collections/{collectionName}/count")
    public R countDocuments(
            @PathVariable String collectionName) {
        long count = milvusService.count(collectionName);
        return R.ok().add("count", count);
    }

    // ==================== 向量搜索 ====================
    /**
     * 向量相似度搜索
     */
    @PostMapping("/collections/{collectionName}/search")
    public R search(
            @PathVariable String collectionName,
            @Validated @RequestBody SearchRequest request) {
        log.info("[VectorController] 向量搜索: collection={}, topK={}",
                collectionName, request.getTopK());

        List<SearchResult> results;
        if (request.getFilterExpr() != null && !request.getFilterExpr().isEmpty()) {
            results = milvusService.search(collectionName, request.getVector(),
                    request.getTopK(), request.getFilterExpr());
        } else {
            results = milvusService.search(collectionName, request.getVector(), request.getTopK());
        }

        return R.ok().add("results", results);
    }

    // ==================== 简化接口（使用默认集合） ====================
    /**
     * 使用默认集合插入文档
     */
    @PostMapping("/documents")
    public R insertDocumentDefault(
            @Validated @RequestBody VectorDocument document) {
        String id = milvusService.insert(document);
        return R.ok().add("id", id);
    }
    /**
     * 默认集合向量搜索
     */
    @PostMapping("/search")
    public R searchDefault(
            @Validated @RequestBody SearchRequest request) {
        List<SearchResult> results = milvusService.search(request.getVector(), request.getTopK());
        return R.ok().add("results", results);
    }
}
