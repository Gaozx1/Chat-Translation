package com.chattranslation.translation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class LingvaTranslationProvider implements TranslationProvider {

    private static final String DEFAULT_API_URL = "https://lingva.ml/api/v1";

    private final HttpClient httpClient;
    private final String apiUrl;

    public LingvaTranslationProvider(String apiUrl, boolean insecureSsl) {
        this.httpClient = HttpClientFactory.create(insecureSsl);
        String configuredUrl = apiUrl == null || apiUrl.isBlank() ? DEFAULT_API_URL : apiUrl.trim();
        this.apiUrl = configuredUrl.endsWith("/") ? configuredUrl.substring(0, configuredUrl.length() - 1) : configuredUrl;
    }

    @Override
    public CompletableFuture<String> translate(String text, String sourceLang, String targetLang) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String source = normalize(sourceLang);
                String target = normalize(targetLang);
                String cleanText = text;
                String url = apiUrl + "/" + encodePath(source) + "/" + encodePath(target) + "/" + encodePath(cleanText);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(12))
                        .GET()
                        .header("Accept", "application/json")
                        .header("User-Agent", "Mozilla/5.0")
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseResponse(response.body());
                }
                return "[Lingva Error: HTTP " + response.statusCode() + "]";
            } catch (Exception e) {
                return "[Lingva Error: " + e.getMessage() + "]";
            }
        });
    }

    @Override
    public CompletableFuture<String> detectLanguage(String text) {
        return CompletableFuture.completedFuture("auto");
    }

    @Override
    public String getName() {
        return "Lingva Translate";
    }

    private String parseResponse(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("translation")) {
                return root.get("translation").getAsString();
            }
        } catch (Exception ignored) {
        }
        return "[Lingva Parse Error]";
    }

    private String normalize(String language) {
        if (language == null || language.isBlank()) {
            return "auto";
        }
        return switch (language.toLowerCase()) {
            case "zh-cn", "zh_cn" -> "zh";
            case "zh-tw", "zh_tw" -> "zh-TW";
            case "ja-jp", "ja_jp" -> "ja";
            case "ko-kr", "ko_kr" -> "ko";
            case "fr-fr", "fr_fr" -> "fr";
            case "de-de", "de_de" -> "de";
            default -> language;
        };
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
