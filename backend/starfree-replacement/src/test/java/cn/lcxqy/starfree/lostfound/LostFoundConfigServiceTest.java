package cn.lcxqy.starfree.lostfound;

import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LostFoundConfigServiceTest {
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private LegacyTokenService tokens;

    private LostFoundConfigService service;

    @BeforeEach
    void setUp() {
        service = new LostFoundConfigService(jdbc, new StaffAccess(tokens), tokens);
    }

    @Test
    void defaultConfigurationRequiresLevelTwoAndFiftyExperience() {
        when(jdbc.queryForList(contains("FROM starfree_lost_found_config")))
                .thenReturn(Collections.<Map<String, Object>>emptyList());

        Map<String, Object> config = service.publicConfig("");

        assertThat(config).containsEntry("minimumLevel", 2)
                .containsEntry("minimumExperience", 50)
                .containsEntry("eligible", false);
    }

    @Test
    void levelOneUserCannotParticipate() {
        when(jdbc.queryForList(contains("FROM starfree_lost_found_config")))
                .thenReturn(Collections.<Map<String, Object>>emptyList());
        authenticated("low-token", 7, "contributor", 49);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.requireParticipant("low-token"));

        assertThat(error.getMessage()).contains("Lv2");
    }

    @Test
    void administratorCanSaveAllowlistedSettings() {
        authenticated("admin-token", 1, "administrator", 0);
        when(jdbc.queryForList(contains("FROM starfree_lost_found_config")))
                .thenReturn(Collections.singletonList(row("enabled", 1, "minimum_level", 3,
                        "audit_required", 1, "contact_enabled", 1,
                        "daily_contact_limit", 8, "item_expiry_days", 45)));
        Map<String, Object> body = row("enabled", 1, "minimumLevel", 3,
                "auditRequired", 1, "contactEnabled", 1,
                "dailyContactLimit", 8, "itemExpiryDays", 45);

        Map<String, Object> saved = service.save("admin-token", body);

        assertThat(saved).containsEntry("minimumLevel", 3).containsEntry("dailyContactLimit", 8);
        verify(jdbc).update(contains("UPDATE starfree_lost_found_config SET"),
                eq(1), eq(3), eq(1), eq(1), eq(8), eq(45), eq(1L), any(Long.class));
    }

    private void authenticated(String token, long uid, String group, long experience) {
        when(tokens.userId(token)).thenReturn(uid);
        when(tokens.userById(uid)).thenReturn(row("uid", uid, "name", "user" + uid,
                "group", group, "experience", experience, "bantime", 0));
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }
}
