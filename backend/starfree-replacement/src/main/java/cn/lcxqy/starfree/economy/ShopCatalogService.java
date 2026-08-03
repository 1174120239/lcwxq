package cn.lcxqy.starfree.economy;

import cn.lcxqy.starfree.cache.LegacyProjectionCacheInvalidator;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Product catalog, product administration, and VIP-package reads backed by the legacy schema.
 *
 * <p>The tables are MyISAM in the supplied database. A Spring transaction therefore cannot make
 * product, notification, dynamic, and cache changes atomic. The product row is authoritative:
 * secondary notification/dynamic/cache failures are logged after the product write and are not
 * reported as a failed write, because a client retry could otherwise create or repeat data.
 *
 * <p>Every write uses a fixed SQL column set or a server-side allowlist. Client-supplied uid,
 * status, created, sellNum, and cid fields are ignored. Identity and staff role are always reloaded
 * from the token through {@link StaffAccess}.
 */
@Service
public class ShopCatalogService {
    private static final Logger LOG = LoggerFactory.getLogger(ShopCatalogService.class);
    private static final int MAX_TITLE = 300;
    private static final int MAX_IMAGE = 500;
    private static final int MAX_DESCRIPTION = 10000;
    private static final int MAX_VALUE = 60000;
    private static final int MAX_PAGE_SIZE = 50;
    private static final Pattern DANGEROUS_CODE = Pattern.compile(
            "<\\s*(script|form|iframe|frame)\\b|javascript\\s*:|\\beval\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    private static final String SHOP_COLUMNS =
            "s.id,s.title,s.imgurl,s.text,s.price,s.integral,s.num,s.type,s.value,s.cid,"
                    + "s.uid,s.created,s.status,s.vipDiscount,s.sellNum,s.isMd,s.sort,"
                    + "s.subtype,s.isView";
    private static final String SHOP_WITH_USER = SHOP_COLUMNS
            + ",u.uid AS user_uid,u.name AS user_name,u.screenName AS user_screenName,"
            + "u.mail AS user_mail,u.`group` AS user_group,u.avatar AS user_avatar,"
            + "u.customize AS user_customize,u.experience AS user_experience,"
            + "u.introduce AS user_introduce,u.bantime AS user_bantime,u.ip AS user_ip,"
            + "u.local AS user_local,u.vip AS user_vip";

    private static final List<String> PRODUCT_KEYS = Collections.unmodifiableList(Arrays.asList(
            "id", "title", "imgurl", "text", "price", "integral", "num", "type",
            "value", "cid", "uid", "created", "status", "vipDiscount", "sellNum",
            "isMd", "sort", "subtype", "isView"));

    private final JdbcTemplate jdbc;
    private final LegacyTokenService tokens;
    private final StaffAccess access;
    private final LegacyProjectionCacheInvalidator caches;

    public ShopCatalogService(JdbcTemplate jdbc, LegacyTokenService tokens, StaffAccess access,
                              LegacyProjectionCacheInvalidator caches) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.access = access;
        this.caches = caches;
    }

