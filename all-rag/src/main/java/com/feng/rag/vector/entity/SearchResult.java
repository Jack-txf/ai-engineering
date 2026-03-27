package com.feng.rag.vector.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 向量搜索结果
 *
 * @author txf
 * @since 2026/3/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    /**
     * 文档 ID
     */
    private String id;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 相似度得分（距离）
     */
    private double score;

    /**
     * 文档来源
     */
    private String source;

    /**
     * 分块索引
     */
    private Integer chunkIndex;

    /**
     * 元数据
     */
    private List<MetadataEntry> metadata;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 元数据条目
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataEntry {
        private String key;
        private String value;
    }
}
