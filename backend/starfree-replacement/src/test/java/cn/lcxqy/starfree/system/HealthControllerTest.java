package cn.lcxqy.starfree.system;

import cn.lcxqy.starfree.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @Test
    void healthReportsTheConnectedDatabaseInsteadOfALocalConstant() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("lcxqy");

        ApiResponse response = new HealthController(jdbc).health();

        assertThat(response.getCode()).isEqualTo(1);
        Map<?, ?> data = (Map<?, ?>) response.getData();
        assertThat(data.get("status")).isEqualTo("UP");
        assertThat(data.get("database")).isEqualTo("lcxqy");
    }
}
