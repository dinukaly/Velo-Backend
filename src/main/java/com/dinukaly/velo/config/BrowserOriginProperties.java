package com.dinukaly.velo.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "cors")
@Validated
@Getter
@Setter
public class BrowserOriginProperties {

    @NotEmpty
    private List<String> allowedOrigins = List.of("http://localhost:3000");

    @AssertTrue(message = "cors.allowed-origins must contain only exact HTTP(S) origins without paths or wildcards")
    public boolean isAllowedOriginsValid() {
        return allowedOrigins != null && allowedOrigins.stream().allMatch(this::isExactHttpOrigin);
    }

    private boolean isExactHttpOrigin(String value) {
        if (value == null || value.isBlank() || value.contains("*")) {
            return false;
        }

        try {
            URI origin = URI.create(value);
            String path = origin.getPath();
            return ("http".equalsIgnoreCase(origin.getScheme()) || "https".equalsIgnoreCase(origin.getScheme()))
                    && origin.getHost() != null
                    && (path == null || path.isEmpty())
                    && origin.getQuery() == null
                    && origin.getFragment() == null
                    && origin.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
