package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.cache.LegacyProjectionCacheInvalidator;
import cn.lcxqy.starfree.economy.EconomyLockExecutor;
import cn.lcxqy.starfree.security.LegacyRedisKeyStore;
import cn.lcxqy.starfree.security.LegacySessionBridge;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.PhpassPasswordVerifier;
import cn.lcxqy.starfree.security.SessionTokenGenerator;
import cn.lcxqy.starfree.security.StaffAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User discovery, staff account management, invitations, bans, and system messages.
 *
 * <p>These operations used to live in one very large controller. The replacement keeps all SQL
 * here so controllers only preserve the wire protocol. User and violation lists are read-only and
 * use bounded pagination. Destructive or balance-adjacent operations authenticate again inside the
 * service and revoke legacy sessions after the authoritative MySQL write.
 */
@Service
public class UserAdministrationService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_INVITATIONS = 100;
    private static final long MAX_BAN_SECONDS = 10L * 365 * 86400;
    private static final String RANDOM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Set<String> GROUPS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "administrator", "editor", "contributor", "subscriber", "visitor")));

    private final JdbcTemplate jdbc;
    private final LegacyTokenService tokens;
    private final StaffAccess access;
    private final LegacySessionBridge sessions;
    private final LegacyRegistrationRedis verificationCodes;
    private final LegacyRedisKeyStore redisKeys;
    private final SessionTokenGenerator tokenGenerator;
    private final PhpassPasswordVerifier passwords;
    private final EconomyLockExecutor economyLock;
    private final ObjectMapper mapper;
    private final LegacyProjectionCacheInvalidator caches;
    private final SecureRandom random = new SecureRandom();

    public UserAdministrationService(
            JdbcTemplate jdbc,
            LegacyTokenService tokens,
            StaffAccess access,
            LegacySessionBridge sessions,
            LegacyRegistrationRedis verificationCodes,
            LegacyRedisKeyStore redisKeys,
            SessionTokenGenerator tokenGenerator,
            PhpassPasswordVerifier passwords,
            EconomyLockExecutor economyLock,
            ObjectMapper mapper,
            LegacyProjectionCacheInvalidator caches) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.access = access;
        this.sessions = sessions;
        this.verificationCodes = verificationCodes;
        this.redisKeys = redisKeys;
        this.tokenGenerator = tokenGenerator;
        this.passwords = passwords;
        this.economyLock = economyLock;
        this.mapper = mapper;
        this.caches = caches;
    }

    /**
     * Lists users with legacy search parameters while applying a private-field boundary.
     * Anonymous callers receive public profile fields; staff additionally receive mail, phone,
     * balances and management state. Unsupported filters and order names are ignored.
     */
    public Page users(Map<String, String> request) {
        Map<String, Object> search = RequestValues.jsonObject(mapper, request.get("searchParams"));
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        int limit = bounded(RequestValues.integer(request, "limit", 15), MAX_PAGE_SIZE);
        String searchKey = RequestValues.text(request, "searchKey");
        String order = userOrder(RequestValues.text(request, "order"));

        Long viewerId = tokens.userId(RequestValues.text(request, "token"));
        Map<String, Object> viewer = viewerId == null ? null : tokens.userById(viewerId);
        boolean staff = viewer != null && isStaff(value(viewer.get("group")));

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        addLongFilter(where, args, search, "uid", "uid");
        addLongFilter(where, args, search, "invitationUser", "invitationUser");
        String group = firstText(search, "groupKey", "group");
        if (!group.isEmpty() && GROUPS.contains(group)) {
            where.append(" AND `group`=?");
            args.add(group);
        }
        if (truthy(search.get("vip"))) {
            where.append(" AND (vip=1 OR vip>UNIX_TIMESTAMP())");
        }
        if (truthy(search.get("bantime"))) {
            where.append(" AND (bantime=1 OR bantime>UNIX_TIMESTAMP())");
        }
        if (!searchKey.isEmpty()) {
            where.append(" AND CONCAT(IFNULL(name,''),IFNULL(screenName,''),uid,IFNULL(mail,'')) LIKE ?");
            args.add("%" + searchKey + "%");
        }

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_users" + where, Integer.class, args.toArray());
        List<Object> rowArgs = new ArrayList<>(args);
        rowArgs.add((page - 1) * limit);
        rowArgs.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT uid,name,mail,url,screenName,created,activated,logged,`group`,introduce,"
                        + "assets,address,pay,customize,vip,experience,avatar,clientId,bantime,"
                        + "posttime,ip,local,phone,userBg,invitationCode,invitationUser,points,"
                        + "campus_option_id AS campusId,grade_option_id AS gradeId,"
                        + "(SELECT name FROM starfree_identity_options io "
                        + "WHERE io.id=starfree_users.campus_option_id) AS campus,"
                        + "(SELECT name FROM starfree_identity_options io "
                        + "WHERE io.id=starfree_users.grade_option_id) AS grade "
                        + "FROM starfree_users" + where + " ORDER BY " + order
                        + " DESC,uid DESC LIMIT ?,?",
                rowArgs.toArray());

        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            long uid = number(get(item, "uid"));
            item.put("groupKey", value(get(item, "group")));
            item.put("isvip", vipState(get(item, "vip")));
            item.put("isFollow", viewerId == null ? 0 : followCount(viewerId, uid));
            if (!staff) {
                removeCaseInsensitive(item, "mail", "phone", "assets", "points", "address",
                        "pay", "clientId", "ip", "local", "invitationCode");
            }
            data.add(item);
        }
        return new Page(data, total == null ? 0 : total);
    }

    /**
     * Completes phone-code login and optionally creates an account when invitations are disabled.
     * The SMS itself is still sent by the official endpoint; this method consumes its shared Redis
     * code only after MySQL and the legacy-compatible session have been written successfully.
     */
    public Map<String, Object> phoneLogin(String phone, String code, String remoteAddress) {
        String normalizedPhone = phone == null ? "" : phone.trim();
        String normalizedCode = code == null ? "" : code.trim();
        if (!normalizedPhone.matches("[0-9+ -]{5,30}") || normalizedCode.isEmpty()) {
            throw new IllegalArgumentException("参数错误");
        }
        String expected = verificationCodes.phoneVerificationCode(normalizedPhone);
        if (expected == null) {
            throw new IllegalArgumentException("请先发送验证码");
        }
        if (!expected.equals(normalizedCode)) {
            throw new IllegalArgumentException("验证码错误");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT uid,name,bantime FROM starfree_users WHERE phone=? ORDER BY uid LIMIT 1",
                normalizedPhone);
        long uid;
        String name;
        boolean createdWithoutPassword = false;
        if (rows.isEmpty()) {
            Map<String, Object> config = firstConfig("isInvite");
            if (integer(get(config, "isInvite")) == 1) {
                throw new IllegalArgumentException("当前注册需要邀请码，请采用其它方式注册");
            }
            name = createUniqueName();
            uid = insertPhoneUser(name, normalizedPhone, remoteAddress);
            createdWithoutPassword = true;
        } else {
            Map<String, Object> row = rows.get(0);
            rejectBanned(row);
            uid = number(get(row, "uid"));
            name = value(get(row, "name"));
        }

        long now = Instant.now().getEpochSecond();
        String token = tokenGenerator.generate(name);
        jdbc.update("UPDATE starfree_users SET authCode=?,logged=?,"
                        + "activated=IF(activated=0,?,activated),ip=? WHERE uid=?",
                token, now, now, safe(remoteAddress, 255), uid);
        Map<String, Object> user = tokens.userById(uid);
        if (user == null) {
            throw new IllegalStateException("登录成功后无法读取用户资料");
        }
        user.put("token", token);
        user.put("time", System.currentTimeMillis());
        user.put("isvip", vipState(user.get("vip")) > 0 ? 1 : 0);
        if (createdWithoutPassword) {
            user.put("noPassWord", 1);
        }
        try {
            sessions.store(normalizedPhone, token, user);
            verificationCodes.consumePhoneVerificationCode(normalizedPhone);
        } catch (RuntimeException error) {
            jdbc.update("UPDATE starfree_users SET authCode=NULL WHERE uid=? AND authCode=?", uid, token);
            throw error;
        }
        caches.afterUserWrite(uid, name);
        return user;
    }

    /** Staff-only profile and role edit using an explicit column whitelist. */
    public int manageEdit(String token, Map<String, Object> params) {
        StaffAccess.Actor actor = access.requireStaff(token);
        long targetUid = number(params.get("uid"));
        String targetName = RequestValues.objectText(params, "name");
        List<Map<String, Object>> targets;
        if (targetUid > 0) {
            targets = jdbc.queryForList(
                    "SELECT uid,name,mail,phone,authCode,`group`,campus_option_id,grade_option_id "
                            + "FROM starfree_users WHERE uid=? LIMIT 1",
                    targetUid);
        } else {
            targets = jdbc.queryForList(
                    "SELECT uid,name,mail,phone,authCode,`group`,campus_option_id,grade_option_id "
                            + "FROM starfree_users WHERE name=? LIMIT 1",
                    targetName);
        }
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("用户不存在");
        }
        Map<String, Object> target = targets.get(0);
        targetUid = number(get(target, "uid"));
        String oldGroup = value(get(target, "group"));
        if (!actor.isAdministrator() && "administrator".equals(oldGroup)) {
            throw new IllegalArgumentException("你没有操作权限");
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        copyText(params, changes, "screenName", "screenName", 32);
        copyText(params, changes, "mail", "mail", 200);
        copyText(params, changes, "phone", "phone", 30);
        copyText(params, changes, "url", "url", 200);
        copyText(params, changes, "customize", "customize", 255);
        copyText(params, changes, "introduce", "introduce", 255);
        copyText(params, changes, "avatar", "avatar", 4000);
        copyText(params, changes, "userBg", "userBg", 400);
        copyText(params, changes, "address", "address", 4000);
        copyText(params, changes, "pay", "pay", 4000);
        if (params.containsKey("campusId")) {
            long campusId = number(params.get("campusId"));
            requireAssignableIdentityOption(campusId, "campus", targetUid);
            changes.put("campus_option_id", campusId);
        }
        if (params.containsKey("gradeId")) {
            long gradeId = number(params.get("gradeId"));
            requireAssignableIdentityOption(gradeId, "grade", targetUid);
            changes.put("grade_option_id", gradeId);
        }
        if (params.containsKey("experience")) {
            changes.put("experience", Math.max(0, RequestValues.objectInteger(params, "experience", 0)));
        }
        String newGroup = RequestValues.objectText(params, "group");
        if (!newGroup.isEmpty()) {
            if (!GROUPS.contains(newGroup)) {
                throw new IllegalArgumentException("用户组不正确");
            }
            if (!actor.isAdministrator() && "administrator".equals(newGroup)) {
                throw new IllegalArgumentException("你没有操作权限");
            }
            if (actor.getUid() == targetUid && "administrator".equals(oldGroup)
                    && !"administrator".equals(newGroup)) {
                throw new IllegalArgumentException("不能降级当前登录的管理员");
            }
            changes.put("group", newGroup);
        }
        String password = rawText(params.get("password"));
        if (!password.isEmpty()) {
            if (password.length() < 6 || password.length() > 128) {
                throw new IllegalArgumentException("密码长度必须为6到128位");
            }
            changes.put("password", passwords.hash(password));
        }
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("没有可保存的字段");
        }

        StringBuilder sql = new StringBuilder("UPDATE starfree_users SET ");
        List<Object> values = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, Object> change : changes.entrySet()) {
            if (index++ > 0) {
                sql.append(',');
            }
            sql.append('`').append(change.getKey()).append("`=?");
            values.add(change.getValue());
        }
        boolean revoke = changes.containsKey("password") || changes.containsKey("mail")
                || changes.containsKey("phone") || changes.containsKey("group");
        if (revoke) {
            sql.append(",authCode=NULL");
        }
        sql.append(" WHERE uid=?");
        values.add(targetUid);
        int changed = jdbc.update(sql.toString(), values.toArray());
        if (changed > 0 && revoke) {
            sessions.removeAccounts(value(get(target, "name")), value(get(target, "mail")),
                    value(get(target, "phone")));
            String oldToken = value(get(target, "authCode"));
            if (!oldToken.isEmpty()) {
                sessions.remove(oldToken);
            }
        } else if (changed > 0) {
            String oldToken = value(get(target, "authCode"));
            Map<String, Object> refreshed = tokens.userById(targetUid);
            if (!oldToken.isEmpty() && refreshed != null) {
                sessions.store(value(get(target, "name")), oldToken, refreshed);
            }
        }
        if (changed > 0) {
            caches.afterUserWrite(targetUid, value(get(target, "name")));
        }
        return changed;
    }

    private void requireAssignableIdentityOption(long id, String type, long targetUid) {
        String column = "campus".equals(type) ? "campus_option_id" : "grade_option_id";
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_identity_options o WHERE o.id=? AND o.type=? "
                        + "AND (o.enabled=1 OR EXISTS (SELECT 1 FROM starfree_users u "
                        + "WHERE u.uid=? AND u.`" + column + "`=o.id))",
                Integer.class, id, type, targetUid);
        if (count == null || count != 1) {
            throw new IllegalArgumentException("campus".equals(type)
                    ? "请选择当前启用的校区" : "请选择当前启用的年级");
        }
    }

    /** Administrator-only account deletion; content is intentionally not cascade-deleted. */
    public int deleteUser(String token, long targetUid) {
        StaffAccess.Actor actor = access.requireAdministrator(token);
        if (targetUid <= 0) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (actor.getUid() == targetUid) {
            throw new IllegalArgumentException("你不可以删除你自己");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT uid,name,mail,phone,authCode,`group` FROM starfree_users WHERE uid=? LIMIT 1",
                targetUid);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("该用户不存在");
        }
        Map<String, Object> target = rows.get(0);
        if ("administrator".equals(value(get(target, "group")))) {
            throw new IllegalArgumentException("不允许删除管理员账号");
        }
        int changed = economyLock.execute(connection -> {
            executeUpdate(connection, "DELETE FROM starfree_userapi WHERE uid=?", targetUid);
            return executeUpdate(connection, "DELETE FROM starfree_users WHERE uid=?", targetUid);
        });
        if (changed > 0) {
            sessions.removeAccounts(value(get(target, "name")), value(get(target, "mail")),
                    value(get(target, "phone")));
            String oldToken = value(get(target, "authCode"));
            if (!oldToken.isEmpty()) {
                sessions.remove(oldToken);
            }
            caches.afterUserWrite(targetUid, value(get(target, "name")));
        }
        return changed;
    }

    /** Approves a server-generated QR login nonce with the authenticated user's token. */
    public void approveScan(String token, String nonce) {
        access.requireUser(token);
        redisKeys.approveScan(nonce, token);
    }

    /** Generates at most one hundred eight-character invitation codes for an administrator. */
    public int generateInvitations(String token, int requested) {
        StaffAccess.Actor actor = access.requireAdministrator(token);
        int count = Math.min(MAX_INVITATIONS, requested);
        if (count < 1) {
            throw new IllegalArgumentException("数量必须大于0");
        }
        int created = 0;
        long now = Instant.now().getEpochSecond();
        while (created < count) {
            String code = randomText(8);
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM starfree_invitation WHERE code=?", Integer.class, code);
            if (exists != null && exists > 0) {
                continue;
            }
            created += jdbc.update(
                    "INSERT INTO starfree_invitation(code,created,uid,status) VALUES(?,?,?,0)",
                    code, now, actor.getUid());
        }
        caches.afterInvitationWrite();
        return created;
    }

    /** Administrator-only invitation page; status is the only accepted search filter. */
    public Page invitations(Map<String, String> request) {
        access.requireAdministrator(RequestValues.text(request, "token"));
        Map<String, Object> search = RequestValues.jsonObject(mapper, request.get("searchParams"));
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        int limit = bounded(RequestValues.integer(request, "limit", 15), MAX_PAGE_SIZE);
        List<Object> args = new ArrayList<>();
        String where = "";
        if (search.containsKey("status")) {
            int status = RequestValues.objectInteger(search, "status", -1);
            if (status == 0 || status == 1) {
                where = " WHERE status=?";
                args.add(status);
            }
        }
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_invitation" + where, Integer.class, args.toArray());
        List<Object> rowArgs = new ArrayList<>(args);
        rowArgs.add((page - 1) * limit);
        rowArgs.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,code,created,uid,status FROM starfree_invitation" + where
                        + " ORDER BY created DESC,id DESC LIMIT ?,?",
                rowArgs.toArray());
        return new Page(rows, total == null ? 0 : total);
    }

    /** Returns unused invitations for the download endpoint, capped to protect the database. */
    public List<Map<String, Object>> invitationExport(String token, int requested) {
        access.requireAdministrator(token);
        int limit = Math.min(10000, Math.max(1, requested));
        return jdbc.queryForList(
                "SELECT id,code,uid,created FROM starfree_invitation WHERE status=0 "
                        + "ORDER BY created DESC,id DESC LIMIT ?", limit);
    }

    /** Sends one persistent system inbox message. Push delivery remains an optional old-service concern. */
    public int sendSystemMessage(String token, long targetUid, String text) {
        StaffAccess.Actor actor = access.requireAdministrator(token);
        String message = text == null ? "" : text.trim();
        if (message.isEmpty() || message.length() > 4000) {
            throw new IllegalArgumentException("发送内容不能为空且不能超过4000字");
        }
        if (tokens.userById(targetUid) == null) {
            throw new IllegalArgumentException("该用户不存在");
        }
        return jdbc.update(
                "INSERT INTO starfree_inbox(type,uid,text,touid,isread,value,created,cid) "
                        + "VALUES('system',?,?,?,0,0,?,0)",
                actor.getUid(), message, targetUid, Instant.now().getEpochSecond());
    }

    /** Staff ban with an immutable violation audit row and immediate session revocation. */
    public int ban(String token, long targetUid, long seconds, String type, String text) {
        StaffAccess.Actor actor = access.requireStaff(token);
        if (seconds <= 0 || seconds > MAX_BAN_SECONDS
                || !("manager".equals(type) || "system".equals(type))) {
            throw new IllegalArgumentException("参数错误");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT uid,name,mail,phone,authCode,`group`,bantime FROM starfree_users WHERE uid=? LIMIT 1",
                targetUid);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("该用户不存在");
        }
        Map<String, Object> target = rows.get(0);
        String targetGroup = value(get(target, "group"));
        if ("administrator".equals(targetGroup)
                || (!actor.isAdministrator() && "editor".equals(targetGroup))) {
            throw new IllegalArgumentException("该用户组无法封禁");
        }
        long now = Instant.now().getEpochSecond();
        long current = number(get(target, "bantime"));
        long expires = Math.max(now, current) + seconds;
        int changed = jdbc.update("UPDATE starfree_users SET bantime=?,authCode=NULL WHERE uid=?",
                expires, targetUid);
        if (changed > 0) {
            jdbc.update("INSERT INTO starfree_violation(uid,type,text,created,handler,value) "
                            + "VALUES(?,?,?,?,?,?)",
                    targetUid, type, safe(text, 4000), now, actor.getUid(), expires);
            revoke(target);
            caches.afterUserWrite(targetUid, value(get(target, "name")));
        }
        return changed;
    }

    /** Administrator-only unban. Setting bantime to now preserves old client status semantics. */
    public int unblock(String token, long targetUid) {
        access.requireAdministrator(token);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT uid,bantime FROM starfree_users WHERE uid=? LIMIT 1", targetUid);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("用户不存在");
        }
        long now = Instant.now().getEpochSecond();
        long old = number(get(rows.get(0), "bantime"));
        if (old != 1 && old <= now) {
            throw new IllegalArgumentException("用户未被封禁");
        }
        int changed = jdbc.update("UPDATE starfree_users SET bantime=? WHERE uid=?", now, targetUid);
        if (changed > 0) {
            Map<String, Object> target = tokens.userById(targetUid);
            caches.afterUserWrite(targetUid, value(get(target, "name")));
        }
        return changed;
    }

    /** Public paginated ban history with a sanitized userJson snapshot. */
    public Page violations(Map<String, String> request) {
        Map<String, Object> search = RequestValues.jsonObject(mapper, request.get("searchParams"));
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        int limit = bounded(RequestValues.integer(request, "limit", 15), MAX_PAGE_SIZE);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        addLongFilter(where, args, search, "uid", "uid");
        String type = RequestValues.objectText(search, "type");
        if (!type.isEmpty()) {
            where.append(" AND type=?");
            args.add(type);
        }
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_violation" + where, Integer.class, args.toArray());
        List<Object> rowArgs = new ArrayList<>(args);
        rowArgs.add((page - 1) * limit);
        rowArgs.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,uid,type,text,created,handler,value FROM starfree_violation" + where
                        + " ORDER BY created DESC,id DESC LIMIT ?,?", rowArgs.toArray());
        for (Map<String, Object> row : rows) {
            Map<String, Object> user = tokens.userById(number(get(row, "uid")));
            row.put("userJson", publicUser(user));
        }
        return new Page(rows, total == null ? 0 : total);
    }

    /**
     * Administrator-only targeted cleanup. The five selectors intentionally preserve the old API's
     * narrow table scope; it does not silently delete unrelated account data.
     */
    public int cleanUserData(String token, long targetUid, int selector) {
        access.requireAdministrator(token);
        Map<String, Object> target = tokens.userById(targetUid);
        if (target == null) {
            throw new IllegalArgumentException("该用户不存在");
        }
        if ("administrator".equals(value(target.get("group")))) {
            throw new IllegalArgumentException("不允许清理管理员数据");
        }
        int changed;
        switch (selector) {
            case 1:
                changed = jdbc.update("DELETE FROM starfree_contents WHERE authorId=?", targetUid);
                break;
            case 2:
                changed = jdbc.update("DELETE FROM starfree_comments WHERE authorId=?", targetUid);
                break;
            case 3:
                changed = jdbc.update("DELETE FROM starfree_space WHERE uid=?", targetUid);
                break;
            case 4:
                changed = jdbc.update("DELETE FROM starfree_shop WHERE uid=?", targetUid);
                break;
            case 5:
                changed = jdbc.update("DELETE FROM starfree_userlog WHERE type='clock' AND uid=?", targetUid);
                break;
            default:
                throw new IllegalArgumentException("参数错误");
        }
        if (changed > 0) {
            caches.afterUserWrite(targetUid, value(target.get("name")));
            caches.afterDashboardCountWrite();
        }
        return changed;
    }

    /** Administrator-controlled shared silence key used by all replacement write guards. */
    public boolean restrict(String token, long targetUid, boolean silenced) {
        access.requireAdministrator(token);
        if (tokens.userById(targetUid) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        int silenceSeconds = integer(get(firstConfig("silenceTime"), "silenceTime"));
        boolean changed = redisKeys.setSilenced(targetUid, silenced,
                silenceSeconds > 0 ? silenceSeconds : 600);
        if (!silenced && !changed) {
            throw new IllegalArgumentException("用户状态正常，无需操作");
        }
        return true;
    }

    /** Staff VIP gift serialized with all other wallet/VIP operations and recorded in paylog. */
    public int giftVip(String token, final long targetUid, final int days) {
        final StaffAccess.Actor actor = access.requireStaff(token);
        if (days < 1 || days > 36500) {
            throw new IllegalArgumentException("参数错误！");
        }
        return economyLock.execute(connection -> {
            Map<String, Object> target = userForUpdate(connection, targetUid);
            if (target == null) {
                throw new IllegalArgumentException("用户不存在");
            }
            long vip = number(get(target, "vip"));
            if (vip == 1) {
                throw new IllegalArgumentException("用户已经是永久VIP，无需续期");
            }
            long now = Instant.now().getEpochSecond();
            long expires = Math.max(now, vip) + days * 86400L;
            int changed = executeUpdate(connection,
                    "UPDATE starfree_users SET vip=? WHERE uid=?", expires, targetUid);
            if (changed > 0) {
                executeUpdate(connection,
                        "INSERT INTO starfree_paylog(subject,total_amount,out_trade_no,trade_no,"
                                + "paytype,uid,created,status) VALUES(?,?,?,?,?,?,?,1)",
                        "管理员赠送VIP", "0",
                        "giftvip-" + actor.getUid() + "-" + targetUid + "-" + now,
                        "", "buyvip", targetUid, now);
            }
            if (changed > 0) {
                caches.afterUserWrite(targetUid, value(get(target, "name")));
            }
            return changed;
        });
    }

    private long insertPhoneUser(String name, String phone, String remoteAddress) {
        String randomPassword = passwords.hash(randomText(24));
        long now = Instant.now().getEpochSecond();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update((PreparedStatementCreator) connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO starfree_users(name,password,screenName,created,activated,logged,"
                            + "`group`,authCode,assets,vip,experience,bantime,posttime,ip,local,phone,"
                            + "invitationUser,points) VALUES(?,?,NULL,?,0,0,'contributor',NULL,0,0,0,0,0,"
                            + "?,'',?,0,0)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            statement.setString(2, randomPassword);
            statement.setLong(3, now);
            statement.setString(4, safe(remoteAddress, 255));
            statement.setString(5, phone);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("手机用户创建后无法取得uid");
        }
        return key.longValue();
    }

    private String createUniqueName() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String name = "u" + randomText(11).toLowerCase();
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM starfree_users WHERE name=?", Integer.class, name);
            if (count == null || count == 0) {
                return name;
            }
        }
        throw new IllegalStateException("无法生成唯一用户名");
    }

    private Map<String, Object> firstConfig(String column) {
        if (!("isInvite".equals(column) || "silenceTime".equals(column))) {
            throw new IllegalArgumentException("Unsupported config column");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT `" + column + "` FROM starfree_apiconfig ORDER BY id LIMIT 1");
        if (rows.isEmpty()) {
            throw new IllegalStateException("系统配置不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> userForUpdate(Connection connection, long uid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT uid,vip FROM starfree_users WHERE uid=? LIMIT 1")) {
            statement.setLong(1, uid);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("uid", result.getLong("uid"));
                row.put("vip", result.getLong("vip"));
                return row;
            }
        }
    }

    private int executeUpdate(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement.executeUpdate();
        }
    }

    private void revoke(Map<String, Object> target) {
        sessions.removeAccounts(value(get(target, "name")), value(get(target, "mail")),
                value(get(target, "phone")));
        String oldToken = value(get(target, "authCode"));
        if (!oldToken.isEmpty()) {
            sessions.remove(oldToken);
        }
    }

    private void rejectBanned(Map<String, Object> row) {
        long bannedUntil = number(get(row, "bantime"));
        long now = Instant.now().getEpochSecond();
        if (bannedUntil == 1) {
            throw new IllegalArgumentException("你的账号已被永久封禁，如有疑问请联系管理员");
        }
        if (bannedUntil > now) {
            throw new IllegalArgumentException("你的账号被暂时封禁，请耐心等待解封。");
        }
    }

    private Map<String, Object> publicUser(Map<String, Object> user) {
        if (user == null) {
            Map<String, Object> missing = new LinkedHashMap<>();
            missing.put("uid", 0);
            missing.put("name", "已注销用户");
            missing.put("avatar", "");
            return missing;
        }
        Map<String, Object> copy = new LinkedHashMap<>(user);
        removeCaseInsensitive(copy, "mail", "phone", "assets", "points", "address", "pay",
                "clientId", "ip", "local", "invitationCode");
        copy.put("isvip", vipState(copy.get("vip")));
        return copy;
    }

    private int followCount(long viewerUid, long targetUid) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_fan WHERE uid=? AND touid=?",
                Integer.class, viewerUid, targetUid);
        return count == null ? 0 : count;
    }

    private int vipState(Object value) {
        long vip = number(value);
        if (vip == 1) {
            return 2;
        }
        return vip > Instant.now().getEpochSecond() ? 1 : 0;
    }

    private String userOrder(String requested) {
        if ("uid".equals(requested) || "logged".equals(requested)
                || "experience".equals(requested) || "assets".equals(requested)
                || "points".equals(requested)) {
            return '`' + requested + '`';
        }
        return "created";
    }

    private void addLongFilter(StringBuilder where, List<Object> args,
                               Map<String, Object> source, String requestKey, String column) {
        long value = number(source.get(requestKey));
        if (value > 0) {
            where.append(" AND `").append(column).append("`=?");
            args.add(value);
        }
    }

    private void copyText(Map<String, Object> source, Map<String, Object> target,
                          String requestKey, String column, int maxLength) {
        if (!source.containsKey(requestKey)) {
            return;
        }
        String value = rawText(source.get(requestKey));
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(requestKey + "超过最大长度");
        }
        target.put(column, value);
    }

    private String firstText(Map<String, Object> source, String first, String second) {
        String value = RequestValues.objectText(source, first);
        return value.isEmpty() ? RequestValues.objectText(source, second) : value;
    }

    private boolean truthy(Object value) {
        return value != null && !("0".equals(String.valueOf(value))
                || "false".equalsIgnoreCase(String.valueOf(value))
                || String.valueOf(value).trim().isEmpty());
    }

    private boolean isStaff(String group) {
        return "administrator".equals(group) || "editor".equals(group);
    }

    private int bounded(int value, int maximum) {
        return Math.max(1, Math.min(maximum, value));
    }

    private int integer(Object value) {
        return (int) number(value);
    }

    private long number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Object get(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void removeCaseInsensitive(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            String found = null;
            for (String current : row.keySet()) {
                if (current.equalsIgnoreCase(key)) {
                    found = current;
                    break;
                }
            }
            if (found != null) {
                row.remove(found);
            }
        }
    }

    private String randomText(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            value.append(RANDOM_ALPHABET.charAt(random.nextInt(RANDOM_ALPHABET.length())));
        }
        return value.toString();
    }

    private String rawText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safe(Object value, int maximum) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** Standard list result; count is derived from data and total from the complete filter. */
    public static final class Page {
        private final List<Map<String, Object>> data;
        private final int total;

        public Page(List<Map<String, Object>> data, int total) {
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
