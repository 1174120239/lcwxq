package cn.lcxqy.starfree.space;

import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Dynamic-topic persistence built on the existing {@code starfree_metas(type='tag')} catalog.
 *
 * <p>Article relationships remain in {@code starfree_relationships}. Dynamic relationships use
 * their own table because article cid and Space id are independent number sequences and can
 * collide. Existing/admin-created tags have no topic profile row and are treated as official.
 * User-created topics have a profile row with {@code is_official=0}; marking them recommended in
 * the existing admin UI promotes them into the official list without changing their identity.
 */
final class SpaceTopicService {
    static final int MAX_TOPICS_PER_SPACE = 3;
    private static final int MAX_TOPIC_NAME_LENGTH = 24;
    private static final Pattern VALID_NAME =
            Pattern.compile("[\\p{L}\\p{N}_\\-]{1," + MAX_TOPIC_NAME_LENGTH + "}");

    private static final String TOPIC_SELECT =
            "SELECT m.mid,m.name,m.slug,m.description,m.imgurl,m.isrecommend,"
                    + "CASE WHEN tm.mid IS NULL OR tm.is_official=1 OR m.isrecommend=1 "
                    + "THEN 1 ELSE 0 END AS official,"
                    + "(SELECT COUNT(*) FROM starfree_topic_follows fc WHERE fc.mid=m.mid) "
                    + "AS followCount,"
                    + "(SELECT COUNT(*) FROM starfree_space_topics sc "
                    + "JOIN starfree_space ss ON ss.id=sc.space_id "
                    + "WHERE sc.mid=m.mid AND ss.status=1 AND ss.onlyMe=0 AND ss.type<>3) "
                    + "AS spaceCount,"
                    + "CASE WHEN EXISTS(SELECT 1 FROM starfree_topic_follows uf "
                    + "WHERE uf.uid=? AND uf.mid=m.mid) THEN 1 ELSE 0 END AS isFollowed "
                    + "FROM starfree_metas m LEFT JOIN starfree_topic_meta tm ON tm.mid=m.mid ";

    private final JdbcTemplate jdbc;

