# Chat Translation · 聊天翻译

Minecraft 客户端聊天翻译模组。仓库同时维护 **Minecraft 1.21.1、1.21.4、1.21.8、1.21.11、26.1.2、26.2 和 26.3** 的 Fabric 版本，模组版本统一为 **1.0.5-alpha.1**。

每个 Minecraft 版本都有独立的 `fabric` 工程，请安装与你的游戏版本完全对应的 JAR。

## 功能

- 自动翻译玩家聊天，可选择是否同时保留原文
- 可选翻译发出的聊天消息，并单独指定发送目标语言
- 命令不会被翻译，当前玩家的服务器回显不会再次翻译
- 可选翻译系统消息
- 自动跟随 Minecraft 当前语言，或手动指定目标语言
- 保护用户名、Rank 标签、命令、链接、点击与悬浮文本
- 翻译失败、超时、译文异常或占位符损坏时恢复原消息
- 内置免费 AI 服务 `ai_free`（deepseek-v4.1-flash），开箱即用，无需填 Key
- 支持 Google Free、Bing Free、MyMemory、Lingva、LibreTranslate 和自定义 AI 接口
- 支持 OpenAI Compatible、Gemini 与 Anthropic 请求格式
- **上下文翻译**：把最近几条聊天一起交给 AI，帮助判断代词、俚语和省略句（默认关闭，见下方隐私说明）

## 上下文翻译

单句翻译最大的问题是缺少上下文。同一句 “he took it and ran”，在“掉了一把钻石剑”和“有人偷了东西”两种对话里应该译成不同结果。

开启「上下文翻译」后，模组会把最近 0～8 条**已经显示在聊天栏**的消息一起发给 AI 作为参考，并要求模型只翻译当前这一句、不要回答或接着写上下文。

实现上有几条刻意的约束：

- **只记录真正上屏的消息**。翻译失败、还在排队、或最终显示原文的消息，都不会进入上下文，避免用用户没看到的内容去影响后续翻译。
- **记录原文而不是译文**，防止模型被自己的历史输出带偏、错误逐条累积。
- **只对 AI 生效**。Google Free、MyMemory、LibreTranslate 等按句翻译的服务会忽略上下文（接口默认实现直接退化为普通翻译），多送历史文本既无收益又多泄露隐私。
- **缓存区分上下文**。缓存键包含上下文指纹和模型 ID，同一句话在不同对话里的译文不会互相串用。

> **隐私提醒**：开启后，最近几条聊天原文会随每次请求发送给第三方翻译服务。这是默认关闭的原因，请自行判断是否接受。

## 性能

- **翻译结果缓存**：512 条 LRU 缓存，重复文本（打招呼、服务器广播等）直接命中，不再发起网络请求。缓存命中在**占用并发槽位之前**就返回，不会排在慢请求后面
- **有界并发 + 保序显示**：接收翻译最多 3 个请求并发，但严格按收到顺序上屏，慢请求不再让后续消息排队等到超时
- **连接池复用**：HTTP 客户端与工作线程全进程共享，避免每条消息重新握手 TLS
- **热路径优化**：去掉消息处理中的正则 `replaceAll`、按玩家名缓存预编译正则、占位符还原改为单趟扫描 + O(1) 索引
- **智能重试**：仅对 429 / 408 / 5xx 重试，4xx 立即失败，不再把延迟放大 3 倍
- **熔断保护**：连续 3 次失败（或遇到密钥错误等永久性错误）后，该服务进入冷却并快速失败，冷却时间从 20 秒起指数退避，成功一次立即恢复。端点挂掉时聊天不会被拖住
- **可配置 AI 超时**：公共端点可能几十秒才返回，超时默认 45 秒、可在 10～180 秒之间调整

## 译文质量校验

AI 有时会"回答"而不是翻译（例如返回 `Sure! Here is the translation: ...`），或把整段解释当译文。模组现在会检查返回内容：疑似说明性开头、代码块、与原文长度严重不成比例、或占位符被破坏时，直接显示原文而不是把废话贴进聊天栏。

## 安全

- API Key 不以明文保存：内置免费服务的 Key 以 **AES-256-GCM** 密文形式编译进 JAR，口令经 XOR 混淆后在运行时合成，仓库与构建产物中都不含明文
- 用户自己填写的 AI Key 仍存于 `config/chattranslation.json`，不要分享该文件

## 支持版本

| Minecraft | 加载器 | Java | 状态 |
|---|---|---:|---|
| 1.21.1 | Fabric | 21+ | 支持 |
| 1.21.4 | Fabric | 21+ | 支持 |
| 1.21.8 | Fabric | 21+ | 支持 |
| 1.21.11 | Fabric | 21+ | 支持 |
| 26.1.2 | Fabric | 25+ | 支持 |
| 26.2 | Fabric | 25+ | 支持 |
| 26.3 | Fabric | 25+ | 支持 |

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
| 26.3 | 0.19.5 | 0.156.0+26.3 | 26.3.159 | 21.0.0 | 25+ |

