package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.economy.EconomyLockExecutor;
import cn.lcxqy.starfree.security.LegacySessionBridge;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.PhpassPasswordVerifier;
import cn.lcxqy.starfree.security.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AccountMaintenanceService {
    private static final Logger LOG = LoggerFactory.getLogger(AccountMaintenanceService.class);
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9_+&*-]+(?:\\.[A-Za-z0-9_+&*-]+)*@"
                    + "(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,63}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+() -]{5,30}$");

    private final EconomyLockExecutor lock;
    private final UserRegistrationRepository registrationRepository;
    private final AccountMaintenanceRepository repository;
    private final PhpassPasswordVerifier passwords;
    private final LegacyRegistrationRedis verificationCodes;
    private final LegacyTokenService tokens;
    private final LegacySessionBridge sessions;

    @Autowired
    public AccountMaintenanceService(
            EconomyLockExecutor lock,
            UserRegistrationRepository registrationRepository,
            AccountMaintenanceRepository repository,
            PhpassPasswordVerifier passwords,
            LegacyRegistrationRedis verificationCodes,
            LegacyTokenService tokens,
            LegacySessionBridge sessions) {
        this.lock = lock;
        this.registrationRepository = registrationRepository;
        this.repository = repository;
        this.passwords = passwords;
        this.verificationCodes = verificationCodes;
        this.tokens = tokens;
        this.sessions = sessions == null ? LegacySessionBridge.NOOP : sessions;
    }

    public Map<String, Object> registrationConfig() {
        Map<String, Object> source = repository.publicRegistrationConfig();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isEmail", number(value(source, "isEmail")));
        result.put("isInvite", number(value(source, "isInvite")));
        result.put("isPhone", number(value(source, "isPhone")));
        return result;
    }

    public boolean containsAccountFields(Map<String, Object> body) {
        if (body == null) return false;
        String[] fields = {"screenName", "introduce", "userBg", "url", "avatar", "address",
                "pay", "mail", "phone", "password"};
        for (String field : fields) if (body.containsKey(field)) return true;
        return false;
    }

    public int forgotPassword(Map<String, Object> body) {
        RegistrationConfig initialConfig = registrationRepository.config();
        if (!initialConfig.isEmailRequired()) {
            throw new IllegalArgumentException(
                    "\u90ae\u7bb1\u9a8c\u8bc1\u5df2\u7ecf\u5173\u95ed\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u627e\u56de\u5bc6\u7801");
        }
        String account = RequestValues.objectText(body, "name");
        String code = RequestValues.objectText(body, "code");
        Object rawPassword = body == null ? null : body.get("password");
        String password = rawPassword == null ? "" : String.valueOf(rawPassword);
        if (account.isEmpty() || code.isEmpty() || password.isEmpty()) {
            throw new IllegalArgumentException("\u53c2\u6570\u9519\u8bef");
        }
        if (account.length() > 200) {
            throw new IllegalArgumentException("\u53c2\u6570\u9519\u8bef");
        }
        PasswordPolicy.requireStrong(password);
        String passwordHash = passwords.hash(password);
        return lock.execute(connection -> resetPasswordLocked(
                connection, account, code, passwordHash));
    }

    public EditResult edit(String token, Map<String, Object> body) {
        long authenticatedUid = authenticatedUid(token);
        long requestedUid = positiveLong(body == null ? null : body.get("uid"));
        if (requestedUid <= 0) {
            throw new IllegalArgumentException("\u7528\u6237\u4e0d\u5b58\u5728");
        }
        if (requestedUid != authenticatedUid) {
            throw new IllegalArgumentException("\u65e0\u6743\u4fee\u6539\u5176\u4ed6\u7528\u6237\u8d44\u6599");
        }
        RegistrationConfig initialConfig = registrationRepository.config();
        EditRequest request = prepareEdit(body, initialConfig);
        EditResult result = lock.execute(connection -> editLocked(
                connection, authenticatedUid, token.trim(), request));
        if (!result.isSessionRevoked()) {
            refreshSession(authenticatedUid, token.trim(), result.getUsername());
        }
        return result;
    }

    public int setClientId(String token, String clientId) {
        long uid = authenticatedUid(token);
        String normalized = clientId == null ? "" : clientId.trim();
        if (normalized.length() > 255 || hasControlCharacter(normalized)) {
            throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        AccountMaintenanceRepository.AccountRecord account = lock.execute(connection -> {
            AccountMaintenanceRepository.AccountRecord current = repository.accountByUid(
                    connection, uid);
            if (current == null) {
                throw new IllegalArgumentException("\u7528\u6237\u4e0d\u5b58\u5728");
            }
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("clientId", normalized);
            if (repository.update(connection, uid, changes) != 1) {
                throw new IllegalStateException("Client ID update did not affect one user");
            }
            return current;
        });
        refreshSession(uid, token.trim(), account.getName());
        return 1;
    }

    private int resetPasswordLocked(Connection connection, String account, String code,
                                    String passwordHash) throws SQLException {
        RegistrationConfig config = registrationRepository.config(connection);
        if (!config.isEmailRequired()) {
            throw new IllegalArgumentException(
                    "\u90ae\u7bb1\u9a8c\u8bc1\u5df2\u7ecf\u5173\u95ed\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u627e\u56de\u5bc6\u7801");
        }
        AccountMaintenanceRepository.AccountRecord user = EMAIL.matcher(account).matches()
                ? repository.accountByMail(connection, account)
                : repository.accountByName(connection, account);
        if (user == null) {
            throw new IllegalArgumentException("\u7528\u6237\u4e0d\u5b58\u5728");
        }
        verifyEmailCode(user.getName(), code);
        // Consume before changing MyISAM. A rare SQL failure requires a new code,
        // but can never leave a successful password reset with a reusable code.
        verificationCodes.consumeVerificationCode(user.getName());
        // Public login still runs in the closed API. It records only Redis
        // userkey/session entries and leaves authCode empty, so password reset
        // must revoke all account aliases as well as any MySQL-backed token.
        sessions.removeAccounts(user.getName(), user.getMail(), user.getPhone());
        revokeSession(user.getAuthCode());

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("password", passwordHash);
        changes.put("authCode", null);
        if (repository.update(connection, user.getUid(), changes) != 1) {
            throw new IllegalStateException("Password reset did not affect one user");
        }
        return 1;
    }

    private EditResult editLocked(Connection connection, long uid, String token,
                                  EditRequest request) throws SQLException {
        RegistrationConfig config = registrationRepository.config(connection);
        AccountMaintenanceRepository.AccountRecord user = repository.accountByUid(connection, uid);
        if (user == null) {
            throw new IllegalArgumentException("\u7528\u6237\u4e0d\u5b58\u5728");
        }
        Map<String, Object> changes = new LinkedHashMap<>(request.changes);
        boolean introductionRejected = false;

        if (request.passwordChanged
                && !passwords.matches(request.currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("\u539f\u5bc6\u7801\u9519\u8bef");
        }

        if (request.mail != null) {
            if (!config.isEmailRequired()) {
                throw new IllegalArgumentException("\u90ae\u7bb1\u9a8c\u8bc1\u5df2\u7ecf\u5173\u95ed");
            }
            if (repository.valueExists(connection, "mail", request.mail, uid)) {
                throw new IllegalArgumentException("\u8be5\u90ae\u7bb1\u5df2\u88ab\u7ed1\u5b9a");
            }
            verifyEmailCode(request.mail, request.code);
        }
        if (request.phone != null) {
            if (repository.valueExists(connection, "phone", request.phone, uid)) {
                throw new IllegalArgumentException("\u8be5\u624b\u673a\u53f7\u5df2\u88ab\u7ed1\u5b9a");
            }
            verifyPhoneCode(request.phone, request.code);
        }
        if (request.screenName != null
                && containsForbidden(config.getForbidden(), request.screenName)) {
            throw new IllegalArgumentException("\u7528\u6237\u540d\u5305\u542b\u8fdd\u89c4\u8bcd\u8bed");
        }
        if (request.screenName != null
                && repository.valueExists(connection, "screenName", request.screenName, uid)) {
            throw new IllegalArgumentException("\u8be5\u6635\u79f0\u5df2\u88ab\u5360\u7528\uff01");
        }
        if (request.introduce != null
                && containsForbidden(config.getForbidden(), request.introduce)) {
            changes.remove("introduce");
            introductionRejected = true;
        }
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
        }

        if (request.mail != null) {
            verificationCodes.consumeVerificationCode(request.mail);
        }
        if (request.phone != null) {
            verificationCodes.consumePhoneVerificationCode(request.phone);
        }

        boolean revoke = request.passwordChanged || request.mail != null;
        if (revoke) {
            revokeSession(token);
            if (!user.getAuthCode().isEmpty() && !user.getAuthCode().equals(token)) {
                revokeSession(user.getAuthCode());
            }
            changes.put("authCode", null);
        }
        if (repository.update(connection, uid, changes) != 1) {
            throw new IllegalStateException("Account update did not affect one user");
        }
        String message = introductionRejected
                ? "\u7b80\u4ecb\u5b58\u5728\u8fdd\u7981\u8bcd\uff0c\u8be5\u5b57\u6bb5\u672a\u4fee\u6539\u3002"
                : "\u64cd\u4f5c\u6210\u529f";
        return new EditResult(1, message, revoke, user.getName());
    }

    private EditRequest prepareEdit(Map<String, Object> body, RegistrationConfig config) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        String screenName = optionalText(body, "screenName", true);
        if (screenName != null) {
            validateShortText(screenName, 32, false);
            changes.put("screenName", screenName);
        }
        String introduce = optionalText(body, "introduce", false);
        if (introduce != null) {
            validateShortText(introduce, 255, true);
            changes.put("introduce", introduce);
        }
        copyBounded(body, changes, "userBg", 400, false);
        copyBounded(body, changes, "url", 200, false);
        copyText(body, changes, "avatar");
        copyText(body, changes, "address");
        copyText(body, changes, "pay");

        String mail = optionalText(body, "mail", true);
        String phone = optionalText(body, "phone", true);
        String code = optionalText(body, "code", true);
        if (mail != null && phone != null) {
            throw new IllegalArgumentException("\u90ae\u7bb1\u548c\u624b\u673a\u53f7\u8bf7\u5206\u522b\u4fee\u6539");
        }
        if (mail != null) {
            if (!config.isEmailRequired()) {
                throw new IllegalArgumentException("\u90ae\u7bb1\u9a8c\u8bc1\u5df2\u7ecf\u5173\u95ed");
            }
            if (mail.length() > 200 || !EMAIL.matcher(mail).matches() || code == null) {
                throw new IllegalArgumentException("\u8bf7\u8f93\u5165\u6b63\u786e\u7684\u90ae\u7bb1\u548c\u9a8c\u8bc1\u7801");
            }
            changes.put("mail", mail);
        }
        if (phone != null) {
            if (!PHONE.matcher(phone).matches() || code == null) {
                throw new IllegalArgumentException("\u8bf7\u8f93\u5165\u6b63\u786e\u7684\u624b\u673a\u53f7\u548c\u9a8c\u8bc1\u7801");
            }
            changes.put("phone", phone);
        }

        Object rawPassword = body.get("password");
        String password = rawPassword == null ? "" : String.valueOf(rawPassword);
        boolean passwordChanged = !password.isEmpty();
        String currentPassword = optionalText(body, "currentPassword", false);
        if (passwordChanged) {
            if (currentPassword == null || currentPassword.isEmpty()) {
                throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
            }
            PasswordPolicy.requireStrong(password);
            changes.put("password", passwords.hash(password));
        }
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        return new EditRequest(changes, screenName, introduce, mail, phone,
                code == null ? "" : code, currentPassword == null ? "" : currentPassword,
                passwordChanged);
    }

    private void verifyEmailCode(String keyPart, String submitted) {
        verifyCode(verificationCodes.verificationCode(keyPart), submitted);
    }

    private void verifyPhoneCode(String keyPart, String submitted) {
        verifyCode(verificationCodes.phoneVerificationCode(keyPart), submitted);
    }

    private void verifyCode(String expected, String submitted) {
        if (expected == null) {
            throw new IllegalArgumentException(
                    "\u9a8c\u8bc1\u7801\u4e0d\u6b63\u786e\u6216\u5df2\u5931\u6548");
        }
        String actual = submitted == null ? "" : submitted;
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("\u9a8c\u8bc1\u7801\u4e0d\u6b63\u786e");
        }
    }

    private long authenticatedUid(String token) {
        String normalized = token == null ? "" : token.trim();
        Long uid = normalized.isEmpty() ? null : tokens.userId(normalized);
        if (uid == null || uid <= 0) {
            throw new IllegalArgumentException(
                    "\u7528\u6237\u672a\u767b\u5f55\u6216Token\u9a8c\u8bc1\u5931\u8d25");
        }
        return uid;
    }

    private void revokeSession(String token) {
        if (token != null && !token.trim().isEmpty()) {
            sessions.remove(token.trim());
        }
    }

    private void refreshSession(long uid, String token, String username) {
        try {
            Map<String, Object> user = tokens.userById(uid);
            if (user != null) {
                user.put("token", token);
                sessions.store(username, token, user);
            }
        } catch (RuntimeException error) {
            // The SQL write remains authoritative. Existing legacy sessions still
            // retain uid/group and can continue to authorize old endpoints.
            LOG.warn("Could not refresh the legacy session after account maintenance", error);
        }
    }

    private void copyBounded(Map<String, Object> source, Map<String, Object> target,
                             String key, int maxLength, boolean allowControl) {
        String value = optionalText(source, key, false);
        if (value != null) {
            validateShortText(value, maxLength, allowControl);
            target.put(key, value);
        }
    }

    private void copyText(Map<String, Object> source, Map<String, Object> target, String key) {
        String value = optionalText(source, key, false);
        if (value != null) {
            if (value.getBytes(StandardCharsets.UTF_8).length > 65535) {
                throw new IllegalArgumentException("\u53c2\u6570\u8fc7\u957f");
            }
            target.put(key, value);
        }
    }

    private String optionalText(Map<String, Object> source, String key, boolean trim) {
        if (source == null || !source.containsKey(key)) {
            return null;
        }
        Object raw = source.get(key);
        String value = raw == null ? "" : String.valueOf(raw);
        return trim ? value.trim() : value;
    }

    private void validateShortText(String value, int maxLength, boolean allowControl) {
        if (value.length() > maxLength || (!allowControl && hasControlCharacter(value))) {
            throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        if (!allowControl && value.isEmpty()) {
            throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
        }
    }

    private boolean containsForbidden(String forbidden, String text) {
        if (forbidden == null || forbidden.trim().isEmpty()
                || text == null || text.isEmpty()) {
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

    private boolean hasControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private long positiveLong(Object value) {
        try {
            long parsed = value == null ? 0L : Long.parseLong(String.valueOf(value));
            return parsed > 0 ? parsed : 0L;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private int number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    static final class EditRequest {
        private final Map<String, Object> changes;
        private final String screenName;
        private final String introduce;
        private final String mail;
        private final String phone;
        private final String code;
        private final String currentPassword;
        private final boolean passwordChanged;

        EditRequest(Map<String, Object> changes, String screenName, String introduce,
                    String mail, String phone, String code, String currentPassword,
                    boolean passwordChanged) {
            this.changes = changes;
            this.screenName = screenName;
            this.introduce = introduce;
            this.mail = mail;
            this.phone = phone;
            this.code = code;
            this.currentPassword = currentPassword;
            this.passwordChanged = passwordChanged;
        }
    }

    public static final class EditResult {
        private final int rows;
        private final String message;
        private final boolean sessionRevoked;
        private final String username;

        EditResult(int rows, String message, boolean sessionRevoked, String username) {
            this.rows = rows;
            this.message = message;
            this.sessionRevoked = sessionRevoked;
            this.username = username;
        }

        public int getRows() {
            return rows;
        }

        public String getMessage() {
            return message;
        }

        boolean isSessionRevoked() {
            return sessionRevoked;
        }

        String getUsername() {
            return username;
        }
    }
}
