package cn.lcxqy.starfree.log;

import cn.lcxqy.starfree.cache.LegacyProjectionCacheInvalidator;
import cn.lcxqy.starfree.economy.EconomyLockExecutor;
import cn.lcxqy.starfree.security.LegacySessionBridge;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserlogAdministrationServiceTest {
    @Mock
    private LegacyTokenService tokens;
    @Mock
    private EconomyLockExecutor lock;
    @Mock
    private LegacySessionBridge sessions;
    @Mock
    private LegacyProjectionCacheInvalidator caches;
    @Mock
    private Connection connection;

    private StubJdbcTemplate jdbc;
    private UserlogAdministrationService service;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = new StubJdbcTemplate();
        service = new UserlogAdministrationService(
                jdbc, new StaffAccess(tokens), lock, sessions, caches);
        lenient().when(lock.execute(any())).thenAnswer(invocation -> {
            EconomyLockExecutor.SqlWork<?> work = invocation.getArgument(0);
            return work.execute(connection);
        });
    }

    @Test
    void buyerOrderNestsShopAndMerchantContactWithoutBuyerPrivateData() {
        arrangeActor("buyer-token", 7, "contributor");
        jdbc.count = 1;
        jdbc.rows = Collections.singletonList(row(
                "id", 11, "uid", 7, "cid", 3, "log_type", "buy", "num", 50,
                "created", 1234, "toid", 9, "shop_id", 3, "shop_title", "Course",
                "shop_price", 50, "shop_type", 4, "shop_uid", 9,
                "merchant_email", "seller@example.com", "buyer_email", "buyer@example.com",
                "buyer_address", "private address"));

        UserlogAdministrationService.Page page = service.buyerOrders("buyer-token");

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getData()).hasSize(1);
        Map<String, Object> order = page.getData().get(0);
        assertThat(order).containsEntry("merchantEmail", "seller@example.com")
                .doesNotContainKeys("address", "userEmail");
        assertThat((Map<String, Object>) order.get("shopInfo"))
                .containsEntry("id", 3).containsEntry("title", "Course")
                .containsEntry("price", 50);
    }

    @Test
    void sellerOrderUsesTokenUidAndIncludesOnlyRequiredBuyerDeliveryFields() {
        arrangeActor("seller-token", 9, "contributor");
        jdbc.count = 1;
        jdbc.rows = Collections.singletonList(row(
                "id", 11, "uid", 7, "cid", 3, "log_type", "buy", "created", 1234,
                "toid", 9, "buyer_email", "buyer@example.com",
                "buyer_address", "delivery address"));

        UserlogAdministrationService.Page page = service.sellerOrders("seller-token", 2, 500);

        assertThat(page.getData().get(0))
                .containsEntry("userEmail", "buyer@example.com")
                .containsEntry("address", "delivery address")
                .doesNotContainKey("merchantEmail");
        assertThat(jdbc.lastQueryArgs).containsExactly(9L, 50, 50);
    }

    @Test
    void usedInvitationCleanupRunsUnderLockAndInvalidatesOldPages() {
        arrangeActor("admin-token", 1, "administrator");
        jdbc.updateResult = 8;

        assertThat(service.clean("admin-token", 5)).isEqualTo(8);

        verify(lock).execute(any());
        assertThat(jdbc.lastUpdateSql).isEqualTo(
                "DELETE FROM starfree_invitation WHERE status=1");
        verify(caches).afterInvitationWrite();
        verify(caches).afterDashboardCountWrite();
    }

    @Test
    void dormantCleanupRevokesSessionsOnlyForRowsActuallyDeleted() {
        arrangeActor("admin-token", 1, "administrator");
        jdbc.rows = Collections.singletonList(row(
                "uid", 7, "name", "old", "mail", "old@example.com",
                "phone", "13800138000", "authCode", "legacy-token"));
        jdbc.updateResult = 2;

        assertThat(service.clean("admin-token", 6)).isEqualTo(1);

        assertThat(jdbc.lastUpdateSql).contains("DELETE u,a")
                .contains("NOT EXISTS(SELECT 1 FROM starfree_contents");
        verify(sessions).removeAccounts("old", "old@example.com", "13800138000");
        verify(sessions).remove("legacy-token");
        verify(caches).afterUserWrite(7, "old");
    }

    @Test
    void invalidCleanupSelectorIsRejectedBeforeTheEconomyLock() {
        arrangeActor("admin-token", 1, "administrator");

        assertThrows(IllegalArgumentException.class,
                () -> service.clean("admin-token", 0));

        verify(lock, never()).execute(any());
    }

    private void arrangeActor(String token, long uid, String group) {
        when(tokens.userId(token)).thenReturn(uid);
        when(tokens.userById(uid)).thenReturn(row(
                "uid", uid, "name", "actor", "group", group));
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }

    /** Captures bound pagination and cleanup SQL while returning deterministic rows. */
    private static final class StubJdbcTemplate extends JdbcTemplate {
        private int count;
        private List<Map<String, Object>> rows = Collections.emptyList();
        private int updateResult;
        private String lastUpdateSql;
        private List<Object> lastQueryArgs = Collections.emptyList();

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return (T) Integer.valueOf(count);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            lastQueryArgs = new ArrayList<>();
            Collections.addAll(lastQueryArgs, args);
            return rows;
        }

        @Override
        public int update(String sql, Object... args) {
            lastUpdateSql = sql;
            return updateResult;
        }

        @Override
        public int update(String sql) {
            return update(sql, new Object[0]);
        }
    }
}
