package com.feng.rag.chunk.strategy;

import com.feng.rag.chunk.model.Chunk;
import com.feng.rag.chunk.model.ChunkingOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归分块策略 —— 按文档结构层级递归切分，保持语义完整性
 *
 * <p>特点：
 * <ul>
 *   <li>按文档结构层级切分（标题 → 段落 → 句子）</li>
 *   <li>优先保持语义边界完整</li>
 *   <li>支持父子分块关系，便于检索时获取上下文</li>
 *   <li>适合层次分明的结构化文档</li>
 * </ul>
 *
 * <p>适用场景：
 * <ul>
 *   <li>技术文档、API 文档</li>
 *   <li>书籍、论文</li>
 *   <li>结构化报告、手册</li>
 * </ul>
 */
@Slf4j
@Component
public class RecursiveChunkingStrategy extends AbstractChunkingStrategy {

    public static final String STRATEGY_NAME = "recursive";

    // 分隔符优先级（从高到低）
    private static final String[] SEPARATORS = {
        "\n\n\n\n",  // 四级分隔（章节间）
        "\n\n\n",    // 三级分隔（小节间）
        "\n\n",      // 二级分隔（段落间）
        "\n",        // 一级分隔（行内）
        "。",        // 句子边界（中文）
        "？", "！", // 句子边界（中文标点）
        ".", "?", "!", // 句子边界（英文）
        " ",         // 单词边界
        ""           // 字符边界（最终fallback）
    };

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public String getStrategyDescription() {
        return "递归分块策略：按文档结构层级递归切分，优先保持语义边界完整。" +
               "支持父子分块关系，适合层次分明的结构化文档如技术文档、论文等。";
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
            .splitAtHeadings(true)
            .maxHierarchyDepth(3)
            .build();
    }

    @Override
    protected List<Chunk> doChunk(String content, String docId, String docName, ChunkingOptions options) {
        List<Chunk> chunks = new ArrayList<>();

        // 从最高优先级分隔符开始递归分割
        recursiveSplit(content, docId, docName, 0, 0, content.length(),
            0, options, new ArrayList<>(), chunks);

        log.debug("[{}] 生成了 {} 个递归分块", STRATEGY_NAME, chunks.size());
        return chunks;
    }

