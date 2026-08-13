package cn.lcxqy.starfree.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web 层公共配置：H5 跨域和旧 API 转发使用的 RestTemplate。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] allowedOriginPatterns;

    public WebConfig(@Value("${app.cors.allowed-origin-patterns}") String origins) {
        this.allowedOriginPatterns = origins.split("\\s*,\\s*");
    }

    /**
     * Production H5 and local development origins are explicit and configurable. Credentials remain
     * disabled because API authentication does not use browser cookies.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Origin", "Content-Type", "Accept", "Authorization",
                        "X-Requested-With");
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
