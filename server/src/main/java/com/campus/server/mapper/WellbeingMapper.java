package com.campus.server.mapper;

import com.campus.server.entity.ActivityConsentEntity;
import com.campus.server.entity.ActivityFeedbackEntity;
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
}
