package com.chattranslation.translation;

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
