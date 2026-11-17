package com.campus.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 回看聚合的计数查询。
 *
 * <p>计数必须由 SQL 做，不能交给智能体，也不能靠前端把列表长度当总数。
 * 活动数取「轨迹 ∪ 有效表单」的去重项目数；匿名表单没有 user_id，天然不计入。
 */
@Mapper
public interface InsightMapper {

    @Select("""
            SELECT COUNT(*)
              FROM (
                SELECT project_id
                  FROM participant_traces
                 WHERE user_id = #{userId} AND updated_at_ms >= #{sinceMs}
                UNION
                SELECT project_id
                  FROM form_submissions
                 WHERE user_id = #{userId}
                   AND status = 'valid'
                   AND submitted_at >= #{since}
              ) activity_ids
            """)
    long countActivities(@Param("userId") String userId,
                         @Param("sinceMs") long sinceMs,
                         @Param("since") java.time.LocalDateTime since);

    @Select("""
            SELECT COUNT(*)
              FROM form_submissions
             WHERE user_id = #{userId}
               AND status = 'valid'
               AND submitted_at >= #{since}
            """)
    long countForms(@Param("userId") String userId,
                    @Param("since") java.time.LocalDateTime since);

    @Select("""
            SELECT COUNT(*)
              FROM participant_traces
             WHERE user_id = #{userId} AND updated_at_ms >= #{sinceMs}
            """)
    long countTraces(@Param("userId") String userId, @Param("sinceMs") long sinceMs);
}
