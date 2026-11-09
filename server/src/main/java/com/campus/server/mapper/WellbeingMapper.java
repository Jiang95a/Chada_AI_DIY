package com.campus.server.mapper;

import com.campus.server.entity.ActivityConsentEntity;
import com.campus.server.entity.ActivityFeedbackEntity;
import com.campus.server.entity.ReferralRequestEntity;
import com.campus.server.entity.UserNotificationEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 心理社团三张新表：知情同意 / 活动反馈 / 站内通知。
 * DDL 见 docs/sql/007_wellbeing_privacy.sql。
 */
@Mapper
public interface WellbeingMapper {

    // ---------------------------------------------------------------- activity_consents

    String CONSENT_COLUMNS =
            "id, project_id, version_id, consent_version, consent_text, consent_hash, "
          + "privacy_mode, retention_days, submitter_hash, accepted, accepted_at, purge_after ";

    @Insert("""
            INSERT INTO activity_consents (id, project_id, version_id, consent_version,
                                           consent_text, consent_hash, privacy_mode,
                                           retention_days, submitter_hash, accepted, purge_after)
            VALUES (#{id}, #{projectId}, #{versionId}, #{consentVersion},
                    #{consentText}, #{consentHash}, #{privacyMode},
                    #{retentionDays}, #{submitterHash}, #{accepted}, #{purgeAfter})
            """)
    int insertConsent(ActivityConsentEntity consent);

    @Select("SELECT " + CONSENT_COLUMNS + " FROM activity_consents WHERE id = #{id} LIMIT 1")
    ActivityConsentEntity findConsent(@Param("id") String id);

    @Select("""
            <script>
            SELECT COUNT(*) FROM activity_consents
             WHERE project_id = #{projectId} AND accepted = 1
            </script>
            """)
    long countConsents(@Param("projectId") String projectId);

    /** 到期清理：同意文本本身也要按保留期限清掉。 */
    @Update("""
            UPDATE activity_consents
               SET consent_text = '（已按保留期限清除）',
                   submitter_hash = NULL,
                   purge_after = NULL
             WHERE purge_after IS NOT NULL AND purge_after <= NOW()
            """)
    int purgeExpiredConsents();

    // ---------------------------------------------------------------- activity_feedback

    String FEEDBACK_COLUMNS =
            "id, project_id, choice, score, comment, anonymous, submitter_hash, created_at, purge_after ";

    @Insert("""
            INSERT INTO activity_feedback (id, project_id, choice, score, comment,
                                           anonymous, submitter_hash, purge_after)
            VALUES (#{id}, #{projectId}, #{choice}, #{score}, #{comment},
                    #{anonymous}, #{submitterHash}, #{purgeAfter})
            """)
    int insertFeedback(ActivityFeedbackEntity feedback);

    @Select("""
            SELECT choice AS k, COUNT(*) AS c
              FROM activity_feedback
             WHERE project_id = #{projectId}
             GROUP BY choice
            """)
    List<Map<String, Object>> feedbackDistribution(@Param("projectId") String projectId);

    @Select("SELECT COUNT(*) FROM activity_feedback WHERE project_id = #{projectId}")
    long countFeedback(@Param("projectId") String projectId);

    @Select("SELECT AVG(score) FROM activity_feedback "
          + "WHERE project_id = #{projectId} AND score IS NOT NULL")
    Double averageFeedbackScore(@Param("projectId") String projectId);

    /** 只取最近的几条，且永远不带 submitter_hash / anonymous_token。 */
    @Select("""
            SELECT comment FROM activity_feedback
             WHERE project_id = #{projectId}
               AND comment IS NOT NULL AND comment <> ''
             ORDER BY created_at DESC
             LIMIT 20
            """)
    List<String> recentComments(@Param("projectId") String projectId);

    // ---------------------------------------------------------------- user_notifications

    String NOTIFICATION_COLUMNS =
            "id, user_id, type, title, detail, target_path, dedupe_key, read_at, created_at ";

    @Insert("""
            INSERT INTO user_notifications (id, user_id, type, title, detail, target_path, dedupe_key)
            VALUES (#{id}, #{userId}, #{type}, #{title}, #{detail}, #{targetPath}, #{dedupeKey})
            """)
    int insertNotification(UserNotificationEntity notification);

    /** dedupe_key 相同就忽略（同一个事件不重复打扰用户）。 */
    @Insert("""
            INSERT IGNORE INTO user_notifications (id, user_id, type, title, detail, target_path, dedupe_key)
            VALUES (#{id}, #{userId}, #{type}, #{title}, #{detail}, #{targetPath}, #{dedupeKey})
            """)
    int insertNotificationIgnoreDuplicate(UserNotificationEntity notification);

