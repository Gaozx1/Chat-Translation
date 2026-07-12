# Chat Translation · 聊天翻译

Minecraft 客户端聊天翻译模组。仓库同时维护 **Minecraft 1.21.1、1.21.4、1.21.8、1.21.11、26.1.2 和 26.2** 的 Fabric 版本，模组版本统一为 **1.0.3-fix1**。

每个 Minecraft 版本都有独立的 `fabric` 工程，请安装与你的游戏版本完全对应的 JAR。

## 功能

- 自动翻译玩家聊天，可选择是否同时保留原文
- 可选翻译发出的聊天消息，并单独指定发送目标语言
- 命令不会被翻译，当前玩家的服务器回显不会再次翻译
- 可选翻译系统消息
- 自动跟随 Minecraft 当前语言，或手动指定目标语言
- 保护用户名、Rank 标签、命令、链接、点击与悬浮文本
- 翻译失败、超时、译文异常或占位符损坏时恢复原消息
- 发出消息按输入顺序翻译；26.2 还会按顺序处理接收翻译并限制待处理请求数量
- 支持 Google Free、Bing Free、MyMemory、Lingva、LibreTranslate 和自定义 AI 接口
- 支持 OpenAI Compatible、Gemini 与 Anthropic 请求格式

## 支持版本

| Minecraft | 加载器 | Java | 状态 |
|---|---|---:|---|
| 1.21.1 | Fabric | 21+ | 支持 |
| 1.21.4 | Fabric | 21+ | 支持 |
| 1.21.8 | Fabric | 21+ | 支持 |
| 1.21.11 | Fabric | 21+ | 支持 |
| 26.1.2 | Fabric | 25+ | 支持 |
| 26.2 | Fabric | 25+ | 支持 |

当前工作树不包含可发布的 NeoForge 版本。

## 安装

选择与你的 Minecraft 版本对应的依赖：

| Minecraft | Fabric Loader | Fabric API | Cloth Config | Mod Menu | Java |
|---|---|---|---|---|---:|
| 1.21.1 | 0.16.9 | 0.116.12+1.21.1 | 15.0.140 | 11.0.3 | 21+ |
| 1.21.4 | 0.16.9 | 0.110.5+1.21.4 | 16.0.141 | 12.0.0 | 21+ |
| 1.21.8 | 0.16.9 | 0.129.0+1.21.8 | 16.0.141 | 12.0.0 | 21+ |
| 1.21.11 | 0.16.9 | 0.135.1+1.21.11 | 17.0.144 | 17.0.0-alpha.1 | 21+ |
| 26.1.2 | 0.18.4 | 0.151.0+26.1.2 | 26.1.154 | 18.0.0-alpha.8 | 25+ |
| 26.2 | 0.19.3 | 0.154.2+26.2 | 26.2.155 | 20.0.1 | 25+ |

Fabric API 和 Cloth Config 为必需依赖；Mod Menu 推荐安装，用于打开图形配置界面。将对应版本的 Chat Translation JAR 和依赖放入 `.minecraft/mods/`。

## 翻译服务

| 服务 | API Key | 说明 |
|---|---|---|
| Google Free | 不需要 | 默认服务，公共非正式接口可能被限流 |
| Bing Free | 不需要 | 第三方公共接口，稳定性取决于服务端 |
| MyMemory | 不需要 | 使用服务端自动语言识别，存在公共额度限制 |
| Lingva | 不需要 | 可配置自建 Lingva 地址 |
| LibreTranslate | 可选 | 需要填写实例地址，部分实例要求 Key |
| AI | 通常需要 | 支持 OpenAI Compatible、Gemini、Anthropic |

所选服务会收到需要翻译的聊天文本。Google Free 或 Bing Free 失败时会回退到 MyMemory；AI、自建 Lingva 与 LibreTranslate 不会被转发到其他服务。所有服务都失败时会显示原消息。

## 安全说明

- TLS 证书校验默认开启。
- “不安全 SSL 兼容模式”会信任任意服务器证书，只应在排查证书问题时临时开启。
- API Key 保存在本地 `config/chattranslation.json`，不要分享该文件。
- 正常运行不会以 INFO 级别记录完整聊天原文和译文。

## 配置

安装 Mod Menu 后，在模组列表中打开 Chat Translation 配置页面。保存后会立即重新加载所选翻译服务，不会同步执行网络测试或阻塞界面。

主要选项：

- 翻译服务
- 目标语言，`auto` 表示跟随 Minecraft 当前语言
- 是否翻译发出的消息，以及发出消息的目标语言
- 是否翻译所有系统消息
- 是否显示原文
- 单行显示与翻译格式
- Lingva / LibreTranslate 地址
- AI 接口格式、地址、Key 和模型 ID
- 不安全 SSL 兼容模式

## 构建

```powershell
cd "D:\mod\Chat Translation\<Minecraft版本>\fabric"
.\gradlew.bat build
```

构建 1.21.x 需要 Java 21 或更高版本，构建 26.x 需要 Java 25 或更高版本。请让 `JAVA_HOME` 或系统 Java 指向对应版本。

产物路径格式：

```text
<Minecraft版本>/fabric/build/libs/chattranslation-fabric+<Minecraft版本>-1.0.3-fix1.jar
```

## 隐私与限制

