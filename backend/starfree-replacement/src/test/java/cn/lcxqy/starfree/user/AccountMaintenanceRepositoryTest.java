package cn.lcxqy.starfree.user;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountMaintenanceRepositoryTest {
    @Test
    void uniquenessCheckConvertsUtf8mb4ParameterToLegacyUserCollation() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(contains(
                "screenName=CONVERT(? USING utf8) COLLATE utf8_general_ci")))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);

        boolean exists = new AccountMaintenanceRepository(null)
                .valueExists(connection, "screenName", "校园昵称", 7L);

        assertThat(exists).isTrue();
        verify(statement).setString(1, "校园昵称");
        verify(statement).setLong(2, 7L);
    }
}
