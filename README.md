# Chat Translation Mod · 聊天翻译模组

自动翻译 Minecraft 聊天消息的客户端模组，支持免 API Key 场景下直接可用，并提供模组菜单配置界面。

> 简体中文  /  English (see below)

---

## ✨ 特性

- 🎯 **自动翻译聊天消息**：进入服务器后自动翻译所有聊天信息
- 🔓 **免 Key 可用**：默认使用无需 API Key 的翻译服务（Google Proxy、彩云小译）
- 🎮 **模组菜单支持**：
  - Fabric：Mod Menu + Cloth Config GUI
  - NeoForge：Catalogue 菜单集成
- 🌐 **多种翻译源**：Google Translate、Google Proxy、彩云小译、DeepL、有道翻译、讯飞听见翻译
- 📡 **连接测试**：可以在配置里直接测试所选翻译源是否可用
- 🔁 **智能回退**：翻译失败（如 HTTP 403）自动显示原文
- 🛡️ **SSL 兼容模式**：可选关闭 SSL 校验以应对证书链问题
- 🧹 **消息清洗**：自动剥离 Hypixel 彩色码、Rank 等装饰信息

## 📦 支持的版本

| Minecraft 版本 | Fabric | NeoForge | Java 要求 |
|---|---|---|---|
| 1.21.1 | ✅ | ✅ | Java 21+ |
| 1.21.4 | ✅ | ✅ | Java 21+ |
| 1.21.8 | ✅ | ✅ | Java 21+ |
| 1.21.11 | ✅ | ✅ | Java 21+ |
| 26.1.2 | ✅ | ✅ | Java 25+ |

## 🚀 安装

