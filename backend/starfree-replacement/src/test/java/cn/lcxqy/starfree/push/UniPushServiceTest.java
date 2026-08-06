package cn.lcxqy.starfree.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class UniPushServiceTest {
    @Test
    void disabledPushDoesNotReadClientIdOrCallProvider() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RestTemplate http = mock(RestTemplate.class);
        UniPushService push = new UniPushService(
                jdbc, http, new ObjectMapper(), false,
                "app-id", "app-key", "app-secret", "https://restapi.getui.com");

        push.sendComment(7L, "title", "body", "spaceComment:11");

        verifyNoInteractions(jdbc, http);
    }
}
