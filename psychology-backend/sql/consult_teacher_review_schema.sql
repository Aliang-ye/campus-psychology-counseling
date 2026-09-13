-- 学生对发起过咨询的教师进行文本评价
CREATE TABLE IF NOT EXISTS consult_teacher_review (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id BIGINT NOT NULL COMMENT '对应 chat_request.id',
  reviewer_id BIGINT NOT NULL COMMENT '评价人(学生)ID',
  reviewer_name VARCHAR(100) NOT NULL COMMENT '评价人用户名',
  teacher_id BIGINT NOT NULL COMMENT '被评价教师ID',
  teacher_name VARCHAR(100) NOT NULL COMMENT '被评价教师用户名',
  content TEXT NOT NULL COMMENT '评价内容',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_reviewer_id (reviewer_id),
  INDEX idx_teacher_id (teacher_id),
  INDEX idx_request_id (request_id),
  INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
