package cn.lcxqy.starfree.api;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 把参数、鉴权、权限和业务状态拒绝统一转换成旧协议 code=0。
 *
 * <p>这里只处理 IllegalArgumentException。数据库不可用、编程错误和无法补偿的状态必须继续
 * 作为 5xx 暴露并记录日志，不能伪装成普通业务失败，否则运维无法发现数据一致性问题。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 返回 HTTP 200 的 ApiResponse.failure；异常消息是前端可显示的业务原因。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse handleInvalidRequest(IllegalArgumentException error) {
        return ApiResponse.failure(error.getMessage());
    }
}
