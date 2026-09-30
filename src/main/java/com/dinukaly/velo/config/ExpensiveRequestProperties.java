package com.dinukaly.velo.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties("security.expensive-requests")
@Validated
@Getter
@Setter
public class ExpensiveRequestProperties {
    @Min(1) @Max(10000)
    private int limit = 20;
    @Min(1) @Max(3600)
    private int windowSeconds = 60;
}
