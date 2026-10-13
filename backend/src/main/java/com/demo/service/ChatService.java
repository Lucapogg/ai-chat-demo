package com.demo.service;

import com.demo.config.AiConfig;
import com.demo.dto.ChatRequest;
import com.demo.memory.ChatMemoryStore;
import com.demo.rag.KnowledgeBase;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatService {

    private final ChatMemoryStore memory;
    private final MultiModelRouter router;
    private final HallucinationGuard guard;
    private final KnowledgeBase ragBase;
    private final AiConfig config;
    private final OpenAiChatModel primaryChat;
    private final OpenAiChatModel secondaryChat;

    public ChatService(ChatMemoryStore memory,
                       MultiModelRouter router,
                       HallucinationGuard guard,
                       KnowledgeBase ragBase,
                       AiConfig config,
                       @Qualifier("primaryChatModel") OpenAiChatModel primaryChat,
                       @Qualifier("secondaryChatModel") OpenAiChatModel secondaryChat) {
        this.memory = memory;
        this.router = router;
        this.guard = guard;
        this.ragBase = ragBase;
        this.config = config;
        this.primaryChat = primaryChat;
        this.secondaryChat = secondaryChat;
    }

    public SseEmitter streamChat(ChatRequest req) {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> log.debug("[SSE] done session={}", req.getSessionId()));
        emitter.onTimeout(() -> log.warn("[SSE] timeout session={}", req.getSessionId()));
        emitter.onError(e -> log.warn("[SSE] error session={}: {}", req.getSessionId(), e.getMessage()));
        new Thread(() -> doStream(req, emitter), "chat-stream-" + req.getSessionId()).start();
        return emitter;
    }

    private void doStream(ChatRequest req, SseEmitter emitter) {
        String sessionId = req.getSessionId();
        String userMessage = req.getMessage();
        try {
            memory.appendUser(sessionId, userMessage);
            MultiModelRouter.ModelSlot slot = router.route(userMessage, Boolean.TRUE.equals(req.getForceSecondary()));
            send(emitter, "model", Map.of("slot", slot.name()));

            String ragSnippet = "";
            if (!Boolean.FALSE.equals(req.getUseRag())) {
                var hits = ragBase.search(userMessage, config.getRag().getTopK());
                ragSnippet = hits.stream().map(s -> s.text()).collect(Collectors.joining("\n---\n"));
                send(emitter, "rag", Map.of(
                        "topK", config.getRag().getTopK(),
                        "chars", ragSnippet.length(),
                        "preview", ragSnippet.length() > 200 ? ragSnippet.substring(0, 200) + "..." : ragSnippet
                ));
            }

            String systemPrompt = buildSystemPrompt(req, ragSnippet);
            String finalUserMsg = userMessage
                    + (ragSnippet.isBlank() ? "" : "\n\n[ref]\n" + ragSnippet);

            String content = generateWithRetry(req, systemPrompt, finalUserMsg, slot, emitter);
            memory.appendAssistant(sessionId, content);

            send(emitter, "done", Map.of(
                    "sessionId", sessionId,
                    "memory", memory.debug(sessionId),
                    "contentLength", content.length()
            ));
            emitter.complete();
        } catch (Exception e) {
            log.error("[SSE] fail session={}", sessionId, e);
            try {
                send(emitter, "error", Map.of("message", e.getMessage() == null ? "unknown" : e.getMessage()));
                emitter.completeWithError(e);
            } catch (Exception ignore) {}
        }
    }

    private String generateWithRetry(ChatRequest req, String systemPrompt, String userMessage,
                                     MultiModelRouter.ModelSlot slot, SseEmitter emitter) throws IOException, InterruptedException {
        int maxRetries = config.getHallucination().getMaxRetries();
        List<String> userAllergens = parseAllergens(req.getAllergens());
        MultiModelRouter.ModelSlot currentSlot = slot;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            send(emitter, "attempt", Map.of("slot", currentSlot.name(), "attempt", attempt, "maxRetries", maxRetries));
            send(emitter, "start", Map.of("attempt", attempt));

            OpenAiChatModel client = (currentSlot == MultiModelRouter.ModelSlot.PRIMARY) ? primaryChat : secondaryChat;
            List<ChatMessage> ctx = buildContextForLangChain(req.getSessionId(), userMessage, systemPrompt);

            String content = "";
            String errMsg = null;
            try {
                content = client.chat(ctx).aiMessage().text();
                log.info("[LLM] OK slot={} attempt={} len={}", currentSlot, attempt, content.length());
            } catch (Exception e) {
                errMsg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("[LLM] fail slot={} attempt={}: {}", currentSlot, attempt, errMsg);
                content = "";
            }

            // Simulated streaming: emit 20-char chunks with 30ms delay
            if (content != null && !content.isEmpty()) {
                int chunkSize = 8;
                for (int i = 0; i < content.length(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, content.length());
                    String piece = content.substring(i, end);
                    send(emitter, "token", Map.of("text", piece));
                    Thread.sleep(20);
                }
            }

            HallucinationGuard.GuardResult check = guard.check(content == null ? "" : content, req.getAllergens());
            send(emitter, "hallucination", Map.of("passed", check.passed, "reason", check.reason));

            if (errMsg != null || (content != null && content.isEmpty())) {
                if (attempt < maxRetries) {
                    currentSlot = router.fallback(currentSlot);
                    continue;
                }
                throw new RuntimeException("LLM empty: " + errMsg);
            }

            if (check.passed) return content;
            if (attempt < maxRetries) {
                log.info("[Guard] attempt={} skip-retry", attempt);
                send(emitter, "retry", Map.of("attempt", attempt + 1));
                continue;
            }
            log.warn("[Guard] fallback: add warning prefix");
            return guard.addWarning(content, userAllergens);
        }
        throw new RuntimeException("Unreachable");
    }

    private List<ChatMessage> buildContextForLangChain(String sessionId, String userMessage, String systemPrompt) {
        List<ChatMessage> out = new ArrayList<>();
        out.add(SystemMessage.from(systemPrompt));
        var memoryCtx = memory.buildContext(sessionId, "");
        for (ChatMessage m : memoryCtx) {
            if (m instanceof UserMessage) continue;
            out.add(m);
        }
        out.add(UserMessage.from(userMessage));
        return out;
    }

    private String buildSystemPrompt(ChatRequest req, String ragSnippet) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a professional hospital dietitian. Be accurate and concise.\n");
        sb.append("- Avoid fabricating medical advice.\n");
        sb.append("- If the user has marked allergens, actively warn.\n");
        sb.append("- Mark dose suggestions as 'for reference only'.\n");
        if (req.getAllergens() != null && !req.getAllergens().isBlank()) {
            sb.append("- User allergens: ").append(req.getAllergens()).append("\n");
        }
        if (!ragSnippet.isBlank()) {
            sb.append("- Reference the following knowledge snippets when relevant.\n");
        }
        return sb.toString();
    }

    private List<String> parseAllergens(String s) {
        if (s == null || s.isBlank()) return Collections.emptyList();
        return Arrays.stream(s.split("[,\\s]+"))
                .map(String::trim).filter(x -> !x.isEmpty())
                .collect(Collectors.toList());
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }
}
