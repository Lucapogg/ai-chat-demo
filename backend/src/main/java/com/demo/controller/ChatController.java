package com.demo.controller;

import com.demo.dto.ChatRequest;
import com.demo.memory.ChatMemoryStore;
import com.demo.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatMemoryStore memory;

    /**
     * 流式聊天 —�?客户端使�?EventSource 监听
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        return chatService.streamChat(request);
    }

    /**
     * 清空会话 Memory
     */
    @PostMapping("/clear/{sessionId}")
    public Map<String, Object> clear(@PathVariable String sessionId) {
        memory.clear(sessionId);
        return Map.of("ok", true);
    }

    /**
     * 调试:查看某会话的 Memory 状�?     */
    @GetMapping("/memory/{sessionId}")
    public Map<String, Object> memoryOf(@PathVariable String sessionId) {
        return memory.debug(sessionId);
    }

    /**
     * 健康检�?     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "ai-chat-demo");
    }
}
