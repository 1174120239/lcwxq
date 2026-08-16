package cn.lcxqy.starfree.space;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiCommentModerationScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(AiCommentModerationScheduler.class);
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String LOCK_NAME = "starfree_ai_comment_daily_review";
    private static final int PAGE_SIZE = 200;

    private final JdbcTemplate jdbc;
    private final AiModerationService moderation;
    private final ObjectMapper mapper;

    public AiCommentModerationScheduler(JdbcTemplate jdbc, AiModerationService moderation,
                                        ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.moderation = moderation;
        this.mapper = mapper;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Shanghai")
    public void checkDue() {
        AiModerationService.CommentPolicy policy = moderation.commentPolicy();
        if (!policy.isEnabled()) {
            return;
        }
        LocalDate today = LocalDate.now(CHINA_ZONE);
        LocalTime now = LocalTime.now(CHINA_ZONE).withSecond(0).withNano(0);
        LocalTime reviewTime = LocalTime.parse(policy.getReviewTime());
        if (now.isBefore(reviewTime) || today.equals(policy.getLastReviewDate())) {
            return;
        }
        if (!acquireLock()) {
            return;
        }
        try {
            AiModerationService.CommentPolicy lockedPolicy = moderation.commentPolicy();
            if (!lockedPolicy.isEnabled() || today.equals(lockedPolicy.getLastReviewDate())) {
                return;
            }
            runDailyReview(today, lockedPolicy);
        } finally {
            releaseLock();
        }
    }

    void runDailyReview(LocalDate reviewDate, AiModerationService.CommentPolicy policy) {
        long started = Instant.now().getEpochSecond();
        long rangeStart = policy.getLastFinished() > 0
                ? policy.getLastFinished() : Math.max(0, started - 86400);
        Stats stats = new Stats();
        String lastError = "";
        jdbc.update("UPDATE starfree_ai_moderation_config SET last_comment_review_started=?,"
                        + "last_comment_review_error='',modified=? WHERE id=1",
                started, started);
        try {
            scanSpaceComments(rangeStart, started, policy.getAction(), stats);
            scanQaAnswers(rangeStart, started, policy.getAction(), stats);
            scanQaComments(rangeStart, started, policy.getAction(), stats);
        } catch (RuntimeException error) {
            lastError = truncate(error.getMessage(), 1000);
            stats.failed++;
            LOG.error("Daily AI comment review failed", error);
        }
        long finished = Instant.now().getEpochSecond();
        saveSummary(reviewDate, rangeStart, started, finished, stats, lastError);
        jdbc.update("UPDATE starfree_ai_moderation_config SET last_comment_review_date=?,"
                        + "last_comment_review_finished=?,last_comment_review_error=?,modified=? WHERE id=1",
                reviewDate.toString(), finished, lastError, finished);
    }

    private void scanSpaceComments(long rangeStart, long rangeEnd, String action, Stats stats) {
        long lastId = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id,toid,uid,text FROM starfree_space WHERE type=3 AND status=1 "
                            + "AND modified>? AND modified<=? AND id>? ORDER BY id ASC LIMIT ?",
                    rangeStart, rangeEnd, lastId, PAGE_SIZE);
            for (Map<String, Object> row : rows) {
                lastId = number(value(row, "id"));
                reviewOne(AiModerationService.TYPE_SPACE_COMMENT, lastId,
                        number(value(row, "toid")), number(value(row, "uid")),
                        text(value(row, "text")), action, stats);
                stats.spaceComments++;
            }
            if (rows.size() < PAGE_SIZE) {
                return;
            }
        }
    }

    private void scanQaAnswers(long rangeStart, long rangeEnd, String action, Stats stats) {
        long lastId = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id,question_id,uid,text FROM starfree_qa_answers WHERE status=1 "
                            + "AND modified>? AND modified<=? AND id>? ORDER BY id ASC LIMIT ?",
                    rangeStart, rangeEnd, lastId, PAGE_SIZE);
            for (Map<String, Object> row : rows) {
                lastId = number(value(row, "id"));
                reviewOne(AiModerationService.TYPE_QA_ANSWER, lastId,
                        number(value(row, "question_id")), number(value(row, "uid")),
                        text(value(row, "text")), action, stats);
                stats.qaAnswers++;
            }
            if (rows.size() < PAGE_SIZE) {
                return;
            }
        }
    }

    private void scanQaComments(long rangeStart, long rangeEnd, String action, Stats stats) {
        long lastId = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id,answer_id,uid,text FROM starfree_qa_comments WHERE status=1 "
                            + "AND modified>? AND modified<=? AND id>? ORDER BY id ASC LIMIT ?",
                    rangeStart, rangeEnd, lastId, PAGE_SIZE);
            for (Map<String, Object> row : rows) {
                lastId = number(value(row, "id"));
                reviewOne(AiModerationService.TYPE_QA_COMMENT, lastId,
                        number(value(row, "answer_id")), number(value(row, "uid")),
                        text(value(row, "text")), action, stats);
                stats.qaComments++;
            }
            if (rows.size() < PAGE_SIZE) {
                return;
            }
        }
    }

    private void reviewOne(String type, long id, long parentId, long uid, String text,
                           String action, Stats stats) {
        stats.scanned++;
        try {
            AiModerationService.Decision decision = moderation.reviewComment(
                    type, id, parentId, uid, text);
            if (!decision.isEvaluated() || "error".equals(decision.getStatus())) {
                stats.failed++;
                moderation.markContentStatus(decision.getReviewId(), 1);
                return;
            }
            if (decision.isSafe()) {
                stats.approved++;
                moderation.markContentStatus(decision.getReviewId(), 1);
                return;
            }
            stats.risk++;
            increment(stats.categories, decision.getCategory());
            boolean hidden = "hide".equals(action) && hide(type, id);
            if (hidden) {
                stats.hidden++;
                moderation.markContentStatus(decision.getReviewId(), 0);
            } else {
                moderation.markContentStatus(decision.getReviewId(), 1);
            }
        } catch (RuntimeException error) {
            stats.failed++;
            LOG.warn("Could not AI review {} {}: {}", type, id,
                    error.getClass().getSimpleName());
        }
    }

    private boolean hide(String type, long id) {
        if (AiModerationService.TYPE_SPACE_COMMENT.equals(type)) {
            return jdbc.update("UPDATE starfree_space SET status=0 WHERE id=? AND type=3 AND status=1",
                    id) == 1;
        }
        if (AiModerationService.TYPE_QA_ANSWER.equals(type)) {
            return jdbc.update("UPDATE starfree_qa_answers SET status=0,modified=? WHERE id=? AND status=1",
                    Instant.now().getEpochSecond(), id) == 1;
        }
        return jdbc.update("UPDATE starfree_qa_comments SET status=0,modified=? WHERE id=? AND status=1",
                Instant.now().getEpochSecond(), id) == 1;
    }

    private void saveSummary(LocalDate reviewDate, long rangeStart, long rangeEnd, long finished,
                             Stats stats, String lastError) {
        String categoryJson;
        try {
            categoryJson = mapper.writeValueAsString(stats.categories);
        } catch (JsonProcessingException error) {
            categoryJson = "{}";
        }
        String summary = "共巡检" + stats.scanned + "条评论内容，正常" + stats.approved
                + "条，风险" + stats.risk + "条，自动隐藏" + stats.hidden
                + "条，失败" + stats.failed + "条。动态评论" + stats.spaceComments
                + "条，问答回答" + stats.qaAnswers + "条，问答评论" + stats.qaComments + "条。";
        long created = Instant.now().getEpochSecond();
        jdbc.update("INSERT INTO starfree_ai_comment_daily_summaries"
                        + "(review_date,range_start,range_end,scanned_count,approved_count,risk_count,"
                        + "hidden_count,failed_count,space_comment_count,qa_answer_count,qa_comment_count,"
                        + "category_summary,summary_text,last_error,created,modified) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE "
                        + "range_start=VALUES(range_start),range_end=VALUES(range_end),"
                        + "scanned_count=VALUES(scanned_count),approved_count=VALUES(approved_count),"
                        + "risk_count=VALUES(risk_count),hidden_count=VALUES(hidden_count),"
                        + "failed_count=VALUES(failed_count),space_comment_count=VALUES(space_comment_count),"
                        + "qa_answer_count=VALUES(qa_answer_count),qa_comment_count=VALUES(qa_comment_count),"
                        + "category_summary=VALUES(category_summary),summary_text=VALUES(summary_text),"
                        + "last_error=VALUES(last_error),modified=VALUES(modified)",
                reviewDate.toString(), rangeStart, rangeEnd, stats.scanned, stats.approved,
                stats.risk, stats.hidden, stats.failed, stats.spaceComments, stats.qaAnswers,
                stats.qaComments, categoryJson, summary, lastError, created, finished);
    }

    private boolean acquireLock() {
        try {
            Integer result = jdbc.queryForObject("SELECT GET_LOCK(?,0)", Integer.class, LOCK_NAME);
            return result != null && result == 1;
        } catch (DataAccessException error) {
            LOG.warn("Could not acquire daily AI moderation lock");
            return false;
        }
    }

    private void releaseLock() {
        try {
            jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, LOCK_NAME);
        } catch (DataAccessException error) {
            LOG.warn("Could not release daily AI moderation lock");
        }
    }

    private void increment(Map<String, Integer> values, String key) {
        String category = key == null || key.trim().isEmpty() ? "其他" : key.trim();
        Integer current = values.get(category);
        values.put(category, current == null ? 1 : current + 1);
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String value, int maximum) {
        if (value == null) {
            return "";
        }
        return value.substring(0, Math.min(value.length(), maximum));
    }

    private static final class Stats {
        private int scanned;
        private int approved;
        private int risk;
        private int hidden;
        private int failed;
        private int spaceComments;
        private int qaAnswers;
        private int qaComments;
        private final Map<String, Integer> categories = new LinkedHashMap<String, Integer>();
    }
}
