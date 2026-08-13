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
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiModerationService {
    private static final Logger LOG = LoggerFactory.getLogger(AiModerationService.class);
    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions";
    private static final String SYSTEM_PROMPT = "你是校园社区内容安全审核员。必须严格审查挑起群体或地域对立、"
            + "暴露学生姓名学号班级宿舍联系方式等个人信息、违法犯罪、威胁辱骂、色情低俗、"
            + "广告垃圾和其他可能伤害学生的内容。只输出JSON，不要Markdown："
            + "{\"safe\":true或false,\"category\":\"正常/挑起对立/学生信息/违法违规/威胁辱骂/色情低俗/广告垃圾/其他\","
            + "\"reason\":\"不超过80字的中文理由\"}。不确定时safe必须为false并说明需人工确认。";

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
        Config config = config();
        return config.enabled && !config.apiKey.isEmpty();
    }

    public Decision review(long spaceId, long authorUid, String text, String pic) {
        Config config = config();
        if (!config.enabled || config.apiKey.isEmpty()) {
            return Decision.disabled();
        }
        Decision decision;
        String raw = "";
        if (pic != null && !pic.trim().isEmpty()) {
            decision = Decision.pending("附件待确认", "动态包含图片或视频，需要人工确认附件内容");
            if (!persistBestEffort(spaceId, authorUid, config, decision, "")) {
                decision = Decision.pending("审核记录异常", "审核记录保存失败，动态保持待审核状态");
            }
            notifyAuthor(authorUid, spaceId, decision.reason);
            return decision;
        }
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
                    message("user", content(text, pic))));
            String response = http.postForObject(config.apiUrl,
                    new HttpEntity<Map<String, Object>>(body, headers), String.class);
            raw = response == null ? "" : response;
            decision = parse(raw);
        } catch (RuntimeException error) {
            LOG.warn("AI moderation failed for space {}: {}", spaceId,
                    error.getClass().getSimpleName());
            decision = Decision.pending("AI服务异常", "AI审核暂不可用，已转人工审核");
        }
        Decision persistedDecision = decision.safe ? decision.pendingPublication() : decision;
        if (!persistBestEffort(spaceId, authorUid, config, persistedDecision, truncate(raw, 8000))) {
            decision = Decision.pending("审核记录异常", "审核记录保存失败，动态保持待审核状态");
            notifyAuthor(authorUid, spaceId, decision.reason);
            return decision;
        }
        if (decision.safe) {
            try {
                if (jdbc.update("UPDATE starfree_space SET status=1 WHERE id=? AND status=0", spaceId) != 1) {
                    decision = Decision.pending("发布状态异常", "自动发布失败，已转人工审核");
                    markPendingBestEffort(spaceId, decision);
                    notifyAuthor(authorUid, spaceId, decision.reason);
                } else {
                    markApprovedBestEffort(spaceId);
                }
            } catch (DataAccessException error) {
                decision = Decision.pending("发布状态异常", "自动发布失败，已转人工审核");
                markPendingBestEffort(spaceId, decision);
                notifyAuthor(authorUid, spaceId, decision.reason);
            }
        } else {
            notifyAuthor(authorUid, spaceId, decision.reason);
        }
        return decision;
    }

    private boolean persistBestEffort(long spaceId, long authorUid, Config config,
                                      Decision decision, String raw) {
        try {
            persist(spaceId, authorUid, config, decision, raw);
            return true;
        } catch (DataAccessException error) {
            LOG.error("Could not persist AI moderation for space {}", spaceId, error);
            return false;
        }
    }

    private void markPendingBestEffort(long spaceId, Decision decision) {
        try {
            jdbc.update("UPDATE starfree_space_ai_reviews SET status='pending',risk_category=?,"
                            + "reason=?,modified=? WHERE space_id=?",
                    decision.category, decision.reason, Instant.now().getEpochSecond(), spaceId);
        } catch (DataAccessException error) {
            LOG.error("Could not restore pending AI review for space {}", spaceId, error);
        }
    }

    private void markApprovedBestEffort(long spaceId) {
        try {
            jdbc.update("UPDATE starfree_space_ai_reviews SET status='approved',modified=? "
                            + "WHERE space_id=? AND status='pending'",
                    Instant.now().getEpochSecond(), spaceId);
        } catch (DataAccessException error) {
            // The dynamic is already public. Queue reconciliation will finalize this audit row.
            LOG.error("Could not finalize approved AI review for space {}", spaceId, error);
        }
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
            if (!(first instanceof Map)) throw new IllegalArgumentException("choice invalid");
            Object message = ((Map<?, ?>) first).get("message");
            if (!(message instanceof Map)) throw new IllegalArgumentException("message invalid");
            String content = String.valueOf(((Map<?, ?>) message).get("content")).trim();
            Map<String, Object> result = mapper.readValue(content,
                    new TypeReference<Map<String, Object>>() { });
            if (!(result.get("safe") instanceof Boolean)) {
                throw new IllegalArgumentException("safe invalid");
            }
            boolean safe = Boolean.TRUE.equals(result.get("safe"));
            String category = bounded(result.get("category"), 40, safe ? "正常" : "其他");
            String reason = bounded(result.get("reason"), 80,
                    safe ? "未发现明显风险" : "内容需要人工确认");
            return new Decision(true, safe, safe ? "approved" : "pending", category, reason);
        } catch (Exception error) {
            return Decision.pending("AI输出异常", "AI审核结果无法确认，已转人工审核");
        }
    }

    private void persist(long spaceId, long authorUid, Config config, Decision decision, String raw) {
        long now = Instant.now().getEpochSecond();
        jdbc.update("INSERT INTO starfree_space_ai_reviews"
                        + "(space_id,author_uid,status,risk_category,reason,provider,model,raw_response,"
                        + "reviewer_uid,review_note,created,modified) VALUES(?,?,?,?,?,?,?,?,NULL,'',?,?) "
                        + "ON DUPLICATE KEY UPDATE status=VALUES(status),risk_category=VALUES(risk_category),"
                        + "reason=VALUES(reason),provider=VALUES(provider),model=VALUES(model),"
                        + "raw_response=VALUES(raw_response),modified=VALUES(modified)",
                spaceId, authorUid, decision.status, decision.category, decision.reason,
                config.provider, config.model, raw, now, now);
    }

    private void notifyAuthor(long uid, long spaceId, String reason) {
        try {
            jdbc.update("INSERT INTO starfree_inbox(type,uid,text,touid,isread,value,created,cid) "
                            + "VALUES('system',0,?,?,0,?,?,0)",
                    "你的动态正在人工审核：" + truncate(reason, 80), uid, spaceId,
                    Instant.now().getEpochSecond());
        } catch (DataAccessException error) {
            LOG.error("Could not notify uid {} about AI moderation", uid, error);
        }
    }

    private Config config() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(
                    "SELECT enabled,provider,api_url,api_key,model,custom_prompt "
                            + "FROM starfree_ai_moderation_config WHERE id=1 LIMIT 1");
        } catch (DataAccessException error) {
            return Config.disabled();
        }
        if (rows.isEmpty()) return Config.disabled();
        Map<String, Object> row = rows.get(0);
        return new Config(number(value(row, "enabled")) == 1,
                "deepseek", DEEPSEEK_API_URL,
                text(value(row, "api_key")), fallback(text(value(row, "model")), "deepseek-chat"),
                truncate(text(value(row, "custom_prompt")), 2000));
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("role", role); result.put("content", content); return result;
    }
    private String content(String text, String pic) {
        int imageCount = pic == null || pic.trim().isEmpty() ? 0 : pic.split("\\|\\|").length;
        return "动态正文：\n" + (text == null ? "" : text) + "\n附件数量：" + imageCount
                + "。无法确认附件内容时应转人工。";
    }
    private Object value(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet())
            if (key.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        return null;
    }
    private long number(Object value) { try { return value == null ? 0 : Long.parseLong(String.valueOf(value)); } catch (NumberFormatException e) { return 0; } }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String fallback(String value, String fallback) { return value.isEmpty() ? fallback : value; }
    private String bounded(Object value, int max, String fallback) { String text=text(value); return text.isEmpty() ? fallback : truncate(text,max); }
    private String truncate(String value, int max) { return value == null ? "" : value.substring(0, Math.min(value.length(), max)); }

    private static final class Config {
        private final boolean enabled; private final String provider; private final String apiUrl;
        private final String apiKey; private final String model; private final String customPrompt;
        private Config(boolean enabled,String provider,String apiUrl,String apiKey,String model,String customPrompt){this.enabled=enabled;this.provider=provider;this.apiUrl=apiUrl;this.apiKey=apiKey;this.model=model;this.customPrompt=customPrompt;}
        private static Config disabled(){return new Config(false,"deepseek",DEEPSEEK_API_URL,"","deepseek-chat","");}
    }
    public static final class Decision {
        private final boolean evaluated; private final boolean safe; private final String status;
        private final String category; private final String reason;
        private Decision(boolean evaluated,boolean safe,String status,String category,String reason){this.evaluated=evaluated;this.safe=safe;this.status=status;this.category=category;this.reason=reason;}
        private static Decision disabled(){return new Decision(false,false,"disabled","","");}
        private static Decision pending(String category,String reason){return new Decision(true,false,"pending",category,reason);}
        private Decision pendingPublication(){return new Decision(evaluated,safe,"pending",category,reason);}
        public boolean isEvaluated(){return evaluated;} public boolean isSafe(){return safe;} public String getReason(){return reason;}
    }
}
