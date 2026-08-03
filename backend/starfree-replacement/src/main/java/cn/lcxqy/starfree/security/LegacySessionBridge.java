package cn.lcxqy.starfree.security;

import java.util.Map;

/**
 * 新后端与旧 Java Redis 登录态之间的最小边界。
 *
 * <p>本地未启用 legacy.redis 时注入实现仍存在，但所有方法等同 NOOP；生产启用后，具体实现
 * 必须使用旧 RedisTemplate 的 Java 序列化格式，不能改用 StringRedisTemplate。
 */
public interface LegacySessionBridge {
    LegacySessionBridge NOOP = new LegacySessionBridge() {
        @Override
        public void store(String account, String token, Map<String, Object> session) {
        }

        @Override
        public Long userId(String token) {
            return null;
        }

        @Override
        public void remove(String token) {
        }

        @Override
        public void removeAccounts(String... accounts) {
        }
    };

    /** 写账号到 token 映射和 token session，并统一设置旧配置的 TTL。 */
    void store(String account, String token, Map<String, Object> session);

    /** 仅从旧 Redis session 解析 uid；找不到或桥接关闭返回 null。 */
    Long userId(String token);

    /** 删除 session，并仅在账号别名仍指向该 token 时删除对应 userkey。 */
    void remove(String token);

    /**
     * 按用户名、邮箱、手机号等账号别名撤销会话。密码重置必须调用它，因为生产旧登录可能只写
     * Redis 而没有更新 starfree_users.authCode。
     */
    void removeAccounts(String... accounts);
}
