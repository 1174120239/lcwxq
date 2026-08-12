package cn.lcxqy.starfree.space;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.push.UniPushService;
import cn.lcxqy.starfree.notify.EmailNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SpaceService {
    private static final Logger LOG = LoggerFactory.getLogger(SpaceService.class);
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_TEXT_LENGTH = 1500;
    private static final int DAILY_EXPERIENCE_LIMIT = 3;
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile(
            "!\\[[^\\]]*\\]\\((https?://[^)\\s]+)(?:\\s+[^)]*)?\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_IMAGE = Pattern.compile(
            "<img[^>]+src=[\\\"']([^\\\"']+)[\\\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final String SPACE_SELECT =
            "SELECT s.id,s.uid,s.created,s.modified,s.text,s.pic,s.type,s.views,s.likes,s.toid,"
                    + "s.status,s.onlyMe,u.uid AS user_uid,u.name AS user_name,"
                    + "u.screenName AS user_screenName,u.mail AS user_mail,"
                    + "u.avatar AS user_avatar,u.experience AS user_experience,"
                    + "u.vip AS user_vip,u.`group` AS user_group,u.bantime AS user_bantime,"
                    + "u.ip AS user_ip,u.local AS user_local,u.customize AS user_customize,"
                    + "u.introduce AS user_introduce,"
                    + "u.campus_option_id AS user_campus_id,campus.name AS user_campus,"
                    + "u.grade_option_id AS user_grade_id,grade.name AS user_grade "
                    + "FROM starfree_space s "
                    + "LEFT JOIN starfree_users u ON u.uid = s.uid "
                    + "LEFT JOIN starfree_identity_options campus ON campus.id = u.campus_option_id "
                    + "LEFT JOIN starfree_identity_options grade ON grade.id = u.grade_option_id";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LegacyTokenService tokens;
    private final LegacySpaceAbuseGuard abuseGuard;
    private final SpaceTopicService topics;
    private final UniPushService push;
    private final EmailNotificationService email;
    private SpacePollService polls;
    private AiModerationService aiModeration;

    public SpaceService(JdbcTemplate jdbc, ObjectMapper mapper, LegacyTokenService tokens,
                        LegacySpaceAbuseGuard abuseGuard) {
        this(jdbc, mapper, tokens, abuseGuard, null, null);
    }

    @Autowired
    public SpaceService(JdbcTemplate jdbc, ObjectMapper mapper, LegacyTokenService tokens,
                        LegacySpaceAbuseGuard abuseGuard, UniPushService push,
                        EmailNotificationService email) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tokens = tokens;
        this.abuseGuard = abuseGuard;
        this.topics = new SpaceTopicService(jdbc);
        this.push = push;
        this.email = email;
    }

    @Autowired
    void setPolls(SpacePollService polls) {
        this.polls = polls;
    }

    @Autowired
    void setAiModeration(AiModerationService aiModeration) {
        this.aiModeration = aiModeration;
    }

    public boolean add(Map<String, String> request, String ip) {
        return addForViewer(requireViewer(request), request, ip);
    }

    /**
     * Publishes a normal dynamic for an already authenticated forum uid.
     *
     * <p>This is used by the QQ bot binding flow. It deliberately reuses the same
     * validation, audit, anti-abuse and reward logic as the public token endpoint,
     * but does not create or consume a login token.
     */
    public boolean addForBotUid(long uid, Map<String, String> request, String ip) {
        return addForViewer(requireViewerByUid(uid), request, ip);
    }

    private boolean addForViewer(Viewer viewer, Map<String, String> request, String ip) {
        int type = validateType(RequestValues.integer(request, "type", 0));
        int toid = RequestValues.integer(request, "toid", 0);
        validateTargetParameter(type, toid);
        int onlyMe = validateOnlyMe(RequestValues.integer(request, "onlyMe", 0));
        String pic = nullableText(request, "pic");
        if (type == 4 && (pic == null || pic.isEmpty())) {
            throw new IllegalArgumentException("\u8bf7\u4e0a\u4f20\u89c6\u9891");
        }
        SpacePollService.PollDraft pollDraft = polls == null ? null
                : polls.draft(RequestValues.text(request, "poll"));
        boolean mediaAllowsEmptyText = type == 0 && ((pic != null && !pic.isEmpty())
                || pollDraft != null);
        String text = validateText(request.get("text"), mediaAllowsEmptyText, type == 3 ? 1 : 4);
        List<Integer> topicIds = topics.validateIds(RequestValues.text(request, "topicIds"));
        if (pollDraft != null && type != 0) {
            throw new IllegalArgumentException("投票只能添加到普通动态");
        }
        SpaceConfig config = config();

        Map<String, Object> user = viewer.user;
        abuseGuard.requireNotSilenced(viewer.uid);
        abuseGuard.checkRobotBurst(viewer.uid, config.banRobots == 1, config.silenceTime);
        validateForbidden(viewer.uid, config, text);

        int experience = (int) number(get(user, "experience"));
        if (experience < config.spaceMinExp) {
            throw new IllegalArgumentException("\u53d1\u5e03\u52a8\u6001\u6700\u4f4e\u8981\u6c42\u7ecf\u9a8c\u503c\u4e3a"
                    + config.spaceMinExp + "\uff0c\u4f60\u5f53\u524d\u7ecf\u9a8c\u503c" + experience);
        }
        if (config.identifysmPost == 1) {
            // The legacy UserStatus implementation always returned 0 for this check.
            throw new IllegalArgumentException("\u8bf7\u5148\u5b8c\u6210\u5b9e\u540d\u8ba4\u8bc1");
        }
        if (config.identifylvPost == 1) {
            throw new IllegalArgumentException("\u8bf7\u5148\u5b8c\u6210\u84ddV\u8ba4\u8bc1");
        }
        Map<String, Object> replyTarget = null;
        if (type == 3) {
            replyTarget = requireSpace(toid);
            Map<String, Object> parent = replyTarget;
            if (number(get(parent, "status")) == 0) {
                throw new IllegalArgumentException("\u52a8\u6001\u8fd8\u672a\u901a\u8fc7\u5ba1\u6838");
            }
            if (number(get(parent, "status")) == 2) {
                throw new IllegalArgumentException(
                        "\u52a8\u6001\u5df2\u9501\u5b9a\uff0c\u65e0\u6cd5\u8bc4\u8bba\u53ca\u8f6c\u53d1");
            }
            if (!canView(parent, viewer)) {
                throw new IllegalArgumentException("\u52a8\u6001\u4e0d\u5b58\u5728\u6216\u65e0\u6743\u67e5\u770b");
            }
        }

        long now = Instant.now().getEpochSecond();
        if (type == 3 && recentDuplicateReply(viewer.uid, toid, text, now)) {
            return false;
        }
        boolean aiEnabled = type != 3 && config.spaceAudit != 1
                && aiModeration != null && aiModeration.enabled();
        int status = (config.spaceAudit == 1 || aiEnabled) ? 0 : 1;
        int recentPosts = requireWithinPostLimit(viewer, config);
        LegacySpaceAbuseGuard.PostReservation reservation = abuseGuard.reservePost(
                viewer.uid, viewer.staff, config.postMax, recentPosts);
        boolean published = false;
        long spaceId = 0;
        try {
            String sql = "INSERT INTO starfree_space "
                    + "(uid,created,modified,text,pic,type,likes,toid,status,onlyMe) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            final String contentText = text.replace("||rn||", "\r\n");
            final String contentPic = pic;
            final int contentType = type;
            final int contentToId = toid;
            final int contentStatus = status;
            final int contentOnlyMe = onlyMe;
            int inserted = jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        sql, Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                statement.setLong(i++, viewer.uid);
                statement.setLong(i++, now);
                statement.setLong(i++, now);
                statement.setString(i++, contentText);
                statement.setString(i++, contentPic);
                statement.setInt(i++, contentType);
                statement.setInt(i++, 0);
                statement.setInt(i++, contentToId);
                statement.setInt(i++, contentStatus);
                statement.setInt(i++, contentOnlyMe);
                return statement;
            }, keyHolder);
            if (inserted != 1) {
                throw new IllegalStateException("Space insert did not affect exactly one row");
            }
            published = true;
            Number generatedId = keyHolder.getKey();
            if (generatedId != null && generatedId.longValue() > 0) {
                spaceId = generatedId.longValue();
            } else {
                // The MyISAM row already exists. Returning a failure here would invite a retry and
                // duplicate the dynamic; secondary relations stay absent/pending for repair.
                LOG.error("Space insert for uid {} succeeded but did not return an id", viewer.uid);
            }
        } finally {
            if (!published) {
                // starfree_space is MyISAM: only release the Redis quota when no row was written.
                reservation.cancel();
            }
        }

        if (!topicIds.isEmpty()) {
            try {
                if (spaceId <= 0) throw new IllegalStateException("Published Space id could not be read");
                topics.replace(spaceId, topicIds);
            } catch (RuntimeException error) {
                // The MyISAM Space row already exists. Do not make the client retry and create a
                // duplicate; log the missing secondary relation for operational repair instead.
                LOG.error("Space {} was published but its topic relations could not be saved for uid {}",
                        spaceId,
                        viewer.uid, error);
            }
        }

        if (pollDraft != null) {
            try {
                if (spaceId <= 0) throw new IllegalStateException("Published Space id could not be read");
                polls.create(spaceId, pollDraft);
            } catch (RuntimeException error) {
                LOG.error("Could not create poll for new space {}; attempting compensation", spaceId,
                        error);
                try {
                    polls.removeForSpace(spaceId);
                    removeTopicsBestEffort(spaceId);
                    if (jdbc.update("DELETE FROM starfree_space WHERE id=?", spaceId) == 1) {
                        reservation.cancel();
                        throw new IllegalStateException("投票创建失败，请重试", error);
                    }
                } catch (IllegalStateException compensated) {
                    throw compensated;
                } catch (RuntimeException compensationError) {
                    LOG.error("Could not compensate failed poll for space {}; returning success to "
                            + "avoid a duplicate dynamic", spaceId, compensationError);
                }
            }
        }

        if (aiEnabled) {
            if (spaceId > 0) {
                AiModerationService.Decision decision = aiModeration.review(spaceId, viewer.uid, text, pic);
                status = decision.isSafe() ? 1 : 0;
            } else {
                status = 0;
                sendSystemNotice(0, viewer.uid, "你的动态正在人工审核：审核记录暂时无法建立");
            }
        }

        // The location lookup in the closed backend called an external service. Preserve the
        // existing local value and only update fields that are known from this request.
        try {
            jdbc.update("UPDATE starfree_users SET posttime = ?,ip = ? WHERE uid = ?",
                    now, ip == null ? "" : ip, viewer.uid);
        } catch (RuntimeException error) {
            // The dynamic already exists in MyISAM. Returning an error would make clients retry
            // and create a duplicate, so this secondary profile update is best effort.
            LOG.error("Space was published but user activity fields could not be updated for uid {}",
                    viewer.uid, error);
        }
        if (status == 1 && config.postExp > 0) {
            try {
                grantPostExperience(viewer.uid, config.postExp, now);
            } catch (RuntimeException error) {
                // Publishing already succeeded in MyISAM. An award failure must not make the
                // client retry and create a duplicate post.
                LOG.error("Space {} was published but postExp could not be granted",
                        viewer.uid, error);
            }
        }
        if (type == 3 && status == 1 && replyTarget != null) {
            notifySpaceComment(viewer.uid, replyTarget, spaceId, text);
        }
        return status == 0;
    }

    private boolean recentDuplicateReply(long uid, int toid, String text, long now) {
        try {
            List<Map<String, Object>> duplicates = jdbc.queryForList(
                    "SELECT id FROM starfree_space WHERE uid=? AND type=3 AND toid=? "
                            + "AND text=? AND created>=? ORDER BY id DESC LIMIT 1",
                    uid, toid, text.replace("||rn||", "\r\n"), now - 20);
            return !duplicates.isEmpty();
        } catch (DataAccessException error) {
            LOG.warn("Could not check recent duplicate reply for uid {} and target {}", uid, toid,
                    error);
            return false;
        }
    }

    /**
     * 匿名动态发布（ng_music 插件功能的本土化实现）。
     *
     * <p>复用 {@link #add(Map, String)} 的全部动态校验（类型、字数、媒体、话题、经验门槛、
     * 实名/蓝V 门槛、违禁词、防刷和发布限额，均按真实用户计算），但把
     * {@code starfree_space.uid} 写成配置的专用匿名账号，onlyMe/toid 强制为公开普通动态。
     * 审核状态取“全局动态审核”与“匿名审核开关”的严格值；真实发布者写入
     * {@code starfree_anonymous_posts}，该表不对外输出；匿名动态不发放发帖经验。
     * 返回 {@code true} 表示待审核。
     */
    public boolean addAnonymous(Map<String, String> request, String ip,
                                long anonymousUid, boolean reviewRequired) {
        Viewer viewer = requireViewer(request);
        int type = validateType(RequestValues.integer(request, "type", 0));
        if (type != 0 && type != 4) {
            throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        String pic = nullableText(request, "pic");
        if (type == 4 && (pic == null || pic.isEmpty())) {
            throw new IllegalArgumentException("\u8bf7\u4e0a\u4f20\u89c6\u9891");
        }
        boolean mediaAllowsEmptyText = type == 0 && pic != null && !pic.isEmpty();
        String text = validateText(request.get("text"), mediaAllowsEmptyText, 4);
        List<Integer> topicIds = topics.validateIds(RequestValues.text(request, "topicIds"));
        SpaceConfig config = config();

        Map<String, Object> user = viewer.user;
        abuseGuard.requireNotSilenced(viewer.uid);
        abuseGuard.checkRobotBurst(viewer.uid, config.banRobots == 1, config.silenceTime);
        validateForbidden(viewer.uid, config, text);
        int experience = (int) number(get(user, "experience"));
        if (experience < config.spaceMinExp) {
            throw new IllegalArgumentException("\u53d1\u5e03\u52a8\u6001\u6700\u4f4e\u8981\u6c42\u7ecf\u9a8c\u503c\u4e3a"
                    + config.spaceMinExp + "\uff0c\u4f60\u5f53\u524d\u7ecf\u9a8c\u503c" + experience);
        }
        if (config.identifysmPost == 1) {
            throw new IllegalArgumentException("\u8bf7\u5148\u5b8c\u6210\u5b9e\u540d\u8ba4\u8bc1");
        }
        if (config.identifylvPost == 1) {
            throw new IllegalArgumentException("\u8bf7\u5148\u5b8c\u6210\u84ddV\u8ba4\u8bc1");
        }

        long now = Instant.now().getEpochSecond();
        boolean aiEnabled = config.spaceAudit != 1 && !reviewRequired
                && aiModeration != null && aiModeration.enabled();
        int status = (config.spaceAudit == 1 || reviewRequired || aiEnabled) ? 0 : 1;
        int recentPosts = requireWithinPostLimit(viewer, config);
        LegacySpaceAbuseGuard.PostReservation reservation = abuseGuard.reservePost(
                viewer.uid, viewer.staff, config.postMax, recentPosts);
        long spaceId = 0;
        try {
            String sql = "INSERT INTO starfree_space "
                    + "(uid,created,modified,text,pic,type,likes,toid,status,onlyMe) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            final String contentText = text.replace("||rn||", "\r\n");
            final String contentPic = pic;
            final int contentType = type;
            final int contentStatus = status;
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        sql, Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                statement.setLong(i++, anonymousUid);
                statement.setLong(i++, now);
                statement.setLong(i++, now);
                statement.setString(i++, contentText);
                statement.setString(i++, contentPic);
                statement.setInt(i++, contentType);
                statement.setInt(i++, 0);
                statement.setInt(i++, 0);
                statement.setInt(i++, contentStatus);
                statement.setInt(i++, 0);
                return statement;
            }, keyHolder);
            if (keyHolder.getKey() == null) {
                throw new IllegalStateException("Anonymous space insert did not return an id");
            }
            spaceId = keyHolder.getKey().longValue();
        } finally {
            if (spaceId == 0) {
                reservation.cancel();
            }
        }

        if (!topicIds.isEmpty()) {
            try {
                topics.replace(spaceId, topicIds);
            } catch (RuntimeException error) {
                // The MyISAM Space row already exists. Do not make the client retry and create a
                // duplicate; log the missing secondary relation for operational repair instead.
                LOG.error("Anonymous space {} was published but topic relations could not be saved for uid {}",
                        spaceId, viewer.uid, error);
            }
        }
        try {
            jdbc.update("INSERT INTO starfree_anonymous_posts (uid, sid, created) VALUES (?, ?, ?)",
                    viewer.uid, spaceId, now);
        } catch (RuntimeException error) {
            LOG.error("Anonymous space {} was published but owner mapping could not be saved for uid {}",
                    spaceId, viewer.uid, error);
        }
        if (aiEnabled) {
            AiModerationService.Decision decision = aiModeration.review(
                    spaceId, viewer.uid, text, pic);
            status = decision.isSafe() ? 1 : 0;
        }
        try {
            jdbc.update("UPDATE starfree_users SET posttime = ?,ip = ? WHERE uid = ?",
                    now, ip == null ? "" : ip, viewer.uid);
        } catch (RuntimeException error) {
            LOG.error("Anonymous space {} was published but user activity fields could not be updated for uid {}",
                    spaceId, viewer.uid, error);
        }
        return status == 0;
    }

    public int edit(Map<String, String> request) {
        Viewer viewer = requireViewer(request);
        long id = RequestValues.integer(request, "id", 0);
        Map<String, Object> existing = requireSpace(id);
        if (!viewer.staff && number(get(existing, "uid")) != viewer.uid) {
            throw new IllegalArgumentException("\u4f60\u6ca1\u6709\u64cd\u4f5c\u6743\u9650");
        }

        // Existing plugin rows are intentionally not editable through the rebuilt API.
        int currentType = validateType((int) number(get(existing, "type")));
        int requestedType = request.containsKey("type")
                ? validateType(RequestValues.integer(request, "type", -1)) : currentType;
        if (requestedType != currentType) {
            // The closed backend validated this field but silently left the stored type intact.
            // Rejecting a mismatch prevents a request from changing target semantics while the
            // row remains a different type.
            throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        int toid = request.containsKey("toid")
                ? RequestValues.integer(request, "toid", 0) : (int) number(get(existing, "toid"));
        validateTargetParameter(requestedType, toid);
        int onlyMe = request.containsKey("onlyMe")
                ? validateOnlyMe(RequestValues.integer(request, "onlyMe", -1))
                : (int) number(get(existing, "onlyMe"));
        String pic = request.containsKey("pic")
                ? nullableText(request, "pic") : value(get(existing, "pic"));
        if (currentType == 4 && (pic == null || pic.isEmpty())) {
            throw new IllegalArgumentException("\u8bf7\u4e0a\u4f20\u89c6\u9891");
        }
        boolean mediaAllowsEmptyText = currentType == 0 && pic != null && !pic.isEmpty();
        String text = validateText(request.get("text"), mediaAllowsEmptyText,
                currentType == 3 ? 1 : 4);
        List<Integer> topicIds = request.containsKey("topicIds")
                ? topics.validateIds(RequestValues.text(request, "topicIds")) : null;
        SpaceConfig editConfig = config();
        abuseGuard.requireNotSilenced(viewer.uid);
        abuseGuard.checkRobotBurst(
                viewer.uid, editConfig.banRobots == 1, editConfig.silenceTime);
        validateForbidden(viewer.uid, editConfig, text);

        if (currentType == 3) {
            Map<String, Object> parent = requireSpace(toid);
            if (number(get(parent, "status")) != 1 || !canView(parent, viewer)) {
                throw new IllegalArgumentException("\u76ee\u6807\u52a8\u6001\u4e0d\u53ef\u56de\u590d");
            }
        }
        long now = Instant.now().getEpochSecond();

        /*
         * Compatibility note: the old endpoint validated type but did not update it. It did
         * update uid to the editor, which transferred ownership when staff edited a post.
         * Preserve the immutable type and original owner while retaining the public contract.
         */
        int changed = jdbc.update(
                "UPDATE starfree_space SET text = ?,pic = ?,toid = ?,onlyMe = ?,modified = ? "
                        + "WHERE id = ?",
                text.replace("||rn||", "\r\n"), pic, toid, onlyMe, now, id);
        if (changed != 1) {
            throw new IllegalStateException("Space update did not affect exactly one row");
        }
        if (topicIds != null) {
            topics.replace(id, topicIds);
        }
        return changed;
    }

    public int review(Map<String, String> request) {
        Viewer viewer = requireStaff(request);
        long id = RequestValues.integer(request, "id", 0);
        int type = RequestValues.integer(request, "type", 1);
        if (type != 0 && type != 1) {
            throw new IllegalArgumentException("\u53c2\u6570\u9519\u8bef");
        }
        Map<String, Object> space = requireSpace(id);
        if (type == 1 && number(get(space, "status")) == 1) {
            throw new IllegalArgumentException("\u52a8\u6001\u5df2\u88ab\u8fdb\u884c\u76f8\u540c\u64cd\u4f5c");
        }

        int changed = type == 1
                ? jdbc.update("UPDATE starfree_space SET status = 1 WHERE id = ?", id)
                : jdbc.update("DELETE FROM starfree_space WHERE id = ?", id);
        if (changed != 1) {
            throw new IllegalStateException("Space review did not affect exactly one row");
        }
        if (type == 0) {
            removeTopicsBestEffort(id);
            if (polls != null) {
                polls.removeForSpace(id);
            }
        }
        if (type == 1 && number(get(space, "type")) == 3) {
            notifySpaceComment(number(get(space, "uid")), space, id,
                    value(get(space, "text")));
        }
        String notice = type == 1
                ? "\u4f60\u7684\u52a8\u6001\u5df2\u5ba1\u6838\u901a\u8fc7"
                : "\u4f60\u7684\u52a8\u6001\u672a\u5ba1\u6838\u901a\u8fc7\uff0c\u5df2\u88ab\u5220\u9664";
        sendSystemNotice(viewer.uid, number(get(space, "uid")), notice);
        return changed;
    }

    public int lock(Map<String, String> request) {
        Viewer viewer = requireStaff(request);
        long id = RequestValues.integer(request, "id", 0);
        int type = RequestValues.integer(request, "type", 1);
        if (type != 1 && type != 2) {
            throw new IllegalArgumentException("\u53c2\u6570\u9519\u8bef");
        }
        Map<String, Object> space = requireSpace(id);
        int oldStatus = (int) number(get(space, "status"));
        if (oldStatus == 0) {
            throw new IllegalArgumentException(
                    "\u52a8\u6001\u672a\u8fc7\u5ba1\uff0c\u6682\u65e0\u6cd5\u64cd\u4f5c");
        }
        if (oldStatus == type) {
            throw new IllegalArgumentException("\u52a8\u6001\u5df2\u88ab\u8fdb\u884c\u76f8\u540c\u64cd\u4f5c");
        }
        int changed = jdbc.update("UPDATE starfree_space SET status = ? WHERE id = ?", type, id);
        if (changed != 1) {
            throw new IllegalStateException("Space lock did not affect exactly one row");
        }
        String notice = type == 1
                ? "\u4f60\u7684\u52a8\u6001\u3010ID:" + id + "\u3011\u5df2\u88ab\u89e3\u9501"
                : "\u4f60\u7684\u52a8\u6001\u3010ID:" + id + "\u3011\u5df2\u88ab\u9501\u5b9a";
        sendSystemNotice(viewer.uid, number(get(space, "uid")), notice);
        return changed;
    }

    public Map<String, Object> info(long id, String token) {
        Viewer viewer = viewer(token, false);
        Map<String, Object> row = requireSpaceRow(id);
        if (!canView(row, viewer)) {
            throw new IllegalArgumentException("\u52a8\u6001\u4e0d\u5b58\u5728\u6216\u65e0\u6743\u67e5\u770b");
        }
        incrementViews(row, id);
        return enrich(row, viewer, config());
    }

    public SpacePage page(String searchParams, int page, int limit, String searchKey,
                          String order, int isManage, String token) {
        Map<String, Object> filters = RequestValues.jsonObject(mapper, searchParams);
        Viewer viewer = viewer(token, isManage == 1);
        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));

        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendVisibility(where, args, viewer);
        appendIntegerFilter(where, args, filters, "id", "s.id");
        appendIntegerFilter(where, args, filters, "uid", "s.uid");
        appendIntegerFilter(where, args, filters, "created", "s.created");
        appendIntegerFilter(where, args, filters, "modified", "s.modified");
        appendIntegerFilter(where, args, filters, "type", "s.type");
        appendIntegerFilter(where, args, filters, "likes", "s.likes");
        appendIntegerFilter(where, args, filters, "toid", "s.toid");
        appendIntegerFilter(where, args, filters, "status", "s.status");
        appendIntegerFilter(where, args, filters, "onlyMe", "s.onlyMe");
        List<Integer> topicFilters = topicFilterIds(filters);
        for (Integer topicId : topicFilters) {
            where.append(" AND EXISTS (SELECT 1 FROM starfree_space_topics st "
                    + "WHERE st.space_id=s.id AND st.mid=?)");
            args.add(topicId);
        }

        if (!filters.containsKey("type") && !viewer.manage) {
            where.append(" AND s.type <> 3");
        }
        String normalizedSearchKey = searchKey == null ? "" : searchKey.trim();
        if (!normalizedSearchKey.isEmpty()) {
            if ("#图集#".equals(normalizedSearchKey)) {
                // New image-only posts keep text empty; old rows still carry the legacy marker.
                where.append(" AND ((s.type = 0 AND COALESCE(s.pic, '') <> '') "
                        + "OR COALESCE(s.text, '') LIKE ?)");
                args.add("%#图集#%");
            } else if ("#视频#".equals(normalizedSearchKey)) {
                where.append(" AND (s.type = 4 OR COALESCE(s.text, '') LIKE ?)");
                args.add("%#视频#%");
            } else {
                where.append(" AND COALESCE(s.text, '') LIKE ?");
                args.add("%" + normalizedSearchKey + "%");
            }
        }

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_space s" + where,
                Integer.class, args.toArray());
        List<Object> rowArgs = new ArrayList<>(args);
        rowArgs.add((safePage - 1) * safeLimit);
        rowArgs.add(safeLimit);
        List<Map<String, Object>> rows = jdbc.queryForList(
                SPACE_SELECT + where + " ORDER BY s." + safeOrder(order)
                        + " DESC,s.id DESC LIMIT ?, ?",
                rowArgs.toArray());

        SpaceConfig config = config();
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            data.add(enrich(row, viewer, config));
        }
        return new SpacePage(data, total == null ? 0 : total);
    }

    public int delete(Map<String, String> request) {
        Viewer viewer = requireViewer(request);
        long id = RequestValues.integer(request, "id", 0);
        Map<String, Object> space = requireSpace(id);
        long owner = number(get(space, "uid"));
        // The closed controller contained this owner branch, but purview="1" blocked every
        // non-staff caller before it could run. The frontend exposes self-delete to owners, so
        // retain the intended owner-or-staff rule instead of reproducing the unreachable bug.
        if (!viewer.staff && owner != viewer.uid) {
            throw new IllegalArgumentException("\u4f60\u6ca1\u6709\u64cd\u4f5c\u6743\u9650");
        }
        // Legacy deletion removed only this row. Replies, forwards, and like logs remain and
        // resolve their missing parent through the existing "deleted or hidden" read behavior.
        int changed = deleteSpaceRow(id);
        if (viewer.staff && owner != viewer.uid) {
            sendSystemNotice(viewer.uid, owner,
                    "\u4f60\u7684\u52a8\u6001\u3010" + value(get(space, "text"))
                            + "\u3011\u5df2\u88ab\u5220\u9664");
        }
        return changed;
    }

    int deleteForModeration(long id) {
        requireSpace(id);
        return deleteSpaceRow(id);
    }

    private int deleteSpaceRow(long id) {
        int changed = jdbc.update("DELETE FROM starfree_space WHERE id = ?", id);
        if (changed != 1) {
            throw new IllegalStateException("Space delete did not affect exactly one row");
        }
        removeTopicsBestEffort(id);
        if (polls != null) {
            polls.removeForSpace(id);
        }
        return changed;
    }

    public int like(Map<String, String> request) {
        Viewer viewer = requireViewer(request);
        final long id = RequestValues.integer(request, "id", 0);
        if (id <= 0) {
            throw new IllegalArgumentException("\u8be5\u52a8\u6001\u4e0d\u5b58\u5728");
        }

        /*
         * Compatibility note: the legacy JAR does not use an IP/User-Agent/Redis TTL
         * limiter here. Its durable de-duplication key is the authenticated uid plus
         * Space id stored as starfree_userlog.type='spaceLike'. Keep that behavior so
         * likes made through either backend see the same history. The named lock also
         * serializes the check/insert/counter sequence because these legacy tables are
         * MyISAM and a Spring transaction cannot make that sequence atomic.
         */
        Integer result = jdbc.execute((ConnectionCallback<Integer>) connection -> {
            String lockName = "starfree:spaceLike:" + viewer.uid + ":" + id;
            if (!acquireLock(connection, lockName)) {
                throw new IllegalArgumentException("\u64cd\u4f5c\u592a\u9891\u7e41\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5");
            }
            try {
                Map<String, Object> target = loadLikeTarget(connection, id);
                if (target == null) {
                    throw new IllegalArgumentException("\u8be5\u52a8\u6001\u4e0d\u5b58\u5728");
                }
                if (!canView(target, viewer)) {
                    throw new IllegalArgumentException("\u52a8\u6001\u4e0d\u5b58\u5728\u6216\u65e0\u6743\u67e5\u770b");
                }
                if (hasLike(connection, viewer.uid, id)) {
                    int deleted = deleteLikeLog(connection, viewer.uid, id);
                    if (deleted < 1) {
                        throw new IllegalStateException("Space like log delete failed");
                    }
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE starfree_space SET likes = CASE WHEN COALESCE(likes, 0) > 0 "
                                    + "THEN COALESCE(likes, 0) - 1 ELSE 0 END WHERE id = ?")) {
                        update.setLong(1, id);
                        if (update.executeUpdate() != 1) {
                            insertLikeLog(connection, viewer.uid, id);
                            throw new IllegalStateException("Space unlike counter update failed");
                        }
                    } catch (SQLException error) {
                        insertLikeLog(connection, viewer.uid, id);
                        throw error;
                    }
                    return 0;
                }

                long logId = insertLikeLog(connection, viewer.uid, id);
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE starfree_space SET likes = COALESCE(likes, 0) + 1 WHERE id = ?")) {
                    update.setLong(1, id);
                    if (update.executeUpdate() != 1) {
                        compensateLikeLog(connection, logId);
                        throw new IllegalStateException("Space like counter update failed");
                    }
                } catch (SQLException error) {
                    compensateLikeLog(connection, logId);
                    throw error;
                }
                return 1;
            } finally {
                releaseLock(connection, lockName);
            }
        });
        return result == null ? 0 : result;
    }

    public SpacePage followed(int page, int limit, String token) {
        Viewer viewer = requireViewer(token);
        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        /*
         * The legacy SQL filtered only status=1, which exposed followed users'
         * onlyMe rows and reply records. A follow relationship is not permission
         * to read private Space data. Keep the count and page query on the same
         * public-only predicate so neither rows nor aggregate counts leak it.
         */
        String where = " WHERE EXISTS (SELECT 1 FROM starfree_fan f "
                + "WHERE f.uid = ? AND f.touid = s.uid) "
                + "AND s.status = 1 AND s.onlyMe = 0 AND s.type <> 3";
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_space s" + where,
                Integer.class, viewer.uid);
        List<Map<String, Object>> rows = jdbc.queryForList(
                SPACE_SELECT + where + " ORDER BY s.created DESC,s.id DESC LIMIT ?, ?",
                viewer.uid, (safePage - 1) * safeLimit, safeLimit);
        SpaceConfig config = config();
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            data.add(enrich(row, viewer, config));
        }
        return new SpacePage(data, total == null ? 0 : total);
    }

    private Map<String, Object> enrich(Map<String, Object> row, Viewer viewer,
                                       SpaceConfig config) {
        Map<String, Object> result = coreSpace(row);
        String text = value(get(result, "text"));
        if (containsForbidden(config.forbidden, text)) {
            result.put("text", "\u5185\u5bb9\u8fdd\u89c4\uff0c\u65e0\u6cd5\u5c55\u793a");
        }
        result.put("userJson", userJson(row, config));

        long id = number(get(row, "id"));
        long owner = number(get(row, "uid"));
        result.put("isLikes", viewer.uid == null ? 0 : exists(
                "SELECT COUNT(*) FROM starfree_userlog "
                        + "WHERE uid = ? AND cid = ? AND type = 'spaceLike'",
                viewer.uid, id));
        result.put("isFollow", viewer.uid == null || viewer.uid == owner ? 0 : exists(
                "SELECT COUNT(*) FROM starfree_fan WHERE uid = ? AND touid = ?",
                viewer.uid, owner));
        result.put("topics", topics.forSpace(id, viewer.uid));
        if (polls != null) {
            Map<String, Object> poll = polls.forSpace(id, viewer.uid);
            if (poll != null) {
                result.put("poll", poll);
            }
        }

        // Public counters use public rows only; pending/private replies must not leak through
        // aggregate values even when their bodies are filtered from the list.
        result.put("forward", countPublicChildren(id, 2));
        result.put("reply", countPublicChildren(id, 3));

        int type = (int) number(get(row, "type"));
        if (type == 1) {
            result.put("contentJson", contentJson(number(get(row, "toid")), viewer));
        } else if (type == 2) {
            result.put("forwardJson", relatedSpaceJson(number(get(row, "toid")), viewer));
        } else if (type == 3) {
            result.put("parentJson", relatedSpaceJson(number(get(row, "toid")), viewer));
        } else if (type == 5) {
            result.put("shopJson", shopJson(number(get(row, "toid")), viewer));
        }
        return result;
    }

    /** Returns official topics and the authenticated user's followed topics. */
    public Map<String, Object> topicCenter(String token, String searchKey) {
        Viewer viewer = viewer(token, false);
        return topics.center(viewer.uid, searchKey);
    }

    /** Creates a normal user topic and automatically follows it. */
    public Map<String, Object> createTopic(Map<String, String> request) {
        Viewer viewer = requireViewer(request);
        return topics.create(viewer.uid, RequestValues.text(request, "name"));
    }

    /** Follows or unfollows one topic. */
    public int followTopic(Map<String, String> request) {
        Viewer viewer = requireViewer(request);
        return topics.follow(viewer.uid,
                RequestValues.integer(request, "mid", 0),
                RequestValues.integer(request, "type", -1));
    }

    /** Lists one user's dynamic replies with an explicit parent visibility state. */
    public SpacePage userReplies(long uid, int page, int limit, String token) {
        if (uid <= 0) {
            Viewer current = requireViewer(token);
            uid = current.uid;
        }
        Viewer viewer = viewer(token, false);
        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        boolean canSeePrivateReplies = viewer.staff
                || (viewer.uid != null && viewer.uid.longValue() == uid);
        // Authors and moderators can review their own pending replies. Other viewers only see
        // public, approved replies, matching the visibility rules used by the dynamic feed.
        String visibleReply = canSeePrivateReplies ? "" : " AND status=1 AND onlyMe=0";
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_space WHERE uid=? AND type=3"
                        + visibleReply,
                Integer.class, uid);
        List<Map<String, Object>> rows = jdbc.queryForList(
                SPACE_SELECT + " WHERE s.uid=? AND s.type=3 "
                        + (canSeePrivateReplies ? "" : "AND s.status=1 AND s.onlyMe=0 ")
                        + "ORDER BY s.created DESC,s.id DESC LIMIT ?,?",
                uid, (safePage - 1) * safeLimit, safeLimit);
        List<Map<String, Object>> data = new ArrayList<>();
        SpaceConfig config = config();
        for (Map<String, Object> row : rows) {
            Map<String, Object> reply = coreSpace(row);
            reply.put("userJson", userJson(row, config));
            long parentId = number(get(row, "toid"));
            List<Map<String, Object>> parents = jdbc.queryForList(
                    SPACE_SELECT + " WHERE s.id=? LIMIT 1", parentId);
            if (parents.isEmpty()) {
                reply.put("originalState", "deleted");
                reply.put("original", null);
            } else if (!canView(parents.get(0), viewer)) {
                reply.put("originalState", "forbidden");
                reply.put("original", null);
            } else {
                Map<String, Object> original = coreSpace(parents.get(0));
                original.put("userJson", userJson(parents.get(0), config));
                original.put("text", preview(value(get(parents.get(0), "text")), 180));
                reply.put("originalState", "visible");
                reply.put("original", original);
            }
            data.add(reply);
        }
        return new SpacePage(data, total == null ? 0 : total);
    }

    private void removeTopicsBestEffort(long spaceId) {
        try {
            topics.remove(spaceId);
        } catch (RuntimeException error) {
            // Space rows are legacy MyISAM data. A cleanup failure must not turn a completed
            // delete/rejection into a client retry; orphan relations are invisible to counts.
            LOG.error("Could not remove topic relations for space {}", spaceId, error);
        }
    }

    private Map<String, Object> coreSpace(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        putNumber(result, "id", get(row, "id"));
        putNumber(result, "uid", get(row, "uid"));
        putNumber(result, "created", get(row, "created"));
        putNumber(result, "modified", get(row, "modified"));
        result.put("text", value(get(row, "text")));
        Object pic = get(row, "pic");
        if (pic != null) {
            result.put("pic", pic);
        }
        putNumber(result, "type", get(row, "type"));
        putNumber(result, "views", get(row, "views"));
        putNumber(result, "likes", get(row, "likes"));
        putNumber(result, "toid", get(row, "toid"));
        putNumber(result, "status", get(row, "status"));
        putNumber(result, "onlyMe", get(row, "onlyMe"));
        return result;
    }

    private Map<String, Object> userJson(Map<String, Object> row, SpaceConfig config) {
        Map<String, Object> user = new LinkedHashMap<>();
        Object uid = get(row, "user_uid");
        if (uid == null) {
            user.put("uid", 0);
            user.put("name", "\u7528\u6237\u5df2\u6ce8\u9500");
            user.put("avatar", config.avatarPrefix + "null");
            user.put("groupKey", "");
            user.put("isvip", 0);
            return user;
        }
        user.put("uid", number(uid));
        String name = value(get(row, "user_screenName"));
        if (name.isEmpty()) {
            name = value(get(row, "user_name"));
        }
        user.put("name", name);
        user.put("avatar", avatar(get(row, "user_avatar"), get(row, "user_mail"),
                config.avatarPrefix));
        user.put("experience", number(get(row, "user_experience")));
        user.put("vip", number(get(row, "user_vip")));
        user.put("isvip", isVip(get(row, "user_vip")) ? 1 : 0);
        user.put("groupKey", value(get(row, "user_group")));
        user.put("bantime", number(get(row, "user_bantime")));
        user.put("ip", value(get(row, "user_ip")));
        user.put("local", value(get(row, "user_local")));
        putNonNull(user, "customize", get(row, "user_customize"));
        putNonNull(user, "introduce", get(row, "user_introduce"));
        putNonNull(user, "campusId", get(row, "user_campus_id"));
        putNonNull(user, "campus", get(row, "user_campus"));
        putNonNull(user, "gradeId", get(row, "user_grade_id"));
        putNonNull(user, "grade", get(row, "user_grade"));
        return user;
    }

    private Map<String, Object> relatedSpaceJson(long id, Viewer viewer) {
        List<Map<String, Object>> rows = jdbc.queryForList(SPACE_SELECT + " WHERE s.id = ? LIMIT 1", id);
        if (rows.isEmpty() || !canView(rows.get(0), viewer)) {
            Map<String, Object> removed = new LinkedHashMap<>();
            removed.put("id", 0);
            removed.put("username", "");
            removed.put("text", "\u8be5\u52a8\u6001\u5df2\u88ab\u5220\u9664\u6216\u5c4f\u853d");
            return removed;
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> related = coreSpace(row);
        String name = value(get(row, "user_screenName"));
        related.put("username", name.isEmpty() ? value(get(row, "user_name")) : name);
        return related;
    }

    private Map<String, Object> contentJson(long cid, Viewer viewer) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT cid,title,text,status,authorId FROM starfree_contents WHERE cid = ? LIMIT 1",
                cid);
        if (rows.isEmpty()) {
            return removedContent();
        }
        Map<String, Object> row = rows.get(0);
        boolean owner = viewer.uid != null && viewer.uid == number(get(row, "authorId"));
        if (!"publish".equals(value(get(row, "status"))) && !viewer.staff && !owner) {
            return removedContent();
        }
        String body = value(get(row, "text"));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("cid", number(get(row, "cid")));
        content.put("title", value(get(row, "title")));
        content.put("images", images(body));
        content.put("text", preview(body, 300));
        content.put("status", value(get(row, "status")));
        return content;
    }

    private Map<String, Object> removedContent() {
        Map<String, Object> removed = new LinkedHashMap<>();
        removed.put("cid", 0);
        removed.put("title", "\u8be5\u6587\u7ae0\u5df2\u88ab\u5220\u9664\u6216\u5c4f\u853d");
        removed.put("text", "");
        return removed;
    }

    private Map<String, Object> shopJson(long id, Viewer viewer) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT sh.id,sh.title,sh.imgurl,sh.text,sh.price,sh.num,sh.type,sh.cid,"
                        + "sh.uid,sh.vipDiscount,sh.created,sh.status,sh.sellNum,sh.isMd,"
                        + "sh.sort,sh.subtype,sh.isView,sh.integral,u.name AS user_name,"
                        + "u.screenName AS user_screenName FROM starfree_shop sh "
                        + "LEFT JOIN starfree_users u ON u.uid = sh.uid WHERE sh.id = ? LIMIT 1",
                id);
        if (rows.isEmpty()) {
            return removedShop();
        }
        Map<String, Object> row = rows.get(0);
        boolean owner = viewer.uid != null && viewer.uid == number(get(row, "uid"));
        if (number(get(row, "status")) != 1 && !viewer.staff && !owner) {
            return removedShop();
        }

        // Do not copy SELECT * here. The legacy entity exposed the shop `value` field, which
        // may contain delivery data and is not needed by the attached-product card.
        Map<String, Object> shop = new LinkedHashMap<>();
        String[] fields = {"id", "title", "imgurl", "text", "price", "num", "type", "cid",
                "uid", "vipDiscount", "created", "status", "sellNum", "isMd", "sort",
                "subtype", "isView", "integral"};
        for (String field : fields) {
            putNonNull(shop, field, get(row, field));
        }
        String name = value(get(row, "user_screenName"));
        shop.put("username", name.isEmpty() ? value(get(row, "user_name")) : name);
        return shop;
    }

    private Map<String, Object> removedShop() {
        Map<String, Object> removed = new LinkedHashMap<>();
        removed.put("id", 0);
        removed.put("title", "\u8be5\u5546\u54c1\u5df2\u88ab\u5220\u9664\u6216\u5c4f\u853d");
        removed.put("username", "");
        return removed;
    }

    private void appendVisibility(StringBuilder where, List<Object> args, Viewer viewer) {
        if (viewer.staff && viewer.manage) {
            return;
        }
        if (viewer.uid == null) {
            where.append(" AND s.status = 1 AND s.onlyMe = 0");
            return;
        }
        // Logged-in users see public posts plus their own pending/private/locked records.
        where.append(" AND ((s.status = 1 AND s.onlyMe = 0) OR s.uid = ?)");
        args.add(viewer.uid);
    }

    private boolean canView(Map<String, Object> space, Viewer viewer) {
        if (viewer.staff) {
            return true;
        }
        long owner = number(get(space, "uid"));
        if (viewer.uid != null && viewer.uid == owner) {
            return true;
        }
        return number(get(space, "status")) == 1 && number(get(space, "onlyMe")) == 0;
    }

    private void appendIntegerFilter(StringBuilder where, List<Object> args,
                                     Map<String, Object> filters, String key, String column) {
        if (!filters.containsKey(key)) {
            return;
        }
        where.append(" AND ").append(column).append(" = ?");
        args.add(RequestValues.objectInteger(filters, key, 0));
    }

    private String safeOrder(String order) {
        if ("modified".equals(order) || "likes".equals(order) || "id".equals(order)) {
            return order;
        }
        return "created";
    }

    private Map<String, Object> requireSpace(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("\u52a8\u6001\u4e0d\u5b58\u5728");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,uid,created,modified,text,pic,type,views,likes,toid,status,onlyMe "
                        + "FROM starfree_space WHERE id = ? LIMIT 1", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("\u52a8\u6001\u4e0d\u5b58\u5728");
        }
        return rows.get(0);
    }

    private void incrementViews(Map<String, Object> row, long id) {
        int changed = jdbc.update(
                "UPDATE starfree_space SET views = COALESCE(views, 0) + 1 WHERE id = ?",
                id);
        if (changed == 1) {
            row.put("views", number(get(row, "views")) + 1);
        }
    }

    private Map<String, Object> requireSpaceRow(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("\u52a8\u6001\u4e0d\u5b58\u5728");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(SPACE_SELECT + " WHERE s.id = ? LIMIT 1", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("\u52a8\u6001\u4e0d\u5b58\u5728");
        }
        return rows.get(0);
    }

    private Viewer requireViewer(Map<String, String> request) {
        return requireViewer(RequestValues.text(request, "token"));
    }

    private Viewer requireViewer(String token) {
        Viewer viewer = viewer(token, false);
        if (viewer.uid == null || viewer.user == null) {
            throw new IllegalArgumentException(
                    "\u7528\u6237\u672a\u767b\u5f55\u6216Token\u9a8c\u8bc1\u5931\u8d25");
        }
        return viewer;
    }

    private Viewer requireViewerByUid(long uid) {
        if (uid <= 0) {
            throw new IllegalArgumentException(
                    "\u7528\u6237\u672a\u767b\u5f55\u6216Token\u9a8c\u8bc1\u5931\u8d25");
        }
        Map<String, Object> user = tokens.userById(uid);
        if (user == null) {
            throw new IllegalArgumentException("\u7528\u6237\u4e0d\u5b58\u5728");
        }
        boolean staff = isStaff(value(get(user, "group")));
        return new Viewer(uid, user, staff, false);
    }

    private Viewer requireStaff(Map<String, String> request) {
        Viewer viewer = requireViewer(request);
        if (!viewer.staff) {
            throw new IllegalArgumentException("\u4f60\u6ca1\u6709\u64cd\u4f5c\u6743\u9650");
        }
        return viewer;
    }

    private Viewer viewer(String token, boolean manageRequested) {
        Long resolvedUid = tokens.userId(token);
        // uid is unsigned/auto-increment in the legacy schema; 0 is never a valid session.
        Long uid = resolvedUid != null && resolvedUid > 0 ? resolvedUid : null;
        Map<String, Object> user = uid == null ? null : tokens.userById(uid);
        boolean staff = user != null && isStaff(value(get(user, "group")));
        // isManage changes reply/status visibility and therefore is accepted only for staff.
        return new Viewer(uid, user, staff, manageRequested && staff);
    }

    private boolean isStaff(String group) {
        return "administrator".equals(group) || "editor".equals(group);
    }

    private int validateType(int type) {
        // Type 6 was the plugin attachment type. Plugins are intentionally out of scope.
        if (type < 0 || type > 5) {
            throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        return type;
    }

    private void validateTargetParameter(int type, int toid) {
        if (type != 0 && type != 4 && toid <= 0) {
            throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
        }
    }

    private int validateOnlyMe(int onlyMe) {
        if (onlyMe != 0 && onlyMe != 1) {
            throw new IllegalArgumentException("onlyMe\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        return onlyMe;
    }

    private String validateText(String text, boolean mediaPresent, int minimumLength) {
        if (text == null) {
            if (mediaPresent) return "";
            throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        if (text.trim().isEmpty()) {
            if (mediaPresent) return "";
            throw new IllegalArgumentException("\u52a8\u6001\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (!mediaPresent && text.trim().length() < minimumLength) {
            throw new IllegalArgumentException("纯文字动态至少需要4个字");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("\u6700\u5927\u52a8\u6001\u5185\u5bb9\u4e3a1500\u5b57\u7b26");
        }
        return text;
    }

    private List<Integer> topicFilterIds(Map<String, Object> filters) {
        Set<Integer> result = new LinkedHashSet<>();
        if (filters.containsKey("topicId")) {
            addTopicFilter(result, filters.get("topicId"));
        }
        Object raw = filters.get("topicIds");
        if (raw instanceof Collection) {
            for (Object value : (Collection<?>) raw) {
                addTopicFilter(result, value);
            }
        } else if (raw != null) {
            for (String value : String.valueOf(raw).split("[,\\s]+")) {
                if (!value.trim().isEmpty()) {
                    addTopicFilter(result, value);
                }
            }
        }
        if (result.size() > SpaceTopicService.MAX_TOPICS_PER_SPACE) {
            throw new IllegalArgumentException("最多同时筛选3个话题");
        }
        return new ArrayList<>(result);
    }

    private void addTopicFilter(Set<Integer> result, Object value) {
        try {
            int id = Integer.parseInt(String.valueOf(value));
            if (id <= 0) throw new NumberFormatException();
            result.add(id);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("话题参数不正确");
        }
    }

    private void validateForbidden(long uid, SpaceConfig config, String text) {
        if (containsForbidden(config.forbidden, text)) {
            boolean silenced = abuseGuard.recordForbidden(uid, config.interceptTime);
            throw new IllegalArgumentException(silenced
                    ? "\u4f60\u5df2\u591a\u6b21\u53d1\u5e03\u8fdd\u7981\u8bcd\uff0c\u5df2\u88ab\u7981\u8a00"
                    : "\u5185\u5bb9\u5b58\u5728\u8fdd\u7981\u8bcd");
        }
    }

    private boolean containsForbidden(String forbidden, String text) {
        if (forbidden == null || forbidden.trim().isEmpty()
                || text == null || text.isEmpty()) {
            return false;
        }
        String[] words = forbidden.split("[,|\\r\\n]+");
        for (String word : words) {
            String value = word.trim();
            if (!value.isEmpty() && text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private int requireWithinPostLimit(Viewer viewer, SpaceConfig config) {
        if (viewer.staff || config.postMax <= 0) {
            return 0;
        }
        long since = Instant.now().getEpochSecond() - 86400;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_space WHERE uid = ? AND created >= ?",
                Integer.class, viewer.uid, since);
        if (count != null && count >= config.postMax) {
            throw new IllegalArgumentException(
                    "\u4f60\u5df2\u8d85\u8fc7\u6700\u5927\u53d1\u5e03\u6570\u91cf\u9650\u5236\uff0c\u8bf7\u60a824\u5c0f\u65f6\u540e\u518d\u64cd\u4f5c");
        }
        return count == null ? 0 : count;
    }

    private SpaceConfig config() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT spaceMinExp,spaceAudit,postMax,postExp,forbidden,"
                        + "identifysmPost,identifylvPost,banRobots,silenceTime,"
                        + "interceptTime,webinfoAvatar "
                        + "FROM starfree_apiconfig ORDER BY id LIMIT 1");
        return new SpaceConfig(rows.isEmpty()
                ? Collections.<String, Object>emptyMap() : rows.get(0));
    }

    private void sendSystemNotice(long fromUid, long toUid, String text) {
        try {
            jdbc.update("INSERT INTO starfree_inbox "
                            + "(type,uid,text,touid,isread,value,created,cid) "
                            + "VALUES (?,?,?,?,?,?,?,?)",
                    "system", fromUid, text, toUid, 0, 0,
                    Instant.now().getEpochSecond(), 0);
        } catch (DataAccessException error) {
            // Moderation already changed a MyISAM row. A notification failure must not turn a
            // successful operation into a client-visible failure and trigger a duplicate retry.
            LOG.error("Could not write space moderation notice for uid {}", toUid, error);
        }
    }

    /** Writes inbox rows for a dynamic owner and, when applicable, the replied-to commenter. */
    private void notifySpaceComment(long actorUid, Map<String, Object> replyTarget,
                                    long commentId, String text) {
        long parentType = number(get(replyTarget, "type"));
        long parentUid = parentType == 3 ? number(get(replyTarget, "uid")) : 0;
        long rootId = number(get(replyTarget, "id"));
        Map<String, Object> root = replyTarget;
        int depth = 0;
        while (number(get(root, "type")) == 3 && depth++ < 16) {
            rootId = number(get(root, "toid"));
            root = findSpaceReference(rootId);
            if (root.isEmpty()) {
                break;
            }
        }
        long ownerUid = number(get(root, "uid"));
        if (commentId <= 0) {
            return;
        }
        if (ownerUid > 0 && ownerUid != actorUid) {
            writeSpaceInbox(actorUid, ownerUid, rootId, commentId,
                    "评论了你的动态：" + previewText(text));
        }
        if (parentUid > 0 && parentUid != actorUid && parentUid != ownerUid) {
            writeSpaceInbox(actorUid, parentUid, rootId, commentId,
                    "回复了你的动态评论：" + previewText(text));
        }
    }

    private Map<String, Object> findSpaceReference(long id) {
        if (id <= 0) {
            return Collections.emptyMap();
        }
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id,uid,toid,type FROM starfree_space WHERE id=? LIMIT 1", id);
            return rows.isEmpty() ? Collections.<String, Object>emptyMap() : rows.get(0);
        } catch (DataAccessException error) {
            LOG.error("Could not resolve root dynamic {} for notification", id, error);
            return Collections.emptyMap();
        }
    }

    private void writeSpaceInbox(long fromUid, long toUid, long spaceId, long commentId, String text) {
        try {
            jdbc.update("INSERT INTO starfree_inbox "
                            + "(type,uid,text,touid,isread,value,created,cid) VALUES (?,?,?,?,?,?,?,?)",
                    "spaceComment", fromUid, text, toUid, 0, spaceId,
                    Instant.now().getEpochSecond(), commentId);
            if (push != null) {
                push.sendComment(toUid, "动态评论", text, "spaceComment:" + spaceId);
            }
            if (email != null) {
                email.sendDynamicNotice(mailOf(toUid), "\u3010LCYZ\u3011\u4f60\u7684\u52a8\u6001\u6536\u5230\u65b0\u8bc4\u8bba",
                        text);
            }
        } catch (DataAccessException error) {
            // The dynamic is already published; notification failure must not duplicate it on retry.
            LOG.error("Could not write dynamic comment notice for uid {}", toUid, error);
        }
    }

    private String mailOf(long uid) {
        try {
            String mail = jdbc.queryForObject(
                    "SELECT mail FROM starfree_users WHERE uid=? LIMIT 1", String.class, uid);
            return mail == null ? "" : mail.trim();
        } catch (DataAccessException error) {
            LOG.error("Could not resolve mail for uid {}", uid, error);
            return "";
        }
    }

    private String previewText(String text) {
        String value = text == null ? "" : text.replace("\r", " ").replace("\n", " ").trim();
        return value.length() > 120 ? value.substring(0, 120) + "..." : value;
    }

    private int countPublicChildren(long id, int type) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_space WHERE toid = ? AND type = ? "
                        + "AND status = 1 AND onlyMe = 0",
                Integer.class, id, type);
        return count == null ? 0 : count;
    }

    private int exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0 ? 1 : 0;
    }

    private boolean acquireLock(java.sql.Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 2)")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }

    private void releaseLock(java.sql.Connection connection, String name) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, name);
            statement.executeQuery().close();
        } catch (SQLException error) {
            LOG.warn("Could not explicitly release MySQL lock {}", name, error);
        }
    }

    private Map<String, Object> loadLikeTarget(java.sql.Connection connection, long id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT uid,status,onlyMe FROM starfree_space WHERE id = ? LIMIT 1")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                Map<String, Object> target = new LinkedHashMap<>();
                target.put("uid", result.getLong("uid"));
                target.put("status", result.getInt("status"));
                target.put("onlyMe", result.getInt("onlyMe"));
                return target;
            }
        }
    }

    private boolean hasLike(java.sql.Connection connection, long uid, long id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM starfree_userlog "
                        + "WHERE uid = ? AND cid = ? AND type = 'spaceLike'")) {
            statement.setLong(1, uid);
            statement.setLong(2, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    private long insertLikeLog(java.sql.Connection connection, long uid, long id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO starfree_userlog (uid,cid,type,num,created,toid) "
                        + "VALUES (?,?,'spaceLike',0,?,0)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, uid);
            statement.setLong(2, id);
            statement.setLong(3, Instant.now().getEpochSecond());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Space like log insert failed");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Space like log did not return a generated id");
                }
                return keys.getLong(1);
            }
        }
    }

    private int deleteLikeLog(java.sql.Connection connection, long uid, long id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM starfree_userlog WHERE uid = ? AND cid = ? AND type = 'spaceLike'")) {
            statement.setLong(1, uid);
            statement.setLong(2, id);
            return statement.executeUpdate();
        }
    }

    private void compensateLikeLog(java.sql.Connection connection, long logId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM starfree_userlog WHERE id = ? AND type = 'spaceLike'")) {
            statement.setLong(1, logId);
            statement.executeUpdate();
        }
    }

    private void grantPostExperience(long uid, int amount, long now) {
        jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            int day = Integer.parseInt(DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now()));
            String lockName = "starfree:postExp:" + uid + ":" + day;
            if (!acquireLock(connection, lockName)) {
                return false;
            }
            try {
                try (PreparedStatement count = connection.prepareStatement(
                        "SELECT COUNT(*) FROM starfree_userlog "
                                + "WHERE uid = ? AND cid = ? AND type = 'postExp'")) {
                    count.setLong(1, uid);
                    count.setInt(2, day);
                    try (ResultSet result = count.executeQuery()) {
                        if (result.next() && result.getInt(1) >= DAILY_EXPERIENCE_LIMIT) {
                            return false;
                        }
                    }
                }

                long logId;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO starfree_userlog (uid,cid,type,num,created,toid) "
                                + "VALUES (?,?,'postExp',?,?,0)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, uid);
                    insert.setInt(2, day);
                    insert.setInt(3, amount);
                    insert.setLong(4, now);
                    if (insert.executeUpdate() != 1) {
                        throw new SQLException("postExp log insert failed");
                    }
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("postExp log did not return a generated id");
                        }
                        logId = keys.getLong(1);
                    }
                }

                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE starfree_users SET experience = COALESCE(experience, 0) + ? "
                                + "WHERE uid = ?")) {
                    update.setInt(1, amount);
                    update.setLong(2, uid);
                    if (update.executeUpdate() != 1) {
                        compensateExperienceLog(connection, logId);
                        throw new SQLException("postExp user update failed");
                    }
                } catch (SQLException error) {
                    compensateExperienceLog(connection, logId);
                    throw error;
                }
                return true;
            } finally {
                releaseLock(connection, lockName);
            }
        });
    }

    private void compensateExperienceLog(java.sql.Connection connection, long logId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM starfree_userlog WHERE id = ? AND type = 'postExp'")) {
            statement.setLong(1, logId);
            statement.executeUpdate();
        }
    }

    private String nullableText(Map<String, String> request, String key) {
        String value = request.get(key);
        return value == null ? null : value.trim();
    }

    private List<String> images(String text) {
        List<String> result = new ArrayList<>();
        Matcher html = HTML_IMAGE.matcher(text);
        while (html.find()) {
            result.add(html.group(1));
        }
        Matcher markdown = MARKDOWN_IMAGE.matcher(text);
        while (markdown.find()) {
            result.add(markdown.group(1));
        }
        return result;
    }

    private String preview(String text, int maximum) {
        String result = text.replace("<!--markdown-->", "");
        result = HTML_IMAGE.matcher(result).replaceAll("");
        result = MARKDOWN_IMAGE.matcher(result).replaceAll("");
        result = HTML_TAG.matcher(result).replaceAll("");
        return result.length() > maximum ? result.substring(0, maximum) : result;
    }

    private String avatar(Object configuredAvatar, Object mail, String prefix) {
        String configured = value(configuredAvatar);
        if (!configured.isEmpty()) {
            return configured;
        }
        String email = value(mail).trim().toLowerCase();
        if (email.endsWith("@qq.com")) {
            return "https://q1.qlogo.cn/g?b=qq&nk="
                    + email.substring(0, email.length() - 7) + "&s=640";
        }
        return prefix + (email.isEmpty() ? "null" : md5(email));
    }

    private String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : bytes) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private boolean isVip(Object input) {
        long vip = number(input);
        return vip == 1 || vip > Instant.now().getEpochSecond();
    }

    private Object get(Map<String, Object> source, String key) {
        if (source.containsKey(key)) {
            return source.get(key);
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long number(Object input) {
        if (input instanceof Number) {
            return ((Number) input).longValue();
        }
        if (input != null) {
            try {
                return Long.parseLong(String.valueOf(input));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String value(Object input) {
        return input == null ? "" : String.valueOf(input);
    }

    private void putNumber(Map<String, Object> target, String key, Object input) {
        target.put(key, number(input));
    }

    private void putNonNull(Map<String, Object> target, String key, Object input) {
        if (input != null) {
            target.put(key, input);
        }
    }

    private static final class Viewer {
        private final Long uid;
        private final Map<String, Object> user;
        private final boolean staff;
        private final boolean manage;

        private Viewer(Long uid, Map<String, Object> user, boolean staff, boolean manage) {
            this.uid = uid;
            this.user = user;
            this.staff = staff;
            this.manage = manage;
        }
    }

    private final class SpaceConfig {
        private final int spaceMinExp;
        private final int spaceAudit;
        private final int postMax;
        private final int postExp;
        private final int identifysmPost;
        private final int identifylvPost;
        private final int banRobots;
        private final int silenceTime;
        private final int interceptTime;
        private final String forbidden;
        private final String avatarPrefix;

        private SpaceConfig(Map<String, Object> row) {
            this.spaceMinExp = (int) number(get(row, "spaceMinExp"));
            this.spaceAudit = (int) number(get(row, "spaceAudit"));
            this.postMax = (int) number(get(row, "postMax"));
            this.postExp = (int) number(get(row, "postExp"));
            this.identifysmPost = (int) number(get(row, "identifysmPost"));
            this.identifylvPost = (int) number(get(row, "identifylvPost"));
            this.banRobots = (int) number(get(row, "banRobots"));
            int configuredSilence = (int) number(get(row, "silenceTime"));
            int configuredIntercept = (int) number(get(row, "interceptTime"));
            this.silenceTime = configuredSilence > 0 ? configuredSilence : 600;
            this.interceptTime = configuredIntercept > 0 ? configuredIntercept : 3600;
            this.forbidden = value(get(row, "forbidden"));
            String prefix = value(get(row, "webinfoAvatar"));
            this.avatarPrefix = prefix.isEmpty()
                    ? "https://cravatar.cn/wp-content/themes/cravatar/assets/img/img1.png#"
                    : prefix;
        }
    }

    public static final class SpacePage {
        private final List<Map<String, Object>> data;
        private final int total;

        private SpacePage(List<Map<String, Object>> data, int total) {
            this.data = data;
            this.total = total;
        }

        public List<Map<String, Object>> getData() {
            return data;
        }

        public int getTotal() {
            return total;
        }
    }
}
