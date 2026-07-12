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

public class MyMemoryTranslationProvider implements TranslationProvider {

    private static final String API_URL = "https://api.mymemory.translated.net/get";

    private final HttpClient httpClient;

    public MyMemoryTranslationProvider(boolean insecureSsl) {
        this.httpClient = HttpClientFactory.create(insecureSsl);
    }

    @Override
    public CompletableFuture<String> translate(String text, String sourceLang, String targetLang) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String cleanText = text;
                String source = normalize(sourceLang);
                String target = normalize(targetLang);
                String query = "q=" + URLEncoder.encode(cleanText, StandardCharsets.UTF_8)
                        + "&langpair=" + URLEncoder.encode(source + "|" + target, StandardCharsets.UTF_8);
                String url = API_URL + "?" + query;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .header("User-Agent", "Mozilla/5.0")
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseResponse(response.body());
                }
                return "[MyMemory Error: HTTP " + response.statusCode() + "]";
            } catch (Exception e) {
                return "[MyMemory Error: " + e.getMessage() + "]";
            }
        });
    }

    @Override
    public CompletableFuture<String> detectLanguage(String text) {
        return CompletableFuture.completedFuture("auto");
    }

    @Override
    public String getName() {
        return "MyMemory API";
    }

    private String parseResponse(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("responseStatus") && !"200".equals(root.get("responseStatus").getAsString())) {
                String details = root.has("responseDetails") && !root.get("responseDetails").isJsonNull()
                        ? root.get("responseDetails").getAsString()
                        : root.get("responseStatus").getAsString();
                return "[MyMemory Error: " + details + "]";
            }
            JsonObject responseData = root.getAsJsonObject("responseData");
            if (responseData != null
                    && responseData.has("translatedText")
                    && !responseData.get("translatedText").isJsonNull()) {
                String translatedText = responseData.get("translatedText").getAsString();
                if (!translatedText.isBlank()) {
                    return translatedText;
                }
            }
        } catch (Exception e) {
        }
        return "[MyMemory Parse Error]";
    }

    private String normalize(String language) {
        if (language == null || language.isBlank() || "auto".equalsIgnoreCase(language)) {
            return "Autodetect";
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
}
