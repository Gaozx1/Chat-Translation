package com.chattranslation.translation;

import java.util.Map;

/**
 * Maps BCP-47 style language codes to English names.
 *
 * <p>Small models follow "translate into Korean" far more reliably than a bare {@code ko} code,
 * which measurably reduces wrong-language replies and therefore wasted round trips. Unknown codes
 * fall through unchanged, so custom codes still behave exactly as before.
 */
public final class LanguageNames {

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("zh-cn", "Simplified Chinese"),
            Map.entry("zh-tw", "Traditional Chinese"),
            Map.entry("zh", "Chinese"),
            Map.entry("en", "English"),
            Map.entry("ja", "Japanese"),
            Map.entry("ko", "Korean"),
            Map.entry("fr", "French"),
            Map.entry("de", "German"),
            Map.entry("es", "Spanish"),
            Map.entry("pt", "Portuguese"),
            Map.entry("ru", "Russian"),
            Map.entry("ar", "Arabic"),
            Map.entry("hi", "Hindi"),
            Map.entry("th", "Thai"),
            Map.entry("vi", "Vietnamese"),
            Map.entry("id", "Indonesian"),
            Map.entry("it", "Italian"),
            Map.entry("nl", "Dutch"),
            Map.entry("pl", "Polish"),
            Map.entry("tr", "Turkish"),
            Map.entry("uk", "Ukrainian"),
            Map.entry("cs", "Czech"),
            Map.entry("sv", "Swedish"),
            Map.entry("da", "Danish"),
            Map.entry("fi", "Finnish"),
            Map.entry("no", "Norwegian"),
            Map.entry("el", "Greek"),
            Map.entry("he", "Hebrew"),
            Map.entry("fa", "Persian"),
            Map.entry("ro", "Romanian"),
            Map.entry("hu", "Hungarian")
    );

    private LanguageNames() {
    }

    /** Returns "Korean" for "ko", "ko-KR" or "KO_KR"; echoes the input when unknown. */
    public static String describe(String code) {
        if (code == null || code.isBlank()) {
            return "English";
        }
        String normalized = code.trim().toLowerCase().replace('_', '-');
        String name = NAMES.get(normalized);
        if (name != null) {
            return name;
        }
        int dash = normalized.indexOf('-');
        if (dash > 0) {
            name = NAMES.get(normalized.substring(0, dash));
            if (name != null) {
                return name;
            }
        }
        return code;
    }

    /** Human readable target used in the prompt, e.g. {@code Simplified Chinese (zh-CN)}. */
    public static String describeWithCode(String code) {
        String name = describe(code);
        if (name.equals(code)) {
            return code;
        }
        return name + " (" + code + ")";
    }
}
