package com.campus.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.campus.server.entity.TemplateEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TemplateMapper {

    String COLUMNS = 
            "id, code, name, category, activity_kind, description, cover_url, document, "
          + "is_active, created_at, updated_at ";

    @Select("<script>SELECT " + COLUMNS + " FROM templates WHERE is_active = 1 "
            + "<if test='category != null and category != \"\"'> AND category = #{category} </if>"
            + "<if test='kind != null and kind != \"\"'> AND activity_kind = #{kind} </if>"
            + " ORDER BY activity_kind, category, name</script>")
    List<TemplateEntity> listActive(@Param("category") String category, @Param("kind") String kind);

    @Select("SELECT " + COLUMNS + " FROM templates WHERE id = #{id} LIMIT 1")
    TemplateEntity findById(@Param("id") String id);

    @Select("SELECT " + COLUMNS + " FROM templates WHERE code = #{code} LIMIT 1")
    TemplateEntity findByCode(@Param("code") String code);
}
