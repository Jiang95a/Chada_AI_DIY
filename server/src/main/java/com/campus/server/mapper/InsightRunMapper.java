package com.campus.server.mapper;

import com.campus.server.entity.InsightRunEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 表 insight_runs 的读写。
 *
 * <p>两个用途共用一张表：
 * <ol>
 *   <li><b>缓存</b>：{@link #findFresh} 按「同一用户 + 同一 facts 哈希 + 24 小时内」取上次结果；</li>
 *   <li><b>复现</b>：产品文档要求留下 insightId / model / promptVersion / 输入哈希。</li>
 * </ol>
 *
 * <p>只缓存 {@code status='ok'} 的那一条：数据不足和危机是**当下**的判断，
 * 缓存住会让用户补了记录之后还看到旧的「数据不足」。
 */
@Mapper
public interface InsightRunMapper {

    String COLUMNS = "id, user_id, facts_hash, days, model, prompt_version, status, "
            + "response_json, created_at ";

    @Insert("""
            INSERT INTO insight_runs
              (id, user_id, facts_hash, days, model, prompt_version, status, response_json)
            VALUES
              (#{id}, #{userId}, #{factsHash}, #{days}, #{model}, #{promptVersion}, #{status},
               CAST(#{responseJson} AS JSON))
            """)
    int insert(InsightRunEntity run);

    /** 缓存命中：同一用户、同一 facts 哈希、24 小时内、且上次是成功的。 */
    @Select("SELECT " + COLUMNS + " FROM insight_runs "
            + "WHERE user_id = #{userId} AND facts_hash = #{factsHash} AND status = 'ok' "
            + "AND created_at >= #{since} "
            + "ORDER BY created_at DESC LIMIT 1")
    InsightRunEntity findFresh(@Param("userId") String userId,
                               @Param("factsHash") String factsHash,
                               @Param("since") java.time.LocalDateTime since);

    @Select("SELECT " + COLUMNS + " FROM insight_runs WHERE id = #{id} AND user_id = #{userId} LIMIT 1")
    InsightRunEntity findById(@Param("id") String id, @Param("userId") String userId);
}
