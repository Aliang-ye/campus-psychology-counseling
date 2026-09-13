-- 为 post 表添加审核相关字段
ALTER TABLE post
ADD COLUMN status VARCHAR(20) DEFAULT 'pending' COMMENT '审核状态: pending(待审核), approved(已发布), rejected(已拒绝)',
ADD COLUMN audit_comment TEXT COMMENT '审核备注';

-- 将现有数据的状态设置为已发布
UPDATE post SET status = 'approved' WHERE status IS NULL OR status = '';
