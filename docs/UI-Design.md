# 界面设计说明与原型规范

本章节用于描述用户界面的设计方案与原型图规范。原型图仅作为样式与布局参考，允许与最终实现存在差异，但应保持信息架构与交互逻辑的一致性。

## 设计目标与原则
- 清晰：优先信息层级与可读性，减少界面噪音。
- 一致：统一色彩、组件样式与交互反馈，降低学习成本。
- 高效：减少操作步骤，支持键盘与快速路径（如搜索、筛选）。
- 可访问：对比度达标、语义化元素、可聚焦、可通过屏幕阅读器理解。
- 响应式：在移动端、平板、桌面端保持布局合理与交互可用。

## 样式基线（建议）
- 色板：Primary（蓝）、Success（绿）、Info（青）、Warning（橙）、Danger（红），灰度 50–900。
- 排版：主字体 14–16px 行高 1.5；标题层级 H1–H6；字重 400/600。
- 间距：8px 基准网格；组件与区块之间遵循 8/16/24 间距体系。
- 栅格：12 列栅格；容器宽度随断点 xs/sm/md/lg 变化；侧栏在 md+ 显示。
- 组件库：按钮、输入框、选择器、表单分组、卡片、列表、分页、模态、标签、徽标、头像、进度条。
- 反馈规范：加载态（骨架屏/进度条）、空态（图文引导）、错误态（醒目提示与恢复动作）。

## 原型工具与流程（建议）
- 工具选型：Axure RP（交互强）、Figma（Auto Layout/组件变体）、Uizard/Galileo AI（快速稿）、Whimsical（低保真）。
- 流程：草图 → 低保真线框（信息架构）→ 高保真样式（色彩与组件）→ 走查与标注 → 交付。
- 版本与命名：`页面-版本-日期`；例如 `Assessment-v1-20260112`；与代码页面一一对应。
- 交互标注：点击/悬停/禁用/加载/错误五态；SSE 流式内容的分段呈现与完成标识。

## 页面原型说明（对应现有前端视图）
以下按实际页面组织目标、布局与交互要点，并提供原型图占位路径（将图像文件放入 `docs/prototypes/`）。

### 登录页（Login）
- 文件对应：[psychology-frontend/src/views/Login.vue](psychology-frontend/src/views/Login.vue)
- 目标：账号登录与角色选择；错误反馈清晰。
- 布局：左侧品牌与说明，右侧登录卡片（账号、密码、角色选择、登录按钮）。
- 交互：表单校验（必填/格式）、错误提示条；加载态按钮；记住角色可选。
- 状态：初始/提交中/错误/成功跳转。
- 原型占位：`docs/prototypes/login.png`

### 主页/总览（Main）
- 文件对应：[psychology-frontend/src/views/Main.vue](psychology-frontend/src/views/Main.vue)
- 目标：入口导航与关键模块概览（评估、咨询、社区、文章、个人中心）。
- 布局：顶部导航 + 主内容区卡片网格；可显示近期评估或公告。
- 交互：卡片点击进入模块；公告轮播/提示。
- 状态：空数据/有数据；权限差异（学生/管理员）。
- 原型占位：`docs/prototypes/main.png`

### 心理测评（Assessment）
- 文件对应：[psychology-frontend/src/views/Assessment.vue](psychology-frontend/src/views/Assessment.vue)
- 目标：题目作答、分维度评分与 AI 建议的流式呈现。
- 布局：题目列表（分页或滚动）、进度与维度概览、提交按钮；右侧建议面板（SSE）。
- 交互：题目作答状态、提交校验、SSE 分段追加与“完成”标识、错误重试。
- 状态：未开始/作答中/提交中/建议生成中/完成/错误。
- 原型占位：`docs/prototypes/assessment.png`

