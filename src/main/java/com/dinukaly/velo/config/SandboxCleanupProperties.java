package com.dinukaly.velo.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ConfigurationProperties(prefix = "sandbox.cleanup")
@Validated
@EnableScheduling
@Getter
@Setter
public class SandboxCleanupProperties {
    @NotBlank
    private String deploymentId = "velo-local";
    @Min(60)
    private long graceSeconds = 600;
}
