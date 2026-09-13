package com.example.psychology.controller;

import com.example.psychology.dto.AssessmentSubmitDto;
import com.example.psychology.entity.AssessmentQuestion;
import com.example.psychology.entity.AssessmentRecord;
import com.example.psychology.entity.AssessmentAnswer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.sql.Statement;
import java.util.*;
import com.example.psychology.security.AuthContext;
import com.example.psychology.security.AuthUser;
import com.example.psychology.security.ForbiddenException;
import com.example.psychology.service.AiService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/assessment")
@CrossOrigin(origins = "*")
public class AssessmentController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiService aiService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String[] DIMENSIONS = {"学业压力", "宿舍关系", "考试焦虑", "就业压力", "恋爱问题"};
    private static final int QUESTIONS_PER_DIMENSION = 6;
    private static final int EXPECTED_QUESTION_COUNT = DIMENSIONS.length * QUESTIONS_PER_DIMENSION;

    /**
     * 获取测评题目 - 从5个维度各抽取6题，共30题
     */
    @GetMapping("/questions")
    public Map<String, Object> getQuestions() {
        try {
            List<AssessmentQuestion> questions = new ArrayList<>();
            
            // 从每个维度随机抽取6题
            for (String dimension : DIMENSIONS) {
                String sql = "SELECT `题目ID` as id, `维度` as dimension, `题目内容` as content, " +
                            "`选项1` as option1, `选项2` as option2, `选项3` as option3, " +
                            "`选项4` as option4, `选项5` as option5, `计分权重` as weight, " +
                            "`反向计分` as isReverse " +
                            "FROM q1 WHERE `维度` = ? ORDER BY RAND() LIMIT 6";
                List<AssessmentQuestion> dimQuestions = jdbcTemplate.query(
                    sql,
                    new Object[]{dimension},
                    new BeanPropertyRowMapper<>(AssessmentQuestion.class)
                );
                questions.addAll(dimQuestions);
            }
            
            // 打乱顺序
            Collections.shuffle(questions);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("questions", questions);
            response.put("totalCount", questions.size());
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取题目失败: " + e.getMessage());
            e.printStackTrace();
            return response;
        }
    }

    /**
     * 提交测评答案并计算分数 - 仅保存分数，不等待AI
     */
    @PostMapping("/submit")
    @Transactional
    public Map<String, Object> submitAssessment(@RequestBody AssessmentSubmitDto submitDto) {
        Map<String, Object> response = new HashMap<>();
        try {
            AuthUser currentUser = AuthContext.require();
            Long userId = currentUser.getUserId();
            String username = currentUser.getUsername();
            Map<String, Integer> answers = submitDto.getAnswers();
            Integer testDuration = submitDto.getTestDuration() == null ? 0 : submitDto.getTestDuration();
            if (answers == null || answers.size() != EXPECTED_QUESTION_COUNT) {
                response.put("success", false);
                response.put("message", "请完成全部 " + EXPECTED_QUESTION_COUNT + " 道题目");
                return response;
            }

            Map<String, Integer> dimensionScores = new LinkedHashMap<>();
            for (String dimension : DIMENSIONS) {
                dimensionScores.put(dimension, 0);
            }

            int totalScore = 0;
            List<AssessmentAnswer> answerList = new ArrayList<>();
            Map<String, Integer> dimensionCounts = new HashMap<>();

            for (Map.Entry<String, Integer> entry : answers.entrySet()) {
                String questionId = entry.getKey();
                Integer selectedOption = entry.getValue();
                if (questionId == null || selectedOption == null || selectedOption < 1 || selectedOption > 5) {
                    response.put("success", false);
                    response.put("message", "存在无效选项，请按 1-5 分作答");
                    return response;
                }

                String sql = "SELECT `题目ID` as id, `维度` as dimension, `计分权重` as weight, `反向计分` as isReverse FROM q1 WHERE `题目ID` = ?";
                List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, questionId);
                if (result.isEmpty()) {
                    response.put("success", false);
                    response.put("message", "题目不存在: " + questionId);
                    return response;
                }

                Map<String, Object> row = result.get(0);
                String dimension = (String) row.get("dimension");
                if (!dimensionScores.containsKey(dimension)) {
                    response.put("success", false);
                    response.put("message", "题目维度无效: " + questionId);
                    return response;
                }

                int rawScore = selectedOption;
                Object reverseFlag = row.get("isReverse");
                boolean isReverse = reverseFlag instanceof Boolean
                        ? (Boolean) reverseFlag
                        : reverseFlag != null && ("1".equals(String.valueOf(reverseFlag))
                        || "true".equalsIgnoreCase(String.valueOf(reverseFlag)));
                if (isReverse) {
                    rawScore = 6 - selectedOption;
                }

                int weight = 1;
                Object weightValue = row.get("weight");
                if (weightValue instanceof Number) {
                    weight = ((Number) weightValue).intValue();
                }
                if (weight <= 0) {
                    weight = 1;
                }

                int score = rawScore * weight;
                dimensionScores.put(dimension, dimensionScores.get(dimension) + score);
                dimensionCounts.put(dimension, dimensionCounts.getOrDefault(dimension, 0) + 1);
                totalScore += score;

                AssessmentAnswer answer = new AssessmentAnswer();
                answer.setQuestionId(questionId);
                answer.setAnswerOption(selectedOption);
                answer.setScore(score);
                answerList.add(answer);
            }

            for (String dimension : DIMENSIONS) {
                if (dimensionCounts.getOrDefault(dimension, 0) != QUESTIONS_PER_DIMENSION) {
                    response.put("success", false);
                    response.put("message", "每个维度需完成 " + QUESTIONS_PER_DIMENSION + " 题");
                    return response;
                }
            }

            String insertRecordSql = "INSERT INTO assessment_record " +
                "(user_id, username, total_score, dimension_a_score, dimension_b_score, " +
                "dimension_c_score, dimension_d_score, dimension_e_score, test_duration, " +
                "advice_overall, advice_by_dimension, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NOW())";

            final int persistedTotalScore = totalScore;
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                var ps = connection.prepareStatement(insertRecordSql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, userId);
                ps.setString(2, username);
                ps.setInt(3, persistedTotalScore);
                ps.setInt(4, dimensionScores.get("学业压力"));
                ps.setInt(5, dimensionScores.get("宿舍关系"));
                ps.setInt(6, dimensionScores.get("考试焦虑"));
                ps.setInt(7, dimensionScores.get("就业压力"));
                ps.setInt(8, dimensionScores.get("恋爱问题"));
                ps.setInt(9, testDuration);
                return ps;
            }, keyHolder);
            Number generatedId = keyHolder.getKey();
            if (generatedId == null) {
                throw new IllegalStateException("无法获取测评记录ID");
            }
            Long recordId = generatedId.longValue();

            String insertAnswerSql = "INSERT INTO assessment_answer " +
                "(record_id, question_id, answer_option, score, created_at) " +
                "VALUES (?, ?, ?, ?, NOW())";
            jdbcTemplate.batchUpdate(insertAnswerSql, answerList, answerList.size(), (ps, answer) -> {
                ps.setLong(1, recordId);
                ps.setString(2, answer.getQuestionId());
                ps.setInt(3, answer.getAnswerOption());
                ps.setInt(4, answer.getScore());
            });
            
            // 异步生成AI建议，前端稍后通过 /advice/stream 拉取
            response.put("success", true);
            response.put("totalScore", totalScore);
            response.put("recordId", recordId);
            Map<String, Integer> persistedDimensionScores = new HashMap<>(dimensionScores);
            response.put("dimensionScores", persistedDimensionScores);
            response.put("message", "测评已提交，正在生成建议...");
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "提交失败: " + e.getMessage());
            e.printStackTrace();
            return response;
        }
    }

    /**
     * 流式获取测评建议 - 检查DB缓存，未缓存则调用模型并存库
     * GET /api/assessment/advice/stream?recordId=123
     */
    @GetMapping(value = "/advice/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAdvice(@RequestParam Long recordId) {
        SseEmitter emitter = new SseEmitter(150 * 1000L);
        List<AssessmentRecord> records = jdbcTemplate.query(
            "SELECT * FROM assessment_record WHERE id = ?",
            new Object[]{recordId},
            new BeanPropertyRowMapper<>(AssessmentRecord.class)
        );
        if (records.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().data("[错误] 记录不存在"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }
        assertCanAccessRecord(records.get(0).getUserId());
        
        new Thread(() -> {
            try {
                // 先查DB，看是否已有缓存建议
                Map<String, Object> cached = aiService.getCachedAdvice(recordId, jdbcTemplate);
                if (cached != null) {
                    // 已缓存，直接流式输出
                    String overall = (String) cached.get("overall");
                    @SuppressWarnings("unchecked")
                    Map<String, String> byDim = (Map<String, String>) cached.getOrDefault("byDimension", new HashMap<>());
                    
                    System.out.println("[AssessmentController] 从DB读取缓存建议");
                    emitAdviceChunked(emitter, overall, byDim);
                    return;
                }
                
                // 未缓存，需调用模型
                // 查询该记录的维度分数
                AssessmentRecord record = records.get(0);
                Map<String, Integer> dimensionScores = new HashMap<>();
                dimensionScores.put("学业压力", record.getDimensionAScore() == null ? 0 : record.getDimensionAScore());
                dimensionScores.put("宿舍关系", record.getDimensionBScore() == null ? 0 : record.getDimensionBScore());
                dimensionScores.put("考试焦虑", record.getDimensionCScore() == null ? 0 : record.getDimensionCScore());
                dimensionScores.put("就业压力", record.getDimensionDScore() == null ? 0 : record.getDimensionDScore());
                dimensionScores.put("恋爱问题", record.getDimensionEScore() == null ? 0 : record.getDimensionEScore());
                
                // 流式生成并保存建议
                aiService.streamAdvice(recordId, dimensionScores, record.getTotalScore() == null ? 0 : record.getTotalScore(),
                    jdbcTemplate, chunk -> {
                        try {
                            emitter.send(SseEmitter.event().data(chunk).id(System.nanoTime() + ""));
                        } catch (IOException e) {
                            System.out.println("[AssessmentController] SSE发送失败: " + e.getMessage());
                        }
                    });
                emitter.complete();
            } catch (Exception e) {
                System.out.println("[AssessmentController] 流式建议错误: " + e.getMessage());
                try {
                    emitter.send(SseEmitter.event().data("[错误] " + e.getMessage()).id(System.nanoTime() + ""));
                    emitter.complete();
                } catch (IOException ignored) {}
            }
        }).start();
        
        return emitter;
    }

    /**
     * 辅助方法：将缓存的建议按块流式输出
     */
    private void emitAdviceChunked(SseEmitter emitter, String overall, Map<String, String> byDimension) throws IOException {
        // 输出总体建议
        emitter.send(SseEmitter.event().data(overall).id(System.nanoTime() + ""));
        emitter.send(SseEmitter.event().data("\n\n--- 各维度建议 ---\n").id(System.nanoTime() + ""));
        
        // 输出维度建议
        for (Map.Entry<String, String> entry : byDimension.entrySet()) {
            emitter.send(SseEmitter.event().data(entry.getKey() + ":\n" + entry.getValue() + "\n\n").id(System.nanoTime() + ""));
        }
        
        emitter.send(SseEmitter.event().data("[完成]").id(System.nanoTime() + ""));
        emitter.complete();
    }


    /**
     * 查看测评历史记录
     */
    @GetMapping("/history/{userId}")
    public Map<String, Object> getHistory(@PathVariable Long userId, @RequestParam(defaultValue = "false") boolean all) {
        Map<String, Object> response = new HashMap<>();
        try {
            AuthUser currentUser = AuthContext.require();
            if (!currentUser.isStaff() && !currentUser.getUserId().equals(userId)) {
                throw new ForbiddenException("无权查看他人测评记录");
            }
            String sql;
            if (all) {
                sql = "SELECT * FROM assessment_record WHERE user_id = ? ORDER BY created_at DESC";
            } else {
                sql = "SELECT * FROM assessment_record WHERE user_id = ? ORDER BY created_at DESC LIMIT 10";
            }
            
            List<AssessmentRecord> records = jdbcTemplate.query(
                sql,
                new Object[]{userId},
                new BeanPropertyRowMapper<>(AssessmentRecord.class)
            );
            
            response.put("success", true);
            response.put("records", records);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return response;
        }
    }

    /**
     * 教师搜索学生的测评记录
     */
    @GetMapping("/history/search")
    public Map<String, Object> searchStudentHistory(@RequestParam String username) {
        Map<String, Object> response = new HashMap<>();
        try {
            requireStaff();
            String sql = "SELECT * FROM assessment_record WHERE username = ? ORDER BY created_at DESC";
            List<AssessmentRecord> records = jdbcTemplate.query(
                sql,
                new Object[]{username},
                new BeanPropertyRowMapper<>(AssessmentRecord.class)
            );
            
            response.put("success", true);
            response.put("records", records);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return response;
        }
    }

    /**
     * 查看测评详情（包含答题明细）
     */
    @GetMapping("/detail/{recordId}")
    public Map<String, Object> getDetail(@PathVariable Long recordId) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 获取记录
            List<AssessmentRecord> records = jdbcTemplate.query(
                "SELECT * FROM assessment_record WHERE id = ?",
                new Object[]{recordId},
                new BeanPropertyRowMapper<>(AssessmentRecord.class)
            );
            
            if (records.isEmpty()) {
                response.put("success", false);
                response.put("message", "记录不存在");
                return response;
            }
            
            AssessmentRecord record = records.get(0);
            assertCanAccessRecord(record.getUserId());
            
            // 获取答题详情
            List<AssessmentAnswer> answers = jdbcTemplate.query(
                "SELECT * FROM assessment_answer WHERE record_id = ?",
                new Object[]{recordId},
                new BeanPropertyRowMapper<>(AssessmentAnswer.class)
            );

            // 直接从数据库读取已缓存的AI建议，不需要重新生成
            String adviceOverall = record.getAdviceOverall();
            Object adviceByDimensionObj = record.getAdviceByDimension();
            Map<String, String> adviceByDimension = new HashMap<>();
            
            if (adviceByDimensionObj != null) {
                try {
                    if (adviceByDimensionObj instanceof Map<?, ?> map) {
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            if (entry.getKey() != null && entry.getValue() != null) {
                                adviceByDimension.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                            }
                        }
                    } else {
                        String json = String.valueOf(adviceByDimensionObj);
                        if (!json.isBlank()) {
                            adviceByDimension = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
                        }
                    }
                } catch (Exception e) {
                    System.out.println("解析 adviceByDimension 失败: " + e.getMessage());
                }
            }
            
            response.put("success", true);
            response.put("record", record);
            response.put("answers", answers);
            response.put("adviceOverall", adviceOverall == null ? "" : adviceOverall);
            response.put("adviceByDimension", adviceByDimension);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return response;
        }
    }

    /**
     * 删除单条记录
     */
    @DeleteMapping("/delete/{recordId}")
    public Map<String, Object> deleteRecord(@PathVariable Long recordId) {
        Map<String, Object> response = new HashMap<>();
        try {
            AuthUser currentUser = AuthContext.require();
            List<AssessmentRecord> records = jdbcTemplate.query(
                "SELECT * FROM assessment_record WHERE id = ?",
                new Object[]{recordId},
                new BeanPropertyRowMapper<>(AssessmentRecord.class)
            );
            if (records.isEmpty()) {
                response.put("success", false);
                response.put("message", "记录不存在");
                return response;
            }
            AssessmentRecord record = records.get(0);
            if (!currentUser.isAdmin() && !currentUser.getUserId().equals(record.getUserId())) {
                throw new ForbiddenException("无权删除该测评记录");
            }
            // 先删除答题详情（外键约束）
            jdbcTemplate.update("DELETE FROM assessment_answer WHERE record_id = ?", recordId);
            
            // 再删除记录
            int affectedRows = jdbcTemplate.update("DELETE FROM assessment_record WHERE id = ?", recordId);
            
            response.put("success", affectedRows > 0);
            response.put("message", affectedRows > 0 ? "删除成功" : "记录不存在");
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
            return response;
        }
    }

    /**
     * 批量删除记录
     */
    @PostMapping("/delete/batch")
    public Map<String, Object> batchDelete(@RequestBody Map<String, List<Long>> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            AuthUser currentUser = AuthContext.require();
            List<Long> recordIds = request.get("recordIds");
            if (recordIds == null || recordIds.isEmpty()) {
                response.put("success", false);
                response.put("message", "没有选择要删除的记录");
                return response;
            }
            
            // 删除答题详情
            String placeholders = String.join(",", Collections.nCopies(recordIds.size(), "?"));
            if (!currentUser.isAdmin()) {
                String ownerSql = "SELECT COUNT(*) FROM assessment_record WHERE id IN (" + placeholders + ") AND user_id = ?";
                List<Object> params = new ArrayList<>(recordIds);
                params.add(currentUser.getUserId());
                Integer owned = jdbcTemplate.queryForObject(ownerSql, Integer.class, params.toArray());
                if (owned == null || owned != recordIds.size()) {
                    throw new ForbiddenException("只能删除自己的测评记录");
                }
            }
            jdbcTemplate.update(
                "DELETE FROM assessment_answer WHERE record_id IN (" + placeholders + ")",
                recordIds.toArray()
            );
            
            // 删除记录
            int affectedRows = jdbcTemplate.update(
                "DELETE FROM assessment_record WHERE id IN (" + placeholders + ")",
                recordIds.toArray()
            );
            
            response.put("success", true);
            response.put("deletedCount", affectedRows);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
            return response;
        }
    }

    /**
     * 管理员查看所有记录（分页）
     */
    @GetMapping("/history/all")
    public Map<String, Object> getAllRecords(@RequestParam(defaultValue = "0") int page, 
                                            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> response = new HashMap<>();
        try {
            requireStaff();
            int offset = page * size;
            List<AssessmentRecord> records = jdbcTemplate.query(
                "SELECT * FROM assessment_record ORDER BY created_at DESC LIMIT ? OFFSET ?",
                new Object[]{size, offset},
                new BeanPropertyRowMapper<>(AssessmentRecord.class)
            );
            
            List<Map<String, Object>> countResult = jdbcTemplate.queryForList(
                "SELECT COUNT(*) as total FROM assessment_record"
            );
            int total = ((Number) countResult.get(0).get("total")).intValue();
            
            response.put("success", true);
            response.put("records", records);
            response.put("total", total);
            response.put("page", page);
            response.put("size", size);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return response;
        }
    }

    private void requireStaff() {
        AuthUser currentUser = AuthContext.require();
        if (!currentUser.isStaff()) {
            throw new ForbiddenException("无权访问该资源");
        }
    }

    private void assertCanAccessRecord(Long ownerId) {
        AuthUser currentUser = AuthContext.require();
        if (!currentUser.isStaff() && !currentUser.getUserId().equals(ownerId)) {
            throw new ForbiddenException("无权查看该测评记录");
        }
    }
}
