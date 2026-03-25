# All-RAG

RAG (Retrieval-Augmented Generation) 数据源接入模块 —— 统一接收多种格式文档，提取纯文本内容供下游向量化使用。
目录说明：【chunking-test】是测试分块的测试项目，初始的三种分块策略可以看这个里面。


## 项目概述

本项目是 RAG 系统的**数据源接入层**，核心职责：
- **统一接收**多种格式文档（PDF、Word、PPT、Excel、TXT、HTML 等）
- **提取纯文本**内容和元数据（作者、创建时间、页数等）
- **输出标准化**的 `DocumentParseResult`，供下游 Chunking / Embedding 模块使用

## 技术栈

- **JDK 21**：Virtual Thread、Record、Switch Expression
- **Spring Boot 3.5.x**：Web、Validation、Actuator
- **Apache Tika 2.9.x**：全格式文档解析核心
- **Micrometer + Prometheus**：可观测性指标

## 项目结构

```
all-rag/
├── pom.xml                                    # Maven 配置
└── src/main/java/com/feng/rag/
    ├── RagApplication.java                    # 启动类
    ├── controller/
    │   └── DataSourceController.java          # REST API 接口层
    ├── datasource/
    │   ├── common/
    │   │   ├── DataSourceRequest.java         # 统一请求模型
    │   │   └── DocumentParseResult.java       # 统一响应模型
    │   ├── config/
    │   │   ├── DataSourceProperties.java      # 配置属性绑定
    │   │   └── TikaConfiguration.java         # Tika 组件配置
    │   ├── exception/
    │   │   └── DocumentParseException.java    # 业务异常
    │   ├── parse/
    │   │   ├── DocumentParser.java            # 解析器接口
    │   │   └── TikaDocumentParser.java        # Tika 解析实现
    │   ├── service/
    │   │   ├── DataSourceIngestionService.java    # 统一接入服务
    │   │   └── DocumentParserService.java         # 解析核心服务
    │   └── util/
    │       ├── MimeTypeDetector.java          # MIME 类型检测
    │       └── TextCleanupUtils.java          # 文本清洗工具
    └── exp/
        └── GlobalExceptionHandler.java        # 全局异常处理
```

## 核心流程

```
┌─────────────────┐     ┌─────────────────────┐     ┌─────────────────┐
│  文件上传/URL    │────▶│  DataSourceIngestion  │────▶│ DocumentParser  │
│  /原始文本      │     │       Service         │     │     Service     │
└─────────────────┘     └─────────────────────┘     └─────────────────┘
                                                            │
                              ┌─────────────────────────────┘
                              ▼
                       ┌─────────────────┐
                       │ TikaDocumentParser│──▶ 提取纯文本 + 元数据
                       │  (Apache Tika)   │
                       └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐
                       │ DocumentParseResult │──▶ 供下游 Chunking/Embedding
                       │   (统一输出模型)    │
                       └─────────────────┘
```

## API 接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 文件上传 | POST | `/api/v1/datasource/upload` | multipart/form-data 上传文件 |
| URL 解析 | POST | `/api/v1/datasource/url` | 下载并解析远程文档 |
| 原始文本 | POST | `/api/v1/datasource/text` | 直接提交文本内容 |
| 批量 URL | POST | `/api/v1/datasource/batch` | 批量解析（最多20个）|

## 支持的文件格式

| 格式 | MIME 类型 | 说明 |
|------|-----------|------|
| PDF | `application/pdf` | 支持文本提取，可选 OCR |
| Word | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` / `application/msword` | docx / doc |
| Excel | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` / `application/vnd.ms-excel` | xlsx / xls |
| PPT | `application/vnd.openxmlformats-officedocument.presentationml.presentation` / `application/vnd.ms-powerpoint` | pptx / ppt |
| 文本 | `text/plain` | 纯文本 |
| HTML | `text/html` | 网页内容 |
| Markdown | `text/markdown` | Markdown 文档 |
| JSON/XML | `application/json` / `application/xml` | 结构化数据 |

## 关键设计

### 1. 三种接入方式统一抽象

```java
public DocumentParseResult ingest(DataSourceRequest request) {
    return switch (request.getSourceType()) {
        case FILE_UPLOAD -> ingestFile(request.getFile(), request.getSourceId());
        case URL         -> ingestUrl(request.getUrl(), request.getSourceId());
        case RAW_TEXT    -> ingestRawText(request.getRawText(), ...);
    };
}
```

### 2. 解析超时控制

使用 `CompletableFuture + 专用线程池` 实现超时中断，防止恶意文件卡死线程：

```java
CompletableFuture<DocumentParseResult> future = CompletableFuture.supplyAsync(
    () -> tikaParser.parse(inputStream, fileName, sourceId),
    documentParseExecutor
);
return future.get(timeoutSeconds, TimeUnit.SECONDS);  // 超时强制取消
```

### 3. 统一输出模型

```java
DocumentParseResult {
    fileName,      // 文件名
    sourceId,      // 业务来源标识
    mimeType,      // MIME 类型
    content,       // 纯文本内容
    contentLength, // 内容长度
    pageCount,     // 页数/Sheet数/Slide数
    metadata,      // 文档元数据（作者、创建时间等）
    status,        // 解析状态：SUCCESS/PARTIAL/FAILED/TIMEOUT/UNSUPPORTED
    parseErrors,   // 错误列表
    parseDurationMs // 解析耗时
}
```

## 配置项

```yaml
rag:
  datasource:
    parser:
      parse-timeout-seconds: 60      # 解析超时时间
      max-file-size-bytes: 209715200 # 最大文件大小（200MB）
      max-content-length: 5000000    # 最大提取文本长度（字符数）
      extract-metadata: true         # 是否提取元数据
      ocr:
        enabled: false               # 是否启用 OCR
        language: chi_sim+eng        # Tesseract 语言包
    allowed-mime-types:              # 文件类型白名单
      - application/pdf
      - application/vnd.openxmlformats-officedocument.wordprocessingml.document
      ...
```

## 运行

```bash
# 编译
cd all-rag && mvn compile

# 运行
cd all-rag && mvn spring-boot:run

# 或打包后运行
cd all-rag && mvn package
java -jar target/all-rag-1.0-SNAPSHOT.jar
```

服务启动后：
- API 地址：`http://localhost:8080/api/v1/datasource`
- 健康检查：`http://localhost:8080/api/actuator/health`
- Prometheus 指标：`http://localhost:8080/api/actuator/prometheus`

## 监控指标

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `rag.document.parse.duration` | Timer | 文档解析耗时（按 parser、status 打标签）|
| `rag.document.parse.count` | Counter | 解析次数（按 parser、status 打标签）|
| `rag.api.upload` | Timer | 文件上传接口耗时 |
| `rag.api.url` | Timer | URL 解析接口耗时 |
| `rag.api.text` | Timer | 文本接入接口耗时 |

## 安全设计

- **文件类型白名单**：基于 MIME 类型检测（非扩展名），防止伪装
- **文件大小限制**：200MB 上限，防止 OOM
- **SSRF 防护**：URL 只允许 http/https 协议
- **解析超时**：60秒强制中断，防止恶意文件阻塞
- **内容截断**：超大文档自动截断，防止内存溢出

## 下游使用

解析结果可直接供 RAG 管道的后续环节使用：

```java
// 1. Chunking：将长文本切分
List<String> chunks = textChunker.split(result.getContent());

// 2. Embedding：生成向量
List<float[]> vectors = embeddingModel.embed(chunks);

// 3. Vector Store：存入向量数据库
vectorStore.upsert(vectors, metadata);
```

## License

MIT
