package com.campus.server.mapper;

import com.campus.server.entity.ToolRecordEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 表 tool_records 的读写。
 *
 * <p><b>每一个语句都带 user_id 条件</b>，包括删除。这是硬约束不是风格问题：
 * 这张表存的是参与者自己写下的心里话，越权读一条都是事故。
 * 服务层拿 userId 一律来自 {@code CurrentUser.require()}，永远不来自请求体。
 */
@Mapper
public interface ToolRecordMapper {

    String COLUMNS = "id, user_id, type, title, summary, tags, created_at_ms, created_at ";

    @Insert("""
            INSERT INTO tool_records (id, user_id, type, title, summary, tags, created_at_ms)
            VALUES (#{id}, #{userId}, #{type}, #{title}, #{summary},
                    CAST(#{tags} AS JSON), #{createdAtMs})
            """)
    int insert(ToolRecordEntity record);

    /** 按人取一段时间内的记录，按用户设备上的时间倒序。 */
    @Select("SELECT " + COLUMNS + " FROM tool_records "
            + "WHERE user_id = #{userId} AND created_at_ms >= #{sinceMs} "
            + "ORDER BY created_at_ms DESC LIMIT #{limit}")
    List<ToolRecordEntity> listByUserSince(@Param("userId") String userId,
                                           @Param("sinceMs") long sinceMs,
                                           @Param("limit") int limit);

    /** 删除只删自己的：多一个 user_id 条件，越权删除会返回 0 行而不是删掉别人的。 */
    @Delete("DELETE FROM tool_records WHERE id = #{id} AND user_id = #{userId}")
    int delete(@Param("id") String id, @Param("userId") String userId);

    @Select("SELECT COUNT(*) FROM tool_records WHERE user_id = #{userId} AND created_at_ms >= #{sinceMs}")
    long countByUserSince(@Param("userId") String userId, @Param("sinceMs") long sinceMs);
}
