# [OPEN] chat-receive-bug

## 症状
- 进入游戏后 mod 仍然无法接收或显示聊天翻译消息。

## 当前结论
- 尚未确认是 mod 未加载、事件未触发、翻译请求失败，还是消息发送阶段被过滤。

## 可证伪假设
- 假设 1：1.21.4 fabric 版本的 mod 没有被 Fabric 正常加载，导致初始化日志和事件注册都未发生。
- 假设 2：mod 已加载，但 `ClientReceiveMessageEvents.CHAT` / `GAME` 在当前版本签名或行为上与预期不一致，事件没有触发。
- 假设 3：事件已触发，但 `handleMessage` 的前置过滤条件拦截了消息，例如空字符串、长度限制或去重逻辑。
- 假设 4：翻译请求已发出，但 Google 翻译返回错误或超时，结果被 `translatedText.startsWith("[")` 过滤。
- 假设 5：翻译结果已生成，但 `client.inGameHud.getChatHud().addMessage(...)` 没有把文本显示到当前聊天 HUD。

## 调试计划
- 在初始化、事件进入、翻译前后、消息展示前添加最小埋点。
- 重新构建 1.21.4 fabric 并让用户复现。
- 根据日志证据判定根因，再做最小修复。
