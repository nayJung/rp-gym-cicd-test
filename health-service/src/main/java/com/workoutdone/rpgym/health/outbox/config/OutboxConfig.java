package com.workoutdone.rpgym.health.outbox.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OutboxPublishProperties.class, OutboxCleanupProperties.class})
public class OutboxConfig {
}