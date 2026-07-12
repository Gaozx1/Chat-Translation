# Minecraft 26.1.2 更新日志

## Chat Translation 1.0.3-fix1

- 发布日期：2026-07-12
- 适用版本：Minecraft 26.1.2
- 加载器：Fabric

### 新增

- 新增“翻译发出的消息”开关，默认关闭。
- 新增独立的发出消息目标语言选项，默认翻译为英语。

### 行为说明

- 开启后，普通聊天消息会先异步翻译，再发送翻译结果。
- 命令消息不会被翻译。
- 当前玩家发送后由服务器回显的消息会直接显示，不会再次进入接收翻译。
- 翻译失败、返回无效内容或译文超过聊天长度限制时，会自动发送原文。
- 连续发送多条消息时会保持原有顺序，并避免翻译后的消息被重复拦截。

### 修复

- 修复 MyMemory 将“请选择两种不同的语言”等接口错误提示当作译文发送的问题。
- MyMemory 自动源语言现在使用接口的自动检测，不再错误地固定为英语。

## Chat Translation 1.0.2

- 发布日期：2026-07-04
- 适用版本：Minecraft 26.1.2
- 加载器：Fabric

### 新增

- 新增 Google Free、Bing Free、MyMemory、Lingva 和 LibreTranslate 翻译服务。
- 新增统一 AI 翻译模式，支持 OpenAI Compatible、Gemini 和 Anthropic 格式。
- 新增 Mod Menu 与 Cloth Config 图形配置界面。
- 新增简体中文、英语、德语、法语、日语和韩语界面文本。
- 新增自动目标语言功能，可跟随 Minecraft 当前语言。
- 新增是否显示原文、单行显示、翻译格式和系统消息翻译选项。

### 改进

- 保护玩家名、Rank 标签、URL、命令和带交互样式的聊天内容。
- 翻译失败或接口返回异常时保留原消息。
- 增加 Lingva 与 LibreTranslate 自定义实例地址配置。
- 增加可选 SSL 兼容模式，用于排查本地证书链问题。
- 优化多语言代码映射和免费翻译服务的响应解析。

### 配置说明

- 默认翻译服务：`google_free`
- 目标语言：`auto`，跟随游戏语言
- Java 要求：Java 25 或更高版本
- 配置文件：`.minecraft/config/chattranslation.json`

### 已知限制

- 免费公共接口可能受到地区网络、限流或服务端变更影响。
- LibreTranslate 需要填写可用实例地址，部分实例还需要 API Key。
- AI 模式需要用户自行提供接口地址、API Key 和模型 ID。