- 单条超过 500 个字符的消息不会发送到翻译服务。
- 26.2 最多保留 64 条待处理的接收翻译；队列已满时直接显示原消息。
- 免费公共接口可能限流、变更或暂时不可用。
- 客户端替换后的译文属于本地系统消息，不保留服务器签名状态。

---

## English

Chat Translation is a client-side Minecraft chat translation mod. The repository maintains Fabric builds for **Minecraft 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.2, and 26.2**, all using mod version **1.0.3-fix1**.

Each Minecraft version has an independent `fabric` project. Install the JAR that exactly matches your game version.

### Features

- Automatically translates player chat, with an option to keep the original message visible.
- Optionally translates outgoing chat with a separate outgoing target language.
- Never translates commands, and does not translate the current player's server echo a second time.
- Optionally translates system messages.
- Follows the selected Minecraft language automatically or uses a manually selected target language.
- Preserves usernames, rank tags, commands, links, click events, and hover text.
- Restores or sends the original message when translation fails, times out, returns invalid text, or damages protected placeholders.
- Processes outgoing messages in input order; 26.2 also orders incoming translations and limits pending requests.
- Supports Google Free, Bing Free, MyMemory, Lingva, LibreTranslate, and custom AI endpoints.
- Supports OpenAI Compatible, Gemini, and Anthropic request formats.

### Supported Versions

| Minecraft | Loader | Java | Status |
|---|---|---:|---|
| 1.21.1 | Fabric | 21+ | Supported |
| 1.21.4 | Fabric | 21+ | Supported |
| 1.21.8 | Fabric | 21+ | Supported |
| 1.21.11 | Fabric | 21+ | Supported |
| 26.1.2 | Fabric | 25+ | Supported |
| 26.2 | Fabric | 25+ | Supported |

The current worktree does not contain a publishable NeoForge build.

### Installation

Use the dependencies matching your Minecraft version:

| Minecraft | Fabric Loader | Fabric API | Cloth Config | Mod Menu | Java |
|---|---|---|---|---|---:|
| 1.21.1 | 0.16.9 | 0.116.12+1.21.1 | 15.0.140 | 11.0.3 | 21+ |
| 1.21.4 | 0.16.9 | 0.110.5+1.21.4 | 16.0.141 | 12.0.0 | 21+ |
| 1.21.8 | 0.16.9 | 0.129.0+1.21.8 | 16.0.141 | 12.0.0 | 21+ |
| 1.21.11 | 0.16.9 | 0.135.1+1.21.11 | 17.0.144 | 17.0.0-alpha.1 | 21+ |
| 26.1.2 | 0.18.4 | 0.151.0+26.1.2 | 26.1.154 | 18.0.0-alpha.8 | 25+ |
| 26.2 | 0.19.3 | 0.154.2+26.2 | 26.2.155 | 20.0.1 | 25+ |

Fabric API and Cloth Config are required. Mod Menu is recommended for access to the graphical configuration screen. Place the matching Chat Translation JAR and its dependencies in `.minecraft/mods/`.

### Translation Providers

| Provider | API Key | Notes |
|---|---|---|
| Google Free | Not required | Default provider; the unofficial public endpoint may be rate-limited |
| Bing Free | Not required | Third-party public endpoint whose availability depends on its server |
| MyMemory | Not required | Uses server-side automatic language detection and has a public quota |
| Lingva | Not required | Supports a configurable self-hosted Lingva instance |
| LibreTranslate | Optional | Requires an instance URL; some instances also require an API key |
| AI | Usually required | Supports OpenAI Compatible, Gemini, and Anthropic formats |

The selected provider receives the chat text that needs translation. Google Free and Bing Free fall back to MyMemory when they fail. AI, self-hosted Lingva, and LibreTranslate requests are never forwarded to another provider. The original message is retained or sent if every applicable provider fails.

### Security

- TLS certificate validation is enabled by default.
- Insecure SSL compatibility mode trusts any server certificate and should only be enabled temporarily while diagnosing certificate problems.
- API keys are stored locally in `config/chattranslation.json`; do not share this file.
- Normal operation does not log complete original messages or translations at INFO level.

### Configuration

With Mod Menu installed, open the Chat Translation configuration screen from the mod list. Saving immediately reloads the selected provider without running a synchronous network test or blocking the interface.

Main options:

- Translation provider
- Incoming target language; `auto` follows the selected Minecraft language
- Outgoing message translation and its separate target language
- Translate all system messages
- Show the original message
- Single-line display and translation format
- Lingva and LibreTranslate instance URLs
- AI request format, endpoint, API key, and model ID
- Insecure SSL compatibility mode

### Build

```powershell
cd "D:\mod\Chat Translation\<Minecraft-version>\fabric"
.\gradlew.bat build
```

Minecraft 1.21.x builds require Java 21 or newer. Minecraft 26.x builds require Java 25 or newer. Point `JAVA_HOME` or the system Java to the matching version.

Output path pattern:

```text
<Minecraft-version>/fabric/build/libs/chattranslation-fabric+<Minecraft-version>-1.0.3-fix1.jar
```

### Privacy And Limitations

- Messages longer than 500 characters are not sent to a translation provider.
- Minecraft 26.2 queues at most 64 incoming translations; the original message is kept when the queue is full.
- Free public endpoints may be rate-limited, changed, or temporarily unavailable.
- Client-side replacement translations are local system messages and do not preserve the server's message signature state.

### License

MIT. See [LICENSE](LICENSE).
