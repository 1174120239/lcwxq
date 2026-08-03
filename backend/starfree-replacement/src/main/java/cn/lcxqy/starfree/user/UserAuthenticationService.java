package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.security.LegacySessionBridge;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.PhpassPasswordVerifier;
import cn.lcxqy.starfree.security.SessionTokenGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class UserAuthenticationService {
    private final JdbcTemplate jdbc;
    private final PhpassPasswordVerifier passwords;
    private final SessionTokenGenerator tokenGenerator;
    private final LegacyTokenService tokens;
    private final LegacySessionBridge sessions;
    private final Clock clock;

    @Autowired
    public UserAuthenticationService(
            JdbcTemplate jdbc,
            PhpassPasswordVerifier passwords,
            SessionTokenGenerator tokenGenerator,
            LegacyTokenService tokens,
            LegacySessionBridge sessions) {
        this(jdbc, passwords, tokenGenerator, tokens, sessions, Clock.systemUTC());
    }

    UserAuthenticationService(
            JdbcTemplate jdbc,
            PhpassPasswordVerifier passwords,
            SessionTokenGenerator tokenGenerator,
            LegacyTokenService tokens,
            Clock clock) {
        this(jdbc, passwords, tokenGenerator, tokens, LegacySessionBridge.NOOP, clock);
    }

    UserAuthenticationService(
            JdbcTemplate jdbc,
            PhpassPasswordVerifier passwords,
            SessionTokenGenerator tokenGenerator,
            LegacyTokenService tokens,
            LegacySessionBridge sessions,
            Clock clock) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.tokenGenerator = tokenGenerator;
        this.tokens = tokens;
        this.sessions = sessions == null ? LegacySessionBridge.NOOP : sessions;
        this.clock = clock;
    }
    public Map<String, Object> login(String account, String password, String remoteAddress) {
        if (account == null || account.trim().isEmpty()
                || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("请输入用户名和密码");
        }

        String normalizedAccount = account.trim();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT uid,name,password,bantime,vip FROM starfree_users "
                        + "WHERE name = ? OR mail = ? LIMIT 1",
                normalizedAccount,
                normalizedAccount);
        if (rows.isEmpty()) {
            throw invalidCredentials();
        }

        Map<String, Object> row = rows.get(0);
        String storedHash = value(row.get("password"));
        if (!passwords.matches(password, storedHash)) {
            throw invalidCredentials();
        }

        long nowSeconds = Instant.now(clock).getEpochSecond();
        long bannedUntil = number(row.get("bantime"));
        if (bannedUntil == 1) {
            throw new IllegalArgumentException("该账号已被停用，请联系管理员");
        }
        if (bannedUntil > nowSeconds) {
            throw new IllegalArgumentException("该账号被临时限制，请稍后再试");
        }

        long uid = number(row.get("uid"));
        String username = value(row.get("name"));
        String token = tokenGenerator.generate(username);
        jdbc.update(
                "UPDATE starfree_users SET authCode = ?, logged = ?, "
                        + "activated = IF(activated = 0, ?, activated), ip = ? WHERE uid = ?",
                token,
                nowSeconds,
                nowSeconds,
                safeAddress(remoteAddress),
                uid);

        Map<String, Object> user = tokens.userById(uid);
        if (user == null) {
            throw new IllegalStateException("登录成功后无法读取用户资料");
        }
        user.put("token", token);
        user.put("time", Instant.now(clock).toEpochMilli());
        user.put("isvip", isVip(row.get("vip"), nowSeconds));
        try {
            sessions.store(normalizedAccount, token, user);
        } catch (RuntimeException ex) {
            jdbc.update("UPDATE starfree_users SET authCode = NULL WHERE uid = ? AND authCode = ?", uid, token);
            throw new IllegalStateException("登录会话同步到旧 API 失败", ex);
        }
        return user;
    }

    public void signOut(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        sessions.remove(token.trim());
        int changed = jdbc.update(
                "UPDATE starfree_users SET authCode = NULL WHERE authCode = ?",
                token.trim());
        if (changed == 0) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
    }

    private IllegalArgumentException invalidCredentials() {
        return new IllegalArgumentException("用户名或密码错误");
    }

    private int isVip(Object rawVip, long nowSeconds) {
        long vip = number(rawVip);
        return vip == 1 || vip > nowSeconds ? 1 : 0;
    }

    private String safeAddress(String remoteAddress) {
        if (remoteAddress == null) {
            return "";
        }
        String trimmed = remoteAddress.trim();
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
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

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
