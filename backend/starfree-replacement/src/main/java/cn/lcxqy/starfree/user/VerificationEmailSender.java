package cn.lcxqy.starfree.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.net.ConnectException;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;

/** Strict SMTP sender used only where delivery success is required for the operation. */
@Service
class VerificationEmailSender {
    private final JavaMailSender mailSender;
    private final String from;
    private final boolean configured;
    private final Semaphore permits;
    private final long authenticationBackoffMillis;
    private final long minimumAttemptIntervalMillis;
    private final LongSupplier currentTimeMillis;
    private final AtomicLong authenticationBlockedUntil = new AtomicLong();
    private final AtomicLong nextAttemptAt = new AtomicLong();

    @Autowired
    VerificationEmailSender(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${spring.mail.from:}") String from,
            @Value("${verification.email.enabled:true}") boolean enabled,
            @Value("${verification.email.max-concurrent:2}") int maxConcurrent,
            @Value("${verification.email.authentication-backoff-seconds:300}")
            long authenticationBackoffSeconds,
            @Value("${verification.email.minimum-attempt-interval-millis:1000}")
            long minimumAttemptIntervalMillis) {
        this(mailSender, username, password, from, enabled, maxConcurrent,
                Math.max(0L, authenticationBackoffSeconds) * 1000L,
                minimumAttemptIntervalMillis, System::currentTimeMillis);
    }

    VerificationEmailSender(JavaMailSender mailSender, String username, String password,
                            String from, boolean enabled, int maxConcurrent) {
        this(mailSender, username, password, from, enabled, maxConcurrent,
                300_000L, 1_000L, System::currentTimeMillis);
    }

    VerificationEmailSender(JavaMailSender mailSender, String username, String password,
                            String from, boolean enabled, int maxConcurrent,
                            long authenticationBackoffMillis,
                            long minimumAttemptIntervalMillis,
                            LongSupplier currentTimeMillis) {
        this.mailSender = mailSender;
        this.from = blank(from) ? username : from.trim();
        this.configured = enabled && mailSender != null
                && !blank(username) && !blank(password) && !blank(this.from);
        this.permits = new Semaphore(Math.max(1, maxConcurrent), true);
        this.authenticationBackoffMillis = Math.max(0L, authenticationBackoffMillis);
        this.minimumAttemptIntervalMillis = Math.max(0L, minimumAttemptIntervalMillis);
        this.currentTimeMillis = currentTimeMillis;
    }

    void requireConfigured() {
        if (!configured) {
            throw new VerificationMailException(VerificationMailException.Kind.CONFIGURATION);
        }
    }

    void send(String recipient, String subject, String html) {
        requireConfigured();
        requireAuthenticationAvailable();
        if (!permits.tryAcquire()) {
            throw new VerificationMailException(VerificationMailException.Kind.BUSY);
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html, true);
            reserveSmtpAttempt();
            mailSender.send(message);
        } catch (MailAuthenticationException error) {
            startAuthenticationBackoff();
            throw new VerificationMailException(
                    VerificationMailException.Kind.AUTHENTICATION, error);
        } catch (MailParseException | MessagingException error) {
            throw new VerificationMailException(
                    VerificationMailException.Kind.CONFIGURATION, error);
        } catch (MailSendException error) {
            VerificationMailException.Kind kind;
            if (hasCause(error, AuthenticationFailedException.class)) {
                kind = VerificationMailException.Kind.AUTHENTICATION;
            } else if (hasCause(error, ConnectException.class)
                    || hasCause(error, SocketException.class)) {
                kind = VerificationMailException.Kind.CONNECTION;
            } else {
                kind = VerificationMailException.Kind.DELIVERY;
            }
            if (kind == VerificationMailException.Kind.AUTHENTICATION) {
                startAuthenticationBackoff();
            }
            throw new VerificationMailException(kind, error);
        } catch (MailException error) {
            throw new VerificationMailException(
                    VerificationMailException.Kind.DELIVERY, error);
        } catch (VerificationMailException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new VerificationMailException(
                    VerificationMailException.Kind.DELIVERY, error);
        } finally {
            permits.release();
        }
    }

    private void requireAuthenticationAvailable() {
        if (currentTimeMillis.getAsLong() < authenticationBlockedUntil.get()) {
            throw new VerificationMailException(VerificationMailException.Kind.AUTHENTICATION);
        }
    }

    private void reserveSmtpAttempt() {
        while (true) {
            requireAuthenticationAvailable();
            long now = currentTimeMillis.getAsLong();
            long next = nextAttemptAt.get();
            if (now < next) {
                throw new VerificationMailException(VerificationMailException.Kind.BUSY);
            }
            long reservedUntil = safeAdd(now, minimumAttemptIntervalMillis);
            if (nextAttemptAt.compareAndSet(next, reservedUntil)) {
                return;
            }
        }
    }

    private void startAuthenticationBackoff() {
        long blockedUntil = safeAdd(currentTimeMillis.getAsLong(), authenticationBackoffMillis);
        while (true) {
            long current = authenticationBlockedUntil.get();
            if (current >= blockedUntil
                    || authenticationBlockedUntil.compareAndSet(current, blockedUntil)) {
                return;
            }
        }
    }

    private long safeAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
