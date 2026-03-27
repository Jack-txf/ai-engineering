# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java-based RAG (Retrieval-Augmented Generation) system consisting of multiple Maven modules:

- **all-rag/**: Main RAG application (Spring Boot 3.5.x, Java 21)
- **chunking-test/**: Standalone test project for chunking strategies
- **claude-code-demo/**: Algorithm practice project (legacy, can be ignored)

The main focus is the `all-rag` module, which implements a complete RAG pipeline.

## Build Commands

```bash
# Compile all modules
cd all-rag && mvn compile

# Run the main application
cd all-rag && mvn spring-boot:run

# Package into JAR
cd all-rag && mvn package

# Run tests
cd all-rag && mvn test

# Run a single test class
cd all-rag && mvn test -Dtest=ClassName

# Clean build artifacts
cd all-rag && mvn clean
```

## RAG Pipeline Architecture

The system implements a 4-stage RAG pipeline:

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Document  │───▶│   Chunking  │───▶│  Embedding  │───▶│    Milvus   │
│   Parsing   │    │  (5 types)  │    │  (Models)   │    │Vector Store │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
   (Tika)                               (Factory)             (SDK)
```

### 1. Document Parsing (`datasource/` package)

Uses Apache Tika to extract text and metadata from various file formats (PDF, Word, Excel, PPT, HTML, etc.).

**Key classes:**
- `DataSourceIngestionService`: Entry point for file upload, URL, or raw text
- `TikaDocumentParser`: Parses documents with timeout protection
- `DocumentParseResult`: Unified output format

**Entry methods:**
```java
// Ingest file/URL/text
ingest(DataSourceRequest request)
ingestFile(MultipartFile file, String sourceId)
ingestUrl(String url, String sourceId)
```

### 2. Text Chunking (`chunk/` package)

Implements 5 chunking strategies selectable via configuration or API:

| Strategy | Class | Best For |
|----------|-------|----------|
| `fixed_size` | `FixedSizeChunkingStrategy` | Code, structured data |
| `paragraph` | `ParagraphChunkingStrategy` | Natural text (PDF, Word) |
| `recursive` | `RecursiveChunkingStrategy` | Markdown, structured docs |
| `semantic` | `SemanticChunkingStrategy` | Long articles, content-rich docs |
| `sliding_window` | `SlidingWindowChunkingStrategy` | Context-sensitive processing |

**Key classes:**
- `ChunkingService`: Strategy routing and pipeline orchestration
- `ChunkingStrategy`: Interface for all strategies
- `ChunkingOptions`: Configuration (size, overlap, boundaries)

**Usage:**
```java
// Auto-select based on MIME type and content
chunkingService.chunk(document);

// Specify strategy
chunkingService.chunk(document, "recursive", options);
```

### 3. Embedding Models (`model/` package)

Factory-based model provider abstraction supporting multiple LLM providers.

**Key classes:**
- `AbstractModel`: Base class with chat, embedding, and rerank methods
- `ModelFactory`: Creates and caches model instances by provider name
- `SiliconfowModel`, `QwenModel`: Provider implementations

**Configuration** (in `application.yml`):
```yaml
ai-model:
  providers:
    siliconflow:
      api-key: ${SILICONFLOW-API-KEY}
      base-url: https://api.siliconflow.cn/v1
```

**Usage:**
```java
AbstractModel model = modelFactory.getModel("siliconflow");
EmbeddingResponse response = model.embedding(texts);
```

### 4. Vector Store (`vector/` package)

Milvus integration for vector storage and hybrid retrieval.

**Key classes:**
- `MilvusService`: Collection management, vector upsert/search
- `VectorController`: REST endpoints for vector operations
- `MilvusConfig`: Connection and default collection settings

**Configuration:**
```yaml
rag:
  vector:
    type: milvus
    milvus:
      uri: http://localhost:19530
      collection:
        name: rag_documents
        dimension: 1536
        index-type: HNSW
        metric-type: COSINE
```

## Configuration Key Points

All configuration is in `all-rag/src/main/resources/application.yml`:

1. **Document parsing**: `rag.datasource.parser.*` - timeout (60s), max file size (200MB)
2. **Chunking**: `rag.chunk.*` - default strategy, size limits, type-specific overrides
3. **Vector store**: `rag.vector.milvus.*` - connection, collection settings
4. **Models**: `ai-model.providers.*` - API keys, model lists

## API Endpoints (Main)

| Endpoint | Path | Description |
|----------|------|-------------|
| File upload | `POST /api/v1/datasource/upload` | Multipart file ingestion |
| URL parse | `POST /api/v1/datasource/url` | Download and parse remote doc |
| Text ingest | `POST /api/v1/datasource/text` | Raw text submission |
| Chunk document | `POST /api/v1/chunk` | Chunk parsed document |
| Vector search | `POST /api/v1/vector/search` | Semantic similarity search |
| File to vector | `POST /api/v1/vector/tackle` | Upload → Parse → Chunk → Embed → Store |

## chunking-test Module

A minimal project for testing chunking algorithms in isolation.

```bash
cd chunking-test && mvn compile && mvn exec:java -Dexec.mainClass="com.feng.rag.MainTest"
```

Contains 3 basic chunking implementations:
- `FixedSizeChunker`: Fixed character window
- `HierarchicalChunker`: Section-aware chunking
- `SemanticBoundaryChunker`: Sentence/paragraph boundary preservation