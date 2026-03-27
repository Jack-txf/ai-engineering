package com.feng.rag.vector.service.impl;

import com.feng.rag.vector.config.MilvusProperties;
import com.feng.rag.vector.entity.SearchResult;
import com.feng.rag.vector.entity.VectorDocument;
import com.feng.rag.vector.exception.MilvusException;
import com.feng.rag.vector.service.MilvusService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Milvus 服务实现
 *
 * @author txf
 * @since 2026/3/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusServiceImpl implements MilvusService {

    private final MilvusClientV2 milvusClient;
    private final MilvusProperties properties;
    private final Gson gson = new Gson();

    // ==================== 集合管理 ====================

    @Override
    public boolean createCollection(String collectionName, int dimension) {
        try {
            log.info("[Milvus] 创建集合: name={}, dimension={}", collectionName, dimension);

            if (hasCollection(collectionName)) {
                log.warn("[Milvus] 集合已存在: {}", collectionName);
                return true;
            }

            // 构建字段列表
            List<CreateCollectionReq.FieldSchema> fields = buildFieldSchemaList(dimension);

            // 构建索引参数
            List<IndexParam> indexParams = buildIndexParams();

            // 创建集合请求 - 使用 builder 模式
            CreateCollectionReq req = CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(CreateCollectionReq.CollectionSchema.builder()
                            .fieldSchemaList(fields)
                            .build())
                    .indexParams(indexParams)
                    .numShards(properties.getCollection().getShardsNum())
                    .build();

            milvusClient.createCollection(req);
            log.info("[Milvus] 集合创建成功: {}", collectionName);
            return true;

        } catch (Exception e) {
            log.error("[Milvus] 创建集合失败: {}", collectionName, e);
            throw new MilvusException("CREATE_COLLECTION", "创建集合失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean createCollection() {
        return createCollection(
                properties.getCollection().getName(),
                properties.getCollection().getDimension()
        );
    }

    @Override
    public boolean dropCollection(String collectionName) {
        try {
            log.info("[Milvus] 删除集合: {}", collectionName);

            if (!hasCollection(collectionName)) {
                log.warn("[Milvus] 集合不存在: {}", collectionName);
                return true;
            }

            milvusClient.dropCollection(DropCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());

            log.info("[Milvus] 集合删除成功: {}", collectionName);
            return true;

        } catch (Exception e) {
            log.error("[Milvus] 删除集合失败: {}", collectionName, e);
            throw new MilvusException("DROP_COLLECTION", "删除集合失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean dropCollection() {
        return dropCollection(properties.getCollection().getName());
    }

    @Override
    public boolean hasCollection(String collectionName) {
        try {
            return milvusClient.hasCollection(HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
        } catch (Exception e) {
            log.error("[Milvus] 检查集合存在失败: {}", collectionName, e);
            return false;
        }
    }

    @Override
    public List<String> listCollections() {
        try {
            ListCollectionsResp resp = milvusClient.listCollections();
            return resp.getCollectionNames();
        } catch (Exception e) {
            log.error("[Milvus] 获取集合列表失败", e);
            throw new MilvusException("LIST_COLLECTIONS", "获取集合列表失败", e);
        }
    }

    // ==================== 文档操作 ====================

    @Override
    public String insert(VectorDocument document) {
        return insert(properties.getCollection().getName(), document);
    }

    @Override
    public String insert(String collectionName, VectorDocument document) {
        List<String> ids = insertBatch(collectionName, Collections.singletonList(document));
        return CollectionUtils.isEmpty(ids) ? null : ids.getFirst();
    }

    @Override
    public List<String> insertBatch(List<VectorDocument> documents) {
        return insertBatch(properties.getCollection().getName(), documents);
    }

    @Override
    public List<String> insertBatch(String collectionName, List<VectorDocument> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return Collections.emptyList();
        }

        try {
            log.debug("[Milvus] 批量插入文档: collection={}, count={}", collectionName, documents.size());

            // 确保集合存在
            if (!hasCollection(collectionName)) {
                int dimension = getDimensionFromDocument(documents.get(0));
                createCollection(collectionName, dimension);
            }

            // 加载集合
            loadCollection(collectionName);

            // 构建插入数据（使用 JsonObject）
            List<JsonObject> data = documents.stream()
                    .map(this::convertToJsonObject)
                    .collect(Collectors.toList());

            InsertResp resp = milvusClient.insert(InsertReq.builder()
                    .collectionName(collectionName)
                    .data(data)
                    .build());

            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) (List<?>) resp.getPrimaryKeys();
            log.info("[Milvus] 文档插入成功: collection={}, count={}", collectionName, ids.size());
            return ids;

        } catch (Exception e) {
            log.error("[Milvus] 文档插入失败: collection={}", collectionName, e);
            throw new MilvusException("INSERT", "文档插入失败: " + e.getMessage(), e);
        }
    }

    @Override
    public long deleteByIds(List<String> ids) {
        return deleteByIds(properties.getCollection().getName(), ids);
    }

    @Override
    public long deleteByIds(String collectionName, List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }

        try {
            log.debug("[Milvus] 删除文档: collection={}, ids={}", collectionName, ids);

            // 构建删除表达式
            String expr = ids.stream()
                    .map(id -> String.format("id == '%s'", id))
                    .collect(Collectors.joining(" || "));

            DeleteResp resp = milvusClient.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter(expr)
                    .build());

            long count = resp.getDeleteCnt();
            log.info("[Milvus] 文档删除成功: collection={}, count={}", collectionName, count);
            return count;

        } catch (Exception e) {
            log.error("[Milvus] 文档删除失败: collection={}", collectionName, e);
            throw new MilvusException("DELETE", "文档删除失败: " + e.getMessage(), e);
        }
    }

    // ==================== 搜索查询 ====================

    @Override
    public List<SearchResult> search(List<Float> vector, int topK) {
        return search(properties.getCollection().getName(), vector, topK);
    }

    @Override
    public List<SearchResult> search(String collectionName, List<Float> vector, int topK) {
        return search(collectionName, vector, topK, null);
    }

    @Override
    public List<SearchResult> search(String collectionName, List<Float> vector, int topK, String filterExpr) {
        if (CollectionUtils.isEmpty(vector)) {
            return Collections.emptyList();
        }

        try {
            log.debug("[Milvus] 向量搜索: collection={}, topK={}", collectionName, topK);

            // 加载集合
            loadCollection(collectionName);

            // 构建向量数据
            List<BaseVector> vectors = Collections.singletonList(new FloatVec(vector));

            // 构建搜索请求
            SearchReq.SearchReqBuilder<?, ?> searchBuilder = SearchReq.builder()
                    .collectionName(collectionName)
                    .data(vectors)
                    .topK(topK)
                    .outputFields(Arrays.asList(
                            properties.getCollection().getIdField(),
                            properties.getCollection().getContentField(),
                            properties.getCollection().getMetadataField(),
                            "source", "chunk_index", "title"
                    ));

            // 添加过滤条件
            if (StringUtils.hasText(filterExpr)) {
                searchBuilder.filter(filterExpr);
            }

            SearchResp resp = milvusClient.search(searchBuilder.build());
            return convertSearchResults(resp);

        } catch (Exception e) {
            log.error("[Milvus] 向量搜索失败: collection={}", collectionName, e);
            throw new MilvusException("SEARCH", "向量搜索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<VectorDocument> getByIds(List<String> ids) {
        return getByIds(properties.getCollection().getName(), ids);
    }

    @Override
    public List<VectorDocument> getByIds(String collectionName, List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }

        try {
            log.debug("[Milvus] 查询文档: collection={}, ids={}", collectionName, ids);

            // 加载集合
            loadCollection(collectionName);

            // 转换 ids 为 List<Object>
            List<Object> idObjects = new ArrayList<>(ids);

            GetResp resp = milvusClient.get(GetReq.builder()
                    .collectionName(collectionName)
                    .ids(idObjects)
                    .outputFields(Arrays.asList(
                            properties.getCollection().getIdField(),
                            properties.getCollection().getContentField(),
                            properties.getCollection().getVectorField(),
                            properties.getCollection().getMetadataField(),
                            "source", "chunk_index", "title"
                    ))
                    .build());

            return convertGetResults(resp);

        } catch (Exception e) {
            log.error("[Milvus] 查询文档失败: collection={}", collectionName, e);
            throw new MilvusException("GET", "查询文档失败: " + e.getMessage(), e);
        }
    }

    // ==================== 统计信息 ====================

    @Override
    public long count(String collectionName) {
        try {
            log.debug("[Milvus] 统计文档数量: {}", collectionName);

            GetCollectionStatsResp resp = milvusClient.getCollectionStats(GetCollectionStatsReq.builder()
                    .collectionName(collectionName)
                    .build());

            return resp.getNumOfEntities();

        } catch (Exception e) {
            log.error("[Milvus] 统计文档数量失败: {}", collectionName, e);
            return 0;
        }
    }

    @Override
    public long count() {
        return count(properties.getCollection().getName());
    }

    // ==================== 集合加载 ====================

    @Override
    public void loadCollection(String collectionName) {
        try {
            milvusClient.loadCollection(LoadCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
        } catch (Exception e) {
            log.warn("[Milvus] 加载集合失败: {}", collectionName, e);
        }
    }

    @Override
    public void releaseCollection(String collectionName) {
        try {
            milvusClient.releaseCollection(ReleaseCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
        } catch (Exception e) {
            log.warn("[Milvus] 释放集合失败: {}", collectionName, e);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建字段 Schema 列表
     */
    private List<CreateCollectionReq.FieldSchema> buildFieldSchemaList(int dimension) {
        List<CreateCollectionReq.FieldSchema> fields = new ArrayList<>();

        // ID 字段
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(properties.getCollection().getIdField())
                .dataType(DataType.VarChar)
                .isPrimaryKey(true)
                .maxLength(64)
                .build());

        // 向量字段
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(properties.getCollection().getVectorField())
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build());

        // 内容字段
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(properties.getCollection().getContentField())
                .dataType(DataType.VarChar)
                .maxLength(65535)
                .build());

        // 元数据字段
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(properties.getCollection().getMetadataField())
                .dataType(DataType.VarChar)
                .maxLength(4096)
                .isNullable(true)
                .build());

        // 来源字段
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("source")
                .dataType(DataType.VarChar)
                .maxLength(512)
                .isNullable(true)
                .build());

        // 分块索引
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("chunk_index")
                .dataType(DataType.Int32)
                .isNullable(true)
                .build());

        // 标题
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("title")
                .dataType(DataType.VarChar)
                .maxLength(512)
                .isNullable(true)
                .build());

        return fields;
    }

    /**
     * 构建索引参数
     */
    private List<IndexParam> buildIndexParams() {
        List<IndexParam> params = new ArrayList<>();

        // 向量索引
        IndexParam.IndexParamBuilder<?, ?> indexBuilder = IndexParam.builder()
                .fieldName(properties.getCollection().getVectorField())
                .indexName(properties.getCollection().getVectorField() + "_idx")
                .indexType(IndexParam.IndexType.valueOf(properties.getCollection().getIndexType()))
                .metricType(IndexParam.MetricType.valueOf(properties.getCollection().getMetricType()));

        // HNSW 额外参数
        if ("HNSW".equals(properties.getCollection().getIndexType())) {
            java.util.Map<String, Object> extraParams = new java.util.HashMap<>();
            extraParams.put("M", 16);
            extraParams.put("efConstruction", 200);
            indexBuilder.extraParams(extraParams);
        }

        params.add(indexBuilder.build());
        return params;
    }

    /**
     * 将文档转换为 JsonObject
     */
    private JsonObject convertToJsonObject(VectorDocument doc) {
        JsonObject json = new JsonObject();

        // 生成 ID（如果没有）
        String id = StringUtils.hasText(doc.getId())
                ? doc.getId()
                : UUID.randomUUID().toString().replace("-", "");
        json.addProperty(properties.getCollection().getIdField(), id);

        // 向量
        if (doc.getVector() != null) {
            json.add(properties.getCollection().getVectorField(), gson.toJsonTree(doc.getVector()));
        }

        // 内容
        json.addProperty(properties.getCollection().getContentField(),
                doc.getContent() != null ? doc.getContent() : "");

        // 元数据（转换为 JSON 字符串）
        if (doc.getMetadata() != null && !doc.getMetadata().isEmpty()) {
            json.addProperty(properties.getCollection().getMetadataField(),
                    gson.toJson(doc.getMetadata()));
        } else {
            json.addProperty(properties.getCollection().getMetadataField(), "{}");
        }

        // 来源
        json.addProperty("source", doc.getSource() != null ? doc.getSource() : "");

        // 分块索引
        json.addProperty("chunk_index", doc.getChunkIndex() != null ? doc.getChunkIndex() : 0);

        // 标题
        json.addProperty("title", doc.getTitle() != null ? doc.getTitle() : "");

        return json;
    }

    /**
     * 从文档获取向量维度
     */
    private int getDimensionFromDocument(VectorDocument doc) {
        if (doc.getVector() != null && !doc.getVector().isEmpty()) {
            return doc.getVector().size();
        }
        return properties.getCollection().getDimension();
    }

    /**
     * 转换搜索结果
     */
    private List<SearchResult> convertSearchResults(SearchResp resp) {
        if (resp == null || CollectionUtils.isEmpty(resp.getSearchResults())) {
            return Collections.emptyList();
        }

        return resp.getSearchResults().stream()
                .flatMap(results -> results.stream().map(this::convertSearchResult))
                .collect(Collectors.toList());
    }

    /**
     * 转换单个搜索结果
     */
    private SearchResult convertSearchResult(SearchResp.SearchResult result) {
        Map<String, Object> entity = result.getEntity();

        return SearchResult.builder()
                .id(getStringValue(entity, properties.getCollection().getIdField()))
                .content(getStringValue(entity, properties.getCollection().getContentField()))
                .score(result.getScore())
                .source(getStringValue(entity, "source"))
                .chunkIndex(getIntValue(entity, "chunk_index"))
                .title(getStringValue(entity, "title"))
                .metadata(parseMetadata(getStringValue(entity, properties.getCollection().getMetadataField())))
                .build();
    }

    /**
     * 转换查询结果
     */
    private List<VectorDocument> convertGetResults(GetResp resp) {
        List<QueryResp.QueryResult> results = resp.getResults;
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        return results.stream()
                .map(this::convertQueryResultToDocument)
                .collect(Collectors.toList());
    }

    /**
     * 将 QueryResult 转换为 VectorDocument
     */
    @SuppressWarnings("unchecked")
    private VectorDocument convertQueryResultToDocument(QueryResp.QueryResult result) {
        Map<String, Object> entity = result.getEntity();

        List<Float> vector = null;
        Object vectorObj = entity.get(properties.getCollection().getVectorField());
        if (vectorObj instanceof List) {
            vector = ((List<Number>) vectorObj).stream()
                    .map(Number::floatValue)
                    .collect(Collectors.toList());
        }

        return VectorDocument.builder()
                .id(getStringValue(entity, properties.getCollection().getIdField()))
                .content(getStringValue(entity, properties.getCollection().getContentField()))
                .vector(vector)
                .source(getStringValue(entity, "source"))
                .chunkIndex(getIntValue(entity, "chunk_index"))
                .title(getStringValue(entity, "title"))
                .metadata(parseMetadataToMap(getStringValue(entity, properties.getCollection().getMetadataField())))
                .createTime(Instant.now())
                .build();
    }

    /**
     * 解析元数据为列表
     */
    private List<SearchResult.MetadataEntry> parseMetadata(String metadataStr) {
        if (!StringUtils.hasText(metadataStr)) {
            return Collections.emptyList();
        }
        try {
            Map<String, String> map = parseMetadataToMap(metadataStr);
            return map.entrySet().stream()
                    .map(e -> SearchResult.MetadataEntry.builder()
                            .key(e.getKey())
                            .value(e.getValue())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 解析元数据字符串为 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseMetadataToMap(String metadataStr) {
        if (!StringUtils.hasText(metadataStr) || "{}".equals(metadataStr)) {
            return new HashMap<>();
        }
        try {
            return gson.fromJson(metadataStr, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * 获取字符串值
     */
    private String getStringValue(Map<String, Object> entity, String fieldName) {
        Object value = entity.get(fieldName);
        return value != null ? value.toString() : "";
    }

    /**
     * 获取整数值
     */
    private Integer getIntValue(Map<String, Object> entity, String fieldName) {
        Object value = entity.get(fieldName);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
}