### Fabric
1. 安装 [Fabric Loader](https://fabricmc.net/use/) 和 [Fabric API](https://modrinth.com/mod/fabric-api)
2. 下载并安装 [Mod Menu](https://modrinth.com/mod/modmenu)（用于配置界面）
3. 将本模组 jar 文件放入 `.minecraft/mods/` 目录
4. 启动游戏

### NeoForge
1. 安装 [NeoForge Loader](https://neoforged.net/)
2. 将本模组 jar 文件放入 `.minecraft/mods/` 目录
3. 在模组列表中找到 Chat Translation，点击配置
4. 启动游戏

## ⚙️ 配置

进入游戏后：
- **Fabric**：在 Mod Menu 里点击 Chat Translation 的配置按钮
- **NeoForge**：在模组列表里点击 Chat Translation 的配置入口

可配置项：
- **翻译服务 Provider**：Google Translate / Google Proxy / 彩云小译 / DeepL / 有道 / 讯飞听见
- **目标语言 Target Language**：例如 `zh-CN`、`en`
- **源语言自动检测**：默认开启
- **SSL 不安全模式**：如遇证书链问题可打开
- **测试连接**：在配置界面直接测试当前 provider 是否可访问

## 🔑 翻译源说明

| Provider | 是否免 Key | 备注 |
|---|---|---|
| Google Translate (官方) | ❌ | 需 Google API Key |
| Google Proxy | ✅ | 走免密钥代理接口，可能触发 403 |
| 彩云小译 | ✅ | 默认使用公共测试 Token |
| DeepL | ❌ | 需 DeepL API Key |
| 有道翻译 | ❌ | 需有道智云 API Key |
| 讯飞听见翻译 | ❌ | 需讯飞 AIU API Key |

推荐优先使用：
1. **Google Proxy**（最快，免 key）
2. **彩云小译**（稳定，免 key）

如果遇到 403 或无法连接，可切换到其它 provider，或在配置里打开 **SSL 不安全模式**。

## 🛠️ 常见问题

### 模组加载时报 missing `cloth-config` / `modmenu`
Fabric 版本中这些是可选依赖；模组会自动降级运行，但没有图形配置界面。安装 Mod Menu + Cloth Config 即可获得完整 GUI。

### 日志中出现 `SSLHandshakeException: PKIX path building failed`
打开配置里的 **SSL 不安全模式**（跳过证书校验）再试。

### 日志中出现 `Google Proxy Error: HTTP 403`
这是 Google 代理接口触发了风控；模组会自动回退显示原文。建议切换到 **彩云小译** 或切换到带 API Key 的官方 Provider。

### 切换 provider 后没变化
在配置界面点击 **重载 Provider** / 保存配置后，或直接 **重启游戏** 即可生效。

### 在 Hypixel 上看不到聊天翻译
请确认：
1. 已经进入服务器（聊天事件只在联机世界里大量触发）
2. 模组在 Fabric/NeoForge 加载日志中显示 `ChatTranslation`
3. 日志里有 `[ChatTranslation] Provider reloaded:` 字样

## 📁 项目结构

```
Chat Translation/
├── 1.21.1/
│   ├── fabric/
│   └── neoforge/
├── 1.21.4/
│   ├── fabric/
│   └── neoforge/
├── 1.21.8/
│   ├── fabric/
│   └── neoforge/
├── 1.21.11/
│   ├── fabric/
│   └── neoforge/
└── 26.1.2/
    ├── fabric/
    └── neoforge/
```

主要源码路径（各版本目录内）：
- Fabric: `src/main/java/com/chattranslation/`
- NeoForge: `src/main/java/com/chattranslation/`

## 🏗️ 构建

进入具体版本目录，例如：

```powershell
cd "d:\mod\Chat Translation\1.21.4\fabric"
.\gradlew.bat build
```

产物位置：
- Fabric: `build/libs/chattranslation-<version>.jar`
- NeoForge: `build/libs/chattranslation-<version>.jar`

Java 版本：
- 1.21.x：Java 21+
- 26.1.2：Java 25+

---

# Chat Translation Mod

A client-side Minecraft mod that automatically translates chat messages. Works **without API keys** by default, and provides a **mod menu configuration UI**.

## ✨ Features

- 🎯 Auto translates every incoming chat message
- 🔓 Works **without API keys** by default (Google Proxy, Caiyun Translate)
- 🎮 **Mod menu UI**:
  - Fabric: Mod Menu + Cloth Config GUI
  - NeoForge: Catalogue integration
- 🌐 Multiple providers: Google Translate, Google Proxy, Caiyun, DeepL, Youdao, iFlyRec
- 📡 Connection test built into the configuration UI
- 🔁 Graceful fallback: original message shown on translation failure (e.g. HTTP 403)
- 🛡️ Optional insecure SSL mode to bypass certificate chain issues
- 🧹 Automatic cleanup of server decorations (Hypixel color codes, ranks)

## 📦 Supported versions

| Minecraft version | Fabric | NeoForge | Java requirement |
|---|---|---|---|
| 1.21.1 | ✅ | ✅ | Java 21+ |
| 1.21.4 | ✅ | ✅ | Java 21+ |
| 1.21.8 | ✅ | ✅ | Java 21+ |
| 1.21.11 | ✅ | ✅ | Java 21+ |
| 26.1.2 | ✅ | ✅ | Java 25+ |

## 🚀 Installation

### Fabric
1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api)
2. Install [Mod Menu](https://modrinth.com/mod/modmenu) for configuration UI
3. Drop the mod jar into `.minecraft/mods/`
4. Launch the game

### NeoForge
1. Install [NeoForge Loader](https://neoforged.net/)
2. Drop the mod jar into `.minecraft/mods/`
3. In the mod list, open Chat Translation's configuration
4. Launch the game

## ⚙️ Configuration

- **Fabric**: In Mod Menu, click the config button next to Chat Translation
- **NeoForge**: Open the configuration entry from the mod list

Available options:
- Provider: Google Translate / Google Proxy / Caiyun / DeepL / Youdao / iFlyRec
- Target language, with automatic source-language detection enabled by default
- Insecure SSL mode (for environments with broken certificate chains)
- Built-in connection test for the selected provider

## 🔑 Providers

| Provider | Works without key | Notes |
|---|---|---|
| Google Translate (official) | ❌ | Requires a Google API key |
| Google Proxy | ✅ | Free proxy endpoint; may return 403 under rate limits |
| Caiyun | ✅ | Uses a public test token by default |
| DeepL | ❌ | Requires a DeepL API key |
| Youdao | ❌ | Requires a Youdao Zhiyun API key |
| iFlyRec | ❌ | Requires an iFlyRec AIU API key |

Recommended order:
1. Google Proxy
2. Caiyun

If you get 403 or connection errors, switch provider or enable insecure SSL mode.

## 🛠️ Troubleshooting

### Mod loads but complains about missing `cloth-config` / `modmenu`
On Fabric these are optional dependencies. The mod still runs, but without the GUI. Install Mod Menu + Cloth Config to restore full configuration.

### `SSLHandshakeException: PKIX path building failed` in logs
Enable insecure SSL mode in the configuration UI.

### `Google Proxy Error: HTTP 403` in logs
The proxy endpoint is being rate-limited. The mod falls back to showing the original message. Switch to Caiyun or to a key-bearing provider.

### Nothing changes after switching provider
Click Reload Provider in the UI, or restart the game.

### No chat translation on Hypixel
Make sure:
- You are actually joined to a server
- `ChatTranslation` appears in the mod load log
- The log contains `[ChatTranslation] Provider reloaded:`

## 📁 Project layout

```
Chat Translation/
├── 1.21.1/
│   ├── fabric/
│   └── neoforge/
├── 1.21.4/
│   ├── fabric/
│   └── neoforge/
├── 1.21.8/
│   ├── fabric/
│   └── neoforge/
├── 1.21.11/
│   ├── fabric/
│   └── neoforge/
└── 26.1.2/
    ├── fabric/
    └── neoforge/
```

Main source paths inside each version:
- Fabric: `src/main/java/com/chattranslation/`
- NeoForge: `src/main/java/com/chattranslation/`

## 🏗️ Build

From any version folder:

```powershell
cd "d:\mod\Chat Translation\1.21.4\fabric"
.\gradlew.bat build
```

Output jars:
- Fabric: `build/libs/chattranslation-<version>.jar`
- NeoForge: `build/libs/chattranslation-<version>.jar`

Java versions:
- 1.21.x: Java 21+
- 26.1.2: Java 25+
