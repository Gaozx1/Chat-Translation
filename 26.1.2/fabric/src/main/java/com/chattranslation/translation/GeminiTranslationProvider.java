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

public class GeminiTranslationProvider implements TranslationProvider {

    private static final int MAX_ATTEMPTS = 3;

    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;

    public GeminiTranslationProvider(String apiUrl, String apiKey, boolean insecureSsl) {
        this.httpClient = HttpClientFactory.create(insecureSsl);
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    @Override
    public CompletableFuture<String> translate(String text, String sourceLang, String targetLang) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (apiUrl == null || apiUrl.isBlank()) {
                    return "[Gemini Error: API URL not set]";
                }
                if (apiKey == null || apiKey.isBlank()) {
                    return "[Gemini Error: API key not set]";
                }

                JsonObject body = new JsonObject();
                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                part.addProperty("text", "Translate this Minecraft chat text from " + sourceLang + " to " + targetLang + ". Return only the translated result. Do not explain. Preserve usernames, ranks, bracket tags, URLs, numbers, and game-specific tokens. If no translation is needed, return an empty string.\nText: " + text);
                parts.add(part);
                userContent.add("parts", parts);
                contents.add(userContent);
                body.add("contents", contents);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                        .build();

                for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        return parseResponse(response.body());
                    }
                    if (attempt == MAX_ATTEMPTS) {
                        return "[Gemini Error: HTTP " + response.statusCode() + "]";
                    }
                }
                return "[Gemini Error: Retry exhausted]";
            } catch (Exception e) {
                return "[Gemini Error: " + e.getMessage() + "]";
            }
        });
    }

    @Override
    public CompletableFuture<String> detectLanguage(String text) {
        return CompletableFuture.completedFuture("auto");
    }

    @Override
    public String getName() {
        return "Gemini";
    }

    private String parseResponse(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
                if (content != null) {
                    JsonArray parts = content.getAsJsonArray("parts");
                    if (parts != null && !parts.isEmpty()) {
                        JsonObject part = parts.get(0).getAsJsonObject();
                        if (part.has("text")) {
                            return part.get("text").getAsString().trim();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "[Gemini Parse Error]";
    }
}
