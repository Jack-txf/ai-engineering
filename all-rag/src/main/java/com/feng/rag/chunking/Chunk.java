package com.feng.rag.chunking;

import java.util.Map;

/**
 * 表示一个分块结果
 * @param content 分块内容
 * @param index 块在原文档中的顺序
 * @param metadata 元数据（如篇幅、字符数、来源等）
 */
public record Chunk(
    String content,
    int index,
    Map<String, Object> metadata
) {}