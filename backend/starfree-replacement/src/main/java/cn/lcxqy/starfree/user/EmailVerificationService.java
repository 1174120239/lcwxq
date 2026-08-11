package cn.lcxqy.starfree.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/** Generates, stores and delivers one-time email codes without exposing them to callers. */
@Service
class EmailVerificationService {
    private static final Logger LOG = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9_+&*-]+(?:\\.[A-Za-z0-9_+&*-]+)*@"
                    + "(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,63}$");
    private static final int CODE_TTL_SECONDS = 1800;
    private static final int RECIPIENT_COOLDOWN_SECONDS = 60;
    private static final int IP_COOLDOWN_SECONDS = 3;

    private final EmailVerificationRepository repository;
    private final LegacyRegistrationRedis redis;
    private final VerificationEmailSender sender;
    private final SecureRandom random;

    EmailVerificationService(EmailVerificationRepository repository,
                             LegacyRegistrationRedis redis,
                             VerificationEmailSender sender) {
        this(repository, redis, sender, new SecureRandom());
    }

    EmailVerificationService(EmailVerificationRepository repository,
                             LegacyRegistrationRedis redis,
                             VerificationEmailSender sender,
                             SecureRandom random) {
        this.repository = repository;
        this.redis = redis;
        this.sender = sender;
        this.random = random;
    }

    void sendRegistrationCode(String requestedMail, String remoteAddress) {
        EmailVerificationRepository.VerificationConfig config = enabledConfig();
        String mail = normalizeMail(requestedMail);
        if (repository.mailExists(mail)) {
            throw new IllegalArgumentException("该邮箱已被注册");
        }
        requireSenderConfigured();
        deliver(mail, mail, "平台用户", config, remoteAddress);
    }

    void sendRecoveryCode(String account, String remoteAddress) {
        EmailVerificationRepository.VerificationConfig config = enabledConfig();
        String normalized = account == null ? "" : account.trim();
        if (normalized.isEmpty() || normalized.length() > 200) {
            throw new IllegalArgumentException("请输入正确的账号");
        }
        EmailVerificationRepository.RecoveryAccount user = EMAIL.matcher(normalized).matches()
                ? repository.accountByMail(normalized.toLowerCase(Locale.ROOT))
                : repository.accountByName(normalized);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        String mail = normalizeMail(user.getMail());
        requireSenderConfigured();
        deliver(user.getName(), mail, user.getName(), config, remoteAddress);
    }

    private EmailVerificationRepository.VerificationConfig enabledConfig() {
        EmailVerificationRepository.VerificationConfig config = repository.config();
        if (!config.isEnabled()) {
            throw new IllegalArgumentException("邮箱验证已关闭");
        }
        return config;
    }

    private void requireSenderConfigured() {
        try {
            sender.requireConfigured();
        } catch (VerificationMailException error) {
            LOG.warn("Verification email sender is not configured");
            throw new IllegalArgumentException(message(error.getKind()));
        }
    }

    private void deliver(String redisKeyPart, String mail, String userName,
                         EmailVerificationRepository.VerificationConfig config,
                         String remoteAddress) {
        try {
            redis.claimEmailSend(redisKeyPart, remoteAddress,
                    RECIPIENT_COOLDOWN_SECONDS, IP_COOLDOWN_SECONDS);
        } catch (IllegalStateException error) {
            LOG.error("Verification code storage is not enabled");
            throw new IllegalArgumentException("验证码存储未配置，请联系管理员");
        }
        String code = sixDigitCode();
        try {
            redis.storeVerificationCode(redisKeyPart, code, CODE_TTL_SECONDS);
            sender.send(mail, subject(config), body(config, userName, code));
        } catch (VerificationMailException error) {
            cleanupFailedSend(redisKeyPart, remoteAddress);
            LOG.warn("Verification email delivery failed: {}", error.getKind());
            throw new IllegalArgumentException(message(error.getKind()));
        } catch (RuntimeException error) {
            cleanupFailedSend(redisKeyPart, remoteAddress);
            throw error;
        }
    }

    private void cleanupFailedSend(String redisKeyPart, String remoteAddress) {
        try {
            redis.consumeVerificationCode(redisKeyPart);
        } catch (RuntimeException error) {
            LOG.error("Could not remove failed verification code", error);
        }
        try {
            redis.releaseEmailSend(redisKeyPart, remoteAddress);
        } catch (RuntimeException error) {
            LOG.error("Could not release failed verification email rate limit", error);
        }
    }

    private String sixDigitCode() {
        return String.valueOf(100000 + random.nextInt(900000));
    }

    private String subject(EmailVerificationRepository.VerificationConfig config) {
        String siteTitle = config.getSiteTitle().isEmpty() ? "聊一论坛" : config.getSiteTitle();
        return "【" + siteTitle + "】邮箱验证码";
    }

    private String body(EmailVerificationRepository.VerificationConfig config,
                        String userName, String code) {
        String template = config.getTemplate();
        if (template.isEmpty()) {
            template = "<p>你好，{{userName}}：</p><p>你的邮箱验证码是 "
                    + "<strong>{{code}}</strong>，30 分钟内有效。</p>";
            if (!config.getSiteUrl().isEmpty()) {
                template += "<p>来自 " + escapeHtml(config.getSiteUrl()) + "</p>";
            }
        } else if (!template.contains("{{code}}")) {
            throw new IllegalArgumentException("验证码邮件模板缺少{{code}}占位符");
        }
        return template.replace("{{userName}}", escapeHtml(userName))
                .replace("{{code}}", code);
    }

    private String message(VerificationMailException.Kind kind) {
        switch (kind) {
            case CONFIGURATION:
                return "邮件服务未配置，请联系管理员";
            case AUTHENTICATION:
                return "邮箱授权码无效或SMTP服务未开启";
            case CONNECTION:
                return "邮件服务器暂时无法连接，请稍后再试";
            case BUSY:
                return "邮件服务繁忙，请稍后再试";
            default:
                return "验证码邮件发送失败，请稍后再试";
        }
    }

    private String normalizeMail(String value) {
        String mail = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (mail.length() > 200 || !EMAIL.matcher(mail).matches()) {
            throw new IllegalArgumentException("请输入正确的邮箱");
        }
        return mail;
    }

    private String escapeHtml(String value) {
        String text = value == null ? "" : value;
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
