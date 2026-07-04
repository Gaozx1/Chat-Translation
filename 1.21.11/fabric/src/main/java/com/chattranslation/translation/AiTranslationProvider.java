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

    private static final int MAX_ATTEMPTS = 3;

    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;
    private final String modelId;
    private final String name;

    public AiTranslationProvider(String apiUrl, String apiKey, String modelId, boolean insecureSsl, String name) {
        this.httpClient = HttpClientFactory.create(insecureSsl);
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.modelId = modelId;
        this.name = name;
    }

    @Override
    public CompletableFuture<String> translate(String text, String sourceLang, String targetLang) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (apiUrl == null || apiUrl.isBlank()) {
                    return "[" + name + " Error: API URL not set]";
                }
                if (apiKey == null || apiKey.isBlank()) {
                    return "[" + name + " Error: API key not set]";
                }
                if (modelId == null || modelId.isBlank()) {
                    return "[" + name + " Error: Model ID not set]";
                }

                JsonObject body = new JsonObject();
                body.addProperty("model", modelId);
                JsonArray messages = new JsonArray();

                JsonObject system = new JsonObject();
                system.addProperty("role", "system");
                system.addProperty("content", "You are a Minecraft chat translation engine. Translate into the requested target language and return only the translated result. Do not explain. Do not add labels, quotes, notes, markdown, romanization, or extra punctuation. Preserve usernames, ranks, bracket tags, URLs, numbers, and game-specific tokens. If the text should not be translated or no translation is needed, return an empty string.");
                messages.add(system);

                JsonObject user = new JsonObject();
                user.addProperty("role", "user");
                user.addProperty("content", "Source language: " + sourceLang + "\nTarget language: " + targetLang + "\nText: " + text);
                messages.add(user);

                body.add("messages", messages);
                body.addProperty("temperature", 0);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                        .build();

                for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    String responseBody = response.body();
                    if (response.statusCode() == 200) {
                        return parseResponse(responseBody);
                    }
                    if (attempt == MAX_ATTEMPTS) {
                        return "[" + name + " Error: HTTP " + response.statusCode() + " " + summarize(responseBody) + "]";
                    }
                }
                return "[" + name + " Error: Retry exhausted]";
            } catch (Exception e) {
                return "[" + name + " Error: " + e.getMessage() + "]";
            }
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

    private String summarize(String body) {
        String value = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        if (value.length() > 180) {
            value = value.substring(0, 180) + "...";
        }
        return value;
    }
}
