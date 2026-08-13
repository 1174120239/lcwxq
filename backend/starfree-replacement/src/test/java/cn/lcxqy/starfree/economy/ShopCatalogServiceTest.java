package cn.lcxqy.starfree.economy;

import cn.lcxqy.starfree.cache.LegacyProjectionCacheInvalidator;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Security and data-boundary tests for product administration. */
@ExtendWith(MockitoExtension.class)
class ShopCatalogServiceTest {
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private LegacyTokenService tokens;
    @Mock
    private LegacyProjectionCacheInvalidator caches;
    @Captor
    private ArgumentCaptor<String> sql;

    private ShopCatalogService service;

    @BeforeEach
    void setUp() {
        service = new ShopCatalogService(jdbc, tokens, new StaffAccess(tokens), caches);
    }

    @Test
    void anonymousListHidesPaidValueAndNeverInterpolatesOrder() {
        Map<String, Object> item = shop(5, 7, 1);
        item.put("value", "paid-secret");
        item.put("user_uid", 7);
        item.put("user_name", "seller");
        item.put("user_mail", "seller@example.test");
        item.put("user_group", "administrator");
        item.put("user_ip", "203.0.113.9");
        item.put("user_local", "private-location");
        item.put("user_vip", 0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(contains("FROM starfree_shop s LEFT JOIN"), eq(0), eq(15)))
                .thenReturn(Collections.singletonList(item));
        when(jdbc.queryForList(startsWith("SELECT webinfoAvatar")))
                .thenReturn(Collections.singletonList(row("webinfoAvatar", "https://avatar/")));

        ShopCatalogService.ShopPage page = service.page(Collections.<String, Object>emptyMap(), "",
                "created DESC; DROP TABLE starfree_shop", 1, 15, "");

        assertThat(page.getData()).hasSize(1);
        assertThat(page.getData().get(0)).doesNotContainKey("value");
        assertThat(page.getData().get(0).get("userJson"))
                .isInstanceOfSatisfying(Map.class, user -> assertThat(user)
                        .containsEntry("groupKey", "")
                        .doesNotContainKeys("ip", "local", "group"));
        verify(jdbc).queryForList(sql.capture(), eq(0), eq(15));
        assertThat(sql.getValue()).contains("ORDER BY s.created DESC,s.id DESC")
                .doesNotContain("DROP TABLE");
    }

    @Test
    void buyerCanReadPaidValueOnlyWithPersistedPurchaseAndValidToken() {
        Map<String, Object> item = shop(5, 7, 1);
        item.put("value", "download-url");
        when(tokens.userId("buyer-token")).thenReturn(8L);
        when(tokens.userById(8L)).thenReturn(user(8, "subscriber", "contributor"));
        when(jdbc.queryForList(contains("FROM starfree_shop s WHERE"), eq(5L)))
                .thenReturn(Collections.singletonList(item));
        when(jdbc.queryForObject(contains("FROM starfree_userlog"), eq(Integer.class), eq(8L), eq(5L)))
                .thenReturn(1);

        assertThat(service.info(5, "buyer-token")).containsEntry("value", "download-url");
    }

    @Test
    void pendingProductDoesNotRevealItsExistenceToAnonymousReader() {
        when(jdbc.queryForList(contains("FROM starfree_shop s WHERE"), eq(5L)))
                .thenReturn(Collections.singletonList(shop(5, 7, 0)));

        assertThat(service.info(5, "")).isEmpty();
        verify(jdbc, never()).queryForObject(contains("FROM starfree_userlog"),
                eq(Integer.class), any(Object[].class));
    }

