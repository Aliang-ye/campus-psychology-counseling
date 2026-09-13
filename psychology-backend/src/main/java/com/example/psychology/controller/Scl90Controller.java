package com.example.psychology.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.example.psychology.security.AuthContext;
import com.example.psychology.security.AuthUser;
import com.example.psychology.security.ForbiddenException;

import java.sql.Statement;
import java.util.*;

/**
 * SCL-90 症状自评量表接口
 * 完全独立，不影响原有测评功能
 */
@RestController
@RequestMapping("/api/scl90")
@CrossOrigin(origins = "*")
public class Scl90Controller {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -------------------------------------------------------
    // SCL-90 标准因子条目映射（Derogatis 1977 标准版本）
    // -------------------------------------------------------
    private static final Map<String, int[]> FACTOR_ITEMS = new LinkedHashMap<>();
    private static final String[] QUESTION_TABLE_CANDIDATES = new String[]{"scl-90", "scl_90", "scl90"};
    static {
        FACTOR_ITEMS.put("躯体化",        new int[]{1,4,12,27,40,42,48,49,52,53,56,58});
        FACTOR_ITEMS.put("强迫症状",      new int[]{3,9,10,28,38,45,46,51,55,65});
        FACTOR_ITEMS.put("人际关系敏感",  new int[]{6,21,34,36,37,41,61,69,73});
        FACTOR_ITEMS.put("抑郁",          new int[]{5,14,15,20,22,26,29,30,31,32,54,71,79});
        FACTOR_ITEMS.put("焦虑",          new int[]{2,17,23,33,39,57,72,78,80,86});
        FACTOR_ITEMS.put("敌对",          new int[]{11,24,63,67,74,81});
        FACTOR_ITEMS.put("恐怖",          new int[]{13,25,47,50,70,75,82});
        FACTOR_ITEMS.put("偏执",          new int[]{8,18,43,68,76,83});
        FACTOR_ITEMS.put("精神病性",      new int[]{7,16,35,62,77,84,85,87,88,90});
    }

