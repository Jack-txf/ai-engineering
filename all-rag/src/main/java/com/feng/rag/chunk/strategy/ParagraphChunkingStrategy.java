package com.feng.rag.chunk.strategy;

import com.feng.rag.chunk.model.Chunk;
import com.feng.rag.chunk.model.ChunkingOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 段落分块策略 —— 以段落为单位，智能合并小段落
 *
 * <p>特点：
 * <ul>
 *   <li>保持段落完整性，不切断句子</li>
 *   <li>相邻小段落智能合并</li>
 *   <li>适合文章、报告等结构化文本</li>
 * </ul>
 *
 * <p>适用场景：
 * <ul>
 *   <li>新闻报道、博客文章</li>
 *   <li>学术论文、技术文档</li>
 *   <li>法律文件、合同文本</li>
 * </ul>
 */
@Slf4j
@Component
public class ParagraphChunkingStrategy extends AbstractChunkingStrategy {

    public static final String STRATEGY_NAME = "paragraph";

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public String getStrategyDescription() {
        return "段落分块策略：以段落为单位进行分块，保持段落边界完整。" +
               "相邻小段落会智能合并，大段落会按句子边界拆分。" +
               "适合文章、报告等结构化文本。";
    }

    @Override
    public ChunkingOptions getDefaultOptions() {
        return ChunkingOptions.builder()
            .targetChunkSize(500)
            .minChunkSize(100)
            .maxChunkSize(1000)
            .overlapSize(0)
            .respectParagraphBoundaries(true)
            .respectSentenceBoundaries(true)
            .build();
    }

    @Override
    protected List<Chunk> doChunk(String content, String docId, String docName, ChunkingOptions options) {
        List<Chunk> chunks = new ArrayList<>();

        // 按段落分割
        String[] paragraphs = content.split("\n\s*\n|\r\n\s*\r\n");

        int position = 0;
        int chunkIndex = 0;
        StringBuilder currentChunk = new StringBuilder();
        int currentChunkStart = 0;

        for (String paragraph : paragraphs) {
            String trimmedParagraph = paragraph.trim();
            if (trimmedParagraph.isEmpty()) {
                position += paragraph.length() + 2; // 跳过换行
                continue;
            }

            int paraLength = trimmedParagraph.length();

            // 如果当前段落本身就超过最大大小，需要拆分
            if (paraLength > options.getMaxChunkSize()) {
                // 先保存当前累积的内容
                if (currentChunk.length() > 0) {
                    saveChunk(chunks, currentChunk.toString(), docId, docName,
                        currentChunkStart, position, chunkIndex++, options);
                    currentChunk = new StringBuilder();
                }

                // 拆分这个大段落
                List<String> subParagraphs = splitLargeParagraph(trimmedParagraph, options);
                int subOffset = position;
                for (String sub : subParagraphs) {
                    saveChunk(chunks, sub, docId, docName,
                        subOffset, subOffset + sub.length(), chunkIndex++, options);
                    subOffset += sub.length();
                }
            }
            // 如果加入当前段落后会超过目标大小，先保存当前分块
            else if (currentChunk.length() + paraLength > options.getTargetChunkSize()
                    && currentChunk.length() >= options.getMinChunkSize()) {
                saveChunk(chunks, currentChunk.toString(), docId, docName,
                    currentChunkStart, position, chunkIndex++, options);

                currentChunk = new StringBuilder(trimmedParagraph);
                currentChunkStart = position;
            }
            // 否则累积到当前分块
            else {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                } else {
                    currentChunkStart = position;
                }
                currentChunk.append(trimmedParagraph);
            }

            position += paragraph.length() + 2;
        }

        // 保存最后一个分块
        if (currentChunk.length() > 0) {
            saveChunk(chunks, currentChunk.toString(), docId, docName,
                currentChunkStart, position, chunkIndex, options);
        }

        log.debug("[{}] 生成了 {} 个段落分块", STRATEGY_NAME, chunks.size());
        return chunks;
    }

    /**
     * 拆分过大的段落
     */
    private List<String> splitLargeParagraph(String paragraph, ChunkingOptions options) {
        List<String> parts = new ArrayList<>();

        // 先尝试按句子分割
        String[] sentences = paragraph.split("(?<=[。！？.!?])\\s*");

        StringBuilder currentPart = new StringBuilder();
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) continue;

            if (currentPart.length() + trimmed.length() > options.getTargetChunkSize()
                    && currentPart.length() >= options.getMinChunkSize()) {
                parts.add(currentPart.toString().trim());
                currentPart = new StringBuilder(trimmed);
            } else {
                if (currentPart.length() > 0) {
                    currentPart.append(" ");
                }
                currentPart.append(trimmed);
            }
        }

        if (currentPart.length() > 0) {
            parts.add(currentPart.toString().trim());
        }

        // 如果按句子分割后还是有部分太大，强制按字符切分
        List<String> finalParts = new ArrayList<>();
        for (String part : parts) {
            if (part.length() > options.getMaxChunkSize()) {
                for (int i = 0; i < part.length(); i += options.getTargetChunkSize()) {
                    int end = Math.min(i + options.getTargetChunkSize(), part.length());
                    finalParts.add(part.substring(i, end));
                }
            } else {
                finalParts.add(part);
            }
        }

        return finalParts;
    }

    private void saveChunk(List<Chunk> chunks, String content, String docId, String docName,
                          int startOffset, int endOffset, int index, ChunkingOptions options) {
        if (content.trim().isEmpty()) return;

        Chunk chunk = Chunk.builder()
            .chunkIndex(index)
            .content(content.trim())
            .contentLength(content.trim().length())
            .documentId(docId)
            .documentName(docName)
            .startOffset(startOffset)
            .endOffset(endOffset)
            .chunkType(detectChunkType(content))
            .startsWithCompleteSentence(isCompleteSentenceStart(content))
            .endsWithCompleteSentence(isCompleteSentenceEnd(content))
            .crossesParagraphBoundary(content.contains("\n\n"))
            .chunkingStrategy(STRATEGY_NAME)
            .build();

        chunks.add(chunk);
    }
}
