package cn.lcxqy.starfree.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 使用 Spring 默认 JDK 序列化格式读写旧 API 的 Redis 登录态。
 *
 * <p>逻辑 key 是 {@code <prefix>_userkey<account>} 和
 * {@code <prefix>_userInfo<token>}，但 Redis 中的实际字节带 Java serialization header。
 * 账号映射和 session 共享同一 TTL。所有删除都先比较当前映射，避免旧 token 的退出请求误删
 * 已经轮换到新 token 的账号链接。
 */
@Service
public class RedisLegacySessionBridge implements LegacySessionBridge {
    private static final long DETACHED_SESSION_TTL_SECONDS = 300;
    private final RedisTemplate<Object, Object> redis;
    private final boolean enabled;
    private final String prefix;
    private final long sessionTtl;

    @Autowired
    public RedisLegacySessionBridge(
            RedisTemplate<Object, Object> redis,
            @Value("${legacy.redis.enabled:false}") boolean enabled,
            @Value("${legacy.redis.prefix:starfree}") String prefix,
            @Value("${legacy.redis.session-ttl:86400}") long sessionTtl) {
        this.redis = redis;
        this.enabled = enabled;
        this.prefix = prefix == null || prefix.trim().isEmpty() ? "starfree" : prefix.trim();
        this.sessionTtl = sessionTtl > 0 ? sessionTtl : 86400;
    }

    /**
     * 写账号别名和 session hash。账号已有不同 token 时先删除旧 session；登录名与数据库 name
     * 不同时同时维护两种别名。写入失败必须由调用方撤销 MySQL authCode。
     */
    @Override
    public void store(String account, String token, Map<String, Object> session) {
        if (!enabled) {
            return;
        }
        String normalizedAccount = required(account, "account");
        String normalizedToken = required(token, "token");
        Map<Object, Object> values = serializableValues(session);
        values.put("token", normalizedToken);

        ValueOperations<Object, Object> strings = redis.opsForValue();
        replaceAccountLink(strings, normalizedAccount, normalizedToken);
        Object username = values.get("name");
        if (username != null && !normalizedAccount.equals(String.valueOf(username))) {
            replaceAccountLink(strings, String.valueOf(username), normalizedToken);
        }

        String sessionKey = sessionKey(normalizedToken);
        HashOperations<Object, Object, Object> hashes = redis.opsForHash();
        redis.delete(sessionKey);
        hashes.putAll(sessionKey, values);
        redis.expire(sessionKey, sessionTtl, TimeUnit.SECONDS);
    }

    /**
     * Store only the token session hash. This is used for server-side legacy
     * API calls and must never replace a user's existing login token.
     */
    @Override
    public void storeDetached(String token, Map<String, Object> session) {
        if (!enabled) {
            return;
        }
        String normalizedToken = required(token, "token");
        Map<Object, Object> values = serializableValues(session);
        values.put("token", normalizedToken);
        String sessionKey = sessionKey(normalizedToken);
        HashOperations<Object, Object, Object> hashes = redis.opsForHash();
        redis.delete(sessionKey);
        hashes.putAll(sessionKey, values);
        redis.expire(sessionKey, Math.min(sessionTtl, DETACHED_SESSION_TTL_SECONDS), TimeUnit.SECONDS);
    }

    @Override
    public boolean available() {
        return enabled;
    }

    /** 从 session hash 的 uid 字段读取用户 id；兼容 Number 和数字字符串。 */
    @Override
    public Long userId(String token) {
        if (!enabled || token == null || token.trim().isEmpty()) {
            return null;
        }
        Object uid = redis.opsForHash().get(sessionKey(token.trim()), "uid");
        if (uid instanceof Number) {
            return ((Number) uid).longValue();
        }
        if (uid == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(uid));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 删除 session，并清理 hash 中可发现的 name/mail/phone 别名。 */
    @Override
    public void remove(String token) {
        if (!enabled || token == null || token.trim().isEmpty()) {
            return;
        }
        String normalizedToken = token.trim();
        String sessionKey = sessionKey(normalizedToken);
        Map<Object, Object> session = redis.opsForHash().entries(sessionKey);
        redis.delete(sessionKey);
        // A legacy session can have been opened by username, mail, or phone.
        // Remove every known account alias so a revoked token cannot survive
        // through an old userkey entry after a password or mail change.
        removeSessionAccountLink(session, "name", normalizedToken);
        removeSessionAccountLink(session, "mail", normalizedToken);
        removeSessionAccountLink(session, "phone", normalizedToken);
    }

    /**
     * 先通过每个 account userkey 找 token，再删除该 token 全部已知别名；用于找回密码和敏感
     * 账号修改。空值和重复账号会被忽略。
     */
    @Override
    public void removeAccounts(String... accounts) {
        if (!enabled || accounts == null || accounts.length == 0) {
            return;
        }
        Set<String> uniqueAccounts = new LinkedHashSet<>();
        for (String account : accounts) {
            if (account != null && !account.trim().isEmpty()) {
                uniqueAccounts.add(account.trim());
            }
        }
        for (String account : uniqueAccounts) {
            Object token = redis.opsForValue().get(accountKey(account));
            if (token == null || String.valueOf(token).trim().isEmpty()) {
                continue;
            }
            String normalizedToken = String.valueOf(token).trim();
            remove(normalizedToken);
            // Old login sessions store the submitted login identifier in the
            // hash's name field. Explicitly clear the alias even when a partial
            // or malformed hash does not contain that identifier.
            removeAccountLink(account, normalizedToken);
        }
    }

    private void replaceAccountLink(ValueOperations<Object, Object> strings, String account, String token) {
        String key = accountKey(account);
        Object previous = strings.get(key);
        if (previous != null && !token.equals(String.valueOf(previous))) {
            redis.delete(sessionKey(String.valueOf(previous)));
        }
        strings.set(key, token, sessionTtl, TimeUnit.SECONDS);
    }

    private void removeAccountLink(String account, String token) {
        if (account == null || account.trim().isEmpty()) {
            return;
        }
        String key = accountKey(account);
        Object current = redis.opsForValue().get(key);
        if (current != null && token.equals(String.valueOf(current))) {
            redis.delete(key);
        }
    }

    private void removeSessionAccountLink(Map<Object, Object> session, String field, String token) {
        Object account = session.get(field);
        if (account != null) {
            removeAccountLink(String.valueOf(account), token);
        }
    }

    private Map<Object, Object> serializableValues(Map<String, Object> session) {
        Map<Object, Object> values = new LinkedHashMap<>();
        if (session == null) {
            return values;
        }
        for (Map.Entry<String, Object> entry : session.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        return values;
    }

    private String accountKey(String account) {
        return prefix + "_userkey" + account;
    }

    private String sessionKey(String token) {
        return prefix + "_userInfo" + token;
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
