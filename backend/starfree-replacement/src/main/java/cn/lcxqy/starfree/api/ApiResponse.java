package cn.lcxqy.starfree.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 旧 StarFree 前端兼容响应。
 *
 * <p>业务成功/失败使用 code=1/0，通常都返回 HTTP 200；调用方必须判断 code。null 字段不会
 * 序列化，所以普通响应不会出现 count/total/clockData。少数接口因旧前端直接读取顶层字段而
 * 返回裸 Map，不得强行套用本类。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {
    private int code;
    private String msg;
    private Object data;
    private Integer count;
    private Integer total;
    private Object clockData;

    public ApiResponse() {
    }

    public ApiResponse(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /** 普通成功，使用旧默认消息“请求成功”。 */
    public static ApiResponse success(Object data) {
        return new ApiResponse(1, "请求成功", data);
    }

    /** 普通成功；用于必须精确兼容旧中文消息或空消息的接口。 */
    public static ApiResponse success(String msg, Object data) {
        return new ApiResponse(1, msg, data);
    }

    /** 业务失败；data 为 null 且序列化时省略，不代表 HTTP 状态失败。 */
    public static ApiResponse failure(String msg) {
        return new ApiResponse(0, msg, null);
    }

    /** 标准分页：count 是当前页行数，total 是相同筛选条件下的数据库总数。 */
    public static ApiResponse paged(Object data, int count, int total) {
        ApiResponse response = new ApiResponse(1, "", data);
        response.setCount(count);
        response.setTotal(total);
        return response;
    }

    /** 每日 Java 打卡的历史特殊包络；结果放在顶层 clockData 而不是 data。 */
    public static ApiResponse clock(Object clockData) {
        ApiResponse response = new ApiResponse(1, "操作成功", null);
        response.setClockData(clockData);
        return response;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Object getClockData() {
        return clockData;
    }

    public void setClockData(Object clockData) {
        this.clockData = clockData;
    }
}
