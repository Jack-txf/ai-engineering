package com.feng.rag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 语义边界切分器
 *
 * 核心思路：
 * 1. 先按自然段落（\n\n）切分为段落列表
 * 2. 太长的段落再按句子拆分
 * 3. 太短的段落和相邻段落合并，直到接近目标大小
 *
 * 这样每个 chunk 都以完整的句子开始和结束，
 * 不会出现"半句话"的情况。
 */
public class SemanticBoundaryChunker {

    private final int targetChunkSize;  // 目标 chunk 大小（字符数，推荐 500~800）
    private final int maxChunkSize;     // 最大 chunk 大小（超出则强制切分）
    private final int minChunkSize;     // 最小 chunk 大小（低于则与相邻合并）

    // 句子分割正则：中英文句子结束符
    private static final Pattern SENTENCE_SPLITTER = Pattern.compile(
        "(?<=[。！？.!?])|(?<=\\n)"
    );

    public SemanticBoundaryChunker(int targetSize) {
        this.targetChunkSize = targetSize;
        this.maxChunkSize = (int)(targetSize * 1.5);
        this.minChunkSize = (int)(targetSize * 0.3);
    }

    public List<Chunk> split(String text, String sourceId) {
        // Step 1: 按空行切分为段落
        List<String> paragraphs = splitToParagraphs(text);

        // Step 2: 超长段落拆分为句子
        List<String> sentences = expandLongParagraphs(paragraphs);

        // Step 3: 合并短段落，直到接近 targetChunkSize
        List<String> mergedChunks = mergeSmallUnits(sentences);

        // Step 4: 构建 Chunk 对象
        return buildChunks(mergedChunks, sourceId);
    }

    /** Step 1：按双换行分割段落，过滤空段落 */
    private List<String> splitToParagraphs(String text) {
        return Arrays.stream(text.split("\n\n+"))
            .map(String::strip)
            .filter(p -> !p.isBlank())
            .toList();
    }

    /** Step 2：对超出 maxChunkSize 的段落，再按句子拆分 */
    private List<String> expandLongParagraphs(List<String> paragraphs) {
        List<String> result = new ArrayList<>();
        for (String para : paragraphs) {
            if (para.length() <= maxChunkSize) {
                result.add(para);
            } else {
                // 按句子边界拆分这个超长段落
                String[] sentences = SENTENCE_SPLITTER.split(para);
                result.addAll(Arrays.asList(sentences));
            }
        }
        return result.stream()
            .map(String::strip)
            .filter(s -> !s.isBlank())
            .toList();
    }

    /**
     * Step 3：贪心合并——将相邻的小单元合并，
     * 直到合并后的大小接近 targetChunkSize
     *
     * 算法：维护一个"当前 chunk 的 buffer"，
     * 不断追加下一个单元，直到追加后会超出 maxChunkSize 为止。
     */
    private List<String> mergeSmallUnits(List<String> units) {
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String unit : units) {
            // 追加后是否会超出最大限制
            boolean wouldExceed = buffer.length() + unit.length() + 1 > maxChunkSize;

            if (wouldExceed && buffer.length() >= minChunkSize) {
                // buffer 已经够大，先输出，再开新的 buffer
                result.add(buffer.toString().strip());
                buffer = new StringBuilder(unit);
            } else {
                // 继续追加到 buffer
                if (buffer.length() > 0) buffer.append("\n");
                buffer.append(unit);
            }
        }

        // 输出最后剩余的内容
        if (buffer.length() >= minChunkSize) {
            result.add(buffer.toString().strip());
        } else if (!result.isEmpty() && !buffer.isEmpty()) {
            // 最后一个太小，合并到上一个 chunk
            result.set(result.size() - 1,
                result.get(result.size() - 1) + "\n" + buffer.toString().strip());
        }

        return result;
    }

    private List<Chunk> buildChunks(List<String> contents, String sourceId) {
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            chunks.add(Chunk.builder()
                .id(sourceId + "_chunk_" + i)
                .content(contents.get(i))
                .sourceId(sourceId)
                .chunkIndex(i)
                .metadata(Map.of("strategy", "semantic_boundary"))
                .build());
        }
        return chunks;
    }
}