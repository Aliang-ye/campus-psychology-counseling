# 危机检测与应对

AI 小助手会在对话中做**关键词辅助检测**。检测到高危表达后，系统会打断普通闲聊式长回复，改为固定短回复，并引导用户申请心理专家。

这是辅助转介，**不能替代专业评估或紧急救助**。

## 检测方式

`AiService` 对用户消息做中文关键词匹配，例如：

- 自杀、自伤、割腕、喝药、跳楼、想死
- 没活着的意义、生不如死、没有希望
- 无法承受、完全崩溃、无法活下去
- 太痛苦、无法忍受、每天都是折磨

命中后会：

1. 后台打印危机检测日志  
2. 前端可看到系统提醒  
3. AI 使用危机提示词，并优先输出固定短回复  
4. 引导点击「申请与心理专家对话」

固定短回复大意：

> 我听到了你现在非常痛苦……建议尽快联系专业心理专家。你可以点击【申请与心理专家对话】。是否申请由你决定。

## 相关接口

均需登录 JWT，会话归属由服务端校验：

- `POST /api/consult/ai/session`
- `POST /api/consult/ai/chat`（SSE）
- `POST /api/consult/ai/request-expert`
- `GET /api/consult/ai/request-status`

前端页面：[`psychology-frontend/src/views/AiAssistant.vue`](../psychology-frontend/src/views/AiAssistant.vue)

## 限制

- 当前是字符串匹配，隐喻或英文表达可能漏报  
- 不会自动派单给值班咨询师，需要用户主动申请  
- 不把检测结果写成医学诊断  

后续可考虑：危机事件落库审计、管理员监控面板、分级响应、更细的意图识别。

**相关代码：** [`AiService.java`](../psychology-backend/src/main/java/com/example/psychology/service/AiService.java)