Fabric API 和 Cloth Config 为必需依赖；Mod Menu 推荐安装，用于打开图形配置界面。将对应版本的 Chat Translation JAR 和依赖放入 `.minecraft/mods/`。

## 翻译服务

| 服务 | API Key | 说明 |
|---|---|---|
| AI Free（内置） | 不需要 | 默认服务，内置 deepseek-v4.1-flash（api.mcapple.top），额度共享、延迟可能较高 |
| Google Free | 不需要 | 公共非正式接口，可能被限流 |
| Bing Free | 不需要 | 第三方公共接口，稳定性取决于服务端 |
| MyMemory | 不需要 | 使用服务端自动语言识别，存在公共额度限制 |
| Lingva | 不需要 | 可配置自建 Lingva 地址 |
| LibreTranslate | 可选 | 需要填写实例地址，部分实例要求 Key |
| AI | 通常需要 | 支持 OpenAI Compatible、Gemini、Anthropic，使用你自己的端点 |

所选服务会收到需要翻译的聊天文本。Google Free 或 Bing Free 失败时会回退到 MyMemory；AI、AI Free、自建 Lingva 与 LibreTranslate 不会被转发到其他服务。所有服务都失败时会显示原消息。

## 安全说明

- TLS 证书校验默认开启。
- “不安全 SSL 兼容模式”会信任任意服务器证书，只应在排查证书问题时临时开启。
- 内置 AI Free 服务的 Key 以 AES-256-GCM 密文编译进 JAR，明文不出现在源码、配置或日志中。
- 你自己填写的 API Key 保存在本地 `config/chattranslation.json`，不要分享该文件。
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
- AI 请求超时（默认 45 秒）
- 不安全 SSL 兼容模式

## 构建

```powershell
cd "D:\mod\Chat Translation\<Minecraft版本>\fabric"
.\gradlew.bat build
```

构建 1.21.x 需要 Java 21 或更高版本，构建 26.x 需要 Java 25 或更高版本。请让 `JAVA_HOME` 或系统 Java 指向对应版本。26.x 工程已在 `gradle.properties` 里通过 `org.gradle.java.home` 固定 JDK 25。

26.3 使用 Gradle 9.6.0 + Loom 1.17.21。注意 `services.gradle.org` 的发行包会重定向到 `github.com`，若该域名不可达需改用镜像（校验和与官方一致）。

产物路径格式：

```text
<Minecraft版本>/fabric/build/libs/chattranslation-fabric+<Minecraft版本>-1.0.5-alpha.1.jar
```

## 隐私与限制

- 单条超过 500 个字符的消息不会发送到翻译服务。
- 接收翻译最多保留 64 条待处理；队列已满时直接显示原消息。
- 接收翻译最多 3 个请求并发，上屏顺序始终与收到顺序一致。
- 免费公共接口可能限流、变更或暂时不可用。
- 客户端替换后的译文属于本地系统消息，不保留服务器签名状态。

---

## English

Chat Translation is a client-side Minecraft chat translation mod. The repository maintains Fabric builds for **Minecraft 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.2, 26.2, and 26.3**, all using mod version **1.0.5-alpha.1**.

Each Minecraft version has an independent `fabric` project. Install the JAR that exactly matches your game version.

### Features

- Automatically translates player chat, with an option to keep the original message visible.
- Optionally translates outgoing chat with a separate outgoing target language.
- Never translates commands, and does not translate the current player's server echo a second time.
- Optionally translates system messages.
- Follows the selected Minecraft language automatically or uses a manually selected target language.
- Preserves usernames, rank tags, commands, links, click events, and hover text.
- Restores or sends the original message when translation fails, times out, returns invalid text, or damages protected placeholders.
- Processes outgoing messages in input order; incoming translations are bounded and displayed in receive order.
- Built-in free `ai_free` provider (deepseek-v4.1-flash) that works with no key setup.
- Supports Google Free, Bing Free, MyMemory, Lingva, LibreTranslate, and custom AI endpoints.
- Supports OpenAI Compatible, Gemini, and Anthropic request formats.
- **Context-aware translation**: recent chat lines are given to the AI so it can resolve pronouns, slang and elliptical replies (off by default, see the privacy note below).

### Context-Aware Translation

Translating one line at a time loses meaning. "he took it and ran" should read differently depending on whether the previous lines were about a dropped sword or a theft.

When enabled, the mod sends the last 0–8 lines that were **actually shown in chat** to the AI as reference material, and instructs the model to translate only the current line rather than answering or continuing the conversation.

Deliberate constraints:

