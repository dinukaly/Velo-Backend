package com.dinukaly.velo.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties("agent.sse")
@Validated
@Getter
@Setter
public class AgentSseProperties {
    @Min(1) @Max(20)
    private int maxSubscribersPerRun = 3;
    @Min(1) @Max(1000)
    private int maxSubscribersPerInstance = 100;
    @Min(1) @Max(1000)
    private int maxReplayEvents = 100;
    @Min(256) @Max(60000)
    private int maxEventPayloadCharacters = 16000;
    @Min(1) @Max(3650)
    private int eventRetentionDays = 30;
    @Min(1) @Max(3650)
    private int terminalRunRetentionDays = 90;
}
