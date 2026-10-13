package com.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 整个 demo 的 AI 配置入口,所有多模型/路由/Memory/RAG/幻觉参数
 * 都集中在这里。@ConfigurationProperties 让 application.yml 自动映射。
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
@Data
public class AiConfig {
    private ModelConfig primary = new ModelConfig();
    private ModelConfig secondary = new ModelConfig();
    private RouterConfig router = new RouterConfig();
    private MemoryConfig memory = new MemoryConfig();
    private RagConfig rag = new RagConfig();
    private HallucinationConfig hallucination = new HallucinationConfig();

    @Data
    public static class ModelConfig {
        private String provider;
        private String baseUrl;
        private String apiKey;
        private String model;
        private Double temperature = 0.7;
        private Integer maxTokens = 2048;
    }

    @Data
    public static class RouterConfig {
        private Integer shortMessageThreshold = 50;
        private Integer timeoutMs = 30000;
        private Integer maxRetries = 2;
    }

    @Data
    public static class MemoryConfig {
        private Integer shortTermWindow = 20;
        private Integer summaryTrigger = 10;
        private Integer summaryMaxTokens = 800;
    }

    @Data
    public static class RagConfig {
        private String knowledgeFile;
        private Integer chunkSize = 200;
        private Integer chunkOverlap = 30;
        private Integer topK = 3;
    }

    @Data
    public static class HallucinationConfig {
        private String allergenKeywords;
        private Integer maxRetries = 2;
    }
}