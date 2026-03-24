package com.feng.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 层级标题切分器（父子 Chunk 结构）
 *
 *  文档结构示例：
 *  ┌─ H1: 第三章 产品规格                    ← 父 chunk（章级）
 *  │   ├─ H2: 3.1 硬件配置                   ← 子 chunk（节级）
 *  │   │   ├─ 段落：CPU 采用...              ← 孙 chunk（段落级）
 *  │   │   └─ 段落：内存配置...
 *  │   └─ H2: 3.2 软件环境
 *  │       └─ 段落：操作系统要求...
 *
 *  检索时：用段落级 chunk 做向量检索（精准匹配）
 *  生成时：扩展到父节点（章节级）提供给 LLM（完整上下文）
 */
public class HierarchicalChunker {

    // 标题识别正则（Markdown 格式）
    private static final Pattern HEADING_PATTERN = Pattern.compile(
        "^(#{1,4})\\s+(.+)$", Pattern.MULTILINE
    );

    // 中文文档标题识别（第一章、1.1、一、）
    private static final Pattern CN_HEADING_PATTERN = Pattern.compile(
        "^(?:第[一二三四五六七八九十百\\d]+[章节部分]|\\d+(?:\\.\\d+){0,2}[、.]|[一二三四五六七八九十]+[、.])\\s*.+$",
        Pattern.MULTILINE
    );

    /**
     * 切分文档为层级 Chunk 树
     *
     * @return 扁平化的 chunk 列表，通过 parentId 维护层级关系
     */
    public List<Chunk> split(String text, String sourceId, String documentTitle) {
        List<Chunk> chunks = new ArrayList<>();

        // 解析文档结构：提取所有标题及其位置
        List<HeadingNode> headings = extractHeadings(text);

        if (headings.isEmpty()) {
            // 没有找到标题结构，降级为语义边界切分
            return new SemanticBoundaryChunker(600).split(text, sourceId);
        }

        // 创建根节点（文档级 chunk）
        Chunk rootChunk = Chunk.builder()
            .id(sourceId + "_root")
            .content(documentTitle != null ? documentTitle : "文档根节点")
            .sourceId(sourceId)
            .chunkIndex(0)
            .level(0)
            .parentId(null)
            .isLeaf(false)
            .metadata(Map.of("strategy", "hierarchical", "node_type", "root"))
            .build();
        chunks.add(rootChunk);

        // 按标题切分，构建父子关系
        for (int i = 0; i < headings.size(); i++) {
            HeadingNode current = headings.get(i);

            // 当前标题到下一个标题之间的文本就是本节内容
            int contentEnd = (i + 1 < headings.size())
                ? headings.get(i + 1).position
                : text.length();
            String sectionContent = text.substring(current.position, contentEnd).strip();

            // 找父节点（比自己级别小1的最近上级标题）
            String parentId = findParentId(headings, i, sourceId);

            // 创建章节级 chunk（父 chunk）
            Chunk sectionChunk = Chunk.builder()
                .id(sourceId + "_h" + current.level + "_" + i)
                .content(sectionContent)
                .sourceId(sourceId)
                .chunkIndex(chunks.size())
                .level(current.level)
                .headingText(current.text)
                .parentId(parentId)
                .isLeaf(false)  // 章节 chunk 是父节点
                .metadata(Map.of(
                    "strategy", "hierarchical",
                    "node_type", "section",
                    "heading_level", String.valueOf(current.level),
                    "heading_text", current.text
                ))
                .build();
            chunks.add(sectionChunk);

            // 将章节内容进一步切分为段落级子 chunk
            List<Chunk> leafChunks = splitSectionToLeaves(
                sectionContent, sourceId, sectionChunk.getId(), chunks.size()
            );
            chunks.addAll(leafChunks);
        }

        return chunks;
    }

    /**
     * 将一个章节的内容切分为段落级的叶子 chunk
     *
     * 叶子 chunk 是实际用于向量检索的单元，大小控制在 200~600 字之间
     */
    private List<Chunk> splitSectionToLeaves(
            String sectionContent, String sourceId, String parentId, int startIndex) {

        List<Chunk> leaves = new ArrayList<>();

        // 按段落切分（双换行为段落边界）
        String[] paragraphs = sectionContent.split("\n\n+");

        StringBuilder buffer = new StringBuilder();
        int leafIndex = 0;

        for (String para : paragraphs) {
            para = para.strip();
            if (para.isBlank()) continue;

            // 跳过标题行本身（已在父 chunk 的 headingText 中记录）
            if (isHeadingLine(para)) continue;

            if (buffer.length() + para.length() > 600 && buffer.length() > 100) {
                // buffer 已经够大，输出一个叶子 chunk
                leaves.add(buildLeafChunk(
                    buffer.toString().strip(), sourceId, parentId,
                    startIndex + leafIndex
                ));
                leafIndex++;
                buffer = new StringBuilder(para);
            } else {
                if (!buffer.isEmpty()) buffer.append("\n\n");
                buffer.append(para);
            }
        }

        // 输出剩余内容
        if (buffer.length() > 50) {
            leaves.add(buildLeafChunk(
                buffer.toString().strip(), sourceId, parentId,
                startIndex + leafIndex
            ));
        }

        return leaves;
    }

    private Chunk buildLeafChunk(String content, String sourceId,
                                  String parentId, int index) {
        return Chunk.builder()
            .id(sourceId + "_leaf_" + index)
            .content(content)
            .sourceId(sourceId)
            .chunkIndex(index)
            .parentId(parentId)
            .isLeaf(true)  // 叶子 chunk：用于向量检索
            .metadata(Map.of("strategy", "hierarchical", "node_type", "leaf"))
            .build();
    }

    private List<HeadingNode> extractHeadings(String text) {
        List<HeadingNode> headings = new ArrayList<>();
        Matcher m = HEADING_PATTERN.matcher(text);
        while (m.find()) {
            headings.add(new HeadingNode(
                m.group(1).length(),  // 标题级别（# 的数量）
                m.group(2).strip(),   // 标题文字
                m.start()             // 在文档中的位置
            ));
        }
        // 也尝试识别中文标题
        if (headings.isEmpty()) {
            Matcher cnM = CN_HEADING_PATTERN.matcher(text);
            while (cnM.find()) {
                headings.add(new HeadingNode(1, cnM.group().strip(), cnM.start()));
            }
        }
        return headings;
    }

    private String findParentId(List<HeadingNode> headings, int currentIdx, String sourceId) {
        HeadingNode current = headings.get(currentIdx);
        for (int i = currentIdx - 1; i >= 0; i--) {
            if (headings.get(i).level < current.level) {
                return sourceId + "_h" + headings.get(i).level + "_" + i;
            }
        }
        return sourceId + "_root";
    }

    private boolean isHeadingLine(String line) {
        return line.startsWith("#") || HEADING_PATTERN.matcher(line).matches();
    }

    record HeadingNode(int level, String text, int position) {}
}