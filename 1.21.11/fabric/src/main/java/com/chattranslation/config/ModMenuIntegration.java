package com.chattranslation.config;

import com.chattranslation.ChatTranslationMod;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

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
                .setTitle(Text.literal("Chat Translation Config"))
                .setSavingRunnable(() -> saveConfig(editing));

        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));

        general.addEntry(entries.startStringDropdownMenu(Text.literal("Translation Service"), editing.getTranslationService(), Text::literal)
                .setSelections(Arrays.asList("google_proxy", "google", "caiyun", "bing"))
                .setDefaultValue("google_proxy")
                .setSaveConsumer(editing::setTranslationService)
                .build());

        general.addEntry(entries.startStringDropdownMenu(Text.literal("Target Language"), editing.getTargetLanguage(), Text::literal)
                .setSelections(Arrays.asList(editing.getSupportedLanguages()))
                .setDefaultValue("auto")
                .setSaveConsumer(editing::setTargetLanguage)
                .build());

        general.addEntry(entries.startBooleanToggle(Text.literal("Translate All Messages"), editing.isTranslateAllMessages())
                .setDefaultValue(true)
                .setSaveConsumer(editing::setTranslateAllMessages)
                .build());

        general.addEntry(entries.startBooleanToggle(Text.literal("Show Original"), editing.isShowOriginal())
                .setDefaultValue(true)
                .setSaveConsumer(editing::setShowOriginal)
                .build());

        general.addEntry(entries.startStrField(Text.literal("Translation Format"), editing.getTranslationFormat())
                .setDefaultValue("&r[&6译&r] {message}")
                .setSaveConsumer(editing::setTranslationFormat)
                .build());

        ConfigCategory advanced = builder.getOrCreateCategory(Text.literal("Advanced"));

        advanced.addEntry(entries.startStrField(Text.literal("Google Proxy URL"), editing.getGoogleProxyUrl())
                .setDefaultValue("https://translate-pa.googleapis.com/v1/translateHtml")
                .setSaveConsumer(editing::setGoogleProxyUrl)
                .build());

        advanced.addEntry(entries.startBooleanToggle(Text.literal("Insecure SSL Compatibility"), editing.isInsecureSsl())
                .setDefaultValue(true)
                .setSaveConsumer(editing::setInsecureSsl)
                .build());

        advanced.addEntry(entries.startStrField(Text.literal("Caiyun Token"), editing.getCaiyunToken())
                .setDefaultValue("3975l6lr5pcbvidl6jl2")
                .setSaveConsumer(editing::setCaiyunToken)
                .build());

        advanced.addEntry(entries.startStrField(Text.literal("Bing API Key"), editing.getBingApiKey())
                .setDefaultValue("")
                .setSaveConsumer(editing::setBingApiKey)
                .build());

        advanced.addEntry(entries.startStrField(Text.literal("Bing Region"), editing.getBingRegion())
                .setDefaultValue("global")
                .setSaveConsumer(editing::setBingRegion)
                .build());

        advanced.addEntry(entries.startTextDescription(Text.literal("保存后会自动重载当前翻译服务。测试连接结果请查看游戏日志中的 [ChatTranslation][test]。"))
                .build());

        return builder.build();
    }

    private void saveConfig(ModConfig config) {
        config.save(configPath());
        ChatTranslationMod.reloadProvider();
        ChatTranslationMod.LOGGER.info("[ChatTranslation][test] {}", ChatTranslationMod.testCurrentProviderConnection());
    }

    private Path configPath() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve(ChatTranslationMod.MOD_ID + ".json");
    }
}