    SpaceTopicService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns all, hot, official and current-user-followed topics.
     *
     * <p>Official means an existing/admin-created tag without a profile, an explicitly official
     * profile, or a topic marked recommended by the existing admin interface.
     */
    Map<String, Object> center(Long uid, String searchKey) {
        long viewerUid = uid == null ? 0L : uid;
        String keyword = searchKey == null ? "" : searchKey.trim();
        List<Object> allArgs = new ArrayList<>();
        allArgs.add(viewerUid);
        StringBuilder allSql = new StringBuilder(TOPIC_SELECT)
                .append("WHERE m.type='tag' ");
        appendSearch(allSql, allArgs, keyword);
        allSql.append("ORDER BY m.`order` DESC,spaceCount DESC,m.mid DESC");
        List<Map<String, Object>> all =
                normalize(jdbc.queryForList(allSql.toString(), allArgs.toArray()));

        List<Object> officialArgs = new ArrayList<>();
        officialArgs.add(viewerUid);
        StringBuilder officialSql = new StringBuilder(TOPIC_SELECT)
                .append("WHERE m.type='tag' ")
                .append("AND (tm.mid IS NULL OR tm.is_official=1 OR m.isrecommend=1) ");
        appendSearch(officialSql, officialArgs, keyword);
        officialSql.append("ORDER BY m.isrecommend DESC,m.`order` DESC,spaceCount DESC,m.mid DESC LIMIT 50");

        List<Map<String, Object>> official =
                normalize(jdbc.queryForList(officialSql.toString(), officialArgs.toArray()));
        List<Map<String, Object>> followed = Collections.emptyList();
        if (uid != null) {
            List<Object> followedArgs = new ArrayList<>();
            followedArgs.add(viewerUid);
            followedArgs.add(viewerUid);
            StringBuilder followedSql = new StringBuilder(TOPIC_SELECT)
                    .append("JOIN starfree_topic_follows mine ON mine.mid=m.mid AND mine.uid=? ")
                    .append("WHERE m.type='tag' ");
            appendSearch(followedSql, followedArgs, keyword);
            followedSql.append("ORDER BY mine.created DESC,m.mid DESC LIMIT 50");
            followed = normalize(jdbc.queryForList(
                    followedSql.toString(), followedArgs.toArray()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("all", all);
        List<Map<String, Object>> hot = new ArrayList<>(all);
        hot.sort((left, right) -> {
            int spaceComparison = Long.compare(number(right.get("spaceCount")),
                    number(left.get("spaceCount")));
            return spaceComparison != 0 ? spaceComparison
                    : Long.compare(number(right.get("followCount")), number(left.get("followCount")));
        });
        if (hot.size() > 30) {
            hot = new ArrayList<>(hot.subList(0, 30));
        }
        result.put("hot", hot);
        result.put("official", official);
        result.put("followed", followed);
        return result;
    }

    /** Creates or reuses a user topic and follows it for the creator. */
    Map<String, Object> create(long uid, String rawName) {
        String name = normalizeName(rawName);
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT mid FROM starfree_metas WHERE type='tag' AND name=? ORDER BY mid LIMIT 1",
                name);
        long mid;
        if (!existing.isEmpty()) {
            mid = number(existing.get(0).get("mid"));
        } else {
            String slug = "space-topic-" + UUID.nameUUIDFromBytes(
                    name.getBytes(StandardCharsets.UTF_8)).toString();
            int inserted = jdbc.update(
                    "INSERT INTO starfree_metas"
                            + "(name,slug,type,description,count,`order`,parent,imgurl,isrecommend) "
                            + "VALUES(?,?,'tag','',0,0,0,'',0)",
                    name, slug);
            if (inserted != 1) {
                throw new IllegalStateException("Topic insert did not affect exactly one row");
            }
            List<Map<String, Object>> created = jdbc.queryForList(
                    "SELECT mid FROM starfree_metas WHERE type='tag' AND slug=? "
                            + "ORDER BY mid DESC LIMIT 1", slug);
            if (created.isEmpty()) {
                throw new IllegalStateException("Topic was inserted but its id could not be read");
            }
            mid = number(created.get(0).get("mid"));
            jdbc.update(
                    "INSERT INTO starfree_topic_meta(mid,creator_uid,is_official,created) "
                            + "VALUES(?,?,0,?) ON DUPLICATE KEY UPDATE creator_uid=VALUES(creator_uid)",
                    mid, uid, Instant.now().getEpochSecond());
        }
        follow(uid, mid, 1);
        return topic(mid, uid);
    }

    /** type=1 follows and type=0 unfollows one existing tag topic. */
    int follow(long uid, long mid, int type) {
        requireTopic(mid);
        if (type == 1) {
            jdbc.update(
                    "INSERT IGNORE INTO starfree_topic_follows(uid,mid,created) VALUES(?,?,?)",
                    uid, mid, Instant.now().getEpochSecond());
            return 1;
        }
        if (type == 0) {
            jdbc.update("DELETE FROM starfree_topic_follows WHERE uid=? AND mid=?", uid, mid);
            return 1;
        }
        throw new IllegalArgumentException("话题关注参数不正确");
    }

    /** Validates a comma-separated topic id list before a Space write. */
    List<Integer> validateIds(String rawIds) {
        if (rawIds == null || rawIds.trim().isEmpty()) {
            return Collections.emptyList();
        }
        if ("0".equals(rawIds.trim())) {
            return Collections.emptyList();
        }
        Set<Integer> unique = new LinkedHashSet<>();
        for (String value : rawIds.split("[,\\s]+")) {
            if (value.trim().isEmpty()) {
                continue;
            }
            try {
                int mid = Integer.parseInt(value.trim());
                if (mid <= 0) {
                    throw new NumberFormatException();
                }
                unique.add(mid);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("话题参数不正确");
            }
        }
        if (unique.size() > MAX_TOPICS_PER_SPACE) {
            throw new IllegalArgumentException("一条动态最多选择3个话题");
        }
        if (unique.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder placeholders = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (Integer mid : unique) {
            if (placeholders.length() > 0) {
                placeholders.append(',');
            }
            placeholders.append('?');
            args.add(mid);
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_metas WHERE type='tag' AND mid IN ("
                        + placeholders + ")", Integer.class, args.toArray());
        if (count == null || count != unique.size()) {
            throw new IllegalArgumentException("话题不存在或已被删除");
        }
        return new ArrayList<>(unique);
    }

    /** Replaces every topic relation for one Space row. */
    void replace(long spaceId, List<Integer> topicIds) {
        jdbc.update("DELETE FROM starfree_space_topics WHERE space_id=?", spaceId);
        long now = Instant.now().getEpochSecond();
        for (Integer mid : topicIds) {
            jdbc.update(
                    "INSERT INTO starfree_space_topics(space_id,mid,created) VALUES(?,?,?)",
                    spaceId, mid, now);
        }
    }

    void remove(long spaceId) {
        jdbc.update("DELETE FROM starfree_space_topics WHERE space_id=?", spaceId);
    }

    List<Map<String, Object>> forSpace(long spaceId, Long uid) {
        long viewerUid = uid == null ? 0L : uid;
        return normalize(jdbc.queryForList(
                TOPIC_SELECT
                        + "JOIN starfree_space_topics st ON st.mid=m.mid "
                        + "WHERE st.space_id=? AND m.type='tag' "
                        + "ORDER BY m.isrecommend DESC,m.`order` DESC,m.mid",
                viewerUid, spaceId));
    }

    private Map<String, Object> topic(long mid, long uid) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                TOPIC_SELECT + "WHERE m.mid=? AND m.type='tag' LIMIT 1", uid, mid);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("话题不存在或已被删除");
        }
        return normalize(rows).get(0);
    }

    private void requireTopic(long mid) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_metas WHERE mid=? AND type='tag'",
                Integer.class, mid);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("话题不存在或已被删除");
        }
    }

    private void appendSearch(StringBuilder sql, List<Object> args, String keyword) {
        if (keyword.isEmpty()) {
            return;
        }
        sql.append("AND (m.name LIKE ? OR m.description LIKE ?) ");
        args.add("%" + keyword + "%");
        args.add("%" + keyword + "%");
    }

    private String normalizeName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        while (name.startsWith("#")) {
            name = name.substring(1).trim();
        }
        while (name.endsWith("#")) {
            name = name.substring(0, name.length() - 1).trim();
        }
        name = name.replaceAll("\\s+", "");
        if (!VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "话题名只能包含中英文、数字、下划线或短横线，长度1到24个字符");
        }
        return name;
    }

    private List<Map<String, Object>> normalize(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("mid", number(row.get("mid")));
            item.put("name", text(row.get("name")));
            item.put("slug", text(row.get("slug")));
            item.put("description", text(row.get("description")));
            item.put("imgurl", text(row.get("imgurl")));
            item.put("isrecommend", number(row.get("isrecommend")));
            item.put("official", number(row.get("official")));
            item.put("followCount", number(row.get("followCount")));
            item.put("spaceCount", number(row.get("spaceCount")));
            item.put("isFollowed", number(row.get("isFollowed")));
            result.add(item);
        }
        return result;
    }

    private long number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
