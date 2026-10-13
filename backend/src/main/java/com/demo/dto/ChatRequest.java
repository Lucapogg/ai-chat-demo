package com.demo.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 聊天请求 DTO —— 支持上下文透传 / 系统提示覆盖 / 知识库开关
 */
@Data
public class ChatRequest {
    /** 会话 ID(用于 Memory 跨请求持久化) */
    @NotBlank
    private String sessionId;

    /** 当前用户输入 */
    @NotBlank
    @Size(max = 4000)
    private String message;

    /** 用户过敏原(可选,用于幻觉兜底校验) */
    private String allergens;

    /** 是否使用 RAG(默认 true) */
    private Boolean useRag = true;

    /** 是否强制路由到 secondary 模型(默认按规则路由) */
    private Boolean forceSecondary = false;

    /** 历史消息快照(前端回显用,后端从 MemoryStore 拉真实历史) */
    private List<MessageSnapshot> history;
}