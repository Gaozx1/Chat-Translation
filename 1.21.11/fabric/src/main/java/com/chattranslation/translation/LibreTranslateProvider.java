package com.chattranslation.translation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class LibreTranslateProvider implements TranslationProvider {

    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;

    public LibreTranslateProvider(String apiUrl, String apiKey, boolean insecureSsl) {
        this.httpClient = HttpClientFactory.create(insecureSsl);
        this.apiUrl = apiUrl;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public CompletableFuture<String> translate(String text, String sourceLang, String targetLang) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String cleanText = text;
                JsonObject body = new JsonObject();
                body.addProperty("q", cleanText);
                body.addProperty("source", normalize(sourceLang));
                body.addProperty("target", normalize(targetLang));
                body.addProperty("format", "text");
                if (!apiKey.isBlank()) {
                    body.addProperty("api_key", apiKey);
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseResponse(response.body());
                }
                if (response.statusCode() == 429) {
                    return "[LibreTranslate Error: Rate limited (HTTP 429)]";
                }
                return "[LibreTranslate Error: HTTP " + response.statusCode() + "]";
            } catch (Exception e) {
                return "[LibreTranslate Error: " + e.getMessage() + "]";
            }
        });
    }

    @Override
    public CompletableFuture<String> detectLanguage(String text) {
        return CompletableFuture.completedFuture("auto");
    }

    @Override
    public String getName() {
        return "LibreTranslate";
    }

    private String parseResponse(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("translatedText")) {
                return root.get("translatedText").getAsString();
            }
        } catch (Exception e) {
        }
        return "[LibreTranslate Parse Error]";
    }

    private String normalize(String language) {
        if (language == null || language.isBlank()) {
            return "auto";
        }
        return switch (language.toLowerCase()) {
            case "zh-cn", "zh_cn" -> "zh";
            case "zh-tw", "zh_tw" -> "zt";
            case "ja-jp", "ja_jp" -> "ja";
            case "ko-kr", "ko_kr" -> "ko";
            case "fr-fr", "fr_fr" -> "fr";
            case "de-de", "de_de" -> "de";
            default -> language;
        };
    }
}
