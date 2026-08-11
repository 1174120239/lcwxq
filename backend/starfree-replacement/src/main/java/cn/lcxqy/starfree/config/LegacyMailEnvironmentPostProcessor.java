package cn.lcxqy.starfree.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Imports only SMTP settings from the legacy runtime configuration. Environment
 * variables and command-line properties keep higher precedence, while unrelated
 * legacy settings such as server.port, database, and Redis are never imported.
 */
public final class LegacyMailEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {
    static final String PROPERTY_SOURCE_NAME = "legacyMailRuntime";
    private static final String DEFAULT_PROPERTIES_PATH = "/opt/application.properties";
    private static final String[] MAIL_KEYS = {
            "spring.mail.host",
            "spring.mail.port",
            "spring.mail.username",
            "spring.mail.password",
            "spring.mail.from"
    };

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        String configuredPath = environment.getProperty(
                "legacy.mail.properties-path", DEFAULT_PROPERTIES_PATH);
        Path path = Paths.get(configuredPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return;
        }

        Properties legacy = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            legacy.load(input);
        } catch (IOException ignored) {
            return;
        }

        Map<String, Object> mail = new LinkedHashMap<>();
        for (String key : MAIL_KEYS) {
            String value = trimToNull(legacy.getProperty(key));
            if (value != null) {
                mail.put(key, value);
            }
        }
        if (mail.isEmpty()) {
            return;
        }

        MutablePropertySources sources = environment.getPropertySources();
        MapPropertySource source = new MapPropertySource(PROPERTY_SOURCE_NAME, mail);
        if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        } else if (sources.contains(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)) {
            sources.addAfter(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, source);
        } else {
            sources.addFirst(source);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
