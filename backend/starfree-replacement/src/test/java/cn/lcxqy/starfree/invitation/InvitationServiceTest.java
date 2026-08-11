package cn.lcxqy.starfree.invitation;

import cn.lcxqy.starfree.security.LegacyTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private LegacyTokenService tokens;

    @Test
    void publicConfigReturnsInviterAndDownloadSettings() {
        when(jdbc.queryForList(contains("FROM lcxqy_invitation_config")))
                .thenReturn(Collections.singletonList(row(
                        "enabled", Boolean.TRUE,
                        "reward_points", 10,
                        "reward_experience", 20,
                        "android_download_url", "https://download.example/app.apk",
                        "ios_download_url", "https://apps.apple.com/app/id1")));
        when(jdbc.queryForList(contains("FROM lcxqy_invitation_codes"), eq("LYCODE1234")))
                .thenReturn(Collections.singletonList(row(
                        "uid", 7L,
                        "invite_code", "LYCODE1234",
                        "screenName", "云云",
                        "name", "yunyun",
                        "avatar", "https://cdn.example/avatar.jpg")));

        Map<String, Object> result = new InvitationService(jdbc, tokens)
                .publicConfig("lycode1234");

        assertThat(result).containsEntry("enabled", true)
                .containsEntry("validInvite", true)
                .containsEntry("rewardPoints", 10L)
                .containsEntry("rewardExperience", 20L)
                .containsEntry("androidDownloadUrl", "https://download.example/app.apk");
        Map<?, ?> inviter = (Map<?, ?>) result.get("inviter");
        assertThat(inviter.get("uid")).isEqualTo(7L);
        assertThat(inviter.get("name")).isEqualTo("云云");
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }
}
