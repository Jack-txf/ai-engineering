package com.feng.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 固定长度切分器（含滑动窗口重叠）
 */
public class FixedSizeChunker {

    private final int chunkSize;    // 每个 chunk 的 token 数上限（推荐 512）
    private final int overlapSize;  // 重叠 token 数（推荐 chunkSize 的 15%~20%）

    public FixedSizeChunker(int chunkSize, int overlapSize) {
        if (overlapSize >= chunkSize) {
            throw new IllegalArgumentException("重叠大小必须小于 chunk 大小");
        }
        this.chunkSize = chunkSize;
        this.overlapSize = overlapSize;
    }

    /**
     * 核心切分逻辑
     * 注意：这里用"字符数"近似"token 数"（中文 1 字 ≈ 1 token，英文 1 词 ≈ 1~2 token）。
     * 生产环境应使用对应 Embedding 模型的 tokenizer 精确计算 token 数。
     */
    public List<Chunk> split(String text, String sourceId) {
        List<Chunk> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) return chunks;

        int start = 0;
        int docLength = text.length();
        int index = 0;

        while (start < docLength) {
            // 计算本次 chunk 的结束位置
            int end = Math.min(start + chunkSize, docLength);

            // 关键优化：在 end 处向前找最近的句子边界
            // 避免在"句子中间"截断（如 "这项政策将于2024年" | "1月1日起实施"）
            if (end < docLength) {
                end = findSentenceBoundary(text, end);
            }

            String chunkContent = text.substring(start, end).strip();

            if (!chunkContent.isBlank()) {
                chunks.add(Chunk.builder()
                    .id(sourceId + "_chunk_" + index)
                    .content(chunkContent)
                    .sourceId(sourceId)
                    .chunkIndex(index)
                    .startOffset(start)
                    .endOffset(end)
                    .metadata(Map.of(
                        "strategy", "fixed_size",
                        "chunk_size", String.valueOf(chunkSize),
                        "overlap", String.valueOf(overlapSize)
                    ))
                    .build());
                index++;
            }

            // 下一个 chunk 的起点 = 当前结束位置 - 重叠大小
            // 重叠确保边界处的内容在两个相邻 chunk 里都出现
            start = end - overlapSize;

            // 防止死循环：若 start 没有前进，强制推进
            if (start <= 0 || end >= docLength) break;
        }

        return chunks;
    }

    /**
     * 在指定位置向前搜索句子边界
     * 优先级：段落边界（\n\n）> 句子边界（。.！？）> 子句边界（，,；）> 原始位置
     */
    private int findSentenceBoundary(String text, int position) {
        // 向前搜索范围：最多回退 chunkSize 的 15%
        int searchBack = Math.max(position - chunkSize / 6, position - 100);
        searchBack = Math.max(searchBack, 0);

        // 1. 优先找段落边界
        int paraIdx = text.lastIndexOf("\n\n", position);
        if (paraIdx > searchBack) return paraIdx + 2;
        // 2. 找句子结束符（中英文）
        String sentenceEnds = "。.！？!?\n";
        for (int i = position; i > searchBack; i--) {
            if (sentenceEnds.indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        // 3. 找子句边界
        String clauseEnds = "，,；;";
        for (int i = position; i > searchBack; i--) {
            if (clauseEnds.indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        // 4. 找到原始 position（无合适边界）
        return position;
    }
}