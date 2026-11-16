-- 011_participant_traces.sql
-- 参与端「动态参与与个人轨迹」——ParticipantTrace 的持久化。
--
-- 背景：前端 web/src/types/trace.ts 定义 ParticipantTrace，web/src/services/traces.ts
-- 声明两个接口 GET /traces?activityId= 与 PUT /traces/:activityId。本迁移是它们的数据底座。
--
-- 设计取舍（写在这里避免以后被"优化"掉）：
--   1. 一条轨迹 = 一个人在一个活动里的全部记录，所以主键是 (user_id, project_id)。
--      不用自增 id：这张表没有任何地方需要"按 id 找一条轨迹"，都是按人+活动查。
--   2. payload 整块存 JSON，不拆成 tags / notes / session_context 三张表。
--      理由：轨迹结构还在变（前端一期规则引擎、三期换 agent），拆表会让每次加字段都要迁移；
--      而且查询永远是"整条取出来"，没有按 tag 搜索的需求。等真的需要按标签统计时再拆。
--   3. activity_id 存的是 project_id（UUID），不是 slug。
--      前端传的是 slug，由服务端反查成 UUID 再存 —— 这样 slug 改名后轨迹不会变成孤儿数据。
--   4. updated_at_ms 是前端传的毫秒时间戳（前端用它做新旧比对，见 stores/trace.ts:181
--      `normalized.updatedAt >= base.updatedAt`）。必须原样存，不能用数据库的 NOW()，
--      否则前端本地时钟与服务器有偏差时会误判"远端更旧/更新"。
--   5. 不做软删除：前端「删除」是删本地 + 删远端整条，没有回收站需求。

CREATE TABLE IF NOT EXISTS participant_traces (
    user_id        CHAR(36)     NOT NULL COMMENT '轨迹归属用户',
    project_id     CHAR(36)     NOT NULL COMMENT '活动（projects.id），由 slug 反查得到',
    domain         VARCHAR(16)  NOT NULL DEFAULT 'mental' COMMENT 'mental 心理 / career 生涯',
    semester       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '学期，前端可留空',
    summary        VARCHAR(500) NOT NULL DEFAULT '' COMMENT '用户手动编辑的一句话总结',
    payload        JSON         NOT NULL COMMENT '完整 ParticipantTrace：tags/sessionContext/notes 等',
    updated_at_ms  BIGINT       NOT NULL COMMENT '前端毫秒时间戳，原样保存用于新旧比对',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, project_id),
    KEY idx_traces_user_updated (user_id, updated_at_ms DESC),
    KEY idx_traces_project (project_id),
    CONSTRAINT fk_traces_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_traces_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_traces_domain CHECK (domain IN ('mental', 'career')),
    CONSTRAINT ck_traces_payload CHECK (JSON_TYPE(payload) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='参与者的动态参与轨迹，一人一活动一条';

-- 说明：summary 放开到 500 是因为前端 updateSummary 允许用户写一整句，
-- 而它同时冗余存在 payload.summary 里 —— 冗余是为了「我的轨迹」列表页能只查这几列，
-- 不用为每条记录解析 JSON。两者由服务端写入时保证一致。
