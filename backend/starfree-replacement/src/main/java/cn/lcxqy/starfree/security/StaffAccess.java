package cn.lcxqy.starfree.security;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Central authorization boundary for management endpoints.
 *
 * <p>The legacy application encoded permissions as numeric annotation values. The replacement
 * resolves the token for every request, reloads the current role from MySQL, and exposes explicit
 * user/staff/administrator checks. Controllers must not trust a client supplied uid or group.
 */
@Component
public class StaffAccess {
    private final LegacyTokenService tokens;

    public StaffAccess(LegacyTokenService tokens) {
        this.tokens = tokens;
    }

    /** Requires any valid account and returns its current database-backed identity. */
    public Actor requireUser(String token) {
        Long uid = tokens.userId(token);
        Map<String, Object> user = uid == null ? null : tokens.userById(uid);
        if (uid == null || user == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        return new Actor(uid, value(user.get("name")), value(user.get("group")), user);
    }

    /** Requires administrator or editor. Resource ownership still needs a separate check. */
    public Actor requireStaff(String token) {
        Actor actor = requireUser(token);
        if (!actor.isStaff()) {
            throw new IllegalArgumentException("你没有操作权限");
        }
        return actor;
    }

    /** Requires administrator. Destructive and economic management routes use this check. */
    public Actor requireAdministrator(String token) {
        Actor actor = requireUser(token);
        if (!actor.isAdministrator()) {
            throw new IllegalArgumentException("你没有操作权限");
        }
        return actor;
    }

    private static String value(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }

    /** Immutable authenticated identity. The map is a snapshot and must never be written back. */
    public static final class Actor {
        private final long uid;
        private final String name;
        private final String group;
        private final Map<String, Object> user;

        private Actor(long uid, String name, String group, Map<String, Object> user) {
            this.uid = uid;
            this.name = name;
            this.group = group;
            this.user = user;
        }

        public long getUid() {
            return uid;
        }

        public String getName() {
            return name;
        }

        public String getGroup() {
            return group;
        }

        public Map<String, Object> getUser() {
            return user;
        }

        public boolean isAdministrator() {
            return "administrator".equals(group);
        }

        public boolean isEditor() {
            return "editor".equals(group);
        }

        public boolean isStaff() {
            return isAdministrator() || isEditor();
        }
    }
}
