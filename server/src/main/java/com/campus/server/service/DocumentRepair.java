package com.campus.server.service;

import com.campus.server.support.Ids;
import com.campus.server.support.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 把智能体吐出来的「候选稿」修成一份<b>能直接落库、能直接 apply</b> 的 PageDocument。
 *
 * <p><b>为什么需要这一步。</b>agent 侧（{@code agent/app/services/tools/planner.py}）产出的是
 * <b>DocPlan</b>：顶层只有 {@code title / intent / reason / theme / meta / pages}，
 * 页面和组件都<b>没有 id</b>，也没有 {@code slug}；前端 {@code planner.ts} 拿到后会走
 * {@code normalizePlan → planToDoc → planPages → createBlock} 自己补 id、补 props、跑 {@code linkPages}。
 * 而后端的 {@code /ai/projects/{id}/generate} 如果原样把这段 JSON 存进 {@code ai_proposals}，
 * 用户点「应用」时 {@code saveDraft} 的 schema 校验就会 422（缺 {@code page.id} / {@code block.id}）。
 *
 * <p><b>所以这里做三件事</b>：
 * <ol>
 *   <li>{@link #repair}：剥壳、补 id、补 props、跑按钮跳转链接、补 theme/meta/slug，产物能直接校验通过；</li>
 *   <li>补完之后再交给 {@code PageDocumentValidator} 校验 —— 修复器不吞错，修不好就让调用方给准确提示；</li>
 *   <li>{@link #diffAgainst}：给出「基线文档 → 候选文档」的变更摘要，前端用来显示冲突提示和做撤销。</li>
 * </ol>
 *
 * <p><b>和前端对齐</b>：这里补的默认 props 只是<b>垫底</b>（保证渲染不白屏），
 * 真实取值以前端 {@code web/src/components/editor/BlockView.vue} 读的键为准，
 * 组件清单与 {@code web/src/types/editor.ts} 的 BLOCK_TYPES 逐字一致。
 */
@Component
public class DocumentRepair {

    /** 与前端 BLOCK_TYPES 逐字一致，顺序也一致，方便肉眼 diff。 */
    private static final List<String> BLOCK_TYPES = List.of(
            "nav", "banner", "heading", "text", "image", "infoCard", "button",
            "form", "radio", "checkbox", "qrcode", "divider", "countdown");

    private static final List<String> PAGE_ROLES = List.of("home", "content", "form", "success");

    private static final List<String> FIELD_TYPES = List.of(
            "text", "tel", "email", "textarea", "select", "radio", "checkbox");

    private static final List<String> INFO_ICONS = List.of(
            "time", "place", "org", "people", "ticket", "contact");

    private static final List<String> BUTTON_ACTIONS = List.of("page", "url", "submit", "none");

    /** 前端 DEFAULT_THEME，缺 theme 时用它，别让页面变成白底黑字的裸 HTML。 */
    private static final Map<String, String> DEFAULT_COLORS = Map.of(
            "primary", "#4f46e5",
            "accent", "#8b5cf6",
            "bg", "#f7f8fc",
            "text", "#111827");

    /** 一次修复的结果：修好的文档 + 说明「动了什么」，便于日志和给用户看。 */
    public record RepairResult(JsonNode document, List<String> notes) {
        public boolean changed() {
            return !notes.isEmpty();
        }
    }

    // ================================================================ 主流程

    /**
     * 把任意形态的候选稿修成完整 PageDocument。
     *
     * <p>接受的形态：
     * <ul>
     *   <li>{@code {title, pages:[...]}} —— 已经是文档，只补缺的 id/props（最常见）；</li>
     *   <li>{@code {document:{...}}} —— 外面包了一层壳（模板表也是这个形状）；</li>
     *   <li>{@code {pages:[...]}} 或裸数组 —— 没有 title，用标题兜底。</li>
     * </ul>
     * 修不好的（没有 pages、页面全空）返回 null，由调用方决定报什么错 —— 这里不抛异常，
     * 因为「修不好」在 generate 和 apply 两处对应的用户话术不一样。
     *
     * @param raw    agent 原样返回 / 历史遗留的候选稿
     * @param title  标题兜底值（项目标题）
     * @param slug   访问路径兜底值（项目 publicSlug 或由标题生成）
     */
    public RepairResult repair(JsonNode raw, String title, String slug) {
        if (raw == null || raw.isNull()) {
            return null;
        }
        // 候选稿可能存成「字符串里套 JSON」（AI 常见），也兜一下
        JsonNode node = raw;
        if (node.isTextual()) {
            try {
                node = JsonUtil.mapper().readTree(node.asText());
            } catch (Exception ex) {
                return null;
            }
        }
        if (!node.isObject() && !node.isArray()) {
            return null;
        }

        List<String> notes = new java.util.ArrayList<>();
        ObjectNode doc;

        if (node.isArray()) {
            doc = JsonUtil.mapper().createObjectNode();
            doc.set("pages", node.deepCopy());
            notes.add("候选稿是裸数组，已按 pages 包装");
        } else {
            ObjectNode source = (ObjectNode) node;
            // 剥壳：{document:{...}} 取内层（与 ProjectService.buildDocumentFromTemplate 的判据一致）
            JsonNode inner = source.get("document");
            if (inner != null && inner.isObject() && inner.has("pages")) {
                doc = ((ObjectNode) inner).deepCopy();
                notes.add("候选稿外层有 document 壳，已剥掉");
            } else {
                doc = source.deepCopy();
            }
        }

        JsonNode pagesNode = doc.get("pages");
        if (pagesNode == null || !pagesNode.isArray() || pagesNode.isEmpty()) {
            return null;
        }

        // ---------- title / slug ----------
        String docTitle = text(doc.get("title"));
        if (docTitle == null || docTitle.isBlank()) {
            doc.put("title", blankTo(title, "未命名活动"));
            notes.add("补 title");
        } else if (docTitle.length() > 160) {
            doc.put("title", docTitle.substring(0, 160));
            notes.add("title 超长已截断");
        }
        if (text(doc.get("slug")) == null || text(doc.get("slug")).isBlank()) {
            doc.put("slug", blankTo(slug, Ids.slugify(text(doc.get("title")))));
            notes.add("补 slug");
        }

        // ---------- theme / meta ----------
        if (!doc.has("theme") || !doc.get("theme").isObject()) {
            ObjectNode theme = JsonUtil.mapper().createObjectNode();
            DEFAULT_COLORS.forEach(theme::put);
            theme.put("radius", 16);
            theme.put("fontScale", 1);
            theme.put("spacing", 1);
            doc.set("theme", theme);
            notes.add("补 theme");
        }
        if (!doc.has("meta") || !doc.get("meta").isObject()) {
            ObjectNode meta = JsonUtil.mapper().createObjectNode();
            meta.put("org", "");
            meta.put("startAt", "");
            meta.put("place", "");
            meta.put("deadline", "");
            meta.put("capacity", 0);
            meta.put("contact", "");
            doc.set("meta", meta);
            notes.add("补 meta");
        }

        // ---------- pages / blocks ----------
        ArrayNode pages = JsonUtil.mapper().createArrayNode();
        int pageIndex = 0;
        for (JsonNode pageRaw : pagesNode) {
            if (!pageRaw.isObject()) {
                continue;
            }
            ObjectNode page = ((ObjectNode) pageRaw).deepCopy();
            pageIndex++;
            if (text(page.get("id")) == null || text(page.get("id")).isBlank()) {
                page.put("id", "page_" + Ids.randomToken(8));
                notes.add("补 page.id");
            }
            String name = text(page.get("name"));
            if (name == null || name.isBlank()) {
                page.put("name", "页面 " + pageIndex);
                notes.add("补 page.name");
            } else if (name.length() > 80) {
                page.put("name", name.substring(0, 80));
            }
            String role = text(page.get("role"));
            if (role == null || !PAGE_ROLES.contains(role)) {
                page.put("role", "content");
                notes.add("修正 page.role（" + blankTo(role, "空") + "）");
            }

            JsonNode blocksNode = page.get("blocks");
            // agent 有时写 components，前端 normalizePlan 也兼容这个键
            if ((blocksNode == null || !blocksNode.isArray() || blocksNode.isEmpty())
                    && page.get("components") instanceof ArrayNode components) {
                blocksNode = components;
                notes.add("page.blocks 为空，改用 components");
            }
            ArrayNode blocks = JsonUtil.mapper().createArrayNode();
            if (blocksNode != null && blocksNode.isArray()) {
                for (JsonNode blockRaw : blocksNode) {
                    ObjectNode block = repairBlock(blockRaw, notes);
                    if (block != null) {
                        blocks.add(block);
                    }
                }
            }
            page.set("blocks", blocks);
            pages.add(page);
        }
        if (pages.isEmpty()) {
            return null;
        }

        // 至少要有一页 home，否则公开页/发布链路都没有入口
        boolean hasHome = false;
        for (JsonNode page : pages) {
            if ("home".equals(text(page.get("role")))) {
                hasHome = true;
                break;
            }
        }
        if (!hasHome) {
            ((ObjectNode) pages.get(0)).put("role", "home");
            notes.add("没有 home 页，已把第一页设为 home");
        }

        doc.set("pages", pages);

        // ---------- 跨页链接：按钮的 targetRole → 真实 pageId ----------
        if (linkPages(doc)) {
            notes.add("补按钮跳转的 pageId");
        }

        // createdAt / updatedAt 存数字，与前端 Doc 一致
        long now = System.currentTimeMillis();
        if (!doc.has("createdAt") || doc.get("createdAt").isNull()) {
            doc.put("createdAt", now);
        }
        doc.put("updatedAt", now);
        if (!doc.has("published")) {
            doc.putNull("published");
        }

        return new RepairResult(doc, List.copyOf(notes));
    }

    /**
     * 让带 targetRole 的按钮找到真实页面 id（与前端 {@code utils/doc.ts linkPages} 同义）。
     *
     * <p>为什么必须做：agent 给的按钮只有 {@code targetRole:"form"}，没有 pageId。
     * 前端预览时 {@code PhoneScreen.onAction} 会按 role 兜底找页面，所以「看着能点」；
     * 但一旦文档存到后端被别处消费，只有 pageId 才是准的。
     *
     * @return 是否真的补了值（用于生成修复说明，避免每次都报「已修复」）
     */
    private boolean linkPages(JsonNode doc) {
        boolean touched = false;
        JsonNode pages = doc.get("pages");
        for (JsonNode page : pages) {
            JsonNode blocks = page.get("blocks");
            if (blocks == null || !blocks.isArray()) {
                continue;
            }
            for (JsonNode block : blocks) {
                if (!"button".equals(text(block.get("type")))) {
                    continue;
                }
                JsonNode props = block.get("props");
                if (!(props instanceof ObjectNode propsNode)) {
                    continue;
                }
                JsonNode action = propsNode.get("action");
                if (!(action instanceof ObjectNode actionNode)) {
                    continue;
                }
                if (!"page".equals(text(actionNode.get("type")))) {
                    continue;
                }
                String pageId = text(actionNode.get("pageId"));
                if (pageId != null && !pageId.isBlank()) {
                    continue;
                }
                String targetRole = text(actionNode.get("targetRole"));
                String resolved = findPageIdByRole(pages, targetRole);
                if (resolved != null) {
                    actionNode.put("pageId", resolved);
                    touched = true;
                }
            }
        }
        return touched;
    }

    private String findPageIdByRole(JsonNode pages, String role) {
        // 没写 role 时按前端 linkPages 的默认目标（表单页）找，找不到就退到第一页
        String wanted = (role == null || role.isBlank()) ? "form" : role;
        for (JsonNode page : pages) {
            if (wanted.equals(text(page.get("role")))) {
                return text(page.get("id"));
            }
        }
        if (pages.isEmpty()) {
            return null;
        }
        return text(pages.get(0).get("id"));
    }

    // ================================================================ 组件修复

    /** 修一个组件：类型不合法直接丢（返回 null），合法则补齐 id 和 props。 */
    private ObjectNode repairBlock(JsonNode raw, List<String> notes) {
        if (raw == null || !raw.isObject()) {
            return null;
        }
        ObjectNode block = ((ObjectNode) raw).deepCopy();
        String type = text(block.get("type"));
        // agent 偶尔写 component，前端 normalizePlan 也认这个键
        if (type == null || type.isBlank()) {
            type = text(block.get("component"));
            if (type != null && !type.isBlank()) {
                block.put("type", type);
                notes.add("组件缺 type，改用 component");
            }
        }
        if (type == null || !BLOCK_TYPES.contains(type)) {
            notes.add("丢弃不支持的组件类型：" + blankTo(type, "空"));
            return null;
        }

        if (text(block.get("id")) == null || text(block.get("id")).isBlank()) {
            block.put("id", type + "_" + Ids.randomToken(8));
            notes.add("补 " + type + ".id");
        }

        ObjectNode props;
        JsonNode propsRaw = block.get("props");
        if (propsRaw instanceof ObjectNode p) {
            props = p.deepCopy();
        } else if (block.get("properties") instanceof ObjectNode p) {
            props = p.deepCopy();
            notes.add(type + " 缺 props，改用 properties");
        } else {
            props = JsonUtil.mapper().createObjectNode();
            notes.add("补 " + type + ".props");
        }
        block.set("props", props);

        switch (type) {
            case "nav" -> props.putIfAbsent("title", JsonUtil.mapper().getNodeFactory().textNode("活动标题"));
            case "banner" -> {
                props.putIfAbsent("title", JsonUtil.mapper().getNodeFactory().textNode("在这里写主标题"));
                props.putIfAbsent("subtitle", JsonUtil.mapper().getNodeFactory().textNode(""));
                props.putIfAbsent("height", JsonUtil.mapper().getNodeFactory().numberNode(220));
            }
            case "heading" -> {
                props.putIfAbsent("text", JsonUtil.mapper().getNodeFactory().textNode("章节标题"));
                props.putIfAbsent("size", JsonUtil.mapper().getNodeFactory().textNode("lg"));
            }
            case "text" -> {
                props.putIfAbsent("text", JsonUtil.mapper().getNodeFactory().textNode(""));
                props.putIfAbsent("size", JsonUtil.mapper().getNodeFactory().textNode("md"));
            }
            case "image" -> {
                props.putIfAbsent("url", JsonUtil.mapper().getNodeFactory().textNode(""));
                props.putIfAbsent("caption", JsonUtil.mapper().getNodeFactory().textNode(""));
                props.putIfAbsent("ratio", JsonUtil.mapper().getNodeFactory().textNode("4:3"));
            }
            case "infoCard" -> repairInfoCard(props, notes);
            case "button" -> repairButton(props, notes);
            case "form" -> repairForm(props, notes);
            case "radio", "checkbox" -> repairChoice(props, type);
            case "qrcode" -> {
                props.putIfAbsent("url", JsonUtil.mapper().getNodeFactory().textNode(""));
                props.putIfAbsent("label", JsonUtil.mapper().getNodeFactory().textNode("扫码查看活动详情"));
            }
            case "divider" -> props.putIfAbsent("label", JsonUtil.mapper().getNodeFactory().textNode(""));
            case "countdown" -> {
                props.putIfAbsent("target", JsonUtil.mapper().getNodeFactory().textNode(""));
                props.putIfAbsent("label", JsonUtil.mapper().getNodeFactory().textNode("距离报名截止"));
                props.putIfAbsent("doneText", JsonUtil.mapper().getNodeFactory().textNode("报名已截止"));
            }
            default -> {
                // 上面已穷举 BLOCK_TYPES，这里只是兜底，不做事
            }
        }
        // 前端 createBlock 一定会带 style，缺了编辑器面板会读到 undefined
        if (!block.has("style") || !block.get("style").isObject()) {
            ObjectNode style = JsonUtil.mapper().createObjectNode();
            style.put("bg", "");
            style.put("paddingX", 0);
            style.put("paddingY", 0);
            style.put("radius", 0);
            // 空串不在 enum(left/center/right) 里，会直接把整份文档卡成 422
            style.put("align", "left");
            notes.add("补 " + type + ".style");
        }
        if (!block.has("hidden")) {
            block.put("hidden", false);
        }
        return block;
    }

    /** infoCard：items 的 id / icon 白名单 / value 兜底，缺 items 就按 meta 造三行。 */
    private void repairInfoCard(ObjectNode props, List<String> notes) {
        props.putIfAbsent("title", JsonUtil.mapper().getNodeFactory().textNode("活动信息"));
        JsonNode itemsRaw = props.get("items");
        if (!(itemsRaw instanceof ArrayNode items) || items.isEmpty()) {
            ArrayNode built = JsonUtil.mapper().createArrayNode();
            for (String[] pair : new String[][] {{"time", "时间"}, {"place", "地点"}, {"org", "主办"}}) {
                ObjectNode item = JsonUtil.mapper().createObjectNode();
                item.put("id", "it_" + Ids.randomToken(8));
                item.put("icon", pair[0]);
                item.put("label", pair[1]);
                item.put("value", "待补充");
                built.add(item);
            }
            props.set("items", built);
            notes.add("infoCard 缺 items，已按时间/地点/主办补默认行");
            return;
        }
        for (JsonNode itemRaw : items) {
            if (!(itemRaw instanceof ObjectNode item)) {
                continue;
            }
            if (text(item.get("id")) == null || text(item.get("id")).isBlank()) {
                item.put("id", "it_" + Ids.randomToken(8));
                notes.add("补 infoCard.items[].id");
            }
            String icon = text(item.get("icon"));
            if (icon == null || !INFO_ICONS.contains(icon)) {
                item.put("icon", "time");
            }
            if (!item.hasNonNull("label")) {
                item.put("label", "");
            }
            if (!item.hasNonNull("value")) {
                item.put("value", "待补充");
            }
        }
    }

    /** button：action 必须是对象，type 必须在白名单里，没写就默认跳表单页。 */
    private void repairButton(ObjectNode props, List<String> notes) {
        props.putIfAbsent("text", JsonUtil.mapper().getNodeFactory().textNode("立即报名"));
        props.putIfAbsent("variant", JsonUtil.mapper().getNodeFactory().textNode("primary"));
        JsonNode actionRaw = props.get("action");
        if (actionRaw instanceof ObjectNode action) {
            String actionType = text(action.get("type"));
            if (actionType == null || !BUTTON_ACTIONS.contains(actionType)) {
                action.put("type", "page");
                notes.add("修正 button.action.type（" + blankTo(actionType, "空") + "）");
            }
            if (!action.hasNonNull("pageId")) {
                action.put("pageId", "");
            }
            if (!action.hasNonNull("url")) {
                action.put("url", "");
            }
            return;
        }
        ObjectNode action = JsonUtil.mapper().createObjectNode();
        // 字符串写法（"submit"/"form"）按语义翻译成对象
        String raw = actionRaw != null && actionRaw.isTextual() ? actionRaw.asText() : "";
        action.put("type", "submit".equals(raw) ? "submit" : "page");
        action.put("pageId", "");
        action.put("url", "");
        action.put("targetRole", "form");
        props.set("action", action);
        notes.add("button.action 不是对象，已补默认跳转");
    }

    /** form：fields 必须是数组，字段要有 id/label/type/required/options。 */
    private void repairForm(ObjectNode props, List<String> notes) {
        props.putIfAbsent("title", JsonUtil.mapper().getNodeFactory().textNode("报名信息"));
        props.putIfAbsent("buttonText", JsonUtil.mapper().getNodeFactory().textNode("提交报名"));
        JsonNode fieldsRaw = props.get("fields");
        if (!(fieldsRaw instanceof ArrayNode fields) || fields.isEmpty()) {
            ArrayNode built = JsonUtil.mapper().createArrayNode();
            built.add(newField("姓名", "text", true, "请输入姓名"));
            built.add(newField("手机号", "tel", true, "用于接收活动通知"));
            props.set("fields", built);
            notes.add("form 缺 fields，已补姓名/手机号");
            return;
        }
        for (JsonNode fieldRaw : fields) {
            if (!(fieldRaw instanceof ObjectNode field)) {
                continue;
            }
            if (text(field.get("id")) == null || text(field.get("id")).isBlank()) {
                field.put("id", "f_" + Ids.randomToken(8));
                notes.add("补 form.fields[].id");
            }
            if (!field.hasNonNull("label") || field.get("label").asText().isBlank()) {
                field.put("label", "未命名字段");
            }
            String fieldType = text(field.get("type"));
            if (fieldType == null || !FIELD_TYPES.contains(fieldType)) {
                field.put("type", "text");
                notes.add("修正表单字段类型（" + blankTo(fieldType, "空") + "）");
            }
            if (!field.has("required")) {
                field.put("required", false);
            }
            if (!(field.get("options") instanceof ArrayNode)) {
                field.put("options", JsonUtil.mapper().createArrayNode());
            }
        }
    }

    private ObjectNode newField(String label, String type, boolean required, String placeholder) {
        ObjectNode field = JsonUtil.mapper().createObjectNode();
        field.put("id", "f_" + Ids.randomToken(8));
        field.put("label", label);
        field.put("type", type);
        field.put("required", required);
        field.put("placeholder", placeholder);
        field.set("options", JsonUtil.mapper().createArrayNode());
        return field;
    }

    /**
     * radio / checkbox：question + options（至少两项，否则 BlockView 渲染出空列表）。
     *
     * <p>单选默认必填、多选默认选填 —— 报名场景里「选一个」通常不能空着。
     */
    private void repairChoice(ObjectNode props, String type) {
        if (!props.hasNonNull("question") || props.get("question").asText().isBlank()) {
            props.put("question", "请选择");
        }
        JsonNode optionsRaw = props.get("options");
        if (!(optionsRaw instanceof ArrayNode options) || options.size() < 2) {
            ArrayNode built = JsonUtil.mapper().createArrayNode();
            built.add("选项一");
            built.add("选项二");
            props.set("options", built);
        }
        if (!props.has("required")) {
            props.put("required", "radio".equals(type));
        }
    }

    // ================================================================ 变更摘要（给前端做冲突提示 / 撤销）

    /**
     * 比较「服务端当前草稿」和「候选稿」，给前端一段人话，用于采用前的冲突提示。
     *
     * <p>刻意做得粗：只数页面数和组件数，不逐字段 diff —— 前端真正要的是
     * 「这次替换会动多少东西」，不是一份精确的 JSON Patch。
     */
    public String diffSummary(JsonNode before, JsonNode after) {
        int beforePages = before != null && before.get("pages") != null && before.get("pages").isArray()
                ? before.get("pages").size() : 0;
        int afterPages = after != null && after.get("pages") != null && after.get("pages").isArray()
                ? after.get("pages").size() : 0;
        int beforeBlocks = countBlocks(before);
        int afterBlocks = countBlocks(after);
        String beforeTitle = before == null ? null : text(before.get("title"));
        String afterTitle = after == null ? null : text(after.get("title"));

        StringBuilder sb = new StringBuilder();
        sb.append("把页面从 ").append(beforePages).append(" 页 / ").append(beforeBlocks)
                .append(" 个组件，替换成 ").append(afterPages).append(" 页 / ").append(afterBlocks)
                .append(" 个组件");
        if (beforeTitle != null && afterTitle != null && !beforeTitle.equals(afterTitle)) {
            sb.append("；标题由「").append(beforeTitle).append("」改为「").append(afterTitle).append("」");
        }
        sb.append("。采用后可在编辑器里先预览再发布。");
        return sb.toString();
    }

    private int countBlocks(JsonNode document) {
        if (document == null || !(document.get("pages") instanceof ArrayNode pages)) {
            return 0;
        }
        int total = 0;
        for (JsonNode page : pages) {
            if (page.get("blocks") instanceof ArrayNode blocks) {
                total += blocks.size();
            }
        }
        return total;
    }

    // ================================================================ 小工具

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
