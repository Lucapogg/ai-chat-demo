package com.demo.rag;

import com.demo.config.AiConfig;
import dev.langchain4j.data.segment.TextSegment;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBase {

    private final AiConfig config;
    private final List<TextSegment> segments = new ArrayList<>();
    private final Map<Integer, Map<String, Integer>> tf = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() throws IOException {
        String filePath = config.getRag().getKnowledgeFile().replace("classpath:", "");
        ClassPathResource resource = new ClassPathResource(filePath);
        if (!resource.exists()) {
            log.warn("[RAG] knowledge file not found: {}", filePath);
            return;
        }
        String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        // Note: Python triple-quoted string preserves \s as \s in the output
        String[] paragraphs = text.split("\n\s*\n");
        int chunkSize = config.getRag().getChunkSize();
        int overlap = config.getRag().getChunkOverlap();
        int id = 0;
        for (String para : paragraphs) {
            if (!StringUtils.hasText(para)) continue;
            StringBuilder buf = new StringBuilder();
            for (String line : para.split("\n")) {
                if (buf.length() + line.length() + 1 > chunkSize && buf.length() > 0) {
                    addSegment(id++, buf.toString().trim());
                    int keep = Math.min(overlap, buf.length());
                    String tail = buf.substring(buf.length() - keep);
                    buf.setLength(0);
                    buf.append(tail).append(" ");
                }
                buf.append(line).append(" ");
            }
            if (buf.length() > 0) {
                addSegment(id++, buf.toString().trim());
            }
        }
        log.info("[RAG] loaded {} chunks", segments.size());
    }

    private void addSegment(int id, String text) {
        TextSegment seg = TextSegment.from(text);
        segments.add(seg);
        Map<String, Integer> freq = new HashMap<>();
        for (String token : tokenize(text)) {
            freq.merge(token, 1, Integer::sum);
        }
        tf.put(id, freq);
    }

    public List<TextSegment> search(String query, int topK) {
        if (segments.isEmpty()) return Collections.emptyList();
        Map<String, Integer> qTokens = new HashMap<>();
        for (String t : tokenize(query)) {
            qTokens.merge(t, 1, Integer::sum);
        }
        Map<Integer, Double> scores = new HashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            Map<String, Integer> docTf = tf.get(i);
            double score = 0;
            for (var e : qTokens.entrySet()) {
                Integer f = docTf.get(e.getKey());
                if (f != null) {
                    score += e.getValue() * f;
                }
            }
            score /= Math.sqrt(docTf.size() + 1);
            if (score > 0) {
                scores.put(i, score);
            }
        }
        return scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(e -> segments.get(e.getKey()))
                .collect(Collectors.toList());
    }

    private List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder ascii = new StringBuilder();
        for (char ch : text.toCharArray()) {
            if (ch >= 0x4E00 && ch <= 0x9FA5) {
                if (ascii.length() > 0) {
                    out.add(ascii.toString().toLowerCase());
                    ascii.setLength(0);
                }
                out.add(String.valueOf(ch));
            } else if (Character.isLetterOrDigit(ch)) {
                ascii.append(ch);
            } else {
                if (ascii.length() > 0) {
                    out.add(ascii.toString().toLowerCase());
                    ascii.setLength(0);
                }
            }
        }
        if (ascii.length() > 0) {
            out.add(ascii.toString().toLowerCase());
        }
        return out;
    }
}
