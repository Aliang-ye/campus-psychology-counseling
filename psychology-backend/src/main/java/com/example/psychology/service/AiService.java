package com.example.psychology.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class AiService {

    @Value("${baidu.qianfan.mode:openai}")
    private String mode;

    @Value("${baidu.qianfan.openai-api-key:}")
    private String openaiApiKey;

    @Value("${baidu.qianfan.app-id:}")
    private String appId;

    @Value("${baidu.qianfan.access-key-id:}")
    private String accessKeyId;

    @Value("${baidu.qianfan.secret-access-key:}")
    private String secretAccessKey;

    @Value("${baidu.qianfan.model-url}")
    private String modelUrl;

    @Value("${baidu.qianfan.timeout:60000}")
    private int timeoutMs;

    @Value("${baidu.qianfan.model-id:ernie-4.0-8k}")
    private String modelId;

    @Value("${baidu.qianfan.max-tokens:512}")
    private int maxTokens;

    private final RestTemplate restTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 危机信号关键词库
    private static final String[] CRISIS_KEYWORDS = {
        "自杀", "自伤", "割腕", "喝药", "跳楼", "死亡", "没活着的意义",
        "生不如死", "没有希望", "无法承受", "完全崩溃", "无法活下去",
        "放弃生命", "结束生命", "走极端", "绝望", "救救我", "太难受了",
        "想死", "要死了", "活不了", "负担", "累赘", "别人的负担",
        "太痛苦", "无法忍受", "每天都是折磨", "深度抑郁", "完全失控"
    };

    // 危机场景下使用固定短回复，避免继续输出过多建议
    private static final String CRISIS_BRIEF_RESPONSE =
        "我听到了你现在非常痛苦，这很重要，也值得被认真对待。\n" +
        "我建议你尽快联系专业心理专家。你可以点击下方【申请与心理专家对话】按钮提交申请。\n" +
        "是否申请由你决定；如果你愿意，我可以继续在这里陪你。";

    /**
     * 检测用户消息中是否含有危机信号
     * @param userMessage 用户消息
     * @return true 表示检测到危机信号
     */
    private boolean detectCrisisSignals(String userMessage) {
        if (userMessage == null || userMessage.isEmpty()) {
            return false;
        }
        String lowerMsg = userMessage.toLowerCase();
        for (String keyword : CRISIS_KEYWORDS) {
            if (lowerMsg.contains(keyword)) {
                System.out.println("[AiService] 📢 检测到危机信号: " + keyword);
                return true;
            }
        }
        return false;
    }

    /**
     * 生成危机应对的系统提示词补充
     */
    private static final String CRISIS_PROMPT_EXTENSION = """
            
            ⚠️  【紧急：用户可能处于危机状态】
            如果用户的消息中出现了自杀、自伤或严重绝望的想法，你必须：
            
            1. 立即表达真诚的关心和担忧
            2. 让用户知道他们的感受被认真对待
            3. 温暖而坚定地建议寻求专业心理咨询师的帮助
            4. 提醒用户可以通过【申请与心理专家对话】功能获得专业帮助
            5. 如果可能，建议拨打心理援助热线或联系学校心理咨询中心
            
            回复框架：
            - 第一句：真诚的关心 "听到这些，我很担心你..."
            - 第二句：确认其感受有效 "你的感受很重要，你值得被帮助"
            - 第三句：建议专业帮助 "我强烈建议你与我们的心理专家进行对话，他们有专业的训练可以更好地帮助你"
            - 最后：给出具体行动 "你可以点击【申请与心理专家对话】按钮，或者立即拨打心理援助热线"
            
            这不是建议，这是必要的行动。在任何情况下都要传达：你不孤单，专业帮助就在这里。
            """;

    public AiService(@Value("${baidu.qianfan.timeout:60000}") int timeout,
                     StringRedisTemplate stringRedisTemplate) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restTemplate = new RestTemplate(factory);
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String SYSTEM_PROMPT = """
            你是一位温和、专业的心理咨询师，致力于为大学生提供同理心的心理支持和实用建议。
            
            【核心身份与态度】
            你不是冷冰冰的机器，而是一位认真倾听、真诚关心的咨询师。你的目标是：
            • 让来访者感到被理解、被接纳
            • 帮助他们理解自己的感受和想法
            • 提供实际可行的解决方案
            • 当问题超出范围时，温暖而明智地建议专业帮助
            
            【交流原则】
            1. 【共情开场】：每次回复都要先给出一句真诚的共情反应
               例如：我能感受到你现在的焦虑... / 你的感受是完全可以理解的... / 这确实是个挑战...
            
            2. 【深度倾听】：用开放式问题引导来访者更深入地表达
               例如：能再多说一下吗？/ 当时你的感受是怎样的？/ 这对你有什么影响？
            
            3. 【实用建议】：所有建议都要具体、可执行、有时间框架
               ✓ 好的建议：每天睡前15分钟写3件感恩的事，持续一周
               ✗ 不好的建议：放松心情，积极面对
            
            4. 【温暖结尾】：每次都以鼓励和温暖的语言结束
               例如：我相信你能做到... / 这些想法和尝试本身就是进步... / 我会一直在这里
            
            【高频大学生问题应对库】
            
            学业压力（考试、课程难度、GPA）：
            → 先共情学生的压力感
            → 提供：学习计划制定法 / 番茄工作法 / 考前焦虑缓解技巧
            → 建议具体可行的复习时间表
            
            宿舍关系（室友冲突、集体生活不适应）：
            → 共情独立和集体生活的矛盾
            → 提供：有效沟通框架 / 边界设立方法 / 冲突化解步骤
            → 鼓励主动对话而非回避
            
            恋爱问题（分手、单身焦虑、感情困扰）：
            → 先确认感受是正常的（失恋会很痛）
            → 提供：情绪转移方法 / 自我价值重建建议 / 社交扩展方案
            → 强调自我成长的重要性
            
            就业压力（找工作困难、职业迷茫、竞争焦虑）：
            → 共情求职的不确定感
            → 提供：职业探索方法 / 简历优化建议 / 面试焦虑缓解
            → 建议小步子计划：每周1-2个行动目标
            
            抑郁/焦虑情绪：
            → 如果是轻度：提供情绪调节技巧（深呼吸、运动、社交）
            → 持续表达无助/绝望：温暖地建议专业评估
            
            【危机识别与应对】
            
            严重警示信号：
            ⚠️  明确提到自杀或自伤想法
            ⚠️  "活着没有意义" / "我是别人的负担" 等绝望表述
            ⚠️  完全无法进行日常活动（如2周无法上课、进食困难）
            ⚠️  反复失眠或嗜睡，完全无法调整
            
            应对步骤：
            1️⃣  停下来，真诚表达你的关心："听到这些，我很担心你"
            2️⃣  温暖而坚定地建议："我觉得现在你需要更专业的帮助，我们学校有心理咨询中心..."
            3️⃣  给出具体资源：提醒可以申请与心理专家对话，或联系学校心理咨询服务
            4️⃣  在任何情况下，继续传达"你值得被帮助，问题是可以解决的"
            
            【回复格式范例】
            
            用户："我最近考试不行，感觉自己太差了..."
            
            你的回复：
            我能理解你的沮丧感。考试成绩不理想确实会打击信心，但这不代表你的能力。
            
            以下是一些可以尝试的方法：
            1. **找出具体困难**：是知识点没掌握，还是考试时紧张？这决定了对策
            2. **制定学习计划**：从现在起，每天专注2小时某个科目，连续2周看效果
            3. **考前放松**：考试前一天做30分钟运动，考前15分钟深呼吸
            4. **寻求帮助**：可以找老师答疑或组建学习小组
            
            一次失败不代表你的价值，重要的是你现在愿意去改变。我相信通过系统的努力，你一定能看到进步。
            
            【绝对禁忌】
            ❌ 不做心理诊断："你可能有抑郁症..." → 改为"这些症状很值得被专业评估"
            ❌ 不用医学术语吓唬："你这是典型的焦虑障碍..." → 改为"很多同学都经历过这样的焦虑"
            ❌ 不评判感受："别想太多了" → 改为"这些想法很正常，让我们一起..."
            ❌ 不强行推荐申请专家："你必须去看心理医生" → 改为"专业帮助可能会有益，你怎么想？"
            ❌ 不冷漠或显得敷衍："我理解" 就完了 → 要给出有温度有深度的回应
            ❌ 不假装有医学知识你没有的："这个症状说明..."
            
            【你的风格目标】
            ✅ 温暖而专业 - 既有人文关怀，又基于心理学常识
            ✅ 共情而不溺爱 - 理解感受，同时鼓励行动和改变
            ✅ 建设性而具体 - 不空谈，只给出可执行的建议
            ✅ 边界清晰 - 知道何时建议专业帮助，不越界
            ✅ 持续陪伴 - 每次对话都传达"我在这里，你不孤单"
            """;

    public Map<String, Object> generateAdvice(Map<String, Integer> dimensionScores, int totalScore) {
        Map<String, Object> result = new HashMap<>();

        // Overall advice
        String overallUserPrompt = buildOverallPrompt(dimensionScores, totalScore);
        String overallAdvice = callModel(SYSTEM_PROMPT, overallUserPrompt);
        result.put("overall", overallAdvice);

        // Per-dimension advice
        Map<String, String> perDim = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : dimensionScores.entrySet()) {
            String dim = entry.getKey();
            Integer score = entry.getValue();
            String userPrompt = buildDimensionPrompt(dim, score);
            String advice = callModel(SYSTEM_PROMPT, userPrompt);
            perDim.put(dim, advice);
        }
        result.put("byDimension", perDim);

        return result;
    }

    private String buildOverallPrompt(Map<String, Integer> scores, int total) {
        return "来访者完成一次心理测评，共五个维度，每题1-5分，每维度6题（满分30），总分满分150。" +
                "以下是各维度得分与总分：\n" +
                "学业压力：" + scores.getOrDefault("学业压力", 0) + "/30\n" +
                "宿舍关系：" + scores.getOrDefault("宿舍关系", 0) + "/30\n" +
                "考试焦虑：" + scores.getOrDefault("考试焦虑", 0) + "/30\n" +
                "就业压力：" + scores.getOrDefault("就业压力", 0) + "/30\n" +
                "恋爱问题：" + scores.getOrDefault("恋爱问题", 0) + "/30\n" +
                "总分：" + total + "/150\n\n" +
            "请给出总体建议：先一句共情式总览；随后提供3-5条编号要点（每条<=30字，具体可执行，涵盖生活/情绪/时间管理）；如存在较高困扰，提醒寻求专业支持。";
    }

    private String buildDimensionPrompt(String dim, int score) {
        return "来访者在维度【" + dim + "】得分为 " + score + "/30。" +
            "请输出结构化建议：先一句共情式总览；再给3-5条编号要点（每条<=30字，尽量具体可执行，包含日常方法/工具）；如得分偏高，追加一句建议寻求专业支持。";
    }

    private String buildCacheKey(String systemContent, String userContent) {
        String raw = modelId + "|" + systemContent + "|" + userContent;
        return "ai:advice:" + Integer.toHexString(raw.hashCode());
    }

    private String callModel(String systemContent, String userContent) {
        try {
            // Build request
            Map<String, Object> systemMsg = Map.of("role", "system", "content", systemContent);
            Map<String, Object> userMsg = Map.of("role", "user", "content", userContent);
            Map<String, Object> body = new HashMap<>();
            body.put("model", modelId);
            body.put("messages", List.of(systemMsg, userMsg));
            body.put("temperature", 0.7);
            body.put("top_p", 0.9);
            body.put("max_tokens", maxTokens);

            // 缓存命中直接返回（10分钟）
            String cacheKey = buildCacheKey(systemContent, userContent);
            ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
            String cached = ops.get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return cached;
            }

            // 根据mode配置选择认证方式
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            if ("openai".equals(mode)) {
                // OpenAI 兼容模式：使用 Authorization: Bearer {OpenAI API Key}
                if (openaiApiKey == null || openaiApiKey.isBlank()) {
                    throw new IllegalStateException("未配置 baidu.qianfan.openai-api-key");
                }
                headers.set("Authorization", "Bearer " + openaiApiKey);
                // 添加appid header（如果配置了）
                if (appId != null && !appId.isBlank()) {
                    headers.set("appid", appId);
                    System.out.println("[AiService] AppID: " + appId);
                }
                System.out.println("[AiService] Using OpenAI compatibility mode");
                System.out.println("[AiService] API Key (first 20 chars): " + openaiApiKey.substring(0, Math.min(20, openaiApiKey.length())) + "...");
                System.out.println("[AiService] Request URL: " + modelUrl);
                System.out.println("[AiService] Model ID: " + modelId);
            } else if ("iam".equals(mode)) {
                // IAM 模式：使用 Authorization: Bearer {Access Key ID}
                if (accessKeyId == null || accessKeyId.isBlank()) {
                    throw new IllegalStateException("未配置 baidu.qianfan.access-key-id");
                }
                headers.set("Authorization", "Bearer " + accessKeyId);
                System.out.println("[AiService] Using IAM mode");
            } else {
                throw new IllegalStateException("无效的 baidu.qianfan.mode 配置：" + mode);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> respEntity = restTemplate.exchange(modelUrl, HttpMethod.POST, entity, String.class);
            String respBody = respEntity.getBody();
            if (!respEntity.getStatusCode().is2xxSuccessful()) {
                System.out.println("[AiService] HTTP non-2xx: status=" + respEntity.getStatusCode() + " body=" + respBody);
                return "当前建议生成服务暂不可用，请稍后重试。";
            }
            if (respBody == null || respBody.isBlank()) {
                System.out.println("[AiService] Empty body from Qianfan");
                return "抱歉，暂未获取到有效建议，请稍后重试。";
            }

            Map<?, ?> resp = MAPPER.readValue(respBody, Map.class);
            // OpenAI兼容协议返回字段：choices[0].message.content 或者 result
            Object result = resp.get("result");
            if (result != null) {
                String value = String.valueOf(result);
                ops.set(cacheKey, value, java.time.Duration.ofMinutes(10));
                return value;
            }
            Object choices = resp.get("choices");
            if (choices instanceof List && !((List<?>) choices).isEmpty()) {
                Object first = ((List<?>) choices).get(0);
                if (first instanceof Map<?, ?> firstMap) {
                    Object message = firstMap.get("message");
                    if (message instanceof Map<?, ?> msgMap) {
                        Object content = msgMap.get("content");
                        if (content != null) {
                            String value = String.valueOf(content);
                            ops.set(cacheKey, value, java.time.Duration.ofMinutes(10));
                            return value;
                        }
                    }
                }
            }
            // 原生接口错误字段
            Object errCode = resp.get("error_code");
            Object errMsg = resp.get("error_msg");
            if (errCode != null || errMsg != null) {
                System.out.println("[AiService] Qianfan native error: code=" + errCode + ", msg=" + errMsg + ", raw=" + respBody);
                return "抱歉，暂未获取到有效建议，请稍后重试。（错误：" + errMsg + ")";
            }
            Object err = resp.get("error_msg");
            if (err != null) {
                System.out.println("[AiService] Qianfan error: " + err + " raw=" + respBody);
                return "抱歉，暂未获取到有效建议，请稍后重试。（错误：" + err + ")";
            }
            System.out.println("[AiService] Qianfan response without known content: " + respBody);
            return "抱歉，暂未获取到有效建议，请稍后重试。";
        } catch (HttpStatusCodeException ex) {
            // Log HTTP status and response body to debug 4xx/5xx from gateway
            System.out.println("[AiService] HTTP error calling Qianfan: status=" + ex.getStatusCode() + " body=" + ex.getResponseBodyAsString());
            return "当前建议生成服务暂不可用，请稍后重试。";
        } catch (Exception ex) {
            System.out.println("[AiService] Exception calling Qianfan: " + ex.getMessage());
            return "当前建议生成服务暂不可用，请稍后重试。";
        }
    }

    // IAM 模式无需 access_token，认证由网关通过 Authorization: Bearer 进行

    /**
     * 获取测评建议（优先从DB查询，未缓存则调用模型并存库）
     * 返回已缓存建议或 null（需调用模型）
     */
    public Map<String, Object> getCachedAdvice(Long recordId, JdbcTemplate jdbcTemplate) {
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(
                "SELECT advice_overall, advice_by_dimension FROM assessment_record WHERE id = ?",
                recordId
            );
            if (!result.isEmpty()) {
                Map<String, Object> row = result.get(0);
                String overall = (String) row.get("advice_overall");
                String dimJson = (String) row.get("advice_by_dimension");
                
                if (overall != null && !overall.isEmpty()) {
                    // 已缓存，直接返回
                    Map<String, Object> advice = new HashMap<>();
                    advice.put("overall", overall);
                    if (dimJson != null && !dimJson.isEmpty()) {
                        try {
                            advice.put("byDimension", MAPPER.readValue(dimJson, Map.class));
                        } catch (Exception e) {
                            advice.put("byDimension", new HashMap<>());
                        }
                    }
                    return advice;
                }
            }
        } catch (Exception e) {
            System.out.println("[AiService] 查询缓存建议失败: " + e.getMessage());
        }
        return null;  // 未缓存，需调用模型
    }

    /**
     * 流式生成并保存测评建议
     */
    public void streamAdvice(Long recordId, Map<String, Integer> dimensionScores, int totalScore,
                            JdbcTemplate jdbcTemplate, java.util.function.Consumer<String> onChunk) {
        try {
            String overallPrompt = buildOverallPrompt(dimensionScores, totalScore);
            StringBuilder overallText = new StringBuilder();
            
            // 流式生成总体建议
            this.streamChat(overallPrompt, chunk -> {
                if (!chunk.equals("[完成]") && !chunk.startsWith("[错误]")) {
                    overallText.append(chunk);
                    onChunk.accept(chunk);
                } else if (chunk.equals("[完成]")) {
                    onChunk.accept("\n\n--- 各维度建议生成中 ---\n");
                }
            });
            
            // 生成维度建议
            Map<String, String> dimAdvice = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : dimensionScores.entrySet()) {
                String dim = entry.getKey();
                Integer score = entry.getValue();
                String dimPrompt = buildDimensionPrompt(dim, score);
                StringBuilder dimText = new StringBuilder();
                
                this.streamChat(dimPrompt, chunk -> {
                    if (!chunk.equals("[完成]") && !chunk.startsWith("[错误]")) {
                        dimText.append(chunk);
                        onChunk.accept(chunk);
                    }
                });
                dimAdvice.put(dim, dimText.toString());
            }
            
            // 保存到数据库
            String dimJson = MAPPER.writeValueAsString(dimAdvice);
            jdbcTemplate.update(
                "UPDATE assessment_record SET advice_overall = ?, advice_by_dimension = ? WHERE id = ?",
                overallText.toString(),
                dimJson,
                recordId
            );
            
            onChunk.accept("\n[完成]");
            System.out.println("[AiService] 测评建议已保存到数据库，recordId=" + recordId);
        } catch (Exception e) {
            System.out.println("[AiService] 流式生成建议失败: " + e.getMessage());
            onChunk.accept("[错误] " + e.getMessage());
        }
    }

    /**
     * 保存聊天消息到数据库并流式输出 AI 回复
     */
    public void streamChatAndSave(Long sessionId, Long userId, String userMessage,
                                  JdbcTemplate jdbcTemplate, java.util.function.Consumer<String> onChunk) {
        try {
            // 检测危机信号
            boolean hasCrisisSignals = detectCrisisSignals(userMessage);
            
            // 保存用户消息
            jdbcTemplate.update(
                "INSERT INTO chat_message (session_id, sender_id, receiver_id, content, is_read, created_at) " +
                "VALUES (?, ?, 0, ?, 1, NOW())",
                sessionId, userId, userMessage
            );
            
            // 如果检测到危机信号，返回固定短回复 + 专家申请引导，避免过多建议
            StringBuilder aiReply = new StringBuilder();
            if (hasCrisisSignals) {
                System.out.println("[AiService] 🚨 触发危机应对模式");
                // 先给前端发一个稳定标记，用于显示“申请心理专家”按钮
                onChunk.accept("[CRISIS_SIGNAL]");
                aiReply.append(CRISIS_BRIEF_RESPONSE);
                onChunk.accept(CRISIS_BRIEF_RESPONSE);
                onChunk.accept("[完成]");
            } else {
                // 正常对话流程
                this.streamChat(userMessage, chunk -> {
                    if (!chunk.equals("[完成]") && !chunk.startsWith("[错误]")) {
                        aiReply.append(chunk);
                        onChunk.accept(chunk);
                    } else if (chunk.equals("[完成]")) {
                        onChunk.accept("[完成]");
                    }
                });
            }
            
            // 保存 AI 回复消息
            jdbcTemplate.update(
                "INSERT INTO chat_message (session_id, sender_id, receiver_id, content, is_read, created_at) " +
                "VALUES (?, 0, ?, ?, 0, NOW())",
                sessionId, userId, aiReply.toString()
            );
            
            System.out.println("[AiService] 聊天消息已保存，sessionId=" + sessionId + ", userId=" + userId);
        } catch (Exception e) {
            System.out.println("[AiService] 保存聊天消息失败: " + e.getMessage());
            onChunk.accept("[错误] " + e.getMessage());
        }
    }

    /**
     * 流式对话接口：调用Qianfan API获取流式响应，按行分割处理
     * 返回逐行文本流，前端可实时渲染
     */
    public void streamChat(String userMessage, java.util.function.Consumer<String> onChunk) {
        streamChatWithEnhancedPrompt(userMessage, false, onChunk);
    }

    /**
     * 带有危机检测增强的流式对话接口
     */
    public void streamChatWithEnhancedPrompt(String userMessage, boolean hasCrisisSignals, 
                                            java.util.function.Consumer<String> onChunk) {
        try {
            // 构建请求 - 包含系统提示词
            Map<String, Object> systemMsg = Map.of("role", "system", 
                "content", hasCrisisSignals ? SYSTEM_PROMPT + CRISIS_PROMPT_EXTENSION : SYSTEM_PROMPT);
            Map<String, Object> userMsg = Map.of("role", "user", "content", userMessage);
            Map<String, Object> body = new HashMap<>();
            body.put("model", modelId);
            body.put("messages", List.of(systemMsg, userMsg));
            body.put("temperature", hasCrisisSignals ? 0.5 : 0.7);  // 危机状态降低temperature以确保一致性
            body.put("top_p", 0.9);
            body.put("max_tokens", maxTokens);
            body.put("stream", true);  // 启用流式

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            if ("openai".equals(mode)) {
                if (openaiApiKey == null || openaiApiKey.isBlank()) {
                    throw new IllegalStateException("未配置 baidu.qianfan.openai-api-key");
                }
                headers.set("Authorization", "Bearer " + openaiApiKey);
                if (appId != null && !appId.isBlank()) {
                    headers.set("appid", appId);
                }
            } else if ("iam".equals(mode)) {
                if (accessKeyId == null || accessKeyId.isBlank()) {
                    throw new IllegalStateException("未配置 baidu.qianfan.access-key-id");
                }
                headers.set("Authorization", "Bearer " + accessKeyId);
            }
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            // 使用 executeAndStream 处理流式响应
            restTemplate.execute(
                modelUrl,
                HttpMethod.POST,
                request -> {
                    request.getHeaders().putAll(headers);
                    try (var os = request.getBody()) {
                        MAPPER.writeValue(os, body);
                    }
                },
                response -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        System.out.println("[AiService Stream] HTTP error: " + response.getStatusCode());
                        onChunk.accept("[错误] 网络请求失败");
                        return null;
                    }
                    
                    try (var is = response.getBody()) {
                        java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(is, StandardCharsets.UTF_8)
                        );
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty()) continue;
                            
                            // SSE 格式或普通 JSON lines
                            if (line.startsWith("data: ")) {
                                line = line.substring(6);
                            }
                            if (line.equals("[DONE]")) {
                                break;
                            }
                            
                            try {
                                Map<?, ?> chunk = MAPPER.readValue(line, Map.class);
                                
                                // 解析 OpenAI 兼容格式
                                Object choices = chunk.get("choices");
                                if (choices instanceof List && !((List<?>) choices).isEmpty()) {
                                    Object first = ((List<?>) choices).get(0);
                                    if (first instanceof Map<?, ?> choiceMap) {
                                        Object delta = choiceMap.get("delta");
                                        if (delta instanceof Map<?, ?> deltaMap) {
                                            Object content = deltaMap.get("content");
                                            if (content != null && !String.valueOf(content).isEmpty()) {
                                                onChunk.accept(String.valueOf(content));
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                System.out.println("[AiService Stream] Parse error: " + e.getMessage());
                            }
                        }
                        onChunk.accept("[完成]");
                    }
                    return null;
                }
            );
        } catch (Exception ex) {
            System.out.println("[AiService Stream] Exception: " + ex.getMessage());
            onChunk.accept("[错误] " + ex.getMessage());
        }
    }
}
