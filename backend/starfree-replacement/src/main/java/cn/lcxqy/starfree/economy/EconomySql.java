package cn.lcxqy.starfree.economy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JDBC helpers for work that must stay on the named-lock connection. */
final class EconomySql {
    private EconomySql() {
    }

    static int update(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            return statement.executeUpdate();
        }
    }

    static long insertKey(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, args);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Insert did not affect exactly one row");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Insert did not return a generated key");
                }
                return keys.getLong(1);
            }
        }
    }

    static Map<String, Object> one(Connection connection, String sql, Object... args)
            throws SQLException {
        List<Map<String, Object>> rows = list(connection, sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    static List<Map<String, Object>> list(Connection connection, String sql, Object... args)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet result = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData metadata = result.getMetaData();
                int columns = metadata.getColumnCount();
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int column = 1; column <= columns; column++) {
                        row.put(metadata.getColumnLabel(column), result.getObject(column));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    static long number(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... args) throws SQLException {
        for (int index = 0; index < args.length; index++) {
            statement.setObject(index + 1, args[index]);
        }
    }
}
