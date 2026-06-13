package com.chattranslation.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class GoogleProxyTranslationProvider implements TranslationProvider {

    private final HttpClient httpClient;
    private final String endpoint;

    public GoogleProxyTranslationProvider(String endpoint, boolean insecureSsl) {
        this.httpClient = HttpClientFactory.create(insecureSsl);
        this.endpoint = endpoint;
    }

    @Override
    public CompletableFuture<String> translate(String text, String sourceLang, String targetLang) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String normalizedSource = "auto".equalsIgnoreCase(sourceLang) ? "auto" : sourceLang;
                String body = "[[\"" + escapeJson(text) + "\",\"" + normalizedSource + "\",\"" + targetLang + "\",true],[1],\"te\"]";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json+protobuf")
                        .header("User-Agent", "Mozilla/5.0")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseResponse(response.body());
                }
                return "[Google Proxy Error: HTTP " + response.statusCode() + "]";
            } catch (Exception e) {
                return "[Google Proxy Error: " + e.getMessage() + "]";
            }
        });
    }

    @Override
    public CompletableFuture<String> detectLanguage(String text) {
        return CompletableFuture.completedFuture("auto");
    }

    @Override
    public String getName() {
        return "Google Translate Proxy";
    }

    private String parseResponse(String jsonResponse) {
        try {
            JsonElement root = JsonParser.parseString(jsonResponse);
            if (!root.isJsonArray()) {
                return "[Google Proxy Parse Error]";
            }
            JsonArray rootArray = root.getAsJsonArray();
            if (rootArray.isEmpty()) {
                return "[Google Proxy Parse Error]";
            }
            JsonArray translations = rootArray.get(0).getAsJsonArray();
            if (translations.isEmpty()) {
                return "[Google Proxy Parse Error]";
            }
            return translations.get(0).getAsString();
        } catch (Exception e) {
            return "[Google Proxy Parse Error: " + e.getMessage() + "]";
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
