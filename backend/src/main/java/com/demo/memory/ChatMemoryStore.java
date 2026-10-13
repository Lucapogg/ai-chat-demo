package com.demo.memory;

import com.demo.config.AiConfig;
import com.demo.dto.MessageSnapshot;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Memory Store -- simplified LangChain4j Memory implementation.
 * - Short term: keep last N messages per session (in-memory).
 * - Long term: every K accumulated messages triggers an LLM summary,
 *   injected as a SystemMessage.
 */
@Slf4j
@Component
public class ChatMemoryStore {

    private final AiConfig config;
    private final OpenAiChatModel summaryModel;

    public ChatMemoryStore(AiConfig config,
                           @Qualifier("secondaryChatModel") OpenAiChatModel summaryModel) {
        this.config = config;
        this.summaryModel = summaryModel;
    }

    private final Map<String, Deque<MessageSnapshot>> shortTerm = new ConcurrentHashMap<>();
    private final Map<String, String> longTermSummary = new ConcurrentHashMap<>();
    private final Map<String, Integer> pendingCount = new ConcurrentHashMap<>();

    /** Guard against null/blank sessionId -- ConcurrentHashMap rejects null keys. */
    private static void requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be null/blank");
        }
    }

    public List<ChatMessage> buildContext(String sessionId, String currentUserMessage) {
        requireSession(sessionId);
        List<ChatMessage> out = new ArrayList<>();
        String summary = longTermSummary.get(sessionId);
        if (summary != null && !summary.isBlank()) {
            out.add(SystemMessage.from(
                    "Below is a long-term summary of the prior conversation; keep persona and continuity:\n" + summary));
        }
        Deque<MessageSnapshot> window = shortTerm.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
        for (MessageSnapshot m : window) {
            out.add(toLangChain(m));
        }
        out.add(UserMessage.from(currentUserMessage));
        return out;
    }

    public void appendUser(String sessionId, String userMessage) {
        requireSession(sessionId);
        Deque<MessageSnapshot> window = shortTerm.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
        synchronized (window) {
            window.addLast(new MessageSnapshot("user", userMessage));
            trimToWindow(window);
            int count = pendingCount.merge(sessionId, 1, Integer::sum);
            if (count >= config.getMemory().getSummaryTrigger()) {
                triggerSummary(sessionId, window);
                pendingCount.put(sessionId, 0);
            }
        }
    }

    public void appendAssistant(String sessionId, String aiMessage) {
        requireSession(sessionId);
        Deque<MessageSnapshot> window = shortTerm.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
        synchronized (window) {
            window.addLast(new MessageSnapshot("assistant", aiMessage));
            trimToWindow(window);
        }
    }

    public void clear(String sessionId) {
        requireSession(sessionId);
        shortTerm.remove(sessionId);
        longTermSummary.remove(sessionId);
        pendingCount.remove(sessionId);
    }

    private void trimToWindow(Deque<MessageSnapshot> window) {
        int max = config.getMemory().getShortTermWindow();
        while (window.size() > max) {
            window.removeFirst();
        }
    }

    private void triggerSummary(String sessionId, Deque<MessageSnapshot> window) {
        try {
            String transcript = window.stream()
                    .map(m -> "[" + m.getRole() + "] " + m.getContent())
                    .collect(Collectors.joining("\n"));
            String prompt = "Compress the following conversation into a Chinese summary no longer than "
                    + config.getMemory().getSummaryMaxTokens()
                    + " tokens. Preserve key facts (user preferences, allergens, conclusions reached):\n\n" + transcript;
            String summary = summaryModel.chat(prompt);
            longTermSummary.put(sessionId, summary);
            log.info("[Memory] session={} triggered long-term summary, length={}", sessionId, summary.length());
        } catch (Exception e) {
            log.warn("[Memory] summary failed, skipped: {}", e.getMessage());
        }
    }

    private ChatMessage toLangChain(MessageSnapshot m) {
        return switch (m.getRole()) {
            case "user" -> UserMessage.from(m.getContent());
            case "assistant" -> AiMessage.from(m.getContent());
            default -> SystemMessage.from(m.getContent());
        };
    }

    public Map<String, Object> debug(String sessionId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("shortTermSize", shortTerm.getOrDefault(sessionId, new ArrayDeque<>()).size());
        out.put("hasLongTermSummary", longTermSummary.containsKey(sessionId));
        out.put("pendingCount", pendingCount.getOrDefault(sessionId, 0));
        return out;
    }

    @PreDestroy
    public void shutdown() {
        log.info("[Memory] shutdown, sessions={}", shortTerm.size());
    }
}
