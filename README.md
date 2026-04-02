# All-RAG

一款基于 Java + Spring Boot 的完整 RAG (Retrieval-Augmented Generation) 系统，实现从文档解析到智能问答的全流程。

> 目录说明：`chunking-test` 是独立的文本分块测试项目，用于快速验证分块算法效果。

## 项目概述

All-RAG 是一个企业级 RAG 系统，实现了完整的 5 阶段管道：

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Document  │───▶│   Chunking  │───▶│  Embedding  │───▶│    Milvus   │───▶│  Retrieval  │
│   Parsing   │    │  (5 types)  │    │  (Models)   │    │Vector Store │    │+ Generation │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
   (Tika)                               (Factory)             (SDK)              (API)
```

### 核心功能

| 模块 | 功能 | 技术实现 |
|------|------|----------|
| 文档解析 | 支持 10+ 种格式 | Apache Tika 3.2.3 |
| 智能分块 | 5 种分块策略 | 自研策略引擎 |
| 向量化 | 多厂商 Embedding | SiliconFlow / 千问 |
| 向量存储 | 高密度向量检索 | Milvus 2.6.6 |
| 混合检索 | 向量 + 稀疏 + 融合 | Milvus Hybrid Search |
| 重排序 | 语义重排序 | Rerank API |
| 智能问答 | 流式/同步生成 | LLM Chat API |

## 技术栈

- **JDK 21**：Virtual Threads、Records、Pattern Matching
- **Spring Boot 3.5.x**：Web、Validation、Actuator
- **Apache Tika 3.2.x**：全格式文档解析
- **Milvus SDK 2.6.6**：向量数据库
- **OkHttp 5.3.x**：HTTP 客户端
- **Micrometer + Prometheus**：可观测性

## 项目结构

```
all-rag/
├── pom.xml                                    # Maven 配置
└── src/main/java/com/feng/rag/
    ├── RagApplication.java                    # 启动类
    ├── controller/                            # REST API 层
    │   ├── DataSourceController.java          # 数据源接口
    │   ├── ChunkController.java               # 分块接口
    │   ├── VectorController.java              # 向量存储接口
    │   ├── RetrievalController.java           # 检索与问答接口
    │   └── R.java                             # 统一响应封装
    ├── datasource/                            # 文档解析模块
    │   ├── parse/TikaDocumentParser.java      # Tika 解析器
    │   ├── service/DataSourceIngestionService.java
    │   └── common/DocumentParseResult.java    # 解析结果
    ├── chunk/                                 # 文本分块模块
    │   ├── strategy/                          # 5种分块策略
    │   │   ├── FixedSizeChunkingStrategy.java
    │   │   ├── ParagraphChunkingStrategy.java
    │   │   ├── RecursiveChunkingStrategy.java
    │   │   ├── SemanticChunkingStrategy.java
    │   │   └── SlidingWindowChunkingStrategy.java
    │   └── service/ChunkingService.java
    ├── model/                                 # AI 模型模块
    │   ├── AbstractModel.java                 # 模型抽象基类
    │   ├── ModelFactory.java                  # 模型工厂
    │   ├── siliconflow/SiliconflowModel.java  # 硅基流动实现
    │   ├── qwen/QwenModel.java                # 千问实现
    │   ├── embedding/                         # Embedding 相关
    │   └── rerank/                            # Rerank 相关
    ├── vector/                                # 向量存储模块
    │   ├── service/MilvusServiceImpl.java     # Milvus 实现
    │   └── config/MilvusConfig.java
    ├── retrieval/                             # 检索与生成模块
    │   ├── RetrievalService.java              # 检索服务（核心）
    │   ├── input/UserInputProcessor.java      # 意图识别 + 查询重写
    │   └── input/IntentClassifier.java
    └── utils/ConvertUtil.java                 # 工具类
```

## 快速开始

### 1. 环境准备

- JDK 21+
- Maven 3.9+
- Milvus 2.3+ (向量数据库)
- (可选) Tesseract OCR

### 2. 配置环境变量

```bash
export SILICONFLOW-API-KEY=your_api_key_here
export MILVUS_TOKEN=optional_token
```

### 3. 编译运行

```bash
# 编译
cd all-rag && mvn compile

# 运行
cd all-rag && mvn spring-boot:run

# 或打包运行
cd all-rag && mvn package
java -jar target/all-rag-1.0-SNAPSHOT.jar
```

### 4. 验证服务

```bash
# 健康检查
curl http://localhost:8080/api/actuator/health

