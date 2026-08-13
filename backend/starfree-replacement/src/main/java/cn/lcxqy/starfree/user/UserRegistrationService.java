package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.economy.EconomyLockExecutor;
import cn.lcxqy.starfree.economy.EconomyOperationJournal;
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
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class UserRegistrationService {
    private static final Logger LOG = LoggerFactory.getLogger(UserRegistrationService.class);
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9_+&*-]+(?:\\.[A-Za-z0-9_+&*-]+)*@"
                    + "(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,63}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+() -]{5,30}$");
    private static final Pattern CODE = Pattern.compile("^[0-9]{6}$");

    private final EconomyLockExecutor lock;
    private final EconomyOperationJournal journal;
    private final UserRegistrationRepository repository;
    private final PhpassPasswordVerifier passwords;
    private final LegacyRegistrationRedis redis;
    private final Clock clock;

    @Autowired
    public UserRegistrationService(EconomyLockExecutor lock, EconomyOperationJournal journal,
                                   UserRegistrationRepository repository,
                                   PhpassPasswordVerifier passwords,
                                   LegacyRegistrationRedis redis) {
        this(lock, journal, repository, passwords, redis, Clock.systemUTC());
    }

    UserRegistrationService(EconomyLockExecutor lock, EconomyOperationJournal journal,
                            UserRegistrationRepository repository,
                            PhpassPasswordVerifier passwords,
                            LegacyRegistrationRedis redis, Clock clock) {
        this.lock = lock;
        this.journal = journal;
        this.repository = repository;
        this.passwords = passwords;
        this.redis = redis;
        this.clock = clock;
    }

    public Map<String, Object> register(Map<String, Object> body, String remoteAddress) {
        RegistrationConfig initialConfig = repository.config();
        RegistrationRequest request = validate(body, initialConfig, remoteAddress);
        redis.checkBurst(request.remoteAddress, initialConfig.isRobotProtection(),
                initialConfig.getSilenceSeconds());
        String passwordHash = passwords.hash(request.password);
        String operationKey = journal.fixedKey("user-register",
                request.name.toLowerCase(Locale.ROOT) + "\n"
                        + request.mail.toLowerCase(Locale.ROOT));

        Map<String, Object> result = lock.execute(connection -> registerLocked(
                connection, request, passwordHash, operationKey));
        if (initialConfig.isEmailRequired()) {
            try {
                redis.consumeVerificationCode(request.mail);
            } catch (RuntimeException error) {
                // The unique mail index and durable journal still prevent another account.
                LOG.warn("Could not consume a registration verification code", error);
            }
        }
        return result;
    }

    private Map<String, Object> registerLocked(Connection connection, RegistrationRequest request,
                                               String passwordHash, String operationKey)
            throws SQLException {
        RegistrationConfig config = repository.config(connection);
        validatePolicy(request, config);
        Map<String, Object> payload = mapOf(
                "name", request.name,
                "mail", request.mail,
                "phone", request.phone,
                "campusId", request.campusId,
                "gradeId", request.gradeId,
                "remoteAddress", request.remoteAddress,
                "inviteCode", request.inviteCode);
        EconomyOperationJournal.BeginResult begin = journal.begin(connection, operationKey,
                "user-register", 0L, 0L, 0L, payload);
        if (begin.isReplay()) {
            return begin.getResult();
        }

        long invitationId = 0L;
        long inviterUid = 0L;
        long oldInviterAssets = 0L;
        long oldInviterPoints = 0L;
        long oldInviterExperience = 0L;
        long userId = 0L;
        long paylogId = 0L;
        boolean invitationConsumed = false;
        boolean inviterChanged = false;
        boolean invitationRecordCreated = false;
        boolean invitationRewardChanged = false;
        boolean reusableInvitation = false;
        int rewardPoints = 0;
        int rewardExperience = 0;
        boolean projectionsComplete = false;
        try {
            if (!repository.enabledIdentityOption(connection, request.campusId, "campus")) {
                throw new IllegalArgumentException("请选择当前启用的校区");
            }
            if (!repository.enabledIdentityOption(connection, request.gradeId, "grade")) {
                throw new IllegalArgumentException("请选择当前启用的年级");
            }
            if (repository.nameExists(connection, request.name)) {
                throw new IllegalArgumentException("\u8be5\u7528\u6237\u540d\u5df2\u6ce8\u518c");
            }
            if (!request.mail.isEmpty() && repository.mailExists(connection, request.mail)) {
                throw new IllegalArgumentException("\u8be5\u90ae\u7bb1\u5df2\u6ce8\u518c");
            }
            verifyCode(request, config);

            UserRegistrationRepository.InvitationRewardConfig invitationRewards =
                    repository.invitationRewardConfig(connection);
            String reusableCode = request.inviteCode.toUpperCase(Locale.ROOT);
            Map<String, Object> reusable = request.inviteCode.isEmpty()
                    || !invitationRewards.isEnabled()
                    ? null : repository.reusableInvitation(connection, reusableCode);
            if (reusable != null) {
                inviterUid = number(reusable.get("uid"));
                reusableInvitation = true;
                rewardPoints = invitationRewards.getPoints();
                rewardExperience = invitationRewards.getExperience();
            } else if (config.isInvitationRequired()) {
                List<Map<String, Object>> invitations =
                        repository.availableInvitations(connection, request.inviteCode);
                if (invitations.isEmpty()) {
                    throw new IllegalArgumentException("\u9519\u8bef\u7684\u9080\u8bf7\u7801");
                }
                if (invitations.size() != 1) {
                    throw new IllegalArgumentException(
                            "\u9080\u8bf7\u7801\u914d\u7f6e\u91cd\u590d\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458");
                }
                Map<String, Object> invitation = invitations.get(0);
                invitationId = number(invitation.get("id"));
                inviterUid = number(invitation.get("uid"));
                if (invitationId <= 0 || inviterUid <= 0) {
                    throw new IllegalArgumentException("\u9519\u8bef\u7684\u9080\u8bf7\u7801");
                }
                if (repository.consumeInvitation(connection, invitationId) != 1) {
                    throw new IllegalArgumentException("\u9080\u8bf7\u7801\u5df2\u88ab\u4f7f\u7528");
                }
                invitationConsumed = true;
            } else if (!request.inviteCode.isEmpty() && invitationRewards.isEnabled()) {
                throw new IllegalArgumentException("\u9519\u8bef\u7684\u9080\u8bf7\u7801");
            }

            Map<String, Object> inviter = inviterUid <= 0
                    ? null : repository.user(connection, inviterUid);
            if (inviterUid > 0 && inviter == null) {
                throw new IllegalArgumentException("\u9519\u8bef\u7684\u9080\u8bf7\u7801");
            }
            if (inviter != null) {
                oldInviterAssets = number(inviter.get("assets"));
                oldInviterPoints = number(inviter.get("points"));
                oldInviterExperience = number(inviter.get("experience"));
            }

            long now = Instant.now(clock).getEpochSecond();
            UserRegistrationRepository.RegistrationUser user =
                    new UserRegistrationRepository.RegistrationUser(
                            request.name, passwordHash, request.mail, request.phone,
                            request.remoteAddress, inviterUid, request.campusId,
                            request.gradeId, now);
            userId = repository.insertUser(connection, user);

            if (reusableInvitation) {
                if (repository.insertInvitationRecord(
                        connection, inviterUid, userId, reusableCode,
                        rewardPoints, rewardExperience) != 1) {
                    throw new SQLException("Invitation reward record insert failed");
                }
                invitationRecordCreated = true;
                if (rewardPoints > 0 || rewardExperience > 0) {
                    long nextPoints = addReward(oldInviterPoints, rewardPoints, "points");
                    long nextExperience = addReward(
                            oldInviterExperience, rewardExperience, "experience");
                    if (repository.setInvitationRewards(
                            connection, inviterUid, nextPoints, nextExperience) != 1) {
                        throw new SQLException("Invitation points and experience update failed");
                    }
                    invitationRewardChanged = true;
                }
            }

            int rebate = inviterUid > 0 && !reusableInvitation ? config.getRebateAmount() : 0;
            if (rebate > 0) {
                long nextAssets = addAsset(oldInviterAssets, rebate);
                if (repository.setAssets(connection, inviterUid, nextAssets) != 1) {
                    throw new SQLException("Invitation rebate balance update failed");
                }
                inviterChanged = true;
                paylogId = repository.insertRebatePaylog(
                        connection, inviterUid, rebate, operationKey, now);
            }

            Map<String, Object> result = mapOf(
                    "rows", 1,
                    "uid", userId,
                    "invitationUser", inviterUid,
                    "rebate", rebate,
                    "rewardPoints", rewardPoints,
                    "rewardExperience", rewardExperience);
            projectionsComplete = true;
            journal.commit(connection, operationKey, result);
            return result;
        } catch (Exception error) {
            if (projectionsComplete) {
                // A commit error is ambiguous: the InnoDB update may have reached MySQL.
                // Keep completed MyISAM rows instead of deleting a possibly valid account.
                throw markNeedsReview(connection, operationKey, error);
            }
            compensate(connection, operationKey, error, invitationId, inviterUid,
                    oldInviterAssets, oldInviterPoints, oldInviterExperience,
                    userId, paylogId, invitationConsumed, inviterChanged,
                    invitationRecordCreated, invitationRewardChanged);
            rethrow(error);
            return Collections.emptyMap();
        }
    }

    private void compensate(Connection connection, String operationKey, Exception original,
                            long invitationId, long inviterUid, long oldInviterAssets,
                            long oldInviterPoints, long oldInviterExperience,
                            long userId, long paylogId, boolean invitationConsumed,
                            boolean inviterChanged, boolean invitationRecordCreated,
                            boolean invitationRewardChanged) throws SQLException {
        Exception compensation = null;
        compensation = compensate(compensation, paylogId > 0,
                () -> repository.deletePaylog(connection, paylogId));
        compensation = compensate(compensation, inviterChanged,
                () -> repository.setAssets(connection, inviterUid, oldInviterAssets));
        compensation = compensate(compensation, invitationRewardChanged,
                () -> repository.setInvitationRewards(
                        connection, inviterUid, oldInviterPoints, oldInviterExperience));
        compensation = compensate(compensation, invitationRecordCreated,
                () -> repository.deleteInvitationRecord(connection, userId));
        compensation = compensate(compensation, userId > 0,
                () -> repository.deleteUser(connection, userId));
        compensation = compensate(compensation, invitationConsumed,
                () -> repository.releaseInvitation(connection, invitationId));
        if (compensation == null) {
            journal.fail(connection, operationKey, original);
            return;
        }
        original.addSuppressed(compensation);
        journal.needsReview(connection, operationKey, original);
        throw new SQLException("Registration compensation requires manual review", original);
    }

    private SQLException markNeedsReview(Connection connection, String operationKey,
                                         Exception original) {
        SQLException review = new SQLException(
                "Registration journal commit requires manual review", original);
        try {
            journal.needsReview(connection, operationKey, original);
        } catch (Exception markingError) {
            review.addSuppressed(markingError);
        }
        return review;
    }

    private Exception compensate(Exception prior, boolean needed, SqlAction action) {
        if (!needed) {
            return prior;
        }
        try {
            if (action.execute() != 1) {
                throw new SQLException("Registration compensation affected an unexpected row count");
            }
        } catch (Exception error) {
            if (prior == null) {
                return error;
            }
            prior.addSuppressed(error);
        }
        return prior;
    }

    private RegistrationRequest validate(Map<String, Object> body, RegistrationConfig config,
                                         String remoteAddress) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("\u53c2\u6570\u9519\u8bef");
        }
        String name = RequestValues.objectText(body, "name");
        String password = body.get("password") == null ? "" : String.valueOf(body.get("password"));
        String mail = RequestValues.objectText(body, "mail").toLowerCase(Locale.ROOT);
        String phone = RequestValues.objectText(body, "phone");
        String code = RequestValues.objectText(body, "code");
        String inviteCode = RequestValues.objectText(body, "inviteCode");
        long campusId = RequestValues.objectInteger(body, "campusId", 0);
        long gradeId = RequestValues.objectInteger(body, "gradeId", 0);
        String address = safeAddress(remoteAddress);

        if (name.isEmpty() || name.length() > 32 || hasControlCharacter(name)) {
            throw new IllegalArgumentException("\u7528\u6237\u540d\u683c\u5f0f\u4e0d\u6b63\u786e");
        }
        PasswordPolicy.requireStrong(password);
        if (!mail.isEmpty() && (mail.length() > 200 || !EMAIL.matcher(mail).matches())) {
            throw new IllegalArgumentException("\u8bf7\u8f93\u5165\u6b63\u786e\u7684\u90ae\u7bb1");
        }
        if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
            throw new IllegalArgumentException("\u8bf7\u8f93\u5165\u6b63\u786e\u7684\u624b\u673a\u53f7");
        }
        if (inviteCode.length() > 255) {
            throw new IllegalArgumentException("\u9519\u8bef\u7684\u9080\u8bf7\u7801");
        }
        if (campusId <= 0) {
            throw new IllegalArgumentException("请选择校区");
        }
        if (gradeId <= 0) {
            throw new IllegalArgumentException("请选择年级");
        }
        RegistrationRequest request = new RegistrationRequest(
                name, password, mail, phone, code, inviteCode, address, campusId, gradeId);
        validatePolicy(request, config);
        return request;
    }

    private void validatePolicy(RegistrationRequest request, RegistrationConfig config) {
        if (config.isEmailRequired()) {
            if (request.mail.isEmpty()) {
                throw new IllegalArgumentException("\u8bf7\u8f93\u5165\u6b63\u786e\u7684\u90ae\u7bb1");
            }
            if (!CODE.matcher(request.code).matches()) {
                throw new IllegalArgumentException("\u8bf7\u8f93\u5165\u6b63\u786e\u7684\u9a8c\u8bc1\u7801");
            }
        }
        if (config.isInvitationRequired() && request.inviteCode.isEmpty()) {
            throw new IllegalArgumentException("\u8bf7\u8f93\u5165\u9080\u8bf7\u7801");
        }
        // isPhone was exposed by the old config but never enforced by userRegister.
        if (config.getPhoneMode() < 0) {
            throw new IllegalArgumentException("\u6ce8\u518c\u914d\u7f6e\u4e0d\u6b63\u786e");
        }
        if (containsForbidden(config.getForbidden(), request.name)) {
            throw new IllegalArgumentException("\u7528\u6237\u540d\u5305\u542b\u8fdd\u89c4\u8bcd\u8bed");
        }
    }

    private void verifyCode(RegistrationRequest request, RegistrationConfig config) {
        if (!config.isEmailRequired()) {
            return;
        }
        String expected = redis.verificationCode(request.mail);
        if (expected == null) {
            throw new IllegalArgumentException("\u8bf7\u5148\u53d1\u9001\u9a8c\u8bc1\u7801");
        }
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                request.code.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("\u9a8c\u8bc1\u7801\u4e0d\u6b63\u786e");
        }
    }

    private long addAsset(long current, int amount) {
        try {
            long next = Math.addExact(current, (long) amount);
            if (next > Integer.MAX_VALUE) {
                throw new ArithmeticException("Legacy asset column overflow");
            }
            return next;
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("\u9080\u8bf7\u5956\u52b1\u8d85\u51fa\u53ef\u7528\u8303\u56f4", error);
        }
    }

    private long addReward(long current, int amount, String field) {
        try {
            long next = Math.addExact(current, (long) amount);
            if (next > Integer.MAX_VALUE) {
                throw new ArithmeticException("Legacy " + field + " column overflow");
            }
            return next;
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("\u9080\u8bf7\u5956\u52b1\u8d85\u51fa\u53ef\u7528\u8303\u56f4", error);
        }
    }

    private boolean containsForbidden(String forbidden, String value) {
        if (forbidden == null || forbidden.trim().isEmpty()) {
            return false;
        }
        for (String word : forbidden.split("[,|\\r\\n]+")) {
            String normalized = word.trim();
            if (!normalized.isEmpty() && value.contains(normalized)) {
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

    private String safeAddress(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private long number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void rethrow(Exception error) throws SQLException {
        if (error instanceof SQLException) {
            throw (SQLException) error;
        }
        if (error instanceof RuntimeException) {
            throw (RuntimeException) error;
        }
        throw new SQLException("Registration failed", error);
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    @FunctionalInterface
    private interface SqlAction {
        int execute() throws SQLException;
    }

    private static final class RegistrationRequest {
        private final String name;
        private final String password;
        private final String mail;
        private final String phone;
        private final String code;
        private final String inviteCode;
        private final String remoteAddress;
        private final long campusId;
        private final long gradeId;

        private RegistrationRequest(String name, String password, String mail, String phone,
                                    String code, String inviteCode, String remoteAddress,
                                    long campusId, long gradeId) {
            this.name = name;
            this.password = password;
            this.mail = mail;
            this.phone = phone;
            this.code = code;
            this.inviteCode = inviteCode;
            this.remoteAddress = remoteAddress;
            this.campusId = campusId;
            this.gradeId = gradeId;
        }
    }
}
