package com.demo.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatModelConfig {

    @Autowired
    private AiConfig config;

    @Bean(name = "primaryChatModel")
    public OpenAiChatModel primaryChatModel() {
        AiConfig.ModelConfig m = config.getPrimary();
        return OpenAiChatModel.builder()
                .baseUrl(m.getBaseUrl())
                .apiKey(m.getApiKey())
                .modelName(m.getModel())
                .temperature(m.getTemperature())
                .maxTokens(m.getMaxTokens())
                .timeout(java.time.Duration.ofMillis(config.getRouter().getTimeoutMs()))
                .build();
    }

    @Bean(name = "secondaryChatModel")
    public OpenAiChatModel secondaryChatModel() {
        AiConfig.ModelConfig m = config.getSecondary();
        return OpenAiChatModel.builder()
                .baseUrl(m.getBaseUrl())
                .apiKey(m.getApiKey())
                .modelName(m.getModel())
                .temperature(m.getTemperature())
                .maxTokens(m.getMaxTokens())
                .timeout(java.time.Duration.ofMillis(config.getRouter().getTimeoutMs()))
                .build();
    }

    @Bean(name = "primaryStreamingModel")
    public OpenAiStreamingChatModel primaryStreamingModel() {
        AiConfig.ModelConfig m = config.getPrimary();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(m.getBaseUrl())
                .apiKey(m.getApiKey())
                .modelName(m.getModel())
                .temperature(m.getTemperature())
                .maxTokens(m.getMaxTokens())
                .timeout(java.time.Duration.ofMillis(config.getRouter().getTimeoutMs()))
                .build();
    }

    @Bean(name = "secondaryStreamingModel")
    public OpenAiStreamingChatModel secondaryStreamingModel() {
        AiConfig.ModelConfig m = config.getSecondary();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(m.getBaseUrl())
                .apiKey(m.getApiKey())
                .modelName(m.getModel())
                .temperature(m.getTemperature())
                .maxTokens(m.getMaxTokens())
                .timeout(java.time.Duration.ofMillis(config.getRouter().getTimeoutMs()))
                .build();
    }
}
