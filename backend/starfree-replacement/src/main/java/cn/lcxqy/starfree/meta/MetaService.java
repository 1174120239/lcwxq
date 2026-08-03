package cn.lcxqy.starfree.meta;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.cache.LegacyProjectionCacheInvalidator;
import cn.lcxqy.starfree.content.ContentService;
import cn.lcxqy.starfree.security.StaffAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetaService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ContentService contents;
    private final StaffAccess access;
    private final LegacyProjectionCacheInvalidator caches;

    public MetaService(JdbcTemplate jdbc, ObjectMapper mapper, ContentService contents,
                       StaffAccess access, LegacyProjectionCacheInvalidator caches) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.contents = contents;
        this.access = access;
        this.caches = caches;
    }

    public List<Map<String, Object>> list(String searchParams, int limit, int page, String order) {
        return page(searchParams, limit, page, order, "").getData();
    }

    public MetaPage page(String searchParams, int limit, int page, String order, String searchKey) {
        Map<String, Object> filters = RequestValues.jsonObject(mapper, searchParams);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        int safePage = Math.max(1, page);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" FROM starfree_metas WHERE 1=1");
        String type = RequestValues.objectText(filters, "type");
        if (!type.isEmpty()) {
            where.append(" AND type = ?");
            args.add(type);
        }
        appendIntegerFilter(where, args, filters, "parent");
        appendIntegerFilter(where, args, filters, "isrecommend");
        String keyword = searchKey == null ? "" : searchKey.trim();
        if (!keyword.isEmpty()) {
            where.append(" AND (name LIKE ? OR slug LIKE ? OR description LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        Integer totalValue = jdbc.queryForObject("SELECT COUNT(*)" + where, Integer.class, args.toArray());
        String sql = "SELECT mid,name,slug,type,description,count,`order`,parent,imgurl,isrecommend"
                + where + " ORDER BY " + safeOrder(order) + " DESC, mid ASC LIMIT ?, ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((safePage - 1) * safeLimit);
        pageArgs.add(safeLimit);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, pageArgs.toArray());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(toLegacyMeta(row));
        }
        return new MetaPage(result, totalValue == null ? 0 : totalValue);
    }

    public Map<String, Object> info(long mid, String slug) {
        String sql;
        Object[] args;
        if (mid > 0) {
            sql = "SELECT mid,name,slug,type,description,count,`order`,parent,imgurl,isrecommend FROM starfree_metas WHERE mid = ? LIMIT 1";
            args = new Object[]{mid};
        } else {
            sql = "SELECT mid,name,slug,type,description,count,`order`,parent,imgurl,isrecommend FROM starfree_metas WHERE slug = ? LIMIT 1";
            args = new Object[]{slug};
        }
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : toLegacyMeta(rows.get(0));
    }

    public List<Map<String, Object>> contents(String searchParams, int limit, int page, String order) {
        return contentsPage(searchParams, limit, page, order, "", "").getData();
    }

    public ContentService.ContentPage contentsPage(String searchParams, int limit, int page, String order,
                                                   String searchKey, String token) {
        return contents.page(searchParams, limit, page, order, searchKey, 0, token);
    }

    /**
     * Creates one category or tag from an explicit field allowlist.
     *
     * <p>Name and slug are unique within the selected type. Derived relationship count is always
     * initialized to zero and cannot be supplied by the client. Only categories may have a parent.
     */
    public int add(String token, Map<String, Object> params) {
        access.requireAdministrator(token);
        String type = requiredType(RequestValues.objectText(params, "type"));
        String name = requiredText(params, "name", 200);
        String slug = requiredText(params, "slug", 200);
        String description = optionalText(params, "description", 200);
        String image = optionalText(params, "imgurl", 500);
        int order = nonNegative(params, "orderKey", 0);
        int parent = nonNegative(params, "parent", 0);
        int recommended = binary(params, "isrecommend", 0);
        validateUnique(0, type, name, slug);
        validateParent(0, type, parent);

        int changed = jdbc.update(
                "INSERT INTO starfree_metas(name,slug,type,description,count,`order`,parent,imgurl,isrecommend) "
                        + "VALUES(?,?,?,?,0,?,?,?,?)",
                name, slug, type, description, order, parent, image, recommended);
        if (changed > 0) {
            caches.afterMetaWrite();
            caches.afterDashboardCountWrite();
        }
        return changed;
    }

    /**
     * Updates one existing meta without permitting type or relationship-count changes.
     * Missing optional fields retain their old values; an explicitly present empty description or
     * image clears that value. At least one editable field must be supplied.
     */
    public int edit(String token, Map<String, Object> params) {
        access.requireAdministrator(token);
        long mid = number(params.get("mid"));
        Map<String, Object> old = rawInfo(mid);
        if (old == null) {
            throw new IllegalArgumentException("数据不存在");
        }
        String type = text(old, "type");
        String name = params.containsKey("name")
                ? requiredText(params, "name", 200) : text(old, "name");
        String slug = params.containsKey("slug")
                ? requiredText(params, "slug", 200) : text(old, "slug");
        validateUnique(mid, type, name, slug);

        Map<String, Object> changes = new LinkedHashMap<>();
        copyIfPresent(params, changes, "name", name);
        copyIfPresent(params, changes, "slug", slug);
        if (params.containsKey("description")) {
            changes.put("description", optionalText(params, "description", 200));
        }
        if (params.containsKey("imgurl")) {
            changes.put("imgurl", optionalText(params, "imgurl", 500));
        }
        if (params.containsKey("orderKey")) {
            changes.put("order", nonNegative(params, "orderKey", 0));
        }
        if (params.containsKey("isrecommend")) {
            changes.put("isrecommend", binary(params, "isrecommend", 0));
        }
        if (params.containsKey("parent")) {
            int parent = nonNegative(params, "parent", 0);
            validateParent(mid, type, parent);
            changes.put("parent", parent);
        }
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("没有可保存的字段");
        }

        StringBuilder sql = new StringBuilder("UPDATE starfree_metas SET ");
        List<Object> values = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, Object> change : changes.entrySet()) {
            if (index++ > 0) {
                sql.append(',');
            }
            sql.append('`').append(change.getKey()).append("`=?");
            values.add(change.getValue());
        }
        sql.append(" WHERE mid=?");
        values.add(mid);
        int changed = jdbc.update(sql.toString(), values.toArray());
        if (changed > 0) {
            caches.afterMetaWrite();
        }
        return changed;
    }

    /**
     * Deletes a meta and every relationship referring to it in one MySQL statement.
     *
     * <p>Categories and article tags still use {@code starfree_relationships}. Dynamic Space
     * topics reuse {@code starfree_metas(type='tag')} as their visible catalog, but keep their own
     * link/follow/profile tables. MyISAM has no foreign keys, so deleting an admin topic must clear
     * every derived row here; otherwise the front-end lists would hide the deleted topic name but
     * keep stale follow/topic counters around.
     */
    public int delete(String token, long mid) {
        access.requireAdministrator(token);
        if (rawInfo(mid) == null) {
            throw new IllegalArgumentException("数据不存在");
        }
        int changed = jdbc.update(
                "DELETE m,r,st,tf,tm FROM starfree_metas m "
                        + "LEFT JOIN starfree_relationships r ON r.mid=m.mid "
                        + "LEFT JOIN starfree_space_topics st ON st.mid=m.mid "
                        + "LEFT JOIN starfree_topic_follows tf ON tf.mid=m.mid "
                        + "LEFT JOIN starfree_topic_meta tm ON tm.mid=m.mid "
                        + "WHERE m.mid=?", mid);
        if (changed > 0) {
            caches.afterMetaWrite();
            caches.afterDashboardCountWrite();
            return 1;
        }
        return 0;
    }

    /** Sets the home-page recommendation flag to exactly zero or one. */
    public int recommend(String token, long mid, int recommended) {
        access.requireAdministrator(token);
        if (recommended != 0 && recommended != 1) {
            throw new IllegalArgumentException("参数错误");
        }
        if (rawInfo(mid) == null) {
            throw new IllegalArgumentException("数据不存在");
        }
        int changed = jdbc.update(
                "UPDATE starfree_metas SET isrecommend=? WHERE mid=?", recommended, mid);
        if (changed > 0) {
            caches.afterMetaWrite();
        }
        return changed;
    }

    private Map<String, Object> rawInfo(long mid) {
        if (mid <= 0) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT mid,name,slug,type,parent FROM starfree_metas WHERE mid=? LIMIT 1", mid);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void validateUnique(long mid, String type, String name, String slug) {
        Integer sameName = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_metas WHERE type=? AND name=? AND mid<>?",
                Integer.class, type, name, mid);
        if (sameName != null && sameName > 0) {
            throw new IllegalArgumentException("已存在同名数据");
        }
        Integer sameSlug = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_metas WHERE type=? AND slug=? AND mid<>?",
                Integer.class, type, slug, mid);
        if (sameSlug != null && sameSlug > 0) {
            throw new IllegalArgumentException("已存在同Slug数据");
        }
    }

    private void validateParent(long mid, String type, int parent) {
        if (parent == 0) {
            return;
        }
        if (!"category".equals(type)) {
            throw new IllegalArgumentException("标签不能设置父级");
        }
        if (parent == mid) {
            throw new IllegalArgumentException("父分类不能是自身");
        }
        long current = parent;
        for (int depth = 0; depth < 100; depth++) {
            Map<String, Object> row = rawInfo(current);
            if (row == null || !"category".equals(text(row, "type"))) {
                throw new IllegalArgumentException("父分类不存在");
            }
            long next = number(valueIgnoreCase(row, "parent"));
            if (next == 0) {
                return;
            }
            if (next == mid) {
                throw new IllegalArgumentException("分类层级不能形成循环");
            }
            current = next;
        }
        throw new IllegalArgumentException("分类层级过深或存在循环");
    }

    private String requiredType(String type) {
        if (!"category".equals(type) && !"tag".equals(type)) {
            throw new IllegalArgumentException("类型参数不正确");
        }
        return type;
    }

    private String requiredText(Map<String, Object> params, String key, int maximum) {
        String value = RequestValues.objectText(params, key);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(key + "为必填字段");
        }
        if (value.length() > maximum) {
            throw new IllegalArgumentException(key + "超过最大长度");
        }
        return value;
    }

    private String optionalText(Map<String, Object> params, String key, int maximum) {
        String value = params.get(key) == null ? "" : String.valueOf(params.get(key)).trim();
        if (value.length() > maximum) {
            throw new IllegalArgumentException(key + "超过最大长度");
        }
        return value;
    }

    private int nonNegative(Map<String, Object> params, String key, int fallback) {
        int value = RequestValues.objectInteger(params, key, fallback);
        if (value < 0) {
            throw new IllegalArgumentException(key + "不能小于0");
        }
        return value;
    }

    private int binary(Map<String, Object> params, String key, int fallback) {
        int value = RequestValues.objectInteger(params, key, fallback);
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException(key + "只能是0或1");
        }
        return value;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target,
                               String key, Object value) {
        if (source.containsKey(key)) {
            target.put(key, value);
        }
    }

    private long number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value == null ? "" : String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String text(Map<String, Object> row, String key) {
        Object value = valueIgnoreCase(row, key);
        return value == null ? "" : String.valueOf(value);
    }

    private Object valueIgnoreCase(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void appendIntegerFilter(StringBuilder sql, List<Object> args, Map<String, Object> filters,
                                     String key) {
        if (filters.containsKey(key)) {
            sql.append(" AND `").append(key).append("` = ?");
            args.add(RequestValues.objectInteger(filters, key, 0));
        }
    }

    private String safeOrder(String order) {
        if ("count".equals(order)) {
            return "count";
        }
        if ("name".equals(order)) {
            return "name";
        }
        return "`order`";
    }

    private Map<String, Object> toLegacyMeta(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getValue() != null) {
                result.put("order".equals(entry.getKey()) ? "orderKey" : entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public static final class MetaPage {
        private final List<Map<String, Object>> data;
        private final int total;

        MetaPage(List<Map<String, Object>> data, int total) {
            this.data = data;
            this.total = total;
        }

        public List<Map<String, Object>> getData() {
            return data;
        }

        public int getTotal() {
            return total;
        }
    }
}
