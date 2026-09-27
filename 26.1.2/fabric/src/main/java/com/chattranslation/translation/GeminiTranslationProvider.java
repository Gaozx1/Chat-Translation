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

    private static final int MAX_ATTEMPTS = 2;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(300);

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
        final String cacheKey = TranslationCache.key("gemini", sourceLang, targetLang, text);
        final TranslationCache cache = TranslationCache.shared();
        final String cached = cache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> doTranslate(text, sourceLang, targetLang),
                        HttpClientFactory.executor())
                .thenApply(result -> {
                    if (result != null && !result.isEmpty() && result.charAt(0) != '[') {
                        cache.put(cacheKey, result);
                    }
                    return result;
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

    private String doTranslate(String text, String sourceLang, String targetLang) {
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
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    return parseResponse(response.body());
                }
                if (attempt < MAX_ATTEMPTS && isRetryable(status)) {
                    sleep(RETRY_BACKOFF.toMillis() * attempt);
                    continue;
                }
                return "[Gemini Error: HTTP " + status + "]";
            }
            return "[Gemini Error: Retry exhausted]";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[Gemini Error: interrupted]";
        } catch (Exception e) {
            return "[Gemini Error: " + e.getMessage() + "]";
        }
    }

    /** 429, 408 and 5xx may succeed on a retry; other 4xx never will. */
    static boolean isRetryable(int status) {
        return status == 429 || status == 408 || status >= 500;
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
