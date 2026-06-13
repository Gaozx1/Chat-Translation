package com.chattranslation;

import com.chattranslation.config.ModConfig;
import com.chattranslation.translation.BingTranslationProvider;
import com.chattranslation.translation.CaiyunTranslationProvider;
import com.chattranslation.translation.GoogleTranslationProvider;
import com.chattranslation.translation.GoogleProxyTranslationProvider;
import com.chattranslation.translation.TranslationProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatTranslationMod implements ClientModInitializer {

    public static final String MOD_ID = "chattranslation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ModConfig config;
    private static TranslationProvider translationProvider;
    private static final Set<String> translatingMessages = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ChatTranslation] Initializing Chat Translation Mod (Client)...");

        // 加载配置
        Path configPath = FabricLoader.getInstance().getConfigDir()
                .resolve(MOD_ID + ".json");
        config = ModConfig.load(configPath);
        LOGGER.info("[ChatTranslation][debug:init] configPath={}, service={}, target={}, translateAllMessages={}",
                configPath, config.getTranslationService(), config.getTargetLanguage(), config.isTranslateAllMessages());

        // 初始化翻译服务
        initTranslationProvider();

        // 注册客户端聊天消息事件 - 拦截所有聊天消息
        ClientReceiveMessageEvents.CHAT.register(this::onChatMessage);
        ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
        LOGGER.info("[ChatTranslation][debug:init] registered chat/game receive events");

        LOGGER.info("[ChatTranslation] Client mod initialized! Service: {}, Target: {}",
                config.getTranslationService(), config.getTargetLanguage());
    }

    private void initTranslationProvider() {
        reloadProviderInternal();
    }

    private static void reloadProviderInternal() {
        String service = config.getTranslationService().toLowerCase();
        LOGGER.info("[ChatTranslation][debug:provider] requestedService={}", service);
        switch (service) {
            case "bing":
                if (config.getBingApiKey().isEmpty()) {
                    LOGGER.warn("[ChatTranslation] Bing API key not set, falling back to Google proxy");
                    translationProvider = new GoogleProxyTranslationProvider(config.getGoogleProxyUrl(), config.isInsecureSsl());
                    LOGGER.info("[ChatTranslation][debug:provider] using provider={} (fallback)", translationProvider.getName());
                } else {
                    translationProvider = new BingTranslationProvider(
                            config.getBingApiKey(), config.getBingRegion());
                    LOGGER.info("[ChatTranslation][debug:provider] using provider={}", translationProvider.getName());
                }
                break;
            case "caiyun":
                translationProvider = new CaiyunTranslationProvider(config.getCaiyunToken(), config.isInsecureSsl());
                LOGGER.info("[ChatTranslation][debug:provider] using provider={}", translationProvider.getName());
                break;
            case "google_proxy":
                translationProvider = new GoogleProxyTranslationProvider(config.getGoogleProxyUrl(), config.isInsecureSsl());
                LOGGER.info("[ChatTranslation][debug:provider] using provider={}", translationProvider.getName());
                break;
            case "google":
            default:
                translationProvider = new GoogleTranslationProvider(config.isInsecureSsl());
                LOGGER.info("[ChatTranslation][debug:provider] using provider={}", translationProvider.getName());
                break;
        }
    }

    private void onChatMessage(Text message, net.minecraft.network.message.SignedMessage signedMessage,
                                @org.jetbrains.annotations.Nullable com.mojang.authlib.GameProfile sender,
                                net.minecraft.network.message.MessageType.Parameters params,
                                Instant receptionTimestamp) {
        LOGGER.info("[ChatTranslation][debug:event-chat] message='{}', senderPresent={}, timestamp={}",
                message.getString(), sender != null, receptionTimestamp);
        handleMessage(message);
    }

    private void onGameMessage(Text message, boolean overlay) {
        LOGGER.info("[ChatTranslation][debug:event-game] overlay={}, translateAllMessages={}, message='{}'",
                overlay, config.isTranslateAllMessages(), message.getString());
        if (config.isTranslateAllMessages()) {
            handleMessage(message);
        }
    }

    private void handleMessage(Text message) {
        String originalText = message.getString();
        LOGGER.info("[ChatTranslation][debug:handle-enter] text='{}', length={}", originalText, originalText.length());
        if (originalText.isEmpty() || originalText.length() > 500) {
            LOGGER.info("[ChatTranslation][debug:handle-skip] reason=empty-or-too-long, length={}", originalText.length());
            return;
        }

        // 防止重复翻译同一消息
        if (!translatingMessages.add(originalText)) {
            LOGGER.info("[ChatTranslation][debug:handle-skip] reason=duplicate, text='{}'", originalText);
            return;
        }

        String targetLang = resolveTargetLanguage();
        LOGGER.info("[ChatTranslation][debug:translate-start] source=auto, target={}, provider={}, text='{}'",
                targetLang, translationProvider.getName(), originalText);

        translationProvider.translate(originalText, "auto", targetLang)
                .thenAccept(translatedText -> {
                    translatingMessages.remove(originalText);
                    LOGGER.info("[ChatTranslation][debug:translate-result] original='{}', translated='{}'",
                            originalText, translatedText);
                    if (translatedText == null || translatedText.startsWith("[")) {
                        LOGGER.info("[ChatTranslation][debug:translate-fallback] reason=provider-error, translated='{}'",
                                translatedText);
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.execute(() -> {
                            Text fallbackMessage = formatFallbackMessage(originalText, translatedText);
                            LOGGER.info("[ChatTranslation][debug:hud-add-fallback] message='{}'", fallbackMessage.getString());
                            if (client.inGameHud != null) {
                                client.inGameHud.getChatHud().addMessage(fallbackMessage);
                            }
                        });
                        return;
                    }
                    if (!translatedText.equals(originalText)) {
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.execute(() -> {
                            Text translationMessage = formatTranslationMessage(originalText, translatedText);
                            LOGGER.info("[ChatTranslation][debug:hud-add] message='{}'", translationMessage.getString());
                            if (client.inGameHud != null) {
                                client.inGameHud.getChatHud().addMessage(translationMessage);
                            } else {
                                LOGGER.info("[ChatTranslation][debug:hud-skip] reason=inGameHud-null");
                            }
                        });
                    } else {
                        LOGGER.info("[ChatTranslation][debug:translate-skip] reason=same-as-original, text='{}'", originalText);
                    }
                });
    }

    private Text formatTranslationMessage(String original, String translated) {
        String format = config.getTranslationFormat();
        String formatted = normalizeMinecraftFormatting(format.replace("{message}", translated)
                .replace("{original}", original));
        return Text.literal(formatted);
    }

    private Text formatFallbackMessage(String original, String error) {
        String message = (config.isShowOriginal() ? original : original) + " " + error;
        return Text.literal(message).formatted(Formatting.GRAY);
    }

    private String normalizeMinecraftFormatting(String input) {
        return input.replace('&', '§');
    }

    private String resolveTargetLanguage() {
        String configLang = config.getTargetLanguage();
        if (!"auto".equals(configLang)) {
            return configLang;
        }
        // 使用当前客户端的 Minecraft 语言设置
        String mcLang = MinecraftClient.getInstance().getLanguageManager().getLanguage();
        return mapMinecraftLangToCode(mcLang);
    }

    static String mapMinecraftLangToCode(String mcLang) {
        if (mcLang == null) return "en";
        return switch (mcLang.toLowerCase()) {
            case "zh_cn", "zh-cn" -> "zh-CN";
            case "zh_tw", "zh-tw" -> "zh-TW";
            case "ja_jp", "ja-jp" -> "ja";
            case "ko_kr", "ko-kr" -> "ko";
            case "fr_fr", "fr-fr" -> "fr";
            case "de_de", "de-de" -> "de";
            case "es_es", "es-es" -> "es";
            case "pt_br", "pt-br", "pt_pt", "pt-pt" -> "pt";
            case "ru_ru", "ru-ru" -> "ru";
            case "en_us", "en-us", "en_gb", "en-gb" -> "en";
            default -> mcLang.length() >= 2 ? mcLang.substring(0, 2) : mcLang;
        };
    }

    public static void reloadProvider() {
        Path configPath = FabricLoader.getInstance().getConfigDir()
                .resolve(MOD_ID + ".json");
        config = ModConfig.load(configPath);
        reloadProviderInternal();
        LOGGER.info("[ChatTranslation] Provider reloaded: {}", translationProvider.getName());
    }

    public static ModConfig getConfig() {
        return config;
    }

    public static TranslationProvider getTranslationProvider() {
        return translationProvider;
    }

    public static String testCurrentProviderConnection() {
        try {
            String result = translationProvider.translate("Hello world", "en", "zh-CN").get();
            if (result == null || result.isBlank() || result.startsWith("[")) {
                return result == null ? "[Test Error: Empty result]" : result;
            }
            return "[Test Success] " + result;
        } catch (Exception e) {
            return "[Test Error: " + e.getMessage() + "]";
        }
    }
}
