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
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final int MAX_PENDING_TRANSLATIONS = 64;
    private static final Object TRANSLATION_QUEUE_LOCK = new Object();
    private static final AtomicInteger pendingTranslations = new AtomicInteger();
    private static CompletableFuture<Void> translationQueue = CompletableFuture.completedFuture(null);

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
        LOGGER.info("[ChatTranslation][debug:init] registered allow/chat/game receive events");

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
                    new LingvaTranslationProvider(config.getLingvaApiUrl(), config.isInsecureSsl());
            case "libretranslate" -> {
                if (config.getLibreTranslateApiUrl().isBlank()) {
                    LOGGER.warn("[ChatTranslation] LibreTranslate API URL not set, falling back to MyMemory");
                    translationProvider = new MyMemoryTranslationProvider(config.isInsecureSsl());
                } else {
                    translationProvider = new LibreTranslateProvider(
                            config.getLibreTranslateApiUrl(),
                            config.getLibreTranslateApiKey(),
                            config.isInsecureSsl());
                }
            }
            case "ai" -> translationProvider = createAiProvider();
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
            public CompletableFuture<String> translate(String text, String sourceLang, String targetLang) {
                return primary.translate(text, sourceLang, targetLang)
                        .handle((result, throwable) -> {
                            if (throwable != null || isProviderError(result)) {
                                LOGGER.warn("[ChatTranslation] {} failed; retrying with {}",
                                        primary.getName(), fallback.getName());
                                return fallback.translate(text, sourceLang, targetLang);
                            }
                            return CompletableFuture.completedFuture(result);
                        })
                        .thenCompose(future -> future);
            }

            @Override
            public CompletableFuture<String> detectLanguage(String text) {
                return primary.detectLanguage(text);
            }

            @Override
            public String getName() {
                return primary.getName() + " + MyMemory fallback";
            }
        };
    }

    private static boolean isProviderError(String result) {
        return result == null || result.isBlank() || result.startsWith("[");
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
        if (config.isShowOriginal()) {
            return true;
        }
        LOGGER.debug("[ChatTranslation] received chat message: length={}, senderPresent={}, timestamp={}",
                message.getString().length(), sender != null, receptionTimestamp);
        return !queueTranslation(message, sender, true);
    }

    private boolean allowGameMessage(Component message, boolean overlay) {
        if (overlay || config.isShowOriginal() || !shouldProcessGameMessage(message)) {
            return true;
        }
        LOGGER.debug("[ChatTranslation] received game message: overlay={}, length={}", overlay, message.getString().length());
        return !queueTranslation(message, null, true);
    }

    private void onChatMessage(Component message, net.minecraft.network.chat.PlayerChatMessage signedMessage,
                               @org.jetbrains.annotations.Nullable com.mojang.authlib.GameProfile sender,
                               net.minecraft.network.chat.ChatType.Bound params,
                               Instant receptionTimestamp) {
        if (!config.isShowOriginal()) {
            return;
        }
        LOGGER.debug("[ChatTranslation] translating displayed chat message: length={}, senderPresent={}, timestamp={}",
                message.getString().length(), sender != null, receptionTimestamp);
        queueTranslation(message, sender, false);
    }

    private void onGameMessage(Component message, boolean overlay) {
        if (!config.isShowOriginal()) {
            return;
        }
        LOGGER.debug("[ChatTranslation] translating displayed game message: overlay={}, translateAllMessages={}, length={}",
                overlay, config.isTranslateAllMessages(), message.getString().length());
        if (!overlay && shouldProcessGameMessage(message)) {
            queueTranslation(message, null, false);
        }
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
        LOGGER.debug("[ChatTranslation] considering message for translation: length={}", originalText.length());
        if (originalText.isEmpty() || originalText.length() > 500) {
            LOGGER.debug("[ChatTranslation] skipped empty or oversized message: length={}", originalText.length());
            return false;
        }

        String targetLang = resolveTargetLanguage();
        if (translationProvider == null) {
            LOGGER.warn("[ChatTranslation] skipped translation because provider is unavailable");
            return false;
        }

        ProtectedMessage protectedMessage = protectMessage(message, sender);
        LOGGER.debug("[ChatTranslation] protected message tokens: count={}", protectedMessage.tokens().size());
        if (!shouldTranslate(protectedMessage, targetLang)) {
            LOGGER.debug("[ChatTranslation] skipped message without translatable content");
            return false;
        }

        String messageKey = (sender == null ? "" : sender.name()) + "\u0000" + originalText;
        if (!translatingMessages.add(messageKey)) {
            LOGGER.debug("[ChatTranslation] skipped duplicate in-flight message");
            return false;
        }

        if (pendingTranslations.incrementAndGet() > MAX_PENDING_TRANSLATIONS) {
            pendingTranslations.decrementAndGet();
            translatingMessages.remove(messageKey);
            LOGGER.warn("[ChatTranslation] translation queue is full; keeping the original message");
            return false;
        }

        TranslationProvider provider = translationProvider;
        LOGGER.debug("[ChatTranslation] queued translation: target={}, provider={}, pending={}",
                targetLang, provider.getName(), pendingTranslations.get());

        synchronized (TRANSLATION_QUEUE_LOCK) {
            translationQueue = translationQueue
                    .handle((ignored, previousError) -> null)
                    .thenCompose(ignored -> translateQueued(
                            provider, message, protectedMessage, originalText, targetLang, showOriginalOnFailure))
                    .whenComplete((ignored, throwable) -> {
                        translatingMessages.remove(messageKey);
                        pendingTranslations.decrementAndGet();
                    });
        }
        return true;
    }

    private CompletableFuture<Void> translateQueued(TranslationProvider provider, Component originalMessage,
                                                     ProtectedMessage protectedMessage, String originalText,
                                                     String targetLang, boolean showOriginalOnFailure) {
        try {
            return provider.translate(protectedMessage.textForTranslation(), "auto", targetLang)
                    .handle((translatedText, throwable) -> {
                        Minecraft.getInstance().execute(() -> handleTranslationResult(
                                originalMessage, protectedMessage, originalText, translatedText,
                                throwable, provider, showOriginalOnFailure));
                        return null;
                    });
        } catch (Throwable throwable) {
            Minecraft.getInstance().execute(() -> handleTranslationResult(
                    originalMessage, protectedMessage, originalText, null,
                    throwable, provider, showOriginalOnFailure));
            return CompletableFuture.completedFuture(null);
        }
    }

    private void handleTranslationResult(Component originalMessage, ProtectedMessage protectedMessage,
                                         String originalText, String translatedText, Throwable throwable,
                                         TranslationProvider provider, boolean showOriginalOnFailure) {
        if (throwable != null) {
            LOGGER.warn("[ChatTranslation] {} translation failed: {}", provider.getName(), throwable.getMessage());
            showOriginalAfterFailure(originalMessage, showOriginalOnFailure);
            return;
        }
        if (isProviderError(translatedText)) {
            LOGGER.warn("[ChatTranslation] {} returned an error: {}", provider.getName(), translatedText);
            showOriginalAfterFailure(originalMessage, showOriginalOnFailure);
            return;
        }
        if (!containsAllPlaceholders(translatedText, protectedMessage.tokens())) {
            LOGGER.warn("[ChatTranslation] {} changed protected message tokens; keeping the original message",
                    provider.getName());
            showOriginalAfterFailure(originalMessage, showOriginalOnFailure);
            return;
        }

        MutableComponent restoredTranslation = restoreProtectedMessage(protectedMessage, translatedText);
        if (restoredTranslation.getString().equals(originalText)) {
            LOGGER.debug("[ChatTranslation] provider returned the original text");
            showOriginalAfterFailure(originalMessage, showOriginalOnFailure);
            return;
        }

        Component translationMessage = formatTranslationMessage(originalText, restoredTranslation);
        showMessage(translationMessage);
    }

    private void showOriginalAfterFailure(Component originalMessage, boolean showOriginalOnFailure) {
        if (showOriginalOnFailure) {
            showMessage(originalMessage);
        }
    }

    private void showMessage(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null) {
            client.gui.hud.getChat().addClientSystemMessage(message);
        } else {
            LOGGER.debug("[ChatTranslation] skipped HUD update because GUI is unavailable");
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
        return hasLettersOrDigits(translatable);
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

    private boolean containsAllPlaceholders(String translatedText, List<ProtectedToken> tokens) {
        for (ProtectedToken token : tokens) {
            if (token.placeholder() == null) {
                continue;
            }
            if (translatedText.contains(token.placeholder())) {
                continue;
            }
            String placeholderIndex = token.placeholder().replaceAll("\\D", "");
            Matcher matcher = PLACEHOLDER_VARIANT_PATTERN.matcher(translatedText);
            boolean found = false;
            while (matcher.find()) {
                if (placeholderIndex.equals(matcher.group(1))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
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
                result.append(Component.literal(text.substring(cursor, matcher.start()))
                        .withStyle(textStyle));
            }
            result.append(createUrlComponent(matcher.group(), textStyle));
            cursor = matcher.end();
        }
        if (cursor < text.length()) {
            result.append(Component.literal(text.substring(cursor)).withStyle(textStyle));
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

}
