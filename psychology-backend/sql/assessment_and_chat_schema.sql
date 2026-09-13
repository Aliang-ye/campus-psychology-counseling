-- ==========================================
-- Assessment (测评) 相关表
-- ==========================================

-- 如果已存在，先删除
DROP TABLE IF EXISTS assessment_answer;
DROP TABLE IF EXISTS assessment_record;

-- 测评记录表
CREATE TABLE assessment_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  username VARCHAR(100) NOT NULL,
  total_score INT NOT NULL,
  dimension_a_score INT NOT NULL DEFAULT 0,  -- 学业压力
  dimension_b_score INT NOT NULL DEFAULT 0,  -- 宿舍关系
  dimension_c_score INT NOT NULL DEFAULT 0,  -- 考试焦虑
  dimension_d_score INT NOT NULL DEFAULT 0,  -- 就业压力
  dimension_e_score INT NOT NULL DEFAULT 0,  -- 恋爱问题
  test_duration INT NOT NULL DEFAULT 0,      -- 测试耗时（秒）
  advice_overall LONGTEXT,                   -- 总体建议（从AI生成后存储）
  advice_by_dimension JSON,                  -- 各维度建议（JSON格式）
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 测评答案详情表
CREATE TABLE assessment_answer (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  record_id BIGINT NOT NULL,
  question_id VARCHAR(50) NOT NULL,
  answer_option INT NOT NULL,  -- 1-5
  score INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (record_id) REFERENCES assessment_record(id) ON DELETE CASCADE,
  INDEX idx_record_id (record_id),
  INDEX idx_question_id (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==========================================
-- Chat (聊天) 相关表
-- ==========================================

DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_session;

-- 聊天会话表（每个用户与AI的会话）
CREATE TABLE chat_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_a BIGINT NOT NULL,        -- 用户ID（真实用户）
  user_b BIGINT NOT NULL,        -- AI ID（固定为0）
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, CLOSED
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_session_user_a (user_a),
  INDEX idx_session_user_b (user_b),
  INDEX idx_session_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 聊天消息表（存储用户与AI的聊天记录）
CREATE TABLE chat_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,     -- 发送者ID（用户ID或0表示AI）
  receiver_id BIGINT NOT NULL,   -- 接收者ID
  content LONGTEXT NOT NULL,     -- 消息内容
  is_read TINYINT(1) DEFAULT 0,  -- 是否已读
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE,
  INDEX idx_message_session (session_id),
  INDEX idx_message_sender (sender_id),
  INDEX idx_message_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==========================================
-- 使用说明
-- ==========================================
/*
1. 运行此脚本在 Navicat 中执行所有SQL语句

2. assessment_record 表：
   - 存储每次测评的结果、分数和AI建议
   - advice_overall: 总体建议（字符串）
   - advice_by_dimension: JSON格式的各维度建议

3. chat_session 表：
   - user_a: 真实用户ID
   - user_b: 固定为0（表示与AI聊天）
   - 通过 LEAST/GREATEST 逻辑，任何用户与AI的会话都只有一条记录

4. chat_message 表：
   - session_id: 关联的聊天会话
   - sender_id: 发送者（用户ID或0表示AI）
   - receiver_id: 接收者
   - 存储所有聊天历史，支持查询和重新加载

5. 索引设置优化查询性能：
   - assessment_record: 按user_id和created_at查询
   - chat_session: 按user_a/user_b查询
   - chat_message: 按session_id和sender_id查询
*/
