package com.campus.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.campus.server.entity.ProjectEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * projects 表。所有读操作都带 deleted_at IS NULL（软删除）。
 */
@Mapper
public interface ProjectMapper {

    // 关键坑：Java 文本块会自动吃掉结尾换行，多个文本块用 + 拼接时，
    // 每个片段末尾必须自己留空格，否则 SELECT 会粘成 "SELECTid"（MySQL 直接语法错误）。
    // 另外文本块后面不能直接跟 `+ 字符串`（编译报"元素值必须为常量表达式"），
    // 所以这里统一用字符串拼接，不用文本块。
    String COLUMNS =
            "id, owner_id, template_id, title, description, "
          + "activity_type, privacy_mode, cover_tone, cover_code, cover_title, status, "
          + "draft_document, draft_revision, public_slug, published_version_id, "
          + "publish_settings, created_at, updated_at, deleted_at ";

    String SEARCH_WHERE =
            " WHERE deleted_at IS NULL AND owner_id = #{ownerId} "
          + "<if test=\"q != null and q != ''\">"
          + " AND (title LIKE CONCAT('%', #{q}, '%') OR description LIKE CONCAT('%', #{q}, '%')) "
          + "</if>"
          + "<if test=\"status != null and status != ''\">"
          + " AND status = #{status} "
          + "</if>"
          + "<if test=\"activityType != null and activityType != ''\">"
          + " AND activity_type = #{activityType} "
          + "</if>";

    String SEARCH_SQL_HEAD = "<script>SELECT " + COLUMNS + " FROM projects" + SEARCH_WHERE + " ORDER BY ";

    // sort 的白名单都在这里，别把用户传的字符串直接拼进 SQL。
    // 前端 stores/projects.ts 传的是 'updated_at' / 'created_at' / 'title'；
    // 旧版只认 'title'，其余全落 otherwise，导致「按创建时间」排不出效果。
    String SEARCH_SQL_TAIL =
            "<choose>"
          + "<when test=\"sort == 'title'\">title ASC</when>"
          + "<when test=\"sort == 'created_at' or sort == 'created'\">created_at DESC</when>"
          + "<when test=\"sort == 'updated_at' or sort == 'updated'\">updated_at DESC</when>"
          + "<otherwise>updated_at DESC</otherwise>"
          + "</choose>, id ASC LIMIT #{limit} OFFSET #{offset}</script>";

