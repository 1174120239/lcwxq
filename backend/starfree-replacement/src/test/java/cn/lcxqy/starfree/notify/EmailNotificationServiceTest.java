package cn.lcxqy.starfree.notify;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailNotificationServiceTest {
    @Test
    void missingSmtpCredentialsSkipSending() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailNotificationService email = new EmailNotificationService(
                mailSender, "", "", "", true);

        email.sendDynamicNotice("owner@example.com", "title", "body");

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void blankRecipientSkipsSending() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailNotificationService email = new EmailNotificationService(
                mailSender, "sender@qq.com", "auth-code", "sender@qq.com", true);

        email.sendDynamicNotice("", "title", "body");

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void configuredServiceSendsUtf8PlainTextMessage() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        EmailNotificationService email = new EmailNotificationService(
                mailSender, "sender@qq.com", "auth-code", "sender@qq.com", true);

        email.sendDynamicNotice("owner@example.com",
                "\u3010LCYZ\u3011\u4f60\u7684\u52a8\u6001\u6536\u5230\u65b0\u8bc4\u8bba",
                "\u8bc4\u8bba\u4e86\u4f60\u7684\u52a8\u6001\uff1ahello");

        verify(mailSender).send(message);
        assertThat(message.getSubject()).isEqualTo("\u3010LCYZ\u3011\u4f60\u7684\u52a8\u6001\u6536\u5230\u65b0\u8bc4\u8bba");
        assertThat(message.getContent()).isEqualTo("\u8bc4\u8bba\u4e86\u4f60\u7684\u52a8\u6001\uff1ahello");
        assertThat(message.getAllRecipients()).hasSize(1);
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("owner@example.com");
    }
}
