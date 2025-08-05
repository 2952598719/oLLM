CREATE EXTENSION IF NOT EXISTS vector;

CREATE DATABASE vector_store ENCODING 'UTF8';
CREATE DATABASE ollm ENCODING 'UTF8';
\c ollm

DROP TABLE IF EXISTS users;
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1 CHECK ( status IN (0, 1) ),  -- 0表示已删除，1表示活跃
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON COLUMN users.id IS '用户id';
INSERT INTO users (id, email, password) VALUES (1951998471364022272, '2952598719@qq.com', '$2a$10$/nrRnnPgLpIAot.ofO5Z7.sfZdTYqhN7/dmKU6kqxGxnZG5ypxVEq');   -- aa111111


DROP TABLE IF EXISTS chats;
CREATE TABLE chats (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1 CHECK ( status IN (0, 1) ),  -- 0表示已删除，1表示活跃
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON COLUMN chats.id IS '会话id';
COMMENT ON COLUMN chats.user_id IS '用户id';
COMMENT ON COLUMN chats.title IS '会话标题';
COMMENT ON COLUMN chats.status IS '会话状态';


DROP TABLE IF EXISTS messages;
CREATE TABLE messages (
    id BIGINT PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('system', 'assistant', 'user')),
    content TEXT NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1 CHECK ( status IN (0, 1) ),  -- 0表示已删除，1表示活跃
    tag_id BIGINT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON COLUMN messages.id IS '消息id';
COMMENT ON COLUMN messages.chat_id IS '所属会话id';
COMMENT ON COLUMN messages.role IS '消息角色';
COMMENT ON COLUMN messages.content IS '消息内容';
COMMENT ON COLUMN messages.status IS '消息状态';
COMMENT ON COLUMN messages.tag_id IS '这条消息使用的知识库id，如果不使用则为空';


DROP TABLE IF EXISTS tags;
CREATE TABLE tags (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tag_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON COLUMN tags.id IS '知识库id';
COMMENT ON COLUMN tags.user_id IS '所属用户id';
COMMENT ON COLUMN tags.tag_name IS '知识库名字';


-- 创建更新时间触发器函数
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
-- $$ LANGUAGE plpgsql;

-- 为每个表添加更新触发器
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_modified_column();
CREATE TRIGGER update_chats_updated_at BEFORE UPDATE ON chats FOR EACH ROW EXECUTE FUNCTION update_modified_column();
CREATE TRIGGER update_messages_updated_at BEFORE UPDATE ON messages FOR EACH ROW EXECUTE FUNCTION update_modified_column();