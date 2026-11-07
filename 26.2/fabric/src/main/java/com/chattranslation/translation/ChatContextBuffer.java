package com.chattranslation.translation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Bounded, in-memory ring buffer of the most recent chat lines, used to give the AI a little
 * conversational context (pronouns, slang, elliptical replies) instead of translating every line
 * in isolation.
 *
 * <p>Design constraints, all deliberate:
 * <ul>
 *   <li><b>Memory only.</b> Nothing is written to disk. The buffer dies with the game session, so
 *       it never becomes a chat log the user forgot they had.</li>
 *   <li><b>Only displayed lines.</b> Entries are recorded after a message actually reached the HUD
 *       (see {@code ChatTranslationMod}), never at receive time. A message that failed to translate
 *       or is still queued must not silently shape later translations.</li>
 *   <li><b>Original text, not translations.</b> The model is asked to translate the current line;
 *       feeding it its own previous output invites drift and compounding errors.</li>
 *   <li><b>Bounded.</b> Fixed capacity and a hard per-line length cap, so a spammy server cannot
 *       inflate request bodies.</li>
 * </ul>
 */
public final class ChatContextBuffer {

    /** Hard upper bound a user may configure. */
    public static final int MAX_LINES = 8;

    /** A single context line longer than this is truncated; chat lines above 500 chars are skipped anyway. */
    private static final int MAX_LINE_LENGTH = 200;

    private static final ChatContextBuffer SHARED = new ChatContextBuffer(MAX_LINES);

    private final int capacity;
    private final Deque<String> lines = new ArrayDeque<>();
    private final Object lock = new Object();

    public ChatContextBuffer(int capacity) {
        this.capacity = Math.max(0, Math.min(MAX_LINES, capacity));
    }

    /** The process-wide buffer used for incoming chat. */
    public static ChatContextBuffer shared() {
        return SHARED;
    }

    /**
     * Records a displayed line as context for later messages.
     *
     * @param speaker sender name, or {@code null} for system messages
     * @param text    the original text that was shown
     */
    public void record(String speaker, String text) {
        if (capacity == 0 || text == null) {
            return;
        }
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            return;
        }
        if (trimmed.length() > MAX_LINE_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LINE_LENGTH) + "\u2026";
        }
        String line = speaker == null || speaker.isBlank() ? trimmed : speaker + ": " + trimmed;
        synchronized (lock) {
            while (lines.size() >= capacity) {
                lines.removeFirst();
            }
            lines.addLast(line);
        }
    }

    /**
     * @param limit how many of the newest lines to return; clamped to the buffer capacity
     * @return an immutable snapshot, oldest first; empty when context is disabled
     */
    public List<String> snapshot(int limit) {
        if (capacity == 0 || limit <= 0) {
            return List.of();
        }
        int wanted = Math.min(limit, capacity);
        synchronized (lock) {
            if (lines.isEmpty()) {
                return List.of();
            }
            int skip = Math.max(0, lines.size() - wanted);
            List<String> out = new ArrayList<>(wanted);
            int index = 0;
            for (String line : lines) {
                if (index++ >= skip) {
                    out.add(line);
                }
            }
            return Collections.unmodifiableList(out);
        }
    }

    public int size() {
        synchronized (lock) {
            return lines.size();
        }
    }

    public void clear() {
        synchronized (lock) {
            lines.clear();
        }
    }
}
