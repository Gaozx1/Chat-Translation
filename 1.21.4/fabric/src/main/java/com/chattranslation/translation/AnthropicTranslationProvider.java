package com.chattranslation.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class AnthropicTranslationProvider implements TranslationProvider {

    private static final int MAX_ATTEMPTS = 3;

    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;
    private final String modelId;

    public AnthropicTranslationProvider(String apiUrl, String apiKey, String modelId, boolean insecureSsl) {
        this.httpClient = HttpClientFactory.create(insecureSsl);
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.modelId = modelId;
    }

    @Override
    public CompletableFuture<String> translate(String text, String sourceLang, String targetLang) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (apiUrl == null || apiUrl.isBlank()) {
                    return "[Anthropic Error: API URL not set]";
                }
                if (apiKey == null || apiKey.isBlank()) {
                    return "[Anthropic Error: API key not set]";
                }
                if (modelId == null || modelId.isBlank()) {
                    return "[Anthropic Error: Model ID not set]";
                }

                JsonObject body = new JsonObject();
                body.addProperty("model", modelId);
                body.addProperty("max_tokens", 256);
                body.addProperty("system", "You are a Minecraft chat translation engine. Return only the translated result. Do not explain. Preserve usernames, ranks, bracket tags, URLs, numbers, and game-specific tokens. If no translation is needed, return an empty string.");
                JsonArray messages = new JsonArray();
                JsonObject message = new JsonObject();
                message.addProperty("role", "user");
                JsonArray content = new JsonArray();
                JsonObject textPart = new JsonObject();
                textPart.addProperty("type", "text");
                textPart.addProperty("text", "Translate this Minecraft chat text from " + sourceLang + " to " + targetLang + ".\nText: " + text);
                content.add(textPart);
                message.add("content", content);
                messages.add(message);
                body.add("messages", messages);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                        .build();

                for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        return parseResponse(response.body());
                    }
                    if (attempt == MAX_ATTEMPTS) {
                        return "[Anthropic Error: HTTP " + response.statusCode() + "]";
                    }
                }
                return "[Anthropic Error: Retry exhausted]";
            } catch (Exception e) {
                return "[Anthropic Error: " + e.getMessage() + "]";
            }
        });
    }

    @Override
    public CompletableFuture<String> detectLanguage(String text) {
        return CompletableFuture.completedFuture("auto");
    }

    @Override
    public String getName() {
        return "Anthropic";
    }

    private String parseResponse(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray content = root.getAsJsonArray("content");
            if (content != null && !content.isEmpty()) {
                JsonObject first = content.get(0).getAsJsonObject();
                if (first.has("text")) {
                    return first.get("text").getAsString().trim();
                }
            }
        } catch (Exception ignored) {
        }
        return "[Anthropic Parse Error]";
    }
}
