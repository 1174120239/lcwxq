package cn.lcxqy.starfree.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisLegacySessionBridgeTest {
    @SuppressWarnings("unchecked")
    @Test
    void storesLegacyHashAndRotatesPreviousSession() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        ValueOperations<Object, Object> strings = mock(ValueOperations.class);
        HashOperations<Object, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForValue()).thenReturn(strings);
        when(redis.opsForHash()).thenReturn(hashes);
        when(strings.get("starfree_userkeyalice")).thenReturn("old-token");

        Map<String, Object> session = new HashMap<>();
        session.put("uid", 7);
        session.put("name", "alice");
        session.put("group", "contributor");
        new RedisLegacySessionBridge(redis, true, "starfree", 15552000)
                .store("alice", "new-token", session);

        verify(redis).delete("starfree_userInfoold-token");
        verify(strings).set("starfree_userkeyalice", "new-token", 15552000, TimeUnit.SECONDS);
        verify(redis).delete("starfree_userInfonew-token");
        verify(hashes).putAll(org.mockito.ArgumentMatchers.eq("starfree_userInfonew-token"), anyMap());
        verify(redis).expire("starfree_userInfonew-token", 15552000, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    @Test
    void enforcesNinetyDayMinimumWhenRuntimeStillSuppliesLegacyTtl() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        ValueOperations<Object, Object> strings = mock(ValueOperations.class);
        HashOperations<Object, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForValue()).thenReturn(strings);
        when(redis.opsForHash()).thenReturn(hashes);

        Map<String, Object> session = new HashMap<>();
        session.put("uid", 7);
        session.put("name", "alice");
        new RedisLegacySessionBridge(redis, true, "starfree", 86400)
                .store("alice", "new-token", session);

        verify(strings).set("starfree_userkeyalice", "new-token", 7776000, TimeUnit.SECONDS);
        verify(redis).expire("starfree_userInfonew-token", 7776000, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    @Test
    void storesDetachedSessionWithoutReplacingAccountToken() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        ValueOperations<Object, Object> strings = mock(ValueOperations.class);
        HashOperations<Object, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);

        Map<String, Object> session = new HashMap<>();
        session.put("uid", 7);
        session.put("name", "alice");
        new RedisLegacySessionBridge(redis, true, "starfree", 86400)
                .storeDetached("upload-token", session);

        verify(redis).delete("starfree_userInfoupload-token");
        verify(hashes).putAll(org.mockito.ArgumentMatchers.eq("starfree_userInfoupload-token"), anyMap());
        verify(redis).expire("starfree_userInfoupload-token", 300, TimeUnit.SECONDS);
        verifyNoInteractions(strings);
        assertThat(new RedisLegacySessionBridge(redis, true, "starfree", 86400).available()).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void disabledBridgeDoesNotContactRedis() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        new RedisLegacySessionBridge(redis, false, "starfree", 86400)
                .store("alice", "token", new HashMap<String, Object>());

        org.mockito.Mockito.verifyNoInteractions(redis);
        assertThat(new RedisLegacySessionBridge(redis, false, "starfree", 86400).available()).isFalse();
    }
    @SuppressWarnings("unchecked")
    @Test
    void resolvesLegacyUserIdFromSessionHash() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        HashOperations<Object, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.get("starfree_userInfolegacy-token", "uid")).thenReturn("7");

        Long uid = new RedisLegacySessionBridge(redis, true, "starfree", 86400)
                .userId("legacy-token");

        assertThat(uid).isEqualTo(7L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void refreshesExpiringSessionAndOnlyMatchingAccountAliases() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        HashOperations<Object, Object, Object> hashes = mock(HashOperations.class);
        ValueOperations<Object, Object> strings = mock(ValueOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.opsForValue()).thenReturn(strings);
        when(redis.getExpire("starfree_userInfotoken", TimeUnit.SECONDS)).thenReturn(60L);
        when(redis.expire("starfree_userInfotoken", 7776000, TimeUnit.SECONDS)).thenReturn(true);
        Map<Object, Object> session = new HashMap<>();
        session.put("uid", 7);
        session.put("name", "alice");
        session.put("mail", "alice@example.com");
        when(hashes.entries("starfree_userInfotoken")).thenReturn(session);
        when(strings.get("starfree_userkeyalice")).thenReturn("token");
        when(strings.get("starfree_userkeyalice@example.com")).thenReturn("newer-token");

        new RedisLegacySessionBridge(redis, true, "starfree", 7776000)
                .refreshIfNeeded("token");

        verify(redis).expire("starfree_userInfotoken", 7776000, TimeUnit.SECONDS);
        verify(redis).expire("starfree_userkeyalice", 7776000, TimeUnit.SECONDS);
        verify(redis, never()).expire("starfree_userkeyalice@example.com", 7776000, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    @Test
    void doesNotRewriteHealthySessionOnEveryRequest() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        when(redis.getExpire("starfree_userInfotoken", TimeUnit.SECONDS)).thenReturn(7000000L);

        new RedisLegacySessionBridge(redis, true, "starfree", 7776000)
                .refreshIfNeeded("token");

        verify(redis, never()).expire("starfree_userInfotoken", 7776000, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    @Test
    void boundsHistoricalSessionWithoutExpiry() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        HashOperations<Object, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.getExpire("starfree_userInfotoken", TimeUnit.SECONDS)).thenReturn(-1L);
        when(redis.expire("starfree_userInfotoken", 7776000, TimeUnit.SECONDS)).thenReturn(true);
        Map<Object, Object> session = new HashMap<>();
        session.put("uid", 7);
        when(hashes.entries("starfree_userInfotoken")).thenReturn(session);

        new RedisLegacySessionBridge(redis, true, "starfree", 7776000)
                .refreshIfNeeded("token");

        verify(redis).expire("starfree_userInfotoken", 7776000, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    @Test
    void removalClearsEveryAccountAliasForTheRevokedToken() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        HashOperations<Object, Object, Object> hashes = mock(HashOperations.class);
        ValueOperations<Object, Object> strings = mock(ValueOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.opsForValue()).thenReturn(strings);
        Map<Object, Object> session = new HashMap<>();
        session.put("name", "alice");
        session.put("mail", "alice@example.com");
        session.put("phone", "13800138000");
        when(hashes.entries("starfree_userInfotoken")).thenReturn(session);
        when(strings.get("starfree_userkeyalice")).thenReturn("token");
        when(strings.get("starfree_userkeyalice@example.com")).thenReturn("token");
        when(strings.get("starfree_userkey13800138000")).thenReturn("token");

        new RedisLegacySessionBridge(redis, true, "starfree", 86400).remove("token");

        verify(redis).delete("starfree_userInfotoken");
        verify(redis).delete("starfree_userkeyalice");
        verify(redis).delete("starfree_userkeyalice@example.com");
        verify(redis).delete("starfree_userkey13800138000");
    }

    @SuppressWarnings("unchecked")
    @Test
    void removalByAccountRevokesRedisOnlyLegacyLoginSessions() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        HashOperations<Object, Object, Object> hashes = mock(HashOperations.class);
        ValueOperations<Object, Object> strings = mock(ValueOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.opsForValue()).thenReturn(strings);
        when(strings.get("starfree_userkeyalice@example.com"))
                .thenReturn("legacy-token", "legacy-token");
        when(hashes.entries("starfree_userInfolegacy-token"))
                .thenReturn(new HashMap<Object, Object>());

        new RedisLegacySessionBridge(redis, true, "starfree", 86400)
                .removeAccounts("alice@example.com");

        verify(redis).delete("starfree_userInfolegacy-token");
        verify(redis).delete("starfree_userkeyalice@example.com");
    }
}
