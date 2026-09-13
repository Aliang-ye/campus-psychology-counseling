# 校园心理咨询评估系统

> **个人项目** · 独立开发  
> Spring Boot 3 + Vue 3 · 前后端分离  
> 心理测评 · AI 辅助疏导 · 危机信号提示 · 专家咨询 · 社区 / 文章管理

[![Java](https://img.shields.io/badge/Java-18-orange)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)]()
[![Vue](https://img.shields.io/badge/Vue-3-brightgreen)]()
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

---

## 项目简介

校园心理服务常有 **预约难、评估分散、危机难以及时发现** 等问题。本系统把 **标准化测评、AI 初筛疏导、专家转介、内容运营** 放在同一平台，学生可自助完成「测 → 聊 → 必要时找人」闭环。

系统面向三类角色：

| 角色 | 说明 |
|------|------|
| 学生 `student` | 完成测评、使用 AI 助手、申请专家、发帖看文章 |
| 咨询师 `doctor` / `teacher` | 接收咨询请求、一对一实时对话、查看学生测评 |
| 管理员 `admin` | 审核帖子 / 文章、管理用户、查看评价 |

> 本系统是毕业设计 / 个人项目，**不是医疗机构正式诊疗系统**。AI 与量表结果仅供参考，危机检测只做辅助转介，不替代专业干预。

---

## 功能一览

| 模块 | 当前实现 |
|------|----------|
| 多角色认证 | 学生 / 咨询师 / 管理员；**PBKDF2 密码哈希** + **JWT**（`Authorization: Bearer`） |
| 心理测评 | 校园场景 30 题量表（反向计分 + 权重）+ **SCL-90**；历史、详情、删除按当前登录用户授权 |
| AI 小助手 | 对接百度千帆，**SSE 流式回复**，会话落库；危机关键词检测后给固定短回复并引导申请专家 |
| 实时咨询 | 用户大厅匹配 + WebSocket 一对一对话；连接必须带 JWT，发送方身份由服务端推导 |
| 社区 / 文章 | 发帖回复、匿名、审核、置顶/锁帖；文章投稿 / 审核 / 下架 / 点赞 |
| 个人中心 | 查看资料、修改密码；管理员可维护用户 |

---

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 3.2 · WebSocket · SSE · Redis · MySQL · JdbcTemplate · JWT · PBKDF2 |
| 前端 | Vue 3 · Vue Router · Vite 5 |
| AI | 百度千帆（ERNIE）OpenAI 兼容接口 · 流式输出 |
| 其它 | Docker Compose（Redis，可选） |

---

## 架构

```text
Vue3 SPA
  ├── REST / SSE  ──►  Spring Boot Controllers
  │                      ├── AuthService / UserService
  │                      ├── ConsultAiSessionService / AiService
  │                      ├── MySQL（用户 / 测评 / 帖子 / 文章 / 会话）
  │                      └── Redis（WS 消息协作）
  └── WebSocket   ──►  ChatWebSocketHandler（token → userId）
                         └── 千帆大模型 API
```

当前后端分层：

```text
controller/     接收请求，做参数与权限入口
service/        登录、用户、AI 会话、大模型调用
repository/     用户数据访问
security/       JWT、密码哈希、拦截器、当前用户上下文
websocket/      实时咨询连接与 Redis 订阅
```

**鉴权流程：** 登录签发 JWT → 前端 `sessionStorage` 保存 → `apiClient.js` 给 `/api` 请求自动加 `Authorization: Bearer` → `AuthInterceptor` 校验（登录 / 注册除外）→ 业务接口从 `AuthContext` 取当前用户，不信任客户端自报的 `userId` / `role`。WebSocket 使用 `/ws/chat?token=...`。

---

## 安全设计

1. **密码**：PBKDF2 存储；旧明文密码仍可登录一次，成功后自动升级为哈希  
2. **接口**：除登录注册外需有效 JWT；测评、聊天、AI 会话、删帖、审核等按当前用户授权  
3. **WebSocket**：必须带 token；`senderId` 由服务端连接绑定推导，非会话成员不能发消息  
4. **管理员操作**：文章 / 社区审核不再信任前端传来的 `role=admin`  
5. **危机**：仅做关键词辅助提示与转介引导，不宣称医疗诊断  
6. **配置**：公开仓库中的 Key 均为占位符；本地密钥放在 `application-local.yml`（已加入 `.gitignore`）

生产环境还可继续加强：HTTPS / WSS、密钥轮换、日志脱敏、更细粒度 RBAC、审计日志等。

---

## 前端页面与路由

| 路由 | 页面 | 文件 |
|------|------|------|
| `/login` | 登录 / 注册 | `Login.vue` |
| `/main` | 爱心形主入口 | `Main.vue` |
| `/profile` | 个人中心 | `Profile.vue` |
| `/assessment` | 校园场景测评 | `Assessment.vue` |
| `/assessment/scl90` | SCL-90 | `Scl90Assessment.vue` |
| `/assessment-history` | 测评历史 | `AssessmentHistory.vue` |
| `/consult` | 咨询服务中心 | `Consult.vue` |
| `/articles` | 文章库 | `Articles.vue` |
| `/community` | 社区 | `Community.vue` |
| `/consult/hall` | 咨询用户大厅 | `ConsultHall.vue` |
| `/consult/chat` | 一对一咨询 | `ConsultChat.vue` |
| `/ai-assistant` | AI 小助手 | `AiAssistant.vue` |

旧路径仍兼容跳转：`/ai`、`/article-library`、`/consult-connect`、`/consult-service`。

前端开发时由 Vite 代理 `/api` 和 `/ws` 到 `localhost:8080`，页面里不再写死后端地址。

---

## 快速开始

### 环境

JDK 18+ · Node 18+ · MySQL 8 · Redis

### 1. 数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS test DEFAULT CHARSET utf8mb4;"
mysql -u root -p test < psychology-backend/sql/assessment_and_chat_schema.sql
# 其余 schema / add_*.sql 按需执行
```

### 2. 配置

推荐复制示例配置为本地文件（**不要提交**）：

```bash
cp psychology-backend/src/main/resources/application-example.yml \
   psychology-backend/src/main/resources/application-local.yml
```

也可直接设置环境变量：

| 变量 | 说明 |
|------|------|
| `DB_USERNAME` / `DB_PASSWORD` | MySQL |
| `JWT_SECRET` | JWT 签名密钥 |
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

登录成功后必须拿到 JWT。旧的无 token 会话会失效，需要重新登录。

### 4. 建议演示路径

1. 学生注册登录 → 完成校园场景测评 / SCL-90 → 查看历史  
2. 进入 AI 小助手对话（可测危机关键词提示）→ 申请专家  
3. 咨询师在用户大厅接受请求 → 一对一实时咨询  
4. 管理员审核文章与帖子、维护用户  

---

## 目录

```text
├── psychology-backend/          Spring Boot API
│   ├── src/main/java/.../controller
│   ├── src/main/java/.../service
│   ├── src/main/java/.../repository
│   ├── src/main/java/.../security
│   ├── src/main/java/.../websocket
│   ├── src/main/resources       application.yml（占位符）
│   └── sql                      建表脚本
├── psychology-frontend/         Vue 3 页面
│   └── src/views
├── docs/                        UI 设计与原型说明
├── docker-compose.yml           Redis
└── README.md
```

更多说明：

- [危机检测说明](docs/crisis-detection.md)
- [界面设计与原型](docs/UI-Design.md)
- [主页爱心布局](docs/ui-heart-design.md)

---

## 个人说明

- **个人项目，本人独立完成**（需求、库表、后端、前端、AI 接入与联调）  
- 非医疗机构正式诊疗系统；量表与大模型服务请遵守各自使用条款  

## License

MIT
