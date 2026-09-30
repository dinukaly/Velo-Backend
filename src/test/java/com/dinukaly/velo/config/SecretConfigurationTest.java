package com.dinukaly.velo.config;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class SecretConfigurationTest {
    private MockEnvironment valid() {
        return new MockEnvironment()
                .withProperty("jwt.secret", "c6A9tZ2kP5mQ8rW1xE4nH7vB0sD3fG6j")
                .withProperty("spring.datasource.password", "test-db-credential")
                .withProperty("spring.ai.openai.api-key", "test-chat-credential")
                .withProperty("spring.mail.username", "mailer@example.test")
                .withProperty("spring.mail.password", "test-mail-credential")
                .withProperty("velo.ai.embedding.api-key", "test-embedding-credential");
    }

    private void start(MockEnvironment environment) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.register(SecretConfiguration.class);
            context.refresh();
        }
    }

    @Test
    void validDefaultAndProductionConfigurationStart() {
        start(valid());
        var environment = valid();
        environment.setActiveProfiles("production");
        start(environment);
    }

    @Test
    void missingAndPlaceholderCredentialsFailWithoutDisclosingValues() {
        for (String property : new String[]{"jwt.secret", "spring.datasource.password",
                "spring.ai.openai.api-key", "spring.mail.username", "spring.mail.password",
                "velo.ai.embedding.api-key"}) {
            for (String value : new String[]{"", "  ", "your_private_credential_here",
                    "dev-only-change-me-12345678901234567890", "${UNSET_SECRET}"}) {
                var environment = valid().withProperty(property, value);
                Exception failure = assertThrows(IllegalStateException.class, () -> start(environment));
                assertTrue(failure.getMessage().contains(property));
                if (!value.isBlank()) assertFalse(failure.getMessage().contains(value));
                assertNull(failure.getCause());
            }
        }
    }

    @Test
    void shortAndRepetitiveJwtKeysFailEvenLocally() {
        for (String key : new String[]{"short-key", "a".repeat(64)}) {
            var environment = valid().withProperty("jwt.secret", key);
            environment.setActiveProfiles("local");
            assertThrows(IllegalStateException.class, () -> start(environment));
        }
    }

    @Test
    void localExceptionsRequireExplicitSoleActiveProfile() {
        var environment = valid().withProperty("spring.ai.openai.api-key", "your_local_stub");
        environment.setDefaultProfiles("local");
        assertThrows(IllegalStateException.class, () -> start(environment));
        environment.setActiveProfiles("local");
        start(environment);
        environment.setActiveProfiles("local", "production");
        assertThrows(IllegalStateException.class, () -> start(environment));
    }

    @Test
    void disabledEmbeddingAndUnauthenticatedSmtpDoNotRequireTheirCredentials() {
        start(valid().withProperty("velo.ai.embedding.enabled", "false")
                .withProperty("velo.ai.embedding.api-key", "")
                .withProperty("spring.mail.properties.mail.smtp.auth", "false")
                .withProperty("spring.mail.username", "")
                .withProperty("spring.mail.password", ""));
    }

    @Test
    void invalidSecretsStopStartupBeforeApplicationSingletons() {
        AtomicBoolean created = new AtomicBoolean();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment());
            context.register(SecretConfiguration.class);
            context.registerBean("externalClient", Object.class, () -> {
                created.set(true);
                return new Object();
            });
            assertThrows(IllegalStateException.class, context::refresh);
            assertFalse(created.get());
        }
    }

    @Test
    void shippedConfigurationHasNoSecretDefaultsOrImplicitLocalImport() throws IOException {
        for (String resource : new String[]{"application.properties", "application.properties.example"}) {
            Properties properties = new Properties();
            try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream);
                properties.load(stream);
            }
            assertNull(properties.getProperty("spring.config.import"));
            for (String property : new String[]{"jwt.secret", "spring.datasource.password",
                    "spring.ai.openai.api-key", "spring.mail.username", "spring.mail.password",
                    "velo.ai.embedding.api-key"}) {
                assertTrue(properties.getProperty(property).endsWith(":}"), property);
            }
        }
    }
}
