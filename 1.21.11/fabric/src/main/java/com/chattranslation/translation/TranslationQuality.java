package com.chattranslation.translation;

import java.util.List;
import java.util.Locale;

/**
 * Lightweight, provider-independent validation of a translated line.
 *
 * <p>Small models occasionally answer instead of translating ("This means...", "Sure! Here is
 * ..."), echo the prompt, or return Chinese text when English was requested. Those replies used to
 * reach the chat window verbatim. These checks are cheap heuristics, not a guarantee, but they
 * catch the common cases and let the caller fall back to the original message.
 */
public final class TranslationQuality {

    private static final String[] META_PREFIXES = {
            "sure", "certainly", "here is", "here's", "the translation", "translation:", "translated:",
            "of course", "as an ai", "i cannot", "i can't", "i'm sorry", "sorry,", "note:",
            "译文：", "翻译：", "以下是", "当然", "好的，", "抱歉",
    };

    private TranslationQuality() {
    }

    /**
     * @param translated      provider output
     * @param original        the text that was sent for translation
     * @param targetLang      requested target language code
     * @param placeholderFree whether the output still contained every protected token (checked by caller)
     * @return true when the output looks like a usable translation
     */
    public static boolean looksUsable(String translated, String original, String targetLang,
                                      boolean placeholderFree) {
        if (translated == null || translated.isBlank() || !placeholderFree) {
            return false;
        }
        String value = translated.strip();
        if (value.length() > Math.max(1000, original.length() * 6)) {
            // A "translation" six times longer than the source is prose, not a translation.
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String prefix : META_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return false;
            }
        }
        // Quoted whole-line output is almost always the model wrapping its answer.
        if (value.length() > 1 && (value.startsWith("```") || value.startsWith("\"") && value.endsWith("\""))) {
            return false;
        }
        return !isSameLanguageClearlyWrong(value, targetLang);
    }

    /**
     * Very small script-based guard: catches the obvious "asked for English, got Chinese" case
     * without pulling in a language detector. Deliberately conservative — it only rejects when the
     * target language's script is completely absent.
     */
    private static boolean isSameLanguageClearlyWrong(String value, String targetLang) {
        if (targetLang == null) {
            return false;
        }
        String lang = targetLang.toLowerCase(Locale.ROOT);
        boolean targetIsCjk = lang.startsWith("zh") || lang.startsWith("ja") || lang.startsWith("ko");
        boolean hasCjk = value.codePoints().anyMatch(cp ->
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN
                        || Character.UnicodeScript.of(cp) == Character.UnicodeScript.HIRAGANA
                        || Character.UnicodeScript.of(cp) == Character.UnicodeScript.KATAKANA
                        || Character.UnicodeScript.of(cp) == Character.UnicodeScript.HANGUL);
        boolean hasLatin = value.codePoints().anyMatch(cp -> Character.isLetter(cp)
                && Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN);
        if (targetIsCjk) {
            // A pure-Latin reply for a CJK target is suspicious only when it is long enough to be prose.
            return !hasCjk && hasLatin && value.length() > 40;
        }
        return false;
    }
}
