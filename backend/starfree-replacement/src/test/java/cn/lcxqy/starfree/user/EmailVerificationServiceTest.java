package cn.lcxqy.starfree.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.mockito.ArgumentCaptor;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationServiceTest {
    private EmailVerificationRepository repository;
    private LegacyRegistrationRedis redis;
    private VerificationEmailSender sender;
    private SecureRandom random;
    private EmailVerificationService service;

    @Test
    void springSelectsTheProductionConstructor() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(EmailVerificationRepository.class, () -> repository);
            context.registerBean(LegacyRegistrationRedis.class, () -> redis);
            context.registerBean(VerificationEmailSender.class, () -> sender);
            context.register(EmailVerificationService.class);
            context.refresh();

            assertThat(context.getBean(EmailVerificationService.class)).isNotNull();
        }
    }

    @BeforeEach
    void setUp() {
        repository = mock(EmailVerificationRepository.class);
        redis = mock(LegacyRegistrationRedis.class);
        sender = mock(VerificationEmailSender.class);
        random = new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                return 234567;
            }
        };
        service = new EmailVerificationService(repository, redis, sender, random);
        when(repository.config()).thenReturn(config(true));
    }

    @Test
    void registrationStoresSixDigitCodeUnderNormalizedEmail() {
        service.sendRegistrationCode(" User@QQ.com ", "203.0.113.7");

        verify(repository).mailExists("user@qq.com");
        verify(redis).claimEmailSend("user@qq.com", "203.0.113.7", 60, 3);
        verify(redis).storeVerificationCode("user@qq.com", "334567", 1800);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender).send(org.mockito.ArgumentMatchers.eq("user@qq.com"),
                org.mockito.ArgumentMatchers.eq("【聊一论坛】邮箱验证码"), body.capture());
        assertThat(body.getValue()).contains("334567").contains("平台用户");
    }

    @Test
    void duplicateRegistrationMailIsRejectedBeforeRateLimit() {
        when(repository.mailExists("user@qq.com")).thenReturn(true);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.sendRegistrationCode("user@qq.com", "203.0.113.7"));

        assertThat(error.getMessage()).isEqualTo("该邮箱已被注册");
        verify(redis, never()).claimEmailSend(anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void recoveryByUsernameUsesUsernameRedisKey() {
        when(repository.accountByName("student")).thenReturn(
                new EmailVerificationRepository.RecoveryAccount("student", "student@qq.com"));

        service.sendRecoveryCode("student", "203.0.113.8");

        verify(redis).storeVerificationCode("student", "334567", 1800);
        verify(sender).send(org.mockito.ArgumentMatchers.eq("student@qq.com"),
                anyString(), org.mockito.ArgumentMatchers.contains("student"));
    }

    @Test
    void recoveryByEmailResolvesAccountAndStillUsesUsernameRedisKey() {
        when(repository.accountByMail("student@qq.com")).thenReturn(
                new EmailVerificationRepository.RecoveryAccount("student", "student@qq.com"));

        service.sendRecoveryCode("STUDENT@QQ.COM", "203.0.113.8");

        verify(repository).accountByMail("student@qq.com");
        verify(redis).storeVerificationCode("student", "334567", 1800);
    }

    @Test
    void disabledEmailPolicyRejectsSending() {
        when(repository.config()).thenReturn(config(false));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.sendRegistrationCode("user@qq.com", "203.0.113.7"));

        assertThat(error.getMessage()).isEqualTo("邮箱验证已关闭");
        verify(sender, never()).requireConfigured();
    }

    @Test
    void smtpAuthenticationFailureRemovesCodeButRetainsCooldown() {
        org.mockito.Mockito.doThrow(new VerificationMailException(
                VerificationMailException.Kind.AUTHENTICATION))
                .when(sender).send(anyString(), anyString(), anyString());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.sendRegistrationCode("user@qq.com", "203.0.113.7"));

        assertThat(error.getMessage()).isEqualTo(
                "QQ邮箱拒绝登录：请检查授权码、SMTP状态或稍后再试");
        verify(redis).consumeVerificationCode("user@qq.com");
        verify(redis, never()).releaseEmailSend("user@qq.com", "203.0.113.7");
    }

    @Test
    void disabledRedisBridgeReturnsActionableMessage() {
        org.mockito.Mockito.doThrow(new IllegalStateException("disabled"))
                .when(redis).claimEmailSend("user@qq.com", "203.0.113.7", 60, 3);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.sendRegistrationCode("user@qq.com", "203.0.113.7"));

        assertThat(error.getMessage()).isEqualTo("验证码存储未配置，请联系管理员");
        verify(sender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void missingSmtpConfigurationReturnsBusinessMessageBeforeRedisWrite() {
        org.mockito.Mockito.doThrow(new VerificationMailException(
                        VerificationMailException.Kind.CONFIGURATION))
                .when(sender).requireConfigured();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.sendRegistrationCode("user@qq.com", "203.0.113.7"));

        assertThat(error.getMessage()).isEqualTo("邮件服务未配置，请联系管理员");
        verify(redis, never()).claimEmailSend(anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void templateWithoutCodePlaceholderIsRejectedAndCodeIsRemoved() {
        when(repository.config()).thenReturn(new EmailVerificationRepository.VerificationConfig(
                true, "聊一论坛", "https://prev.lcxqy.cn/", "<p>欢迎回来</p>"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.sendRegistrationCode("user@qq.com", "203.0.113.7"));

        assertThat(error.getMessage()).contains("{{code}}");
        verify(redis).consumeVerificationCode("user@qq.com");
        verify(redis).releaseEmailSend("user@qq.com", "203.0.113.7");
        verify(sender, never()).send(anyString(), anyString(), anyString());
    }

    private EmailVerificationRepository.VerificationConfig config(boolean enabled) {
        return new EmailVerificationRepository.VerificationConfig(enabled,
                "聊一论坛", "https://prev.lcxqy.cn/",
                "<p>{{userName}}，验证码：<b>{{code}}</b></p>");
    }
}