# 上传文件测试
curl -X POST -F "file=@test.pdf" http://localhost:8080/api/v1/datasource/upload
```

## API 接口文档

### 数据源接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 文件上传 | POST | `/api/v1/datasource/upload` | multipart/form-data 上传文件 |
| URL 解析 | POST | `/api/v1/datasource/url` | 下载并解析远程文档 |
| 原始文本 | POST | `/api/v1/datasource/text` | 直接提交文本内容 |

### 向量存储接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 文件向量化 | POST | `/api/v1/vector/upload` | 上传→解析→分块→嵌入→存储（全流程） |
| 向量搜索 | POST | `/api/v1/vector/search` | 语义相似度搜索 |
| 集合管理 | POST | `/api/v1/vector/collections` | 创建/删除集合 |

### RAG 问答接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| **RAG 问答** | POST | `/api/v1/retrieval/rag-answer` | 完整 RAG 流程（意图→检索→重排→生成） |
| **简化 RAG** | POST | `/api/v1/retrieval/rag-answer-simple` | 快速问答（默认参数） |
| 向量检索 | GET | `/api/v1/retrieval/vector-search` | 纯向量检索 |
| 稀疏检索 | GET | `/api/v1/retrieval/sparse-search` | 关键词检索 |
| 混合检索 | GET | `/api/v1/retrieval/hybrid-search` | 向量 + 稀疏融合检索 |

### RAG 问答请求示例

```bash
curl -X POST http://localhost:8080/api/v1/retrieval/rag-answer \
  -H "Content-Type: application/json" \
  -d '{
    "query": "什么是 KRaft 模式？",
    "sessionId": "optional-session-id",
    "topK": 10,
    "topN": 5,
    "orgId": "default"
  }'
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "aiRes": "KRaft 是 Kafka 的..."
  },
  "sessionId": "xxx"
}
```

## 核心模块详解

### 1. 文档解析 (Document Parsing)

**支持的文件格式：**

| 格式 | MIME 类型 | 备注 |
|------|-----------|------|
| PDF | `application/pdf` | 支持 OCR 扫描件 |
| Word | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | docx/doc |
| Excel | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | xlsx/xls |
| PPT | `application/vnd.openxmlformats-officedocument.presentationml.presentation` | pptx/ppt |
| Markdown | `text/markdown` | 保留结构 |
| HTML | `text/html` | 提取正文 |
| 纯文本 | `text/plain` | 直接读取 |

**关键特性：**
- 60秒超时保护（防止恶意文件阻塞）
- 200MB 文件大小限制
- MIME 类型白名单校验

### 2. 文本分块 (Text Chunking)

**5 种分块策略：**

| 策略 | 适用场景 | 特点 |
|------|----------|------|
| `fixed_size` | 代码、结构化数据 | 固定字符窗口 |
| `paragraph` | 文章、报告 | 段落智能合并/拆分（3层处理） |
| `recursive` | Markdown、结构化文档 | 保留层级结构 |
| `semantic` | 长篇文章 | 语义边界感知 |
| `sliding_window` | 上下文敏感处理 | 滑动窗口重叠 |

**配置示例：**
```yaml
rag:
  chunk:
    default-strategy: sliding_window
    defaults:
      target-chunk-size: 300
      min-chunk-size: 100
      max-chunk-size: 1000
      overlap-size: 30
```

### 3. 检索与生成 (Retrieval & Generation)

**完整 RAG 流程：**

```
用户问题
    ↓
[意图识别] → 闲聊/敏感词过滤
    ↓
[查询重写] → 扩展查询语义
    ↓
[混合检索] → 向量 + 稀疏（RRF 融合）
    ↓
[语义重排] → Rerank 模型精排
    ↓
[Prompt 构建] → 注入参考资料
    ↓
[LLM 生成] → 流式/同步回答
```

**检索模式对比：**

| 模式 | 优点 | 适用场景 |
|------|------|----------|
| 向量检索 | 语义理解好 | 概念查询、同义词 |
| 稀疏检索 | 精确匹配强 | 关键词、ID、代码 |
| 混合检索 | 兼顾两者 | 通用场景（推荐） |

## 配置说明

### application.yml 核心配置

```yaml
# 模型配置
ai-model:
  providers:
    siliconflow:
      api-key: ${SILICONFLOW-API-KEY}
      base-url: https://api.siliconflow.cn/v1
      chat-model:
        - Qwen/Qwen3.5-122B-A10B
      embed-model:
        - Qwen/Qwen3-Embedding-4B
      rerank-model:
        - Qwen/Qwen3-Reranker-4B

# 向量数据库配置
rag:
  vector:
    type: milvus
    milvus:
      uri: http://localhost:19530
      collection:
        name: rag_documents
        dimension: 2560        # 与 Embedding 模型维度一致
        index-type: HNSW
        metric-type: COSINE

# 分块配置
  chunk:
    default-strategy: paragraph
    defaults:
      target-chunk-size: 500
      max-chunk-size: 1000
```

## 监控与指标

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `rag.document.parse.duration` | Timer | 文档解析耗时 |
| `rag.document.parse.count` | Counter | 解析次数 |
| `rag.api.upload` | Timer | 上传接口耗时 |
| `rag.retrieval.duration` | Timer | 检索耗时 |
| `rag.rerank.duration` | Timer | 重排耗时 |

**访问端点：**
- 健康检查：`GET /api/actuator/health`
- Prometheus：`GET /api/actuator/prometheus`

## 开发指南

### 添加新的分块策略

```java
@Component
public class CustomChunkingStrategy extends AbstractChunkingStrategy {
    @Override
    public String getStrategyName() {
        return "custom";
    }
    
    @Override
    protected List<Chunk> doChunk(String content, String docId, 
            String docName, ChunkingOptions options) {
        // 实现分块逻辑
    }
}
```

### 添加新的模型厂商

```java
public class CustomModel extends AbstractModel {
    @Override
    public R chatSync(List<Message> messages) { ... }
    
    @Override
    public EmbeddingResponse embedding(List<String> text) { ... }
    
    @Override
    public RerankResponse rerank(String query, List<String> docs, Integer topN) { ... }
}
```

## 项目模块说明

| 模块 | 说明 |
|------|------|
| `all-rag` | 主模块，完整 RAG 系统 |
| `chunking-test` | 独立测试项目，用于快速验证分块算法 |

## 注意事项

1. **向量维度一致性**：Embedding 模型输出维度必须与 Milvus collection 的 dimension 一致（默认 2560）
2. **API 密钥安全**：生产环境务必使用环境变量注入，不要硬编码
3. **文件上传限制**：单文件最大 200MB，超时 60 秒
4. **会话隔离**：org_id 用于多租户数据隔离，确保数据安全

## License

MIT
