package com.example.psychology.controller;

import com.example.psychology.entity.Post;
import com.example.psychology.entity.Reply;
import com.example.psychology.dto.PostDto;
import com.example.psychology.dto.ReplyDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;
import com.example.psychology.security.AuthContext;
import com.example.psychology.security.AuthUser;
import com.example.psychology.security.ForbiddenException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/community")
@CrossOrigin
public class CommunityController {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Post 行映射器
    private static final RowMapper<Post> POST_ROW_MAPPER = new RowMapper<Post>() {
        @Override
        public Post mapRow(ResultSet rs, int rowNum) throws SQLException {
            Post post = new Post();
            post.setId(rs.getLong("id"));
            post.setUserId(rs.getLong("user_id"));
            post.setUsername(rs.getString("username"));
            post.setRole(rs.getString("role"));
            post.setTitle(rs.getString("title"));
            post.setContent(rs.getString("content"));
            post.setIsPinned(rs.getBoolean("is_pinned"));
            post.setIsLocked(rs.getBoolean("is_locked"));
            post.setReplyCount(rs.getInt("reply_count"));
            post.setStatus(rs.getString("status"));
            post.setAuditComment(rs.getString("audit_comment"));
            post.setIsAnonymous(rs.getBoolean("is_anonymous"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                post.setCreatedAt(createdAt.toLocalDateTime());
            }
            return post;
        }
    };

    // Reply 行映射器
    private static final RowMapper<Reply> REPLY_ROW_MAPPER = new RowMapper<Reply>() {
        @Override
        public Reply mapRow(ResultSet rs, int rowNum) throws SQLException {
            Reply reply = new Reply();
            reply.setId(rs.getLong("id"));
            reply.setPostId(rs.getLong("post_id"));
            reply.setUserId(rs.getLong("user_id"));
            reply.setUsername(rs.getString("username"));
            reply.setRole(rs.getString("role"));
            reply.setContent(rs.getString("content"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                reply.setCreatedAt(createdAt.toLocalDateTime());
            }
            return reply;
        }
    };

    // 获取所有帖子列表（只显示已审核通过的帖子）
    @GetMapping("/posts")
    public Map<String, Object> getAllPosts(
            @RequestParam(required = false) String viewerRole) {
        Map<String, Object> response = new HashMap<>();
        List<Post> posts = new ArrayList<>();

        try {
            String sql = "SELECT * FROM post WHERE status = 'approved' ORDER BY is_pinned DESC, created_at DESC";
            posts = jdbcTemplate.query(sql, POST_ROW_MAPPER);

            // 非管理员查看匿名帖时隐藏真实作者信息
            if (!"admin".equals(viewerRole)) {
                for (Post p : posts) {
                    if (Boolean.TRUE.equals(p.getIsAnonymous())) {
                        p.setUsername("匿名用户");
                    }
                }
            }

            response.put("code", 200);
            response.put("message", "获取帖子列表成功");
            response.put("data", posts);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "获取帖子列表失败: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    // 获取单个帖子详情及其回复
    @GetMapping("/posts/{postId}")
    public Map<String, Object> getPostDetail(
            @PathVariable Long postId,
            @RequestParam(required = false) String viewerRole) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();

        try {
            // 获取帖子信息
            String postSql = "SELECT * FROM post WHERE id = ?";
            List<Post> posts = jdbcTemplate.query(postSql, new Object[]{postId}, POST_ROW_MAPPER);
            
            if (posts.isEmpty()) {
                response.put("code", 404);
                response.put("message", "帖子不存在");
                return response;
            }

            Post post = posts.get(0);

            // 非管理员查看匿名帖时隐藏真实作者信息
            if (!"admin".equals(viewerRole) && Boolean.TRUE.equals(post.getIsAnonymous())) {
                post.setUsername("匿名用户");
            }

            // 获取回复列表
            String replySql = "SELECT * FROM reply WHERE post_id = ? ORDER BY created_at ASC";
            List<Reply> replies = jdbcTemplate.query(replySql, new Object[]{postId}, REPLY_ROW_MAPPER);

            data.put("post", post);
            data.put("replies", replies);

            response.put("code", 200);
            response.put("message", "获取帖子详情成功");
            response.put("data", data);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "获取帖子详情失败: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    // 发布帖子
    @PostMapping("/posts")
    public Map<String, Object> createPost(@RequestBody PostDto postDto) {
        Map<String, Object> response = new HashMap<>();
        AuthUser currentUser = AuthContext.require();
        Long userId = currentUser.getUserId();
        String username = currentUser.getUsername();
        String role = currentUser.getRole();

        try {
            boolean anonymous = Boolean.TRUE.equals(postDto.getIsAnonymous());
            String sql = "INSERT INTO post (user_id, username, role, title, content, status, is_anonymous) VALUES (?, ?, ?, ?, ?, 'pending', ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            int affectedRows = jdbcTemplate.update(connection -> {
                var ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, userId);
                ps.setString(2, username);
                ps.setString(3, role);
                ps.setString(4, postDto.getTitle());
                ps.setString(5, postDto.getContent());
                ps.setBoolean(6, anonymous);
                return ps;
            }, keyHolder);
            
            if (affectedRows > 0 && keyHolder.getKey() != null) {
                response.put("code", 201);
                response.put("message", "发布帖子成功");
                response.put("data", Collections.singletonMap("postId", keyHolder.getKey().longValue()));
            } else {
                response.put("code", 500);
                response.put("message", "发布帖子失败");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "发布帖子失败: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    // 获取待审核帖子列表（仅管理员）
    @GetMapping("/posts/pending")
    public Map<String, Object> getPendingPosts() {
        Map<String, Object> response = new HashMap<>();
        requireAdmin();

        List<Post> posts = new ArrayList<>();
        try {
            String sql = "SELECT * FROM post WHERE status = 'pending' ORDER BY created_at DESC";
            posts = jdbcTemplate.query(sql, POST_ROW_MAPPER);

            response.put("code", 200);
            response.put("message", "获取待审核帖子成功");
            response.put("data", posts);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "获取待审核帖子失败: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    // 审核帖子（仅管理员）
    @PutMapping("/posts/{postId}/audit")
    public Map<String, Object> auditPost(
            @PathVariable Long postId,
            @RequestParam String action,
            @RequestParam(required = false) String comment) {
        Map<String, Object> response = new HashMap<>();
        requireAdmin();

        if (!action.equals("approve") && !action.equals("reject")) {
            response.put("code", 400);
            response.put("message", "无效的审核操作");
            return response;
        }

        try {
            String newStatus = action.equals("approve") ? "approved" : "rejected";
            String sql = "UPDATE post SET status = ?, audit_comment = ? WHERE id = ?";
            int affectedRows = jdbcTemplate.update(sql, newStatus, comment, postId);

            if (affectedRows > 0) {
                String msg = action.equals("approve") ? "帖子审核通过" : "帖子已被拒绝";
                response.put("code", 200);
                response.put("message", msg);
            } else {
                response.put("code", 404);
                response.put("message", "帖子不存在");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "审核失败: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    // 删除帖子
    @DeleteMapping("/posts/{postId}")
    public Map<String, Object> deletePost(
            @PathVariable Long postId) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 先检查帖子所有者
            String checkSql = "SELECT user_id FROM post WHERE id = ?";
            List<Map<String, Object>> result = jdbcTemplate.queryForList(checkSql, postId);

            if (result.isEmpty()) {
                response.put("code", 404);
                response.put("message", "帖子不存在");
                return response;
            }

            Long postOwnerId = ((Number) result.get(0).get("user_id")).longValue();
            AuthUser currentUser = AuthContext.require();
            if (!currentUser.getUserId().equals(postOwnerId) && !currentUser.isAdmin()) {
                throw new ForbiddenException("无权删除此帖子");
            }

            // 删除帖子（会级联删除回复）
            String deleteSql = "DELETE FROM post WHERE id = ?";
            int affectedRows = jdbcTemplate.update(deleteSql, postId);

            if (affectedRows > 0) {
                response.put("code", 200);
                response.put("message", "删除帖子成功");
            } else {
                response.put("code", 500);
                response.put("message", "删除帖子失败");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "删除帖子失败: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    // 置顶帖子（仅管理员）
    @PutMapping("/posts/{postId}/pin")
    public Map<String, Object> pinPost(
            @PathVariable Long postId) {
        Map<String, Object> response = new HashMap<>();
        requireAdmin();

        try {
            String sql = "UPDATE post SET is_pinned = TRUE WHERE id = ?";
            int affectedRows = jdbcTemplate.update(sql, postId);

            if (affectedRows > 0) {
                response.put("code", 200);
                response.put("message", "置顶帖子成功");
            } else {
                response.put("code", 404);
                response.put("message", "帖子不存在");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "置顶帖子失败: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    // 取消置顶帖子（仅管理员）
    @PutMapping("/posts/{postId}/unpin")
    public Map<String, Object> unpinPost(
            @PathVariable Long postId) {
        Map<String, Object> response = new HashMap<>();
        requireAdmin();

        try {
            String sql = "UPDATE post SET is_pinned = FALSE WHERE id = ?";
            int affectedRows = jdbcTemplate.update(sql, postId);

            if (affectedRows > 0) {
                response.put("code", 200);
                response.put("message", "取消置顶成功");
            } else {
                response.put("code", 404);
                response.put("message", "帖子不存在");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "取消置顶失败: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    // 锁帖（禁止回复，仅管理员）
    @PutMapping("/posts/{postId}/lock")
    public Map<String, Object> lockPost(
            @PathVariable Long postId) {
        Map<String, Object> response = new HashMap<>();
        requireAdmin();

        try {
            String sql = "UPDATE post SET is_locked = TRUE WHERE id = ?";
            int affectedRows = jdbcTemplate.update(sql, postId);

            if (affectedRows > 0) {
                response.put("code", 200);
                response.put("message", "锁帖成功");
            } else {
                response.put("code", 404);
                response.put("message", "帖子不存在");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "锁帖失败: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    // 解锁帖子（仅管理员）
    @PutMapping("/posts/{postId}/unlock")
    public Map<String, Object> unlockPost(
            @PathVariable Long postId) {
        Map<String, Object> response = new HashMap<>();
        requireAdmin();

        try {
            String sql = "UPDATE post SET is_locked = FALSE WHERE id = ?";
            int affectedRows = jdbcTemplate.update(sql, postId);

            if (affectedRows > 0) {
                response.put("code", 200);
                response.put("message", "解锁帖子成功");
            } else {
                response.put("code", 404);
                response.put("message", "帖子不存在");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "解锁帖子失败: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    // 添加回复
    @PostMapping("/replies")
    public Map<String, Object> createReply(
            @RequestParam Long postId,
            @RequestBody ReplyDto replyDto) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 检查帖子是否存在且未锁定
            String checkSql = "SELECT is_locked FROM post WHERE id = ?";
            List<Map<String, Object>> result = jdbcTemplate.queryForList(checkSql, postId);

            if (result.isEmpty()) {
                response.put("code", 404);
                response.put("message", "帖子不存在");
                return response;
            }

            Boolean isLocked = (Boolean) result.get(0).get("is_locked");
            if (isLocked != null && isLocked) {
                response.put("code", 403);
                response.put("message", "该帖子已被锁定，无法回复");
                return response;
            }

            AuthUser currentUser = AuthContext.require();
            String replySql = "INSERT INTO reply (post_id, user_id, username, role, content) VALUES (?, ?, ?, ?, ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            int affectedRows = jdbcTemplate.update(connection -> {
                var ps = connection.prepareStatement(replySql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, postId);
                ps.setLong(2, currentUser.getUserId());
                ps.setString(3, currentUser.getUsername());
                ps.setString(4, currentUser.getRole());
                ps.setString(5, replyDto.getContent());
                return ps;
            }, keyHolder);

            if (affectedRows > 0 && keyHolder.getKey() != null) {
                String updateSql = "UPDATE post SET reply_count = reply_count + 1 WHERE id = ?";
                jdbcTemplate.update(updateSql, postId);
                response.put("code", 201);
                response.put("message", "添加回复成功");
                response.put("data", Collections.singletonMap("replyId", keyHolder.getKey().longValue()));
            } else {
                response.put("code", 500);
                response.put("message", "添加回复失败");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "添加回复失败: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    // 删除回复
    @DeleteMapping("/replies/{replyId}")
    public Map<String, Object> deleteReply(
            @PathVariable Long replyId) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 检查回复所有者
            String checkSql = "SELECT user_id, post_id FROM reply WHERE id = ?";
            List<Map<String, Object>> result = jdbcTemplate.queryForList(checkSql, replyId);

            if (result.isEmpty()) {
                response.put("code", 404);
                response.put("message", "回复不存在");
                return response;
            }

            Long replyOwnerId = ((Number) result.get(0).get("user_id")).longValue();
            Long postId = ((Number) result.get(0).get("post_id")).longValue();
            AuthUser currentUser = AuthContext.require();
            if (!currentUser.getUserId().equals(replyOwnerId) && !currentUser.isAdmin()) {
                throw new ForbiddenException("无权删除此回复");
            }

            // 删除回复
            String deleteSql = "DELETE FROM reply WHERE id = ?";
            int affectedRows = jdbcTemplate.update(deleteSql, replyId);

            if (affectedRows > 0) {
                // 减少帖子的回复数
                String updateSql = "UPDATE post SET reply_count = reply_count - 1 WHERE id = ?";
                jdbcTemplate.update(updateSql, postId);

                response.put("code", 200);
                response.put("message", "删除回复成功");
            } else {
                response.put("code", 500);
                response.put("message", "删除回复失败");
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "删除回复失败: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    private void requireAdmin() {
        if (!AuthContext.require().isAdmin()) {
            throw new ForbiddenException("只有管理员才能执行该操作");
        }
    }
}
