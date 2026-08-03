package cn.lcxqy.starfree.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web 层公共配置：H5 跨域和旧 API 转发使用的 RestTemplate。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 允许 HBuilderX H5 从不同 localhost 端口访问。本服务不使用 Cookie 登录，所以未开启
     * allowCredentials。生产仍应由 Nginx 限制公开端口和来源，不能因这里允许 `*` 就暴露 18082。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    /** 供兜底代理和内容写委托复用；错误响应由 LegacyProxyResponseConfig 配置为透传。 */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Dedicated client for fixed public read providers. Short timeouts keep a provider outage from
     * exhausting servlet threads; the legacy fallback client remains separate because uploads and
     * payment-provider callbacks can legitimately take longer.
     */
    @Bean("externalReadRestTemplate")
    public RestTemplate externalReadRestTemplate() {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(5000);
        requests.setReadTimeout(10000);
        return new RestTemplate(requests);
    }
}