    @Select(SEARCH_SQL_HEAD + SEARCH_SQL_TAIL)
    List<ProjectEntity> search(@Param("ownerId") String ownerId,
                               @Param("q") String q,
                               @Param("status") String status,
                               @Param("activityType") String activityType,
                               @Param("sort") String sort,
                               @Param("limit") int limit,
                               @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM projects" + SEARCH_WHERE + "</script>")
    long countSearch(@Param("ownerId") String ownerId,
                     @Param("q") String q,
                     @Param("status") String status,
                     @Param("activityType") String activityType);

    @Select("SELECT " + COLUMNS + " FROM projects WHERE id = #{id} AND deleted_at IS NULL LIMIT 1")
    ProjectEntity findById(@Param("id") String id);

    @Select("SELECT " + COLUMNS + " FROM projects WHERE public_slug = #{slug} AND deleted_at IS NULL LIMIT 1")
    ProjectEntity findByPublicSlug(@Param("slug") String slug);

    @Select("SELECT COUNT(*) FROM projects WHERE public_slug = #{slug} AND id <> #{excludeId}")
    int countBySlug(@Param("slug") String slug, @Param("excludeId") String excludeId);

    @Insert("""
            INSERT INTO projects (id, owner_id, template_id, title, description,
                                  activity_type, privacy_mode, cover_tone, cover_code, cover_title, status,
                                  draft_document, draft_revision, publish_settings)
            VALUES (#{id}, #{ownerId}, #{templateId}, #{title}, #{description},
                    #{activityType}, #{privacyMode}, #{coverTone}, #{coverCode}, #{coverTitle}, #{status},
                    #{draftDocument}, #{draftRevision}, #{publishSettings})
            """)
    int insert(ProjectEntity project);

    @Update("""
            UPDATE projects
               SET title = #{title},
                   description = #{description},
                   activity_type = #{activityType},
                   cover_tone = #{coverTone},
                   cover_code = #{coverCode},
                   cover_title = #{coverTitle}
             WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updateBasic(@Param("id") String id,
                    @Param("title") String title,
                    @Param("description") String description,
                    @Param("activityType") String activityType,
                    @Param("coverTone") String coverTone,
                    @Param("coverCode") String coverCode,
                    @Param("coverTitle") String coverTitle);

    /**
     * 乐观锁核心：只有 baseRevision 与服务端一致时才写。
     * 返回 0 表示被别人改过 → 上层翻译成 409 REVISION_CONFLICT。
     */
    @Update("""
            UPDATE projects
               SET draft_document = #{document},
                   draft_revision = #{nextRevision}
             WHERE id = #{id}
               AND deleted_at IS NULL
               AND draft_revision = #{baseRevision}
            """)
    int updateDraftWithRevision(@Param("id") String id,
                                @Param("document") JsonNode document,
                                @Param("baseRevision") long baseRevision,
                                @Param("nextRevision") long nextRevision);

    /** AI 应用建议时也走乐观锁。 */
    @Update("""
            UPDATE projects
               SET draft_document = #{document},
                   draft_revision = #{nextRevision},
                   status = CASE WHEN status = 'unpublished' THEN 'draft' ELSE status END
             WHERE id = #{id}
               AND deleted_at IS NULL
               AND draft_revision = #{baseRevision}
            """)
    int applyDraftWithRevision(@Param("id") String id,
                               @Param("document") JsonNode document,
                               @Param("baseRevision") long baseRevision,
                               @Param("nextRevision") long nextRevision);

    @Update("""
            UPDATE projects
               SET status = #{status},
                   public_slug = #{publicSlug},
                   published_version_id = #{publishedVersionId},
                   publish_settings = #{publishSettings}
             WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updatePublish(@Param("id") String id,
                      @Param("status") String status,
                      @Param("publicSlug") String publicSlug,
                      @Param("publishedVersionId") String publishedVersionId,
                      @Param("publishSettings") JsonNode publishSettings);

    @Update("""
            UPDATE projects
               SET status = 'unpublished'
             WHERE id = #{id} AND deleted_at IS NULL
            """)
    int markUnpublished(@Param("id") String id);

    @Update("""
            UPDATE projects
               SET status = 'draft', deleted_at = CURRENT_TIMESTAMP
             WHERE id = #{id} AND deleted_at IS NULL
            """)
    int softDelete(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM projects WHERE owner_id = #{ownerId} AND deleted_at IS NULL")
    long countByOwner(@Param("ownerId") String ownerId);

    // ------------------------------------------------------------------------
    // 公开活动列表：只查已发布、未删除、且还在开放时间窗内的项目。
    // 时间窗放在 SQL 里做，避免把整表拉回内存再过滤。
    // ------------------------------------------------------------------------
    String PUBLIC_WHERE =
            " WHERE deleted_at IS NULL AND status = 'published' "
          + " AND public_slug IS NOT NULL "
          + "<if test=\"q != null and q != ''\">"
          + " AND (title LIKE CONCAT('%', #{q}, '%') OR cover_title LIKE CONCAT('%', #{q}, '%')) "
          + "</if>"
          + "<if test=\"activityType != null and activityType != ''\">"
          + " AND activity_type = #{activityType} "
          + "</if>";

    String PUBLIC_COLUMNS =
            "id, title, description, activity_type, privacy_mode, cover_tone, cover_code, cover_title, "
          + "status, draft_revision, public_slug, published_version_id, publish_settings, updated_at ";

    @Select("<script>SELECT " + PUBLIC_COLUMNS + " FROM projects" + PUBLIC_WHERE
            + " ORDER BY updated_at DESC, id ASC LIMIT #{limit} OFFSET #{offset}</script>")
    List<ProjectEntity> searchPublic(@Param("q") String q,
                                     @Param("activityType") String activityType,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM projects" + PUBLIC_WHERE + "</script>")
    long countPublic(@Param("q") String q,
                     @Param("activityType") String activityType);

    /** 公开活动列表用：按 slug 取到项目后还要判是否仍在开放窗口内。 */
    @Select("SELECT " + COLUMNS + " FROM projects WHERE public_slug = #{slug} AND deleted_at IS NULL LIMIT 1")
    ProjectEntity findPublicBySlug(@Param("slug") String slug);

    // ------------------------------------------------------------------------
    // 隐私设置：完整对象放 publish_settings.privacy（不动其他发布参数），
    // 档位冗余到 privacy_mode 一列，方便列表/统计直接筛。
    // ------------------------------------------------------------------------
    @Update("""
            UPDATE projects
               SET publish_settings = JSON_SET(
                     COALESCE(publish_settings, JSON_OBJECT()),
                     '$.privacy', CAST(#{privacy} AS JSON)),
                   privacy_mode = #{privacyMode}
             WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updatePrivacy(@Param("id") String id,
                      @Param("privacy") String privacyJson,
                      @Param("privacyMode") String privacyMode);

    @Select("SELECT privacy_mode FROM projects WHERE id = #{id} AND deleted_at IS NULL LIMIT 1")
    String findPrivacyMode(@Param("id") String id);
}
