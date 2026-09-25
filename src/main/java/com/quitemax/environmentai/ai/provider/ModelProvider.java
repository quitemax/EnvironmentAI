package com.quitemax.environmentai.ai.provider;

import java.util.concurrent.CompletableFuture;

/**
 * A model backend that can complete a prompt. The returned future resolves on whatever
 * executor the underlying HttpClient was built with — callers must not touch game state
 * from the completion stage without marshaling back onto the server thread first.
 */
public interface ModelProvider {
	CompletableFuture<String> complete(PromptRequest request);
}
