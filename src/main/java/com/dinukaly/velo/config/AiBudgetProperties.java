package com.dinukaly.velo.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties("velo.ai")
@Validated
@Getter
@Setter
public class AiBudgetProperties {
    @Min(1) @Max(1000000)
    private int maxPromptCharacters = 12000;
    @Min(1) @Max(32768)
    private int maxCompletionTokens = 4096;
    @Min(1) @Max(1000)
    private int maxContextItems = 100;
}
