package com.campus.server.service;

import com.campus.server.dto.Dtos;
import com.campus.server.entity.UserEntity;
import com.campus.server.mapper.UserMapper;
import com.campus.server.mapper.WellbeingMapper;
import com.campus.server.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人成长档案：「我参加过哪些活动」。
 *
 * <p>这是把心理活动和生涯活动<b>串成一条线</b>的地方。心理社团的招新、心晴活动，
 * 和生涯规划的简历门诊、模拟面试，在数据上是同一种东西——一次参与。
 * 把参与轨迹聚合给本人看，平台才有了「长期价值」，而不只是一次次孤立的报名表。
 *
 * <p>三条不可越界的约束：
 * <ul>
 *   <li>只返回<b>本人</b>的数据，任何接口都拿不到别人的档案；</li>
 *   <li>只返回「参加过什么」，<b>绝不返回 answers</b>——档案不是答题记录本；</li>
 *   <li>匿名参与照常出现在列表里，但 role 标成 anonymous，并明确告诉本人这一条没有留身份痕迹。</li>
 * </ul>
 */
@Service
public class PortfolioService {

    private final WellbeingMapper wellbeingMapper;
    private final UserMapper userMapper;

    public PortfolioService(WellbeingMapper wellbeingMapper, UserMapper userMapper) {
        this.wellbeingMapper = wellbeingMapper;
        this.userMapper = userMapper;
    }

    public Dtos.PortfolioDto ofCurrentUser() {
        CurrentUser current = CurrentUser.require();
        List<Map<String, Object>> rows = wellbeingMapper.portfolioOfUser(current.userId());

        List<Dtos.PortfolioItemDto> items = new ArrayList<>();
        long wellbeingCount = 0;
        long careerCount = 0;
        // 按月份聚合：{ "2026-10": 3, "2026-11": 1 }
        Map<String, Long> monthMap = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String kind = toStr(row.get("category"));
            if ("wellbeing".equals(kind)) {
                wellbeingCount++;
            } else if ("career".equals(kind)) {
                careerCount++;
            }

            LocalDateTime last = toDateTime(row.get("lastJoinedAt"));
            if (last != null) {
                String month = YearMonth.from(last).toString();
                monthMap.merge(month, 1L, Long::sum);
            }

            String slug = toStr(row.get("slug"));
            items.add(new Dtos.PortfolioItemDto(
                    toStr(row.get("projectId")),
                    toStr(row.get("title")),
                    toStr(row.get("activityType")),
                    kind == null ? "general" : kind,
                    slug,
                    // publicUrl 交给前端按自己的路由拼（前端是 /p/{slug}），后端只给 slug
                    null,
                    toStr(row.get("role")),
                    "anonymous".equals(toStr(row.get("role"))) ? "以匿名身份参与" : "以实名身份参与",
                    isoOrNull(toDateTime(row.get("firstJoinedAt"))),
                    isoOrNull(last),
                    toLong(row.get("joinedCount")),
                    toStr(row.get("privacyMode")),
                    toBool(row.get("consentRecorded"))));
        }

        List<Map<String, Object>> byMonth = new ArrayList<>();
        monthMap.forEach((month, count) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", month);
            m.put("count", count);
            byMonth.add(m);
        });

        // 前端 Portfolio 类型里 displayName 是非空 string，这里必须给值，
        // 否则前端拿到 null 会在渲染时炸。取不到就退化成「同学」。
        UserEntity user = userMapper.findById(current.userId());
        String displayName = user == null || blank(user.getDisplayName())
                ? "同学" : user.getDisplayName();

        return new Dtos.PortfolioDto(current.userId(), displayName, items.size(),
                wellbeingCount, careerCount, byMonth, items);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static boolean toBool(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static LocalDateTime toDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }

    private static String isoOrNull(LocalDateTime time) {
        return time == null ? null : time.toString();
    }
}
