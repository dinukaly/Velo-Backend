package com.dinukaly.velo.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "cookie")
@Validated
@Getter
@Setter
public class CookieProperties {

    private boolean secure;

    @NotNull
    private SameSitePolicy sameSite = SameSitePolicy.LAX;

    @AssertTrue(message = "cookie.secure must be true when cookie.same-site is None")
    public boolean isSameSiteNoneSecure() {
        return sameSite != SameSitePolicy.NONE || secure;
    }

    public enum SameSitePolicy {
        STRICT("Strict"),
        LAX("Lax"),
        NONE("None");

        private final String attributeValue;

        SameSitePolicy(String attributeValue) {
            this.attributeValue = attributeValue;
        }

        public String attributeValue() {
            return attributeValue;
        }
    }
}
