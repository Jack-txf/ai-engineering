package com.feng.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Description: All-rag主启动类
 * @Author: txf
 * @Date: 2026/3/23
 * RAG 数据源接入模块 —— Spring Boot 启动入口
 *
 * <p>模块职责：
 * <ul>
 *   <li>统一接收多种格式文档（PDF、Word、PPT、Excel、TXT 等）</li>
 *   <li>基于 Apache Tika 提取纯文本内容和元数据</li>
 *   <li>输出统一的 DocumentParseResult，供下游 Chunking / Embedding 使用</li>
 * </ul>
 *
 * <p>技术栈：
 * <ul>
 *   <li>JDK 21（Virtual Thread、Record、Switch Expression）</li>
 *   <li>Spring Boot 3.5.x</li>
 *   <li>Apache Tika 2.9.x</li>
 *   <li>Micrometer + Prometheus（可观测性）</li>
 * </ul>
 */
@Slf4j
@SpringBootApplication
public class RagApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
        log.info("================================================");
        log.info("  RAG 数据源接入模块启动成功");
        log.info("  接口地址: http://localhost:8080/api/v1/datasource");
        log.info("  健康检查: http://localhost:8080/api/actuator/health");
        log.info("================================================");
    }
}