    @Test
    void addUsesTokenOwnerAndIgnoresForgedUidAndStatus() throws Exception {
        authenticated("owner-token", 7, "contributor");
        when(jdbc.queryForList(startsWith("SELECT contentAuditlevel")))
                .thenReturn(Collections.singletonList(row(
                        "contentAuditlevel", 0, "forbidden", "", "vipDiscount", "0.8",
                        "disableCode", 0)));
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    KeyHolder holder = invocation.getArgument(1);
                    holder.getKeyList().add(Collections.<String, Object>singletonMap("GENERATED_KEY", 42L));
                    return 1;
                });
        Map<String, Object> params = row("title", "A product", "type", 2, "value", "source",
                "uid", 999, "status", 2, "cid", 123, "sellNum", 500);

        assertThat(service.add("owner-token", params, "description", 1, false)).isEqualTo(42L);

        ArgumentCaptor<PreparedStatementCreator> creator = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbc).update(creator.capture(), any(KeyHolder.class));
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        PreparedStatement statement = org.mockito.Mockito.mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(statement);
        creator.getValue().createPreparedStatement(connection);
        verify(statement).setInt(9, -1);
        verify(statement).setLong(10, 7L);
        verify(statement).setLong(eq(11), anyLong());
        verify(caches).afterShopWrite(42L);
    }

    @Test
    void normalOwnerEditIsRemoderatedAndDoesNotWriteForgedUid() {
        authenticated("owner-token", 7, "contributor");
        Map<String, Object> existing = shop(5, 7, 1);
        existing.put("title", "Old");
        existing.put("text", "Old description");
        existing.put("vipDiscount", "0.8");
        when(jdbc.queryForList(contains("FROM starfree_shop s WHERE"), eq(5L)))
                .thenReturn(Collections.singletonList(existing));
        when(jdbc.queryForList(startsWith("SELECT contentAuditlevel")))
                .thenReturn(Collections.singletonList(row(
                        "contentAuditlevel", 2, "forbidden", "", "vipDiscount", "0.8",
                        "disableCode", 0)));
        Map<String, Object> params = row("id", 5, "title", "New", "uid", 999,
                "status", 1, "cid", 999, "sellNum", 999);

        assertThat(service.edit("owner-token", params, "New description", 1)).isEqualTo(1);

        verify(jdbc).update(startsWith("UPDATE starfree_shop SET title"),
                eq("New"), eq(""), eq("New description"), eq(10), eq(0), eq(1), eq(2),
                eq(""), eq(0), eq("0.8"), eq(1), eq(0), eq(0), eq(1), eq(5L));
        verify(caches).afterShopWrite(5L);
    }

    @Test
    void rejectionRequiresReasonBeforeAnyShopRead() {
        authenticated("staff-token", 1, "editor");

        assertThrows(IllegalArgumentException.class,
                () -> service.audit("staff-token", 5, 1, ""));

        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void mountRejectsAnotherUsersArticleForNormalProductOwner() {
        authenticated("owner-token", 7, "contributor");
        Map<String, Object> item = shop(5, 7, 1);
        Map<String, Object> foreignContent = row("cid", 99, "authorId", 8, "type", "post");
        when(jdbc.queryForList(contains("FROM starfree_shop s WHERE"), eq(5L)))
                .thenReturn(Collections.singletonList(item));
        when(jdbc.queryForList(contains("FROM starfree_contents"), eq(99L)))
                .thenReturn(Collections.singletonList(foreignContent));

        assertThrows(IllegalArgumentException.class, () -> service.mount("owner-token", 5, 99));

        verify(jdbc, never()).update(startsWith("UPDATE starfree_shop SET cid"), any(Object[].class));
    }

    private void authenticated(String token, long uid, String group) {
        when(tokens.userId(token)).thenReturn(uid);
        when(tokens.userById(uid)).thenReturn(user(uid, "user" + uid, group));
    }

    private Map<String, Object> shop(long id, long uid, int status) {
        return row("id", id, "uid", uid, "status", status, "title", "Product", "imgurl", "",
                "text", "Description", "price", 10, "integral", 0, "num", 1, "type", 2,
                "cid", -1, "created", 1, "vipDiscount", "0.8", "sellNum", 0, "isMd", 1,
                "sort", 0, "subtype", 0, "isView", 1);
    }

    private Map<String, Object> user(long uid, String name, String group) {
        return row("uid", uid, "name", name, "group", group);
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }
}
