package cn.lcxqy.starfree.bot;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.economy.SigninService;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.PhpassPasswordVerifier;
import cn.lcxqy.starfree.space.SpaceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class BotService {
    private static final Logger LOG = LoggerFactory.getLogger(BotService.class);
    private static final int BIND_TOKEN_TTL_SECONDS = 900;
    private static final int MAX_DYNAMIC_TEXT = 1500;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PhpassPasswordVerifier passwords;
    private final LegacyTokenService tokens;
    private final SpaceService spaces;
    private final SigninService signin;
    private final SecureRandom random = new SecureRandom();

    public BotService(JdbcTemplate jdbc, ObjectMapper mapper, PhpassPasswordVerifier passwords,
                      LegacyTokenService tokens, SpaceService spaces, SigninService signin) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.passwords = passwords;
        this.tokens = tokens;
        this.spaces = spaces;
        this.signin = signin;
    }

    public Map<String, Object> config(Map<String, String> request) {
        requireBotSecret(request);
        Map<String, String> config = configValues();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enabled", bool(config, "enabled", false));
        response.put("dynamicOnly", true);
        response.put("deepseekModel", value(config, "deepseek_model", "deepseek-chat"));
        response.put("backendChat", true);
        response.put("syncIntervalSeconds", integer(config, "sync_interval_seconds", 45, 10, 3600));
        response.put("syncMaxImages", integer(config, "sync_max_images", 3, 0, 9));
        response.put("syncSummaryLength", integer(config, "sync_summary_length", 120, 20, 500));

        Map<String, Object> tools = new LinkedHashMap<>();
        tools.put("addSpace", bool(config, "tool_add_space", true));
        tools.put("updateProfile", bool(config, "tool_update_profile", true));
        tools.put("status", bool(config, "tool_status", true));
        tools.put("signin", bool(config, "tool_signin", true));
        response.put("tools", tools);
        response.put("groups", syncGroups());
        return response;
    }

    public Map<String, Object> bindChallenge(Map<String, String> request, String fallbackBaseUrl) {
        requireBotSecret(request);
        String platform = platform(request);
        String qqUserId = requiredText(request, "qqUserId", "缺少 QQ 用户 ID");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        jdbc.update("INSERT INTO lcxqy_bot_bind_challenge "
                        + "(bind_token,platform,qq_user_id,expires_at,created_at,ip,user_agent) "
                        + "VALUES (?,?,?,?,?,?,?)",
                token, platform, qqUserId,
                Timestamp.from(now.plusSeconds(BIND_TOKEN_TTL_SECONDS)),
                Timestamp.from(now), safe(request.get("ip"), 64), safe(request.get("userAgent"), 255));
        String baseUrl = value(configValues(), "bot_public_base_url", "");
        if (baseUrl.trim().isEmpty()) {
            baseUrl = fallbackBaseUrl == null ? "" : fallbackBaseUrl;
        }
        baseUrl = trimTrailingSlash(baseUrl);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("bindUrl", baseUrl + "/SFreeBot/bindPage?token=" + token);
        response.put("expiresInSeconds", BIND_TOKEN_TTL_SECONDS);
        return response;
    }

    public String bindPage(String bindToken) {
        Map<String, Object> challenge = activeChallenge(bindToken);
        if (challenge == null) {
            return page("绑定链接无效", "<p>这个 QQ 绑定链接不存在、已经使用或已过期。请回到 QQ 里重新发送绑定指令。</p>");
        }
        String form = "<p>请使用论坛账号登录完成 QQ 绑定。这里不会创建新的论坛登录态，也不会让其他设备下线。</p>"
                + "<form method=\"post\" action=\"/SFreeBot/bindLogin\">"
                + "<input type=\"hidden\" name=\"token\" value=\"" + html(bindToken) + "\">"
                + "<label>账号或邮箱</label><input name=\"account\" autocomplete=\"username\" required>"
                + "<label>密码</label><input name=\"password\" type=\"password\" autocomplete=\"current-password\" required>"
                + "<button type=\"submit\">绑定 QQ</button>"
                + "</form>"
                + "<p class=\"hint\">没有论坛账号时，请先回论坛注册，再重新打开绑定链接。</p>";
        return page("QQ 绑定论坛账号", form);
    }

    public String bindLogin(Map<String, String> request) {
        String bindToken = RequestValues.text(request, "token");
        String account = RequestValues.text(request, "account");
        String password = request.get("password") == null ? "" : request.get("password");
        try {
            Map<String, Object> challenge = requireActiveChallenge(bindToken);
            long uid = verifyForumPassword(account, password);
            String platform = text(challenge.get("platform"));
            String qqUserId = text(challenge.get("qq_user_id"));
            Timestamp now = Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO lcxqy_bot_bindings "
                            + "(platform,qq_user_id,forum_uid,status,created_at,updated_at,last_used_at) "
                            + "VALUES (?,?,?,?,?,?,?) "
                            + "ON DUPLICATE KEY UPDATE forum_uid=VALUES(forum_uid),"
                            + "status='active',updated_at=VALUES(updated_at),last_used_at=VALUES(last_used_at)",
                    platform, qqUserId, uid, "active", now, now, now);
            jdbc.update("UPDATE lcxqy_bot_bind_challenge SET used_at=? "
                            + "WHERE bind_token=? AND used_at IS NULL",
                    now, bindToken);
            return page("绑定成功", "<p>QQ 已绑定论坛账号，后续可以直接在 QQ 里发动态、签到和查询积分。</p>");
        } catch (IllegalArgumentException error) {
            return page("绑定失败", "<p>" + html(error.getMessage()) + "</p><p>如果没有论坛账号，请先去论坛注册。</p>");
        }
    }

    public Map<String, Object> meStatus(Map<String, String> request) {
        requireTool(request, "tool_status", "查询功能已关闭");
        Binding binding = binding(request);
        if (binding == null) {
            return unbound();
        }
        Map<String, Object> user = tokens.userById(binding.uid);
        if (user == null) {
            return unbound();
        }
        touchBinding(binding);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("bound", true);
        response.put("user", user);
        response.put("signin", signin.streakForUid(binding.uid));
        return response;
    }

    public Map<String, Object> signin(Map<String, String> request) {
        requireTool(request, "tool_signin", "签到功能已关闭");
        Binding binding = requireBinding(request);
        String requestId = RequestValues.text(request, "requestId");
        if (requestId.isEmpty()) {
            requestId = "qqbot-signin-" + binding.platform + "-" + binding.qqUserId + "-" + LocalDate.now();
        }
        final String operationId = requestId;
        return runOperation(operationId, "signin", binding, () -> {
            Map<String, Object> result = signin.signinForUid(binding.uid);
            touchBinding(binding);
            return result;
        });
    }

    public Map<String, Object> addSpace(Map<String, String> request, String ip) {
        requireTool(request, "tool_add_space", "发动态功能已关闭");
        Binding binding = requireBinding(request);
        String requestId = requiredText(request, "requestId", "缺少 requestId");
        return runOperation(requestId, "addSpace", binding, () -> {
            Map<String, String> dynamic = new LinkedHashMap<>();
            dynamic.put("type", "0");
            dynamic.put("toid", "0");
            dynamic.put("onlyMe", RequestValues.text(request, "onlyMe").equals("1") ? "1" : "0");
            dynamic.put("text", boundedText(request.get("text"), MAX_DYNAMIC_TEXT, true));
            dynamic.put("pic", RequestValues.text(request, "pic"));
            dynamic.put("topicIds", RequestValues.text(request, "topicIds"));
            boolean pending = spaces.addForBotUid(binding.uid, dynamic, ip);
            Long latestId = latestSpaceId(binding.uid);
            touchBinding(binding);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pending", pending);
            result.put("spaceId", latestId == null ? 0 : latestId);
            result.put("h5Url", h5Url(latestId == null ? 0 : latestId));
            result.put("msg", pending ? "动态已提交审核" : "动态已发布");
            return result;
        });
    }

    public Map<String, Object> updateProfile(Map<String, String> request) {
        requireTool(request, "tool_update_profile", "资料修改功能已关闭");
        Binding binding = requireBinding(request);
        String requestId = requiredText(request, "requestId", "缺少 requestId");
        return runOperation(requestId, "updateProfile", binding, () -> {
            Map<String, Object> changes = profileChanges(request, binding.uid);
            if (changes.isEmpty()) {
                throw new IllegalArgumentException("没有可修改的资料字段");
            }
            StringBuilder sql = new StringBuilder("UPDATE starfree_users SET ");
            List<Object> args = new ArrayList<>();
            int index = 0;
            for (Map.Entry<String, Object> entry : changes.entrySet()) {
                if (index++ > 0) {
                    sql.append(',');
                }
                sql.append(entry.getKey()).append("=?");
                args.add(entry.getValue());
            }
            sql.append(" WHERE uid=?");
            args.add(binding.uid);
            if (jdbc.update(sql.toString(), args.toArray()) != 1) {
                throw new IllegalArgumentException("用户不存在");
            }
            touchBinding(binding);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("changed", changes.keySet());
            response.put("user", tokens.userById(binding.uid));
            return response;
        });
    }

    public Map<String, Object> latestSpaces(Map<String, String> request) {
        requireBotSecret(request);
        String platform = platform(request);
        String groupId = RequestValues.text(request, "groupId");
        long afterId = longValue(RequestValues.text(request, "afterId"));
        int limit = integer(RequestValues.text(request, "limit"), 10, 1, 20);
        GroupSyncSetting setting = groupSetting(platform, groupId);
        int maxImages = setting.maxImages > -1
                ? setting.maxImages : integer(configValues(), "sync_max_images", 3, 0, 9);
        int summaryLength = setting.summaryLength > -1
                ? setting.summaryLength : integer(configValues(), "sync_summary_length", 120, 20, 500);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT s.id,s.uid,s.created,s.modified,s.text,s.pic,s.type,s.views,s.likes,"
                        + "u.name AS user_name,u.screenName AS user_screenName,u.avatar AS user_avatar,"
                        + "u.mail AS user_mail,campus.name AS user_campus,grade.name AS user_grade "
                        + "FROM starfree_space s "
                        + "LEFT JOIN starfree_users u ON u.uid=s.uid "
                        + "LEFT JOIN starfree_identity_options campus ON campus.id=u.campus_option_id "
                        + "LEFT JOIN starfree_identity_options grade ON grade.id=u.grade_option_id "
                        + "WHERE s.id>? AND s.status=1 AND s.onlyMe=0 AND s.type<>3 "
                        + "ORDER BY s.id ASC LIMIT ?",
                afterId, limit);
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            data.add(dynamicPayload(row, maxImages, summaryLength));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("spaces", data);
        response.put("groupEnabled", setting.enabled);
        return response;
    }

    public Map<String, Object> registerGroup(Map<String, String> request) {
        requireBotSecret(request);
        String platform = platform(request);
        String groupId = requiredText(request, "groupId", "缺少群号");
        if (!groupId.matches("\\d{5,20}")) {
            throw new IllegalArgumentException("QQ群号格式不正确");
        }
        String groupName = safe(request.get("groupName"), 128);
        String unifiedMsgOrigin = safe(request.get("unifiedMsgOrigin"), 255);
        if (unifiedMsgOrigin.trim().isEmpty()) {
            unifiedMsgOrigin = oneBotGroupOrigin(groupId);
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO lcxqy_bot_group_sync "
                        + "(platform,group_id,group_name,unified_msg_origin,enabled,created_at,updated_at) "
                        + "VALUES (?,?,?,?,1,?,?) "
                        + "ON DUPLICATE KEY UPDATE group_name=VALUES(group_name),"
                        + "unified_msg_origin=VALUES(unified_msg_origin),enabled=1,updated_at=VALUES(updated_at)",
                platform, groupId, groupName, unifiedMsgOrigin, now, now);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("groupId", groupId);
        response.put("unifiedMsgOrigin", unifiedMsgOrigin);
        response.put("enabled", true);
        return response;
    }

    private String oneBotGroupOrigin(String groupId) {
        return "lcxqy_onebot:GroupMessage:" + groupId;
    }

    public Map<String, Object> delivery(Map<String, String> request) {
        requireBotSecret(request);
        String platform = platform(request);
        String groupId = requiredText(request, "groupId", "缺少群号");
        long spaceId = longValue(requiredText(request, "spaceId", "缺少动态 ID"));
        String status = RequestValues.text(request, "status");
        boolean success = "success".equals(status);
        String messageId = safe(request.get("messageId"), 128);
        String error = safe(request.get("error"), 1000);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO lcxqy_bot_delivery_log "
                        + "(platform,group_id,target_type,target_id,message_id,status,error_message,"
                        + "delivered_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE message_id=VALUES(message_id),status=VALUES(status),"
                        + "error_message=VALUES(error_message),delivered_at=VALUES(delivered_at),"
                        + "retry_count=retry_count+1,updated_at=VALUES(updated_at)",
                platform, groupId, "space", spaceId, messageId,
                success ? "success" : "error", error, success ? now : null, now, now);
        if (success) {
            jdbc.update("UPDATE lcxqy_bot_group_sync SET cursor_space_id=GREATEST(cursor_space_id,?),"
                            + "last_success_at=?,last_error=NULL,updated_at=? WHERE platform=? AND group_id=?",
                    spaceId, now, now, platform, groupId);
        } else {
            jdbc.update("UPDATE lcxqy_bot_group_sync SET last_error=?,updated_at=? "
                            + "WHERE platform=? AND group_id=?",
                    error, now, platform, groupId);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("recorded", true);
        response.put("cursorAdvanced", success);
        return response;
    }

    public Map<String, Object> chat(Map<String, String> request) {
        requireBotSecret(request);
        Map<String, String> config = configValues();
        if (!bool(config, "enabled", false)) {
            throw new IllegalArgumentException("Bot 已关闭");
        }
        String apiKey = value(config, "deepseek_api_key", "");
        if (apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("DeepSeek API Key 未配置");
        }
        String model = value(config, "deepseek_model", "deepseek-chat");
        List<Object> messages = messages(request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", 0.6);
        Map<String, Object> result = callDeepSeek(value(config, "deepseek_api_base",
                "https://api.deepseek.com"), apiKey, payload);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("model", model);
        response.put("content", result.get("content"));
        return response;
    }

    private Map<String, Object> callDeepSeek(String apiBase, String apiKey,
                                             Map<String, Object> payload) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(trimTrailingSlash(apiBase) + "/chat/completions");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            byte[] body = mapper.writeValueAsBytes(payload);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String raw = stream == null ? "" : readAll(stream);
            if (status >= 400) {
                throw new IllegalStateException("DeepSeek 调用失败: " + preview(raw, 200));
            }
            Map<String, Object> parsed = mapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            Object choicesRaw = parsed.get("choices");
            if (!(choicesRaw instanceof List) || ((List<?>) choicesRaw).isEmpty()) {
                throw new IllegalStateException("DeepSeek 响应缺少 choices");
            }
            Object first = ((List<?>) choicesRaw).get(0);
            if (!(first instanceof Map)) {
                throw new IllegalStateException("DeepSeek 响应格式不正确");
            }
            Object message = ((Map<?, ?>) first).get("message");
            Object content = message instanceof Map ? ((Map<?, ?>) message).get("content") : null;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content == null ? "" : String.valueOf(content));
            return result;
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("DeepSeek 调用失败", error);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<Object> messages(Map<String, String> request) {
        String raw = RequestValues.text(request, "messages");
        if (!raw.isEmpty()) {
            try {
                return mapper.readValue(raw, new TypeReference<List<Object>>() {});
            } catch (Exception error) {
                throw new IllegalArgumentException("messages 不是合法 JSON");
            }
        }
        String message = boundedText(request.get("message"), 4000, false);
        if (message.isEmpty()) {
            throw new IllegalArgumentException("缺少聊天内容");
        }
        List<Object> messages = new ArrayList<>();
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("role", "system");
        system.put("content", "你是聊一下校园论坛的 QQ 助手。动态是唯一核心内容系统；不要引导用户发帖子或文章。");
        messages.add(system);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", message);
        messages.add(user);
        return messages;
    }

    private Map<String, Object> dynamicPayload(Map<String, Object> row, int maxImages,
                                               int summaryLength) {
        long id = number(value(row, "id"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("uid", number(value(row, "uid")));
        item.put("created", number(value(row, "created")));
        item.put("text", text(value(row, "text")));
        item.put("summary", preview(text(value(row, "text")), summaryLength));
        item.put("type", number(value(row, "type")));
        item.put("views", number(value(row, "views")));
        item.put("likes", number(value(row, "likes")));
        item.put("h5Url", h5Url(id));
        item.put("images", images(text(value(row, "pic")), maxImages));
        Map<String, Object> author = new LinkedHashMap<>();
        String displayName = text(value(row, "user_screenName"));
        if (displayName.isEmpty()) {
            displayName = text(value(row, "user_name"));
        }
        author.put("name", displayName);
        author.put("campus", text(value(row, "user_campus")));
        author.put("grade", text(value(row, "user_grade")));
        item.put("author", author);
        item.put("topics", topics(id));
        return item;
    }

    private List<Map<String, Object>> topics(long spaceId) {
        try {
            return jdbc.queryForList(
                    "SELECT m.mid AS id,m.name FROM starfree_space_topics st "
                            + "LEFT JOIN starfree_metas m ON m.mid=st.mid WHERE st.space_id=?",
                    spaceId);
        } catch (RuntimeException error) {
            LOG.warn("Could not read topics for dynamic {}", spaceId, error);
            return Collections.emptyList();
        }
    }

    private List<String> images(String pic, int maxImages) {
        if (pic == null || pic.trim().isEmpty() || maxImages <= 0) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        String trimmed = pic.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<Object> values = mapper.readValue(trimmed, new TypeReference<List<Object>>() {});
                for (Object value : values) {
                    addImage(result, text(value), maxImages);
                }
                return result;
            } catch (Exception ignored) {
                // Fall back to delimiter parsing below.
            }
        }
        for (String part : trimmed.split("[,|\\r\\n]+")) {
            addImage(result, part, maxImages);
        }
        return result;
    }

    private void addImage(List<String> result, String value, int maxImages) {
        String image = value == null ? "" : value.trim();
        if (!image.isEmpty() && result.size() < maxImages) {
            result.add(image);
        }
    }

    private Map<String, Object> profileChanges(Map<String, String> request, long uid) {
        Map<String, Object> changes = new LinkedHashMap<>();
        String forbidden = forbiddenWords();
        if (request.containsKey("screenName")) {
            String screenName = boundedText(request.get("screenName"), 32, false).trim();
            if (screenName.isEmpty() || hasControl(screenName)) {
                throw new IllegalArgumentException("昵称格式不正确");
            }
            if (containsForbidden(forbidden, screenName)) {
                throw new IllegalArgumentException("昵称包含违规词语");
            }
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM starfree_users WHERE screenName=? AND uid<>?",
                    Integer.class, screenName, uid);
            if (count != null && count > 0) {
                throw new IllegalArgumentException("该昵称已被占用");
            }
            changes.put("screenName", screenName);
        }
        if (request.containsKey("introduce")) {
            String introduce = boundedText(request.get("introduce"), 255, true);
            if (containsForbidden(forbidden, introduce)) {
                throw new IllegalArgumentException("简介包含违规词语");
            }
            changes.put("introduce", introduce);
        }
        if (request.containsKey("avatar")) {
            changes.put("avatar", boundedBytes(request.get("avatar"), 65535));
        }
        if (request.containsKey("campusId")) {
            long campusId = longValue(RequestValues.text(request, "campusId"));
            changes.put("campus_option_id", campusId > 0 ? requireIdentity(campusId, "campus") : null);
        }
        if (request.containsKey("gradeId")) {
            long gradeId = longValue(RequestValues.text(request, "gradeId"));
            changes.put("grade_option_id", gradeId > 0 ? requireIdentity(gradeId, "grade") : null);
        }
        return changes;
    }

    private Long requireIdentity(long id, String type) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_identity_options WHERE id=? AND type=? AND enabled=1",
                Integer.class, id, type);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("身份选项不存在或已停用");
        }
        return id;
    }

    private String forbiddenWords() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT forbidden FROM starfree_apiconfig ORDER BY id LIMIT 1");
        return rows.isEmpty() ? "" : text(value(rows.get(0), "forbidden"));
    }

    private long verifyForumPassword(String account, String password) {
        if (account.isEmpty() || password.isEmpty()) {
            throw new IllegalArgumentException("请输入论坛账号和密码");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT uid,name,password,bantime FROM starfree_users "
                        + "WHERE name=? OR mail=? LIMIT 1",
                account, account);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("账号不存在，请先去论坛注册");
        }
        Map<String, Object> row = rows.get(0);
        if (!passwords.matches(password, text(value(row, "password")))) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        long now = Instant.now().getEpochSecond();
        long bannedUntil = number(value(row, "bantime"));
        if (bannedUntil == 1 || bannedUntil > now) {
            throw new IllegalArgumentException("该账号当前不可用，请联系管理员");
        }
        return number(value(row, "uid"));
    }

    private Map<String, Object> runOperation(String requestId, String action, Binding binding,
                                             Supplier<Map<String, Object>> operation) {
        if (requestId.length() > 96) {
            throw new IllegalArgumentException("requestId 过长");
        }
        Timestamp now = Timestamp.from(Instant.now());
        try {
            jdbc.update("INSERT INTO lcxqy_bot_operation_log "
                            + "(request_id,platform,qq_user_id,forum_uid,action,status,created_at,updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?)",
                    requestId, binding.platform, binding.qqUserId, binding.uid, action,
                    "processing", now, now);
        } catch (DuplicateKeyException duplicate) {
            return replayOperation(requestId, action);
        }
        try {
            Map<String, Object> result = operation.get();
            Long targetId = number(result.get("spaceId")) > 0 ? number(result.get("spaceId")) : null;
            jdbc.update("UPDATE lcxqy_bot_operation_log SET status='success',target_type=?,"
                            + "target_id=?,updated_at=? WHERE request_id=?",
                    targetId == null ? null : "space", targetId, Timestamp.from(Instant.now()), requestId);
            result.put("replay", false);
            return result;
        } catch (RuntimeException error) {
            jdbc.update("UPDATE lcxqy_bot_operation_log SET status='failed',error_message=?,"
                            + "updated_at=? WHERE request_id=?",
                    safe(error.getMessage(), 1000), Timestamp.from(Instant.now()), requestId);
            throw error;
        }
    }

    private Map<String, Object> replayOperation(String requestId, String action) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT action,status,target_type,target_id,error_message FROM lcxqy_bot_operation_log "
                        + "WHERE request_id=? LIMIT 1", requestId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("requestId 正在处理，请稍后重试");
        }
        Map<String, Object> row = rows.get(0);
        if (!action.equals(text(value(row, "action")))) {
            throw new IllegalArgumentException("requestId 已被其他操作使用");
        }
        String status = text(value(row, "status"));
        if ("success".equals(status)) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("replay", true);
            response.put("status", "success");
            long targetId = number(value(row, "target_id"));
            if (targetId > 0) {
                response.put("spaceId", targetId);
                response.put("h5Url", h5Url(targetId));
            }
            return response;
        }
        if ("failed".equals(status)) {
            throw new IllegalArgumentException("该 requestId 曾处理失败，请换一个 requestId 重试");
        }
        throw new IllegalArgumentException("requestId 正在处理，请稍后重试");
    }

    private Binding requireBinding(Map<String, String> request) {
        Binding binding = binding(request);
        if (binding == null) {
            throw new IllegalArgumentException("QQ 尚未绑定论坛账号，请先绑定");
        }
        return binding;
    }

    private Binding binding(Map<String, String> request) {
        requireBotSecret(request);
        String platform = platform(request);
        String qqUserId = requiredText(request, "qqUserId", "缺少 QQ 用户 ID");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT platform,qq_user_id,forum_uid FROM lcxqy_bot_bindings "
                        + "WHERE platform=? AND qq_user_id=? AND status='active' LIMIT 1",
                platform, qqUserId);
        return rows.isEmpty() ? null : new Binding(platform, qqUserId, number(value(rows.get(0), "forum_uid")));
    }

    private void touchBinding(Binding binding) {
        jdbc.update("UPDATE lcxqy_bot_bindings SET last_used_at=?,updated_at=? "
                        + "WHERE platform=? AND qq_user_id=?",
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()),
                binding.platform, binding.qqUserId);
    }

    private Map<String, Object> unbound() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("bound", false);
        return response;
    }

    private List<Map<String, Object>> syncGroups() {
        return jdbc.queryForList(
                "SELECT platform,group_id AS groupId,group_name AS groupName,"
                        + "unified_msg_origin AS unifiedMsgOrigin,enabled,cursor_space_id AS cursorSpaceId,"
                        + "max_images AS maxImages,summary_length AS summaryLength "
                        + "FROM lcxqy_bot_group_sync WHERE enabled=1 ORDER BY id");
    }

    private GroupSyncSetting groupSetting(String platform, String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) {
            return GroupSyncSetting.empty();
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT enabled,max_images,summary_length FROM lcxqy_bot_group_sync "
                        + "WHERE platform=? AND group_id=? LIMIT 1",
                platform, groupId);
        if (rows.isEmpty()) {
            return GroupSyncSetting.empty();
        }
        Map<String, Object> row = rows.get(0);
        return new GroupSyncSetting(number(value(row, "enabled")) == 1,
                (int) number(value(row, "max_images")),
                (int) number(value(row, "summary_length")));
    }

    private Map<String, String> configValues() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT config_key,config_value FROM lcxqy_bot_config");
        Map<String, String> config = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            config.put(text(value(row, "config_key")), text(value(row, "config_value")));
        }
        return config;
    }

    private void requireTool(Map<String, String> request, String key, String message) {
        requireBotSecret(request);
        if (!bool(configValues(), "enabled", false)) {
            throw new IllegalArgumentException("Bot 已关闭");
        }
        if (!bool(configValues(), key, true)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireBotSecret(Map<String, String> request) {
        String provided = RequestValues.text(request, "botSecret");
        if (provided.isEmpty()) {
            provided = RequestValues.text(request, "secret");
        }
        String expected = selectBotSecret(
                value(configValues(), "bot_secret", ""),
                System.getenv("LCXQY_QQBOT_SECRET"));
        if (expected.trim().isEmpty()) {
            throw new IllegalArgumentException("Bot secret 未配置");
        }
        if (!constantEquals(provided, expected)) {
            throw new IllegalArgumentException("Bot secret 不正确");
        }
    }

    static String selectBotSecret(String configuredSecret, String environmentSecret) {
        String configured = configuredSecret == null ? "" : configuredSecret;
        if (!configured.trim().isEmpty()) {
            return configured;
        }
        return environmentSecret == null ? "" : environmentSecret;
    }

    private Map<String, Object> requireActiveChallenge(String bindToken) {
        Map<String, Object> challenge = activeChallenge(bindToken);
        if (challenge == null) {
            throw new IllegalArgumentException("绑定链接无效或已过期，请回 QQ 重新获取链接");
        }
        return challenge;
    }

    private Map<String, Object> activeChallenge(String bindToken) {
        if (bindToken == null || bindToken.trim().isEmpty()) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT bind_token,platform,qq_user_id FROM lcxqy_bot_bind_challenge "
                        + "WHERE bind_token=? AND used_at IS NULL AND expires_at>NOW() LIMIT 1",
                bindToken.trim());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long latestSpaceId(long uid) {
        List<Long> rows = jdbc.query(
                "SELECT id FROM starfree_space WHERE uid=? ORDER BY id DESC LIMIT 1",
                new Object[]{uid}, (rs, rowNum) -> rs.getLong(1));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String h5Url(long spaceId) {
        String base = trimTrailingSlash(value(configValues(), "h5_base_url", "https://prev.lcxqy.cn"));
        return base + "/#/pages/space/info?id=" + spaceId;
    }

    private String platform(Map<String, String> request) {
        String platform = RequestValues.text(request, "platform");
        return platform.isEmpty() ? "qq" : safe(platform, 32);
    }

    private String requiredText(Map<String, String> request, String key, String message) {
        String value = RequestValues.text(request, key);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String boundedText(String value, int maxLength, boolean allowControl) {
        String text = value == null ? "" : value;
        if (text.length() > maxLength || (!allowControl && hasControl(text))) {
            throw new IllegalArgumentException("参数不正确");
        }
        return text;
    }

    private String boundedBytes(String value, int maxBytes) {
        String text = value == null ? "" : value;
        if (text.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException("参数过长");
        }
        return text;
    }

    private boolean hasControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsForbidden(String forbidden, String text) {
        if (forbidden == null || forbidden.trim().isEmpty() || text == null || text.isEmpty()) {
            return false;
        }
        for (String word : forbidden.split("[,|\\r\\n]+")) {
            String normalized = word.trim();
            if (!normalized.isEmpty() && text.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean bool(Map<String, String> config, String key, boolean fallback) {
        String value = config.get(key);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return "1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim());
    }

    private int integer(Map<String, String> config, String key, int fallback, int min, int max) {
        return integer(config.get(key), fallback, min, max);
    }

    private int integer(String value, int fallback, int min, int max) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long longValue(String value) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private long number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(text(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String value(Map<String, String> config, String key, String fallback) {
        String value = config.get(key);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private Object value(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safe(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private boolean constantEquals(String left, String right) {
        byte[] a = (left == null ? "" : left).getBytes(StandardCharsets.UTF_8);
        byte[] b = (right == null ? "" : right).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private String readAll(InputStream stream) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private String preview(String text, int max) {
        if (text == null) {
            return "";
        }
        String compact = text.replace("\r", " ").replace("\n", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, max) + "...";
    }

    private String html(String value) {
        return text(value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String page(String title, String body) {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + html(title) + "</title><style>"
                + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f5f7fb;margin:0;color:#1f2937}"
                + ".box{max-width:420px;margin:9vh auto;background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:24px;box-shadow:0 12px 35px rgba(15,23,42,.08)}"
                + "h1{font-size:22px;margin:0 0 16px}p{line-height:1.7}.hint{font-size:13px;color:#6b7280}"
                + "label{display:block;margin:14px 0 6px;font-size:14px;color:#4b5563}input{box-sizing:border-box;width:100%;padding:11px;border:1px solid #d1d5db;border-radius:8px;font-size:15px}"
                + "button{margin-top:18px;width:100%;padding:11px;border:0;border-radius:8px;background:#2563eb;color:white;font-size:15px}"
                + "</style></head><body><main class=\"box\"><h1>" + html(title) + "</h1>"
                + body + "</main></body></html>";
    }

    private static final class Binding {
        private final String platform;
        private final String qqUserId;
        private final long uid;

        private Binding(String platform, String qqUserId, long uid) {
            this.platform = platform;
            this.qqUserId = qqUserId;
            this.uid = uid;
        }
    }

    private static final class GroupSyncSetting {
        private final boolean enabled;
        private final int maxImages;
        private final int summaryLength;

        private GroupSyncSetting(boolean enabled, int maxImages, int summaryLength) {
            this.enabled = enabled;
            this.maxImages = maxImages;
            this.summaryLength = summaryLength;
        }

        private static GroupSyncSetting empty() {
            return new GroupSyncSetting(false, -1, -1);
        }
    }
}
