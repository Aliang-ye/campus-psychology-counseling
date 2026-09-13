# 校园心理咨询评估系统

> **个人项目** · 独立开发  
> Spring Boot 3 + Vue 3 · 前后端分离  
> 心理测评 · AI 辅助疏导 · 危机信号提示 · 专家咨询 · 社区 / 文章管理

[![Java](https://img.shields.io/badge/Java-18-orange)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)]()
[![Vue](https://img.shields.io/badge/Vue-3-brightgreen)]()
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

---

## 为什么做这个项目

校园心理服务常有 **预约难、评估分散、危机难以及时发现** 等问题。本系统把 **标准化测评、AI 初筛疏导、专家转介、内容运营** 放在同一平台，学生可自助完成「测 → 聊 → 必要时找人」闭环。

| 模块 | 我做了什么 |
|------|------------|
| 多角色认证 | 学生 / 专家 / 管理员；**PBKDF2 密码哈希** + **JWT**（`Authorization: Bearer`） |
| 心理测评 | 场景量表 + **SCL-90** 计分、历史与权限可见范围 |
| AI 小助手 | 对接百度千帆，**SSE 流式回复**，会话落库 |
| 危机提示 | 关键词检测 → 系统提醒 + 引导申请专家（**辅助转介，不替代专业干预**） |
| 实时咨询 | WebSocket 师生对话；**发送方身份取自连接上下文**，不信任客户端自报 senderId |
| 社区 / 文章 | 发帖回复、匿名、审核；文章投稿 / 审核 / 下架 / 点赞防重 |

---

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 3.2 · WebSocket · SSE · Redis · MySQL · JdbcTemplate · JWT · PBKDF2 |
| 前端 | Vue 3 · Vue Router · Vite |
| AI | 百度千帆（ERNIE）OpenAI 兼容接口 · 流式输出 |
| 其它 | Docker Compose（Redis，可选） |

---

## 架构

```text
Vue3 SPA  ── REST / SSE ──►  Spring Boot
              │                    ├── MySQL（用户/测评/帖子/文章/会话）
              │                    ├── Redis（缓存、WS 消息协作）
              └── WebSocket ──►  ChatHandler（session → userId）
                                       └── 千帆大模型 API
```

**鉴权简图：** 登录签发 JWT → 前端 `sessionStorage` 保存 → 全局 `fetch` 自动带 `Authorization: Bearer` → 拦截器校验（登录/公开接口除外；WebSocket 用 `?token=`）。

---

## 安全设计（可讲点）

1. **密码**：PBKDF2 存储；兼容旧明文数据并在登录成功后自动升级哈希  
2. **接口**：除登录注册外，需有效 JWT；身份取自服务端，不信任客户端自报 `userId/role`  
3. **WebSocket**：连接必须带 token，消息 `senderId` 由服务端连接绑定推导  
4. **业务权限**：测评/聊天/AI 会话、删帖、文章审核等校验角色与作者  
5. **危机**：仅做关键词辅助提示与转介引导，不宣称医疗诊断  

> 配置文件中的 Key 均为占位符，请用环境变量注入。生产还可继续加强：HTTPS、刷新令牌、更细粒度 RBAC、审计日志等。

---

## 快速开始

### 环境

JDK 18+ · Node 18+ · MySQL 8 · Redis

### 1. 数据库

```bash
# 详见 psychology-backend/sql/README.md
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS test DEFAULT CHARSET utf8mb4;"
mysql -u root -p test < psychology-backend/sql/assessment_and_chat_schema.sql
# 其余 schema / add_*.sql 按需执行
```

### 2. 配置

编辑 `psychology-backend/src/main/resources/application.yml`（或环境变量）：

| 变量 | 说明 |
|------|------|
| `DB_USERNAME` / `DB_PASSWORD` | MySQL |
| `QIANFAN_API_KEY` | 千帆 API Key |
| Redis | 默认 `localhost:6379` |

```bash
# 可选：仅起 Redis
docker compose up -d
```

### 3. 启动

```bash
# 后端 http://localhost:8080
cd psychology-backend && mvn spring-boot:run

# 前端 http://localhost:5173
cd psychology-frontend && npm install && npm run dev
```

### 4. 建议演示路径

1. 学生注册登录 → 完成测评 / SCL-90 → 看历史  
2. AI 对话（可测危机关键词提示）→ 申请专家  
3. 专家 / 管理员：处理申请、审核文章与帖子  

（效果图可放 `docs/screenshots/`，答辩时展示更直观。）

---

## 目录

```text
├── psychology-backend/     # Spring Boot API · security · WebSocket · AI
├── psychology-frontend/    # Vue3 页面
├── docs/                   # 危机检测说明、UI 设计等
├── docker-compose.yml      # Redis
└── README.md
```

---

## 个人说明

- **个人项目，本人独立完成**（需求、库表、后端、前端、AI 接入与联调）  
- 非医疗机构正式诊疗系统；量表与大模型服务请遵守各自使用条款  

## License

MIT
