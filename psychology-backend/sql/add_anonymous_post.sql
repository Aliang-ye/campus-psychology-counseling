-- 为 post 表添加匿名发帖字段
ALTER TABLE post
ADD COLUMN is_anonymous TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否匿名发帖: 0-否, 1-是';
