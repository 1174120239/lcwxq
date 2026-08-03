package cn.lcxqy.starfree.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 旧表单协议的集中解析工具。
 *
 * <p>旧前端以 application/x-www-form-urlencoded 发送数据，复杂对象放在字符串字段
 * {@code params} 中。这里刻意采用“空值/非法数字回退，再由 Service 做业务校验”的兼容方式，
 * 因而不能把返回默认值理解为参数已经合法。
 */
public final class RequestValues {
    private RequestValues() {
    }

    /**
     * 把 params JSON 解析成保序 Map。缺失、空串或非法 JSON 返回空对象，以匹配旧控制器行为；
     * 需要严格 JSON 的路由策略必须自行解析并在歧义时委托旧后端。
     */
    public static Map<String, Object> jsonObject(ObjectMapper mapper, String value) {
        if (value == null || value.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return mapper.readValue(value, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return new LinkedHashMap<String, Object>();
        }
    }

    /** 读取顶层表单字段并 trim；缺失返回空串。密码等不能 trim 的值不得使用此方法。 */
    public static String text(Map<String, String> params, String key) {
        String value = params.get(key);
        return value == null ? "" : value.trim();
    }

    /** 读取顶层整数；缺失、空串或格式错误返回调用方提供的 fallback。 */
    public static int integer(Map<String, String> params, String key, int fallback) {
        try {
            return Integer.parseInt(text(params, key));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** 读取 params JSON 中的整数；不接受的值返回 fallback。 */
    public static int objectInteger(Map<String, Object> params, String key, int fallback) {
        Object value = params.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** 读取 params JSON 中的文本并 trim；缺失返回空串。 */
    public static String objectText(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static Map<String, Object> emptyObject() {
        return Collections.emptyMap();
    }
}
