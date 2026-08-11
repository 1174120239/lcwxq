package cn.lcxqy.starfree.user;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationEmailSenderTest {
    @Test
    void missingCredentialsAreReportedAsConfigurationFailure() {
        VerificationEmailSender sender = new VerificationEmailSender(
                mock(JavaMailSender.class), "", "", "", true, 2);

        VerificationMailException error = assertThrows(VerificationMailException.class,
                sender::requireConfigured);

        assertThat(error.getKind()).isEqualTo(VerificationMailException.Kind.CONFIGURATION);
    }

    @Test
    void configuredSenderCreatesUtf8HtmlMessage() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        VerificationEmailSender sender = new VerificationEmailSender(
                mailSender, "sender@qq.com", "auth-code", "sender@qq.com", true, 2);

        sender.send("student@qq.com", "【聊一论坛】邮箱验证码", "<b>334567</b>");

        verify(mailSender).send(message);
        assertThat(message.getSubject()).isEqualTo("【聊一论坛】邮箱验证码");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("student@qq.com");
        assertThat(String.valueOf(message.getContent())).contains("334567");
    }

    @Test
    void authenticationFailureIsClassified() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        org.mockito.Mockito.doThrow(new MailAuthenticationException("535 Login fail"))
                .when(mailSender).send(message);
        VerificationEmailSender sender = new VerificationEmailSender(
                mailSender, "sender@qq.com", "bad-code", "sender@qq.com", true, 2);

        VerificationMailException error = assertThrows(VerificationMailException.class,
                () -> sender.send("student@qq.com", "验证码", "body"));

        assertThat(error.getKind()).isEqualTo(VerificationMailException.Kind.AUTHENTICATION);
    }

    @Test
    void socketFailureIsClassifiedAsConnectionFailure() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        org.mockito.Mockito.doThrow(new MailSendException(
                        "connection failed", new SocketException("offline")))
                .when(mailSender).send(message);
        VerificationEmailSender sender = new VerificationEmailSender(
                mailSender, "sender@qq.com", "auth-code", "sender@qq.com", true, 2);

        VerificationMailException error = assertThrows(VerificationMailException.class,
                () -> sender.send("student@qq.com", "验证码", "body"));

        assertThat(error.getKind()).isEqualTo(VerificationMailException.Kind.CONNECTION);
    }

    @Test
    void ordinaryMailSendFailureIsClassifiedAsDeliveryFailure() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        org.mockito.Mockito.doThrow(new MailSendException("rejected"))
                .when(mailSender).send(message);
        VerificationEmailSender sender = new VerificationEmailSender(
                mailSender, "sender@qq.com", "auth-code", "sender@qq.com", true, 2);

        VerificationMailException error = assertThrows(VerificationMailException.class,
                () -> sender.send("student@qq.com", "验证码", "body"));

        assertThat(error.getKind()).isEqualTo(VerificationMailException.Kind.DELIVERY);
    }

    @Test
    void concurrentSendAboveLimitFailsFast() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            entered.countDown();
            release.await(3, TimeUnit.SECONDS);
            return null;
        }).when(mailSender).send(message);
        VerificationEmailSender sender = new VerificationEmailSender(
                mailSender, "sender@qq.com", "auth-code", "sender@qq.com", true, 1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = executor.submit(
                    () -> sender.send("first@qq.com", "验证码", "body"));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            VerificationMailException error = assertThrows(VerificationMailException.class,
                    () -> sender.send("second@qq.com", "验证码", "body"));

            assertThat(error.getKind()).isEqualTo(VerificationMailException.Kind.BUSY);
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
