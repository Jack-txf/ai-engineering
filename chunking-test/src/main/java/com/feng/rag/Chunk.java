package com.feng.rag;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 统一 Chunk 数据模型
 *
 * isLeaf = true  → 参与向量检索（叶子 chunk，语义精准）
 * isLeaf = false → 不直接检索，通过 parentId 被引用（父 chunk，提供上下文）
 */
@Data
@Builder
public class Chunk {
    /** 唯一标识，格式：{sourceId}_{strategy}_{index} */
    private String id;

    /** chunk 的文本内容 */
    private String content;

    /** 所属文档的来源标识 */
    private String sourceId;

    /** 在文档中的顺序索引 */
    private int chunkIndex;

    /** 字符偏移量（用于溯源定位原文位置） */
    private int startOffset;
    private int endOffset;

    // ─── 层级结构字段（层级切分策略专用）───
    /** 标题级别（1=H1, 2=H2...），非层级切分时为 0 */
    private int level;
    /** 标题文字 */
    private String headingText;
    /** 父 chunk 的 id */
    private String parentId;
    /** 是否是叶子节点（叶子才参与向量检索） */
    @Builder.Default
    private boolean isLeaf = true;

    /** 扩展元数据（切分策略、文档类型、权限标签等） */
    private Map<String, String> metadata;
}