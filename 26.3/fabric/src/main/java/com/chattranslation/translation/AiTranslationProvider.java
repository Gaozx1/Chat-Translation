package com.chattranslation.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class AiTranslationProvider implements TranslationProvider {

    private static final int MAX_ATTEMPTS = 2;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(300);

    private static final String SYSTEM_PROMPT = "You are a Minecraft chat translation engine. "
            + "Translate into the requested target language and return only the translated result. "
            + "Do not explain. Do not add labels, quotes, notes, markdown, romanization, or extra punctuation. "
            + "Preserve usernames, ranks, bracket tags, URLs, numbers, and game-specific tokens. "
            + "If the text should not be translated or no translation is needed, return an empty string.";

    /** Escaped once; the system prompt is identical for every request. */
    private static final String ESCAPED_SYSTEM_PROMPT = escape(SYSTEM_PROMPT);

    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;
    private final String modelId;
    private final String name;
    private final String escapedModelId;
    private final Duration requestTimeout;

    public AiTranslationProvider(String apiUrl, String apiKey, String modelId, boolean insecureSsl, String name) {
        this(apiUrl, apiKey, modelId, insecureSsl, name, 45);
    }

    /**
     * @param timeoutSeconds per-request deadline; public AI endpoints can take tens of seconds,
     *                       so this is configurable instead of a hardcoded 20s that guarantees failures
     */
    public AiTranslationProvider(String apiUrl, String apiKey, String modelId, boolean insecureSsl, String name,
                                 int timeoutSeconds) {
        this.httpClient = HttpClientFactory.create(insecureSsl);
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.modelId = modelId;
        this.name = name;
        this.escapedModelId = escape(modelId);
        this.requestTimeout = Duration.ofSeconds(Math.max(10, Math.min(180, timeoutSeconds)));
    }

    @Override
    public CompletableFuture<String> translate(String text, String sourceLang, String targetLang) {
        if (apiUrl == null || apiUrl.isBlank()) {
            return CompletableFuture.completedFuture("[" + name + " Error: API URL not set]");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture("[" + name + " Error: API key not set]");
        }
        if (modelId == null || modelId.isBlank()) {
            return CompletableFuture.completedFuture("[" + name + " Error: Model ID not set]");
        }

        final String cacheKey = TranslationCache.key(name, sourceLang, targetLang, text);
        final TranslationCache cache = TranslationCache.shared();
        final String cached = cache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        final String body = buildBody(text, sourceLang, targetLang);
        return CompletableFuture.supplyAsync(() -> request(body), HttpClientFactory.executor())
                .thenApply(result -> {
                    if (!isFailure(result)) {
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
        return name;
    }

    /** Hand-rolled JSON: same wire shape as before, without building a Gson tree per message. */
    private String buildBody(String text, String sourceLang, String targetLang) {
        String safeText = text == null ? "" : text;
        StringBuilder builder = new StringBuilder(safeText.length() + 384);
        builder.append("{\"model\":\"").append(escapedModelId).append("\",\"messages\":[");
        builder.append("{\"role\":\"system\",\"content\":\"").append(ESCAPED_SYSTEM_PROMPT).append("\"},");
        builder.append("{\"role\":\"user\",\"content\":\"Source language: ").append(escape(sourceLang));
        builder.append("\\nTarget language: ").append(escape(LanguageNames.describeWithCode(targetLang)));
        builder.append("\\nText: ").append(escape(safeText)).append("\"}],");
        builder.append("\"temperature\":0}");
        return builder.toString();
    }

    /** Blocking send with bounded retries; runs on the shared translation pool. */
    private String request(String body) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        } catch (RuntimeException e) {
            return "[" + name + " Error: " + e.getMessage() + "]";
        }

        try {
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
                return "[" + name + " Error: HTTP " + status + " " + summarize(response.body()) + "]";
            }
            return "[" + name + " Error: Retry exhausted]";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[" + name + " Error: interrupted]";
        } catch (Exception e) {
            return "[" + name + " Error: " + e.getMessage() + "]";
        }
    }

    /** 429, 408 and 5xx may succeed on a retry; other 4xx (bad key, bad model) never will. */
    private static boolean isRetryable(int status) {
        return status == 429 || status == 408 || status >= 500;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String parseResponse(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            String direct = extractText(parsed);
            if (!direct.isBlank()) {
                return sanitize(direct);
            }
        } catch (Exception ignored) {
        }
        String plain = body == null ? "" : body.trim();
        if (!plain.isBlank() && !plain.startsWith("{") && !plain.startsWith("[")) {
            return sanitize(plain);
        }
        return "[" + name + " Parse Error: " + summarize(body) + "]";
    }

    private String extractText(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        if (element.isJsonArray()) {
            StringBuilder builder = new StringBuilder();
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                String text = extractText(item);
                if (!text.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(text);
                }
            }
            return builder.toString();
        }
        if (!element.isJsonObject()) {
            return "";
        }

        JsonObject object = element.getAsJsonObject();
        // Reasoning models can return chain-of-thought next to the answer; the answer wins.
        String[] directFields = {"content", "text", "output_text", "result", "response", "answer", "translation", "translatedText"};
        for (String field : directFields) {
            if (object.has(field)) {
                String text = extractText(object.get(field));
                if (!text.isBlank()) {
                    return text;
                }
            }
        }

        if (object.has("message")) {
            String text = extractText(object.get("message"));
            if (!text.isBlank()) {
                return text;
            }
        }

        if (object.has("choices")) {
            JsonArray choices = object.getAsJsonArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                String[] choiceFields = {"message", "delta", "text"};
                for (String field : choiceFields) {
                    if (choice.has(field)) {
                        String text = extractText(choice.get(field));
                        if (!text.isBlank()) {
                            return text;
                        }
                    }
                }
            }
        }

        String[] nestedFields = {"data", "output"};
        for (String field : nestedFields) {
            if (object.has(field)) {
                String text = extractText(object.get(field));
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private String sanitize(String content) {
        String value = content == null ? "" : content.trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        String[] prefixes = {"Translation:", "Translated:", "译文：", "翻译：", "번역:", "번역：", "Traduction :", "Übersetzung:"};
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                value = value.substring(prefix.length()).trim();
                break;
            }
        }
        return value;
    }

    /** True when a provider reply is an internal error marker rather than a translation. */
    private static boolean isFailure(String result) {
        return result == null || result.isEmpty() || result.charAt(0) == '[';
    }

    /** Minimal JSON string escaping; avoids a full Gson writer on the hot path. */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    if (c < 0x20) {
                        builder.append("\\u").append(String.format("%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }

    private String summarize(String body) {
        String value = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        if (value.length() > 180) {
            value = value.substring(0, 180) + "...";
        }
        return value;
    }
}