    /**
     * Lists products using a strict filter and order allowlist.
     *
     * <p>For compatibility, a uid/status filter remains readable without authentication because
     * the existing user-shop and management pages do not send a token. Sensitive {@code value}
     * is still removed unless the token belongs to the owner or staff. New clients should always
     * send token when reading their own drafts; this legacy metadata visibility is intentionally
     * documented and must not be expanded to private value/contact data.
     */
    public ShopPage page(Map<String, Object> filters, String searchKey, String requestedOrder,
                         int requestedPage, int requestedLimit, String token) {
        int page = Math.max(1, requestedPage);
        int limit = Math.max(1, Math.min(requestedLimit, MAX_PAGE_SIZE));
        int offset = (page - 1) * limit;
        Viewer viewer = viewer(token);

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> arguments = new ArrayList<>();
        addNumericFilter(where, arguments, filters, "id", "s.id", 1, Integer.MAX_VALUE);
        addNumericFilter(where, arguments, filters, "type", "s.type", 1, 4);
        addNumericFilter(where, arguments, filters, "cid", "s.cid", -1, Integer.MAX_VALUE);
        addNumericFilter(where, arguments, filters, "uid", "s.uid", 1, Integer.MAX_VALUE);
        addNumericFilter(where, arguments, filters, "status", "s.status", 0, 2);
        addNumericFilter(where, arguments, filters, "isMd", "s.isMd", 0, 1);
        addNumericFilter(where, arguments, filters, "sort", "s.sort", 0, Integer.MAX_VALUE);
        addNumericFilter(where, arguments, filters, "subtype", "s.subtype", 0, Integer.MAX_VALUE);
        addNumericFilter(where, arguments, filters, "isView", "s.isView", 0, 1);
        String keyword = clean(searchKey);
        if (!keyword.isEmpty()) {
            where.append(" AND (s.title LIKE ? OR s.text LIKE ?)");
            String like = "%" + keyword + "%";
            arguments.add(like);
            arguments.add(like);
        }

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_shop s" + where,
                Integer.class, arguments.toArray());
        String order = orderColumn(requestedOrder);
        List<Object> pagedArguments = new ArrayList<>(arguments);
        pagedArguments.add(offset);
        pagedArguments.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + SHOP_WITH_USER + " FROM starfree_shop s "
                        + "LEFT JOIN starfree_users u ON u.uid=s.uid" + where
                        + " ORDER BY " + order + " DESC,s.id DESC LIMIT ?,?",
                pagedArguments.toArray());
        String avatarPrefix = avatarPrefix();
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> product = product(row);
            long ownerUid = number(get(row, "uid"));
            if (!viewer.canManage(ownerUid)) {
                product.remove("value");
            }
            product.put("userJson", publicUser(row, avatarPrefix));
            data.add(product);
        }
        return new ShopPage(data, total == null ? 0 : total);
    }

    /**
     * Reads one product and applies status/value visibility independently.
     *
     * <p>Published products are public. Pending/rejected products are visible only to their owner
     * or staff. Paid value is visible only to owner, staff, or a token-bound purchaser with a
     * {@code starfree_userlog(type=buy, cid=sid)} row. Missing and unauthorized products both
     * return an empty map so callers cannot distinguish hidden moderation records.
     */
    public Map<String, Object> info(long shopId, String token) {
        if (shopId <= 0) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + SHOP_COLUMNS + " FROM starfree_shop s WHERE s.id=? LIMIT 1", shopId);
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> row = rows.get(0);
        Viewer viewer = viewer(token);
        long ownerUid = number(get(row, "uid"));
        int status = integer(get(row, "status"), 0);
        if (status != 1 && !viewer.canManage(ownerUid)) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = product(row);
        if (!viewer.canManage(ownerUid) && !hasPurchased(viewer.uid, shopId)) {
            result.remove("value");
        }
        return result;
    }

    /**
     * Creates a product and returns its generated id.
     *
     * <p>Accepted fields are title, type, imgurl, price, integral, num, value, vipDiscount,
     * subtype, isView, and (for staff only) sort. uid/status/cid/created/sellNum are derived by the
     * server. Product types are 1 physical, 2 source code, 3 tool, and 4 paid reading. Description
     * and paid value have independent length limits because both map to MySQL TEXT columns.
     */
    public long add(String token, Map<String, Object> params, String formText,
                    int requestedIsMd, boolean createSpace) {
        StaffAccess.Actor actor = access.requireUser(token);
        ShopConfig config = config();
        String title = requiredText(params, "title", MAX_TITLE, "商品标题不能为空");
        String description = description(params, formText);
        int isMd = binary(requestedIsMd, "isMd参数错误");
        if (isMd == 1) {
            description = description.replace("||rn||", "\n");
        }
        validateDescription(description, config);
        int type = rangedInteger(params, "type", 1, 4, 1, "商品类型错误");
        String image = optionalText(params, "imgurl", MAX_IMAGE);
        int price = rangedInteger(params, "price", 0, Integer.MAX_VALUE, 0, "商品价格错误");
        int integral = rangedInteger(params, "integral", 0, Integer.MAX_VALUE, 0, "积分价格错误");
        int quantity = rangedInteger(params, "num", 0, Integer.MAX_VALUE, 0, "商品库存错误");
        String value = boundedText(params.get("value"), MAX_VALUE, false);
        String discount = discount(params.containsKey("vipDiscount")
                ? params.get("vipDiscount") : config.vipDiscount);
        int subtype = rangedInteger(params, "subtype", 0, Integer.MAX_VALUE, 0, "商品子类型错误");
        int isView = rangedInteger(params, "isView", 0, 1, 1, "isView参数错误");
        int sort = actor.isStaff()
                ? rangedInteger(params, "sort", 0, Integer.MAX_VALUE, 0, "排序值错误") : 0;
        int status = moderationStatus(actor, config, title + "\n" + description);
        long created = Instant.now().getEpochSecond();
        final String storedDescription = description;

        KeyHolder keys = new GeneratedKeyHolder();
        int changed = jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO starfree_shop"
                            + "(title,imgurl,text,price,integral,num,type,value,cid,uid,created,status,"
                            + "vipDiscount,sellNum,isMd,sort,subtype,isView) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            int index = 1;
            statement.setString(index++, title);
            statement.setString(index++, image);
            statement.setString(index++, storedDescription);
            statement.setInt(index++, price);
            statement.setInt(index++, integral);
            statement.setInt(index++, quantity);
            statement.setInt(index++, type);
            statement.setString(index++, value);
            statement.setInt(index++, -1);
            statement.setLong(index++, actor.getUid());
            statement.setLong(index++, created);
            statement.setInt(index++, status);
            statement.setString(index++, discount);
            statement.setInt(index++, 0);
            statement.setInt(index++, isMd);
            statement.setInt(index++, sort);
            statement.setInt(index++, subtype);
            statement.setInt(index, isView);
            return statement;
        }, keys);
        if (changed <= 0 || keys.getKey() == null) {
            throw new IllegalStateException("商品写入成功但未取得主键");
        }
        long shopId = keys.getKey().longValue();
        if (createSpace) {
            createProductSpaceBestEffort(actor.getUid(), shopId, created, status);
        }
        caches.afterShopWrite(shopId);
        return shopId;
    }

    /**
     * Edits an existing product through a fixed column set.
     *
     * <p>The owner or staff may edit. uid, cid, created, sellNum, and arbitrary database columns
     * cannot be changed here. A non-staff owner edit is re-evaluated by the current content audit
     * policy, so editing an approved product can move it back to pending. Use {@link #mount} for
     * cid and {@link #audit} for explicit moderation state changes.
     */
    public int edit(String token, Map<String, Object> params, String formText,
                    Integer requestedIsMd) {
        StaffAccess.Actor actor = access.requireUser(token);
        long shopId = requiredId(params, "id", "商品不存在");
        Map<String, Object> existing = requiredShop(shopId);
        long ownerUid = number(get(existing, "uid"));
        if (ownerUid != actor.getUid() && !actor.isStaff()) {
            throw new IllegalArgumentException("你无权进行此操作");
        }
        ShopConfig config = config();
        String title = params.containsKey("title")
                ? requiredText(params, "title", MAX_TITLE, "商品标题不能为空")
                : text(get(existing, "title"));
        String description = formText != null ? formText
                : params.containsKey("text") ? boundedText(params.get("text"), MAX_DESCRIPTION, false)
                : text(get(existing, "text"));
        int isMd = requestedIsMd == null
                ? integer(get(existing, "isMd"), 1) : binary(requestedIsMd, "isMd参数错误");
        if (isMd == 1) {
            description = description.replace("||rn||", "\n");
        }
        validateDescription(description, config);
        int type = params.containsKey("type")
                ? rangedInteger(params, "type", 1, 4, 1, "商品类型错误")
                : integer(get(existing, "type"), 1);
        String image = params.containsKey("imgurl")
                ? optionalText(params, "imgurl", MAX_IMAGE) : text(get(existing, "imgurl"));
        int price = params.containsKey("price")
                ? rangedInteger(params, "price", 0, Integer.MAX_VALUE, 0, "商品价格错误")
                : integer(get(existing, "price"), 0);
        int integral = params.containsKey("integral")
                ? rangedInteger(params, "integral", 0, Integer.MAX_VALUE, 0, "积分价格错误")
                : integer(get(existing, "integral"), 0);
        int quantity = params.containsKey("num")
                ? rangedInteger(params, "num", 0, Integer.MAX_VALUE, 0, "商品库存错误")
                : integer(get(existing, "num"), 0);
        String value = params.containsKey("value")
                ? boundedText(params.get("value"), MAX_VALUE, false) : text(get(existing, "value"));
        String vipDiscount = params.containsKey("vipDiscount")
                ? discount(params.get("vipDiscount")) : discount(get(existing, "vipDiscount"));
        int subtype = params.containsKey("subtype")
                ? rangedInteger(params, "subtype", 0, Integer.MAX_VALUE, 0, "商品子类型错误")
                : integer(get(existing, "subtype"), 0);
        int isView = params.containsKey("isView")
                ? rangedInteger(params, "isView", 0, 1, 1, "isView参数错误")
                : integer(get(existing, "isView"), 1);
        int sort = actor.isStaff() && params.containsKey("sort")
                ? rangedInteger(params, "sort", 0, Integer.MAX_VALUE, 0, "排序值错误")
                : integer(get(existing, "sort"), 0);
        int status = moderationStatus(actor, config, title + "\n" + description);

        jdbc.update("UPDATE starfree_shop SET title=?,imgurl=?,text=?,price=?,integral=?,num=?,"
                        + "type=?,value=?,status=?,vipDiscount=?,isMd=?,sort=?,subtype=?,isView=? "
                        + "WHERE id=?",
                title, image, description, price, integral, quantity, type, value, status,
                vipDiscount, isMd, sort, subtype, isView, shopId);
        caches.afterShopWrite(shopId);
        return 1;
    }

    /**
     * Deletes one product after owner/staff authorization.
     *
     * <p>For legacy compatibility, purchase logs and mounted content are not cascaded. Since the
     * source tables have no foreign keys, deleting a sold product can leave historical order rows
     * whose shopInfo no longer resolves. Staff deletion writes a best-effort system notice to the
     * owner; notification failure does not restore the MyISAM product row.
     */
    public int delete(String token, long shopId) {
        StaffAccess.Actor actor = access.requireUser(token);
        Map<String, Object> existing = requiredShop(shopId);
        long ownerUid = number(get(existing, "uid"));
        if (ownerUid != actor.getUid() && !actor.isStaff()) {
            throw new IllegalArgumentException("你无权进行此操作");
        }
        int changed = jdbc.update("DELETE FROM starfree_shop WHERE id=?", shopId);
        if (changed > 0) {
            if (actor.isStaff() && ownerUid != actor.getUid()) {
                sendSystemNoticeBestEffort(actor.getUid(), ownerUid,
                        "你的商品【" + text(get(existing, "title")) + "】已被删除");
            }
            caches.afterShopWrite(shopId);
        }
        return changed;
    }

    /**
     * Approves or rejects a product as staff.
     *
     * @param action legacy action where 0 approves and 1 rejects
     * @param reason mandatory rejection reason; ignored for approval
     */
    public int audit(String token, long shopId, int action, String reason) {
        StaffAccess.Actor actor = access.requireStaff(token);
        if (action != 0 && action != 1) {
            throw new IllegalArgumentException("审核类型错误");
        }
        String cleanReason = clean(reason);
        if (action == 1 && cleanReason.isEmpty()) {
            throw new IllegalArgumentException("请输入拒绝理由");
        }
        if (cleanReason.length() > 1000) {
            throw new IllegalArgumentException("拒绝理由不能超过1000字符");
        }
        Map<String, Object> existing = requiredShop(shopId);
        int targetStatus = action == 0 ? 1 : 2;
        if (integer(get(existing, "status"), -1) == targetStatus) {
            return 1;
        }
        int changed = jdbc.update("UPDATE starfree_shop SET status=? WHERE id=?",
                targetStatus, shopId);
        if (changed > 0) {
            String title = text(get(existing, "title"));
            String notice = action == 0
                    ? "你的商品【" + title + "】已审核通过"
                    : "你的商品【" + title + "】未审核通过。理由如下：" + cleanReason;
            sendSystemNoticeBestEffort(actor.getUid(), number(get(existing, "uid")), notice);
            caches.afterShopWrite(shopId);
        }
        return changed;
    }

    /**
     * Mounts or unmounts a product on content.
     *
     * <p>cid=-1 unmounts. A positive cid must exist and belong to the product owner, unless the
     * caller is staff. This closes the legacy vulnerability that allowed an owner to attach a
     * product to another user's article by submitting an arbitrary cid. Product ownership is also
     * token-derived; client uid fields are ignored.
     */
    public int mount(String token, long shopId, long contentId) {
        StaffAccess.Actor actor = access.requireUser(token);
        Map<String, Object> shop = requiredShop(shopId);
        long ownerUid = number(get(shop, "uid"));
        if (ownerUid != actor.getUid() && !actor.isStaff()) {
            throw new IllegalArgumentException("你无权限修改他人的商品");
        }
        if (contentId != -1) {
            if (contentId <= 0) {
                throw new IllegalArgumentException("文章参数错误");
            }
            List<Map<String, Object>> contents = jdbc.queryForList(
                    "SELECT cid,authorId,type FROM starfree_contents WHERE cid=? LIMIT 1", contentId);
            if (contents.isEmpty()) {
                throw new IllegalArgumentException("文章不存在");
            }
            Map<String, Object> content = contents.get(0);
            String type = text(get(content, "type"));
            if (!("post".equals(type) || "video".equals(type))) {
                throw new IllegalArgumentException("该内容类型不能挂载商品");
            }
            if (number(get(content, "authorId")) != ownerUid && !actor.isStaff()) {
                throw new IllegalArgumentException("不能把商品挂载到他人的文章");
            }
        }
        jdbc.update("UPDATE starfree_shop SET cid=? WHERE id=?", contentId, shopId);
        caches.afterShopWrite(shopId);
        return 1;
    }

    /** Public VIP package list ordered exactly as the retained mapper: orderKey descending. */
    public List<Map<String, Object>> vipPackages() {
        return jdbc.queryForList("SELECT id,orderKey,name,price,day,giftDay,intro "
                + "FROM starfree_vips ORDER BY orderKey DESC,id DESC");
    }

    private Map<String, Object> requiredShop(long shopId) {
        if (shopId <= 0) {
            throw new IllegalArgumentException("商品不存在");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + SHOP_COLUMNS + " FROM starfree_shop s WHERE s.id=? LIMIT 1", shopId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("商品不存在");
        }
        return rows.get(0);
    }

    private ShopConfig config() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT contentAuditlevel,forbidden,vipDiscount,disableCode,webinfoAvatar "
                        + "FROM starfree_apiconfig ORDER BY id LIMIT 1");
        return new ShopConfig(rows.isEmpty()
                ? Collections.<String, Object>emptyMap() : rows.get(0));
    }

    private int moderationStatus(StaffAccess.Actor actor, ShopConfig config, String content) {
        if (actor.isStaff() || config.auditLevel == 0) {
            return 1;
        }
        if (config.auditLevel == 1) {
            return containsForbidden(config.forbidden, content) ? 0 : 1;
        }
        return 0;
    }

    private void validateDescription(String description, ShopConfig config) {
        if (description.trim().isEmpty()) {
            throw new IllegalArgumentException("内容不能为空");
        }
        if (description.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("超出最大内容长度");
        }
        if (config.disableCode && DANGEROUS_CODE.matcher(description).find()) {
            throw new IllegalArgumentException("你的内容包含敏感代码，请修改后重试！");
        }
    }

    private boolean containsForbidden(String forbidden, String content) {
        if (forbidden == null || forbidden.trim().isEmpty() || content == null) {
            return false;
        }
        for (String word : forbidden.split("[,|\\r\\n]+")) {
            String candidate = word.trim();
            if (!candidate.isEmpty() && content.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void createProductSpaceBestEffort(long uid, long shopId, long created, int status) {
        try {
            jdbc.update("INSERT INTO starfree_space"
                            + "(uid,created,modified,text,type,toid,status,onlyMe,likes) "
                            + "VALUES (?,?,?,?,?,?,?,?,?)",
                    uid, created, created, "发布了新商品", 5, shopId, status, 0, 0);
        } catch (DataAccessException error) {
            LOG.error("Could not create product space for shop {}", shopId, error);
        }
    }

    private void sendSystemNoticeBestEffort(long fromUid, long toUid, String notice) {
        try {
            jdbc.update("INSERT INTO starfree_inbox"
                            + "(type,uid,text,touid,isread,value,created,cid) VALUES (?,?,?,?,?,?,?,?)",
                    "system", fromUid, notice, toUid, 0, 0, Instant.now().getEpochSecond(), 0);
        } catch (DataAccessException error) {
            LOG.error("Could not write shop system notice for uid {}", toUid, error);
        }
    }

    private boolean hasPurchased(long uid, long shopId) {
        if (uid <= 0) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_userlog WHERE uid=? AND cid=? AND type='buy'",
                Integer.class, uid, shopId);
        return count != null && count > 0;
    }

    private Viewer viewer(String token) {
        String value = clean(token);
        if (value.isEmpty()) {
            return Viewer.ANONYMOUS;
        }
        Long uid = tokens.userId(value);
        Map<String, Object> user = uid == null ? null : tokens.userById(uid);
        if (uid == null || user == null) {
            return Viewer.ANONYMOUS;
        }
        String group = text(get(user, "group"));
        return new Viewer(uid, "administrator".equals(group) || "editor".equals(group));
    }

    private String avatarPrefix() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT webinfoAvatar FROM starfree_apiconfig ORDER BY id LIMIT 1");
        return rows.isEmpty() ? "" : text(get(rows.get(0), "webinfoAvatar"));
    }

    private Map<String, Object> product(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : PRODUCT_KEYS) {
            Object value = get(row, key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    private Map<String, Object> publicUser(Map<String, Object> row, String avatarPrefix) {
        Map<String, Object> user = new LinkedHashMap<>();
        Object uid = get(row, "user_uid");
        if (uid == null) {
            user.put("uid", 0);
            user.put("name", "用户已注销");
            user.put("groupKey", "");
            user.put("avatar", avatarPrefix + "null");
            user.put("isvip", 0);
            return user;
        }
        long userId = number(uid);
        String name = text(get(row, "user_screenName"));
        if (name.isEmpty()) {
            name = text(get(row, "user_name"));
        }
        Object vip = get(row, "user_vip");
        user.put("name", name);
        user.put("groupKey", text(get(row, "user_group")));
        user.put("uid", userId);
        user.put("avatar", avatar(get(row, "user_avatar"), get(row, "user_mail"), avatarPrefix));
        putIfPresent(user, "customize", get(row, "user_customize"));
        user.put("experience", integer(get(row, "user_experience"), 0));
        putIfPresent(user, "introduce", get(row, "user_introduce"));
        user.put("bantime", integer(get(row, "user_bantime"), 0));
        putIfPresent(user, "ip", get(row, "user_ip"));
        putIfPresent(user, "local", get(row, "user_local"));
        user.put("vip", integer(vip, 0));
        long vipTime = number(vip);
        user.put("isvip", vipTime == 1 || vipTime > Instant.now().getEpochSecond() ? 1 : 0);
        return user;
    }

    private String avatar(Object explicitAvatar, Object mailValue, String prefix) {
        String explicit = text(explicitAvatar);
        if (!explicit.isEmpty()) {
            return explicit;
        }
        String mail = text(mailValue);
        if (mail.matches("[1-9][0-9]{8,10}\\@qq\\.com")) {
            return "https://thirdqq.qlogo.cn/g?b=qq&nk="
                    + mail.substring(0, mail.indexOf('@')) + "&s=100";
        }
        if (mail.isEmpty()) {
            return prefix + "null";
        }
        return prefix + md5(mail);
    }

    private String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(32);
            for (byte current : bytes) {
                result.append(String.format(Locale.ROOT, "%02x", current & 0xff));
            }
            return result.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException("MD5 algorithm is unavailable", impossible);
        }
    }

    private void addNumericFilter(StringBuilder where, List<Object> args,
                                  Map<String, Object> filters, String key, String column,
                                  int minimum, int maximum) {
        if (filters == null || !filters.containsKey(key)) {
            return;
        }
        int value = strictInteger(filters.get(key), "筛选参数" + key + "错误");
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("筛选参数" + key + "错误");
        }
        where.append(" AND ").append(column).append("=?");
        args.add(value);
    }

    private String orderColumn(String requested) {
        String value = clean(requested);
        if ("id".equals(value)) {
            return "s.id";
        }
        if ("price".equals(value)) {
            return "s.price";
        }
        if ("sellNum".equals(value)) {
            return "s.sellNum";
        }
        if ("sort".equals(value)) {
            return "s.sort";
        }
        return "s.created";
    }

    private String description(Map<String, Object> params, String formText) {
        if (formText != null) {
            return formText;
        }
        return params.containsKey("text")
                ? boundedText(params.get("text"), MAX_DESCRIPTION, false) : "";
    }

    private String requiredText(Map<String, Object> params, String key, int maximum, String message) {
        String value = optionalText(params, key, maximum);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String optionalText(Map<String, Object> params, String key, int maximum) {
        return boundedText(params == null ? null : params.get(key), maximum, true);
    }

    private String boundedText(Object value, int maximum, boolean trim) {
        String result = value == null ? "" : String.valueOf(value);
        if (trim) {
            result = result.trim();
        }
        if (result.length() > maximum) {
            throw new IllegalArgumentException("字段" + maximum + "字符长度超限");
        }
        return result;
    }

    private int rangedInteger(Map<String, Object> params, String key, int minimum, int maximum,
                              int fallback, String message) {
        if (params == null || !params.containsKey(key)) {
            return fallback;
        }
        int value = strictInteger(params.get(key), message);
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private int strictInteger(Object value, String message) {
        try {
            if (value == null || String.valueOf(value).trim().isEmpty()) {
                throw new NumberFormatException();
            }
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(message);
        }
    }

    private int binary(int value, String message) {
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private long requiredId(Map<String, Object> params, String key, String message) {
        int id = rangedInteger(params, key, 1, Integer.MAX_VALUE, 0, message);
        if (id <= 0) {
            throw new IllegalArgumentException(message);
        }
        return id;
    }

    private String discount(Object value) {
        try {
            BigDecimal decimal = new BigDecimal(String.valueOf(value).trim());
            if (decimal.compareTo(BigDecimal.ZERO) < 0
                    || decimal.compareTo(BigDecimal.ONE) > 0) {
                throw new NumberFormatException();
            }
            return decimal.stripTrailingZeros().toPlainString();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("VIP折扣必须在0到1之间");
        }
    }

    private static Object get(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return value == null ? 0 : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int integer(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    /** Immutable page result used by the legacy count/total response envelope. */
    public static final class ShopPage {
        private final List<Map<String, Object>> data;
        private final int total;

        public ShopPage(List<Map<String, Object>> data, int total) {
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

    private static final class Viewer {
        private static final Viewer ANONYMOUS = new Viewer(0, false);
        private final long uid;
        private final boolean staff;

        private Viewer(long uid, boolean staff) {
            this.uid = uid;
            this.staff = staff;
        }

        private boolean canManage(long ownerUid) {
            return staff || (uid > 0 && uid == ownerUid);
        }
    }

    private static final class ShopConfig {
        private final int auditLevel;
        private final String forbidden;
        private final String vipDiscount;
        private final boolean disableCode;

        private ShopConfig(Map<String, Object> row) {
            this.auditLevel = integer(get(row, "contentAuditlevel"), 2);
            this.forbidden = text(get(row, "forbidden"));
            String configuredDiscount = text(get(row, "vipDiscount"));
            this.vipDiscount = configuredDiscount.isEmpty() ? "0.1" : configuredDiscount;
            this.disableCode = integer(get(row, "disableCode"), 0) == 1;
        }
    }
}
