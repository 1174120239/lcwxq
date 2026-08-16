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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiModerationService {
    public static final String TYPE_SPACE = "space";
    public static final String TYPE_QUESTION = "question";
    public static final String TYPE_SPACE_COMMENT = "space_comment";
    public static final String TYPE_QA_ANSWER = "qa_answer";
    public static final String TYPE_QA_COMMENT = "qa_comment";

    private static final Logger LOG = LoggerFactory.getLogger(AiModerationService.class);
    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions";
    private static final String SYSTEM_PROMPT = "你是校园社区内容安全审核员。必须严格审查挑起群体或地域对立、"
            + "暴露学生姓名、学号、班级、宿舍、联系方式等个人信息、违法犯罪、威胁辱骂、色情低俗、"
            + "广告垃圾、自伤或伤害他人的风险，以及其他可能伤害学生的内容。只审核提供的文字，不要"
            + "猜测图片或视频内容。只输出JSON，不要Markdown："
            + "{\"safe\":true或false,\"category\":\"正常/挑起对立/学生信息/违法违规/威胁辱骂/色情低俗/广告垃圾/自伤风险/其他\","
            + "\"reason\":\"不超过160字的具体中文理由\"}。不确定时safe必须为false并说明需人工确认。";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RestTemplate http;

    public AiModerationService(JdbcTemplate jdbc, ObjectMapper mapper,
                               @Qualifier("externalReadRestTemplate") RestTemplate http) {
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
        String attachment = attachmentSummary(pic);
        Decision decision = reviewContent(Scope.SPACE, TYPE_SPACE, spaceId, 0, authorUid,
                "realtime", normalize(text), attachment);
        syncLegacySpaceReview(spaceId, authorUid, decision);
        if (decision.isEvaluated() && !decision.isSafe()) {
            notifyAuthor(authorUid, spaceId, "你的动态未通过 AI 审核：" + decision.getReason());
        }
        return decision;
    }

    public Decision reviewQuestion(long questionId, long authorUid, String title,
                                   String description, String coverUrl) {
        String text = "问题标题：" + normalize(title) + "\n问题说明：" + normalize(description);
        Decision decision = reviewContent(Scope.QUESTION, TYPE_QUESTION, questionId, 0,
                authorUid, "realtime", text, attachmentSummary(coverUrl));
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
                "daily", normalize(text), "");
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
        long now = Instant.now().getEpochSecond();
        Long reviewId = latestReviewId(contentType, contentId);
        String action = toStatus == 1 ? "approved" : (toStatus == 2 ? "locked" : "rejected");
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

    private Decision reviewContent(Scope scope, String contentType, long contentId, long parentId,
                                   long authorUid, String source, String text,
                                   String attachmentSummary) {
        Config config = config();
        if (!config.available(scope)) {
            return Decision.disabled();
        }
        String raw = "";
        Decision decision;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.apiKey);
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("model", config.model);
            body.put("temperature", 0);
            body.put("response_format", Collections.singletonMap("type", "json_object"));
            String system = SYSTEM_PROMPT;
            if (!config.customPrompt.isEmpty()) {
                system += "\n后台补充规则（只能加严，不得放宽上述底线）：" + config.customPrompt;
            }
            body.put("messages", Arrays.asList(message("system", system),
                    message("user", moderationContent(contentType, text, attachmentSummary))));
            String response = http.postForObject(config.apiUrl,
                    new HttpEntity<Map<String, Object>>(body, headers), String.class);
            raw = response == null ? "" : response;
            decision = parse(raw);
        } catch (RuntimeException error) {
            LOG.warn("AI moderation failed for {} {}: {}", contentType, contentId,
                    error.getClass().getSimpleName());
            decision = Decision.error("AI服务异常", "AI审核暂时不可用，内容已保持隐藏并转人工确认");
        }

        long reviewId;
        try {
            reviewId = persist(contentType, contentId, parentId, authorUid, source, text,
                    attachmentSummary, config, decision, truncate(raw, 30000));
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
                    category, reason, 0);
        } catch (Exception error) {
            return Decision.error("AI输出异常", "AI审核结果无法解析，内容已保持隐藏并转人工确认");
        }
    }

    private long persist(String contentType, long contentId, long parentId, long authorUid,
                         String source, String snapshot, String attachmentSummary, Config config,
                         Decision decision, String raw) {
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
                decision.category, decision.reason, config.provider, config.model, raw,
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
                            + "c.comment_review_time,c.comment_action,c.provider,c.api_url,c.api_key,"
                            + "c.model,c.custom_prompt,c.last_comment_review_date,"
                            + "c.last_comment_review_started,c.last_comment_review_finished,a.spaceAudit "
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
                    "deepseek", DEEPSEEK_API_URL, text(value(row, "api_key")),
                    fallback(text(value(row, "model")), "deepseek-chat"),
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

    private Map<String, String> message(String role, String content) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("role", role);
        result.put("content", content);
        return result;
    }

    private String moderationContent(String contentType, String text, String attachment) {
        String label;
        if (TYPE_SPACE.equals(contentType)) {
            label = "动态文字";
        } else if (TYPE_QUESTION.equals(contentType)) {
            label = "校园提问";
        } else if (TYPE_SPACE_COMMENT.equals(contentType)) {
            label = "动态评论";
        } else if (TYPE_QA_ANSWER.equals(contentType)) {
            label = "问答回答";
        } else {
            label = "问答评论";
        }
        return "内容类型：" + label + "\n待审核文字：\n" + text
                + (attachment.isEmpty() ? "" : "\n附件说明：" + attachment
                + "。只判断文字，不因附件本身拒绝。");
    }

    private String attachmentSummary(String value) {
        String raw = normalize(value);
        if (raw.isEmpty()) {
            return "";
        }
        int count = raw.contains("||") ? raw.split("\\|\\|", -1).length : 1;
        return "包含" + count + "个图片或视频附件，未进行视觉识别";
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

    private enum Scope { SPACE, QUESTION, COMMENT }

    private static final class Config {
        private final boolean globalAudit;
        private final boolean enabled;
        private final boolean spaceEnabled;
        private final boolean questionEnabled;
        private final boolean commentEnabled;
        private final String provider;
        private final String apiUrl;
        private final String apiKey;
        private final String model;
        private final String customPrompt;
        private final String commentReviewTime;
        private final String commentAction;
        private final LocalDate lastCommentReviewDate;
        private final long lastCommentReviewStarted;
        private final long lastCommentReviewFinished;

        private Config(boolean globalAudit, boolean enabled, boolean spaceEnabled,
                       boolean questionEnabled, boolean commentEnabled, String provider,
                       String apiUrl, String apiKey, String model, String customPrompt,
                       String commentReviewTime, String commentAction,
                       LocalDate lastCommentReviewDate, long lastCommentReviewStarted,
                       long lastCommentReviewFinished) {
            this.globalAudit = globalAudit;
            this.enabled = enabled;
            this.spaceEnabled = spaceEnabled;
            this.questionEnabled = questionEnabled;
            this.commentEnabled = commentEnabled;
            this.provider = provider;
            this.apiUrl = apiUrl;
            this.apiKey = apiKey;
            this.model = model;
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
            return commentEnabled;
        }

        private static Config disabled() {
            return new Config(false, false, false, false, false, "deepseek",
                    DEEPSEEK_API_URL, "", "deepseek-chat", "", "03:30", "hide",
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

        private Decision(boolean evaluated, boolean safe, String status, String category,
                         String reason, long reviewId) {
            this.evaluated = evaluated;
            this.safe = safe;
            this.status = status;
            this.category = category;
            this.reason = reason;
            this.reviewId = reviewId;
        }

        private static Decision disabled() {
            return new Decision(false, false, "disabled", "", "", 0);
        }

        private static Decision error(String category, String reason) {
            return new Decision(true, false, "error", category, reason, 0);
        }

        public static Decision approved(String category, String reason, long reviewId) {
            return new Decision(true, true, "approved", category, reason, reviewId);
        }

        public static Decision rejected(String category, String reason, long reviewId) {
            return new Decision(true, false, "rejected", category, reason, reviewId);
        }

        private Decision withReviewId(long id) {
            return new Decision(evaluated, safe, status, category, reason, id);
        }

        public boolean isEvaluated() { return evaluated; }
        public boolean isSafe() { return safe; }
        public String getStatus() { return status; }
        public String getCategory() { return category; }
        public String getReason() { return reason; }
        public long getReviewId() { return reviewId; }
    }
}
