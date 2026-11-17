-- ============================================================================
-- 012_insight_agent.sql —— 回看智能体（M1：数据与契约）
--
-- 两个新表：
--   1) tool_records  —— 能量牌局 / 三栏记录落库，跨设备可读。
--      原来这些只存在 localStorage，回看只能分析活动表单，工具记录完全不参与。
--   2) insight_runs  —— 每次「让 AI 回看」的可复现记录。
--      产品文档要求「记录 insightId / model / promptVersion / 输入哈希，便于复现」，
--      同时它也是 24 小时缓存的落地位置。
--
-- 幂等：只有 CREATE TABLE IF NOT EXISTS，可重复执行。
-- 顺序：在 011 之后跑（docs/sql/README.md 也补了本文件）。
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------- tool_records
CREATE TABLE IF NOT EXISTS tool_records (
  id            CHAR(36)     NOT NULL,
  user_id       CHAR(36)     NOT NULL,
  type          VARCHAR(16)  NOT NULL COMMENT 'energy=能量牌局 / thought=三栏',
  title         VARCHAR(160) NOT NULL DEFAULT '',
  summary       VARCHAR(500) NOT NULL DEFAULT '',
  tags          JSON         NOT NULL COMMENT '标签数组，只存短词，不存长文本',
  created_at_ms BIGINT       NOT NULL COMMENT '客户端毫秒时间戳，原样保留用于排序',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tool_records_user_time (user_id, created_at_ms DESC),
  CONSTRAINT fk_tool_records_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_tool_records_type CHECK (type IN ('energy', 'thought')),
  CONSTRAINT ck_tool_records_tags CHECK (JSON_TYPE(tags) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='参与者的工具记录：能量牌局与三栏，回看分析的输入之一';

-- ---------------------------------------------------------------- insight_runs
CREATE TABLE IF NOT EXISTS insight_runs (
  id             CHAR(36)     NOT NULL,
  user_id        CHAR(36)     NOT NULL,
  facts_hash     CHAR(64)     NOT NULL COMMENT '输入事实的 sha256，用于缓存与复现',
  days           INT          NOT NULL,
  model          VARCHAR(120) NOT NULL DEFAULT '',
  prompt_version VARCHAR(40)  NOT NULL DEFAULT '',
  status         VARCHAR(24)  NOT NULL
                 COMMENT 'ok / insufficient_data / crisis / analysis_error',
  response_json  JSON         NULL COMMENT '成功时的完整响应，缓存命中直接回放',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_insight_runs_cache (user_id, facts_hash, created_at),
  CONSTRAINT fk_insight_runs_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_insight_runs_status CHECK (status IN
    ('ok', 'insufficient_data', 'crisis', 'analysis_error')),
  CONSTRAINT ck_insight_runs_response CHECK
    (response_json IS NULL OR JSON_TYPE(response_json) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='回看智能体的调用记录：模型、提示词版本、事实哈希与结果';

-- 自查：
--   SHOW TABLES LIKE '%tool_records%';
--   SHOW TABLES LIKE '%insight_runs%';