    /**
     * 递归分割文本
     *
     * @param text 当前要分割的文本
     * @param docId 文档ID
     * @param docName 文档名称
     * @param startOffset 在原文档中的起始位置
     * @param level 当前层级深度
     * @param options 分块选项
     * @param currentPath 当前章节路径
     * @param result 结果收集器
     */
    private void recursiveSplit(String text, String docId, String docName,
                                 int absoluteStart, int level, int parentLength,
                                 int chunkIndex, ChunkingOptions options,
                                 List<String> currentPath, List<Chunk> result) {

        // 如果文本长度在目标范围内，直接作为分块
        if (text.length() <= options.getTargetChunkSize() && text.length() >= options.getMinChunkSize()) {
            createChunk(text, docId, docName, absoluteStart, level, chunkIndex,
                options, currentPath, result, false);
            return;
        }

        // 如果文本很短，尝试合并（在调用方处理）
        if (text.length() < options.getMinChunkSize()) {
            createChunk(text, docId, docName, absoluteStart, level, chunkIndex,
                options, currentPath, result, false);
            return;
        }

        // 如果超过最大大小或需要进一步分割
        if (text.length() > options.getMaxChunkSize() ||
            (text.length() > options.getTargetChunkSize() && level < SEPARATORS.length - 1)) {

            // 查找合适的分隔符
            String separator = findAppropriateSeparator(text, level, options);

            if (separator.isEmpty() || !text.contains(separator)) {
                // 没有合适的分隔符，强制按字符切分
                forceSplit(text, docId, docName, absoluteStart, level, chunkIndex,
                    options, currentPath, result);
                return;
            }

            // 按分隔符分割
            String[] parts = text.split(separator, -1);
            int currentOffset = absoluteStart;

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.isEmpty()) {
                    // 更新偏移量，包含分隔符长度
                    currentOffset += (i < parts.length - 1) ?
                        (parts[i].length() + separator.length()) : parts[i].length();
                    continue;
                }

                // 更新章节路径（如果是标题行）
                List<String> newPath = new ArrayList<>(currentPath);
                if (isHeading(part)) {
                    String heading = extractHeadingText(part);
                    // 根据层级调整路径
                    int headingLevel = getHeadingLevel(part);
                    while (newPath.size() >= headingLevel && !newPath.isEmpty()) {
                        newPath.removeLast();
                    }
                    newPath.add(heading);
                }

                // 递归处理子部分
                recursiveSplit(part, docId, docName, currentOffset, level + 1,
                    text.length(), chunkIndex + result.size(), options, newPath, result);

                // 更新偏移量
                currentOffset += part.length();
                if (i < parts.length - 1) {
                    currentOffset += separator.length();
                }
            }
        } else {
            // 大小合适，直接创建分块
            createChunk(text, docId, docName, absoluteStart, level, chunkIndex,
                options, currentPath, result, false);
        }
    }

    /**
     * 查找合适的分隔符
     */
    private String findAppropriateSeparator(String text, int startLevel, ChunkingOptions options) {
        // 首先尝试根据文本特征选择分隔符
        if (options.isSplitAtHeadings() && text.contains("#")) {
            // Markdown 标题
            return "\n";
        }

        // 按优先级尝试分隔符
        for (int i = startLevel; i < SEPARATORS.length; i++) {
            String sep = SEPARATORS[i];
            if (!sep.isEmpty() && text.contains(sep)) {
                // 检查分割后的大小是否合适
                String[] parts = text.split(sep, -1);
                boolean suitable = true;
                for (String part : parts) {
                    if (part.length() > options.getMaxChunkSize() * 1.5) {
                        suitable = false;
                        break;
                    }
                }
                if (suitable) {
                    return sep;
                }
            }
        }

        return "";
    }

    /**
     * 强制按字符切分（fallback）
     */
    private void forceSplit(String text, String docId, String docName,
                           int absoluteStart, int level, int chunkIndex,
                           ChunkingOptions options, List<String> currentPath, List<Chunk> result) {
        int position = 0;

        while (position < text.length()) {
            int endPos = Math.min(position + options.getTargetChunkSize(), text.length());

            // 尝试找更好的切分点
            if (endPos < text.length()) {
                endPos = findBestSplitPoint(text, endPos, true, options);
            }

            String chunkText = text.substring(position, endPos).trim();
            if (!chunkText.isEmpty()) {
                createChunk(chunkText, docId, docName, absoluteStart + position, level,
                    chunkIndex + result.size(), options, currentPath, result, true);
            }

            position = endPos;
        }
    }

    /**
     * 创建分块
     */
    private void createChunk(String text, String docId, String docName,
                            int absoluteStart, int level, int chunkIndex,
                            ChunkingOptions options, List<String> currentPath,
                            List<Chunk> result, boolean isForcedSplit) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return;

        Chunk chunk = Chunk.builder()
            .chunkIndex(chunkIndex)
            .content(trimmed)
            .contentLength(trimmed.length())
            .documentId(docId)
            .documentName(docName)
            .startOffset(absoluteStart)
            .endOffset(absoluteStart + text.length())
            .hierarchyLevel(level)
            .sectionPath(new ArrayList<>(currentPath))
            .chunkType(detectChunkType(trimmed))
            .startsWithCompleteSentence(isCompleteSentenceStart(trimmed))
            .endsWithCompleteSentence(isCompleteSentenceEnd(trimmed))
            .chunkingStrategy(STRATEGY_NAME)
            .build();

        // 强制切分可能质量较低
        if (isForcedSplit) {
            chunk.setCrossesParagraphBoundary(true);
        }

        // 检查是否是标题分块
        if (isHeading(trimmed)) {
            chunk.setChunkType(Chunk.ChunkType.HEADING);
        }

        result.add(chunk);
    }

    /**
     * 检测是否为标题行
     */
    private boolean isHeading(String text) {
        String firstLine = text.split("\n")[0].trim();
        return firstLine.startsWith("#") ||
               firstLine.matches("^\\d+[\\.\\)\\s]+") ||
               firstLine.matches("^第[一二三四五六七八九十\\d]+[章节篇]");
    }

    /**
     * 获取标题层级
     */
    private int getHeadingLevel(String text) {
        String firstLine = text.split("\n")[0].trim();
        if (firstLine.startsWith("#")) {
            int level = 0;
            for (char c : firstLine.toCharArray()) {
                if (c == '#') level++;
                else break;
            }
            return level;
        }
        return 1;
    }

    /**
     * 提取标题文本
     */
    private String extractHeadingText(String text) {
        String firstLine = text.split("\n")[0].trim();
        // 移除 Markdown 标题标记
        firstLine = firstLine.replaceAll("^#+\\s*", "");
        // 移除数字标记
        firstLine = firstLine.replaceAll("^\\d+[\\.\\)\\s]*", "");
        return firstLine;
    }
}
