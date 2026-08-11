package cn.lcxqy.starfree.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationRepositoryTest {
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement statement;
    @Mock
    private ResultSet result;
    @Mock
    private ResultSetMetaData metadata;

    @Test
    void invitationRewardConfigAcceptsBooleanEnabledValue() throws Exception {
        when(connection.prepareStatement(contains("FROM lcxqy_invitation_config")))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.getMetaData()).thenReturn(metadata);
        when(result.next()).thenReturn(true, false);
        when(metadata.getColumnCount()).thenReturn(3);
        when(metadata.getColumnLabel(1)).thenReturn("enabled");
        when(metadata.getColumnLabel(2)).thenReturn("reward_points");
        when(metadata.getColumnLabel(3)).thenReturn("reward_experience");
        when(result.getObject(1)).thenReturn(Boolean.TRUE);
        when(result.getObject(2)).thenReturn(12);
        when(result.getObject(3)).thenReturn(30);

        UserRegistrationRepository.InvitationRewardConfig config =
                new UserRegistrationRepository(jdbc).invitationRewardConfig(connection);

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getPoints()).isEqualTo(12);
        assertThat(config.getExperience()).isEqualTo(30);
    }
}
