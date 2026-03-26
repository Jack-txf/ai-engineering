package com.feng.rag.chunk.strategy;

import com.feng.rag.chunk.model.Chunk;
import com.feng.rag.chunk.model.ChunkingOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 滑动窗口分块策略 —— 固定窗口大小，按步长滑动
 *
 * <p>特点：
 * <ul>
 *   <li>相邻分块有大量重叠，保证上下文连续性</li>
 *   <li>召回率高，不会遗漏跨分块的信息</li>
 *   <li>分块数量多，存储和计算成本高</li>
 * </ul>
 *
 * <p>适用场景：
 * <ul>
 *   <li>需要极高召回率的场景（如法律条文检索）</li>
 *   <li>短文本、关键信息密集的内容</li>
 *   <li>对响应速度要求不高的场景</li>
 * </ul>
 */
@Slf4j
@Component
public class SlidingWindowChunkingStrategy extends AbstractChunkingStrategy {

    public static final String STRATEGY_NAME = "sliding_window";

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public String getStrategyDescription() {
        return "滑动窗口分块策略：固定窗口大小，按步长滑动产生重叠分块。" +
               "召回率高，适合对精确度要求极高的场景如法律条文检索。";
    }

    @Override
    public ChunkingOptions getDefaultOptions() {
        return ChunkingOptions.builder()
            .targetChunkSize(400)
            .minChunkSize(100)
            .maxChunkSize(500)
            .overlapSize(200)  // 默认 50% 重叠
            .respectSentenceBoundaries(true)
            .build();
    }

    @Override
    protected List<Chunk> doChunk(String content, String docId, String docName, ChunkingOptions options) {
        List<Chunk> chunks = new ArrayList<>();
        int contentLength = content.length();

        int windowSize = options.getTargetChunkSize();
        int stepSize = windowSize - options.getOverlapSize();

        // 确保步长至少为 1
        if (stepSize <= 0) {
            stepSize = windowSize / 2;
        }

        int position = 0;
        int chunkIndex = 0;

        while (position < contentLength) {
            int endPos = Math.min(position + windowSize, contentLength);

            // 尝试在边界处切分
            if (endPos < contentLength) {
                endPos = findBestSplitPoint(content, endPos, true, options);
            }

            String chunkContent = content.substring(position, endPos).trim();

            if (!chunkContent.isEmpty() && chunkContent.length() >= options.getMinChunkSize()) {
                Chunk chunk = Chunk.builder()
                    .chunkIndex(chunkIndex++)
                    .content(chunkContent)
                    .contentLength(chunkContent.length())
                    .documentId(docId)
                    .documentName(docName)
                    .startOffset(position)
                    .endOffset(endPos)
                    .chunkType(detectChunkType(chunkContent))
                    .startsWithCompleteSentence(isCompleteSentenceStart(chunkContent))
                    .endsWithCompleteSentence(isCompleteSentenceEnd(chunkContent))
                    .chunkingStrategy(STRATEGY_NAME)
                    .build();

                chunks.add(chunk);
            }

            position += stepSize;

            // 防止最后一个分块太小，提前结束
            if (contentLength - position < options.getMinChunkSize() && !chunks.isEmpty()) {
                // 将剩余内容合并到最后一个分块
                Chunk lastChunk = chunks.get(chunks.size() - 1);
                if (endPos < contentLength) {
                    String extended = content.substring(lastChunk.getStartOffset(), contentLength);
                    lastChunk.setContent(extended);
                    lastChunk.setContentLength(extended.length());
                    lastChunk.setEndOffset(contentLength);
                }
                break;
            }
        }

        log.debug("[{}] 生成了 {} 个滑动窗口分块", STRATEGY_NAME, chunks.size());
        return chunks;
    }
}
