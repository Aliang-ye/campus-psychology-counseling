# 界面设计说明与原型规范

原型图仅作为样式与布局参考，允许与最终实现存在差异，但应保持信息架构与交互逻辑一致。

## 设计原则

- 清晰：优先信息层级与可读性
- 一致：统一色彩、组件与反馈
- 高效：减少操作步骤
- 温暖：主页爱心布局，咨询场景避免冰冷后台感
- 响应式：移动端、平板、桌面均可用

当前实现以玫红 `#ff4d7d` 为强调色，登录后进入爱心形主入口，再进入各业务页。

## 页面与文件对应

| 页面 | 路由 | 文件 |
|------|------|------|
| 登录 / 注册 | `/login` | `psychology-frontend/src/views/Login.vue` |
| 主入口 | `/main` | `psychology-frontend/src/views/Main.vue` |
| 个人中心 | `/profile` | `psychology-frontend/src/views/Profile.vue` |
| 校园场景测评 | `/assessment` | `psychology-frontend/src/views/Assessment.vue` |
| SCL-90 | `/assessment/scl90` | `psychology-frontend/src/views/Scl90Assessment.vue` |
| 测评历史 | `/assessment-history` | `psychology-frontend/src/views/AssessmentHistory.vue` |
| 咨询服务中心 | `/consult` | `psychology-frontend/src/views/Consult.vue` |
| 文章库 | `/articles` | `psychology-frontend/src/views/Articles.vue` |
| 社区 | `/community` | `psychology-frontend/src/views/Community.vue` |
| 咨询大厅 | `/consult/hall` | `psychology-frontend/src/views/ConsultHall.vue` |
| 一对一咨询 | `/consult/chat` | `psychology-frontend/src/views/ConsultChat.vue` |
| AI 小助手 | `/ai-assistant` | `psychology-frontend/src/views/AiAssistant.vue` |

旧路由 `/ai`、`/article-library`、`/consult-connect`、`/consult-service` 会重定向到上表。

### 登录页

账号、密码、角色选择；注册默认学生角色。成功后保存 JWT 与用户信息，再进入主页。

### 主页

爱心形四个入口：个人中心、心理测评、咨询服务、AI 小助手。详见 [ui-heart-design.md](ui-heart-design.md)。

### 心理测评

30 题校园场景量表，提交后展示维度分与 AI 建议（SSE）。另有 SCL-90 独立页面。历史记录仅本人可见，咨询师 / 管理员可按权限查看。

### AI 小助手

流式对话、历史回放、清空会话。检测到危机关键词时给出系统提醒和专家申请入口。

### 咨询服务

咨询中心扇形菜单进入文章库、社区、用户大厅。大厅发起沟通，一对一页用 WebSocket 收发消息；管理员可看评价。

### 社区 / 文章

发帖可匿名，需审核后公开。管理员可审核、置顶、锁帖。文章支持投稿、审核、下架和点赞。

### 个人中心

查看当前登录用户资料、修改密码。管理员可维护其他用户。

## 原型图

将 PNG / JPG / SVG 放到 `docs/prototypes/`，建议命名：

- `login.png`
- `main.png`
- `assessment.png`
- `scl90.png`
- `assessment-history.png`
- `consult.png`
- `ai-assistant.png`
- `consult-hall.png`
- `consult-chat.png`
- `community.png`
- `articles.png`
- `profile.png`

## 实现偏差

当前实现已采用爱心主页、JWT 登录态和上述路由命名；若高保真原型仍使用旧文件名或卡片宫格主页，以本文件和代码为准。
