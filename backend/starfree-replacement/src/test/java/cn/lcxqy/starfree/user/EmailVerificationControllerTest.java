package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.api.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailVerificationControllerTest {
    private EmailVerificationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(EmailVerificationService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new EmailVerificationController(service, new ObjectMapper()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void registrationCodeSupportsLegacyGetFormProtocol() throws Exception {
        mvc.perform(get("/SFreeUsers/RegSendCode")
                        .param("params", "{\"mail\":\"user@qq.com\"}")
                        .header("X-Real-IP", "203.0.113.7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("邮件发送成功"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("123456"))));

        verify(service).sendRegistrationCode("user@qq.com", "203.0.113.7");
    }

    @Test
    void recoveryCodeSupportsPostAndForwardedAddress() throws Exception {
        mvc.perform(post("/SFreeUsers/SendCode")
                        .param("params", "{\"name\":\"student\"}")
                        .header("X-Forwarded-For", "198.51.100.4, 10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(service).sendRecoveryCode("student", "198.51.100.4");
    }
}
