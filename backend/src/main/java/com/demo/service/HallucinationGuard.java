package com.demo.service;

import com.demo.config.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class HallucinationGuard {

    private final AiConfig config;

    public static class GuardResult {
        public boolean passed;
        public String finalContent;
        public String reason;
        public GuardResult(boolean p, String c, String r) {
            passed = p; finalContent = c; reason = r;
        }
    }

    public GuardResult check(String llmOutput, String userAllergens) {
        if (userAllergens == null || userAllergens.isBlank()) {
            return new GuardResult(true, llmOutput, "no_allergens");
        }
        List<String> list = Arrays.stream(userAllergens.split("[,,\s]+"))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (list.isEmpty()) return new GuardResult(true, llmOutput, "no_allergens");

        String lowerOutput = llmOutput.toLowerCase();
        for (String allergen : list) {
            if (lowerOutput.contains(allergen.toLowerCase())) {
                log.warn("[HallucinationGuard] allergen hit: {}", allergen);
                return new GuardResult(false, llmOutput, "detected:" + allergen);
            }
        }
        return new GuardResult(true, llmOutput, "pass");
    }

    public String addWarning(String content, List<String> allergens) {
        if (allergens == null || allergens.isEmpty()) return content;
        String warn = "[WARN] May contain allergens (" + String.join(",", allergens) + ").\n\n";
        return warn + content;
    }
}