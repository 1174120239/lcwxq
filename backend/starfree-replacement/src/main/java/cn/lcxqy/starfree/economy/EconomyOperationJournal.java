package cn.lcxqy.starfree.economy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists idempotency state independently from the legacy MyISAM projections.
 *
 * A committed entry is replayed as success for the same requestId. A lingering
 * started entry is never retried automatically because the process may have
 * stopped after changing a MyISAM balance. That case is surfaced for audit
 * instead of risking a second debit or credit.
 */
@Component
public class EconomyOperationJournal {
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() { };

    private final ObjectMapper mapper;

    public EconomyOperationJournal(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String requestKey(String type, long actorUid, String requestId, String fingerprint) {
        String id = normalizeRequestId(requestId);
        if (id.isEmpty()) {
            // Legacy clients have no requestId. The short bucket preserves the old
            // anti-double-click behavior while allowing intentional later repeats.
            id = "legacy:" + (Instant.now().getEpochSecond() / 5L) + ":" + fingerprint;
        }
        return type + ":" + sha256(actorUid + ":" + id);
    }

    public String fixedKey(String type, long referenceId) {
        return type + ":" + sha256(String.valueOf(referenceId));
    }

    /** Stable key for natural identifiers such as a normalized registration account. */
    public String fixedKey(String type, String naturalId) {
        return type + ":" + sha256(naturalId == null ? "" : naturalId);
    }

    public BeginResult begin(Connection connection, String operationKey, String type,
                             long actorUid, long targetUid, long referenceId,
                             Map<String, Object> payload) throws SQLException {
        Existing existing = find(connection, operationKey);
        if (existing != null) {
            if ("committed".equals(existing.state)) {
                return BeginResult.replay(readMap(existing.resultJson));
            }
            if ("failed".equals(existing.state)) {
                restart(connection, operationKey, payload);
                return BeginResult.started();
            }
            throw new IllegalArgumentException(
                    "\u4e0a\u6b21\u8d26\u52a1\u64cd\u4f5c\u72b6\u6001\u5f85\u6838\u5bf9\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u5e76\u63d0\u4f9brequestId");
        }

        long now = Instant.now().getEpochSecond();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO starfree_economy_operations "
                        + "(operation_key,operation_type,state,actor_uid,target_uid,reference_id,"
                        + "payload_json,created,updated) VALUES (?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, operationKey);
            statement.setString(2, type);
            statement.setString(3, "started");
            statement.setLong(4, actorUid);
            statement.setLong(5, targetUid);
            statement.setLong(6, referenceId);
            statement.setString(7, writeMap(payload));
            statement.setLong(8, now);
            statement.setLong(9, now);
            statement.executeUpdate();
        }
        return BeginResult.started();
    }

    public void commit(Connection connection, String operationKey, Map<String, Object> result)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE starfree_economy_operations SET state='committed',result_json=?,"
                        + "last_error=NULL,updated=? WHERE operation_key=? AND state='started'")) {
            statement.setString(1, writeMap(result));
            statement.setLong(2, Instant.now().getEpochSecond());
            statement.setString(3, operationKey);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Economy journal commit did not affect one started row");
            }
        }
    }

    public void fail(Connection connection, String operationKey, Throwable error) throws SQLException {
        String message = error == null ? "Unknown economy operation failure" : error.toString();
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE starfree_economy_operations SET state='failed',last_error=?,updated=? "
                        + "WHERE operation_key=? AND state='started'")) {
            statement.setString(1, message);
            statement.setLong(2, Instant.now().getEpochSecond());
            statement.setString(3, operationKey);
            statement.executeUpdate();
        }
    }

    public void needsReview(Connection connection, String operationKey, Throwable error)
            throws SQLException {
        String message = error == null ? "Compensation state is unknown" : error.toString();
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE starfree_economy_operations SET state='needs_review',last_error=?,updated=? "
                        + "WHERE operation_key=?")) {
            statement.setString(1, message);
            statement.setLong(2, Instant.now().getEpochSecond());
            statement.setString(3, operationKey);
            statement.executeUpdate();
        }
    }

    private Existing find(Connection connection, String operationKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT state,result_json FROM starfree_economy_operations "
                        + "WHERE operation_key=? LIMIT 1")) {
            statement.setString(1, operationKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new Existing(result.getString("state"), result.getString("result_json"))
                        : null;
            }
        }
    }

    private void restart(Connection connection, String operationKey, Map<String, Object> payload)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE starfree_economy_operations SET state='started',payload_json=?,"
                        + "result_json=NULL,last_error=NULL,updated=? "
                        + "WHERE operation_key=? AND state='failed'")) {
            statement.setString(1, writeMap(payload));
            statement.setLong(2, Instant.now().getEpochSecond());
            statement.setString(3, operationKey);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Failed economy operation could not be restarted");
            }
        }
    }

    private String writeMap(Map<String, Object> value) throws SQLException {
        try {
            return mapper.writeValueAsString(value == null
                    ? Collections.<String, Object>emptyMap() : value);
        } catch (Exception error) {
            throw new SQLException("Could not serialize economy journal data", error);
        }
    }

    private Map<String, Object> readMap(String value) throws SQLException {
        if (value == null || value.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return mapper.readValue(value, MAP_TYPE);
        } catch (Exception error) {
            throw new SQLException("Could not read committed economy result", error);
        }
    }

    private String normalizeRequestId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static final class Existing {
        private final String state;
        private final String resultJson;

        private Existing(String state, String resultJson) {
            this.state = state;
            this.resultJson = resultJson;
        }
    }

    public static final class BeginResult {
        private final boolean replay;
        private final Map<String, Object> result;

        private BeginResult(boolean replay, Map<String, Object> result) {
            this.replay = replay;
            this.result = result;
        }

        public static BeginResult started() {
            return new BeginResult(false, Collections.<String, Object>emptyMap());
        }

        public static BeginResult replay(Map<String, Object> result) {
            return new BeginResult(true, result);
        }

        public boolean isReplay() {
            return replay;
        }

        public Map<String, Object> getResult() {
            return result;
        }
    }
}
