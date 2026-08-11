package cn.lcxqy.starfree.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyMailEnvironmentPostProcessorTest {
    @TempDir
    Path tempDir;

    @Test
    void importsOnlyWhitelistedMailSettingsAheadOfApplicationDefaults() throws Exception {
        Path properties = tempDir.resolve("application.properties");
        Files.write(properties, (
                "spring.mail.host=smtp.qq.com\n"
                        + "spring.mail.port=465\n"
                        + "spring.mail.username=sender@qq.com\n"
                        + "spring.mail.password=authorization-code\n"
                        + "spring.mail.from=sender@qq.com\n"
                        + "server.port=8081\n"
                        + "spring.datasource.password=must-not-import\n")
                .getBytes(StandardCharsets.ISO_8859_1));

        StandardEnvironment environment = environment(properties);
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("spring.mail.username", "");
        defaults.put("server.port", "18082");
        environment.getPropertySources().addLast(
                new MapPropertySource("applicationConfig", defaults));

        process(environment);

        assertThat(environment.getProperty("spring.mail.username"))
                .isEqualTo("sender@qq.com");
        assertThat(environment.getProperty("spring.mail.password"))
                .isEqualTo("authorization-code");
        assertThat(environment.getProperty("server.port")).isEqualTo("18082");
        assertThat(environment.getProperty("spring.datasource.password")).isNull();
    }

    @Test
    void explicitRuntimeSettingsKeepPriorityOverLegacyFile() throws Exception {
        Path properties = tempDir.resolve("application.properties");
        Files.write(properties, "spring.mail.username=legacy@qq.com\n"
                .getBytes(StandardCharsets.ISO_8859_1));

        StandardEnvironment environment = environment(properties);
        environment.getPropertySources().addFirst(new MapPropertySource(
                "commandLineArgs",
                Collections.<String, Object>singletonMap(
                        "spring.mail.username", "runtime@qq.com")));

        process(environment);

        assertThat(environment.getProperty("spring.mail.username"))
                .isEqualTo("runtime@qq.com");
    }

    private StandardEnvironment environment(Path properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "testPath",
                Collections.<String, Object>singletonMap(
                        "legacy.mail.properties-path", properties.toString())));
        return environment;
    }

    private void process(StandardEnvironment environment) {
        new LegacyMailEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication(Object.class));
    }
}
