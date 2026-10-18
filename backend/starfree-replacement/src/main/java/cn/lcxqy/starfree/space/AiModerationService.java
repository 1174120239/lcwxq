package cn.lcxqy.starfree.space;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestTemplate;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiModerationService {
    public static final String TYPE_SPACE = "space";
    public static final String TYPE_QUESTION = "question";
    public static final String TYPE_SPACE_COMMENT = "space_comment";
    public static final String TYPE_QA_ANSWER = "qa_answer";
    public static final String TYPE_QA_COMMENT = "qa_comment";
    public static final String TYPE_GALLERY = "gallery";

    private static final Logger LOG = LoggerFactory.getLogger(AiModerationService.class);
    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions";
    private static final int MAX_IMAGES = 9;
    private static final int MAX_INLINE_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_INLINE_IMAGE_DIMENSION = 1280;
    private static final int MAX_INLINE_PAYLOAD_BYTES = 2 * 1024 * 1024;
    private static final String[] INLINE_IMAGE_HOST_SUFFIXES = {".lcxqy.cn", ".lcyz.site"};
    private static final String SYSTEM_PROMPT = "你是校园社区内容安全审核员。必须严格审查挑起群体或地域对立、"
            + "暴露学生姓名、学号、班级、宿舍、联系方式等个人信息、违法犯罪、威胁辱骂、色情低俗、"
            + "广告垃圾、自伤或伤害他人的风险，以及其他可能伤害学生的内容。只审核提供的文字，不要"
            + "猜测图片或视频内容。只输出JSON，不要Markdown："
            + "{\"safe\":true或false,\"category\":\"正常/挑起对立/学生信息/违法违规/威胁辱骂/色情低俗/广告垃圾/自伤风险/其他\","
            + "\"reason\":\"不超过160字的具体中文理由\"}。不确定时safe必须为false并说明需人工确认。";
    private static final String VISION_SYSTEM_PROMPT = "你是校园社区内容安全审核员。必须同时审查文字和随消息提供的全部图片，"
            + "逐张检查，不得遗漏。重点检查：色情低俗或性暗示、暴力血腥、违法犯罪、广告引流和二维码、"
            + "自伤或伤害他人、挑起群体或地域对立、威胁辱骂，以及图片中暴露学生姓名、学号、班级、"
            + "宿舍、证件、联系方式等个人隐私。只审核实际提供的内容，不要猜测未提供的信息。只输出JSON，"
            + "不要Markdown：{\"safe\":true或false,\"category\":\"正常/挑起对立/学生信息/违法违规/威胁辱骂/"
            + "色情低俗/暴力血腥/广告垃圾/自伤风险/其他\",\"reason\":\"不超过160字的具体中文理由\","
            + "\"images\":[{\"index\":1,\"safe\":true或false,\"category\":\"同上\",\"reason\":\"不超过80字\"}]}。"
            + "所有文字和图片都安全时safe才为true；任一图片有风险时safe必须为false，并在reason中写明第几张图。"
            + "不确定时safe必须为false并说明需人工确认。";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RestTemplate http;

    public AiModerationService(JdbcTemplate jdbc, ObjectMapper mapper,
                               @Qualifier("aiModerationRestTemplate") RestTemplate http) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.http = http;
    }

    public boolean enabled() {
        return enabledForSpace();
    }

    public boolean enabledForSpace() {
        return config().available(Scope.SPACE);
    }

    public boolean enabledForQuestion() {
        return config().available(Scope.QUESTION);
    }

    public boolean enabledForComments() {
        return config().available(Scope.COMMENT);
    }

    public boolean enabledForGallery() {
        return config().available(Scope.GALLERY);
    }

    public boolean globalAuditEnabled() {
        return config().globalAudit;
    }

    public CommentPolicy commentPolicy() {
        Config config = config();
        return new CommentPolicy(config.available(Scope.COMMENT), config.commentReviewTime,
                config.commentAction, config.lastCommentReviewDate,
                config.lastCommentReviewStarted, config.lastCommentReviewFinished);
    }

    /** Compatibility entry point retained for dynamic publishing. */
    public Decision review(long spaceId, long authorUid, String text, String pic) {
        return reviewSpace(spaceId, authorUid, text, pic);
    }

    public Decision reviewSpace(long spaceId, long authorUid, String text, String pic) {
        return reviewSpace(spaceId, authorUid, text, pic, imageUrls(pic));
    }

    public Decision reviewSpace(long spaceId, long authorUid, String text, String pic,
                                List<String> images) {
        int attachments = attachmentCount(pic);
        Decision decision = reviewContent(Scope.SPACE, TYPE_SPACE, spaceId, 0, authorUid,
                "realtime", normalize(text), attachments, images);
        syncLegacySpaceReview(spaceId, authorUid, decision);
        return decision;
    }

    public Decision reviewGallery(long galleryId, long authorUid, String text,
                                  List<String> images) {
        List<String> safeImages = images == null ? Collections.<String>emptyList() : images;
        return reviewContent(Scope.GALLERY, TYPE_GALLERY, galleryId, 0, authorUid,
                "realtime", normalize(text), safeImages.size(), safeImages);
    }

    public Decision reviewQuestion(long questionId, long authorUid, String title,
                                   String description, String coverUrl) {
        String text = "问题标题：" + normalize(title) + "\n问题说明：" + normalize(description);
        int attachments = normalize(coverUrl).isEmpty() ? 0 : 1;
        Decision decision = reviewContent(Scope.QUESTION, TYPE_QUESTION, questionId, 0,
                authorUid, "realtime", text, attachments, Collections.<String>emptyList());
        if (decision.isEvaluated() && !decision.isSafe()) {
            notifyAuthor(authorUid, questionId, "你的提问未通过 AI 审核：" + decision.getReason());
        }
        return decision;
    }

    public Decision reviewComment(String contentType, long contentId, long parentId,
                                  long authorUid, String text) {
        if (!TYPE_SPACE_COMMENT.equals(contentType) && !TYPE_QA_ANSWER.equals(contentType)
                && !TYPE_QA_COMMENT.equals(contentType)) {
            throw new IllegalArgumentException("不支持的评论类型");
        }
        return reviewContent(Scope.COMMENT, contentType, contentId, parentId, authorUid,
                "daily", normalize(text), 0, Collections.<String>emptyList());
    }

    /**
     * Consumes a submission after its database transaction commits. Safe content is published;
     * risky or failed reviews stay hidden and are handed to staff.
     */
    @Async("moderationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void reviewWhenReady(AiModerationRequest request) {
        if (request == null) {
            return;
        }
        try {
            if (TYPE_SPACE.equals(request.getContentType())) {
                if (enabledForSpace()) {
                    applySpaceDecision(request);
                }
            } else if (TYPE_GALLERY.equals(request.getContentType())) {
                if (enabledForGallery()) {
                    applyGalleryDecision(request);
                }
            }
        } catch (RuntimeException error) {
            LOG.error("Asynchronous AI moderation failed for {} {}", request.getContentType(),
                    request.getContentId(), error);
        }
    }

    public void markContentStatus(long reviewId, int status) {
        if (reviewId <= 0) {
            return;
        }
        try {
            jdbc.update("UPDATE starfree_ai_moderation_reviews SET content_status=?,modified=? WHERE id=?",
                    status, Instant.now().getEpochSecond(), reviewId);
        } catch (DataAccessException error) {
            LOG.error("Could not update AI review {} content status", reviewId, error);
        }
    }

    @Transactional
    public void recordHumanAction(String contentType, long contentId, int fromStatus, int toStatus,
                                  long operatorUid, String note) {
        String action = toStatus == 1 ? "approved" : (toStatus == 2 ? "locked" : "rejected");
        recordHumanAction(contentType, contentId, fromStatus, toStatus, operatorUid, note, action);
    }

    @Transactional
    public void recordHumanAction(String contentType, long contentId, int fromStatus, int toStatus,
                                  long operatorUid, String note, String action) {
        long now = Instant.now().getEpochSecond();
        Long reviewId = latestReviewId(contentType, contentId);
        jdbc.update("INSERT INTO starfree_ai_moderation_actions"
                        + "(review_id,content_type,content_id,operator_uid,from_status,to_status,action,note,created) "
                        + "VALUES(?,?,?,?,?,?,?,?,?)",
                reviewId, contentType, contentId, operatorUid, fromStatus, toStatus,
                action, truncate(normalize(note), 1000), now);
        if (reviewId != null) {
            jdbc.update("UPDATE starfree_ai_moderation_reviews SET content_status=?,human_decision=?,"
                            + "reviewer_uid=?,review_note=?,reviewed=?,modified=? WHERE id=?",
                    toStatus, action, operatorUid, truncate(normalize(note), 1000), now, now, reviewId);
        }
    }

    @Transactional
    public void recordGalleryAction(long contentId, String fromStatus, String toStatus,
                                    long operatorUid, String note) {
        try {
            int from = "published".equals(fromStatus) ? 1 : 0;
            int to = "published".equals(toStatus) ? 1 : 0;
            String action = "published".equals(toStatus) ? "approved"
                    : ("submitted".equals(toStatus) ? "pending" : "rejected");
            recordHumanAction(TYPE_GALLERY, contentId, from, to, operatorUid, note, action);
        } catch (DataAccessException error) {
            LOG.warn("Could not record gallery human action for {}", contentId);
        }
    }

    /** Splits a legacy pic field into image URLs that a vision model can consume. */
    public static List<String> imageUrls(String pic) {
        if (pic == null || pic.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>();
        for (String part : pic.split("\\|\\|")) {
            String url = part.trim();
            if (url.isEmpty()) {
                continue;
            }
            String lower = url.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                continue;
            }
            if (lower.matches(".*\\.(mp4|mov|m4v|webm|avi|mkv|flv|m3u8)(\\?.*)?$")) {
                continue;
            }
            result.add(url);
            if (result.size() >= MAX_IMAGES) {
                break;
            }
        }
        return result;
    }

    private void applySpaceDecision(AiModerationRequest request) {
        Decision decision = reviewSpace(request.getContentId(), request.getAuthorUid(),
                request.getText(), request.getAttachment(), request.getImages());
        if (!decision.isEvaluated()) {
            if (spaceStatus(request.getContentId()) == 0 && spaceExists(request.getContentId())) {
                notifyPending(request.getAuthorUid(), request.getContentId(), "动态");
            }
            return;
        }
        int current = spaceStatus(request.getContentId());
        if (decision.isSafe()) {
            int changed = jdbc.update("UPDATE starfree_space SET status=1 "
                            + "WHERE id=? AND status=0 AND modified=?",
                    request.getContentId(), request.getModified());
            if (changed == 1) {
                markContentStatus(decision.getReviewId(), 1);
                return;
            }
            markContentStatus(decision.getReviewId(), current == 1 ? 1 : 0);
            return;
        }
        markContentStatus(decision.getReviewId(), current == 1 ? 1 : 0);
        if (current == 0) {
            notifyPending(request.getAuthorUid(), request.getContentId(), "动态");
        }
    }

    private void applyGalleryDecision(AiModerationRequest request) {
        Decision decision = reviewGallery(request.getContentId(), request.getAuthorUid(),
                request.getText(), request.getImages());
        String current = galleryStatus(request.getContentId());
        long now = Instant.now().getEpochSecond();
        if (!decision.isEvaluated()) {
            if ("submitted".equals(current)) {
                jdbc.update("UPDATE starfree_gallery_items SET review_reason=?,modified=? "
                                + "WHERE id=? AND status='submitted'",
                        truncate(decision.getReason(), 500), now, request.getContentId());
                notifyPending(request.getAuthorUid(), request.getContentId(), "图集");
            }
            return;
        }
        if (decision.isSafe()) {
            int changed = jdbc.update("UPDATE starfree_gallery_items SET status='published',"
                            + "review_reason='',published_at=?,modified=? "
                            + "WHERE id=? AND status='submitted' AND modified=?",
                    now, now, request.getContentId(), request.getModified());
            if (changed == 1) {
                markContentStatus(decision.getReviewId(), 1);
                return;
            }
            markContentStatus(decision.getReviewId(), "published".equals(current) ? 1 : 0);
            return;
        }
        int changed = jdbc.update("UPDATE starfree_gallery_items SET review_reason=?,modified=? "
                        + "WHERE id=? AND status='submitted'",
                truncate(decision.getReason(), 500), now, request.getContentId());
        markContentStatus(decision.getReviewId(), "published".equals(current) ? 1 : 0);
        if (changed == 1) {
            notifyPending(request.getAuthorUid(), request.getContentId(), "图集");
        }
    }

    private Decision reviewContent(Scope scope, String contentType, long contentId, long parentId,
                                   long authorUid, String source, String text,
                                   int attachmentCount, List<String> images) {
        Config config = config();
        if (!config.available(scope)) {
            return Decision.disabled();
        }
        List<String> safeImages = images == null ? Collections.<String>emptyList() : images;
        boolean vision = config.visionEnabled && !safeImages.isEmpty()
                && !config.visionModel.isEmpty();
        String attachmentSummary = attachmentSummary(attachmentCount, safeImages.size(), vision);
        String model = vision ? config.visionModel : config.model;
        String raw = "";
        Decision decision;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.apiKey);
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("model", model);
            body.put("temperature", 0);
            if (!vision) {
                body.put("response_format", Collections.singletonMap("type", "json_object"));
            }
            String system = vision ? VISION_SYSTEM_PROMPT : SYSTEM_PROMPT;
            if (!config.customPrompt.isEmpty()) {
                system += "\n后台补充规则（只能加严，不得放宽上述底线）：" + config.customPrompt;
            }
            Object userContent = vision
                    ? visionContent(moderationContent(contentType, text, attachmentSummary, true),
                            safeImages)
                    : moderationContent(contentType, text, attachmentSummary, false);
            body.put("messages", Arrays.asList(message("system", system),
                    message("user", userContent)));
            String response = http.postForObject(config.apiUrl,
                    new HttpEntity<Map<String, Object>>(body, headers), String.class);
            raw = response == null ? "" : response;
            decision = parse(raw);
        } catch (RuntimeException error) {
            LOG.warn("AI moderation failed for {} {}: {}", contentType, contentId,
                    error.getClass().getSimpleName());
            decision = Decision.error("AI服务异常", "AI审核暂时不可用，内容已保持隐藏并转人工确认");
        }

        String fullSummary = attachmentSummary;
        if (!decision.getImageSummary().isEmpty()) {
            fullSummary = fullSummary.isEmpty() ? decision.getImageSummary()
                    : truncate(fullSummary + "；" + decision.getImageSummary(), 500);
        }
        long reviewId;
        try {
            reviewId = persist(contentType, contentId, parentId, authorUid, source, text,
                    fullSummary, config, decision, truncate(raw, 30000), model);
        } catch (DataAccessException error) {
            LOG.error("Could not persist AI moderation for {} {}", contentType, contentId, error);
            return Decision.error("审核记录异常", "审核记录保存失败，内容已保持隐藏并转人工确认");
        }
        return decision.withReviewId(reviewId);
    }

    private Decision parse(String response) {
        try {
            Map<String, Object> root = mapper.readValue(response,
                    new TypeReference<Map<String, Object>>() { });
            Object choicesValue = root.get("choices");
            if (!(choicesValue instanceof List) || ((List<?>) choicesValue).isEmpty()) {
                throw new IllegalArgumentException("choices missing");
            }
            Object first = ((List<?>) choicesValue).get(0);
            if (!(first instanceof Map)) {
                throw new IllegalArgumentException("choice invalid");
            }
            Object message = ((Map<?, ?>) first).get("message");
            if (!(message instanceof Map)) {
                throw new IllegalArgumentException("message invalid");
            }
            String content = String.valueOf(((Map<?, ?>) message).get("content")).trim();
            if (content.startsWith("```")) {
                content = content.replaceFirst("^```(?:json)?\\s*", "")
                        .replaceFirst("\\s*```$", "").trim();
            }
            Map<String, Object> result = mapper.readValue(content,
                    new TypeReference<Map<String, Object>>() { });
            if (!(result.get("safe") instanceof Boolean)) {
                throw new IllegalArgumentException("safe invalid");
            }
            boolean safe = Boolean.TRUE.equals(result.get("safe"));
            String category = bounded(result.get("category"), 80, safe ? "正常" : "其他");
            String reason = bounded(result.get("reason"), 1000,
                    safe ? "未发现明显风险" : "内容需要人工确认");
            return new Decision(true, safe, safe ? "approved" : "rejected",
                    category, reason, 0, imageSummary(result));
        } catch (Exception error) {
            return Decision.error("AI输出异常", "AI审核结果无法解析，内容已保持隐藏并转人工确认");
        }
    }

    private String imageSummary(Map<String, Object> result) {
        Object value = result.get("images");
        if (!(value instanceof List)) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        int included = 0;
        for (Object entry : (List<?>) value) {
            if (!(entry instanceof Map)) {
                continue;
            }
            Map<?, ?> image = (Map<?, ?>) entry;
            if (!Boolean.FALSE.equals(image.get("safe"))) {
                continue;
            }
            String index = text(image.get("index"));
            String category = bounded(image.get("category"), 40, "其他");
            String reason = bounded(image.get("reason"), 80, "需要人工确认");
            if (summary.length() > 0) {
                summary.append("；");
            }
            summary.append("第").append(index.isEmpty() ? "?" : index).append("张：")
                    .append(category).append("（").append(reason).append("）");
            included++;
            if (included >= MAX_IMAGES) {
                break;
            }
        }
        return truncate(summary.toString(), 300);
    }

    private long persist(String contentType, long contentId, long parentId, long authorUid,
                         String source, String snapshot, String attachmentSummary, Config config,
                         Decision decision, String raw, String model) {
        long now = Instant.now().getEpochSecond();
        String hash = sha256(contentType + "\n" + contentId + "\n" + snapshot + "\n"
                + attachmentSummary);
        jdbc.update("INSERT INTO starfree_ai_moderation_reviews"
                        + "(content_type,content_id,parent_id,author_uid,review_source,content_hash,"
                        + "content_snapshot,attachment_summary,ai_decision,risk_category,reason,provider,"
                        + "model,raw_response,content_status,human_decision,review_note,reviewed,created,modified) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'','',0,?,?) "
                        + "ON DUPLICATE KEY UPDATE ai_decision=VALUES(ai_decision),"
                        + "risk_category=VALUES(risk_category),reason=VALUES(reason),provider=VALUES(provider),"
                        + "model=VALUES(model),raw_response=VALUES(raw_response),content_status=VALUES(content_status),"
                        + "modified=VALUES(modified),id=LAST_INSERT_ID(id)",
                contentType, contentId, parentId, authorUid, source, hash,
                truncate(snapshot, 10000), truncate(attachmentSummary, 500), decision.status,
                decision.category, decision.reason, config.provider, model, raw,
                decision.safe ? 1 : 0, now, now);
        Long id = jdbc.queryForObject("SELECT id FROM starfree_ai_moderation_reviews "
                        + "WHERE content_type=? AND content_id=? AND content_hash=? LIMIT 1",
                Long.class, contentType, contentId, hash);
        if (id == null || id <= 0) {
            throw new IllegalStateException("AI审核记录编号读取失败");
        }
        return id;
    }

    private void syncLegacySpaceReview(long spaceId, long authorUid, Decision decision) {
        if (!decision.isEvaluated()) {
            return;
        }
        try {
            Config config = config();
            long now = Instant.now().getEpochSecond();
            jdbc.update("INSERT INTO starfree_space_ai_reviews"
                            + "(space_id,author_uid,status,risk_category,reason,provider,model,raw_response,"
                            + "reviewer_uid,review_note,created,modified) VALUES(?,?,?,?,?,?,?,'',NULL,'',?,?) "
                            + "ON DUPLICATE KEY UPDATE status=VALUES(status),risk_category=VALUES(risk_category),"
                            + "reason=VALUES(reason),provider=VALUES(provider),model=VALUES(model),modified=VALUES(modified)",
                    spaceId, authorUid, decision.safe ? "approved" : "pending", decision.category,
                    decision.reason, config.provider, config.model, now, now);
        } catch (DataAccessException error) {
            LOG.warn("Could not synchronize legacy AI review for space {}", spaceId);
        }
    }

    private Long latestReviewId(String contentType, long contentId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM starfree_ai_moderation_reviews WHERE content_type=? AND content_id=? "
                        + "ORDER BY created DESC,id DESC LIMIT 1",
                Long.class, contentType, contentId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private int spaceStatus(long spaceId) {
        try {
            Integer status = jdbc.queryForObject("SELECT status FROM starfree_space WHERE id=?",
                    Integer.class, spaceId);
            return status == null ? 0 : status;
        } catch (DataAccessException error) {
            return 0;
        }
    }

    private String galleryStatus(long galleryId) {
        try {
            String status = jdbc.queryForObject(
                    "SELECT status FROM starfree_gallery_items WHERE id=?", String.class, galleryId);
            return status == null ? "" : status;
        } catch (DataAccessException error) {
            return "";
        }
    }

    private void notifyPending(long uid, long contentId, String label) {
        notifyAuthor(uid, contentId, "你的" + label + "已进入人工复审，请耐心等待");
    }

    private void notifyAuthor(long uid, long contentId, String message) {
        if (uid <= 0) {
            return;
        }
        try {
            jdbc.update("INSERT INTO starfree_inbox(type,uid,text,touid,isread,value,created,cid) "
                            + "VALUES('system',0,?,?,0,?,?,0)",
                    truncate(message, 300), uid, contentId, Instant.now().getEpochSecond());
        } catch (DataAccessException error) {
            LOG.error("Could not notify uid {} about AI moderation", uid, error);
        }
    }

    private Config config() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT c.enabled,c.space_enabled,c.question_enabled,c.comment_enabled,"
                            + "c.gallery_enabled,c.vision_enabled,c.comment_review_time,c.comment_action,"
                            + "c.provider,c.api_url,c.api_key,c.model,c.vision_model,c.custom_prompt,"
                            + "c.last_comment_review_date,c.last_comment_review_started,"
                            + "c.last_comment_review_finished,a.spaceAudit "
                            + "FROM starfree_ai_moderation_config c LEFT JOIN "
                            + "(SELECT spaceAudit FROM starfree_apiconfig ORDER BY id LIMIT 1) a ON 1=1 "
                            + "WHERE c.id=1 LIMIT 1");
            if (rows.isEmpty()) {
                return Config.disabled();
            }
            Map<String, Object> row = rows.get(0);
            return new Config(number(value(row, "spaceAudit")) == 1,
                    number(value(row, "enabled")) == 1,
                    number(value(row, "space_enabled")) == 1,
                    number(value(row, "question_enabled")) == 1,
                    number(value(row, "comment_enabled")) == 1,
                    number(value(row, "gallery_enabled")) == 1,
                    number(value(row, "vision_enabled")) == 1,
                    "deepseek", DEEPSEEK_API_URL, text(value(row, "api_key")),
                    fallback(text(value(row, "model")), "deepseek-chat"),
                    fallback(text(value(row, "vision_model")), "deepseek-flash"),
                    truncate(text(value(row, "custom_prompt")), 2000),
                    validTime(text(value(row, "comment_review_time"))),
                    "record".equals(text(value(row, "comment_action"))) ? "record" : "hide",
                    date(value(row, "last_comment_review_date")),
                    number(value(row, "last_comment_review_started")),
                    number(value(row, "last_comment_review_finished")));
        } catch (DataAccessException error) {
            return Config.disabled();
        }
    }

    private Map<String, Object> message(String role, Object content) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("role", role);
        result.put("content", content);
        return result;
    }

    private List<Map<String, Object>> visionContent(String text, List<String> images) {
        List<Map<String, Object>> content = new ArrayList<Map<String, Object>>();
        Map<String, Object> textPart = new LinkedHashMap<String, Object>();
        textPart.put("type", "text");
        textPart.put("text", text);
        content.add(textPart);
        for (String url : images) {
            // DeepSeek cannot always reach the public image host, so hand it the bytes directly.
            String payload = imagePayload(url);
            Map<String, Object> imageUrl = new LinkedHashMap<String, Object>();
            imageUrl.put("url", payload == null ? url : payload);
            Map<String, Object> imagePart = new LinkedHashMap<String, Object>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", imageUrl);
            content.add(imagePart);
        }
        return content;
    }

    private String imagePayload(String url) {
        if (!allowedImageUrl(url)) {
            return null;
        }
        try {
            byte[] bytes = http.getForObject(url, byte[].class);
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_INLINE_IMAGE_BYTES) {
                return null;
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                LOG.warn("Moderation image is not readable, falling back to URL: {}", url);
                return null;
            }
            byte[] jpeg = shrinkImage(image);
            if (jpeg == null || jpeg.length == 0 || jpeg.length > MAX_INLINE_PAYLOAD_BYTES) {
                return null;
            }
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
        } catch (Exception error) {
            LOG.warn("Could not inline moderation image {}, falling back to URL: {}",
                    url, error.getClass().getSimpleName());
            return null;
        }
    }

    private boolean allowedImageUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return false;
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return false;
            }
            String lower = host.toLowerCase(Locale.ROOT);
            for (String suffix : INLINE_IMAGE_HOST_SUFFIXES) {
                if (lower.endsWith(suffix) || lower.equals(suffix.substring(1))) {
                    return true;
                }
            }
            return false;
        } catch (Exception error) {
            return false;
        }
    }

    private byte[] shrinkImage(BufferedImage source) throws Exception {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        double scale = Math.min(1d,
                (double) MAX_INLINE_IMAGE_DIMENSION / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage canvas = new BufferedImage(targetWidth, targetHeight,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        float[] qualities = {0.82f, 0.68f, 0.55f};
        byte[] encoded = null;
        for (float quality : qualities) {
            encoded = writeJpeg(canvas, quality);
            if (encoded.length <= MAX_INLINE_PAYLOAD_BYTES) {
                break;
            }
        }
        return encoded;
    }

    private byte[] writeJpeg(BufferedImage image, float quality) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageOutputStream stream = ImageIO.createImageOutputStream(out);
        try {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            stream.close();
            writer.dispose();
        }
        return out.toByteArray();
    }

    private boolean spaceExists(long spaceId) {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_space WHERE id=?",
                    Integer.class, spaceId);
            return count != null && count > 0;
        } catch (DataAccessException error) {
            return false;
        }
    }

    private String moderationContent(String contentType, String text, String attachment,
                                     boolean vision) {
        String label;
        if (TYPE_SPACE.equals(contentType)) {
            label = "动态文字";
        } else if (TYPE_QUESTION.equals(contentType)) {
            label = "校园提问";
        } else if (TYPE_SPACE_COMMENT.equals(contentType)) {
            label = "动态评论";
        } else if (TYPE_QA_ANSWER.equals(contentType)) {
            label = "问答回答";
        } else if (TYPE_QA_COMMENT.equals(contentType)) {
            label = "问答评论";
        } else if (TYPE_GALLERY.equals(contentType)) {
            label = "图集标题、描述和标签";
        } else {
            label = "用户内容";
        }
        StringBuilder result = new StringBuilder();
        result.append("内容类型：").append(label).append("\n待审核文字：\n").append(text);
        if (!attachment.isEmpty()) {
            result.append("\n附件说明：").append(attachment);
        }
        if (vision) {
            result.append("\n请同时审查随消息提供的全部图片。");
        } else if (!attachment.isEmpty()) {
            result.append("。只判断文字，不因附件本身拒绝。");
        }
        return result.toString();
    }

    private String attachmentSummary(int attachments, int images, boolean vision) {
        if (attachments <= 0) {
            return "";
        }
        if (images <= 0) {
            return "包含" + attachments + "个图片或视频附件，未进行视觉审核";
        }
        return "包含" + attachments + "个附件，其中" + images + "张图片"
                + (vision ? "已进行视觉审核" : "未进行视觉审核");
    }

    private int attachmentCount(String pic) {
        return pic == null || pic.trim().isEmpty() ? 0 : pic.split("\\|\\|", -1).length;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private Object value(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long number(Object value) {
        try {
            return value == null ? 0 : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private LocalDate date(Object value) {
        try {
            return value == null ? null : LocalDate.parse(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String fallback(String value, String fallback) {
        return value.isEmpty() ? fallback : value;
    }

    private String bounded(Object value, int max, String fallback) {
        String result = text(value);
        return result.isEmpty() ? fallback : truncate(result, max);
    }

    private String truncate(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), max));
    }

    private String validTime(String value) {
        return value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d") ? value : "03:30";
    }

    private enum Scope { SPACE, QUESTION, COMMENT, GALLERY }

    private static final class Config {
        private final boolean globalAudit;
        private final boolean enabled;
        private final boolean spaceEnabled;
        private final boolean questionEnabled;
        private final boolean commentEnabled;
        private final boolean galleryEnabled;
        private final boolean visionEnabled;
        private final String provider;
        private final String apiUrl;
        private final String apiKey;
        private final String model;
        private final String visionModel;
        private final String customPrompt;
        private final String commentReviewTime;
        private final String commentAction;
        private final LocalDate lastCommentReviewDate;
        private final long lastCommentReviewStarted;
        private final long lastCommentReviewFinished;

        private Config(boolean globalAudit, boolean enabled, boolean spaceEnabled,
                       boolean questionEnabled, boolean commentEnabled, boolean galleryEnabled,
                       boolean visionEnabled, String provider, String apiUrl, String apiKey,
                       String model, String visionModel, String customPrompt,
                       String commentReviewTime, String commentAction,
                       LocalDate lastCommentReviewDate, long lastCommentReviewStarted,
                       long lastCommentReviewFinished) {
            this.globalAudit = globalAudit;
            this.enabled = enabled;
            this.spaceEnabled = spaceEnabled;
            this.questionEnabled = questionEnabled;
            this.commentEnabled = commentEnabled;
            this.galleryEnabled = galleryEnabled;
            this.visionEnabled = visionEnabled;
            this.provider = provider;
            this.apiUrl = apiUrl;
            this.apiKey = apiKey;
            this.model = model;
            this.visionModel = visionModel;
            this.customPrompt = customPrompt;
            this.commentReviewTime = commentReviewTime;
            this.commentAction = commentAction;
            this.lastCommentReviewDate = lastCommentReviewDate;
            this.lastCommentReviewStarted = lastCommentReviewStarted;
            this.lastCommentReviewFinished = lastCommentReviewFinished;
        }

        private boolean available(Scope scope) {
            if (!globalAudit || !enabled || apiKey.isEmpty()) {
                return false;
            }
            if (scope == Scope.SPACE) {
                return spaceEnabled;
            }
            if (scope == Scope.QUESTION) {
                return questionEnabled;
            }
            if (scope == Scope.GALLERY) {
                return galleryEnabled;
            }
            return commentEnabled;
        }

        private static Config disabled() {
            return new Config(false, false, false, false, false, false, false, "deepseek",
                    DEEPSEEK_API_URL, "", "deepseek-chat", "deepseek-flash", "", "03:30", "hide",
                    null, 0, 0);
        }
    }

    public static final class CommentPolicy {
        private final boolean enabled;
        private final String reviewTime;
        private final String action;
        private final LocalDate lastReviewDate;
        private final long lastStarted;
        private final long lastFinished;

        CommentPolicy(boolean enabled, String reviewTime, String action,
                      LocalDate lastReviewDate, long lastStarted, long lastFinished) {
            this.enabled = enabled;
            this.reviewTime = reviewTime;
            this.action = action;
            this.lastReviewDate = lastReviewDate;
            this.lastStarted = lastStarted;
            this.lastFinished = lastFinished;
        }

        public boolean isEnabled() { return enabled; }
        public String getReviewTime() { return reviewTime; }
        public String getAction() { return action; }
        public LocalDate getLastReviewDate() { return lastReviewDate; }
        public long getLastStarted() { return lastStarted; }
        public long getLastFinished() { return lastFinished; }
    }

    public static final class Decision {
        private final boolean evaluated;
        private final boolean safe;
        private final String status;
        private final String category;
        private final String reason;
        private final long reviewId;
        private final String imageSummary;

        private Decision(boolean evaluated, boolean safe, String status, String category,
                         String reason, long reviewId, String imageSummary) {
            this.evaluated = evaluated;
            this.safe = safe;
            this.status = status;
            this.category = category;
            this.reason = reason;
            this.reviewId = reviewId;
            this.imageSummary = imageSummary == null ? "" : imageSummary;
        }

        private static Decision disabled() {
            return new Decision(false, false, "disabled", "", "", 0, "");
        }

        private static Decision error(String category, String reason) {
            return new Decision(true, false, "error", category, reason, 0, "");
        }

        public static Decision approved(String category, String reason, long reviewId) {
            return new Decision(true, true, "approved", category, reason, reviewId, "");
        }

        public static Decision rejected(String category, String reason, long reviewId) {
            return new Decision(true, false, "rejected", category, reason, reviewId, "");
        }

        private Decision withReviewId(long id) {
            return new Decision(evaluated, safe, status, category, reason, id, imageSummary);
        }

        public boolean isEvaluated() { return evaluated; }
        public boolean isSafe() { return safe; }
        public String getStatus() { return status; }
        public String getCategory() { return category; }
        public String getReason() { return reason; }
        public long getReviewId() { return reviewId; }
        public String getImageSummary() { return imageSummary; }
    }
}
