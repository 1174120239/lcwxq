package cn.lcxqy.starfree.content;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * contentsAdd/contentsUpdate 的保守新旧分流策略。
 *
 * <p>只有能够证明是普通 post/video 的请求才返回 true。任何缺字段、非法 JSON、付费、草稿、
 * Space/商品关联或未知类型都返回 false，由旧 API 继续处理。这里的 false 不是业务失败，而是
 * “新后端不拥有该功能”；不要为提高新路由命中率而放宽歧义请求。
 */
@Component
public class ContentAddRoutingPolicy {
    private final ObjectMapper mapper;

    public ContentAddRoutingPolicy(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Only ordinary posts and videos are owned by the replacement. Any missing,
     * malformed, paid, draft, Space-linked, shop-linked, or unknown request is
     * delegated so closed-backend behavior remains available during migration.
     */
    public boolean useReplacement(Map<String, String> request) {
        if (!disabledFlag(request, "isPaid")
                || !disabledFlag(request, "isDraft")
                || !disabledFlag(request, "isSpace")) {
            return false;
        }

        String rawParams = value(request, "params");
        if (rawParams.isEmpty()) {
            return false;
        }
        final Map<String, Object> params;
        try {
            params = mapper.readValue(rawParams, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return false;
        }

        if (params.containsKey("sid")) {
            try {
                if (Integer.parseInt(String.valueOf(params.get("sid"))) >= 0) {
                    return false;
                }
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        Object rawType = params.get("type");
        String type = rawType == null ? "post" : String.valueOf(rawType).trim();
        return type.isEmpty() || "post".equals(type) || "video".equals(type);
    }

    public boolean useReplacementUpdate(Map<String, String> request) {
        if (!disabledFlag(request, "isPaid") || !disabledFlag(request, "isDraft")) {
            return false;
        }
        String rawParams = value(request, "params");
        if (rawParams.isEmpty()) {
            return false;
        }
        final Map<String, Object> params;
        try {
            params = mapper.readValue(rawParams, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return false;
        }
        try {
            if (Integer.parseInt(String.valueOf(params.get("cid"))) <= 0) {
                return false;
            }
            if (params.containsKey("sid")
                    && Integer.parseInt(String.valueOf(params.get("sid"))) >= 0) {
                return false;
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        Object rawType = params.get("type");
        String type = rawType == null ? "" : String.valueOf(rawType).trim();
        return type.isEmpty() || "post".equals(type) || "video".equals(type);
    }

    private boolean disabledFlag(Map<String, String> request, String key) {
        String flag = value(request, key);
        return flag.isEmpty() || "0".equals(flag);
    }

    private String value(Map<String, String> request, String key) {
        String value = request.get(key);
        return value == null ? "" : value.trim();
    }
}
