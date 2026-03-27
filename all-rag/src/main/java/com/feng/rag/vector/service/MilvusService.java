package com.feng.rag.vector.service;

import com.feng.rag.vector.entity.VectorDocument;
import com.feng.rag.vector.entity.SearchResult;

import java.util.List;
import java.util.Map;

/**
 * Milvus 向量数据库服务接口
 *
 * @author txf
 * @since 2026/3/26
 */
public interface MilvusService {

    /**
     * 创建集合（如果不存在）
     *
     * @param collectionName 集合名称
     * @param dimension      向量维度
     * @return 是否创建成功
     */
    boolean createCollection(String collectionName, int dimension);

    /**
     * 使用默认配置创建集合
     *
     * @return 是否创建成功
     */
    boolean createCollection();

    /**
     * 删除集合
     *
     * @param collectionName 集合名称
     * @return 是否删除成功
     */
    boolean dropCollection(String collectionName);

    /**
     * 删除默认集合
     *
     * @return 是否删除成功
     */
    boolean dropCollection();

    /**
     * 检查集合是否存在
     *
     * @param collectionName 集合名称
     * @return 是否存在
     */
    boolean hasCollection(String collectionName);

    /**
     * 获取所有集合名称
     *
     * @return 集合名称列表
     */
    List<String> listCollections();

    /**
     * 插入单个文档
     *
     * @param document 向量文档
     * @return 文档 ID
     */
    String insert(VectorDocument document);

    /**
     * 插入单个文档到指定集合
     *
     * @param collectionName 集合名称
     * @param document       向量文档
     * @return 文档 ID
     */
    String insert(String collectionName, VectorDocument document);

    /**
     * 批量插入文档
     *
     * @param documents 文档列表
     * @return 插入的文档 ID 列表
     */
    List<String> insertBatch(List<VectorDocument> documents);

    /**
     * 批量插入文档到指定集合
     *
     * @param collectionName 集合名称
     * @param documents      文档列表
     * @return 插入的文档 ID 列表
     */
    List<String> insertBatch(String collectionName, List<VectorDocument> documents);

    /**
     * 根据 ID 删除文档
     *
     * @param ids 文档 ID 列表
     * @return 删除数量
     */
    long deleteByIds(List<String> ids);

    /**
     * 根据 ID 删除指定集合中的文档
     *
     * @param collectionName 集合名称
     * @param ids            文档 ID 列表
     * @return 删除数量
     */
    long deleteByIds(String collectionName, List<String> ids);

    /**
     * 向量相似度搜索
     *
     * @param vector 查询向量
     * @param topK   返回结果数量
     * @return 搜索结果列表
     */
    List<SearchResult> search(List<Float> vector, int topK);

    /**
     * 向量相似度搜索（指定集合）
     *
     * @param collectionName 集合名称
     * @param vector         查询向量
     * @param topK           返回结果数量
     * @return 搜索结果列表
     */
    List<SearchResult> search(String collectionName, List<Float> vector, int topK);

    /**
     * 带过滤条件的向量搜索
     *
     * @param collectionName 集合名称
     * @param vector         查询向量
     * @param topK           返回结果数量
     * @param filterExpr     过滤表达式（如 "source == 'doc1.pdf'"）
     * @return 搜索结果列表
     */
    List<SearchResult> search(String collectionName, List<Float> vector, int topK, String filterExpr);

    /**
     * 根据 ID 查询文档
     *
     * @param ids 文档 ID 列表
     * @return 文档列表
     */
    List<VectorDocument> getByIds(List<String> ids);

    /**
     * 根据 ID 查询指定集合中的文档
     *
     * @param collectionName 集合名称
     * @param ids            文档 ID 列表
     * @return 文档列表
     */
    List<VectorDocument> getByIds(String collectionName, List<String> ids);

    /**
     * 获取集合中的文档数量
     *
     * @param collectionName 集合名称
     * @return 文档数量
     */
    long count(String collectionName);

    /**
     * 获取默认集合中的文档数量
     *
     * @return 文档数量
     */
    long count();

    /**
     * 加载集合到内存
     *
     * @param collectionName 集合名称
     */
    void loadCollection(String collectionName);

    /**
     * 释放集合内存
     *
     * @param collectionName 集合名称
     */
    void releaseCollection(String collectionName);
}