- **Only displayed lines are recorded.** Messages that failed, are still queued, or were shown as untranslated originals never enter the context, so content the user never saw cannot shape later translations.
- **Originals, not translations**, so the model is not nudged by its own earlier output and errors do not compound.
- **AI only.** Google Free, MyMemory, LibreTranslate and other per-sentence services ignore the context entirely (the interface's default implementation degrades to a plain translation). Sending them history would add latency and leak more text for no benefit.
- **Cache identity includes context.** The cache key contains a context fingerprint plus the model id, so the same line translated in two different conversations cannot collide.

> **Privacy notice:** enabling this sends the last few chat lines to a third-party translation provider with every request. That is why it is off by default.

### Performance

- 512-entry LRU translation cache; repeated lines skip the network entirely. Cache hits are answered **before** taking a concurrency slot, so they never queue behind slow requests.
- Bounded concurrency (3 in flight) with strictly ordered display, so one slow reply no longer stalls the queue.
- Process-wide HTTP client and worker pool, so chat bursts reuse connections instead of redoing TLS handshakes.
- Hot-path cleanups: no regex `replaceAll`, cached per-player name patterns, single-pass placeholder restore with O(1) lookup.
- Smart retries: only 429/408/5xx are retried, so failures no longer triple the latency.
- Circuit breaker: after 3 consecutive failures (or a permanent error such as a bad key) a provider cools down and fails fast, starting at 20s with exponential backoff, resetting on the first success. A dead endpoint no longer drags the whole chat down.
- Configurable AI timeout (default 45s, adjustable 10–180s) because public endpoints can legitimately take tens of seconds.

### Translation Quality Guard

Models sometimes answer instead of translating (`Sure! Here is the translation: ...`) or return a prose explanation. The mod now checks the reply: explanatory openers, code fences, output wildly disproportionate to the source, or damaged placeholders all cause the original message to be shown instead of dumping noise into chat.

### Supported Versions

| Minecraft | Loader | Java | Status |
|---|---|---:|---|
| 1.21.1 | Fabric | 21+ | Supported |
| 1.21.4 | Fabric | 21+ | Supported |
| 1.21.8 | Fabric | 21+ | Supported |
| 1.21.11 | Fabric | 21+ | Supported |
| 26.1.2 | Fabric | 25+ | Supported |
| 26.2 | Fabric | 25+ | Supported |
| 26.3 | Fabric | 25+ | Supported |

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
| 26.3 | 0.19.5 | 0.156.0+26.3 | 26.3.159 | 21.0.0 | 25+ |

Fabric API and Cloth Config are required. Mod Menu is recommended for access to the graphical configuration screen. Place the matching Chat Translation JAR and its dependencies in `.minecraft/mods/`.

### Translation Providers

| Provider | API Key | Notes |
|---|---|---|
| AI Free (built-in) | Not required | Default provider; built-in deepseek-v4.1-flash on api.mcapple.top with a shared quota and variable latency |
| Google Free | Not required | The unofficial public endpoint may be rate-limited |
| Bing Free | Not required | Third-party public endpoint whose availability depends on its server |
| MyMemory | Not required | Uses server-side automatic language detection and has a public quota |
| Lingva | Not required | Supports a configurable self-hosted Lingva instance |
| LibreTranslate | Optional | Requires an instance URL; some instances also require an API key |
| AI | Usually required | Supports OpenAI Compatible, Gemini, and Anthropic formats with your own endpoint |

The selected provider receives the chat text that needs translation. Google Free and Bing Free fall back to MyMemory when they fail. AI, AI Free, self-hosted Lingva, and LibreTranslate requests are never forwarded to another provider. The original message is retained or sent if every applicable provider fails.

### Security

- TLS certificate validation is enabled by default.
- Insecure SSL compatibility mode trusts any server certificate and should only be enabled temporarily while diagnosing certificate problems.
- The built-in AI Free key ships as AES-256-GCM ciphertext; the plaintext appears in no source file, config, or log.
- Your own API keys are stored locally in `config/chattranslation.json`; do not share this file.
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
- AI request timeout (default 45s)
- Insecure SSL compatibility mode

### Build

```powershell
cd "D:\mod\Chat Translation\<Minecraft-version>\fabric"
.\gradlew.bat build
```

Minecraft 1.21.x builds require Java 21 or newer. Minecraft 26.x builds require Java 25 or newer. Point `JAVA_HOME` or the system Java to the matching version. 26.x projects pin JDK 25 through `org.gradle.java.home` in `gradle.properties`.

Minecraft 26.3 uses Gradle 9.6.0 + Loom 1.17.21. Note that the `services.gradle.org` distribution redirects to `github.com`; if that host is unreachable, use a mirror with a matching checksum.

Output path pattern:

```text
<Minecraft-version>/fabric/build/libs/chattranslation-fabric+<Minecraft-version>-1.0.5-alpha.1.jar
```

### Privacy And Limitations

- Messages longer than 500 characters are not sent to a translation provider.
- At most 64 incoming translations are pending; the original message is kept when the queue is full.
- At most 3 incoming requests run concurrently, and display order always matches receive order.
- Free public endpoints may be rate-limited, changed, or temporarily unavailable.
- Client-side replacement translations are local system messages and do not preserve the server's message signature state.

### License

MIT. See [LICENSE](LICENSE).
