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
import java.util.concurrent.Semaphore;

/** Strict SMTP sender used only where delivery success is required for the operation. */
@Service
class VerificationEmailSender {
    private final JavaMailSender mailSender;
    private final String from;
    private final boolean configured;
    private final Semaphore permits;

    VerificationEmailSender(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${spring.mail.from:}") String from,
            @Value("${verification.email.enabled:true}") boolean enabled,
            @Value("${verification.email.max-concurrent:2}") int maxConcurrent) {
        this.mailSender = mailSender;
        this.from = blank(from) ? username : from.trim();
        this.configured = enabled && mailSender != null
                && !blank(username) && !blank(password) && !blank(this.from);
        this.permits = new Semaphore(Math.max(1, maxConcurrent), true);
    }

    void requireConfigured() {
        if (!configured) {
            throw new VerificationMailException(VerificationMailException.Kind.CONFIGURATION);
        }
    }

    void send(String recipient, String subject, String html) {
        requireConfigured();
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
            mailSender.send(message);
        } catch (MailAuthenticationException error) {
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
            throw new VerificationMailException(kind, error);
        } catch (MailException error) {
            throw new VerificationMailException(
                    VerificationMailException.Kind.DELIVERY, error);
        } catch (RuntimeException error) {
            throw new VerificationMailException(
                    VerificationMailException.Kind.DELIVERY, error);
        } finally {
            permits.release();
        }
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
