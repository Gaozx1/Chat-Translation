package com.chattranslation.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod 配置管理
 */
public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("target_language")
    private String targetLanguage = "auto";

    @SerializedName("translation_service")
    private String translationService = "google_proxy";

    @SerializedName("bing_api_key")
    private String bingApiKey = "";

    @SerializedName("bing_region")
    private String bingRegion = "global";

    @SerializedName("show_original")
    private boolean showOriginal = true;

    @SerializedName("translation_format")
    private String translationFormat = "&r[&6译&r] {message}";

    @SerializedName("translate_all_messages")
    private boolean translateAllMessages = true;

    @SerializedName("supported_languages")
    private String[] supportedLanguages = {
            "auto", "zh-CN", "zh-TW", "en", "ja", "ko", "fr", "de",
            "es", "pt", "ru", "ar", "hi", "th", "vi", "id", "it", "nl", "pl", "tr"
    };

    @SerializedName("google_proxy_url")
    private String googleProxyUrl = "https://translate-pa.googleapis.com/v1/translateHtml";

    @SerializedName("caiyun_token")
    private String caiyunToken = "3975l6lr5pcbvidl6jl2";

    @SerializedName("insecure_ssl")
    private boolean insecureSsl = true;

    // ===== Getters and Setters =====

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public void setTargetLanguage(String targetLanguage) {
        this.targetLanguage = targetLanguage;
    }

    public String getTranslationService() {
        return translationService;
    }

    public void setTranslationService(String translationService) {
        this.translationService = translationService;
    }

    public String getGoogleProxyUrl() {
        return googleProxyUrl;
    }

    public void setGoogleProxyUrl(String googleProxyUrl) {
        this.googleProxyUrl = googleProxyUrl;
    }

    public String getBingApiKey() {
        return bingApiKey;
    }

    public void setBingApiKey(String bingApiKey) {
        this.bingApiKey = bingApiKey;
    }

    public String getCaiyunToken() {
        return caiyunToken;
    }

    public void setCaiyunToken(String caiyunToken) {
        this.caiyunToken = caiyunToken;
    }

    public boolean isInsecureSsl() {
        return insecureSsl;
    }

    public void setInsecureSsl(boolean insecureSsl) {
        this.insecureSsl = insecureSsl;
    }

    public String getBingRegion() {
        return bingRegion;
    }

    public void setBingRegion(String bingRegion) {
        this.bingRegion = bingRegion;
    }

    public boolean isShowOriginal() {
        return showOriginal;
    }

    public void setShowOriginal(boolean showOriginal) {
        this.showOriginal = showOriginal;
    }

    public String getTranslationFormat() {
        return translationFormat;
    }

    public void setTranslationFormat(String translationFormat) {
        this.translationFormat = translationFormat;
    }

    public boolean isTranslateAllMessages() {
        return translateAllMessages;
    }

    public void setTranslateAllMessages(boolean translateAllMessages) {
        this.translateAllMessages = translateAllMessages;
    }

    public String[] getSupportedLanguages() {
        return supportedLanguages;
    }

    // ===== 序列化方法 =====

    public static ModConfig load(Path configPath) {
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                return GSON.fromJson(json, ModConfig.class);
            } catch (IOException e) {
                System.err.println("[ChatTranslation] Failed to load config: " + e.getMessage());
            }
        }
        // 返回默认配置并保存
        ModConfig defaultConfig = new ModConfig();
        defaultConfig.save(configPath);
        return defaultConfig;
    }

    public void save(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[ChatTranslation] Failed to save config: " + e.getMessage());
        }
    }
}
