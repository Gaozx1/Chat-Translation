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
import java.util.concurrent.CompletableFuture;

public class CaiyunTranslationProvider implements TranslationProvider {

    private static final String TRANSLATE_URL = "https://api.interpreter.caiyunai.com/v1/translator";

    private final HttpClient httpClient;
    private final String token;

    public CaiyunTranslationProvider(String token, boolean insecureSsl) {
        this.httpClient = HttpClientFactory.create(insecureSsl);
        this.token = token;
    }

    @Override
    public CompletableFuture<String> translate(String text, String sourceLang, String targetLang) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String direction = buildDirection(sourceLang, targetLang);
                String body = "{\"source\":[\"" + escapeJson(text) + "\"],\"trans_type\":\"" + direction + "\",\"request_id\":\"chattranslation\",\"detect\":true}";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(TRANSLATE_URL))
                        .header("Content-Type", "application/json")
                        .header("X-Authorization", "token " + token)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseResponse(response.body());
                }
                return "[Caiyun Error: HTTP " + response.statusCode() + "]";
            } catch (Exception e) {
                return "[Caiyun Error: " + e.getMessage() + "]";
            }
        });
    }

    @Override
    public CompletableFuture<String> detectLanguage(String text) {
        return CompletableFuture.completedFuture("auto");
    }

    @Override
    public String getName() {
        return "Caiyun Translator";
    }

    private String parseResponse(String jsonResponse) {
        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonElement target = root.get("target");
            if (target == null) {
                return "[Caiyun Parse Error]";
            }
            if (target.isJsonArray()) {
                JsonArray array = target.getAsJsonArray();
                return array.isEmpty() ? "[Caiyun Parse Error]" : array.get(0).getAsString();
            }
            return target.getAsString();
        } catch (Exception e) {
            return "[Caiyun Parse Error: " + e.getMessage() + "]";
        }
    }

    private String buildDirection(String sourceLang, String targetLang) {
        String source = normalize(sourceLang);
        String target = normalize(targetLang);
        if ("auto".equals(source)) {
            source = "auto";
        }
        return source + "2" + target;
    }

    private String normalize(String language) {
        if (language == null) {
            return "auto";
        }
        return switch (language.toLowerCase()) {
            case "zh-cn", "zh_cn", "zh" -> "zh";
            case "zh-tw", "zh_tw" -> "zh";
            case "en" -> "en";
            case "ja" -> "ja";
            case "ko" -> "ko";
            case "fr" -> "fr";
            case "de" -> "de";
            case "es" -> "es";
            case "ru" -> "ru";
            default -> language.toLowerCase();
        };
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
