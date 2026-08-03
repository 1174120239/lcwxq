package cn.lcxqy.starfree.ads;

import cn.lcxqy.starfree.economy.EconomyService;
import cn.lcxqy.starfree.economy.EconomyLockExecutor;
import cn.lcxqy.starfree.economy.EconomyOperationJournal;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdsServiceTest {
    @Test
    void addRunsThroughTheSharedEconomyLock() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        EconomyService economy = mock(EconomyService.class);
        EconomyLockExecutor lock = mock(EconomyLockExecutor.class);
        EconomyOperationJournal journal = mock(EconomyOperationJournal.class);
        when(tokens.userId("valid-token")).thenReturn(7L);
        Map<String, Object> user = new HashMap<>();
        user.put("uid", 7L);
        user.put("group", "subscriber");
        when(tokens.userById(7L)).thenReturn(user);
        when(economy.isStaff("subscriber")).thenReturn(false);
        when(journal.requestKey(eq("ad-buy"), eq(7L), eq("request-1"), anyString()))
                .thenReturn("ad-buy:key");
        Map<String, Object> lockedResult = new HashMap<>();
        lockedResult.put("aid", 9L);
        lockedResult.put("status", 0);
        when(lock.execute(any())).thenReturn(lockedResult);

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");
        request.put("day", "2");
        request.put("requestId", "request-1");
        request.put("params",
                "{\"name\":\"A\",\"type\":0,\"img\":\"/a.png\","
                        + "\"intro\":\"intro\",\"urltype\":1,"
                        + "\"url\":\"https://example.com\"}");

        AdsService service = new AdsService(
                jdbc, new ObjectMapper(), tokens, economy, lock, journal);
        assertThat(service.add(request)).isSameAs(lockedResult);

        verify(lock).execute(any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void nonAdministratorCannotRenewAnAdvertisement() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        EconomyService economy = mock(EconomyService.class);
        EconomyLockExecutor lock = mock(EconomyLockExecutor.class);
        EconomyOperationJournal journal = mock(EconomyOperationJournal.class);
        when(tokens.userId("valid-token")).thenReturn(7L);
        Map<String, Object> user = new HashMap<>();
        user.put("uid", 7L);
        user.put("group", "contributor");
        when(tokens.userById(7L)).thenReturn(user);

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");
        request.put("id", "3");
        request.put("day", "1");

        AdsService service = new AdsService(
                jdbc, new ObjectMapper(), tokens, economy, lock, journal);
        assertThatThrownBy(() -> service.renewal(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("你没有操作权限");

        verify(lock, never()).execute(any());
    }

    @Test
    void editSendsUserChangedPublishedAdBackToReview() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        EconomyService economy = mock(EconomyService.class);
        when(tokens.userId("valid-token")).thenReturn(7L);
        Map<String, Object> user = new HashMap<>();
        user.put("uid", 7L);
        user.put("group", "subscriber");
        when(tokens.userById(7L)).thenReturn(user);
        when(economy.isStaff("subscriber")).thenReturn(false);
        Map<String, Object> ad = new HashMap<>();
        ad.put("aid", 3L);
        ad.put("uid", 7L);
        ad.put("type", 0);
        ad.put("status", 1);
        when(jdbc.queryForList(startsWith("SELECT aid"), eq(3L)))
                .thenReturn(Collections.singletonList(ad));

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");
        request.put("params",
                "{\"aid\":3,\"name\":\"A\",\"type\":0,\"img\":\"/a.png\","
                        + "\"intro\":\"intro\",\"urltype\":1,"
                        + "\"url\":\"https://example.com\"}");

        new AdsService(jdbc, new ObjectMapper(), tokens, economy).edit(request);

        verify(jdbc).update(
                startsWith("UPDATE starfree_ads SET name"),
                eq("A"), eq(0), eq("/a.png"), eq("intro"), eq(1),
                eq("https://example.com"), eq(0), eq(3L));
    }

    @Test
    void nonStaffCannotReadAnotherUsersPendingAd() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        EconomyService economy = mock(EconomyService.class);
        when(tokens.userId("valid-token")).thenReturn(7L);
        Map<String, Object> user = new HashMap<>();
        user.put("uid", 7L);
        user.put("group", "subscriber");
        when(tokens.userById(7L)).thenReturn(user);
        when(economy.isStaff("subscriber")).thenReturn(false);

        AdsService service = new AdsService(jdbc, new ObjectMapper(), tokens, economy);
        assertThatThrownBy(() -> service.page(
                "{\"status\":0,\"uid\":9}", 8, 1, "", "valid-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("没有查看权限");

        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
    }

    private Map<String, Object> adConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("pushAdsPrice", 100);
        config.put("pushAdsNum", 10);
        config.put("bannerAdsPrice", 100);
        config.put("bannerAdsNum", 5);
        config.put("startAdsPrice", 100);
        config.put("startAdsNum", 1);
        return config;
    }
}
