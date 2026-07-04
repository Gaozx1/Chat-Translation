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

public class BingFreeTranslationProvider implements TranslationProvider {

    private static final String API_URL = "https://api.bing-translate-api.dev/translate";

    private final HttpClient httpClient;

    public BingFreeTranslationProvider(boolean insecureSsl) {
        this.httpClient = HttpClientFactory.create(insecureSsl);
    }

    @Override
    public CompletableFuture<String> translate(String text, String sourceLang, String targetLang) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("text", text);
                body.addProperty("from", "auto".equalsIgnoreCase(sourceLang) ? "auto-detect" : sourceLang);
                body.addProperty("to", targetLang);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseResponse(response.body());
                }
                return "[Bing Free Error: HTTP " + response.statusCode() + "]";
            } catch (Exception e) {
                return "[Bing Free Error: " + e.getMessage() + "]";
            }
        });
    }

    @Override
    public CompletableFuture<String> detectLanguage(String text) {
        return CompletableFuture.completedFuture("auto");
    }

    @Override
    public String getName() {
        return "Bing Free";
    }

    private String parseResponse(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("translation")) {
                return root.get("translation").getAsString();
            }
            if (root.has("translations")) {
                JsonArray translations = root.getAsJsonArray("translations");
                if (!translations.isEmpty()) {
                    JsonObject first = translations.get(0).getAsJsonObject();
                    if (first.has("text")) {
                        return first.get("text").getAsString();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "[Bing Free Parse Error]";
    }
}
