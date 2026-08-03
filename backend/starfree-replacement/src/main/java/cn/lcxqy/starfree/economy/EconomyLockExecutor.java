package cn.lcxqy.starfree.economy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Runs one economy operation while holding the shared MySQL advisory lock.
 *
 * All statements inside the callback must use the supplied connection. The
 * legacy tables are MyISAM, so a Spring transaction cannot protect a multi-row
 * transfer. One global lock is deliberately conservative: rewards, purchases,
 * withdrawals, manual adjustments, and wrapped official recharge callbacks
 * cannot overwrite one another inside the replacement boundary.
 */
@Component
public class EconomyLockExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(EconomyLockExecutor.class);

    public static final String GLOBAL_LOCK = "starfree:economy:global";

    private final DataSource dataSource;
    private final int timeoutSeconds;

    public EconomyLockExecutor(
            DataSource dataSource,
            @Value("${economy.lock-timeout-seconds:10}") int timeoutSeconds) {
        this.dataSource = dataSource;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    public <T> T execute(SqlWork<T> work) {
        // Use a physical connection that is independent of any caller transaction.
        // The InnoDB journal must commit before the advisory lock is released; a
        // transaction-bound JdbcTemplate connection would leave a duplicate window.
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getAutoCommit()) {
                connection.setAutoCommit(true);
            }
            if (!acquire(connection)) {
                throw new IllegalArgumentException(
                        "\u8d26\u52a1\u7cfb\u7edf\u6b63\u5fd9\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5");
            }
            try {
                return work.execute(connection);
            } finally {
                release(connection);
            }
        } catch (SQLException error) {
            throw new DataAccessResourceFailureException(
                    "Could not execute the serialized economy operation", error);
        }
    }

    private boolean acquire(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, GLOBAL_LOCK);
            statement.setInt(2, timeoutSeconds);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }

    private void release(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, GLOBAL_LOCK);
            try (ResultSet ignored = statement.executeQuery()) {
                // Reading the result makes Connector/J complete the statement before close.
            }
        } catch (SQLException error) {
            LOG.error("Could not release the global economy lock", error);
        }
    }

    @FunctionalInterface
    public interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
