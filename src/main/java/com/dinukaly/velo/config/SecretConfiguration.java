package com.dinukaly.velo.config;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Validate before application singletons (and their external clients) are created. */
@Configuration(proxyBeanMethods = false)
public class SecretConfiguration {
    @Bean
    static BeanFactoryPostProcessor validateSecrets(Environment environment) {
        return beanFactory -> {
            String jwt = requireSecret(environment, "jwt.secret");
            if (jwt.getBytes(StandardCharsets.UTF_8).length < 32
                    || jwt.chars().distinct().count() < 8) {
                throw invalid("jwt.secret", "use a cryptographically random key of at least 32 bytes");
            }

            // Only an explicitly activated, sole local profile permits provider stubs.
            String[] profiles = environment.getActiveProfiles();
            boolean local = profiles.length == 1 && "local".equals(profiles[0]);
            if (!local) {
                requireSecret(environment, "spring.datasource.password");
                requireSecret(environment, "spring.ai.openai.api-key");
                if (environment.getProperty("spring.mail.properties.mail.smtp.auth", Boolean.class, true)) {
                    requireSecret(environment, "spring.mail.username");
                    requireSecret(environment, "spring.mail.password");
                }
                if (environment.getProperty("velo.ai.embedding.enabled", Boolean.class, true)) {
                    requireSecret(environment, "velo.ai.embedding.api-key");
                }
            }
        };
    }

    private static String requireSecret(Environment environment, String property) {
        String value;
        try {
            value = environment.getProperty(property);
        } catch (IllegalArgumentException ex) {
            // Do not chain resolution errors: they may contain credential values.
            throw invalid(property, "provide an explicit non-placeholder value");
        }
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.contains("${")
                || normalized.startsWith("your_") || normalized.startsWith("your-")
                || normalized.contains("change-me") || normalized.contains("changeme")
                || normalized.contains("change_me") || normalized.contains("placeholder")
                || normalized.startsWith("dev-only") || normalized.equals("password")
                || normalized.equals("secret")) {
            throw invalid(property, "provide an explicit non-placeholder value");
        }
        return value;
    }

    private static IllegalStateException invalid(String property, String reason) {
        return new IllegalStateException("Invalid secret configuration for " + property + ": " + reason);
    }
}
