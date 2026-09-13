package com.example.psychology.controller;

import com.example.psychology.entity.Article;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import com.example.psychology.security.AuthContext;
import com.example.psychology.security.AuthUser;
import com.example.psychology.security.ForbiddenException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles")
@CrossOrigin(origins = "*")
public class ArticleController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 学生/老师：查看已发布文章
    @GetMapping("/public")
    public Map<String, Object> listPublished() {
        Map<String, Object> result = new HashMap<>();
        List<Article> list = jdbcTemplate.query(
                "SELECT * FROM article WHERE status='published' ORDER BY created_at DESC",
                new BeanPropertyRowMapper<>(Article.class));
        result.put("success", true);
        result.put("articles", list);
        return result;
    }

    // 点赞（学生/老师均可）
    @PostMapping("/like")
    public Map<String, Object> likeArticle(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        String username = AuthContext.require().getUsername();
        String articleIdStr = body.get("articleId");
        if (articleIdStr == null) {
            result.put("success", false);
            result.put("message", "参数不完整");
            return result;
        }
        Long articleId = Long.valueOf(articleIdStr);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM article_like WHERE article_id=? AND username=?",
                Integer.class, articleId, username);
        if (count != null && count > 0) {
            result.put("success", false);
            result.put("message", "请勿重复点赞");
            return result;
        }
        jdbcTemplate.update("INSERT INTO article_like(article_id, username) VALUES (?,?)",
                articleId, username);
        jdbcTemplate.update("UPDATE article SET like_count = like_count + 1 WHERE id=?",
                articleId);
        result.put("success", true);
        return result;
    }

    // 老师：提交文章，等待管理员审核
    @PostMapping("/submit")
    public Map<String, Object> submitArticle(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        AuthUser currentUser = AuthContext.require();
        String title = body.get("title");
        String content = body.get("content");
        String authorUsername = currentUser.getUsername();
        String authorRole = currentUser.getRole();
        if (title == null || content == null) {
            result.put("success", false);
            result.put("message", "参数不完整");
            return result;
        }
        jdbcTemplate.update(
                "INSERT INTO article(title, content, author_username, author_role, status, like_count, created_at) " +
                        "VALUES (?,?,?,?,'pending',0,NOW())",
                title, content, authorUsername, authorRole == null ? "doctor" : authorRole);
        result.put("success", true);
        return result;
    }

    // 管理员：查看待审核文章
    @GetMapping("/pending")
    public Map<String, Object> listPending() {
        Map<String, Object> result = new HashMap<>();
        requireAdmin();
        List<Article> list = jdbcTemplate.query(
                "SELECT * FROM article WHERE status='pending' ORDER BY created_at ASC",
                new BeanPropertyRowMapper<>(Article.class));
        result.put("success", true);
        result.put("articles", list);
        return result;
    }

    // 管理员：查看全部文章（可按状态过滤：published/unpublished/pending/rejected，默认全部）
    @GetMapping("/admin/list")
    public Map<String, Object> listAllForAdmin(@RequestParam(required = false) String status) {
        Map<String, Object> result = new HashMap<>();
        requireAdmin();
        String sql = "SELECT * FROM article";
        Object[] params = new Object[]{};
        if (status != null && !status.isEmpty() && !"all".equalsIgnoreCase(status)) {
            sql += " WHERE status=?";
            params = new Object[]{status};
        }
        sql += " ORDER BY created_at DESC";
        List<Article> list = jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(Article.class));
        result.put("success", true);
        result.put("articles", list);
        return result;
    }

    // 管理员：审核文章（发布/打回）
    @PostMapping("/audit")
    public Map<String, Object> auditArticle(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        requireAdmin();
        String articleIdStr = body.get("articleId");
        String action = body.get("action");
        String comment = body.get("comment");
        if (articleIdStr == null || action == null) {
            result.put("success", false);
            result.put("message", "参数不完整");
            return result;
        }
        Long articleId = Long.valueOf(articleIdStr);
        String status;
        if ("approve".equalsIgnoreCase(action)) {
            status = "published";
        } else if ("reject".equalsIgnoreCase(action)) {
            status = "rejected";
        } else {
            result.put("success", false);
            result.put("message", "未知操作类型");
            return result;
        }
        jdbcTemplate.update(
                "UPDATE article SET status=?, audit_comment=? WHERE id=?",
                status, comment, articleId);
        result.put("success", true);
        return result;
    }

    // 管理员：更新文章内容/标题/备注/状态（已发布或待审都可改）
    @PostMapping("/admin/update")
    public Map<String, Object> adminUpdateArticle(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        requireAdmin();
        String articleIdStr = body.get("articleId");
        if (articleIdStr == null) {
            result.put("success", false);
            result.put("message", "缺少文章ID");
            return result;
        }
        Long articleId = Long.valueOf(articleIdStr);
        String newTitle = body.get("title");
        String newContent = body.get("content");
        String newStatus = body.get("status"); // 可选：published / unpublished / pending / rejected
        String comment = body.get("comment");

        StringBuilder sql = new StringBuilder("UPDATE article SET ");
        List<Object> params = new ArrayList<>();
        if (newTitle != null && !newTitle.isEmpty()) {
            sql.append("title=?,");
            params.add(newTitle);
        }
        if (newContent != null && !newContent.isEmpty()) {
            sql.append("content=?,");
            params.add(newContent);
        }
        if (newStatus != null && !newStatus.isEmpty()) {
            sql.append("status=?,");
            params.add(newStatus);
        }
        if (comment != null) {
            sql.append("audit_comment=?,");
            params.add(comment);
        }
        if (params.isEmpty()) {
            result.put("success", false);
            result.put("message", "没有需要更新的字段");
            return result;
        }
        sql.setLength(sql.length() - 1); // 去掉最后一个逗号
        sql.append(" WHERE id=?");
        params.add(articleId);
        int updated = jdbcTemplate.update(sql.toString(), params.toArray());
        result.put("success", updated > 0);
        if (updated == 0) {
            result.put("message", "目标文章不存在");
        }
        return result;
    }

    // 管理员：下架文章（状态置为 unpublished，并可写备注）
    @PostMapping("/admin/unpublish")
    public Map<String, Object> adminUnpublish(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        requireAdmin();
        String articleIdStr = body.get("articleId");
        String comment = body.get("comment");
        if (articleIdStr == null) {
            result.put("success", false);
            result.put("message", "缺少文章ID");
            return result;
        }
        Long articleId = Long.valueOf(articleIdStr);
        int updated = jdbcTemplate.update(
                "UPDATE article SET status='unpublished', audit_comment=? WHERE id=?",
                comment, articleId);
        if (updated == 0) {
            result.put("success", false);
            result.put("message", "目标文章不存在");
        } else {
            result.put("success", true);
        }
        return result;
    }

    // 管理员：删除文章
    @PostMapping("/admin/delete")
    public Map<String, Object> adminDelete(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        System.out.println("=== 删除文章请求 ===");
        System.out.println("请求体: " + body);
        
        requireAdmin();
        String articleIdStr = body.get("articleId");
        System.out.println("文章ID字符串: " + articleIdStr);
        
        if (articleIdStr == null) {
            result.put("success", false);
            result.put("message", "缺少文章ID");
            return result;
        }
        Long articleId = Long.valueOf(articleIdStr);
        System.out.println("转换后的文章ID: " + articleId);
        
        // 先删除点赞记录
        int likeDeleted = jdbcTemplate.update("DELETE FROM article_like WHERE article_id=?", articleId);
        System.out.println("删除点赞记录数: " + likeDeleted);
        
        // 删除文章
        int deleted = jdbcTemplate.update("DELETE FROM article WHERE id=?", articleId);
        System.out.println("删除文章结果: " + deleted);
        
        if (deleted == 0) {
            result.put("success", false);
            result.put("message", "目标文章不存在");
        } else {
            result.put("success", true);
            result.put("message", "删除成功");
        }
        System.out.println("返回结果: " + result);
        return result;
    }

    private void requireAdmin() {
        if (!AuthContext.require().isAdmin()) {
            throw new ForbiddenException("只有管理员可以执行该操作");
        }
    }
}

