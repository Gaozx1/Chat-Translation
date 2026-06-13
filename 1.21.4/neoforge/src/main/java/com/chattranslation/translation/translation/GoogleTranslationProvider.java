package com.chattranslation.translation;

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

/**
 * Google 翻译服务实现 (使用免费翻译API)
 */
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
                String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
                String url = TRANSLATE_URL +
                        "&sl=" + sourceLang +
                        "&tl=" + targetLang +
                        "&q=" + encodedText;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .header("User-Agent", "Mozilla/5.0")
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseTranslationResponse(response.body());
                } else {
                    return "[Translation Error: HTTP " + response.statusCode() + "]";
                }
            } catch (Exception e) {
                return "[Translation Error: " + e.getMessage() + "]";
            }
        });
    }

    @Override
    public CompletableFuture<String> detectLanguage(String text) {
        // Google Translate API 的自动检测功能在翻译时设置 sl=auto 即可,
        // 这里通过简短翻译来检测语言
        return translate(text.substring(0, Math.min(text.length(), 50)), "auto", "en")
                .thenApply(result -> "auto");
    }

    @Override
    public String getName() {
        return "Google Translate";
    }

    private String parseTranslationResponse(String jsonResponse) {
        try {
            JsonElement root = JsonParser.parseString(jsonResponse);
            if (root.isJsonArray()) {
                JsonArray rootArray = root.getAsJsonArray();
                if (!rootArray.isEmpty()) {
                    JsonArray firstBlock = rootArray.get(0).getAsJsonArray();
                    StringBuilder result = new StringBuilder();
                    for (JsonElement segment : firstBlock) {
                        if (segment.isJsonArray()) {
                            JsonArray segArray = segment.getAsJsonArray();
                            if (!segArray.isEmpty()) {
                                result.append(segArray.get(0).getAsString());
                            }
                        }
                    }
                    return result.toString();
                }
            }
        } catch (Exception e) {
            // 解析失败返回原始错误
        }
        return "[Parse Error]";
    }
}
