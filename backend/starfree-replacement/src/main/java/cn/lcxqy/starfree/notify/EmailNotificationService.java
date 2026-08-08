package cn.lcxqy.starfree.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;

/**
 * Optional email notifications for dynamic interactions. Sending is skipped when the
 * feature is disabled, SMTP credentials are missing, or the recipient has no address,
 * so email can never break the underlying comment/like. SMTP credentials live only in
 * the production runtime configuration (spring.mail.*), never in the repository.
 */
@Service
public class EmailNotificationService {
    private static final Logger LOG = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final boolean configured;

    public EmailNotificationService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${spring.mail.from:}") String from,
            @Value("${notification.email.enabled:true}") boolean enabled) {
        this.mailSender = mailSender;
        this.from = blank(from) ? username : from;
        this.configured = enabled && mailSender != null
                && !blank(username) && !blank(password) && !blank(this.from);
    }

    /** Sends one plain-text notice; failures are logged and never propagated. */
    public void sendDynamicNotice(String toEmail, String title, String text) {
        if (!configured || blank(toEmail)) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject(title == null ? "" : title);
            helper.setText(text == null ? "" : text);
            mailSender.send(message);
            LOG.info("Email notification sent to {}", toEmail);
        } catch (Exception error) {
            LOG.warn("Email notification failed for {}", toEmail, error);
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