### 测评历史（AssessmentHistory）
- 文件对应：[psychology-frontend/src/views/AssessmentHistory.vue](psychology-frontend/src/views/AssessmentHistory.vue)
- 目标：按时间与维度查看历史记录，支持搜索、详情、删除。
- 布局：过滤条（搜索/时间范围）+ 列表 + 详情抽屉/模态 + 分页。
- 交互：点击查看详情（维度分布、建议摘要）、批量/单条删除、导出（可选）。
- 状态：空态、加载中、错误、操作成功提示。
- 原型占位：`docs/prototypes/assessment-history.png`

### AI 咨询（Consult AI）
- 文件对应：[psychology-frontend/src/views/AiAssistant.vue](psychology-frontend/src/views/AiAssistant.vue)
- 目标：与 AI 助手会话，SSE 流式消息渲染与会话管理。
- 布局：顶部会话信息 + 消息时间线（左右气泡）+ 底部输入区；侧栏显示历史与会话操作。
- 交互：发送/流式接收、重试、清空会话、超时处理、滚动定位至最新。
- 状态：空会话/连接中/流式中/完成/错误/超时。
- 原型占位：`docs/prototypes/consult-ai.png`

### 一对一咨询（ConsultChat）
- 文件对应：[psychology-frontend/src/views/ConsultChat.vue](psychology-frontend/src/views/ConsultChat.vue)
- 目标：专家咨询申请与状态跟踪。
- 布局：申请表单 + 进度状态卡片 + 提交确认与审核结果说明。
- 交互：表单校验、申请提交、状态轮询或刷新、撤回与重新申请。
- 状态：未申请/审核中/已安排/拒绝/完成。
- 原型占位：`docs/prototypes/consult-chat.png`

### 社区（Community）
- 文件对应：[psychology-frontend/src/views/Community.vue](psychology-frontend/src/views/Community.vue)
- 目标：帖子浏览、发布、互动（点赞/回复），管理员置顶/锁帖。
- 布局：顶部筛选与发布入口 + 帖子列表（卡片）+ 详情/回复区。
- 交互：发布模态、回复线程、点赞节流与重复保护、管理员操作入口。
- 状态：空列表/加载中/错误/操作成功。
- 原型占位：`docs/prototypes/community.png`

### 文章库（Articles）
- 文件对应：[psychology-frontend/src/views/Articles.vue](psychology-frontend/src/views/Articles.vue)
- 目标：文章浏览与筛选、点赞；管理员审核与上下架。
- 布局：筛选条 + 文章卡片网格 + 详情/审核面板（管理员）。
- 交互：点赞防重、提交待审、审核通过/拒绝、编辑更新、取消发布。
- 状态：空态/加载中/错误/审核提示与结果反馈。
- 原型占位：`docs/prototypes/articles.png`

### 个人中心（Profile）
- 文件对应：[psychology-frontend/src/views/Profile.vue](psychology-frontend/src/views/Profile.vue)
- 目标：查看与更新个人信息与偏好设置。
- 布局：信息卡片 + 可编辑表单 + 保存与恢复默认。
- 交互：表单校验、保存反馈、角色显示、头像上传（可选）。
- 状态：查看/编辑中/保存中/成功/错误。
- 原型占位：`docs/prototypes/profile.png`

## 原型与实现的偏差声明（写作建议）
- 原型强调信息架构与视觉方向；实现阶段可对细节与交互进行迭代优化。
- 任意差异需在文档中记录：变更原因（技术限制、可用性测试结果、性能考虑）与变更项（布局、组件、文案）。
- 保持关键用户流程与可达性不变（例如评估提交、AI 咨询的流式反馈与完成提示）。

## 交付物清单
- 原型图：放置于 `docs/prototypes/`，命名与页面对应。
- 说明文档：本文件（结构、规范、页面说明、偏差声明）。
- 标注文件（可选）：Figma 标注/切图或 Axure 元件说明。

## 后续工作（可选）
- 依据本规范完善高保真原型，并进行小规模可用性走查。
- 将样式基线转换为 CSS 变量与设计令牌，统一主题与组件细节。
