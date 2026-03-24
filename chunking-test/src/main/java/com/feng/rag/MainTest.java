package com.feng.rag;

import java.util.List;

/**
 * @Description:
 * @Author: txf
 * @Date: 2026/3/24
 */
public class MainTest {

    public static void main(String[] args) {
        // 1. 固定切分
        FixedSizeChunker fixedSizeChunker = new FixedSizeChunker(150, 10);
        List<Chunk> chunks = fixedSizeChunker.split(text, "test");
        for (Chunk chunk : chunks) {
            System.out.println(chunk);
            System.out.println("========");
        }

        // 2. 结构化边界切分
        // SemanticBoundaryChunker semanticBoundaryChunker = new SemanticBoundaryChunker(600);
        // List<Chunk> chunks = semanticBoundaryChunker.split(text, "test");
        // for (Chunk chunk : chunks) {
        //     System.out.println(chunk);
        //     System.out.println("========");
        // }

        // 3. 层级切分
        // HierarchicalChunker hierarchicalChunker = new HierarchicalChunker();
        // List<Chunk> chunks2 = hierarchicalChunker.split(text, "test", "深度学习推荐系统中的点击率（CTR）预估模型综述");
        // for (Chunk chunk : chunks2) {
        //     System.out.println(chunk);
        //     System.out.println("========");
        // }

    }

    //==来一段文本
    public static String text = """
            深度学习推荐系统中的点击率（CTR）预估模型综述
            
            1. 概述
            在电子商务和内容分发平台中，点击率（CTR）预估是推荐系统的核心任务之一。其目标是预测用户在特定上下文中点击特定物品的概率。准确的 CTR 预估能够显著提升用户的点击量和平台的转化率。
            
            2. 核心特征工程
            CTR 模型通常处理大规模的稀疏特征。常见的特征类别包括：
            * 用户特征：用户 ID、性别、年龄、历史行为序列（如最近点击的 10 个商品）。
            * 物品特征：商品 ID、类目名称、品牌、价格、历史点击率。
            * 上下文特征：当前时间、地理位置、设备类型、网络状态。
            
            3. 模型架构：以 DeepFM 为例
            DeepFM 结合了因子分解机（FM）和深度神经网络（DNN）的优点。
            - FM 部分：负责建模低阶特征组合（二阶交互），能够有效处理稀疏特征下的组合问题。
            - DNN 部分：负责建模高阶非线性特征交互。
            这两部分共享输入向量空间，通过 Embedding 层将高维稀疏特征转换为低维稠密向量。
            
            4. 实验评估指标
            在评估 CTR 模型性能时，工业界通常采用以下指标：
            1. AUC (Area Under Curve)：反映模型对正负样本排序能力的稳定性，不敏感于样本分布。
            2. LogLoss (对数损失)：直接衡量预估概率与真实标签之间的距离，数值越小越好。
            3. 自定义业务指标：如千次展示收益（RPM）或转化率（CVR）。
            
            5. 部署注意事项
            在线推理时，模型需要处理每秒数万次的请求。为了保证低延迟，通常会采用特征预缓存、Embedding 向量化索引以及模型量化等技术。此外，在冷启动阶段，系统需要结合多任务学习（Multi-task Learning）来缓解数据稀疏问题。
            """;
}
