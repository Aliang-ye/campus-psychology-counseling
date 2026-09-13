-- 为 user 表添加 status 字段，表示用户是否可接受咨询
ALTER TABLE user
ADD COLUMN status VARCHAR(20) DEFAULT 'available' COMMENT '用户状态: available(可接受咨询), busy(忙碌/拒绝沟通)';

-- 将现有用户的状态设置为可用
UPDATE user SET status = 'available' WHERE status IS NULL OR status = '';
