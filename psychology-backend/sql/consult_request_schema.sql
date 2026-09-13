-- 心理专家咨询申请表
CREATE TABLE IF NOT EXISTS consult_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  username VARCHAR(100) NOT NULL,
  reason LONGTEXT,  -- 申请原因或描述
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING（待处理）, APPROVED（已批准）, REJECTED（已拒绝）, CLOSED（已结束）
  consultant_id BIGINT,  -- 分配的咨询师 ID
  consultant_name VARCHAR(100),  -- 咨询师名称
  notes LONGTEXT,  -- 咨询师备注
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_status (status),
  INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
