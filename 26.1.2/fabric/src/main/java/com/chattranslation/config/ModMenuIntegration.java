package com.chattranslation.config;

import com.chattranslation.ChatTranslationMod;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Arrays;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return this::createScreen;
    }

    private Screen createScreen(Screen parent) {
        ModConfig editing = ModConfig.load(configPath());

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("chattranslation.config.title"))
                .setSavingRunnable(() -> saveConfig(editing));

        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("chattranslation.config.category.general"));

        general.addEntry(entries.startStringDropdownMenu(Component.translatable("chattranslation.config.translation_service"), editing.getTranslationService(), Component::literal)
                .setSelections(Arrays.asList("google_free", "bing_free", "mymemory", "lingva", "libretranslate", "ai"))
                .setDefaultValue("google_free")
                .setSaveConsumer(editing::setTranslationService)
                .build());

        general.addEntry(entries.startStringDropdownMenu(Component.translatable("chattranslation.config.target_language"), editing.getTargetLanguage(), Component::literal)
                .setSelections(Arrays.asList(editing.getSupportedLanguages()))
                .setDefaultValue("auto")
                .setSaveConsumer(editing::setTargetLanguage)
                .build());

        general.addEntry(entries.startBooleanToggle(Component.translatable("chattranslation.config.translate_outgoing_messages"), editing.isTranslateOutgoingMessages())
                .setDefaultValue(false)
                .setSaveConsumer(editing::setTranslateOutgoingMessages)
                .build());

        general.addEntry(entries.startStringDropdownMenu(Component.translatable("chattranslation.config.outgoing_target_language"), editing.getOutgoingTargetLanguage(), Component::literal)
                .setSelections(Arrays.stream(editing.getSupportedLanguages()).filter(language -> !"auto".equals(language)).toList())
                .setDefaultValue("en")
                .setSaveConsumer(editing::setOutgoingTargetLanguage)
                .build());

        general.addEntry(entries.startBooleanToggle(Component.translatable("chattranslation.config.translate_all_messages"), editing.isTranslateAllMessages())
                .setDefaultValue(false)
                .setSaveConsumer(editing::setTranslateAllMessages)
                .build());

        general.addEntry(entries.startBooleanToggle(Component.translatable("chattranslation.config.show_original"), editing.isShowOriginal())
                .setDefaultValue(false)
                .setSaveConsumer(editing::setShowOriginal)
                .build());

        general.addEntry(entries.startStrField(Component.translatable("chattranslation.config.translation_format"), editing.getTranslationFormat())
                .setDefaultValue("{message}")
                .setSaveConsumer(editing::setTranslationFormat)
                .build());

        general.addEntry(entries.startBooleanToggle(Component.translatable("chattranslation.config.single_line_display"), editing.isSingleLineDisplay())
                .setDefaultValue(true)
                .setSaveConsumer(editing::setSingleLineDisplay)
                .build());

        ConfigCategory freeServices = builder.getOrCreateCategory(Component.translatable("chattranslation.config.category.free_services"));

        freeServices.addEntry(entries.startStrField(Component.translatable("chattranslation.config.lingva_api_url"), editing.getLingvaApiUrl())
                .setDefaultValue("https://lingva.ml/api/v1")
                .setSaveConsumer(editing::setLingvaApiUrl)
                .build());

        freeServices.addEntry(entries.startStrField(Component.translatable("chattranslation.config.libretranslate_api_url"), editing.getLibreTranslateApiUrl())
                .setDefaultValue("")
                .setSaveConsumer(editing::setLibreTranslateApiUrl)
                .build());

        freeServices.addEntry(entries.startStrField(Component.translatable("chattranslation.config.libretranslate_api_key"), editing.getLibreTranslateApiKey())
                .setDefaultValue("")
                .setSaveConsumer(editing::setLibreTranslateApiKey)
                .build());

        ConfigCategory advanced = builder.getOrCreateCategory(Component.translatable("chattranslation.config.category.advanced"));

        advanced.addEntry(entries.startBooleanToggle(Component.translatable("chattranslation.config.insecure_ssl"), editing.isInsecureSsl())
                .setDefaultValue(true)
                .setSaveConsumer(editing::setInsecureSsl)
                .build());

        advanced.addEntry(entries.startStringDropdownMenu(Component.translatable("chattranslation.config.ai_format"), editing.getAiFormat(), Component::literal)
                .setSelections(Arrays.asList("openai_compatible", "gemini", "anthropic"))
                .setDefaultValue("openai_compatible")
                .setSaveConsumer(editing::setAiFormat)
                .build());

        advanced.addEntry(entries.startStrField(Component.translatable("chattranslation.config.ai_api_url"), editing.getAiApiUrl())
                .setDefaultValue("")
                .setSaveConsumer(editing::setAiApiUrl)
                .build());

        advanced.addEntry(entries.startStrField(Component.translatable("chattranslation.config.ai_api_key"), editing.getAiApiKey())
                .setDefaultValue("")
                .setSaveConsumer(editing::setAiApiKey)
                .build());

        advanced.addEntry(entries.startStrField(Component.translatable("chattranslation.config.ai_model_id"), editing.getAiModelId())
                .setDefaultValue("")
                .setSaveConsumer(editing::setAiModelId)
                .build());

        advanced.addEntry(entries.startTextDescription(Component.translatable("chattranslation.config.notice"))
                .build());

        return builder.build();
    }

    private void saveConfig(ModConfig config) {
        config.save(configPath());
        ChatTranslationMod.reloadProvider();
        ChatTranslationMod.LOGGER.info("[ChatTranslation][test] {}", ChatTranslationMod.testCurrentProviderConnection());
    }

    private Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(ChatTranslationMod.MOD_ID + ".json");
    }
}
