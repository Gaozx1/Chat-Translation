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

/**
 * Bing 翻译服务实现 (使用 Microsoft Azure Translator API)
 * 需要在配置中设置 API Key 和 Region
 */
public class BingTranslationProvider implements TranslationProvider {

    private static final String TRANSLATE_URL = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String region;

    public BingTranslationProvider(String apiKey, String region, boolean insecureSsl) {
        this.httpClient = HttpClientFactory.create(insecureSsl);
        this.apiKey = apiKey;
        this.region = region;
    }

    @Override
    public CompletableFuture<String> translate(String text, String sourceLang, String targetLang) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = TRANSLATE_URL +
                        "&from=" + sourceLang +
                        "&to=" + targetLang;

                String body = "[{\"Text\":\"" + escapeJson(text) + "\"}]";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Ocp-Apim-Subscription-Key", apiKey)
                        .header("Ocp-Apim-Subscription-Region", region)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseBingResponse(response.body());
                } else {
                    return "[Bing Error: HTTP " + response.statusCode() + "]";
                }
            } catch (Exception e) {
                return "[Bing Error: " + e.getMessage() + "]";
            }
        });
    }

    @Override
    public CompletableFuture<String> detectLanguage(String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String detectUrl = "https://api.cognitive.microsofttranslator.com/detect?api-version=3.0";
                String body = "[{\"Text\":\"" + escapeJson(text.substring(0, Math.min(text.length(), 100))) + "\"}]";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(detectUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Ocp-Apim-Subscription-Key", apiKey)
                        .header("Ocp-Apim-Subscription-Region", region)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonElement root = JsonParser.parseString(response.body());
                    if (root.isJsonArray() && !root.getAsJsonArray().isEmpty()) {
                        JsonObject firstResult = root.getAsJsonArray().get(0).getAsJsonObject();
                        return firstResult.get("language").getAsString();
                    }
                }
            } catch (Exception ignored) {
            }
            return "unknown";
        });
    }

    @Override
    public String getName() {
        return "Bing Translator";
    }

    private String parseBingResponse(String jsonResponse) {
        try {
            JsonElement root = JsonParser.parseString(jsonResponse);
            if (root.isJsonArray() && !root.getAsJsonArray().isEmpty()) {
                JsonObject firstResult = root.getAsJsonArray().get(0).getAsJsonObject();
                JsonArray translations = firstResult.getAsJsonArray("translations");
                if (translations != null && !translations.isEmpty()) {
                    return translations.get(0).getAsJsonObject().get("text").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return "[Bing Parse Error]";
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
