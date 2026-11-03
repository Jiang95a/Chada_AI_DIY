package com.campus.server.service;

import com.campus.server.common.BizException;
import com.campus.server.common.ErrorCode;
import com.campus.server.common.PageDocumentValidator;
import com.campus.server.dto.Dtos;
import com.campus.server.entity.MiscEntities.ActivityEventEntity;
import com.campus.server.entity.PageVersionEntity;
import com.campus.server.entity.ProjectEntity;
import com.campus.server.entity.TemplateEntity;
import com.campus.server.mapper.PageVersionMapper;
import com.campus.server.mapper.ProjectMapper;
import com.campus.server.mapper.SubmissionMapper;
import com.campus.server.mapper.TemplateMapper;
import com.campus.server.security.CurrentUser;
import com.campus.server.support.Ids;
import com.campus.server.support.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 项目 CRUD、草稿保存（乐观锁）、版本、发布、公开读取。
 *
 * <p>所有方法都会校验「这个项目是不是当前用户的」，越权统一返回 403 而不是 404 ——
 * 便于前端排查，但会暴露项目是否存在。V1 接受这个取舍（PRD 也要求越权检查可见）。
 */
@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final PageVersionMapper versionMapper;
    private final TemplateMapper templateMapper;
    private final SubmissionMapper submissionMapper;
    private final PageDocumentValidator documentValidator;
    private final DocumentNormalizer normalizer;
    private final com.campus.server.config.AppProperties props;
    private final PrivacyService privacyService;

    public ProjectService(ProjectMapper projectMapper, PageVersionMapper versionMapper,
                          TemplateMapper templateMapper, SubmissionMapper submissionMapper,
                          PageDocumentValidator documentValidator, DocumentNormalizer normalizer,
                          com.campus.server.config.AppProperties props,
                          PrivacyService privacyService) {
        this.projectMapper = projectMapper;
        this.versionMapper = versionMapper;
        this.templateMapper = templateMapper;
        this.submissionMapper = submissionMapper;
        this.documentValidator = documentValidator;
        this.normalizer = normalizer;
        this.props = props;
        this.privacyService = privacyService;
    }

    // ================================================================ 查询

    public Dtos.PagedResultHolder<Dtos.ProjectDto> list(String q, String status, String sort,
                                                        int page, int pageSize) {
        return list(q, status, null, sort, page, pageSize);
    }

    public Dtos.PagedResultHolder<Dtos.ProjectDto> list(String q, String status, String activityType,
                                                        String sort, int page, int pageSize) {
        String ownerId = CurrentUser.require().userId();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        String type = blankToNull(activityType);
        long total = projectMapper.countSearch(ownerId, q, status, type);
        List<ProjectEntity> rows = total == 0
                ? List.of()
                : projectMapper.search(ownerId, q, status, type, sort, safeSize, (safePage - 1) * safeSize);
        List<Dtos.ProjectDto> data = rows.stream().map(this::toDto).toList();
        return new Dtos.PagedResultHolder<>(data, new Dtos.PageMeta(safePage, safeSize, total));
    }

    public Dtos.ProjectDto get(String projectId) {
        return toDto(loadOwned(projectId));
    }

    // ================================================================ 创建

    @Transactional
    public Dtos.ProjectDto create(Dtos.CreateProjectRequest request) {
        String ownerId = CurrentUser.require().userId();
        JsonNode document = request.initialDocument();

        if (document != null && !document.isNull()) {
            documentValidator.validateOrThrow(document);
        } else if (request.templateId() != null && !request.templateId().isBlank()) {
            TemplateEntity template = templateMapper.findById(request.templateId());
            if (template == null) {
                throw BizException.notFound("模板不存在");
            }
            document = buildDocumentFromTemplate(template, request.title());
        } else {
            throw BizException.invalid("缺少 initialDocument，也没给 templateId");
        }

        ProjectEntity project = new ProjectEntity();
        project.setId(Ids.uuid());
        project.setOwnerId(ownerId);
        project.setTemplateId(request.templateId());
        project.setTitle(request.title().trim());
        project.setDescription(request.description());
        project.setStatus("draft");

        // 封面四项：显式传了就用，没传就从模板 / 标题推导，
        // 避免项目列表里的卡片一片空白。
        TemplateEntity coverSource = null;
        if (request.templateId() != null && !request.templateId().isBlank()) {
            coverSource = templateMapper.findById(request.templateId());
        }
        // activityType 优先用模板 category（如 recruitment → 社团招新），
        // 因为前端 templates.ts 的 CATEGORY_META 就是按 category 映射的场景名；
        // 用模板名会得到「社团招新季」这种和列表筛选对不上的值。
        project.setActivityType(firstNonBlank(request.activityType(),
                sceneOfCategory(coverSource == null ? null : coverSource.getCategory()),
                coverSource == null ? null : coverSource.getName(),
                "校园活动"));
        project.setCoverTone(firstNonBlank(request.coverTone(),
                toneOfCategory(coverSource == null ? null : coverSource.getCategory())));
        project.setCoverCode(firstNonBlank(request.coverCode(),
                codeOfCategory(coverSource == null ? null : coverSource.getCategory())));
        project.setCoverTitle(firstNonBlank(request.coverTitle(), project.getTitle()));

        project.setDraftDocument(document);
        project.setDraftRevision(0L);
        project.setPublishSettings(JsonUtil.mapper().createObjectNode());
        // privacy_mode 这一列是 NOT NULL：新项目一律先给默认档位（optional），
        // 组织者之后再在隐私面板里改。
        project.setPrivacyMode(Dtos.PrivacySettings.defaults().getPrivacyMode());
        projectMapper.insert(project);
        return toDto(projectMapper.findById(project.getId()));
    }

    @Transactional
    public Dtos.ProjectDto duplicate(String projectId, Dtos.DuplicateProjectRequest request) {
        ProjectEntity source = loadOwned(projectId);
        ProjectEntity copy = new ProjectEntity();
        copy.setId(Ids.uuid());
        copy.setOwnerId(source.getOwnerId());
        copy.setTemplateId(source.getTemplateId());
        copy.setTitle(request.title().trim());
        copy.setDescription(source.getDescription());
        copy.setActivityType(source.getActivityType());
        copy.setCoverTone(source.getCoverTone());
        copy.setCoverCode(source.getCoverCode());
        // 副本换了标题，封面大标题也跟着走；没给新标题时沿用原标题
        copy.setCoverTitle(firstNonBlank(source.getCoverTitle(), request.title().trim()));
        copy.setStatus("draft");
        copy.setDraftDocument(source.getDraftDocument());
        copy.setDraftRevision(0L);
        copy.setPublishSettings(JsonUtil.mapper().createObjectNode());
        copy.setPrivacyMode(source.getPrivacyMode());

        // 副本要换 slug，否则公开页会打架
        ObjectNode doc = source.getDraftDocument() == null
                ? JsonUtil.mapper().createObjectNode()
                : source.getDraftDocument().deepCopy();
        doc.remove("published");
        doc.put("slug", uniqueSlug(Ids.slugify(request.title()), null));
        copy.setDraftDocument(doc);

        projectMapper.insert(copy);
        return toDto(projectMapper.findById(copy.getId()));
    }

    // ================================================================ 修改 / 删除

    public Dtos.ProjectDto update(String projectId, Dtos.UpdateProjectRequest request) {
        ProjectEntity project = loadOwned(projectId);
        String title = request.title() == null || request.title().isBlank()
                ? project.getTitle()
                : request.title().trim();
        if (title.length() > 160) {
            throw BizException.invalid("标题最多 160 字");
        }
        String description = request.description() == null
                ? project.getDescription()
                : request.description();
        // 封面字段：传 null 表示「保持不变」，传空串表示「清空回默认」
        String coverTitle = request.coverTitle() == null
                ? project.getCoverTitle()
                : request.coverTitle();
        if (coverTitle != null && coverTitle.length() > 160) {
            throw BizException.invalid("封面标题最多 160 字");
        }
        projectMapper.updateBasic(projectId, title, description,
                nullableOr(project.getActivityType(), request.activityType()),
                nullableOr(project.getCoverTone(), request.coverTone()),
                nullableOr(project.getCoverCode(), request.coverCode()),
                coverTitle);
        return toDto(projectMapper.findById(projectId));
    }

    public void delete(String projectId) {
        loadOwned(projectId);
        projectMapper.softDelete(projectId);
    }

    // ================================================================ 草稿

    public Dtos.DraftDto getDraft(String projectId) {
        ProjectEntity project = loadOwned(projectId);
        return new Dtos.DraftDto(project.getDraftRevision(), project.getDraftDocument(),
                Ids.iso(project.getUpdatedAt()));
    }

    /**
     * 乐观锁保存：baseRevision 与服务端不一致 → 409。
     * 前端拿到 409 后应提示「别人改过，先刷新」，而不是覆盖。
     */
    @Transactional
    public Dtos.SaveDraftResult saveDraft(String projectId, Dtos.SaveDraftRequest request) {
        ProjectEntity project = loadOwned(projectId);
        if (request.document() == null || request.document().isNull()) {
            throw new BizException(ErrorCode.PAGE_DOCUMENT_INVALID, "页面文档不能为空");
        }
        documentValidator.validateOrThrow(request.document());

        long base = request.baseRevision() == null ? project.getDraftRevision() : request.baseRevision();
        if (request.baseRevision() != null && request.baseRevision() < 0) {
            throw BizException.invalid("baseRevision 不能为负");
        }
        long next = base + 1;
        int updated = projectMapper.updateDraftWithRevision(projectId, request.document(), base, next);
        if (updated == 0) {
            throw new BizException(ErrorCode.REVISION_CONFLICT,
                    "文档已被修改，当前服务端版本是 " + project.getDraftRevision() + "，请刷新后再保存");
        }
        return new Dtos.SaveDraftResult(next, Ids.nowIso());
    }

    /**
     * 局部更新草稿（RFC 7386 JSON Merge Patch 语义）。
     *
     * <p>为什么单独开一个 PATCH：编辑器每敲一个字都会触发一次保存，
     * 整体 PUT 每条都要把整个文档（几十 KB）传上来，既费流量又容易互相覆盖。
     * PATCH 只传「变了的那一小块」，例如：
     * <pre>
     * { "baseRevision": 7,
     *   "patch": { "pages": [ { "id": "pg-1", "blocks": [ { "id": "blk-2", "props": { "text": "新文案" } } ] } ] } }
     * </pre>
     * 合并规则：对象逐键递归合并（patch 里值为 null 表示删掉这个键），
     * 数组整体替换（数组里不做按 id 的智能合并 —— 太容易猜错，交给前端传完整数组）。
     * 合并完仍然要过一遍 schema 校验，所以不会因为 patch 把文档写坏。
     */
    /**
     * 合并规则（对 RFC 7386 的务实扩展）：
     * <ul>
     *   <li>对象：逐键递归合并，patch 里显式 null 表示删掉这个键；</li>
     *   <li>两侧都是「带 id 的对象数组」：<b>按 id 合并</b>——patch 里出现的 id 逐字段合并，
     *       没出现的元素原样保留，patch 里带 {@code "_deleted": true} 的元素被移除；</li>
     *   <li>其余数组：整体替换。</li>
     * </ul>
     * 之所以不照搬「数组整体替换」，是因为前端改一个页面名就得把全部 pages（含所有 blocks）
     * 完整回传，传输量随页面规模线性增长，而且漏传一个字段就会把内容删掉。
     * <p>
     * 合并完仍然要过一遍 schema 校验，所以不会因为 patch 把文档写坏。
     */
    @Transactional
    public Dtos.SaveDraftResult patchDraft(String projectId, Dtos.PatchDraftRequest request) {
        ProjectEntity project = loadOwned(projectId);
        if (request.patch() == null || request.patch().isNull() || !request.patch().isObject()) {
            throw new BizException(ErrorCode.PAGE_DOCUMENT_INVALID, "patch 必须是一个 JSON 对象");
        }
        if (request.baseRevision() != null && request.baseRevision() < 0) {
            throw BizException.invalid("baseRevision 不能为负");
        }
        long base = request.baseRevision() == null ? project.getDraftRevision() : request.baseRevision();
        if (request.baseRevision() != null && request.baseRevision() != project.getDraftRevision()) {
            throw new BizException(ErrorCode.REVISION_CONFLICT,
                    "文档已被修改，当前服务端版本是 " + project.getDraftRevision() + "，请刷新后再保存");
        }

        JsonNode current = project.getDraftDocument();
        if (current == null || current.isNull()) {
            current = JsonUtil.mapper().createObjectNode();
        }
        JsonNode merged = mergePatch(current.deepCopy(), request.patch());

        documentValidator.validateOrThrow(merged);

        long next = base + 1;
        int updated = projectMapper.updateDraftWithRevision(projectId, merged, base, next);
        if (updated == 0) {
            throw new BizException(ErrorCode.REVISION_CONFLICT,
                    "文档已被修改，请刷新后再保存");
        }
        return new Dtos.SaveDraftResult(next, Ids.nowIso());
    }

    /** 对象递归合并、id 数组按 id 合并、其余整体替换；patch 里显式 null 表示删除该键。 */
    private static JsonNode mergePatch(JsonNode target, JsonNode patch) {
        if (!patch.isObject()) {
            if (patch.isArray() && target != null && target.isArray()) {
                return mergeByIdArray((ArrayNode) target, (ArrayNode) patch);
            }
            return patch.deepCopy();
        }
        ObjectNode result = (target != null && target.isObject())
                ? (ObjectNode) target.deepCopy()
                : JsonUtil.mapper().createObjectNode();
        patch.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isNull()) {
                result.remove(key);
            } else {
                result.set(key, mergePatch(result.get(key), value));
            }
        });
        return result;
    }

    /**
     * 按 id 合并两个「对象数组」。只有一方不是「全是带 id 的对象」时退化成整体替换，
     * 免得把 {@code pages} 和 {@code ["a","b"]} 这种形状不兼容的数组瞎合。
     */
    private static JsonNode mergeByIdArray(ArrayNode target, ArrayNode patch) {
        if (!allHaveId(target) || !allHaveId(patch)) {
            return patch.deepCopy();
        }
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        for (JsonNode item : target) {
            byId.put(item.get("id").asText(), item.deepCopy());
        }
        List<String> removed = new ArrayList<>();
        for (JsonNode item : patch) {
            String id = item.get("id").asText();
            JsonNode deleted = item.get("_deleted");
            if (deleted != null && deleted.asBoolean(false)) {
                removed.add(id);
                continue;
            }
            JsonNode existing = byId.get(id);
            byId.put(id, existing == null ? item.deepCopy() : mergePatch(existing, item));
        }
        removed.forEach(byId::remove);
        // 保留原数组顺序：没被删的按原顺序，新出现的按 patch 里的顺序追加到末尾
        ArrayNode out = JsonUtil.mapper().createArrayNode();
        for (JsonNode item : target) {
            JsonNode merged = byId.get(item.get("id").asText());
            if (merged != null) {
                out.add(merged);
            }
        }
        for (JsonNode item : patch) {
            String id = item.get("id").asText();
            if (indexOfId(target, id) < 0 && byId.containsKey(id)) {
                out.add(byId.get(id));
            }
        }
        return out;
    }

    private static boolean allHaveId(ArrayNode array) {
        for (JsonNode item : array) {
            if (!item.isObject() || !item.hasNonNull("id") || item.get("id").asText().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static int indexOfId(ArrayNode array, String id) {
        for (int i = 0; i < array.size(); i++) {
            JsonNode item = array.get(i);
            if (item.isObject() && item.hasNonNull("id") && id.equals(item.get("id").asText())) {
                return i;
            }
        }
        return -1;
    }

    // ================================================================ 版本

    public List<Dtos.VersionDto> listVersions(String projectId) {
        loadOwned(projectId);
        return versionMapper.listByProject(projectId).stream()
                .map(v -> new Dtos.VersionDto(v.getId(), v.getVersionNo(), v.getSource(),
                        v.getDocument(), Ids.iso(v.getCreatedAt())))
                .toList();
    }

    @Transactional
    public Dtos.VersionDto createVersion(String projectId, Dtos.CreateVersionRequest request) {
        ProjectEntity project = loadOwned(projectId);
        if (request.baseRevision() == null || request.baseRevision() != project.getDraftRevision()) {
            throw new BizException(ErrorCode.REVISION_CONFLICT,
                    "baseRevision 与服务端不一致（服务端 " + project.getDraftRevision() + "）");
        }
        String source = request.source() == null ? "manual" : request.source();
        if (!List.of("manual", "ai", "template").contains(source)) {
            throw BizException.invalid("source 只能是 manual / ai / template");
        }
        PageVersionEntity version = insertVersion(project, source);
        return new Dtos.VersionDto(version.getId(), version.getVersionNo(), version.getSource(),
                version.getDocument(), Ids.iso(version.getCreatedAt()));
    }

    // ================================================================ 发布 / 下线

    @Transactional
    public Dtos.PublishResult publish(String projectId, Dtos.PublishRequest request) {
        ProjectEntity project = loadOwned(projectId);
        JsonNode document;
        PageVersionEntity version;

        if (request != null && request.versionId() != null && !request.versionId().isBlank()) {
            version = versionMapper.findById(request.versionId());
            if (version == null || !version.getProjectId().equals(projectId)) {
                throw BizException.notFound("版本不存在");
            }
            document = version.getDocument();
        } else {
            documentValidator.validateOrThrow(project.getDraftDocument());
            normalizer.assertPublishable(project.getDraftDocument());
            version = insertVersion(project, "manual");
            document = version.getDocument();
        }

        String slug = resolveSlug(project, request == null ? null : request.slug());
        String publicUrl = props.getPublicBaseUrl().replaceAll("/+$", "") + "/" + slug;
        JsonNode publishSettings = (request != null && request.publishSettings() != null)
                ? request.publishSettings()
                : (project.getPublishSettings() == null
                   ? JsonUtil.mapper().createObjectNode() : project.getPublishSettings());
        // 发布时明确带上当前隐私设置：前端换一台电脑发布，公开页也读到同一份规则
        publishSettings = privacyService.mergeIntoPublishSettings(publishSettings, privacyService.read(project));

        projectMapper.updatePublish(projectId, "published", slug, version.getId(), publishSettings);

        String publishedAt = Ids.iso(version.getCreatedAt());
        return new Dtos.PublishResult(slug, publicUrl, version.getId(),
                publishedAt == null ? Ids.nowIso() : publishedAt);
    }

    @Transactional
    public void unpublish(String projectId) {
        loadOwned(projectId);
        projectMapper.markUnpublished(projectId);
    }

    // ================================================================ 公开读取（不需要登录）

    public Dtos.PublicActivityDto getPublic(String slug, boolean countView) {
        ProjectEntity project = projectMapper.findByPublicSlug(slug);
        if (project == null || !"published".equals(project.getStatus())) {
            throw BizException.notFound("活动不存在或未发布");
        }
        JsonNode document = publishedDocument(project);

        if (countView) {
            ActivityEventEntity event = new ActivityEventEntity();
            event.setProjectId(project.getId());
            event.setEventType("public_page_view");
            event.setPageId(firstPageId(document));
            event.setMetadata(JsonUtil.mapper().createObjectNode());
            // 埋点失败不能影响浏览
            try {
                miscMapper.insertEvent(event);
            } catch (RuntimeException ignored) {
                // ignore
            }
        }

        String publicStatus = publicStatus(project);
        return new Dtos.PublicActivityDto(slug, project.getTitle(), publicStatus,
                document, project.getPublishSettings(),
                project.getActivityType(), project.getCoverTone(),
                project.getCoverCode(), project.getCoverTitle(),
                privacyService.read(project));
    }

    /** 公开页匿名事件：只接受表单首次交互，并校验页面确实属于已发布活动。 */
    public void recordPublicEvent(String slug, Dtos.PublicEventRequest request) {
        ProjectEntity project = projectMapper.findByPublicSlug(slug);
        if (project == null || !"published".equals(project.getStatus())) {
            throw BizException.notFound("活动不存在或未发布");
        }
        String eventType = request == null ? null : request.eventType();
        if (!"form_started".equals(eventType)) {
            throw BizException.invalid("不支持的事件类型");
        }
        JsonNode document = publishedDocument(project);
        String pageId = request.pageId();
        if (pageId == null || pageId.isBlank() || !containsPage(document, pageId)) {
            throw BizException.invalid("pageId 不属于当前活动");
        }
        ActivityEventEntity event = new ActivityEventEntity();
        event.setProjectId(project.getId());
        event.setEventType(eventType);
        event.setPageId(pageId);
        event.setMetadata(request.metadata());
        miscMapper.insertEvent(event);
    }

    /**
     * 公开活动列表（不需要登录）。
     * 只返回白名单字段：slug / 标题 / 封面四项 / 状态 / 更新时间。
     * 绝不带 draftDocument、ownerId、publishedVersionId。
     */
    public Dtos.PagedResultHolder<Dtos.PublicActivitySummaryDto> listPublic(String q, String activityType,
                                                                            int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        String type = blankToNull(activityType);
        long total = projectMapper.countPublic(q, type);
        if (total == 0) {
            return new Dtos.PagedResultHolder<>(List.of(), new Dtos.PageMeta(safePage, safeSize, 0));
        }
        List<ProjectEntity> rows = projectMapper.searchPublic(q, type, safeSize, (safePage - 1) * safeSize);
        List<Dtos.PublicActivitySummaryDto> data = rows.stream()
                .map(p -> new Dtos.PublicActivitySummaryDto(
                        p.getPublicSlug(),
                        p.getTitle(),
                        p.getDescription(),
                        p.getActivityType(),
                        p.getCoverTone(),
                        p.getCoverCode(),
                        p.getCoverTitle(),
                        publicStatus(p),
                        Ids.iso(p.getUpdatedAt())))
                .toList();
        return new Dtos.PagedResultHolder<>(data, new Dtos.PageMeta(safePage, safeSize, total));
    }

    /** 公开页的提交状态：published / closed / full / unpublished。 */
    public String publicStatus(ProjectEntity project) {
        if (!"published".equals(project.getStatus())) {
            return "unpublished";
        }
        JsonNode settings = project.getPublishSettings();
        long now = System.currentTimeMillis();
        if (settings != null && !settings.isNull()) {
            long closesAt = settings.path("closesAt").asLong(0);
            if (closesAt > 0 && now > closesAt) {
                return "closed";
            }
            long opensAt = settings.path("opensAt").asLong(0);
            if (opensAt > 0 && now < opensAt) {
                return "closed";
            }
            long max = settings.path("maxSubmissions").asLong(0);
            if (max > 0 && submissionMapper.countValid(project.getId()) >= max) {
                return "full";
            }
        }
        return "published";
    }

    private String firstPageId(JsonNode document) {
        JsonNode pages = document == null ? null : document.get("pages");
        if (pages == null || !pages.isArray() || pages.isEmpty()) {
            return null;
        }
        JsonNode selected = pages.get(0);
        for (JsonNode page : pages) {
            if ("home".equals(page.path("role").asText())) {
                selected = page;
                break;
            }
        }
        String id = selected == null ? "" : selected.path("id").asText("");
        return id.isBlank() ? null : id;
    }

    private JsonNode publishedDocument(ProjectEntity project) {
        if (project.getPublishedVersionId() != null) {
            PageVersionEntity version = versionMapper.findById(project.getPublishedVersionId());
            if (version != null) {
                return version.getDocument();
            }
        }
        return project.getDraftDocument();
    }

    private boolean containsPage(JsonNode document, String pageId) {
        JsonNode pages = document == null ? null : document.get("pages");
        if (pages == null || !pages.isArray()) {
            return false;
        }
        for (JsonNode page : pages) {
            if (pageId.equals(page.path("id").asText())) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ 内部

    public ProjectEntity loadOwned(String projectId) {
        CurrentUser current = CurrentUser.require();
        ProjectEntity project = projectMapper.findById(projectId);
        if (project == null) {
            throw BizException.notFound("项目不存在");
        }
        if (!project.getOwnerId().equals(current.userId())) {
            throw BizException.forbidden("这不是你的项目");
        }
        return project;
    }

    private Dtos.ProjectDto toDto(ProjectEntity project) {
        long submissions = submissionMapper.countValid(project.getId());
        String publicUrl = project.getPublicSlug() == null ? null
                : props.getPublicBaseUrl().replaceAll("/+$", "") + "/" + project.getPublicSlug();
        return new Dtos.ProjectDto(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                project.getActivityType(),
                project.getCoverTone(),
                project.getCoverCode(),
                project.getCoverTitle(),
                project.getStatus(),
                project.getDraftRevision() == null ? 0 : project.getDraftRevision(),
                project.getPublicSlug(),
                publicUrl,
                project.getPublishedVersionId(),
                submissions,
                Ids.iso(project.getCreatedAt()),
                Ids.iso(project.getUpdatedAt()),
                project.getDraftDocument()
        );
    }

    private String resolveSlug(ProjectEntity project, String requested) {
        // 纯空白 / 空串一律等同于「没给」：前端以前会把用户没填过的空 slug 原样发上来，
        // 这里如果按「显式传了」处理，业务会卡在 slug 校验上。
        String explicit = (requested == null || requested.isBlank()) ? null : requested.trim().toLowerCase(Locale.ROOT);
        String candidate;
        if (explicit != null) {
            candidate = explicit;
        } else if (project.getPublicSlug() != null && !project.getPublicSlug().isBlank()) {
            candidate = project.getPublicSlug();
        } else {
            // 中文标题自动生成时是 activity-xxxxxx 这种带随机后缀的形式，
            // 前端并不知道，所以生成的 slug 必须写回项目，否则下次发布又换一个新链接。
            candidate = Ids.slugify(project.getTitle());
        }
        if (!candidate.matches("^[a-z0-9]+(-[a-z0-9]+)*$")) {
            throw BizException.invalid("slug 只能是小写字母、数字和连字符");
        }
        if (projectMapper.countBySlug(candidate, project.getId()) > 0) {
            throw new BizException(ErrorCode.SLUG_CONFLICT, "该访问路径已被占用: " + candidate);
        }
        return candidate;
    }

    private String uniqueSlug(String base, String excludeId) {
        String candidate = (base == null || base.isBlank()) ? Ids.slugify("activity") : base;
        if (projectMapper.countBySlug(candidate, excludeId) == 0) {
            return candidate;
        }
        for (int i = 2; i < 50; i++) {
            String next = candidate + "-" + i;
            if (projectMapper.countBySlug(next, excludeId) == 0) {
                return next;
            }
        }
        return candidate + "-" + Ids.randomToken(6);
    }

    /** 插入新版本号（在并发下靠唯一索引兜底）。 */
    private PageVersionEntity insertVersion(ProjectEntity project, String source) {
        int next = versionMapper.maxVersionNo(project.getId()) + 1;
        PageVersionEntity version = new PageVersionEntity();
        version.setId(Ids.uuid());
        version.setProjectId(project.getId());
        version.setVersionNo(next);
        version.setDocument(project.getDraftDocument());
        version.setSource(source);
        version.setCreatedBy(CurrentUser.require().userId());
        try {
            versionMapper.insert(version);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.REVISION_CONFLICT, "版本号冲突，请重试");
        }
        return versionMapper.findById(version.getId());
    }

    private JsonNode buildDocumentFromTemplate(TemplateEntity template, String title) {
        JsonNode source = template.getDocument();
        if (source == null || source.isNull()) {
            throw new BizException(ErrorCode.PAGE_DOCUMENT_INVALID,
                    "模板 " + template.getCode() + " 的 document 是空的");
        }
        // 模板表里存的是「外层包了一层」的结构：{intent, reason, theme, title, pages:[...]}，
        // 而真正要落进 projects.draft_document 的是里面那个 Doc 本体。
        // 早期直接 doc.setAll(source) 会把整个壳塞进 pages[0]，页面就全乱了 —— 必须只取内层。
        JsonNode inner = source.get("document");
        JsonNode doc = (inner != null && inner.isObject()) ? inner : source;

        if (!doc.hasNonNull("title")) {
            ((ObjectNode) doc).put("title", title);
        }
        if (!doc.has("pages") || !doc.get("pages").isArray() || doc.get("pages").isEmpty()) {
            throw new BizException(ErrorCode.PAGE_DOCUMENT_INVALID,
                    "模板 " + template.getCode() + " 的 document 里没有 pages");
        }
        return fillIds(doc.deepCopy());
    }

    /**
     * 模板里的页面和区块是「配方」，没有 id：id 是运行时由编辑器生成的。
     * 但 schema 要求 page.id / block.id 必填（前端 createBlock 也会补），
     * 所以模板实例化时要在这里补齐，否则发布时会被校验器打回 422。
     */
    private JsonNode fillIds(JsonNode doc) {
        JsonNode pages = doc.get("pages");
        for (JsonNode page : pages) {
            if (page instanceof ObjectNode pageNode) {
                if (!pageNode.hasNonNull("id") || pageNode.get("id").asText().isBlank()) {
                    pageNode.put("id", "pg-" + Ids.uuid());
                }
                JsonNode blocks = pageNode.get("blocks");
                if (blocks != null && blocks.isArray()) {
                    for (JsonNode block : blocks) {
                        if (block instanceof ObjectNode blockNode) {
                            if (!blockNode.hasNonNull("id") || blockNode.get("id").asText().isBlank()) {
                                blockNode.put("id", "blk-" + Ids.uuid());
                            }
                            if (!blockNode.has("props") || blockNode.get("props").isNull()) {
                                blockNode.set("props", JsonUtil.mapper().createObjectNode());
                            }
                        }
                    }
                }
            }
        }
        return doc;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.campus.server.mapper.MiscMapper miscMapper;

    // ================================================================ 封面字段推导
    //
    // 前端 ProjectRecord 里 type / coverTone / coverCode / coverTitle 是必填的，
    // 但建项目时通常只给标题和模板。这里按模板 category 推导默认值，
    // 保证列表接口永远不会返回 null 让卡片空白。

    private static String toneOfCategory(String category) {
        if (category == null) {
            return "photo";
        }
        return switch (category) {
            case "recruitment" -> "photo";
            case "registration" -> "music";
            case "vote" -> "vote";
            case "checkin" -> "checkin";
            default -> "photo";
        };
    }

    /**
     * 模板 category → 中文场景名，与前端 {@code web/src/services/templates.ts}
     * 里的 {@code CATEGORY_META[*].scene} 逐字保持一致。
     *
     * <p>为什么要对齐：前端项目列表按 {@code activityType} 显示和筛选，
     * 如果后端给的是模板名（「社团招新季」）而前端场景名是「社团招新」，
     * 用户在同一类活动之间来回切就会筛不出东西。
     */
    private static String sceneOfCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return switch (category) {
            case "recruitment" -> "社团招新";
            case "registration" -> "活动报名";
            case "vote" -> "投票评选";
            case "checkin" -> "每日打卡";
            default -> null;
        };
    }

    private static String codeOfCategory(String category) {
        if (category == null || category.isBlank()) {
            return "CAMPUS";
        }
        return switch (category) {
            case "recruitment" -> "RECRUIT";
            case "registration" -> "SIGNUP";
            case "vote" -> "VOTE";
            case "checkin" -> "CHECKIN";
            default -> "CAMPUS";
        };
    }

    /** 返回第一个非空白字符串；全空返回 null。 */
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /** 更新语义：请求值为 null 表示「不改」，非 null 就用请求值（含空串 → 置空）。 */
    private static String nullableOr(String current, String incoming) {
        return incoming == null ? current : (incoming.isBlank() ? null : incoming);
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }
}
