package com.chattranslation.config;

import com.chattranslation.ChatTranslationMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class ModConfig {

    private static final int CURRENT_CONFIG_VERSION = 3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> FREE_SERVICES = Set.of(
            "google_free",
            "bing_free",
            "mymemory",
            "lingva",
            "libretranslate",
            "ai"
    );

    @SerializedName("target_language")
    private String targetLanguage = "auto";

    @SerializedName("config_version")
    private int configVersion = CURRENT_CONFIG_VERSION;

    @SerializedName("translation_service")
    private String translationService = "google_free";

    @SerializedName("show_original")
    private boolean showOriginal = false;

    @SerializedName("translation_format")
    private String translationFormat = "{message}";

    @SerializedName("single_line_display")
    private boolean singleLineDisplay = true;

    @SerializedName("translate_all_messages")
    private boolean translateAllMessages = false;

    @SerializedName("translate_outgoing_messages")
    private boolean translateOutgoingMessages = false;

    @SerializedName("outgoing_target_language")
    private String outgoingTargetLanguage = "en";

    @SerializedName("supported_languages")
    private String[] supportedLanguages = {
            "auto", "zh-CN", "zh-TW", "en", "ja", "ko", "fr", "de",
            "es", "pt", "ru", "ar", "hi", "th", "vi", "id", "it", "nl", "pl", "tr"
    };

    @SerializedName("insecure_ssl")
    private boolean insecureSsl = true;

    @SerializedName("ai_api_url")
    private String aiApiUrl = "";

    @SerializedName("ai_api_key")
    private String aiApiKey = "";

    @SerializedName("ai_model_id")
    private String aiModelId = "";

    @SerializedName("ai_format")
    private String aiFormat = "openai_compatible";

    @SerializedName("lingva_api_url")
    private String lingvaApiUrl = "https://lingva.ml/api/v1";

    @SerializedName("libretranslate_api_url")
    private String libreTranslateApiUrl = "";

    @SerializedName("libretranslate_api_key")
    private String libreTranslateApiKey = "";

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

    public boolean isSingleLineDisplay() {
        return singleLineDisplay;
    }

    public void setSingleLineDisplay(boolean singleLineDisplay) {
        this.singleLineDisplay = singleLineDisplay;
    }

    public boolean isTranslateAllMessages() {
        return translateAllMessages;
    }

    public void setTranslateAllMessages(boolean translateAllMessages) {
        this.translateAllMessages = translateAllMessages;
    }

    public boolean isTranslateOutgoingMessages() {
        return translateOutgoingMessages;
    }

    public void setTranslateOutgoingMessages(boolean translateOutgoingMessages) {
        this.translateOutgoingMessages = translateOutgoingMessages;
    }

    public String getOutgoingTargetLanguage() {
        return outgoingTargetLanguage;
    }

    public void setOutgoingTargetLanguage(String outgoingTargetLanguage) {
        this.outgoingTargetLanguage = outgoingTargetLanguage;
    }

    public String[] getSupportedLanguages() {
        return supportedLanguages;
    }

    public boolean isInsecureSsl() {
        return insecureSsl;
    }

    public void setInsecureSsl(boolean insecureSsl) {
        this.insecureSsl = insecureSsl;
    }

    public String getAiApiUrl() {
        return aiApiUrl;
    }

    public void setAiApiUrl(String aiApiUrl) {
        this.aiApiUrl = aiApiUrl;
    }

    public String getAiApiKey() {
        return aiApiKey;
    }

    public void setAiApiKey(String aiApiKey) {
        this.aiApiKey = aiApiKey;
    }

    public String getAiModelId() {
        return aiModelId;
    }

    public void setAiModelId(String aiModelId) {
        this.aiModelId = aiModelId;
    }

    public String getAiFormat() {
        return aiFormat;
    }

    public void setAiFormat(String aiFormat) {
        this.aiFormat = aiFormat;
    }

    public String getLingvaApiUrl() {
        return lingvaApiUrl;
    }

    public void setLingvaApiUrl(String lingvaApiUrl) {
        this.lingvaApiUrl = lingvaApiUrl;
    }

    public String getLibreTranslateApiUrl() {
        return libreTranslateApiUrl;
    }

    public void setLibreTranslateApiUrl(String libreTranslateApiUrl) {
        this.libreTranslateApiUrl = libreTranslateApiUrl;
    }

    public String getLibreTranslateApiKey() {
        return libreTranslateApiKey;
    }

    public void setLibreTranslateApiKey(String libreTranslateApiKey) {
        this.libreTranslateApiKey = libreTranslateApiKey;
    }

    public static ModConfig load(Path configPath) {
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                ModConfig config = GSON.fromJson(json, ModConfig.class);
                if (config == null) {
                    throw new JsonParseException("Config is empty");
                }
                boolean migrated = config.migrateLegacyDefaults();
                config.normalize();
                if (migrated) {
                    config.save(configPath);
                }
                ChatTranslationMod.LOGGER.info("[ChatTranslation][debug:config-load] path={}, service={}, target={}, singleLine={}, showOriginal={}",
                        configPath, config.translationService, config.targetLanguage, config.singleLineDisplay, config.showOriginal);
                return config;
            } catch (IOException | JsonParseException | IllegalStateException e) {
                ChatTranslationMod.LOGGER.warn("[ChatTranslation] Failed to load config, using defaults: {}", e.getMessage());
            }
        }
        ModConfig defaultConfig = new ModConfig();
        defaultConfig.save(configPath);
        return defaultConfig;
    }

    public void save(Path configPath) {
        try {
            normalize();
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(this));
            ChatTranslationMod.LOGGER.info("[ChatTranslation][debug:config-save] path={}, service={}, target={}, singleLine={}, showOriginal={}",
                    configPath, translationService, targetLanguage, singleLineDisplay, showOriginal);
        } catch (IOException e) {
            ChatTranslationMod.LOGGER.warn("[ChatTranslation] Failed to save config: {}", e.getMessage());
        }
    }

    private void normalize() {
        if (targetLanguage == null || targetLanguage.isBlank()) {
            targetLanguage = "auto";
        }
        if (outgoingTargetLanguage == null || outgoingTargetLanguage.isBlank() || "auto".equalsIgnoreCase(outgoingTargetLanguage)) {
            outgoingTargetLanguage = "en";
        }
        if (configVersion <= 0 || configVersion > CURRENT_CONFIG_VERSION) {
            configVersion = CURRENT_CONFIG_VERSION;
        }
        if (translationService == null || !FREE_SERVICES.contains(translationService.toLowerCase())) {
            translationService = "google_free";
        } else {
            translationService = translationService.toLowerCase();
        }
        if (translationFormat == null || translationFormat.isBlank()) {
            translationFormat = "{message}";
        }
        if (supportedLanguages == null || supportedLanguages.length == 0) {
            supportedLanguages = new String[]{
                    "auto", "zh-CN", "zh-TW", "en", "ja", "ko", "fr", "de",
                    "es", "pt", "ru", "ar", "hi", "th", "vi", "id", "it", "nl", "pl", "tr"
            };
        }
        if (aiApiUrl == null) aiApiUrl = "";
        if (aiApiKey == null) aiApiKey = "";
        if (aiModelId == null) aiModelId = "";
        if (aiFormat == null || aiFormat.isBlank()) aiFormat = "openai_compatible";
        if (lingvaApiUrl == null || lingvaApiUrl.isBlank()) lingvaApiUrl = "https://lingva.ml/api/v1";
        if (libreTranslateApiUrl == null) libreTranslateApiUrl = "";
        if (libreTranslateApiKey == null) libreTranslateApiKey = "";
    }

    private boolean migrateLegacyDefaults() {
        if (configVersion >= CURRENT_CONFIG_VERSION) {
            return false;
        }
        if (configVersion < 2) {
            showOriginal = false;
            translateAllMessages = false;
            translationFormat = "{message}";
            singleLineDisplay = true;
        }
        translateOutgoingMessages = false;
        outgoingTargetLanguage = "en";
        configVersion = CURRENT_CONFIG_VERSION;
        return true;
    }
}
