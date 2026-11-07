package com.chattranslation.translation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded LRU cache for finished provider results.
 *
 * <p>Chat traffic repeats heavily (greetings, "gg", server broadcasts, re-sent lines), so a
 * small cache removes a large share of network round trips for every provider, and it is the
 * cheapest win for slow AI endpoints. Entries are keyed by provider, source language, target
 * language and the protected text that was actually sent.
 *
 * <p>Access-ordered {@link LinkedHashMap} keeps the bookkeeping O(1) and the lock is only held
 * for a single map operation, which is negligible next to a network request.
 */
public final class TranslationCache {

    private static final int DEFAULT_CAPACITY = 512;
    private static final char SEPARATOR = '\u0001';

    /** Shared across every provider so a config reload keeps the warm entries. */
    private static final TranslationCache SHARED = new TranslationCache(DEFAULT_CAPACITY);

    private final Map<String, String> entries;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public TranslationCache() {
        this(DEFAULT_CAPACITY);
    }

    /** The process-wide cache used by all providers. */
    public static TranslationCache shared() {
        return SHARED;
    }

    public TranslationCache(int capacity) {
        int bounded = Math.max(16, capacity);
        this.entries = Collections.synchronizedMap(new LinkedHashMap<>(bounded + 1, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > bounded;
            }
        });
    }

    /**
     * Builds a collision-safe key. The separator is a control character that cannot appear in
     * chat text, and the text length prefix prevents two different splits from colliding.
     */
    public static String key(String provider, String sourceLang, String targetLang, String text) {
        return key(provider, sourceLang, targetLang, text, "");
    }

    /**
     * Variant for context-aware requests.
     *
     * <p>{@code contextFingerprint} must capture everything besides the text that can change the
     * model's answer. Without it a line translated in one conversation would be served from cache
     * in a completely different one, which is exactly the bug that makes context features look
     * "randomly wrong". The fingerprint is length-prefixed like the text so it cannot be confused
     * with adjacent fields.
     */
    public static String key(String provider, String sourceLang, String targetLang, String text,
                             String contextFingerprint) {
        String body = text == null ? "" : text;
        String context = contextFingerprint == null ? "" : contextFingerprint;
        return provider + SEPARATOR + sourceLang + SEPARATOR + targetLang + SEPARATOR
                + body.length() + SEPARATOR + body + SEPARATOR
                + context.length() + SEPARATOR + context;
    }

    /** @return the cached translation, or {@code null} when absent */
    public String get(String key) {
        String value = entries.get(key);
        if (value == null) {
            misses.incrementAndGet();
        } else {
            hits.incrementAndGet();
        }
        return value;
    }

    /** Stores a successful translation; error strings are rejected by the caller. */
    public void put(String key, String value) {
        if (key != null && value != null && !value.isEmpty()) {
            entries.put(key, value);
        }
    }

    public int size() {
        return entries.size();
    }

    public long hitCount() {
        return hits.get();
    }

    public long missCount() {
        return misses.get();
    }

    public String describeStats() {
        return "entries=" + size() + ", hits=" + hitCount() + ", misses=" + missCount();
    }
}
