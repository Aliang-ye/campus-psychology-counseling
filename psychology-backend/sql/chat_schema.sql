-- Recreate chat subsystem: drop old chat tables and create new ones.
-- WARNING: This will remove existing chat schema/data. Run only when you want to reset chat data.

DROP TABLE IF EXISTS chat_message_archive;
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_session;
DROP TABLE IF EXISTS chat_request;

-- Table to store pending requests (invitations). A request exists until accepted or rejected.
CREATE TABLE chat_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  from_user_id BIGINT NOT NULL,
  to_user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, ACCEPTED, REJECTED
  session_id BIGINT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_request_from (from_user_id),
  INDEX idx_request_to (to_user_id),
  INDEX idx_request_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Table for active/closed chat sessions between two users.
CREATE TABLE chat_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_a BIGINT NOT NULL,
  user_b BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, CLOSED
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_session_user_a (user_a),
  INDEX idx_session_user_b (user_b),
  INDEX idx_session_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Messages persisted per session. Keeps history for reloads.
CREATE TABLE chat_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  receiver_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  is_read TINYINT(1) DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_message_session (session_id),
  INDEX idx_message_sender (sender_id),
  CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Optional archive table (same structure)
CREATE TABLE chat_message_archive LIKE chat_message;

-- Notes:
-- 1) Ensure your `user` table exists; consider adding FK constraints if desired.
-- 2) Workflow:
--    - A sends request -> insert row in `chat_request` (PENDING)
--    - B accepts -> update request.status=ACCEPTED, create a `chat_session` row, store session_id in request, notify A/B
--    - B rejects -> update request.status=REJECTED, notify A
--    - Messages are inserted into `chat_message` with session_id
--    - Closing a session sets `chat_session.status` to CLOSED
-- 3) Backups: use mysqldump or scheduled archive to `chat_message_archive` for old messages.