    // -------------------------------------------------------
    // 获取全部90道题目（从 scl_90 表读取）
    // -------------------------------------------------------
    @GetMapping("/questions")
    public Map<String, Object> getQuestions() {
        Map<String, Object> response = new HashMap<>();
        try {
            String tableName = resolveQuestionTable();
            if (tableName == null) {
                response.put("success", false);
                response.put("message", "未找到SCL-90题库表（支持: scl-90 / scl_90 / scl90）");
                return response;
            }

            // CSV 导入后的列名：item_id, item_text
            String sql = "SELECT item_id, item_text FROM `" + tableName + "` ORDER BY item_id";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            List<Map<String, Object>> questions = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> q = new LinkedHashMap<>();
                q.put("id",   row.get("item_id"));
                q.put("text", row.get("item_text"));
                questions.add(q);
            }

            response.put("success", true);
            response.put("questions", questions);
            response.put("options", new String[]{"没有", "很轻", "中等", "偏重", "严重"});
            response.put("table", tableName);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "获取题目失败: " + e.getMessage());
            e.printStackTrace();
        }
        return response;
    }

    // -------------------------------------------------------
    // 提交答案 & 计算结果
    // Body: { userId, username, answers: {1:2, 2:3, ...}, testDuration }
    // -------------------------------------------------------
    @PostMapping("/submit")
    @Transactional
    public Map<String, Object> submit(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (body.get("answers") == null) {
                response.put("success", false);
                response.put("message", "提交参数不完整");
                return response;
            }
            AuthUser currentUser = AuthContext.require();
            Long userId   = currentUser.getUserId();
            String username = currentUser.getUsername();
            int testDuration = body.containsKey("testDuration")
                    ? Integer.parseInt(body.get("testDuration").toString()) : 0;

            @SuppressWarnings("unchecked")
            Map<String, Object> rawAnswers = (Map<String, Object>) body.get("answers");

            Map<Integer, Integer> answers = new HashMap<>();
            for (Map.Entry<String, Object> e : rawAnswers.entrySet()) {
                int itemId = Integer.parseInt(e.getKey());
                int score = Integer.parseInt(e.getValue().toString());
                if (itemId < 1 || itemId > 90 || score < 1 || score > 5) {
                    response.put("success", false);
                    response.put("message", "存在无效题号或选项，请按 1-90 题、1-5 分作答");
                    return response;
                }
                answers.put(itemId, score);
            }

            for (int itemId = 1; itemId <= 90; itemId++) {
                if (!answers.containsKey(itemId)) {
                    response.put("success", false);
                    response.put("message", "请完成全部90道题目（当前已答 " + answers.size() + " 题）");
                    return response;
                }
            }

            // --- 总分 & 均分 ---
            int totalScore = answers.values().stream().mapToInt(Integer::intValue).sum();
            double totalAvg = totalScore / 90.0;

            // --- 阳性项目数 & 阳性症状均分 ---
            int positiveCount = (int) answers.values().stream().filter(v -> v > 1).count();
            double positiveAvg = positiveCount == 0 ? 0.0
                    : answers.values().stream().filter(v -> v > 1).mapToInt(Integer::intValue).sum()
                      / (double) positiveCount;

            // --- 9个因子均分 ---
            Map<String, Double> factorAvg = new LinkedHashMap<>();
            for (Map.Entry<String, int[]> fe : FACTOR_ITEMS.entrySet()) {
                int[] items = fe.getValue();
                double sum = 0;
                int cnt = 0;
                for (int itemId : items) {
                    if (answers.containsKey(itemId)) {
                        sum += answers.get(itemId);
                        cnt++;
                    }
                }
                factorAvg.put(fe.getKey(), cnt == 0 ? 0.0 : Math.round(sum / cnt * 100.0) / 100.0);
            }

            // --- 持久化 ---
            String answersJson = MAPPER.writeValueAsString(answers);
            String insertSql =
                "INSERT INTO scl90_record " +
                "(user_id, username, total_score, total_avg, positive_count, positive_avg, " +
                " f1_somatization, f2_obsession, f3_interpersonal, f4_depression, f5_anxiety, " +
                " f6_hostility, f7_phobia, f8_paranoia, f9_psychosis, answers_json, test_duration) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                var ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, userId);
                ps.setString(2, username);
                ps.setInt(3, totalScore);
                ps.setDouble(4, Math.round(totalAvg * 100.0) / 100.0);
                ps.setInt(5, positiveCount);
                ps.setDouble(6, Math.round(positiveAvg * 100.0) / 100.0);
                ps.setDouble(7, factorAvg.get("躯体化"));
                ps.setDouble(8, factorAvg.get("强迫症状"));
                ps.setDouble(9, factorAvg.get("人际关系敏感"));
                ps.setDouble(10, factorAvg.get("抑郁"));
                ps.setDouble(11, factorAvg.get("焦虑"));
                ps.setDouble(12, factorAvg.get("敌对"));
                ps.setDouble(13, factorAvg.get("恐怖"));
                ps.setDouble(14, factorAvg.get("偏执"));
                ps.setDouble(15, factorAvg.get("精神病性"));
                ps.setString(16, answersJson);
                ps.setInt(17, testDuration);
                return ps;
            }, keyHolder);
            Number generatedId = keyHolder.getKey();
            if (generatedId == null) {
                throw new IllegalStateException("无法获取 SCL-90 记录ID");
            }
            Long recordId = generatedId.longValue();

            // 构建返回值
            response.put("success",       true);
            response.put("recordId",      recordId);
            response.put("totalScore",    totalScore);
            response.put("totalAvg",      Math.round(totalAvg * 100.0) / 100.0);
            response.put("positiveCount", positiveCount);
            response.put("positiveAvg",   Math.round(positiveAvg * 100.0) / 100.0);
            response.put("factorScores",  factorAvg);
            response.put("message",       "SCL-90 测评已完成");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "提交失败: " + e.getMessage());
            e.printStackTrace();
        }
        return response;
    }

    // -------------------------------------------------------
    // 查询历史记录
    // -------------------------------------------------------
    @GetMapping("/history/{userId}")
    public Map<String, Object> getHistory(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            AuthUser currentUser = AuthContext.require();
            if (!currentUser.isStaff() && !currentUser.getUserId().equals(userId)) {
                throw new ForbiddenException("无权查看他人测评记录");
            }
            String sql =
                "SELECT id, user_id AS userId, username, total_score AS totalScore, total_avg AS totalAvg, " +
                "positive_count AS positiveCount, positive_avg AS positiveAvg, " +
                "f1_somatization AS f1Somatization, f2_obsession AS f2Obsession, " +
                "f3_interpersonal AS f3Interpersonal, f4_depression AS f4Depression, f5_anxiety AS f5Anxiety, " +
                "f6_hostility AS f6Hostility, f7_phobia AS f7Phobia, f8_paranoia AS f8Paranoia, " +
                "f9_psychosis AS f9Psychosis, test_duration AS testDuration, created_at AS createdAt " +
                "FROM scl90_record WHERE user_id = ? ORDER BY created_at DESC LIMIT 20";

            List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, userId);
            response.put("success", true);
            response.put("records", records);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
        }
        return response;
    }

    @GetMapping("/history/all")
    public Map<String, Object> getAllHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Map<String, Object> response = new HashMap<>();
        try {
            requireStaff();
            int offset = Math.max(page, 0) * Math.max(size, 1);
            String sql =
                "SELECT id, user_id AS userId, username, total_score AS totalScore, total_avg AS totalAvg, " +
                "positive_count AS positiveCount, positive_avg AS positiveAvg, " +
                "f1_somatization AS f1Somatization, f2_obsession AS f2Obsession, " +
                "f3_interpersonal AS f3Interpersonal, f4_depression AS f4Depression, f5_anxiety AS f5Anxiety, " +
                "f6_hostility AS f6Hostility, f7_phobia AS f7Phobia, f8_paranoia AS f8Paranoia, " +
                "f9_psychosis AS f9Psychosis, test_duration AS testDuration, created_at AS createdAt " +
                "FROM scl90_record ORDER BY created_at DESC LIMIT ? OFFSET ?";

            List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, size, offset);
            response.put("success", true);
            response.put("records", records);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
        }
        return response;
    }

    @GetMapping("/history/search")
    public Map<String, Object> searchHistory(@RequestParam String username) {
        Map<String, Object> response = new HashMap<>();
        try {
            requireStaff();
            String sql =
                "SELECT id, user_id AS userId, username, total_score AS totalScore, total_avg AS totalAvg, " +
                "positive_count AS positiveCount, positive_avg AS positiveAvg, " +
                "f1_somatization AS f1Somatization, f2_obsession AS f2Obsession, " +
                "f3_interpersonal AS f3Interpersonal, f4_depression AS f4Depression, f5_anxiety AS f5Anxiety, " +
                "f6_hostility AS f6Hostility, f7_phobia AS f7Phobia, f8_paranoia AS f8Paranoia, " +
                "f9_psychosis AS f9Psychosis, test_duration AS testDuration, created_at AS createdAt " +
                "FROM scl90_record WHERE username = ? ORDER BY created_at DESC";

            List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, username);
            response.put("success", true);
            response.put("records", records);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
        }
        return response;
    }

    // -------------------------------------------------------
    // 查询单条记录详情（含答题明细）
    // -------------------------------------------------------
    @GetMapping({"/history/detail/{recordId}", "/detail/{recordId}"})
    public Map<String, Object> getDetail(@PathVariable Long recordId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String sql =
                "SELECT id, user_id AS userId, username, total_score AS totalScore, total_avg AS totalAvg, " +
                "positive_count AS positiveCount, positive_avg AS positiveAvg, " +
                "f1_somatization AS f1Somatization, f2_obsession AS f2Obsession, " +
                "f3_interpersonal AS f3Interpersonal, f4_depression AS f4Depression, f5_anxiety AS f5Anxiety, " +
                "f6_hostility AS f6Hostility, f7_phobia AS f7Phobia, f8_paranoia AS f8Paranoia, " +
                "f9_psychosis AS f9Psychosis, answers_json AS answersJson, " +
                "test_duration AS testDuration, created_at AS createdAt " +
                "FROM scl90_record WHERE id = ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, recordId);
            if (rows.isEmpty()) {
                response.put("success", false);
                response.put("message", "记录不存在");
                return response;
            }
            Long ownerId = ((Number) rows.get(0).get("userId")).longValue();
            assertCanAccessRecord(ownerId);
            response.put("success", true);
            response.put("record", rows.get(0));
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
        }
        return response;
    }

    @DeleteMapping("/delete/{recordId}")
    public Map<String, Object> deleteRecord(@PathVariable Long recordId) {
        Map<String, Object> response = new HashMap<>();
        try {
            AuthUser currentUser = AuthContext.require();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT user_id FROM scl90_record WHERE id = ?", recordId);
            if (rows.isEmpty()) {
                response.put("success", false);
                response.put("message", "记录不存在");
                return response;
            }
            Long ownerId = ((Number) rows.get(0).get("user_id")).longValue();
            if (!currentUser.isAdmin() && !currentUser.getUserId().equals(ownerId)) {
                throw new ForbiddenException("无权删除该测评记录");
            }
            int affected = jdbcTemplate.update("DELETE FROM scl90_record WHERE id = ?", recordId);
            response.put("success", affected > 0);
            if (affected > 0) {
                response.put("message", "删除成功");
            } else {
                response.put("message", "记录不存在");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/delete/batch")
    public Map<String, Object> batchDelete(@RequestBody Map<String, List<Long>> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            AuthUser currentUser = AuthContext.require();
            List<Long> ids = body.getOrDefault("recordIds", Collections.emptyList());
            if (ids.isEmpty()) {
                response.put("success", false);
                response.put("message", "recordIds 不能为空");
                return response;
            }
            if (!currentUser.isAdmin()) {
                String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
                List<Object> params = new ArrayList<>(ids);
                params.add(currentUser.getUserId());
                Integer owned = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM scl90_record WHERE id IN (" + placeholders + ") AND user_id = ?",
                        Integer.class, params.toArray());
                if (owned == null || owned != ids.size()) {
                    throw new ForbiddenException("只能删除自己的测评记录");
                }
            }

            String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            String sql = "DELETE FROM scl90_record WHERE id IN (" + placeholders + ")";
            int deleted = jdbcTemplate.update(sql, ids.toArray());

            response.put("success", true);
            response.put("deletedCount", deleted);
            response.put("message", "批量删除完成");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "批量删除失败: " + e.getMessage());
        }
        return response;
    }

    private String resolveQuestionTable() {
        for (String candidate : QUESTION_TABLE_CANDIDATES) {
            try {
                String sql = "SELECT 1 FROM `" + candidate + "` LIMIT 1";
                jdbcTemplate.queryForList(sql);
                return candidate;
            } catch (Exception ignored) {
                // try next candidate
            }
        }
        return null;
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
