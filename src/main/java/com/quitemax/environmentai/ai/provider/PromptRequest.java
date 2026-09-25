package com.quitemax.environmentai.ai.provider;

public record PromptRequest(String systemPrompt, String userPrompt, double temperature, int maxTokens) {
}
