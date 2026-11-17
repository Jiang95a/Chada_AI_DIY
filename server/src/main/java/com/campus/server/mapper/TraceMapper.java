package com.campus.server.mapper;

import com.campus.server.entity.ParticipantTraceEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 表 participant_traces 的读写。
 *
 * <p>主键是 (user_id, project_id)：一条轨迹就是「一个人在一场活动里的全部记录」。
 * 所有查询都带 user_id 条件——这张表没有「看别人的轨迹」这种用法，
 * 唯一例外是下面 listByUser 里的 join 到 projects 用于回显活动标题，
 * 那是**查自己**的轨迹时连带取活动名，不是越权。
 */
@Mapper
public interface TraceMapper {

    String COLUMNS =
            "user_id, project_id, domain, semester, summary, payload, updated_at_ms, created_at, updated_at ";

    @Select("SELECT " + COLUMNS + " FROM participant_traces WHERE user_id = #{userId} AND project_id = #{projectId} LIMIT 1")
    ParticipantTraceEntity find(@Param("userId") String userId, @Param("projectId") String projectId);

    /**
     * 按人取全部轨迹（「我的轨迹」页需要跨活动列出）。
     * 只返回有内容的：summary 或 payload.tags 非空，与前端 list() 的过滤口径一致
     * （见 web/src/stores/trace.ts:268）。
     */
    @Select("""
            SELECT t.user_id, t.project_id, t.domain, t.semester, t.summary, t.payload,
                   t.updated_at_ms, t.created_at, t.updated_at,
                   p.title AS activity_title, p.public_slug
              FROM participant_traces t
              JOIN projects p ON p.id = t.project_id
             WHERE t.user_id = #{userId}
             ORDER BY t.updated_at_ms DESC
             LIMIT #{limit}
            """)
    List<java.util.Map<String, Object>> listByUser(@Param("userId") String userId, @Param("limit") int limit);

    /** 聚合接口只取一段时间内的轨迹；仍按 user_id 隔离。 */
    @Select("""
            SELECT t.user_id, t.project_id, t.domain, t.semester, t.summary, t.payload,
                   t.updated_at_ms, t.created_at, t.updated_at,
                   p.title AS activity_title, p.public_slug
              FROM participant_traces t
              JOIN projects p ON p.id = t.project_id
             WHERE t.user_id = #{userId} AND t.updated_at_ms >= #{sinceMs}
             ORDER BY t.updated_at_ms ASC
             LIMIT #{limit}
            """)
    List<java.util.Map<String, Object>> listByUserSince(@Param("userId") String userId,
                                                        @Param("sinceMs") long sinceMs,
                                                        @Param("limit") int limit);

    /**
     * 写入或整条覆盖。前端 PUT 是「整条轨迹覆盖」，不是增量补丁
     * （web/src/services/traces.ts:37 直接把整个 trace 当 body），
     * 所以这里用 ON DUPLICATE KEY UPDATE 覆盖全部字段，而不是只改部分。
     */
    @Insert("""
            INSERT INTO participant_traces
              (user_id, project_id, domain, semester, summary, payload, updated_at_ms)
            VALUES
              (#{userId}, #{projectId}, #{domain}, #{semester}, #{summary}, CAST(#{payload} AS JSON), #{updatedAtMs})
            ON DUPLICATE KEY UPDATE
              domain = VALUES(domain),
              semester = VALUES(semester),
              summary = VALUES(summary),
              payload = VALUES(payload),
              updated_at_ms = VALUES(updated_at_ms)
            """)
    int upsert(ParticipantTraceEntity trace);

    @Delete("DELETE FROM participant_traces WHERE user_id = #{userId} AND project_id = #{projectId}")
    int delete(@Param("userId") String userId, @Param("projectId") String projectId);
}
