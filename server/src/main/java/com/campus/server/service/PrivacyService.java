package com.campus.server.service;

import com.campus.server.common.BizException;
import com.campus.server.dto.Dtos;
import com.campus.server.entity.ProjectEntity;
import com.campus.server.mapper.ProjectMapper;
import com.campus.server.support.Ids;
import com.campus.server.support.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 活动隐私设置：读、写、快照、判定。
 *
 * <p>存储位置是 {@code projects.publish_settings.privacy}（整块 JSON），
 * 同时把档位冗余到 {@code projects.privacy_mode} 一列供列表筛选。
 * 这样发布时的其他参数（opensAt / closesAt / maxSubmissions）不会被顺手覆盖掉。
 *
 * <p>为什么不再放前端：{@code useActivityPrivacy.ts} 原来读的是 localStorage，
 * 换台电脑就回默认值，「保留 30 天」也只是一句没人执行的文案。
 */
@Service
public class PrivacyService {

    /** real_name 实名 / optional 可匿名 / anonymous 全匿名。 */
    public static final Set<String> MODES = Set.of("real_name", "optional", "anonymous");

    private final ProjectMapper projectMapper;

    public PrivacyService(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    // ================================================================ 读取

    /** 从项目行里取隐私设置；没设置过的老项目返回默认值（不是 null）。 */
    public Dtos.PrivacySettings read(ProjectEntity project) {
        JsonNode node = project == null || project.getPublishSettings() == null
                ? null
                : project.getPublishSettings().path("privacy");
        return parse(node, project == null ? null : project.getPrivacyMode());
    }

    public Dtos.PrivacyDto get(String projectId, ProjectService projectService) {
        ProjectEntity project = projectService.loadOwned(projectId);
        return new Dtos.PrivacyDto(projectId, read(project), Ids.iso(project.getUpdatedAt()));
    }

    // ================================================================ 写入

    public Dtos.PrivacyDto save(String projectId, Dtos.PrivacySettings input,
                                ProjectService projectService) {
        ProjectEntity project = projectService.loadOwned(projectId);
        Dtos.PrivacySettings normalized = normalize(input, read(project));

        String json = JsonUtil.toJson(normalized);
        int updated = projectMapper.updatePrivacy(projectId, json, normalized.getPrivacyMode());
        if (updated == 0) {
            throw BizException.notFound("项目不存在");
        }
        ProjectEntity fresh = projectMapper.findById(projectId);
        return new Dtos.PrivacyDto(projectId, read(fresh), Ids.iso(fresh == null ? null : fresh.getUpdatedAt()));
    }

    /** 供发布流程调用：把 privacy 一并写进 publish_settings。 */
    public JsonNode mergeIntoPublishSettings(JsonNode settings, Dtos.PrivacySettings privacy) {
        ObjectNode out = settings != null && settings.isObject()
                ? ((ObjectNode) settings).deepCopy()
                : JsonUtil.mapper().createObjectNode();
        out.set("privacy", JsonUtil.mapper().valueToTree(privacy));
        return out;
    }

    // ================================================================ 规范化

    /** 把任意输入（可能全是 null、可能是旧前端）洗成一份完整可用的设置。 */
    public Dtos.PrivacySettings normalize(Dtos.PrivacySettings input, Dtos.PrivacySettings fallback) {
        Dtos.PrivacySettings base = fallback == null ? Dtos.PrivacySettings.defaults() : fallback;
        Dtos.PrivacySettings out = new Dtos.PrivacySettings();

        String mode = input == null ? null : input.getPrivacyMode();
        out.setPrivacyMode(mode != null && MODES.contains(mode) ? mode : base.getPrivacyMode());

        out.setConsentRequired(pick(input == null ? null : input.getConsentRequired(),
                base.getConsentRequired(), Boolean.TRUE));

        String text = input == null ? null : input.getConsentText();
        if (text == null || text.isBlank()) {
            text = base.getConsentText() == null || base.getConsentText().isBlank()
                    ? Dtos.DEFAULT_CONSENT_TEXT : base.getConsentText();
        }
        out.setConsentText(clamp(text, 1000));

        Integer days = input == null ? null : input.getRetentionDays();
        if (days == null) {
            days = base.getRetentionDays();
        }
        // 0 或负数表示"不自动清理"；1..3650 之间是合理的保留天数
        if (days != null && (days < 0 || days > 3650)) {
            days = 30;
        }
        out.setRetentionDays(days);

        out.setAllowExport(pick(input == null ? null : input.getAllowExport(),
                base.getAllowExport(), Boolean.TRUE));
        out.setAllowLeave(pick(input == null ? null : input.getAllowLeave(),
                base.getAllowLeave(), Boolean.TRUE));
        out.setQuietSpace(pick(input == null ? null : input.getQuietSpace(),
                base.getQuietSpace(), Boolean.TRUE));
        return out;
    }

    /**
     * 这次提交到底算不算匿名。
     *
     * <ul>
     *   <li>anonymous 档：一律匿名，前端传 false 也没用；</li>
     *   <li>optional 档：看提交者自己选；</li>
     *   <li>real_name 档：一律实名。</li>
     * </ul>
     */
    public boolean resolveAnonymous(Dtos.PrivacySettings privacy, Boolean requested) {
        if (privacy == null) {
            return false;
        }
        return switch (privacy.getPrivacyMode() == null ? "optional" : privacy.getPrivacyMode()) {
            case "anonymous" -> true;
            case "real_name" -> false;
            default -> Boolean.TRUE.equals(requested);
        };
    }

    // ================================================================ 内部

    private Dtos.PrivacySettings parse(JsonNode node, String overflowMode) {
        Dtos.PrivacySettings out = Dtos.PrivacySettings.defaults();
        if (node != null && node.isObject()) {
            if (node.hasNonNull("privacyMode")) {
                String mode = node.get("privacyMode").asText();
                if (MODES.contains(mode)) {
                    out.setPrivacyMode(mode);
                }
            }
            if (node.hasNonNull("consentRequired")) {
                out.setConsentRequired(node.get("consentRequired").asBoolean(true));
            }
            if (node.hasNonNull("consentText")) {
                String text = node.get("consentText").asText();
                if (!text.isBlank()) {
                    out.setConsentText(clamp(text, 1000));
                }
            }
            if (node.hasNonNull("retentionDays")) {
                out.setRetentionDays(node.get("retentionDays").asInt(30));
            }
            if (node.hasNonNull("allowExport")) {
                out.setAllowExport(node.get("allowExport").asBoolean(true));
            }
            if (node.hasNonNull("allowLeave")) {
                out.setAllowLeave(node.get("allowLeave").asBoolean(true));
            }
            if (node.hasNonNull("quietSpace")) {
                out.setQuietSpace(node.get("quietSpace").asBoolean(true));
            }
        } else if (overflowMode != null && MODES.contains(overflowMode)) {
            // 只有冗余列、没有完整 JSON 的老数据（理论上不会出现，兜底）
            out.setPrivacyMode(overflowMode);
        }
        return out;
    }

    private static Boolean pick(Boolean value, Boolean fallback, Boolean def) {
        if (value != null) {
            return value;
        }
        return fallback != null ? fallback : def;
    }

    private static String clamp(String raw, int max) {
        if (raw == null) {
            return null;
        }
        return raw.length() > max ? raw.substring(0, max) : raw;
    }
}
