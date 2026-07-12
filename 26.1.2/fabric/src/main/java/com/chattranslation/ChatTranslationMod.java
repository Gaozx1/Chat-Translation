package com.chattranslation;

import com.chattranslation.config.ModConfig;
import com.chattranslation.translation.AiTranslationProvider;
import com.chattranslation.translation.AnthropicTranslationProvider;
import com.chattranslation.translation.BingFreeTranslationProvider;
import com.chattranslation.translation.GeminiTranslationProvider;
import com.chattranslation.translation.GoogleTranslationProvider;
import com.chattranslation.translation.LibreTranslateProvider;
import com.chattranslation.translation.LingvaTranslationProvider;
import com.chattranslation.translation.MyMemoryTranslationProvider;
import com.chattranslation.translation.TranslationProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatTranslationMod implements ClientModInitializer {

    public static final String MOD_ID = "chattranslation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])@[A-Za-z0-9_]{3,16}(?![A-Za-z0-9_])");
    private static final Pattern LEGACY_FORMAT_PATTERN = Pattern.compile("(?i)(?:\\u00A7|&)[0-9A-FK-ORX]");
    private static final Pattern BRACKET_TAG_PATTERN = Pattern.compile("\\[[^\\]\\r\\n]{1,32}]");
    private static final Pattern COMMAND_PATTERN = Pattern.compile("(?i)(?<!\\S)/(?:trigger\\s+[A-Za-z0-9_:\\-]+|[A-Za-z0-9_:\\-]+)");
    private static final Pattern CHAT_PREFIX_PATTERN = Pattern.compile("^(.{0,120}?[A-Za-z0-9_]{3,16}(?:(?:\\u00A7|&)[0-9A-FK-ORX])*\\s*[:\\uFF1A]\\s*)\\S+");
    private static final Pattern PLAIN_CHAT_PATTERN = Pattern.compile(".*[A-Za-z0-9_]{3,16}\\s*[:\\uFF1A]\\s*\\S+.*");
    private static final Pattern SENDER_PREFIX_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16})(?=\\s*[:\\uFF1A])");
    private static final Pattern PLAYER_STATUS_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16})(?=\\s+(?:joined|left)\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_BEFORE_CJK_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16})(?=\\p{IsHan})");
    private static final Pattern PLACEHOLDER_VARIANT_PATTERN = Pattern.compile("(?i)(?<![A-Za-z0-9])[_\\-\\s]*CT\\s*(\\d+)[_\\-\\s]*(?![A-Za-z0-9])");
    private static final Pattern LATIN_WORD_PATTERN = Pattern.compile("[A-Za-z]{2,}");

    private static ModConfig config;
    private static TranslationProvider translationProvider;
    private static final Set<String> translatingMessages = ConcurrentHashMap.newKeySet();
    private static final int MAX_CHAT_MESSAGE_LENGTH = 256;
    private static final Object OUTGOING_TRANSLATION_LOCK = new Object();
    private static CompletableFuture<Void> outgoingTranslationQueue = CompletableFuture.completedFuture(null);
    private static boolean bypassOutgoingTranslation;
    private static final long OUTGOING_ECHO_TTL_MILLIS = 15_000L;
    private static final ConcurrentLinkedDeque<RecentOutgoingMessage> recentOutgoingMessages = new ConcurrentLinkedDeque<>();

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ChatTranslation] Initializing Chat Translation Mod (Client)...");

        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
        config = ModConfig.load(configPath);
        LOGGER.info("[ChatTranslation][debug:init] configPath={}, service={}, target={}, translateAllMessages={}",
                configPath, config.getTranslationService(), config.getTargetLanguage(), config.isTranslateAllMessages());

        initTranslationProvider();

        ClientReceiveMessageEvents.ALLOW_CHAT.register(this::allowChatMessage);
        ClientReceiveMessageEvents.ALLOW_GAME.register(this::allowGameMessage);
        ClientReceiveMessageEvents.CHAT.register(this::onChatMessage);
        ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
        ClientSendMessageEvents.ALLOW_CHAT.register(this::allowOutgoingChatMessage);
        LOGGER.info("[ChatTranslation][debug:init] registered receive and outgoing chat events");

        LOGGER.info("[ChatTranslation] Client mod initialized! Service: {}, Target: {}",
                config.getTranslationService(), config.getTargetLanguage());
    }

    private void initTranslationProvider() {
        reloadProviderInternal();
    }

    private static void reloadProviderInternal() {
        String service = config.getTranslationService() == null ? "google_free" : config.getTranslationService().toLowerCase();
        LOGGER.info("[ChatTranslation][debug:provider] requestedService={}", service);
        switch (service) {
            case "google", "google_free", "google_translate" -> translationProvider =
                    withMyMemoryFallback(new GoogleTranslationProvider(config.isInsecureSsl()));
            case "bing_free" -> translationProvider =
                    withMyMemoryFallback(new BingFreeTranslationProvider(config.isInsecureSsl()));
            case "mymemory" -> translationProvider =
                    new MyMemoryTranslationProvider(config.isInsecureSsl());
            case "lingva" -> translationProvider =
                    withMyMemoryFallback(new LingvaTranslationProvider(config.getLingvaApiUrl(), config.isInsecureSsl()));
            case "libretranslate" -> {
                if (config.getLibreTranslateApiUrl().isBlank()) {
                    LOGGER.warn("[ChatTranslation] LibreTranslate API URL not set, falling back to MyMemory");
                    translationProvider = new MyMemoryTranslationProvider(config.isInsecureSsl());
                } else {
                    translationProvider = withMyMemoryFallback(new LibreTranslateProvider(
                            config.getLibreTranslateApiUrl(),
                            config.getLibreTranslateApiKey(),
                            config.isInsecureSsl()));
                }
            }
            case "ai" -> translationProvider = withMyMemoryFallback(createAiProvider());
            default -> {
                LOGGER.warn("[ChatTranslation] Unsupported translation service '{}', falling back to MyMemory", service);
                translationProvider = new MyMemoryTranslationProvider(config.isInsecureSsl());
            }
        }
        LOGGER.info("[ChatTranslation][debug:provider] using provider={}", translationProvider.getName());
    }

    private static TranslationProvider withMyMemoryFallback(TranslationProvider primary) {
        return new TranslationProvider() {
            private final TranslationProvider fallback = new MyMemoryTranslationProvider(config.isInsecureSsl());

            @Override
            public java.util.concurrent.CompletableFuture<String> translate(String text, String sourceLang, String targetLang) {
                return primary.translate(text, sourceLang, targetLang)
                        .thenCompose(result -> {
                            if (isProviderError(result)) {
                                LOGGER.warn("[ChatTranslation][debug:provider-fallback] primary={} failed={}, fallback={}",
                                        primary.getName(), result, fallback.getName());
                                return fallback.translate(text, sourceLang, targetLang);
                            }
                            return java.util.concurrent.CompletableFuture.completedFuture(result);
                        });
            }

            @Override
            public java.util.concurrent.CompletableFuture<String> detectLanguage(String text) {
                return primary.detectLanguage(text);
            }

            @Override
            public String getName() {
                return primary.getName() + " + MyMemory fallback";
            }
        };
    }

    private static boolean isProviderError(String result) {
        if (result == null || result.isBlank() || result.startsWith("[")) {
            return true;
        }
        String normalized = result.trim().toLowerCase(java.util.Locale.ROOT);
        return (normalized.contains("select two")
                && normalized.contains("language")
                && (normalized.contains("distinct") || normalized.contains("different")))
                || normalized.contains("请选择两种不同的语言")
                || normalized.contains("请选择两种不同语言");
    }

    private static TranslationProvider createAiProvider() {
        String format = config.getAiFormat() == null ? "openai_compatible" : config.getAiFormat().toLowerCase();
        return switch (format) {
            case "gemini" -> new GeminiTranslationProvider(
                    config.getAiApiUrl(),
                    config.getAiApiKey(),
                    config.isInsecureSsl());
            case "anthropic" -> new AnthropicTranslationProvider(
                    config.getAiApiUrl(),
                    config.getAiApiKey(),
                    config.getAiModelId(),
                    config.isInsecureSsl());
            case "openai_compatible", "openai" -> new AiTranslationProvider(
                    config.getAiApiUrl(),
                    config.getAiApiKey(),
                    config.getAiModelId(),
                    config.isInsecureSsl(),
                    "AI Translator");
            default -> new AiTranslationProvider(
                    config.getAiApiUrl(),
                    config.getAiApiKey(),
                    config.getAiModelId(),
                    config.isInsecureSsl(),
                    "AI Translator");
        };
    }

    private boolean allowChatMessage(Component message, net.minecraft.network.chat.PlayerChatMessage signedMessage,
                                     @org.jetbrains.annotations.Nullable com.mojang.authlib.GameProfile sender,
                                     net.minecraft.network.chat.ChatType.Bound params,
                                     Instant receptionTimestamp) {
        if (isLocalPlayer(sender)) {
            consumeRecentOutgoingEcho(message);
            LOGGER.info("[ChatTranslation][debug:allow-chat] skipping translation for local player message='{}'",
                    message.getString());
            return true;
        }
        if (config.isShowOriginal()) {
            return true;
        }
        LOGGER.info("[ChatTranslation][debug:allow-chat] message='{}', senderPresent={}, timestamp={}",
                message.getString(), sender != null, receptionTimestamp);
        return !queueTranslation(message, sender, false);
    }

    private boolean allowGameMessage(Component message, boolean overlay) {
        if (overlay || config.isShowOriginal()) {
            return true;
        }
        if (consumeRecentOutgoingEcho(message)) {
            LOGGER.info("[ChatTranslation][debug:allow-game] skipping translation for outgoing echo='{}'",
                    message.getString());
            return true;
        }
        if (!shouldProcessGameMessage(message)) {
            return true;
        }
        LOGGER.info("[ChatTranslation][debug:allow-game] overlay={}, message='{}'", overlay, message.getString());
        return !queueTranslation(message, null, false);
    }

    private void onChatMessage(Component message, net.minecraft.network.chat.PlayerChatMessage signedMessage,
                               @org.jetbrains.annotations.Nullable com.mojang.authlib.GameProfile sender,
                               net.minecraft.network.chat.ChatType.Bound params,
                               Instant receptionTimestamp) {
        if (!config.isShowOriginal() || isLocalPlayer(sender)) {
            return;
        }
        LOGGER.info("[ChatTranslation][debug:event-chat] message='{}', senderPresent={}, timestamp={}",
                message.getString(), sender != null, receptionTimestamp);
        queueTranslation(message, sender, false);
    }

    private boolean isLocalPlayer(GameProfile sender) {
        if (sender == null) {
            return false;
        }
        GameProfile localProfile = Minecraft.getInstance().getGameProfile();
        return localProfile != null && java.util.Objects.equals(sender.id(), localProfile.id());
    }

    private void onGameMessage(Component message, boolean overlay) {
        if (!config.isShowOriginal()) {
            return;
        }
        if (!overlay && consumeRecentOutgoingEcho(message)) {
            LOGGER.info("[ChatTranslation][debug:event-game] skipping translation for outgoing echo='{}'",
                    message.getString());
            return;
        }
        LOGGER.info("[ChatTranslation][debug:event-game] overlay={}, translateAllMessages={}, message='{}'",
                overlay, config.isTranslateAllMessages(), message.getString());
        if (!overlay && shouldProcessGameMessage(message)) {
            queueTranslation(message, null, false);
        }
    }

    private boolean allowOutgoingChatMessage(String message) {
        if (bypassOutgoingTranslation) {
            return true;
        }
        if (!config.isTranslateOutgoingMessages()
                || message == null
                || message.isBlank()
                || message.startsWith("/")
                || message.length() > MAX_CHAT_MESSAGE_LENGTH
                || translationProvider == null) {
            return true;
        }

        ProtectedMessage protectedMessage = protectMessage(Component.literal(message), Minecraft.getInstance().getGameProfile());
        if (!hasLettersOrDigits(stripPlaceholders(protectedMessage.textForTranslation(), protectedMessage.tokens()))) {
            return true;
        }

        queueOutgoingTranslation(message, protectedMessage, config.getOutgoingTargetLanguage());
        return false;
    }

    private void queueOutgoingTranslation(String originalMessage, ProtectedMessage protectedMessage, String targetLanguage) {
        synchronized (OUTGOING_TRANSLATION_LOCK) {
            outgoingTranslationQueue = outgoingTranslationQueue
                    .handle((ignored, throwable) -> null)
                    .thenCompose(ignored -> translateAndSendOutgoingMessage(originalMessage, protectedMessage, targetLanguage));
        }
    }

    private CompletableFuture<Void> translateAndSendOutgoingMessage(String originalMessage,
                                                                     ProtectedMessage protectedMessage,
                                                                     String targetLanguage) {
        LOGGER.info("[ChatTranslation][debug:outgoing-start] source=auto, target={}, provider={}, text='{}'",
                targetLanguage, translationProvider.getName(), originalMessage);
        CompletableFuture<String> translationFuture;
        try {
            translationFuture = translationProvider.translate(protectedMessage.textForTranslation(), "auto", targetLanguage);
        } catch (RuntimeException e) {
            LOGGER.warn("[ChatTranslation][debug:outgoing-error] provider failed before starting", e);
            sendOutgoingChatMessage(originalMessage);
            return CompletableFuture.completedFuture(null);
        }
        return translationFuture
                .handle((translatedText, throwable) -> {
                    String messageToSend = originalMessage;
                    try {
                        if (throwable != null) {
                            LOGGER.warn("[ChatTranslation][debug:outgoing-error] original='{}', error={}",
                                    originalMessage, throwable.getMessage());
                        } else if (!isProviderError(translatedText)) {
                            String restored = restoreProtectedMessage(protectedMessage, translatedText).getString();
                            if (isValidOutgoingMessage(restored)) {
                                messageToSend = restored;
                            } else {
                                LOGGER.warn("[ChatTranslation][debug:outgoing-fallback] invalid translated message, length={}",
                                        restored.length());
                            }
                        } else {
                            LOGGER.warn("[ChatTranslation][debug:outgoing-fallback] provider returned='{}'", translatedText);
                        }
                    } catch (RuntimeException e) {
                        LOGGER.warn("[ChatTranslation][debug:outgoing-fallback] failed to restore translated message", e);
                    }
                    sendOutgoingChatMessage(messageToSend);
                    return null;
                });
    }

    private boolean isValidOutgoingMessage(String message) {
        return message != null
                && !message.isBlank()
                && message.length() <= MAX_CHAT_MESSAGE_LENGTH
                && message.indexOf('\n') < 0
                && message.indexOf('\r') < 0;
    }

    private void sendOutgoingChatMessage(String message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            ClientPacketListener connection = client.getConnection();
            if (connection == null) {
                LOGGER.warn("[ChatTranslation][debug:outgoing-drop] connection is no longer available");
                return;
            }
            bypassOutgoingTranslation = true;
            try {
                connection.sendChat(message);
                rememberOutgoingMessage(message);
                LOGGER.info("[ChatTranslation][debug:outgoing-send] text='{}'", message);
            } finally {
                bypassOutgoingTranslation = false;
            }
        });
    }

    private void rememberOutgoingMessage(String message) {
        long now = System.currentTimeMillis();
        recentOutgoingMessages.removeIf(entry -> now - entry.sentAtMillis() > OUTGOING_ECHO_TTL_MILLIS);
        while (recentOutgoingMessages.size() >= 32) {
            recentOutgoingMessages.pollFirst();
        }
        recentOutgoingMessages.addLast(new RecentOutgoingMessage(message, now));
    }

    private boolean consumeRecentOutgoingEcho(Component component) {
        GameProfile localProfile = Minecraft.getInstance().getGameProfile();
        if (localProfile == null || localProfile.name() == null || localProfile.name().isBlank()) {
            return false;
        }

        String displayedText = stripMinecraftFormatting(component.getString());
        long now = System.currentTimeMillis();
        for (RecentOutgoingMessage entry : recentOutgoingMessages) {
            if (now - entry.sentAtMillis() > OUTGOING_ECHO_TTL_MILLIS) {
                recentOutgoingMessages.remove(entry);
                continue;
            }
            int messageStart = displayedText.lastIndexOf(entry.message());
            int nameStart = displayedText.lastIndexOf(localProfile.name(), messageStart);
            if (messageStart >= 0 && nameStart >= 0 && messageStart - nameStart <= 80) {
                recentOutgoingMessages.remove(entry);
                return true;
            }
        }
        return false;
    }

    private boolean shouldProcessGameMessage(Component message) {
        String text = message.getString();
        if (looksLikePlayerChat(text)) {
            return true;
        }
        return config.isTranslateAllMessages() && isLikelyTranslatableSystemMessage(text);
    }

    private boolean looksLikePlayerChat(String text) {
        String plain = stripMinecraftFormatting(text);
        if (plain.isBlank() || plain.indexOf('\n') >= 0 || plain.indexOf('\r') >= 0 || isCommonSystemPrefix(plain)) {
            return false;
        }
        return CHAT_PREFIX_PATTERN.matcher(text).find()
                || PLAIN_CHAT_PATTERN.matcher(plain).matches();
    }

    private boolean isLikelyTranslatableSystemMessage(String text) {
        String plain = stripMinecraftFormatting(text);
        return !plain.isBlank()
                && plain.indexOf('\n') < 0
                && plain.indexOf('\r') < 0
                && LATIN_WORD_PATTERN.matcher(plain).find()
                && !isCommonSystemPrefix(plain);
    }

    private boolean isCommonSystemPrefix(String plain) {
        String lower = plain.toLowerCase();
        return lower.startsWith("welcome ")
                || lower.startsWith("[tip]")
                || lower.startsWith("tip ")
                || lower.startsWith("use /")
                || lower.contains("资源包")
                || lower.contains("返回大厅")
                || lower.contains("正版账户");
    }

    private boolean queueTranslation(Component message, GameProfile sender, boolean showOriginalOnFailure) {
        String originalText = message.getString();
        LOGGER.info("[ChatTranslation][debug:handle-enter] text='{}', length={}", originalText, originalText.length());
        if (originalText.isEmpty() || originalText.length() > 500) {
            LOGGER.info("[ChatTranslation][debug:handle-skip] reason=empty-or-too-long, length={}", originalText.length());
            return false;
        }

        String targetLang = resolveTargetLanguage();
        if (translationProvider == null) {
            LOGGER.warn("[ChatTranslation][debug:handle-skip] reason=provider-null");
            return false;
        }

        ProtectedMessage protectedMessage = protectMessage(message, sender);
        LOGGER.info("[ChatTranslation][debug:protect] protectedText='{}', tokenCount={}",
                protectedMessage.textForTranslation(), protectedMessage.tokens().size());
        if (!shouldTranslate(protectedMessage, targetLang)) {
            LOGGER.info("[ChatTranslation][debug:handle-skip] reason=no-translatable-content-or-target-language");
            return false;
        }

        String messageKey = (sender == null ? "" : sender.name()) + "\u0000" + originalText;
        if (!translatingMessages.add(messageKey)) {
            LOGGER.info("[ChatTranslation][debug:handle-skip] reason=duplicate, text='{}'", originalText);
            return false;
        }

        LOGGER.info("[ChatTranslation][debug:translate-start] source=auto, target={}, provider={}, text='{}'",
                targetLang, translationProvider.getName(), originalText);

        translationProvider.translate(protectedMessage.textForTranslation(), "auto", targetLang)
                .whenComplete((translatedText, throwable) -> {
                    translatingMessages.remove(messageKey);
                    if (throwable != null) {
                        LOGGER.warn("[ChatTranslation][debug:translate-error] original='{}', error={}",
                                originalText, throwable.getMessage());
                        if (showOriginalOnFailure) {
                            showMessage(message);
                        }
                        return;
                    }
                    LOGGER.info("[ChatTranslation][debug:translate-result] original='{}', translated='{}'",
                            originalText, translatedText);
                    if (translatedText == null || translatedText.isBlank() || translatedText.startsWith("[")) {
                        LOGGER.info("[ChatTranslation][debug:translate-fallback] reason=provider-error, translated='{}'",
                                translatedText);
                        return;
                    }
                    MutableComponent restoredTranslation = restoreProtectedMessage(protectedMessage, translatedText);
                    if (!restoredTranslation.getString().equals(originalText)) {
                        Minecraft client = Minecraft.getInstance();
                        client.execute(() -> {
                            Component translationMessage = formatTranslationMessage(originalText, restoredTranslation);
                            LOGGER.info("[ChatTranslation][debug:hud-add] message='{}'", translationMessage.getString());
                            showMessage(translationMessage);
                        });
                    } else {
                        LOGGER.info("[ChatTranslation][debug:translate-skip] reason=same-as-original, text='{}'", originalText);
                        if (showOriginalOnFailure) {
                            showMessage(message);
                        }
                    }
                });
        return true;
    }

    private void showMessage(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null) {
            client.gui.getChat().addClientSystemMessage(message);
        } else {
            LOGGER.info("[ChatTranslation][debug:hud-skip] reason=gui-null");
        }
    }

    private Component formatTranslationMessage(String original, Component translated) {
        if (config.isSingleLineDisplay() || !config.isShowOriginal()) {
            return translated;
        }

        String format = config.getTranslationFormat();
        int messageIndex = format.indexOf("{message}");
        if (messageIndex < 0) {
            return Component.literal(normalizeMinecraftFormatting(format.replace("{original}", original)));
        }

        MutableComponent result = Component.literal(normalizeMinecraftFormatting(format.substring(0, messageIndex)
                .replace("{original}", original)));
        result.append(translated);
        String suffix = format.substring(messageIndex + "{message}".length()).replace("{original}", original);
        if (!suffix.isEmpty()) {
            result.append(Component.literal(normalizeMinecraftFormatting(suffix)));
        }
        return result;
    }

    public static String stripMinecraftFormatting(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String sectionSign = Character.toString((char) 0x00A7);
        return input.replaceAll("(?i)" + sectionSign + "[0-9A-FK-ORX]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeMinecraftFormatting(String input) {
        return input.replace('&', (char) 0x00A7);
    }

    private ProtectedMessage protectMessage(Component message, GameProfile sender) {
        String text = message.getString();
        List<ProtectedToken> tokens = new ArrayList<>();
        collectChatPrefixToken(text, tokens);
        collectStyledTokens(message, tokens);
        collectPatternTokens(text, URL_PATTERN, TokenKind.URL, tokens);
        collectPatternTokens(text, BRACKET_TAG_PATTERN, TokenKind.TEXT, tokens);
        collectPatternTokens(text, COMMAND_PATTERN, TokenKind.TEXT, tokens);
        collectPatternTokens(text, LEGACY_FORMAT_PATTERN, TokenKind.FORMAT_CODE, tokens);
        collectPatternTokens(text, MENTION_PATTERN, TokenKind.TEXT, tokens);
        collectContextualPlayerNameTokens(text, tokens);
        collectPlayerNameTokens(text, sender, tokens);
        tokens.sort(Comparator.comparingInt(ProtectedToken::start)
                .thenComparing((ProtectedToken token) -> -token.length()));

        List<ProtectedToken> selected = new ArrayList<>();
        int lastEnd = -1;
        int index = 0;
        StringBuilder builder = new StringBuilder(text.length());
        for (ProtectedToken token : tokens) {
            if (token.start() < lastEnd || token.start() < 0 || token.end() > text.length()) {
                continue;
            }
            builder.append(text, lastEnd < 0 ? 0 : lastEnd, token.start());
            String placeholder = "__CT" + index++ + "__";
            selected.add(token.withPlaceholder(placeholder));
            builder.append(placeholder);
            lastEnd = token.end();
        }
        if (lastEnd < 0) {
            builder.append(text);
        } else {
            builder.append(text.substring(lastEnd));
        }
        return new ProtectedMessage(builder.toString(), selected, findPreferredTextStyle(message));
    }

    private void collectStyledTokens(Component component, List<ProtectedToken> tokens) {
        int[] cursor = {0};
        component.visit((style, text) -> {
            if (!text.isEmpty() && shouldProtectStyle(style)) {
                TokenKind kind = style.getClickEvent() instanceof ClickEvent.OpenUrl ? TokenKind.URL : TokenKind.STYLED;
                tokens.add(new ProtectedToken(cursor[0], cursor[0] + text.length(), text, style, kind, null));
            }
            cursor[0] += text.length();
            return java.util.Optional.empty();
        }, Style.EMPTY);
    }

    private boolean shouldProtectStyle(Style style) {
        return style != null && (style.getClickEvent() != null || style.getHoverEvent() != null || style.isObfuscated());
    }

    private Style findPreferredTextStyle(Component component) {
        Style[] preferred = {component.getStyle() == null ? Style.EMPTY : component.getStyle()};
        component.visit((style, text) -> {
            if (!text.isBlank() && style != null && hasVisibleTextStyle(style)) {
                preferred[0] = stripInteractiveStyle(style);
                return java.util.Optional.of(Boolean.TRUE);
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return stripInteractiveStyle(preferred[0]);
    }

    private boolean hasVisibleTextStyle(Style style) {
        return style.getColor() != null
                || style.isBold()
                || style.isItalic()
                || style.isUnderlined()
                || style.isStrikethrough();
    }

    private Style stripInteractiveStyle(Style style) {
        if (style == null) {
            return Style.EMPTY;
        }
        return style.withClickEvent(null).withHoverEvent(null).withInsertion(null);
    }

    private void collectPatternTokens(String text, Pattern pattern, TokenKind kind, List<ProtectedToken> tokens) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            tokens.add(new ProtectedToken(matcher.start(), matcher.end(), value, Style.EMPTY, kind, null));
        }
    }

    private void collectChatPrefixToken(String text, List<ProtectedToken> tokens) {
        Matcher matcher = CHAT_PREFIX_PATTERN.matcher(text);
        if (matcher.find()) {
            tokens.add(new ProtectedToken(matcher.start(1), matcher.end(1), matcher.group(1), Style.EMPTY, TokenKind.TEXT, null));
        }
    }

    private void collectPlayerNameTokens(String text, GameProfile sender, List<ProtectedToken> tokens) {
        Set<String> names = ConcurrentHashMap.newKeySet();
        if (sender != null && sender.name() != null && !sender.name().isBlank()) {
            names.add(sender.name());
        }
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener connection = client.getConnection();
        if (connection != null) {
            for (PlayerInfo playerInfo : connection.getOnlinePlayers()) {
                String name = playerInfo.getProfile().name();
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
        for (String name : names) {
            Matcher matcher = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])").matcher(text);
            while (matcher.find()) {
                tokens.add(new ProtectedToken(matcher.start(), matcher.end(), matcher.group(), Style.EMPTY, TokenKind.TEXT, null));
            }
        }
    }

    private void collectContextualPlayerNameTokens(String text, List<ProtectedToken> tokens) {
        collectGroupTokens(text, SENDER_PREFIX_PATTERN, tokens);
        collectGroupTokens(text, PLAYER_STATUS_PATTERN, tokens);
        if (looksLikeChineseStatusMessage(text)) {
            collectGroupTokens(text, PLAYER_BEFORE_CJK_PATTERN, tokens);
        }
    }

    private boolean looksLikeChineseStatusMessage(String text) {
        return text.contains("\u8fdb\u5165")
                || text.contains("\u52a0\u5165")
                || text.contains("\u79bb\u5f00")
                || text.contains("\u9000\u51fa");
    }

    private void collectGroupTokens(String text, Pattern pattern, List<ProtectedToken> tokens) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int start = matcher.start(1);
            int end = matcher.end(1);
            tokens.add(new ProtectedToken(start, end, matcher.group(1), Style.EMPTY, TokenKind.TEXT, null));
        }
    }

    private boolean shouldTranslate(ProtectedMessage protectedMessage, String targetLang) {
        String translatable = stripPlaceholders(protectedMessage.textForTranslation(), protectedMessage.tokens());
        if (!hasLettersOrDigits(translatable)) {
            return false;
        }
        return !isLikelyAlreadyTargetLanguage(translatable, targetLang);
    }

    private String stripPlaceholders(String text, List<ProtectedToken> tokens) {
        String result = text == null ? "" : text;
        for (ProtectedToken token : tokens) {
            if (token.placeholder() != null) {
                result = result.replace(token.placeholder(), " ");
            }
        }
        return result.replaceAll("\\s+", " ").trim();
    }

    private boolean hasLettersOrDigits(String text) {
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private boolean isLikelyAlreadyTargetLanguage(String text, String targetLang) {
        String lang = targetLang == null ? "" : targetLang.toLowerCase();
        boolean hasLatin = LATIN_WORD_PATTERN.matcher(text).find();
        if (lang.startsWith("zh")) {
            return containsCodePointInRange(text, Character.UnicodeScript.HAN) && !hasLatin;
        }
        if (lang.startsWith("ja")) {
            return (containsCodePointInRange(text, Character.UnicodeScript.HAN)
                    || containsCodePointInRange(text, Character.UnicodeScript.HIRAGANA)
                    || containsCodePointInRange(text, Character.UnicodeScript.KATAKANA)) && !hasLatin;
        }
        if (lang.startsWith("ko")) {
            return containsCodePointInRange(text, Character.UnicodeScript.HANGUL) && !hasLatin;
        }
        if (lang.startsWith("ru")) {
            return containsCodePointInRange(text, Character.UnicodeScript.CYRILLIC) && !hasLatin;
        }
        if (lang.startsWith("en")) {
            return hasLatin
                    && !containsCodePointInRange(text, Character.UnicodeScript.HAN)
                    && !containsCodePointInRange(text, Character.UnicodeScript.HIRAGANA)
                    && !containsCodePointInRange(text, Character.UnicodeScript.KATAKANA)
                    && !containsCodePointInRange(text, Character.UnicodeScript.HANGUL)
                    && !containsCodePointInRange(text, Character.UnicodeScript.CYRILLIC);
        }
        return false;
    }

    private boolean containsCodePointInRange(String text, Character.UnicodeScript script) {
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (Character.UnicodeScript.of(codePoint) == script) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private MutableComponent restoreProtectedMessage(ProtectedMessage protectedMessage, String translatedText) {
        MutableComponent result = Component.empty();
        String remaining = translatedText == null ? "" : translatedText;
        int cursor = 0;
        while (cursor < remaining.length()) {
            PlaceholderHit next = findNextPlaceholder(remaining, cursor, protectedMessage.tokens());
            if (next == null) {
                appendLiteralWithLinks(result, remaining.substring(cursor), protectedMessage.textStyle());
                break;
            }
            if (next.start() > cursor) {
                appendLiteralWithLinks(result, remaining.substring(cursor, next.start()), protectedMessage.textStyle());
            }
            result.append(next.token().toComponent());
            cursor = next.end();
        }
        return result;
    }

    private PlaceholderHit findNextPlaceholder(String text, int fromIndex, List<ProtectedToken> tokens) {
        PlaceholderHit hit = null;
        for (ProtectedToken token : tokens) {
            int index = text.indexOf(token.placeholder(), fromIndex);
            if (index >= 0 && (hit == null || index < hit.start())) {
                hit = new PlaceholderHit(index, index + token.placeholder().length(), token);
            }
        }
        Matcher matcher = PLACEHOLDER_VARIANT_PATTERN.matcher(text);
        matcher.region(fromIndex, text.length());
        while (matcher.find()) {
            ProtectedToken token = findTokenByPlaceholderIndex(tokens, matcher.group(1));
            if (token != null && (hit == null || matcher.start() < hit.start())) {
                hit = new PlaceholderHit(matcher.start(), matcher.end(), token);
                break;
            }
        }
        return hit;
    }

    private ProtectedToken findTokenByPlaceholderIndex(List<ProtectedToken> tokens, String indexText) {
        String expected = "__CT" + indexText + "__";
        for (ProtectedToken token : tokens) {
            if (expected.equalsIgnoreCase(token.placeholder())) {
                return token;
            }
        }
        return null;
    }

    private void appendLiteralWithLinks(MutableComponent result, String text, Style textStyle) {
        Matcher matcher = URL_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                result.append(Component.literal(normalizeMinecraftFormatting(text.substring(cursor, matcher.start())))
                        .withStyle(textStyle));
            }
            result.append(createUrlComponent(matcher.group(), textStyle));
            cursor = matcher.end();
        }
        if (cursor < text.length()) {
            result.append(Component.literal(normalizeMinecraftFormatting(text.substring(cursor))).withStyle(textStyle));
        }
    }

    private Component createUrlComponent(String value, Style style) {
        String url = value.startsWith("http://") || value.startsWith("https://") ? value : "https://" + value;
        try {
            return Component.literal(value).withStyle(style.withClickEvent(new ClickEvent.OpenUrl(new URI(url))));
        } catch (URISyntaxException e) {
            return Component.literal(value).withStyle(style);
        }
    }

    private enum TokenKind {
        TEXT,
        URL,
        STYLED,
        FORMAT_CODE
    }

    private record ProtectedMessage(String textForTranslation, List<ProtectedToken> tokens, Style textStyle) {
    }

    private record PlaceholderHit(int start, int end, ProtectedToken token) {
    }

    private record RecentOutgoingMessage(String message, long sentAtMillis) {
    }

    private record ProtectedToken(int start, int end, String value, Style style, TokenKind kind, String placeholder) {
        int length() {
            return end - start;
        }

        ProtectedToken withPlaceholder(String placeholder) {
            return new ProtectedToken(start, end, value, style, kind, placeholder);
        }

        Component toComponent() {
            if (kind == TokenKind.URL) {
                return createUrlComponentStatic(value, style);
            }
            return Component.literal(value).withStyle(style == null ? Style.EMPTY : style);
        }

        private static Component createUrlComponentStatic(String value, Style style) {
            Style effectiveStyle = style == null ? Style.EMPTY : style;
            if (effectiveStyle.getClickEvent() != null) {
                return Component.literal(value).withStyle(effectiveStyle);
            }
            String url = value.startsWith("http://") || value.startsWith("https://") ? value : "https://" + value;
            try {
                return Component.literal(value).withStyle(effectiveStyle.withClickEvent(new ClickEvent.OpenUrl(new URI(url))));
            } catch (URISyntaxException e) {
                return Component.literal(value).withStyle(effectiveStyle);
            }
        }
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
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
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
