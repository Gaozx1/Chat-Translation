# Chat Translation · 聊天翻译

Minecraft 客户端聊天翻译模组。当前开发版本为 **Minecraft 26.2 / Fabric / 模组版本 1.0.3-fix1**。

旧版目录用于维护对应 Minecraft 版本；本说明主要描述 `26.2/fabric`。

## 功能

- 自动翻译玩家聊天，可选择是否同时保留原文
- 可选翻译发出的聊天消息，并单独指定发送目标语言
- 命令不会被翻译，当前玩家的服务器回显不会再次翻译
- 可选翻译系统消息
- 自动跟随 Minecraft 当前语言，或手动指定目标语言
- 保护用户名、Rank 标签、命令、链接、点击与悬浮文本
- 翻译失败、超时、译文异常或占位符损坏时恢复原消息
- 请求按接收顺序处理，避免聊天乱序和瞬间请求洪泛
- 支持 Google Free、Bing Free、MyMemory、Lingva、LibreTranslate 和自定义 AI 接口
- 支持 OpenAI Compatible、Gemini 与 Anthropic 请求格式

## 支持版本

| Minecraft | 加载器 | Java | 状态 |
|---|---|---:|---|
| 1.21.1 | Fabric | 21+ | 历史版本 |
| 1.21.4 | Fabric | 21+ | 历史版本 |
| 1.21.8 | Fabric | 21+ | 历史版本 |
| 1.21.11 | Fabric | 21+ | 历史版本 |
| 26.1.2 | Fabric | 25+ | 历史版本 |
| 26.2 | Fabric | 25+ | 当前版本 |

当前工作树不包含可发布的 NeoForge 版本。

## 安装

26.2 版本需要：

1. Fabric Loader 0.19.3 或更高版本
2. Fabric API 0.154.2+26.2 或更高版本
3. Cloth Config 26.2.155 或更高版本
4. Mod Menu 20.0.1 或更高版本（推荐，用于图形配置界面）
5. Java 25 或更高版本

将生成的 JAR 放入 `.minecraft/mods/`。

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
cd "D:\mod\Chat Translation\26.2\fabric"
.\gradlew.bat build
```

需要让 `JAVA_HOME` 或系统 Java 指向 Java 25。项目不再写死开发者本机的 Java 安装路径。

产物：

```text
26.2/fabric/build/libs/chattranslation-fabric+26.2-1.0.3-fix1.jar
```

## 隐私与限制

- 单条超过 500 个字符的消息不会发送到翻译服务。
- 最多保留 64 条待处理翻译；队列已满时直接显示原消息。
- 免费公共接口可能限流、变更或暂时不可用。
- 客户端替换后的译文属于本地系统消息，不保留服务器签名状态。

---

## English

Chat Translation is a client-side Minecraft chat translation mod. The current development target is **Minecraft 26.2 / Fabric / mod version 1.0.3-fix1**.

Older directories maintain their corresponding Minecraft versions. This document primarily describes `26.2/fabric`.

### Features

- Automatically translates player chat, with an option to keep the original message visible.
- Optionally translates outgoing chat with a separate outgoing target language.
- Never translates commands, and does not translate the current player's server echo a second time.
- Optionally translates system messages.
- Follows the selected Minecraft language automatically or uses a manually selected target language.
- Preserves usernames, rank tags, commands, links, click events, and hover text.
- Restores or sends the original message when translation fails, times out, returns invalid text, or damages protected placeholders.
- Processes incoming and outgoing translations in order to avoid message reordering and request bursts.
- Supports Google Free, Bing Free, MyMemory, Lingva, LibreTranslate, and custom AI endpoints.
- Supports OpenAI Compatible, Gemini, and Anthropic request formats.

### Supported Versions

| Minecraft | Loader | Java | Status |
|---|---|---:|---|
| 1.21.1 | Fabric | 21+ | Legacy |
| 1.21.4 | Fabric | 21+ | Legacy |
| 1.21.8 | Fabric | 21+ | Legacy |
| 1.21.11 | Fabric | 21+ | Legacy |
| 26.1.2 | Fabric | 25+ | Legacy |
| 26.2 | Fabric | 25+ | Current |

The current worktree does not contain a publishable NeoForge build.

### Installation

Minecraft 26.2 requires:

1. Fabric Loader 0.19.3 or newer
2. Fabric API 0.154.2+26.2 or newer
3. Cloth Config 26.2.155 or newer
4. Mod Menu 20.0.1 or newer, recommended for the graphical configuration screen
5. Java 25 or newer

Place the generated JAR in `.minecraft/mods/`.

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
cd "D:\mod\Chat Translation\26.2\fabric"
.\gradlew.bat build
```

`JAVA_HOME` or the system Java must point to Java 25. The project does not hard-code a developer-specific Java installation path.

Output:

```text
26.2/fabric/build/libs/chattranslation-fabric+26.2-1.0.3-fix1.jar
```

### Privacy And Limitations

- Messages longer than 500 characters are not sent to a translation provider.
- At most 64 incoming translations are queued; the original message is kept when the queue is full.
- Free public endpoints may be rate-limited, changed, or temporarily unavailable.
- Client-side replacement translations are local system messages and do not preserve the server's message signature state.

### License

MIT. See [LICENSE](LICENSE).
