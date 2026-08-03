package cn.lcxqy.starfree.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

/**
 * 让所有 RestTemplate 把旧 API 的 4xx/5xx 当作可透传响应，而不是提前抛异常。
 *
 * <p>LegacyProxyController 和内容委托需要复制旧状态、Content-Type 和 body。删除此配置会使旧 API
 * 业务错误在新代理层变成不同的 Spring 异常响应，破坏兼容。它只影响客户端 RestTemplate，
 * 不会吞掉本服务自身 Controller 的异常。
 */
@Component
public class LegacyProxyResponseConfig implements BeanPostProcessor {

    private static final ResponseErrorHandler PASS_THROUGH = new ResponseErrorHandler() {
        @Override
        public boolean hasError(ClientHttpResponse response) throws IOException {
            return false;
        }

        @Override
        public void handleError(ClientHttpResponse response) throws IOException {
            // Legacy HTTP status codes are copied by the proxy controller.
        }
    };

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof RestTemplate) {
            ((RestTemplate) bean).setErrorHandler(PASS_THROUGH);
        }
        return bean;
    }
}
