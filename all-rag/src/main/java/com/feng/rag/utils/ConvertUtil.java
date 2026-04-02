package com.feng.rag.utils;

import com.feng.rag.vector.entity.SearchResult;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @Description: Milvus SearchResp 转换工具
 * @Author: txf
 * @Date: 2026/4/2
 */
@Slf4j
public class ConvertUtil {

    /**
     * 将 Milvus SearchResp 转换为 SearchResult 实体列表
     *
     * @param resp Milvus 检索响应
     * @return List<SearchResult> 扁平化的搜索结果列表
     */
    public static List<SearchResult> convertToSearchResults(SearchResp resp) {
        if (resp == null || resp.getSearchResults() == null || resp.getSearchResults().isEmpty()) {
            return Collections.emptyList();
        }
        List<SearchResult> results = new ArrayList<>();
        // SearchResp.getSearchResults() 返回 List<List<SearchResp.SearchResult>>
        // 外层是多个查询的结果（批量查询），内层是单个查询的多个结果
        for (List<SearchResp.SearchResult> queryResults : resp.getSearchResults()) {
            for (SearchResp.SearchResult result : queryResults) {
                SearchResult searchResult = convertSingleResult(result);
                if (searchResult != null) {
                    results.add(searchResult);
                }
            }
        }
        return results;
    }

    /**
     * 转换单个 Milvus SearchResult 为自定义 SearchResult
     */
    private static SearchResult convertSingleResult(SearchResp.SearchResult result) {
        if (result == null || result.getEntity() == null) {
            return null;
        }
        Map<String, Object> entity = result.getEntity();
        return SearchResult.builder()
                .docId(getStringValue(entity, "doc_id"))
                .content(getStringValue(entity, "content"))
                .score(result.getScore() != null ? result.getScore() : null)
                .metadata(getMetadata(entity, "metadata"))
                .chunkIndex(getIntegerValue(entity, "chunk_index"))
                .orgId(getStringValue(entity, "org_id"))
                .build();
    }

    /**
     * 从 entity 中获取字符串值
     */
    private static String getStringValue(Map<String, Object> entity, String key) {
        Object value = entity.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 从 entity 中获取整数值
     */
    private static Integer getIntegerValue(Map<String, Object> entity, String key) {
        Object value = entity.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取元数据（JSON字段）
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMetadata(Map<String, Object> entity, String key) {
        Object value = entity.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        if (value instanceof com.google.gson.JsonObject jsonObj) {
            Map<String, Object> map = new java.util.HashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : jsonObj.entrySet()) {
                map.put(entry.getKey(), entry.getValue().getAsString());
            }
            return map;
        }
        return null;
    }
}
