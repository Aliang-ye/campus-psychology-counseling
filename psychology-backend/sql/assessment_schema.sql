-- 心理测评题目表
CREATE TABLE IF NOT EXISTS assessment_question (
    id VARCHAR(10) PRIMARY KEY COMMENT '题目ID (如A01, B01)',
    dimension VARCHAR(20) NOT NULL COMMENT '维度名称 (学业压力/宿舍关系/考试焦虑/就业压力/恋爱问题)',
    content TEXT NOT NULL COMMENT '题目内容',
    option1 VARCHAR(50) NOT NULL COMMENT '选项1文本',
    option2 VARCHAR(50) NOT NULL COMMENT '选项2文本',
    option3 VARCHAR(50) NOT NULL COMMENT '选项3文本',
    option4 VARCHAR(50) NOT NULL COMMENT '选项4文本',
    option5 VARCHAR(50) NOT NULL COMMENT '选项5文本',
    weight INT DEFAULT 1 COMMENT '计分权重',
    is_reverse BOOLEAN DEFAULT FALSE COMMENT '是否反向计分',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='心理测评题库';

-- 心理测评记录表
CREATE TABLE IF NOT EXISTS assessment_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    username VARCHAR(100) COMMENT '用户名（冗余存储，便于查询）',
    total_score INT NOT NULL COMMENT '总分 (满分150)',
    dimension_a_score INT NOT NULL COMMENT '学业压力维度得分',
    dimension_b_score INT NOT NULL COMMENT '宿舍关系维度得分',
    dimension_c_score INT NOT NULL COMMENT '考试焦虑维度得分',
    dimension_d_score INT NOT NULL COMMENT '就业压力维度得分',
    dimension_e_score INT NOT NULL COMMENT '恋爱问题维度得分',
    test_duration INT COMMENT '测试时长（秒）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '测评时间',
    INDEX idx_user_id (user_id),
    INDEX idx_username (username),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='心理测评记录';

-- 心理测评答题详情表（用于存储用户具体答题情况）
CREATE TABLE IF NOT EXISTS assessment_answer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL COMMENT '测评记录ID',
    question_id VARCHAR(10) NOT NULL COMMENT '题目ID',
    answer_option INT NOT NULL COMMENT '选择的选项 (1-5)',
    score INT NOT NULL COMMENT '该题得分',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_record_id (record_id),
    FOREIGN KEY (record_id) REFERENCES assessment_record(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='心理测评答题详情';
