-- SCL-90 测评历史记录表
CREATE TABLE IF NOT EXISTS scl90_record (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT       NOT NULL,
  username      VARCHAR(100) NOT NULL,
  total_score   INT          NOT NULL COMMENT '总分（90题之和）',
  total_avg     DOUBLE       NOT NULL COMMENT '总均分 = 总分/90',
  positive_count INT         NOT NULL COMMENT '阳性项目数（得分>1的题目数）',
  positive_avg  DOUBLE       NOT NULL COMMENT '阳性症状均分',
  -- 9个因子均分
  f1_somatization   DOUBLE NOT NULL DEFAULT 0 COMMENT '躯体化',
  f2_obsession      DOUBLE NOT NULL DEFAULT 0 COMMENT '强迫症状',
  f3_interpersonal  DOUBLE NOT NULL DEFAULT 0 COMMENT '人际关系敏感',
  f4_depression     DOUBLE NOT NULL DEFAULT 0 COMMENT '抑郁',
  f5_anxiety        DOUBLE NOT NULL DEFAULT 0 COMMENT '焦虑',
  f6_hostility      DOUBLE NOT NULL DEFAULT 0 COMMENT '敌对',
  f7_phobia         DOUBLE NOT NULL DEFAULT 0 COMMENT '恐怖',
  f8_paranoia       DOUBLE NOT NULL DEFAULT 0 COMMENT '偏执',
  f9_psychosis      DOUBLE NOT NULL DEFAULT 0 COMMENT '精神病性',
  answers_json  JSON         COMMENT '答题详情 {item_id: score, ...}',
  test_duration INT          NOT NULL DEFAULT 0 COMMENT '耗时秒',
  created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_scl90_user (user_id),
  INDEX idx_scl90_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
