package com.demo.service;

import com.demo.config.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MultiModelRouter {

    private final AiConfig config;

    public enum ModelSlot { PRIMARY, SECONDARY }

    public ModelSlot route(String userMessage, boolean forceSecondary) {
        if (forceSecondary) return ModelSlot.SECONDARY;
        int len = userMessage == null ? 0 : userMessage.length();
        if (len < config.getRouter().getShortMessageThreshold()) {
            log.debug("[Router] short msg (len={}) -> secondary", len);
            return ModelSlot.SECONDARY;
        }
        log.debug("[Router] long msg (len={}) -> primary", len);
        return ModelSlot.PRIMARY;
    }

    public ModelSlot fallback(ModelSlot failed) {
        return switch (failed) {
            case PRIMARY -> ModelSlot.SECONDARY;
            case SECONDARY -> ModelSlot.PRIMARY;
        };
    }
}