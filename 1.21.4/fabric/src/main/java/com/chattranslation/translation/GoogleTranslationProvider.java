package com.chattranslation.translation;

import com.chattranslation.ChatTranslationMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class GoogleTranslationProvider implements TranslationProvider {

    private static final String TRANSLATE_URL =
            "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t";

    private final HttpClient httpClient;

    public GoogleTranslationProvider(boolean insecureSsl) {
        this.httpClient = HttpClientFactory.create(insecureSsl);
    }

    @Override
    public CompletableFuture<String> translate(String text, String sourceLang, String targetLang) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String source = normalize(sourceLang);
                String target = normalize(targetLang);
                String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
                String url = TRANSLATE_URL +
                        "&sl=" + source +
                        "&tl=" + target +
                        "&q=" + encodedText;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .header("User-Agent", "Mozilla/5.0")
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseTranslationResponse(response.body());
                }
                return "[Google Free Error: HTTP " + response.statusCode() + "]";
            } catch (Exception e) {
                return "[Google Free Error: " + e.getMessage() + "]";
            }
        });
    }

    @Override
    public CompletableFuture<String> detectLanguage(String text) {
        return CompletableFuture.completedFuture("auto");
    }

    @Override
    public String getName() {
        return "Google Translate Free";
    }

    private String parseTranslationResponse(String jsonResponse) {
        try {
            JsonElement root = JsonParser.parseString(jsonResponse);
            if (root.isJsonArray()) {
                JsonArray rootArray = root.getAsJsonArray();
                if (!rootArray.isEmpty() && rootArray.get(0).isJsonArray()) {
                    JsonArray firstBlock = rootArray.get(0).getAsJsonArray();
                    StringBuilder result = new StringBuilder();
                    for (JsonElement segment : firstBlock) {
                        if (segment.isJsonArray()) {
                            JsonArray segmentArray = segment.getAsJsonArray();
                            if (!segmentArray.isEmpty() && !segmentArray.get(0).isJsonNull()) {
                                result.append(segmentArray.get(0).getAsString());
                            }
                        }
                    }
                    return result.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return "[Google Free Parse Error]";
    }

    private String normalize(String language) {
        if (language == null || language.isBlank()) {
            return "auto";
        }
        return switch (language.toLowerCase()) {
            case "zh-cn", "zh_cn" -> "zh-CN";
            case "zh-tw", "zh_tw" -> "zh-TW";
            case "ja-jp", "ja_jp" -> "ja";
            case "ko-kr", "ko_kr" -> "ko";
            case "fr-fr", "fr_fr" -> "fr";
            case "de-de", "de_de" -> "de";
            default -> language;
        };
    }
}
