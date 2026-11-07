package com.chattranslation.translation;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 翻译服务提供者接口
 */
public interface TranslationProvider {

    /**
     * 翻译文本到目标语言
     *
     * @param text       待翻译文本
     * @param sourceLang 源语言代码 (auto 表示自动检测)
     * @param targetLang 目标语言代码
     * @return 翻译后的文本
     */
    CompletableFuture<String> translate(String text, String sourceLang, String targetLang);

    /**
     * 带上下文翻译。
     *
     * <p>默认实现**直接忽略上下文**并退化为 {@link #translate}，因此非 AI 提供商（Google Free、
     * MyMemory、LibreTranslate 等）的行为与旧版本完全一致——它们按单句翻译，多送历史文本既无收益
     * 又多泄露隐私。
     *
     * <p>只有真正能从上下文获益的提供商（AI）才会覆盖此方法。
     *
     * @param text        待翻译文本
     * @param sourceLang  源语言代码
     * @param targetLang  目标语言代码
     * @param contextLines 最近几条已显示的聊天原文，按时间从旧到新；不可变，可能为空
     * @return 翻译后的文本
     */
    default CompletableFuture<String> translateWithContext(String text, String sourceLang, String targetLang,
                                                           List<String> contextLines) {
        return translate(text, sourceLang, targetLang);
    }

    /**
     * 缓存的预查询钩子。
     *
     * <p>只有提供商自己知道完整的缓存标识（模型 ID、上下文指纹等）。主流程用它在**占用并发槽位之前**
     * 判断是否已有结果，避免缓存命中还要排队；返回 {@code null} 表示该文本未缓存或此提供商不缓存。
     *
     * <p>默认返回 {@code null}（不参与预查询），行为与旧版本一致。
     *
     * @param text       待翻译文本（已做占位符保护）
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param contextLines 本次请求将附带的上下文
     * @return 已缓存的译文，或 {@code null}
     */
    default String peekCached(String text, String sourceLang, String targetLang, List<String> contextLines) {
        return null;
    }

    /**
     * 获取翻译服务名称
     */
    String getName();

    /**
     * 检测文本语言
     *
     * @param text 待检测文本
     * @return 检测到的语言代码
     */
    CompletableFuture<String> detectLanguage(String text);
}
