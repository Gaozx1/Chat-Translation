package com.chattranslation;

import com.chattranslation.config.ModConfig;
import com.chattranslation.translation.BingTranslationProvider;
import com.chattranslation.translation.CaiyunTranslationProvider;
import com.chattranslation.translation.GoogleTranslationProvider;
import com.chattranslation.translation.GoogleProxyTranslationProvider;
import com.chattranslation.translation.TranslationProvider;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.GuiMessageSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod(value = ChatTranslationMod.MOD_ID, dist = Dist.CLIENT)
public class ChatTranslationMod {

    public static final String MOD_ID = "chattranslation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ModConfig config;
    private static TranslationProvider translationProvider;
    private static final Set<String> translatingMessages = ConcurrentHashMap.newKeySet();

    public ChatTranslationMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[ChatTranslation] Initializing Chat Translation Mod (Client)...");

        // 加载配置
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(MOD_ID + ".json");
        config = ModConfig.load(configPath);

        // 初始化翻译服务
        initTranslationProvider();

        // 注册客户端事件
        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("[ChatTranslation] Client mod initialized! Service: {}, Target: {}",
                config.getTranslationService(), config.getTargetLanguage());
    }

    private void initTranslationProvider() {
        reloadProviderInternal();
    }

    private static void reloadProviderInternal() {
        String service = config.getTranslationService().toLowerCase();
        switch (service) {
            case "bing":
                if (config.getBingApiKey().isEmpty()) {
                    LOGGER.warn("[ChatTranslation] Bing API key not set, falling back to Google proxy");
                    translationProvider = new GoogleProxyTranslationProvider(config.getGoogleProxyUrl(), config.isInsecureSsl());
                } else {
                    translationProvider = new BingTranslationProvider(
                            config.getBingApiKey(), config.getBingRegion(), config.isInsecureSsl());
                }
                break;
            case "caiyun":
                translationProvider = new CaiyunTranslationProvider(config.getCaiyunToken(), config.isInsecureSsl());
                break;
            case "google_proxy":
                translationProvider = new GoogleProxyTranslationProvider(config.getGoogleProxyUrl(), config.isInsecureSsl());
                break;
            case "google":
            default:
                translationProvider = new GoogleTranslationProvider(config.isInsecureSsl());
                break;
        }
    }

    @SubscribeEvent
    public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("chattranslation")
                .then(Commands.literal("reload")
                        .executes(context -> {
                            reloadProvider();
                            context.getSource().sendSuccess(() -> Component.literal("ChatTranslation reloaded: " + translationProvider.getName()), false);
                            return 1;
                        }))
                .then(Commands.literal("test")
                        .executes(context -> {
                            String result = testCurrentProviderConnection();
                            context.getSource().sendSuccess(() -> Component.literal(result), false);
                            return 1;
                        })));
    }

    /**
     * 处理客户端收到的聊天消息
     */
    @SubscribeEvent
    public void onClientChatReceived(ClientChatReceivedEvent event) {
        Component message = event.getMessage();
        if (message == null) return;

        String originalText = message.getString();
        if (originalText.isEmpty() || originalText.length() > 500) {
            return;
        }

        // 根据配置决定是否处理系统消息
        // 在 NeoForge 1.21.1 中，ClientChatReceivedEvent 没有 getType()
        // 所有聊天消息（包括系统和玩家消息）都通过此事件
        // 可以通过检查消息格式来判断是否为系统消息
        String msgString = message.toString();
        boolean isSystemMessage = msgString.contains("text") 
                && (msgString.contains("system") || msgString.contains("death")
                    || msgString.contains("join") || msgString.contains("leave")
                    || msgString.contains("achievement"));
        if (isSystemMessage && !config.isTranslateAllMessages()) {
            return;
        }

        if (!translatingMessages.add(originalText)) {
            return;
        }

        String targetLang = resolveTargetLanguage();

        translationProvider.translate(originalText, "auto", targetLang)
                .thenAccept(translatedText -> {
                    translatingMessages.remove(originalText);
                    if (translatedText == null || translatedText.startsWith("[")) {
                        Minecraft.getInstance().execute(() -> {
                            Component fallbackMessage = formatFallbackMessage(originalText, translatedText);
                            if (Minecraft.getInstance().gui != null) {
                                Minecraft.getInstance().gui.getChat().addMessage(fallbackMessage, (MessageSignature) null, GuiMessageSource.SYSTEM, GuiMessageTag.system());
                            }
                        });
                        return;
                    }
                    if (!translatedText.equals(originalText)) {
                        Minecraft.getInstance().execute(() -> {
                            Component translationMessage = formatTranslationMessage(
                                    originalText, translatedText);
                            if (Minecraft.getInstance().gui != null) {
                                Minecraft.getInstance().gui.getChat()
                                        .addMessage(translationMessage, (MessageSignature) null, GuiMessageSource.SYSTEM, GuiMessageTag.system());
                            }
                        });
                    }
                });
    }

    private Component formatTranslationMessage(String original, String translated) {
        String format = config.getTranslationFormat();
        String formatted = normalizeMinecraftFormatting(format.replace("{message}", translated)
                .replace("{original}", original));
        return Component.literal(formatted);
    }

    private Component formatFallbackMessage(String original, String error) {
        return Component.literal(original + " " + error);
    }

    private String normalizeMinecraftFormatting(String input) {
        return input.replace('&', '§');
    }

    private String resolveTargetLanguage() {
        String configLang = config.getTargetLanguage();
        if (!"auto".equals(configLang)) {
            return configLang;
        }
        String mcLang = Minecraft.getInstance().getLanguageManager().getSelected();
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
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(MOD_ID + ".json");
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
