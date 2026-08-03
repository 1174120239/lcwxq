package cn.lcxqy.starfree.economy;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EconomyServiceTest {
    @Test
    void dailyExperienceStopsAtThreeAwards() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L),
                any(Integer.class), eq("postExp"))).thenReturn(3);

        boolean granted = new EconomyService(jdbc)
                .grantDailyExperience(7L, "postExp", 5);

        assertThat(granted).isFalse();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void clockCannotBypassTheNamedLockAndJournal() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        assertThatThrownBy(() -> new EconomyService(jdbc).clock(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Economy lock and journal are required");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void invalidRewardIsRejectedBeforeFinancialInfrastructure() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        assertThatThrownBy(() -> new EconomyService(jdbc).reward(7L, 11L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u53c2\u6570\u4e0d\u6b63\u786e");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void contentAuditLevelTwoStillPublishesForStaff() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> config = new HashMap<>();
        config.put("contentAuditlevel", 2);
        when(jdbc.queryForList(anyString())).thenReturn(Collections.singletonList(config));

        String status = new EconomyService(jdbc)
                .contentStatus("administrator", "title", "body");

        assertThat(status).isEqualTo("publish");
    }
}
