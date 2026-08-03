package cn.lcxqy.starfree.economy;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EconomyLockExecutorTest {
    @Test
    void operationAndNamedLockUseOneIndependentAutoCommitConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        PreparedStatement release = mock(PreparedStatement.class);
        ResultSet acquireResult = mock(ResultSet.class);
        ResultSet releaseResult = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(false);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.contains("GET_LOCK") ? acquire : release;
        });
        when(acquire.executeQuery()).thenReturn(acquireResult);
        when(acquireResult.next()).thenReturn(true);
        when(acquireResult.getInt(1)).thenReturn(1);
        when(release.executeQuery()).thenReturn(releaseResult);

        EconomyLockExecutor executor = new EconomyLockExecutor(dataSource, 10);
        String result = executor.execute(supplied -> {
            assertThat(supplied).isSameAs(connection);
            return "done";
        });

        assertThat(result).isEqualTo("done");
        verify(connection).setAutoCommit(true);
        verify(connection).close();
        verify(acquire).setString(1, EconomyLockExecutor.GLOBAL_LOCK);
        verify(acquire).setInt(2, 10);
        verify(release).setString(1, EconomyLockExecutor.GLOBAL_LOCK);
    }
}
