package com.campus.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.campus.server.entity.FormSubmissionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SubmissionMapper {

    // privacy_mode / retention_days 是 SQL 保留字风险很小的别名，这里统一用 s. 前缀避免歧义
    String COLUMNS =
            "id, project_id, version_id, page_id, form_component_id, answers, "
          + "idempotency_key, consent_id, privacy_mode_snapshot, retention_days_snapshot, "
          + "anonymous, anonymous_token, purge_after, submitter_hash, status, submitted_at ";

    @Insert("""
            INSERT INTO form_submissions (id, project_id, version_id, page_id, form_component_id,
                                          answers, idempotency_key, consent_id, privacy_mode_snapshot,
                                          retention_days_snapshot, anonymous, anonymous_token,
                                          purge_after, submitter_hash, status)
            VALUES (#{id}, #{projectId}, #{versionId}, #{pageId}, #{formComponentId},
                    #{answers}, #{idempotencyKey}, #{consentId}, #{privacyModeSnapshot},
                    #{retentionDaysSnapshot}, #{anonymous}, #{anonymousToken},
                    #{purgeAfter}, #{submitterHash}, #{status})
            """)
    int insert(FormSubmissionEntity submission);

    @Select("SELECT " + COLUMNS + " FROM form_submissions "
          + "WHERE project_id = #{projectId} AND idempotency_key = #{idempotencyKey} LIMIT 1")
    FormSubmissionEntity findByIdempotencyKey(@Param("projectId") String projectId,
                                             @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT " + COLUMNS + " FROM form_submissions WHERE id = #{id} LIMIT 1")
    FormSubmissionEntity findById(@Param("id") String id);

    /** 个人提交历史：按匿名编号或 submitter_hash 查（只看自己的）。 */
    @Select("<script>SELECT " + COLUMNS + " FROM form_submissions "
          + "WHERE project_id = #{projectId} AND ("
          + "  (#{token} IS NOT NULL AND anonymous_token = #{token}) OR "
          + "  (#{hash} IS NOT NULL AND submitter_hash = #{hash})"
          + ") ORDER BY submitted_at DESC LIMIT 50</script>")
    List<FormSubmissionEntity> findMine(@Param("projectId") String projectId,
                                        @Param("token") String token,
                                        @Param("hash") String hash);

    @Select("<script>SELECT " + COLUMNS + " FROM form_submissions "
          + "WHERE project_id = #{projectId} "
          + "<if test=\"status != null and status != ''\"> AND status = #{status} </if>"
          + " ORDER BY submitted_at DESC, id ASC LIMIT #{limit} OFFSET #{offset}</script>")
    List<FormSubmissionEntity> page(@Param("projectId") String projectId,
                                    @Param("status") String status,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM form_submissions
             WHERE project_id = #{projectId}
            <if test="status != null and status != ''">
               AND status = #{status}
            </if>
            </script>
            """)
    long count(@Param("projectId") String projectId, @Param("status") String status);

    @Select("SELECT COUNT(*) FROM form_submissions WHERE project_id = #{projectId} AND status = 'valid'")
    long countValid(@Param("projectId") String projectId);

    // ---------------------------------------------------------------- 隐私统计

    @Select("SELECT COUNT(*) FROM form_submissions WHERE project_id = #{projectId} AND anonymous = 1")
    long countAnonymous(@Param("projectId") String projectId);

    @Select("SELECT COUNT(*) FROM form_submissions "
          + "WHERE project_id = #{projectId} AND consent_id IS NOT NULL")
    long countConsented(@Param("projectId") String projectId);

    @Select("""
            SELECT DATE(submitted_at) AS d, COUNT(*) AS c
              FROM form_submissions
             WHERE project_id = #{projectId}
             GROUP BY DATE(submitted_at)
             ORDER BY d
            """)
    List<java.util.Map<String, Object>> byDay(@Param("projectId") String projectId);

    @Select("""
            SELECT status AS s, COUNT(*) AS c
              FROM form_submissions
             WHERE project_id = #{projectId}
             GROUP BY status
            """)
    List<java.util.Map<String, Object>> byStatus(@Param("projectId") String projectId);

    /**
     * 到期清除：把到期的报名答案抹掉，但保留一条「曾经有人报过」的空壳，
     * 让统计数字不会因为到期而凭空缩水。返回影响行数。
     */
    @Update("""
            UPDATE form_submissions
               SET answers = JSON_OBJECT(),
                   submitter_hash = NULL,
                   anonymous_token = NULL,
                   status = 'purged',
                   idempotency_key = CONCAT('purged-', id)
             WHERE project_id = #{projectId}
               AND purge_after IS NOT NULL
               AND purge_after <= NOW()
               AND status <> 'purged'
            """)
    int purgeExpiredOfProject(@Param("projectId") String projectId);

    /** 全库到期清理（定时任务用），返回影响行数。 */
    @Update("""
            UPDATE form_submissions
               SET answers = JSON_OBJECT(),
                   submitter_hash = NULL,
                   anonymous_token = NULL,
                   status = 'purged',
                   idempotency_key = CONCAT('purged-', id)
             WHERE purge_after IS NOT NULL
               AND purge_after <= NOW()
               AND status <> 'purged'
            """)
    int purgeExpired();

    /** 距离最近一次到期还有多少条（给看板提示用）。 */
    @Select("""
            SELECT COUNT(*) FROM form_submissions
             WHERE project_id = #{projectId}
               AND purge_after IS NOT NULL
               AND status <> 'purged'
            """)
    long countScheduled(@Param("projectId") String projectId);
}