    /**
     * 通知是"算出来"而不是"写进去"的：每次读都从真实数据里推导，
     * 只有被标为已读的 id 存进 user_notifications。
     * 这样不会出现"数据库没记录就没有通知"的漏报，也不用写事务。
     */
    @Select("SELECT " + NOTIFICATION_COLUMNS + " FROM user_notifications "
          + "WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT 200")
    List<UserNotificationEntity> listByUser(@Param("userId") String userId);

    @Update("""
            UPDATE user_notifications
               SET read_at = NOW()
             WHERE id = #{id} AND user_id = #{userId} AND read_at IS NULL
            """)
    int markRead(@Param("id") String id, @Param("userId") String userId);

    @Update("""
            UPDATE user_notifications
               SET read_at = NOW()
             WHERE user_id = #{userId} AND read_at IS NULL
            """)
    int markAllRead(@Param("userId") String userId);

    // ---------------------------------------------------------------- referral_requests
    // DDL 见 docs/sql/009_career_and_referral.sql。
    // 组织者查询一律不取 user_id —— 这是这张表的核心约束，写在这里当约定。

    String REFERRAL_COLUMNS =
            "id, project_id, user_id, contact, preferred_time, status, created_at, handled_at ";

    @Insert("""
            INSERT INTO referral_requests (id, project_id, user_id, contact, preferred_time, status)
            VALUES (#{id}, #{projectId}, #{userId}, #{contact}, #{preferredTime}, #{status})
            """)
    int insertReferral(ReferralRequestEntity referral);

    @Select("SELECT " + REFERRAL_COLUMNS + " FROM referral_requests WHERE id = #{id} LIMIT 1")
    ReferralRequestEntity findReferral(@Param("id") String id);

    /** 组织者待办列表：只查自己项目的工单，按提交时间倒序。 */
    @Select("""
            <script>
            SELECT r.id, r.project_id, r.contact, r.preferred_time, r.status, r.created_at, r.handled_at,
                   p.title AS activity_title, p.public_slug
              FROM referral_requests r
              JOIN projects p ON p.id = r.project_id
             WHERE p.owner_id = #{ownerId}
             <if test='status != null and status != ""'> AND r.status = #{status} </if>
             ORDER BY r.created_at DESC
             LIMIT #{limit}
            </script>
            """)
    List<java.util.Map<String, Object>> listReferralsForOwner(@Param("ownerId") String ownerId,
                                                              @Param("status") String status,
                                                              @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM referral_requests r
              JOIN projects p ON p.id = r.project_id
             WHERE p.owner_id = #{ownerId}
             <if test='status != null and status != ""'> AND r.status = #{status} </if>
            </script>
            """)
    long countReferralsForOwner(@Param("ownerId") String ownerId, @Param("status") String status);

    /** 组织者推进状态。只能动自己项目下的工单（ownerId 一起判）。 */
    @Update("""
            UPDATE referral_requests r
              JOIN projects p ON p.id = r.project_id
               SET r.status = #{status},
                   r.handled_at = IF(#{status} = 'pending', NULL, NOW())
             WHERE r.id = #{id} AND p.owner_id = #{ownerId}
            """)
    int updateReferralStatus(@Param("id") String id, @Param("ownerId") String ownerId,
                             @Param("status") String status);

    // ---------------------------------------------------------------- 成长档案

    /**
     * 本人参与过的已发布活动。匿名参与也进列表，但 role 标成 anonymous，
     * 且绝不返回 answers —— 档案是「我参加过什么」，不是「我说过什么」。
     */
    @Select("""
            SELECT s.project_id                        AS projectId,
                   p.title                             AS title,
                   p.activity_type                     AS activityType,
                   t.activity_kind                     AS category,
                   p.public_slug                       AS slug,
                   s.privacy_mode_snapshot             AS privacyMode,
                   CASE WHEN s.privacy_mode_snapshot = 'anonymous' THEN 'anonymous' ELSE 'participant' END AS role,
                   COUNT(*)                            AS joinedCount,
                   MIN(s.submitted_at)                 AS firstJoinedAt,
                   MAX(s.submitted_at)                 AS lastJoinedAt,
                   MAX(s.consent_id IS NOT NULL)       AS consentRecorded
              FROM form_submissions s
              JOIN projects p ON p.id = s.project_id
              LEFT JOIN templates t ON t.id = p.template_id
             WHERE s.user_id = #{userId} AND p.status = 'published'
             GROUP BY s.project_id, p.title, p.activity_type, t.activity_kind,
                      p.public_slug, s.privacy_mode_snapshot
             ORDER BY MAX(s.submitted_at) DESC
             LIMIT 200
            """)
    List<java.util.Map<String, Object>> portfolioOfUser(@Param("userId") String userId);
}
